/**
 * 规范化后的交易候选（纯 Kotlin/JVM，无 Android 依赖）。
 *
 * 依据：
 * - `docs/03` §16 决策输入（规范化后的候选：各字段 confidence、`overallConfidence`、
 *   去重结果、字段保护状态）
 * - `docs/03` §12.2 判重所需字段（金额、交易时间、商户、方向、账户、渠道单号、捕获时间）
 * - `docs/03` §14 领域不变量（`amountMinor >= 0`）
 *
 * 职责边界（`AGENTS.md` §4）：
 * 本类型只是 **Normalize（M1b）交给 Decision / Dedup 的数据**，不负责规范化、不推导置信度、
 * 不产生任何状态。`Candidate` 一旦构造完成即视为“已规范化”，两个领域服务只读不改。
 *
 * 字段缺省语义：
 * - 可空字段为 `null` 表示“该字段不可用/未识别”，**不得解读为 0 或空串**；
 * - 各 `*Confidence` 为 `null` 表示“无法证明达标”，决策层按“不足”处理（宁可 Pending）；
 * - [currency] 用字符串而非 `Currency` 枚举：`Currency` 在 v0.1 只有 `CNY`
 *   （`docs/03` §5、`Enums.kt`），而非 CNY 的币种**没有**合法枚举值可用，
 *   若强塞枚举就会把“USD”误当成 CNY。此处保留上游规范化后的币种代码，
 *   由 Decision 规则 6 统一拒绝非 CNY（reason = `AMOUNT_UNCERTAIN`）。
 */
package com.zhangran.app.domain.model

/**
 * 规范化后的交易候选（`docs/03` §16 的决策输入）。
 *
 * @property currency 币种代码（v0.1 仅接受 `CNY`；其余一律 Pending）
 * @property transactionTime 交易时间（UTC epoch millis；规范化后必填）
 * @property overallConfidence 整笔候选的总体置信度（`0.0..1.0`）
 * @property amountMinor 金额（最小货币单位，分）；`null` 表示无法解析
 * @property amountConfidence 金额字段置信度
 * @property direction 交易方向/类型（`docs/03` §5 的 `type`）；`null` 表示未识别
 * @property directionConfidence 方向字段置信度
 * @property merchant 规范化后的商户名；`null` 表示未识别
 * @property merchantConfidence 商户字段置信度
 * @property categoryId 已匹配的分类 id；`null` 表示未匹配
 * @property categoryConfidence 分类字段置信度
 * @property accountId 已匹配的账户 id；`null` 表示未匹配
 * @property accountConfidence 账户字段置信度
 * @property textClue 任何可用于判断“不是空事件”的文本线索（通知标题/正文等，脱敏后）
 * @property packageName 来源应用包名（规则 1 的“支持列表”判定用）
 * @property externalTransactionId 上游交易号（`docs/03` §12.2 规则 1）
 * @property channelOrderId 渠道单号（`docs/03` §12.2 规则 2）
 * @property capturedAt 捕获时间（UTC epoch millis；`docs/03` §12.2 规则 2 的 24 小时窗口）
 * @property upstreamResultUsable 上游结果是否可用；`false` 表示未识别 / schema 校验失败 /
 *   缺少可用字段（规则 10）。默认 `true`
 * @property directionSemanticsTrusted 方向语义是否可信；`false` 表示上游类型为借贷 / 还款 /
 *   报销等未定义语义（`docs/03` §18.2，规则 11）。默认 `true`
 */
data class Candidate(
    val currency: String,
    val transactionTime: Long,
    val overallConfidence: Double,
    val amountMinor: Long? = null,
    val amountConfidence: Double? = null,
    val direction: TransactionType? = null,
    val directionConfidence: Double? = null,
    val merchant: String? = null,
    val merchantConfidence: Double? = null,
    val categoryId: String? = null,
    val categoryConfidence: Double? = null,
    val accountId: String? = null,
    val accountConfidence: Double? = null,
    val textClue: String? = null,
    val packageName: String? = null,
    val externalTransactionId: String? = null,
    val channelOrderId: String? = null,
    val capturedAt: Long? = null,
    val upstreamResultUsable: Boolean = true,
    val directionSemanticsTrusted: Boolean = true,
) {
    init {
        // `docs/03` §14：amountMinor 恒非负（方向由 direction 表达，金额不带符号）。
        require(amountMinor == null || amountMinor >= 0L) {
            "amountMinor 不得为负：$amountMinor（方向应由 direction 表达）"
        }
    }
}
