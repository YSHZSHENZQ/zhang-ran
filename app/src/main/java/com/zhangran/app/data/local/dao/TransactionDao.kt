/**
 * 交易表 DAO（`transactions`）。
 *
 * 边界（`T2a` §1/§4.3）：本任务只到 DAO，不含 Repository 业务逻辑、不含 Decision。
 *
 * 软删除纪律（`docs/03` §6、`08` §9）：
 * - `deletedAt != null` 即视为已删除；
 * - **默认列表查询必须排除**软删除记录；
 * - 提供"按 id 查询（含已删除）"与"恢复"所需的入口（恢复窗口判定属 Domain）；
 * - **禁止**暴露物理删除接口。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.TransactionState

@Dao
interface TransactionDao {

    /** 新增（冲突则替换，便于导入/迁移场景）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /** 更新（不改软删除状态；删除走 [softDelete]）。 */
    @Update
    suspend fun update(transaction: TransactionEntity)

    /** 按 id 查询**未删除**记录。 */
    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun findById(id: String): TransactionEntity?

    /** 按 id 查询**含已删除**记录（供恢复流程使用，`T2a` §4.3）。 */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findByIdIncludingDeleted(id: String): TransactionEntity?

    /** 默认列表：排除软删除，按交易时间倒序。 */
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY transactionTime DESC")
    suspend fun listAll(): List<TransactionEntity>

    /** 按状态查询（排除软删除）。 */
    @Query(
        "SELECT * FROM transactions WHERE state = :state AND deletedAt IS NULL " +
            "ORDER BY transactionTime DESC",
    )
    suspend fun listByState(state: TransactionState): List<TransactionEntity>

    /** 按月份区间查询（排除软删除），用于统计。 */
    @Query(
        "SELECT * FROM transactions WHERE transactionTime >= :fromInclusive " +
            "AND transactionTime < :toExclusive AND deletedAt IS NULL " +
            "ORDER BY transactionTime DESC",
    )
    suspend fun listInTimeRange(fromInclusive: Long, toExclusive: Long): List<TransactionEntity>

    /** 含软删除的全量查询（仅用于备份/导出等明确场景）。 */
    @Query("SELECT * FROM transactions ORDER BY transactionTime DESC")
    suspend fun listAllIncludingDeleted(): List<TransactionEntity>

    /** 软删除：写入 `deletedAt`，不物理删除。 */
    @Query("UPDATE transactions SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long, updatedAt: Long)

    /** 恢复软删除（清空 `deletedAt`）；恢复窗口判定属 Domain。 */
    @Query("UPDATE transactions SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: String, updatedAt: Long)

    /** 计数（排除软删除），用于测试与统计前置。 */
    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    suspend fun countVisible(): Int
}
