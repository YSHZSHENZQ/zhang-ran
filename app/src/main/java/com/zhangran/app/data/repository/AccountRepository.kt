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

interface AccountRepository {

    /** 全部账户（**含已禁用**），按 `sortOrder` 升序。 */
    suspend fun all(): List<AccountEntity>

    /** 当前默认账户（`docs/03` §8：全库至多一个；无默认时为 `null`）。 */
    suspend fun defaultAccount(): AccountEntity?

    /** 按名字查找（含已禁用；调用方自行决定是否排除禁用）。 */
    suspend fun findByName(name: String): AccountEntity?
}

@Singleton
class RoomAccountRepository @Inject constructor(
    private val dao: AccountDao,
) : AccountRepository {

    override suspend fun all(): List<AccountEntity> = dao.listAll()

    override suspend fun defaultAccount(): AccountEntity? = dao.findDefault()

    override suspend fun findByName(name: String): AccountEntity? =
        dao.listAll()
            .asSequence()
            .filter { it.name == name }
            .sortedWith(compareByDescending<AccountEntity> { it.enabled }.thenBy { it.sortOrder })
            .firstOrNull()
}
