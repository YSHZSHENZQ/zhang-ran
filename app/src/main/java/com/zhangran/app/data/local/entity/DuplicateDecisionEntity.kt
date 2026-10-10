/**
 * 重复判定表实体（`duplicate_decisions`）。
 *
 * 字段逐字对齐 `docs/03` §20 DuplicateDecision（11 个字段）。
 *
 * 关键点（`03` §20 规则）：
 * - 用户判定"这是同一笔" → 新候选状态置 `DUPLICATE`，保留候选与证据、不物理删除，并写入一条本记录；
 * - 用户判定"这是两笔不同交易" → 也写入一条（`verdict = DIFFERENT_TRANSACTION`），避免反复判定；
 * - 撤销（`DUPLICATE → PENDING`，`03` §6）时写 `revokedAt`，**不得删除**原记录；
 * - `existingTransactionId` **必填**；`candidateTransactionId` 可空（候选未必已落库）；
 * - `matchSignals` 只作证据快照，不得替代判重依据（判重依据见 `03` §12）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.DecisionActor
import com.zhangran.app.domain.model.DuplicateVerdict

@Entity(
    tableName = "duplicate_decisions",
    indices = [
        // 重复详情页：按候选或原交易查判定记录
        Index(value = ["candidateId"]),
        Index(value = ["existingTransactionId"]),
        Index(value = ["candidateTransactionId"]),
    ],
)
data class DuplicateDecisionEntity(
    @PrimaryKey
    val id: String,
    /** 被判定的新候选（捕获 id）。 */
    val candidateId: String,
    /** 候选已落为 Transaction 时回填。 */
    val candidateTransactionId: String? = null,
    /** 既有交易（`03` §12.2 的"原记录"），**必填**。 */
    val existingTransactionId: String,
    val verdict: DuplicateVerdict,
    val decidedBy: DecisionActor,
    val decidedAt: Long,
    /** 判定依据的可读说明。 */
    val reason: String? = null,
    /** 结构化信号快照（金额 / 时间差 / 商户 / 单号）。 */
    val matchSignals: String? = null,
    /** 关联 `CaptureEvidence`。 */
    val evidenceId: String? = null,
    /** 撤销时间；非空表示该判定已撤销。 */
    val revokedAt: Long? = null,
)
