/**
 * 预算读写（`M4a` §4.3；字段与规则 SSOT：`docs/03` §9）。
 *
 * 职责边界：**只做数据访问**。使用量口径（`BudgetUsage`）、唯一性收敛（更新而非新增）、
 * 删除语义都在 UseCase 层；本层不解释金额。
 *
 * 注意：`Budgets` 的 `(periodKey, categoryId)` 有库级唯一索引；但 SQLite 把 NULL 视为互不相等，
 * 因此 **TOTAL 的唯一性**（`categoryId = NULL`）无法由该索引表达，必须经
 * [insertTotalIfAbsent] / 先查后写保证（`docs/03` §9）。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.BudgetDao
import com.zhangran.app.data.local.entity.BudgetEntity
import javax.inject.Inject
import javax.inject.Singleton

interface BudgetRepository {

    /** 某月的全部预算（含禁用；按 `scope` 升序）。 */
    suspend fun listByPeriodKey(periodKey: String): List<BudgetEntity>

    suspend fun findById(id: String): BudgetEntity?

    /** 某月的总预算（至多一条）。 */
    suspend fun findTotal(periodKey: String): BudgetEntity?

    /**
     * 插入总预算（事务内"存在则不插"）。
     *
     * @return `true` 表示插入成功；`false` 表示已存在（调用方改为更新）
     */
    suspend fun insertTotalIfAbsent(budget: BudgetEntity): Boolean

    suspend fun insert(budget: BudgetEntity)

    suspend fun update(budget: BudgetEntity)

    /** 物理删除（`M4a` §4.4.1；预算与账务解耦，不影响交易/统计）。 */
    suspend fun delete(id: String)
}

@Singleton
class RoomBudgetRepository @Inject constructor(
    private val dao: BudgetDao,
) : BudgetRepository {

    override suspend fun listByPeriodKey(periodKey: String): List<BudgetEntity> =
        dao.listByPeriodKey(periodKey)

    override suspend fun findById(id: String): BudgetEntity? = dao.findById(id)

    override suspend fun findTotal(periodKey: String): BudgetEntity? =
        dao.findTotalByPeriodKey(periodKey)

    override suspend fun insertTotalIfAbsent(budget: BudgetEntity): Boolean =
        dao.insertTotalBudgetIfAbsent(budget)

    override suspend fun insert(budget: BudgetEntity) = dao.insert(budget)

    override suspend fun update(budget: BudgetEntity) = dao.update(budget)

    override suspend fun delete(id: String) = dao.deleteById(id)
}
