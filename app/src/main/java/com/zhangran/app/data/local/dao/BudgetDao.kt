/**
 * 预算表 DAO（`budgets`）。
 *
 * 规则（`docs/03` §9）：
 * - 总预算每个 `periodKey` 唯一；分类预算每个 `periodKey + categoryId` 唯一；
 * - 总预算与分类预算互相独立，不做叠加校验。
 *
 * 唯一性实现：
 * - 分类预算由库级唯一索引 `(periodKey, categoryId)` 保证（`categoryId` 必填）；
 * - 总预算的 `categoryId` 为 NULL，而 SQLite 视 NULL 互不相等，唯一索引无法覆盖，
 *   因此由应用层在同一事务内保证：见 [insertTotalBudgetIfAbsent]。
 *   `deleteByPeriodKey` 等物理删除接口**不对外暴露**（预算删除语义属 Domain）。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.domain.model.BudgetScope

@Dao
interface BudgetDao {

    /** 分类预算插入：由库级唯一索引 `(periodKey, categoryId)` 拦截重复。 */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(budget: BudgetEntity)

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun findById(id: String): BudgetEntity?

    /** 默认列表：只含启用预算。 */
    @Query("SELECT * FROM budgets WHERE enabled = 1 ORDER BY periodKey DESC")
    suspend fun listEnabled(): List<BudgetEntity>

    @Query("SELECT * FROM budgets ORDER BY periodKey DESC")
    suspend fun listAll(): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE periodKey = :periodKey ORDER BY scope ASC")
    suspend fun listByPeriodKey(periodKey: String): List<BudgetEntity>

    /** 指定月份的总预算（至多一条；应用层保证）。 */
    @Query("SELECT * FROM budgets WHERE periodKey = :periodKey AND scope = 'TOTAL' LIMIT 1")
    suspend fun findTotalByPeriodKey(periodKey: String): BudgetEntity?

    /** 指定月份的总预算条数（用于验收 A5 与自检）。 */
    @Query("SELECT COUNT(*) FROM budgets WHERE periodKey = :periodKey AND scope = 'TOTAL'")
    suspend fun countTotalByPeriodKey(periodKey: String): Int

    /**
     * 物理删除预算（`M4a` §4.4.1：总预算与分类预算都可删除，删除后回到"未设置"）。
     *
     * 预算只是目标值，删除**不影响**交易与统计。
     */
    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * 插入总预算（应用层事务保证"每个 periodKey 至多一条"）。
     *
     * 若该月份已有总预算则**不插入**并返回 `false`，调用方据此判断冲突。
     */
    @Transaction
    suspend fun insertTotalBudgetIfAbsent(budget: BudgetEntity): Boolean {
        require(budget.scope == BudgetScope.TOTAL) { "本方法只用于 TOTAL 预算" }
        require(budget.categoryId == null) { "TOTAL 预算的 categoryId 必须为空（docs/03 §9）" }
        if (countTotalByPeriodKey(budget.periodKey) > 0) return false
        insert(budget)
        return true
    }
}
