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
}
