/**
 * 账户读取（`docs/03` §8、§17；`docs/04` §6）。
 *
 * 职责边界：**只做数据访问**。默认账户的选取策略、账户名匹配规则不在本层。
 *
 * 关键约束（`docs/03` §8、`docs/08` §9）：**已禁用账户仍可读出**——历史交易引用禁用账户时
 * 必须能正常展示名称。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.AccountDao
import com.zhangran.app.data.local.entity.AccountEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

interface AccountRepository {

    /** 全部账户（**含已禁用**），按 `sortOrder` 升序。 */
    suspend fun all(): List<AccountEntity>

    /**
     * 响应式全部账户（**含已禁用**），按 `sortOrder` 升序；
     * 与 [all] 的过滤/排序一致（改账户名后列表自动更新）。
     */
    fun observeAll(): Flow<List<AccountEntity>>

    /** 当前默认账户（`docs/03` §8：全库至多一个；无默认时为 `null`）。 */
    suspend fun defaultAccount(): AccountEntity?

    /** 按名字查找（含已禁用；调用方自行决定是否排除禁用）。 */
    suspend fun findByName(name: String): AccountEntity?

    /**
     * 新增账户（`M2d-2` §4.2）。
     *
     * 本层不做业务校验：名称唯一、尾号规则、D-1（至少保留一个启用账户）都在
     * `SaveAccountUseCase`；默认账户的"先清后置"由 [setDefault] 的事务保证。
     */
    suspend fun insert(account: AccountEntity)

    /** 更新账户（名称/渠道/尾号/启用/排序；**不提供物理删除**，`06` §4.13）。 */
    suspend fun update(account: AccountEntity)

    /**
     * 设为默认账户：**同一事务**内先清除其它账户的 `isDefault` 再置位（`docs/03` §8）。
     *
     * 直接委托 `AccountDao.setAsDefault`（Room `@Transaction`）。
     */
    suspend fun setDefault(id: String, updatedAt: Long)

    /** 启用账户（保留 `isDefault` 原值）。 */
    suspend fun enable(account: AccountEntity, updatedAt: Long)

    /** 禁用账户（保留 `isDefault` 原值，D-2）。 */
    suspend fun disable(account: AccountEntity, updatedAt: Long)

    /**
     * **可用默认账户** = `isDefault && enabled`（`docs/03` §8、D-2）。
     *
     * 禁用账户仍保留 `isDefault`，因此不能用 [defaultAccount] 作为新交易的默认值。
     */
    suspend fun usableDefault(): AccountEntity?

    /** 当前启用的账户数量（D-1 判定用）。 */
    suspend fun enabledCount(): Int
}

@Singleton
class RoomAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {

    override suspend fun all(): List<AccountEntity> = dao.listAll()

    override fun observeAll(): Flow<List<AccountEntity>> = dao.observeAll()

    override suspend fun defaultAccount(): AccountEntity? = dao.findDefault()

    override suspend fun findByName(name: String): AccountEntity? =
        dao.listAll()
            .asSequence()
            .filter { it.name == name }
            .sortedWith(compareByDescending<AccountEntity> { it.enabled }.thenBy { it.sortOrder })
            .firstOrNull()

    override suspend fun insert(account: AccountEntity) = dao.insert(account)

    override suspend fun update(account: AccountEntity) = dao.update(account)

    override suspend fun setDefault(id: String, updatedAt: Long) = dao.setAsDefault(id, updatedAt)

    override suspend fun enable(account: AccountEntity, updatedAt: Long) =
        dao.update(account.copy(enabled = true, updatedAt = updatedAt))

    override suspend fun disable(account: AccountEntity, updatedAt: Long) =
        dao.update(account.copy(enabled = false, updatedAt = updatedAt))

    override suspend fun usableDefault(): AccountEntity? = dao.findDefault()?.takeIf { it.enabled }

    override suspend fun enabledCount(): Int = dao.listEnabled().size
}
