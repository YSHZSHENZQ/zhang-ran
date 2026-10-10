/**
 * 金额解析与转分（纯 Kotlin/JVM，无 Android 依赖）。
 *
 * 依据：
 * - `docs/03` §4 金额（`Long amountMinor`；非法金额不得产生 CONFIRMED；`amountMinor >= 0`）
 * - `docs/03` §5 字段约束（`currency` v0.1 仅 CNY，非 CNY → Pending，禁止换算）
 * - `docs/08` §2.1 Money 断言表
 * - `docs/10` §5 上游事实（`money` / `fee` 是 `Double`）
 * - `AGENTS.md` §5 数据安全（禁止 Double / Float 参与金额存储与最终运算）
 *
 * 本文件是**金额语义的唯一转换点**：上游 `Double` → 账然 `Long amountMinor`。
 *
 * 金额纪律：
 * - 输入先**转字符串再解析**（`BigDecimal(value.toString())`），绝不使用 `value * 100`
 *   或 `BigDecimal(Double)`（后者会带二进制误差，见 `docs/09` 相关条目）。
 * - 最终金额运算全部在 `BigDecimal` / `Long` 上进行，不出现 `Double` / `Float`。
 * - 失败必须**显式返回失败**，不得静默返回 0 或 null（`docs/03` §4）。
 */
package com.zhangran.app.domain.service

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 每 1 个货币单位对应多少个最小单位（人民币为“分”，故为 100）。
 *
 * 说明：v0.1 仅支持 CNY（`docs/03` §5），因此这里只有一个常量；
 * 若未来支持多币种，应改为按币种查询，而不是在此堆分支。
 */
private const val MINOR_UNITS_PER_UNIT: Int = 2

/**
 * 金额上限（最小单位）。
 *
 * 取 `999_999_999_99L`（约 100 亿元），远大于个人记账量级；
 * 超出即视为非法（`docs/03` §4「超出上限」）。
 */
const val MAX_AMOUNT_MINOR: Long = 99_999_999_999L

/** v0.1 唯一支持的币种（`docs/03` §5）。 */
const val SUPPORTED_CURRENCY: String = "CNY"

/**
 * 金额转换结果。
 *
 * 用密封类显式表达三种（含两类失败）结局，而不是 `Long?` 或抛异常，
 * 以便上层据此决定进入 Pending 与对应 reason（`docs/03` §16）。
 */
sealed interface MoneyResult {

    /** 面额（绝对金额，最小单位）。方向由 `TransactionType` 表达，金额本身恒非负。 */
    val amountMinor: Long

    /**
     * 成功。
     *
     * @property amountMinor 金额（最小单位，`>= 0`）
     */
    data class Success(override val amountMinor: Long) : MoneyResult

    /**
     * 非法金额：无法解析、含非法字符、超过上限。
     *
     * 上层处理：不得入账（`docs/03` §4），应进入 Pending。
     *
     * @property amountMinor 固定为 -1，仅用于满足接口；**调用方不得使用该值**
     * @property raw 原始输入（脱敏后可用于证据）
     * @property reason 失败原因（便于日志与测试断言）
     */
    data class Invalid(
        val raw: String,
        val reason: String,
        override val amountMinor: Long = UNUSED_AMOUNT,
    ) : MoneyResult

    /**
     * 币种不支持（非 CNY）。
     *
     * 上层处理：进入 Pending + `AMOUNT_UNCERTAIN`（`docs/03` §5、§16 规则 6）；
     * **禁止换算**（丢弃上游 `rate`）。
     *
     * @property currency 上游给出的币种原文
     */
    data class CurrencyNotSupported(
        val currency: String,
        override val amountMinor: Long = UNUSED_AMOUNT,
    ) : MoneyResult

    companion object {
        /** 失败结果中占位用的金额；任何消费方都不得把它当作真实金额。 */
        const val UNUSED_AMOUNT: Long = -1L
    }
}

/**
 * 金额解析入口（对象方法均为纯函数，无状态）。
 */
object Money {

    /**
     * 把上游 `Double` 金额转为最小单位。
     *
     * 这是 Adapter 的主入口：**先转字符串再解析**，避免二进制误差。
     *
     * @param value 上游金额（可为 `null`，表示上游未提供）
     * @param currency 上游币种；空串或 `null` 视为 CNY（`docs/03` §5）
     */
    fun fromUpstream(value: Double?, currency: String? = null): MoneyResult {
        if (value == null) {
            return MoneyResult.Invalid(raw = "null", reason = "上游未提供金额")
        }
        if (!value.isFinite()) {
            return MoneyResult.Invalid(raw = value.toString(), reason = "金额不是有限数")
        }
        return fromText(value.toString(), currency)
    }

    /**
     * 把文本金额转为最小单位。
     *
     * 可容忍：前置 `¥` / `￥`、首尾空白、半角逗号 `,` 与全角逗号 `，` 千分位。
     * 一律拒绝：空串、非数字字符、多个小数点、多个负号、负号、科学计数法以外的异常写法。
     *
     * @param text 金额文本，例如 `"¥12.34"`、`"1,234.56"`、`"1，234.56"`
     * @param currency 币种；空串或 `null` 视为 CNY
     */
    fun fromText(text: String?, currency: String? = null): MoneyResult {
        val currencyCheck = checkCurrency(currency)
        if (currencyCheck != null) return currencyCheck

        val raw = text ?: return MoneyResult.Invalid(raw = "null", reason = "金额为空")
        val normalized = normalize(raw)
            ?: return MoneyResult.Invalid(raw = raw, reason = "金额含非法字符或为空")

        val decimal = try {
            BigDecimal(normalized)
        } catch (e: NumberFormatException) {
            return MoneyResult.Invalid(raw = raw, reason = "无法解析为数字")
        }

        if (decimal.signum() < 0) {
            return MoneyResult.Invalid(raw = raw, reason = "金额不得为负（方向由 type 表达）")
        }

        val minor = try {
            decimal.movePointRight(MINOR_UNITS_PER_UNIT)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: ArithmeticException) {
            return MoneyResult.Invalid(raw = raw, reason = "金额超出可表示范围或小数位过多")
        }

        if (minor > MAX_AMOUNT_MINOR) {
            return MoneyResult.Invalid(raw = raw, reason = "金额超过上限")
        }
        return MoneyResult.Success(minor)
    }

    /**
     * 币种校验。
     *
     * @return 非 CNY 时返回失败结果；CNY（含空/`null`）返回 `null` 表示通过
     */
    private fun checkCurrency(currency: String?): MoneyResult.CurrencyNotSupported? {
        val normalized = currency?.trim().orEmpty()
        // 空串视为 CNY（上游未提供币种时按 v0.1 唯一支持的币种处理）
        if (normalized.isEmpty()) return null
        if (normalized.equals(SUPPORTED_CURRENCY, ignoreCase = true)) return null
        return MoneyResult.CurrencyNotSupported(currency = normalized)
    }

    /**
     * 归一化金额文本；非法返回 `null`。
     *
     * 规则：
     * - 去掉首尾空白、前置货币符号 `¥` / `￥`、千分位逗号（半角与全角）
     * - 只允许 `[0-9]` 与至多一个 `.`
     * - 允许省略整数部分（`.5` → `0.5`）与省略小数部分（`1.` → `1`）
     */
    private fun normalize(raw: String): String? {
        var text = raw.trim()
        if (text.isEmpty()) return null

        // 去掉前置货币符号（可重复出现，如 "¥￥1"）
        text = text.trimStart('¥', '￥').trim()
        if (text.isEmpty()) return null

        // 去掉千分位（半角 + 全角）
        text = text.replace(",", "").replace("，", "")
        if (text.isEmpty()) return null

        // 只允许数字与一个小数点；用正则一次性拒绝负号/字母/多小数点等
        if (!MONEY_PATTERN.matches(text)) return null

        // 补全省略的整数/小数部分，交给 BigDecimal 解析
        return when {
            text.startsWith(".") -> "0$text"
            text.endsWith(".") -> text.dropLast(1)
            else -> text
        }
    }

    /** 合法金额文本：可选整数部分 + 可选小数部分，且不能为空。 */
    private val MONEY_PATTERN = Regex("""^(\d+(\.\d*)?|\.\d+)$""")
}
