/**
 * 交易读写（`docs/03` §5、§6、§17；`docs/04` §6）。
 *
 * 职责边界（任务书 M1c' §4.1）：
 * - **只做数据访问**：不含校验、不含状态判定、不含去重、**不含统计口径**
 *   （规则在 UseCase / Domain）。
 * - 默认列表查询**排除软删除**（`docs/03` §6：`deletedAt IS NULL`）。
 *
 * 关于列表刷新（`M2e-1a` 更新）：DAO 现已提供 `Flow` 查询，因此本层同时提供
 * **响应式读取** [observeVisible] 与既有的 `suspend` 一次性读取 [listVisible]；
 * 后者**保留**（还有其他调用方，且一次性读取在某些场景更省），两者过滤/排序一致。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.TransactionDao
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {

    suspend fun insert(transaction: TransactionEntity)

    /** 按状态读取（默认排除软删除）。 */
    suspend fun listByState(state: TransactionState): List<TransactionEntity>

    /** 全部可见交易（排除软删除），按 `transactionTime` 倒序。 */
    suspend fun listVisible(): List<TransactionEntity>

    /**
     * 响应式可见交易（排除软删除），按 `transactionTime` 倒序；
     * 与 [listVisible] 的过滤/排序一致，任何写入（insert / update / softDelete）都会重新发射。
     */
    fun observeVisible(): Flow<List<TransactionEntity>>

    /**
     * 月区间内的**已确认**交易（排除软删除），半开区间 `[startInclusive, endExclusive)`。
     *
     * 供首页/统计取数（`M2e-1a` §4.6）；口径计算属 Domain，本层不解释金额。
     */
    suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity>

    /** 某状态的条数（排除软删除）；首页「待确认数量」用 `PENDING`。 */
    suspend fun countByState(state: TransactionState): Int

    /** 按 id 读取；`includeDeleted = true` 用于恢复场景（`docs/03` §6）。 */
    suspend fun getById(id: String, includeDeleted: Boolean = false): TransactionEntity?

    /**
     * 更新一笔交易（字段级编辑的唯一落库口，`M2b` §4.3）。
     *
     * 本方法**不做校验**：校验、`FieldEdit` 写入与状态守卫都在
     * `UpdateTransactionFieldUseCase`，本层只负责写。
     */
    suspend fun update(transaction: TransactionEntity)

    /**
     * 软删除：写入 `deletedAt`，**不改 `state`**（`docs/03` §6：可见性由 `deletedAt` 控制）。
     *
     * @param deletedAt 删除时刻（UTC epoch millis）
     */
    suspend fun softDelete(id: String, deletedAt: Long)

    /** 恢复软删除（清空 `deletedAt`）；恢复窗口的判定属 Domain。 */
    suspend fun restore(id: String, updatedAt: Long)

    /**
     * 多条件搜索（`M4e` §4.2；软删除排除、`transactionTime DESC`、分页）。
     *
     * 条件→SQL 的映射在 DAO（`@Bind` 参数，禁止拼接）；本层只做参数传递。
     */
    suspend fun search(criteria: SearchCriteria): List<TransactionEntity>

    /** 与 [search] 同条件的总条数（供 `hasMore` 判定）。 */
    suspend fun countSearch(criteria: SearchCriteria): Int
}

@Singleton
class RoomTransactionRepository @Inject constructor(
    private val dao: TransactionDao,
) : TransactionRepository {

    override suspend fun insert(transaction: TransactionEntity) = dao.insert(transaction)

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        dao.listByState(state)

    override suspend fun listVisible(): List<TransactionEntity> =
        // DAO 已保证 deletedAt IS NULL 且按 transactionTime DESC
        dao.listAll()

    override fun observeVisible(): Flow<List<TransactionEntity>> =
        // DAO 的过滤/排序与 listAll() 逐字一致
        dao.observeVisible()

    override suspend fun listInRange(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransactionEntity> = dao.listConfirmedInRange(startInclusive, endExclusive)

    override suspend fun countByState(state: TransactionState): Int = dao.countByState(state)

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        if (includeDeleted) dao.findByIdIncludingDeleted(id) else dao.findById(id)

    override suspend fun update(transaction: TransactionEntity) = dao.update(transaction)

    override suspend fun softDelete(id: String, deletedAt: Long) =
        // updatedAt 与 deletedAt 同值：软删除也是一次写入
        dao.softDelete(id = id, deletedAt = deletedAt, updatedAt = deletedAt)

    override suspend fun restore(id: String, updatedAt: Long) = dao.restore(id, updatedAt)

    override suspend fun search(criteria: SearchCriteria): List<TransactionEntity> {
        val normalized = criteria.normalized()
        return dao.search(
            keyword = normalized.keyword,
            amountExact = normalized.amountExactMinor,
            amountMin = normalized.amountMinMinor,
            amountMax = normalized.amountMaxMinor,
            dateFrom = normalized.dateFromInclusive,
            dateTo = normalized.dateToExclusive,
            categoryId = normalized.categoryId,
            accountId = normalized.accountId,
            type = normalized.type,
            source = normalized.source,
            state = normalized.state,
            limit = normalized.pageSize,
            offset = normalized.page * normalized.pageSize,
        )
    }

    override suspend fun countSearch(criteria: SearchCriteria): Int {
        val normalized = criteria.normalized()
        return dao.countSearch(
            keyword = normalized.keyword,
            amountExact = normalized.amountExactMinor,
            amountMin = normalized.amountMinMinor,
            amountMax = normalized.amountMaxMinor,
            dateFrom = normalized.dateFromInclusive,
            dateTo = normalized.dateToExclusive,
            categoryId = normalized.categoryId,
            accountId = normalized.accountId,
            type = normalized.type,
            source = normalized.source,
            state = normalized.state,
        )
    }
}
