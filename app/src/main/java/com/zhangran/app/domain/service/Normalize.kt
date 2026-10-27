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
     * 分类解析（`docs/03` §7、任务书 §4.4 D-3；按裁定单 §1.3 加"双段命中"保护）。
     *
     * 回退顺序（D-3 保留）：
     * 1. 先试**完整名**（`父/子` 本身也可能就是一个分类名）——命中即返回，不拆分；
     * 2. 完整名未命中时才拆父子名，且**父段与子段都必须能映射到分类**才算"父子命中"，
     *    此时返回子名对应的 id（最具体），置信度由 [Confidence] 记为 1.0；
     * 3. 任一段映射失败 → **不拆开**，把**完整名**作为线索保留（`categoryId = null`，
     *    [Confidence] 记为 0.7，`03` §16 规则 8 判 Pending）。
     *
     * 只"少拆"不"误拆"：即使分隔符猜对，拆分本身也不构成分类可信的证据
     * （裁定单 §1.3）。
     *
     * @return `(分类 id, 名称线索)`
     */
    private fun resolveCategory(raw: String?): Pair<String?, String?> {
        val fullName = raw?.trim()?.ifBlank { null } ?: return null to null

        // 第 1 级：完整名优先（完整名自身命中时无需拆分，不受"双段命中"约束）
        categoryResolver.resolveId(fullName)?.let { return it to fullName }

        // 第 2 级：拆父子名，但父段与子段都必须能映射到分类（裁定单 §1.3）
        val split = splitCategoryName(fullName)
        if (split != null) {
            val (parentName, childName) = split
            val childId = categoryResolver.resolveId(childName)
            if (childId != null && categoryResolver.resolveId(parentName) != null) {
                return childId to childName
            }
        }

        // 未命中或拆分未通过校验：保留**完整名**作线索，不拆开（宁可 Pending，不可误分类）
        return null to fullName
    }

    /**
     * 按 [CATEGORY_SEPARATORS] 把分类名语法拆成 `(父段, 子段)`；无合法分隔符时返回 `null`。
     *
     * 本函数只做**语法拆分**，不判断两段是否都能映射到分类（该校验在 [resolveCategory]）。
     */
    private fun splitCategoryName(fullName: String): Pair<String, String>? {
        for (separator in CATEGORY_SEPARATORS) {
            val index = fullName.indexOf(separator)
            if (index <= 0) continue
            val parent = fullName.substring(0, index).trim()
            val child = fullName.substring(index + separator.length).trim()
            if (parent.isNotEmpty() && child.isNotEmpty()) return parent to child
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
     * 商户规范化（`docs/03` §5 领域约束；裁定单 §2）。
     *
     * 步骤：去首尾空白 → 合并连续空白（含全角空格）→ **仅剥离一次**渠道前缀 →
     * 去尾部连字符 → 再收敛空白。
     *
     * 前缀/后缀规则见 [MERCHANT_PREFIXES]：仅匹配开头、只剥离一次、剥离后不得为空
     * （为空则保留剥离前的值，避免本来有商户信息却变成 `UNKNOWN_MERCHANT`）。
     * 结果为空串时返回 `null`（`03` §16 规则 9 视为"商户无法确定"）。
     */
    fun normalizeMerchant(raw: String?): String? {
        val original = raw?.trim().orEmpty()
        if (original.isEmpty()) return null

        var text = collapseSpaces(original)

        // 仅剥离一次；剥离后为空则保留剥离前的值（裁定单 §2.2）
        val stripped = stripMerchantChannelPrefix(text)
        if (stripped.isNotBlank()) text = stripped

        text = collapseSpaces(text).trimEnd('-', '－', '—', '–').let(::collapseSpaces).trim()

        return text.ifBlank { null }
    }

    /**
     * 剥离**开头**的渠道角色标签（[MERCHANT_PREFIXES]），最多一次。
     *
     * - **仅匹配前缀**：出现在中间的标签不处理（如 `星巴克收款方店` 保持原样）；
     * - 可吞掉标签后紧跟的全/半角冒号与空白（`收款方：张三` / `收款方: 张三` / `收款方 张三`）；
     * - **只剥离一次**：只处理清单中第一个命中的标签，不连续剥离
     *   （`收款方付款方张三` → `付款方张三`，不会继续剥成 `张三`）；
     * - 返回空串表示"剥离后为空"，由调用方保留剥离前的值。
     */
    private fun stripMerchantChannelPrefix(text: String): String {
        for (prefix in MERCHANT_PREFIXES) {
            if (!text.startsWith(prefix)) continue
            val rest = text.removePrefix(prefix).trimStart()
            val withoutColon = when {
                rest.startsWith("：") || rest.startsWith(":") -> rest.substring(1)
                else -> rest
            }
            return withoutColon.trimStart()
        }
        return text
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

        /**
         * 商户名需要剥离的**渠道角色标签**（`docs/03` §5 举例"收款方"；裁定单 §2）。
         *
         * 三条约束（**必须同时满足**，见 [stripMerchantChannelPrefix]）：
         * 1. **仅前缀**：只剥离字符串起始处的标签，出现在中间不处理
         *    （`星巴克收款方店` 保持原样）；
         * 2. **仅一次**：命中第一个标签后即停止，不连续剥离多个标签
         *    （`收款方付款方张三` → `付款方张三`）；
         * 3. **剥离后不得为空**：剥离结果为空串时保留剥离前的值
         *    （`收款方` 保持 `收款方`，否则会被 `03` §16 规则 9 误判为 `UNKNOWN_MERCHANT`）。
         *
         * `商户` **不在清单内**：它是通用词，可能是真实店名前缀（如 `商户服务中心`），
         * 误删会改变商户名，影响展示与 `03` §12.2 的去重比对。
         *
         * 后缀（尾部连字符 `-` / `－` / `—` / `–`）的剥离见 [normalizeMerchant]。
         */
        internal val MERCHANT_PREFIXES = listOf(
            "收款方", "付款方", "收款人", "付款人", "对方",
        )

        /**
         * 分类名父子分隔符（顺序即优先级）。
         *
         * **已收窄为 `/` 与 `>`**（裁定单 §1.1/§1.2）：`-`（`话费-充值`、`工资-奖金` 等
         * 复合分类名会被误拆并错误分类）、`：`/`:` 风险高，均已移除。
         *
         * **待 P0 真实载荷校准（`docs/10` §8 #1）**：具体分隔符必须在 P0 用真实载荷确认，
         * 禁止按文档猜测；本常量集中定义，便于 P0 后一处修改。
         */
        internal val CATEGORY_SEPARATORS = listOf("/", ">")

        /** 连续空白（含全角空格 U+3000）。 */
        private val SPACES = Regex("""[\s\u3000]+""")
    }
}
