/**
 * 采集核心侧给出的**未规范化**字段（账然自有模型）。
 *
 * 依据：
 * - `docs/03` §3.2 TransactionCandidate（候选允许字段不完整）
 * - `docs/03` §18.1 字段映射（`money`/`time`/`cateName`/`accountName*` 的原始形态）
 * - `docs/03` §5 领域约束（`merchant` 需规范化、`note` 最长 500）
 * - `docs/10` §5 上游字段硬事实（无时区、`cateName` 含父子分隔符且写法未统一）
 *
 * 纪律：
 * - 本类型**不得**出现任何上游类型（`net.ankio.auto.*`、`BillInfoModel` 等，`docs/03` §15、`docs/04` §20）；
 *   它是 Adapter（U2/M1b 边界）交出结果时使用的账然内部结构。
 * - 金额已由 `Money`（T3）转成分；本类型**只接收结果**，不再做金额解析
 *   （`docs/03` §4：金额语义的唯一转换点是 `Money`）。
 * - 本类型是**只读快照**：上游对象可能被就地修改（手续费改写 `money`），
 *   因此 Adapter 必须在交出前完成拷贝。
 */
package com.zhangran.app.domain.model

/**
 * 采集核心给出的原始字段（未规范化）。
 *
 * @property packageName 来源应用包名（`docs/03` §16 规则 1 的"支持列表"判定用）
 * @property sourceChannel 渠道原文（如 `wechat` / `alipay`；仅入证据，不参与判重）
 * @property captureSource 采集来源（`docs/07` §5 枚举 SSOT）
 * @property amountMinor 已转分的金额；`null` 表示上游未提供金额
 * @property currency 上游币种原文（可能为空；非 CNY 一律由 Decision 规则 6 拒绝）
 * @property direction 已由上游类型映射得出的交易方向（`docs/03` §18.2）；`null` = 未识别
 * @property merchantRaw 未规范化商户名
 * @property noteRaw 未规范化备注
 * @property categoryNameRaw 上游 `cateName`（可能含父子分隔符，`docs/10` §5）
 * @property accountNameRaw 上游账户名（转出 / 收入方）
 * @property transactionTimeRaw 上游毫秒时间戳；`null` 表示未提供
 * @property transactionTimeZoneRaw 上游若提供时区则给出 IANA id；通常为 `null`（`docs/10` §5）
 * @property externalTransactionId 上游交易号（`docs/03` §12.2 规则 1）
 * @property channelOrderId 上游订单号（`docs/03` §12.2 规则 2）
 * @property capturedAt 捕获时间（UTC epoch millis；规则 2 的 24 小时窗口）
 * @property textClue 脱敏后的文本线索（用于规则 1「不是空事件」与证据）
 * @property upstreamResultUsable 上游结果是否可用（`false` = 未识别 / schema 校验失败）
 * @property directionSemanticsTrusted 方向语义是否可信（`false` = 借贷 / 还款 / 报销等
 *   `docs/03` §18.2 未定义语义，规则 11）
 */
data class RawCandidate(
    val packageName: String?,
    val sourceChannel: String?,
    val captureSource: CaptureSource,
    val amountMinor: Long?,
    val currency: String?,
    val direction: TransactionType?,
    val merchantRaw: String?,
    val noteRaw: String?,
    val categoryNameRaw: String?,
    val accountNameRaw: String?,
    val transactionTimeRaw: Long?,
    val transactionTimeZoneRaw: String?,
    val externalTransactionId: String?,
    val channelOrderId: String?,
    val capturedAt: Long?,
    val textClue: String?,
    val upstreamResultUsable: Boolean = true,
    val directionSemanticsTrusted: Boolean = true,
)
