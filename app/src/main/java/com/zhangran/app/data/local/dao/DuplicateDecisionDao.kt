/**
 * 重复判定表 DAO（`duplicate_decisions`）。
 *
 * 规则（`docs/03` §20）：
 * - 撤销判定时写 `revokedAt`（`DUPLICATE → PENDING`），**不得删除**原记录 → 不提供 DELETE 接口；
 * - 支持按候选 / 原交易查询，供重复详情页展示。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhangran.app.data.local.entity.DuplicateDecisionEntity

@Dao
interface DuplicateDecisionDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(decision: DuplicateDecisionEntity)

    @Query("SELECT * FROM duplicate_decisions ORDER BY decidedAt ASC")
    suspend fun listAll(): List<DuplicateDecisionEntity>

    @Query("SELECT * FROM duplicate_decisions WHERE id = :id")
    suspend fun findById(id: String): DuplicateDecisionEntity?

    /** 按候选查判定记录（重复详情页）。 */
    @Query("SELECT * FROM duplicate_decisions WHERE candidateId = :candidateId ORDER BY decidedAt DESC")
    suspend fun listByCandidate(candidateId: String): List<DuplicateDecisionEntity>

    /** 按原交易查判定记录（重复详情页）。 */
    @Query(
        "SELECT * FROM duplicate_decisions WHERE existingTransactionId = :transactionId " +
            "ORDER BY decidedAt DESC",
    )
    suspend fun listByExistingTransaction(transactionId: String): List<DuplicateDecisionEntity>

    /** 是否存在**未撤销**的判定（避免同一对候选被反复判定，`03` §20）。 */
    @Query(
        "SELECT COUNT(*) FROM duplicate_decisions " +
            "WHERE candidateId = :candidateId AND existingTransactionId = :existingTransactionId " +
            "AND revokedAt IS NULL",
    )
    suspend fun countActive(candidateId: String, existingTransactionId: String): Int

    /** 撤销判定：写 `revokedAt`，**不删除**记录。 */
    @Query("UPDATE duplicate_decisions SET revokedAt = :revokedAt WHERE id = :id")
    suspend fun revoke(id: String, revokedAt: Long)

    /** 调试清账。产品撤销走 [revoke]，不走物理删除。 */
    @Query("DELETE FROM duplicate_decisions")
    suspend fun clearAll()
}
