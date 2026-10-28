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
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import kotlinx.coroutines.flow.Flow

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

    // ------------------------------------------------------------------
    // 响应式读取与首页统计查询（`M2e-1a` §4.6：**只加不删**，
    // 既有 suspend 方法保持原样，供一次性读取与既有调用方继续使用）。
    // ------------------------------------------------------------------

    /**
     * 响应式默认列表：与 [listAll] 的**过滤与排序逐字一致**
     * （`WHERE deletedAt IS NULL ORDER BY transactionTime DESC`）。
     *
     * 逐字一致是硬要求：两套结果若不一致，首页会出现"手动刷新前后不同"的假象。
     */
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY transactionTime DESC")
    fun observeVisible(): Flow<List<TransactionEntity>>

    /**
     * 月区间内的**已确认**交易（排除软删除），半开区间 `[startInclusive, endExclusive)`。
     *
     * 为什么固定 `state = 'CONFIRMED'`：`docs/02` §14「统计」只计 CONFIRMED；
     * PENDING / IGNORED / DELETED / DUPLICATE 一律不计（`docs/03` §6 可见性矩阵）。
     * 枚举以**枚举名**存为 TEXT（见 `Converters`），所以这里直接写字面量。
     *
     * 注意：本方法只负责"取数"，**不含**任何统计口径（EXPENSE−REFUND、排除 TRANSFER 等
     * 都属领域层，禁止下沉到 DAO）。
     */
    @Query(
        "SELECT * FROM transactions WHERE state = 'CONFIRMED' " +
            "AND transactionTime >= :startInclusive AND transactionTime < :endExclusive " +
            "AND deletedAt IS NULL ORDER BY transactionTime DESC",
    )
    suspend fun listConfirmedInRange(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransactionEntity>

    /**
     * 某状态的条数（**排除软删除**）。
     *
     * 首页「待确认数量」用它统计**全时段** PENDING（待办概念，`docs/06` §4.1），不限月。
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE state = :state AND deletedAt IS NULL")
    suspend fun countByState(state: TransactionState): Int

    // ------------------------------------------------------------------
    // 搜索（`M4e` §4.2；**只加不删**；不新增索引、不改 schema）。
    //
    // 全部条件都用 `@Bind` 命名参数，禁止拼接用户输入（SQL 注入）；
    // `:x IS NULL OR ...` 让 9 个维度可任意组合（AND）；软删除一律排除。
    // ------------------------------------------------------------------

    /**
     * 多条件搜索（默认 `transactionTime DESC`，分页）。
     *
     * @param keyword 同时匹配 merchant/note（包含、不区分大小写；由调用方归一化空串→null）
     * @param amountExact 金额等值（分）；与 min/max 互斥（调用方保证等值优先）
     */
    @Query(
        "SELECT * FROM transactions " +
            "WHERE deletedAt IS NULL " +
            "AND (:keyword IS NULL OR LOWER(merchant) LIKE '%' || LOWER(:keyword) || '%' " +
            "OR LOWER(note) LIKE '%' || LOWER(:keyword) || '%') " +
            "AND (:amountExact IS NULL OR amountMinor = :amountExact) " +
            "AND (:amountMin IS NULL OR amountMinor >= :amountMin) " +
            "AND (:amountMax IS NULL OR amountMinor <= :amountMax) " +
            "AND (:dateFrom IS NULL OR transactionTime >= :dateFrom) " +
            "AND (:dateTo IS NULL OR transactionTime < :dateTo) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:accountId IS NULL OR accountId = :accountId) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:source IS NULL OR source = :source) " +
            "AND (:state IS NULL OR state = :state) " +
            "ORDER BY transactionTime DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun search(
        keyword: String?,
        amountExact: Long?,
        amountMin: Long?,
        amountMax: Long?,
        dateFrom: Long?,
        dateTo: Long?,
        categoryId: String?,
        accountId: String?,
        type: TransactionType?,
        source: CaptureSource?,
        state: TransactionState?,
        limit: Int,
        offset: Int,
    ): List<TransactionEntity>

    /** 与 [search] 完全相同的过滤条件，返回总条数（用于 `hasMore`）。 */
    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE deletedAt IS NULL " +
            "AND (:keyword IS NULL OR LOWER(merchant) LIKE '%' || LOWER(:keyword) || '%' " +
            "OR LOWER(note) LIKE '%' || LOWER(:keyword) || '%') " +
            "AND (:amountExact IS NULL OR amountMinor = :amountExact) " +
            "AND (:amountMin IS NULL OR amountMinor >= :amountMin) " +
            "AND (:amountMax IS NULL OR amountMinor <= :amountMax) " +
            "AND (:dateFrom IS NULL OR transactionTime >= :dateFrom) " +
            "AND (:dateTo IS NULL OR transactionTime < :dateTo) " +
            "AND (:categoryId IS NULL OR categoryId = :categoryId) " +
            "AND (:accountId IS NULL OR accountId = :accountId) " +
            "AND (:type IS NULL OR type = :type) " +
            "AND (:source IS NULL OR source = :source) " +
            "AND (:state IS NULL OR state = :state)",
    )
    suspend fun countSearch(
        keyword: String?,
        amountExact: Long?,
        amountMin: Long?,
        amountMax: Long?,
        dateFrom: Long?,
        dateTo: Long?,
        categoryId: String?,
        accountId: String?,
        type: TransactionType?,
        source: CaptureSource?,
        state: TransactionState?,
    ): Int
}