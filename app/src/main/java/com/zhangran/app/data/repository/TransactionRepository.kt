/**
 * 交易读写（`docs/03` §5、§6、§17；`docs/04` §6）。
 *
 * 职责边界（任务书 M1c' §4.1）：
 * - **只做数据访问**：不含校验、不含状态判定、不含去重（规则在 UseCase / Domain）。
 * - 默认列表查询**排除软删除**（`docs/03` §6：`deletedAt IS NULL`）。
 *
 * 关于列表刷新：DAO 全是 `suspend` 一次性查询（`data/local/` 本任务不可改，
 * 无法新增 `Flow` 返回的查询），因此本层只提供 `suspend` 列表读取；
 * 写入后由调用方（`HomeViewModel.refresh()`）重新拉取。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.TransactionDao
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.TransactionState
import javax.inject.Inject
import javax.inject.Singleton

interface TransactionRepository {

    suspend fun insert(transaction: TransactionEntity)

    /** 按状态读取（默认排除软删除）。 */
    suspend fun listByState(state: TransactionState): List<TransactionEntity>

    /** 全部可见交易（排除软删除），按 `transactionTime` 倒序。 */
    suspend fun listVisible(): List<TransactionEntity>

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

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        if (includeDeleted) dao.findByIdIncludingDeleted(id) else dao.findById(id)
}
