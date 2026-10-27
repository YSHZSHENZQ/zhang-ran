/**
 * 置信度推导与 `Candidate` 组装（`docs/03` §18.3、`docs/07` §14）。
 *
 * 依据 `03` §18.3："上游不输出置信度，由 Adapter 按字段缺失情况推导各字段 confidence 与
 * `overallConfidence`，再交 §16 决策；推导规则必须可测试，不得随手给固定值。"
 *
 * 本文件的规则表（任务书 M1b §4.3，人已确认为初始裁定 D-1）：
 *
 * | 字段 | `1.0` | `0.7` | `null`（无法证明达标） |
 * |---|---|---|---|
 * | amount | 金额 `> 0` | — | 缺失（`0` 记 `0.0`） |
 * | direction | 方向明确且语义可信 | 方向明确但语义不可信（借贷/报销） | 缺失 |
 * | merchant | 非空且长度 `>= 2` | 非空但长度 `< 2` | 缺失 |
 * | category | resolver 命中 | 有名字但未命中（保留 hint） | 无名字 |
 * | account | resolver 命中 | 有名字但未命中（保留 hint） | 无名字 |
 *
 * `overallConfidence = min(amountConfidence, directionConfidence)`，任一为 `null` → `0.0`。
 *
 * 为什么用"关键字段最小值"而不是平均值（任务书 §4.3 的理由，`03` §16 优先级说明）：
 * `03` §16 规则 2 只看关键字段是否 `< 0.5`，规则 3 看整体 `< 0.8`；把 overall 定义为关键字段
 * 最小值，可保证"关键字段可靠但分类不可靠"时 overall 仍 `>= 0.8`，从而由规则 8 精确给出
 * `CATEGORY_UNCERTAIN`，而不是笼统的 `LOW_CONFIDENCE`。
 *
 * 纪律：本文件**不产出状态**（Pending / Confirmed 只能由 `Decision` 产出，`03` §16）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.Candidate
import kotlin.math.min

/**
 * 置信度推导与候选组装。
 */
object Confidence {

    /** 金额有值时给满分（可解析且 `> 0`）。 */
    private const val CONF_CERTAIN: Double = 1.0

    /** 有值但不可完全采信（未命中映射 / 语义不可信 / 文本过短）。 */
    private const val CONF_WEAK: Double = 0.7

    /** 金额为 0 时的置信度（可解析但不可作为有效金额）。 */
    private const val CONF_ZERO: Double = 0.0

    /** 商户名视为"足够具体"的最小长度（`03` §5 规范化后仍过短则可靠性下降）。 */
    private const val MIN_MERCHANT_LENGTH: Int = 2

    /**
     * 由规范化结果组装可交给 `Decision` 的 `Candidate`。
     *
     * @param normalized `Normalize.normalize(raw)` 的产出（已含 resolver 结果与线索）
     * @return 满足 `Candidate` 构造约束（`amountMinor >= 0`）的候选
     *
     * 时间缺失时的处理（**重要**）：`Candidate.transactionTime` 非空，因此此处用 `0L` 占位，
     * 同时 `upstreamResultUsable = false`（任务书 A8）。`0L` **不是**真实交易时间：
     * M1c 必须在 Decision 结果为 `Pending` / `ClearlyInvalid` 时不得把该占位值写入
     * `transactions.transactionTime`（`docs/03` §5 禁止伪造时间）。
     */
    fun derive(normalized: NormalizedCandidate): Candidate {
        val amountConfidence = amountConfidenceOf(normalized.amountMinor)
        val directionConfidence = directionConfidenceOf(
            direction = normalized.direction,
            semanticsTrusted = normalized.directionSemanticsTrusted,
        )
        val merchantConfidence = merchantConfidenceOf(normalized.merchant)
        val categoryConfidence = categoryConfidenceOf(
            categoryId = normalized.categoryId,
            nameHint = normalized.categoryNameHint,
        )
        val accountConfidence = accountConfidenceOf(
            accountId = normalized.accountId,
            nameHint = normalized.accountNameHint,
        )

        return Candidate(
            currency = normalized.currency,
            transactionTime = normalized.transactionTime ?: 0L,
            overallConfidence = overallConfidenceOf(amountConfidence, directionConfidence),
            amountMinor = normalized.amountMinor,
            amountConfidence = amountConfidence,
            direction = normalized.direction,
            directionConfidence = directionConfidence,
            merchant = normalized.merchant,
            merchantConfidence = merchantConfidence,
            categoryId = normalized.categoryId,
            categoryConfidence = categoryConfidence,
            accountId = normalized.accountId,
            accountConfidence = accountConfidence,
            textClue = normalized.textClue,
            packageName = normalized.packageName,
            externalTransactionId = normalized.externalTransactionId,
            channelOrderId = normalized.channelOrderId,
            capturedAt = normalized.capturedAt,
            upstreamResultUsable = normalized.upstreamResultUsable,
            directionSemanticsTrusted = normalized.directionSemanticsTrusted,
        )
    }

    // ------------------------------------------------------------ 各字段置信度

    /** 金额：`null` → `null`；`> 0` → `1.0`；`0` → `0.0`。 */
    private fun amountConfidenceOf(amountMinor: Long?): Double? = when {
        amountMinor == null -> null
        amountMinor > 0L -> CONF_CERTAIN
        else -> CONF_ZERO
    }

    /** 方向：`null` → `null`；可信 → `1.0`；有值但语义不可信 → `0.7`（`03` §18.2）。 */
    private fun directionConfidenceOf(
        direction: com.zhangran.app.domain.model.TransactionType?,
        semanticsTrusted: Boolean,
    ): Double? = when {
        direction == null -> null
        semanticsTrusted -> CONF_CERTAIN
        else -> CONF_WEAK
    }

    /** 商户：`null` → `null`；长度 `>= 2` → `1.0`；否则 `0.7`。 */
    private fun merchantConfidenceOf(merchant: String?): Double? = when {
        merchant == null -> null
        merchant.length >= MIN_MERCHANT_LENGTH -> CONF_CERTAIN
        else -> CONF_WEAK
    }

    /** 分类：命中 → `1.0`；有名字未命中 → `0.7`；无名字 → `null`。 */
    private fun categoryConfidenceOf(categoryId: String?, nameHint: String?): Double? = when {
        categoryId != null -> CONF_CERTAIN
        nameHint != null -> CONF_WEAK
        else -> null
    }

    /** 账户：命中 → `1.0`；有名字未命中 → `0.7`；无名字 → `null`。 */
    private fun accountConfidenceOf(accountId: String?, nameHint: String?): Double? = when {
        accountId != null -> CONF_CERTAIN
        nameHint != null -> CONF_WEAK
        else -> null
    }

    /**
     * 整体置信度 = 关键字段（金额、方向）的最小值；任一为 `null` → `0.0`。
     *
     * 非关键字段（商户 / 分类 / 账户）**不参与** overall：它们通过 `03` §16 规则 7/8/9
     * 单独触发 reason，参与 overall 会让规则 8 被规则 3 抢先（见文件头说明）。
     */
    private fun overallConfidenceOf(amountConfidence: Double?, directionConfidence: Double?): Double {
        if (amountConfidence == null || directionConfidence == null) return CONF_ZERO
        return min(amountConfidence, directionConfidence)
    }
}
