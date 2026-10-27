/**
 * 领域决策服务（纯 Kotlin/JVM，无 Android / Room / DAO 依赖）。
 *
 * 唯一依据：`docs/03` §16 领域决策规则（SSOT，12 条）。
 * **Decision 是唯一允许决定 Pending / Confirmed / Duplicate / ClearlyInvalid 的环节**
 * （`docs/03` §15、§16 约束；任务书 A3）——Adapter / Normalize / Confidence / Dedup /
 * Repository / UI 都不得决定状态。
 *
 * 三条硬约束（`docs/03` §16 约束段、任务书 §4.1）：
 * 1. 决策是唯一决定状态的地方；
 * 2. 同一候选命中多条规则时，**全部 reason 都要保留**；
 * 3. **不得覆盖被保护字段**：冲突时保留用户值、把冲突记入证据，且不得改写候选。
 *
 * 阈值（任务书 A6）：
 * - [DEFAULT_OVERALL_CONFIDENCE_THRESHOLD] = 0.8（`docs/03` §16 规则 3）
 * - [DEFAULT_FIELD_CONFIDENCE_THRESHOLD] = 0.5（规则 2 / 9，非关键字段同理）
 * 均通过构造参数注入，不硬编码在判定里；本任务不做设置持久化。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.DecisionResult
import com.zhangran.app.domain.model.FieldConflict
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.ProtectedField
import com.zhangran.app.domain.model.TransactionType

/**
 * 决策服务（`docs/03` §16）。
 *
 * @param overallConfidenceThreshold 规则 3 的整体置信度阈值（默认 0.8）
 * @param fieldConfidenceThreshold 关键/非关键字段的字段级阈值（默认 0.5）
 * @param supportedPackages 规则 1 的“支持来源包名”集合。
 *   `null` 表示调用方未配置来源过滤（不做包名拒绝）；非 `null` 时包名缺失或不在集合内
 *   一律 `ClearlyInvalid`。来源清单的 SSOT 属采集配置（`docs/07` §5），不在此硬编码。
 */
class Decision(
    private val overallConfidenceThreshold: Double = DEFAULT_OVERALL_CONFIDENCE_THRESHOLD,
    private val fieldConfidenceThreshold: Double = DEFAULT_FIELD_CONFIDENCE_THRESHOLD,
    private val supportedPackages: Set<String>? = null,
) {

    init {
        require(overallConfidenceThreshold in 0.0..1.0) {
            "overallConfidenceThreshold 必须在 0.0..1.0：$overallConfidenceThreshold"
        }
        require(fieldConfidenceThreshold in 0.0..1.0) {
            "fieldConfidenceThreshold 必须在 0.0..1.0：$fieldConfidenceThreshold"
        }
    }

    /**
     * 对单个候选执行决策。
     *
     * 规则顺序（`docs/03` §16）：1–10 按序短路，全部不命中才执行 11，12 兜底。
     * 其中规则 2/3 是前置条件：命中后整笔必为 Pending，但仍继续收集后续规则的 reason，
     * 以满足“多条规则命中时全部 reason 保留”（任务书 A4）。
     *
     * @param candidate 已规范化的候选
     * @param dedup 判重结论（`Dedup.check` 的返回；默认无结论）
     * @param protectedFields 用户已修改并受保护的字段（`docs/03` §11）
     */
    fun decide(
        candidate: Candidate,
        dedup: DedupResult = DedupResult.None,
        protectedFields: List<ProtectedField> = emptyList(),
    ): DecisionResult {
        val conflicts = conflictsOf(candidate, protectedFields)

        // 规则 1：ClearlyInvalid —— 无金额且无方向且无任何文本线索，或来源包名不受支持。
        if (isClearlyInvalid(candidate)) {
            return DecisionResult.ClearlyInvalid(candidate)
        }

        val reasons = LinkedHashSet<PendingReason>()

        // 规则 2：关键字段校验。关键字段为 amountMinor、direction、transactionTime
        // （`docs/03` §16 规则 2，P-007 修订）。
        // - amount / direction：缺失或 confidence < 字段阈值 → 各自 reason；
        // - transactionTime：**没有 confidence 概念**（上游不输出），只按"是否存在且 > 0"判定；
        //   不可用（<= 0）→ UNSUPPORTED_NOTIFICATION（复用"上游结果不可用"语义，不新增枚举）。
        // 三者**都不提前 return**：多 reason 必须全部保留（`docs/03` §16 优先级说明）。
        if (candidate.amountMinor == null || !atLeast(candidate.amountConfidence)) {
            reasons += PendingReason.AMOUNT_UNCERTAIN
        }
        if (candidate.direction == null || !atLeast(candidate.directionConfidence)) {
            reasons += PendingReason.DIRECTION_UNCERTAIN
        }
        // 与 `docs/03` §14「Confirmed 必须拥有真实 transactionTime」互为表里：
        // 任何 <= 0 的候选都不得走到规则 12（`docs/03` §5 字段约束）。
        if (candidate.transactionTime <= 0L) {
            reasons += PendingReason.UNSUPPORTED_NOTIFICATION
        }

        // 规则 3：整体置信度不足。
        if (candidate.overallConfidence < overallConfidenceThreshold) {
            reasons += PendingReason.LOW_CONFIDENCE
        }

        // 规则 4：去重结果为 Duplicate → DUPLICATE（保留候选与证据）。
        // 规则 2/3 在规则 4 之前：前置条件不满足时仍按 Pending 处理（顺序短路）。
        if (dedup is DedupResult.Duplicate) {
            return if (reasons.isEmpty()) {
                DecisionResult.Duplicate(
                    candidate = candidate,
                    decisionId = dedup.matchedTransactionId,
                    conflicts = conflicts,
                )
            } else {
                DecisionResult.Pending(candidate = candidate, reasons = reasons, conflicts = conflicts)
            }
        }

        // 规则 5：疑似重复。
        if (dedup is DedupResult.PossibleDuplicate) {
            reasons += PendingReason.POSSIBLE_DUPLICATE
        }

        // 规则 6：非 CNY → AMOUNT_UNCERTAIN（v0.1 不换算）。
        if (!candidate.currency.equals(SUPPORTED_CURRENCY, ignoreCase = true)) {
            reasons += PendingReason.AMOUNT_UNCERTAIN
        }

        // 规则 7：账户无法匹配。
        if (candidate.accountId.isNullOrBlank() || !atLeast(candidate.accountConfidence)) {
            reasons += PendingReason.ACCOUNT_UNCERTAIN
        }

        // 规则 8：分类无法确定（金额与方向明确时也不允许猜分类入账）。
        // TRANSFER 的 categoryId 按 `docs/03` §13 必须为空，分类对其不适用，故不产生该 reason。
        if (requiresCategory(candidate.direction) &&
            (candidate.categoryId.isNullOrBlank() || !atLeast(candidate.categoryConfidence))
        ) {
            reasons += PendingReason.CATEGORY_UNCERTAIN
        }

        // 规则 9：商户无法确定（merchant 为空，或 merchantConfidence < 字段阈值）。
        if (candidate.merchant.isNullOrBlank() || !atLeast(candidate.merchantConfidence)) {
            reasons += PendingReason.UNKNOWN_MERCHANT
        }

        // 规则 10：上游结果不可用。
        if (!candidate.upstreamResultUsable) {
            reasons += PendingReason.UNSUPPORTED_NOTIFICATION
        }

        // 规则 11：上游类型未定义或语义不可信（借贷 / 还款 / 报销等，`docs/03` §18.2）。
        if (!candidate.directionSemanticsTrusted) {
            reasons += PendingReason.DIRECTION_UNCERTAIN
        }

        // 规则 12：以上全部不命中 → CONFIRMED。
        return if (reasons.isEmpty()) {
            DecisionResult.Confirmed(candidate = candidate, conflicts = conflicts)
        } else {
            DecisionResult.Pending(candidate = candidate, reasons = reasons, conflicts = conflicts)
        }
    }

    /**
     * 规则 1（`docs/03` §16）：无法获得任何可用字段（无金额、无方向、无文本线索），
     * 或来源包名不在支持列表内。
     */
    private fun isClearlyInvalid(candidate: Candidate): Boolean {
        if (supportedPackages != null) {
            val packageName = candidate.packageName
            if (packageName == null || packageName !in supportedPackages) return true
        }
        val noAmount = candidate.amountMinor == null
        val noDirection = candidate.direction == null
        val noTextClue = candidate.merchant.isNullOrBlank() && candidate.textClue.isNullOrBlank()
        return noAmount && noDirection && noTextClue
    }

    /**
     * 该方向是否需要分类。
     *
     * `docs/03` §13：TRANSFER 的 `categoryId` 必须为空，因此分类对其不适用；
     * 其它类型（含方向缺失时）按规则 8 正常判定。
     */
    private fun requiresCategory(direction: TransactionType?): Boolean = direction != TransactionType.TRANSFER

    /**
     * 字段置信度是否达到阈值。
     *
     * `null` 表示无法证明达标 → 视为不足（宁可 Pending，`AGENTS.md` §1）。
     */
    private fun atLeast(confidence: Double?): Boolean =
        confidence != null && confidence >= fieldConfidenceThreshold

    /**
     * 检测候选值与被保护字段的冲突（`docs/03` §11、§16 约束）。
     *
     * 只记录冲突：候选保持原样，用户值以 [FieldConflict.userValue] 为最终写入值。
     */
    private fun conflictsOf(
        candidate: Candidate,
        protectedFields: List<ProtectedField>,
    ): List<FieldConflict> = protectedFields.mapNotNull { protected ->
        val candidateValue = candidateValueOf(candidate, protected.fieldName) ?: return@mapNotNull null
        if (candidateValue == protected.userValue) {
            null
        } else {
            FieldConflict(
                fieldName = protected.fieldName,
                candidateValue = candidateValue,
                userValue = protected.userValue,
            )
        }
    }

    /**
     * 取候选在 `docs/03` §11 字段名下的规范文本值。
     *
     * 候选没有该字段（或值为 `null`）时返回 `null`：此时不存在“被候选覆盖”的风险，
     * 因而不构成冲突。`type` 是 `docs/03` §11 的字段名，对应候选的 [Candidate.direction]。
     */
    private fun candidateValueOf(candidate: Candidate, fieldName: String): String? = when (fieldName) {
        "merchant" -> candidate.merchant
        "amountMinor" -> candidate.amountMinor?.toString()
        "type" -> candidate.direction?.name
        "categoryId" -> candidate.categoryId
        "accountId" -> candidate.accountId
        "transactionTime" -> candidate.transactionTime.toString()
        // sourceAccountId / targetAccountId / note：候选未携带，不存在覆盖风险。
        else -> null
    }

    companion object {
        /** 规则 3 的整体置信度默认阈值（`docs/03` §16）。 */
        const val DEFAULT_OVERALL_CONFIDENCE_THRESHOLD: Double = 0.8

        /** 规则 2 / 9 的字段置信度默认阈值（`docs/03` §16）。 */
        const val DEFAULT_FIELD_CONFIDENCE_THRESHOLD: Double = 0.5
    }
}
