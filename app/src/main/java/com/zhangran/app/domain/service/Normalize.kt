/**
 * 字段规范化（`docs/03` §5 领域约束、§7 分类、§8 账户、§18.1 字段映射）。
 *
 * 职责边界（`docs/03` §15、任务书 M1b §4.2）：
 * - **纯函数**：不查数据库、不读系统时间、不改参数对象；名称→ID 的查询经
 *   [CategoryResolver] / [AccountResolver] 注入，由 M1c 提供实现。
 * - **不产生状态、不产生 confidence**：状态只由 `Decision` 产出（`03` §16），
 *   置信度由 `Confidence` 推导（§18.3）。
 * - 金额、时间**复用 T3 的唯一转换点**（`Money` / `TransactionTime`），不得重新实现口径。
 *
 * 「拒绝语义」（任务书 §4.2 表）：
 * - **可空字段**无法规范化时留 `null`（不算错误，如商户/分类/账户）；
 * - **关键字段**（金额、时间）无法规范化时置 `upstreamResultUsable = false`，
 *   由 Decision 规则 2 / 规则 10 决定进入 Pending（`03` §16）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.RawCandidate
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver

/**
 * 规范化过程中发现的问题（可解释、可测试；不直接产出状态）。
 */
enum class NormalizeIssue {
    /** 金额为负（`docs/03` §4：方向由 type 表达，金额恒非负）。 */
    AMOUNT_NEGATIVE,

    /** 金额超出上限（`Money.MAX_AMOUNT_MINOR`）。 */
    AMOUNT_OVER_LIMIT,

    /** 上游未提供交易时间。 */
    TIME_MISSING,

    /** 上游时间非正数（上游用 0 表示未设置）。 */
    TIME_NON_POSITIVE,

    /** 上游时间超出合理范围。 */
    TIME_OUT_OF_RANGE,

    /** 时区 id 非法（**显式失败**，不得静默回退到设备时区，`docs/03` §5）。 */
    TIME_INVALID_ZONE,

    /** 备注被截断（`docs/03` §5：最长 500 字符）。 */
    NOTE_TRUNCATED,
}

/**
 * 规范化后的中间结构（交给 [Confidence] 组装 `Candidate`）。
 *
 * 说明：本结构比 `Candidate` 多出"名字线索"（`categoryNameHint` / `accountNameHint`）
 * 与 `issues`，用于在未命中映射时保留 hint 并计算 0.7 的字段置信度（`03` §18.3）。
 *
 * @property currency 规范化后的币种；空/空白一律视为 `CNY`（`docs/03` §5）
 * @property transactionTime UTC epoch millis；关键字段无法规范化时为 `null`
 * @property transactionTimeZone 交易发生时区 id；时间失败时为 `null`
 * @property categoryNameHint 分类名的**最佳线索**（未命中时保留，供 Pending 页提示）
 * @property accountNameHint 账户名的**最佳线索**（未命中时保留，供 Pending 页提示）
 * @property upstreamResultUsable 上游结果是否可用；关键字段失败时置 `false`
 */
data class NormalizedCandidate(
    val packageName: String?,
    val sourceChannel: String?,
    val captureSource: CaptureSource,
    val amountMinor: Long?,
    val currency: String,
    val direction: TransactionType?,
    val merchant: String?,
    val note: String?,
    val categoryId: String?,
    val categoryNameHint: String?,
    val accountId: String?,
    val accountNameHint: String?,
    val transactionTime: Long?,
    val transactionTimeZone: String?,
    val externalTransactionId: String?,
    val channelOrderId: String?,
    val capturedAt: Long?,
    val textClue: String?,
    val upstreamResultUsable: Boolean,
    val directionSemanticsTrusted: Boolean,
    val issues: List<NormalizeIssue>,
)

/**
 * 字段规范化服务。
 *
 * @param categoryResolver 分类名 → id（M1c 实现；测试注入 fake）
 * @param accountResolver 账户名 → id（M1c 实现；测试注入 fake）
 * @param defaultZoneId 上游未提供时区时使用的时区 id（默认设备时区，`docs/03` §5）；
 *   构造时注入而不是在转换中读取，保证可测试（`docs/09` 关于"隐式读环境"的教训）
 */
class Normalize(
    private val categoryResolver: CategoryResolver,
    private val accountResolver: AccountResolver,
    private val defaultZoneId: String = TransactionTime.deviceZoneId(),
) {

    /**
     * 规范化一条原始候选。
     *
     * 顺序：金额 → 时间（含时区）→ 商户 → 备注 → 分类 → 账户 → 币种 → 可用性汇总。
     */
    fun normalize(raw: RawCandidate): NormalizedCandidate {
        val issues = mutableListOf<NormalizeIssue>()

        val amountMinor = normalizeAmount(raw.amountMinor, issues)
        val (transactionTime, zoneId) = normalizeTime(
            epochMillis = raw.transactionTimeRaw,
            zoneIdRaw = raw.transactionTimeZoneRaw,
            issues = issues,
        )
        val merchant = normalizeMerchant(raw.merchantRaw)
        val note = normalizeNote(raw.noteRaw, issues)
        val (categoryId, categoryHint) = resolveCategory(raw.categoryNameRaw)
        val (accountId, accountHint) = resolveAccount(raw.accountNameRaw)

        // 关键字段失败 → 上游结果不可用（任务书 §4.2「拒绝语义」、A8）
        val usable = raw.upstreamResultUsable && amountMinor != null && transactionTime != null

        return NormalizedCandidate(
            packageName = raw.packageName?.trim()?.ifBlank { null },
            sourceChannel = raw.sourceChannel?.trim()?.ifBlank { null },
            captureSource = raw.captureSource,
            amountMinor = amountMinor,
            currency = normalizeCurrency(raw.currency),
            direction = raw.direction,
            merchant = merchant,
            note = note,
            categoryId = categoryId,
            categoryNameHint = categoryHint,
            accountId = accountId,
            accountNameHint = accountHint,
            transactionTime = transactionTime,
            transactionTimeZone = zoneId,
            externalTransactionId = raw.externalTransactionId?.trim()?.ifBlank { null },
            channelOrderId = raw.channelOrderId?.trim()?.ifBlank { null },
            capturedAt = raw.capturedAt,
            textClue = raw.textClue?.trim()?.ifBlank { null },
            upstreamResultUsable = usable,
            directionSemanticsTrusted = raw.directionSemanticsTrusted,
            issues = issues.toList(),
        )
    }

    // ---------------------------------------------------------------- 金额

    /**
     * 金额校验：只做**结果校验**，不做解析（解析已是 [Money] 的职责，`docs/03` §4）。
     */
    private fun normalizeAmount(amountMinor: Long?, issues: MutableList<NormalizeIssue>): Long? {
        if (amountMinor == null) return null
        if (amountMinor < 0) {
            issues += NormalizeIssue.AMOUNT_NEGATIVE
            return null
        }
        if (amountMinor > MAX_AMOUNT_MINOR) {
            issues += NormalizeIssue.AMOUNT_OVER_LIMIT
            return null
        }
        return amountMinor
    }

    // ---------------------------------------------------------------- 时间

    /**
     * 时间规范化（复用 [TransactionTime]，**不重新实现**时区/范围判定）。
     *
     * 时区策略（`docs/03` §5）：
     * - 上游未提供时区 → 用 [defaultZoneId] 补齐；
     * - 上游提供了**非法**时区 → **显式失败**，不回退到设备时区（否则跨月归属会错且不可测）。
     */
    private fun normalizeTime(
        epochMillis: Long?,
        zoneIdRaw: String?,
        issues: MutableList<NormalizeIssue>,
    ): Pair<Long?, String?> {
        val providedZone = zoneIdRaw?.trim()?.ifBlank { null }
        val zoneId = providedZone ?: defaultZoneId

        return when (val result = TransactionTime.fromEpochMillis(epochMillis, zoneId)) {
            is TransactionTimeResult.Success -> result.epochMillis to result.zoneId
            is TransactionTimeResult.Failure -> {
                issues += when (result.issue) {
                    TimeIssue.MISSING -> NormalizeIssue.TIME_MISSING
                    TimeIssue.NON_POSITIVE -> NormalizeIssue.TIME_NON_POSITIVE
                    TimeIssue.OUT_OF_RANGE -> NormalizeIssue.TIME_OUT_OF_RANGE
                    TimeIssue.INVALID_ZONE -> NormalizeIssue.TIME_INVALID_ZONE
                }
                null to null
            }
        }
    }

    // ---------------------------------------------------------------- 币种

    /** 空/空白视为 CNY；其余保留原文（非 CNY 由 Decision 规则 6 拒绝，`03` §16）。 */
    private fun normalizeCurrency(raw: String?): String {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return SUPPORTED_CURRENCY
        return if (text.equals(SUPPORTED_CURRENCY, ignoreCase = true)) {
            SUPPORTED_CURRENCY
        } else {
            text
        }
    }

    // ---------------------------------------------------------------- 分类

    /**
     * 分类解析（`docs/03` §7、任务书 §4.4 D-3）。
     *
     * 三级回退：先试**完整名**（如 `父/子`），再试**子名**，都不中则 `categoryId = null`
     * 并保留线索（由 [Confidence] 记为 0.7，`03` §16 规则 8 决定 Pending）。
     *
     * @return `(分类 id, 名称线索)`
     */
    private fun resolveCategory(raw: String?): Pair<String?, String?> {
        val fullName = raw?.trim()?.ifBlank { null } ?: return null to null
        val childName = splitCategoryChildName(fullName)

        categoryResolver.resolveId(fullName)?.let { return it to fullName }
        if (childName != null) {
            categoryResolver.resolveId(childName)?.let { return it to childName }
        }
        // 未命中：保留最具体的线索（子名优先，便于用户辨认）
        return null to (childName ?: fullName)
    }

    /**
     * 拆出分类名的子名（父/子分隔符）。
     *
     * 分隔符集合来自 `docs/10` §5 的上游观察（**具体写法仍待 P0 实测确认**，属 `10` §8 待确认项）：
     * 常见为 `/`、`>`、`:`；`-` 放最后，避免误伤名称本身含连字符的分类。
     */
    private fun splitCategoryChildName(fullName: String): String? {
        for (separator in CATEGORY_SEPARATORS) {
            val index = fullName.indexOf(separator)
            if (index <= 0) continue
            val parent = fullName.substring(0, index).trim()
            val child = fullName.substring(index + separator.length).trim()
            if (parent.isNotEmpty() && child.isNotEmpty()) return child
        }
        return null
    }

    // ---------------------------------------------------------------- 账户

    /** 账户名称映射（`docs/03` §8）：命中 → id；未命中 → `null` 并保留线索。 */
    private fun resolveAccount(raw: String?): Pair<String?, String?> {
        val name = raw?.trim()?.ifBlank { null } ?: return null to null
        val id = accountResolver.resolveId(name)
        return id to name
    }

    // ---------------------------------------------------------------- 商户

    /**
     * 商户规范化（`docs/03` §5 领域约束）。
     *
     * 步骤：去首尾空白 → 合并连续空白（含全角空格）→ 去渠道前后缀 → 去尾部连字符 → 再收敛空白。
     * 结果为空串时返回 `null`（`03` §16 规则 9 视为"商户无法确定"）。
     */
    fun normalizeMerchant(raw: String?): String? {
        var text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null

        text = collapseSpaces(text)

        // 去渠道前后缀（带冒号或不带，按长度降序避免"收款"先于"收款方"被匹配）
        for (prefix in MERCHANT_PREFIXES) {
            text = text.removePrefix("$prefix：").removePrefix("$prefix:")
            if (text.startsWith(prefix)) text = text.removePrefix(prefix)
        }

        text = collapseSpaces(text).trimEnd('-', '－', '—', '–').let(::collapseSpaces).trim()

        return text.ifBlank { null }
    }

    // ---------------------------------------------------------------- 备注

    /**
     * 备注规范化：超长按任务书 §4.4 **D-2 截断**到 500 字符（不拒绝）。
     *
     * 说明：按 **Unicode 码点**计数（emoji 等代理对算 1 个字符），避免把半个代理对截断。
     */
    private fun normalizeNote(raw: String?, issues: MutableList<NormalizeIssue>): String? {
        val text = raw?.trim()
        if (text.isNullOrEmpty()) return null
        if (text.codePointCount(0, text.length) <= MAX_NOTE_LENGTH) return text

        issues += NormalizeIssue.NOTE_TRUNCATED
        val end = text.offsetByCodePoints(0, MAX_NOTE_LENGTH)
        return text.substring(0, end)
    }

    private fun collapseSpaces(text: String): String = SPACES.replace(text, " ")

    companion object {
        /** `docs/03` §5：`note` 最大长度 500 字符。 */
        const val MAX_NOTE_LENGTH: Int = 500

        /** 商户名可能需要剥离的渠道前后缀（`docs/03` §5 举例"收款方"）。 */
        private val MERCHANT_PREFIXES = listOf(
            "收款方", "付款方", "收款人", "付款人", "对方", "商户",
        )

        /** 分类名可能的父子分隔符（顺序即优先级；`docs/10` §5 待实测确认）。 */
        private val CATEGORY_SEPARATORS = listOf("/", ">", "：" , ":", "-")

        /** 连续空白（含全角空格 U+3000）。 */
        private val SPACES = Regex("""[\s\u3000]+""")
    }
}
