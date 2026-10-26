/**
 * 领域决策结果类型（纯 Kotlin/JVM，无 Android 依赖）。
 *
 * 依据：
 * - `docs/03` §16 领域决策规则（决策输出：`CONFIRMED` / `PENDING(+reasons)` /
 *   `DUPLICATE` / `ClearlyInvalid`）
 * - `docs/03` §6 状态机（`CONFIRMED` / `PENDING` / `DUPLICATE` 的合法来源）
 * - `docs/03` §11 用户修改保护（`FieldEdit` 的字段名清单）
 * - `docs/08` §2.3 Decision 断言表
 *
 * 纪律：本文件**只定义结果与证据载体**，不产出 `TransactionState`。
 * `TransactionState` 的落地由 M1c 的装配层依据本结果映射；Decision 是唯一决定状态的环节
 * （`docs/03` §16 约束、任务书 A3）。
 */
package com.zhangran.app.domain.model

/**
 * Decision 的四种唯一结论（`docs/03` §16）。
 *
 * 与 `TransactionState` 的映射由后续装配层完成：
 * `Confirmed` → `CONFIRMED`；`Pending` → `PENDING`；`Duplicate` → `DUPLICATE`；
 * `ClearlyInvalid` → **不创建 Transaction**（写 `CaptureLog`，状态 `IGNORED_INVALID`）。
 */
sealed interface DecisionResult {

    /** 命中的候选（`ClearlyInvalid` 也保留，便于写捕获日志与证据）。 */
    val candidate: Candidate

    /**
     * 确认入账（`docs/03` §16 规则 12）。
     *
     * @property conflicts 候选值与被保护字段的冲突（只记录、不修改候选；见 [FieldConflict]）
     */
    data class Confirmed(
        override val candidate: Candidate,
        val conflicts: List<FieldConflict> = emptyList(),
    ) : DecisionResult

    /**
     * 进入 Pending（`docs/03` §16 规则 2/3/5/6/7/8/9/10/11）。
     *
     * @property reasons 全部命中的原因（**多 reason 必须全部保留**，`docs/03` §16 优先级说明）
     * @property conflicts 候选值与被保护字段的冲突（只记录、不修改候选）
     */
    data class Pending(
        override val candidate: Candidate,
        val reasons: Set<PendingReason>,
        val conflicts: List<FieldConflict> = emptyList(),
    ) : DecisionResult {
        init {
            // `docs/03` §14：Pending 必须记录原因，且至少一个 reason 非空。
            require(reasons.isNotEmpty()) { "Pending 至少需要一个 reason（docs/03 §14）" }
        }
    }

    /**
     * 判定为既有交易的重复（`docs/03` §16 规则 4；判重结论来自 Dedup）。
     *
     * 约束（`docs/03` §12.2）：保留候选与证据，不物理删除。
     *
     * @property decisionId 命中的既有交易标识（便于 M1c 记录 `DuplicateDecision`）；
     *   本任务只透传，不落库
     * @property conflicts 候选值与被保护字段的冲突（只记录、不修改候选）
     */
    data class Duplicate(
        override val candidate: Candidate,
        val decisionId: String? = null,
        val conflicts: List<FieldConflict> = emptyList(),
    ) : DecisionResult

    /**
     * 明确无效（`docs/03` §16 规则 1）：不创建 Transaction，写 `CaptureLog`，
     * 日志状态 `IGNORED_INVALID`（由 M1c 执行）。
     */
    data class ClearlyInvalid(
        override val candidate: Candidate,
    ) : DecisionResult
}

/**
 * 已被用户修改、受字段级保护的字段（`docs/03` §11 的 `FieldEdit` 子集）。
 *
 * 说明：`docs/03` §11 的持久化实体只保存 `fieldName` / `editedAt` / `editedValueHash`，
 * **用户值本身在既有 Transaction 上**。因此这里由调用方把“字段名 + 用户当前值”一并传入，
 * 供 Decision 检测冲突；Decision 只记录冲突，**绝不改写候选**（任务书 §4.1 硬约束）。
 *
 * @property fieldName 字段名，取值必须属于 [ALLOWED_FIELD_NAMES]（`docs/03` §11）
 * @property userValue 用户当前值的规范化文本（`null` 表示用户明确置空）
 */
data class ProtectedField(
    val fieldName: String,
    val userValue: String?,
) {
    init {
        require(fieldName in ALLOWED_FIELD_NAMES) {
            "未知的受保护字段：$fieldName（允许值见 docs/03 §11）"
        }
    }

    companion object {
        /** `docs/03` §11 列出的全部可保护字段名（SSOT，不得扩展）。 */
        val ALLOWED_FIELD_NAMES: Set<String> = setOf(
            "merchant",
            "amountMinor",
            "type",
            "categoryId",
            "accountId",
            "sourceAccountId",
            "targetAccountId",
            "transactionTime",
            "note",
        )
    }
}

/**
 * 候选值与被保护字段的冲突证据（`docs/03` §11、§16 约束）。
 *
 * 处理路径：
 * 1. 候选保持原样，**不得**因冲突被改写（任务书 §4.1）；
 * 2. 最终写入时以 [userValue] 为准（保留用户值）；
 * 3. 冲突本身作为证据落库，供 Pending 页 / 捕获日志展示。
 *
 * @property fieldName 冲突字段（`docs/03` §11 字段名）
 * @property candidateValue 候选想要写入的值（规范文本；冲突存在时必非空）
 * @property userValue 用户当前值（`null` 表示用户置空，此时候选值同样不得覆盖）
 */
data class FieldConflict(
    val fieldName: String,
    val candidateValue: String,
    val userValue: String?,
)
