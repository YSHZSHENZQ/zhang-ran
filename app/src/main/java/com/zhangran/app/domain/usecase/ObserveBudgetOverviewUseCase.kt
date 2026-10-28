/**
 * 当前月预算概览（`M4a` §4.3）。
 *
 * 刷新方式：**一次性 `suspend` + 页面 `refresh()`**（任务书 §4.3 允许二选一）。
 * 理由见交付说明：预算/交易都已有各自的读取路径，本页写入后立即刷新即可；
 * 不做 Flow 组合可避免为预算新增 `observe*` 查询（`BudgetDao` 无响应式预算查询，
 * 而 `TransactionDao` 的响应式列表不带区间参数），改动面更小。
 *
 * 月份取**设备时区**当前月（A15），月边界复用 `TransactionTime.monthRangeUtc`（M2e-1a）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.BudgetRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.calculateBudgetUsage
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * 分类预算的展示数据（含分类名与图标标识）。
 *
 * @property budgetId 预算 id（编辑/删除用）
 * @property usedMinor 该分类当月已用净额（可能为负）
 */
data class CategoryBudgetOverview(
    val budgetId: String,
    val categoryId: String,
    val categoryName: String,
    val categoryIcon: String,
    val limitMinor: Long,
    val usedMinor: Long,
)

/**
 * 当前月预算概览。
 *
 * @property periodKey 形如 `2026-10`（设备时区）
 * @property totalBudgetId `null` = 未设置总预算
 * @property totalLimitMinor `null` = 未设置总预算
 * @property totalUsedMinor 当月净消费（EXPENSE − REFUND；与是否设预算无关）
 */
data class BudgetOverview(
    val periodKey: String,
    val totalBudgetId: String?,
    val totalLimitMinor: Long?,
    val totalUsedMinor: Long,
    val categories: List<CategoryBudgetOverview>,
) {
    /** 未设置任何预算（总额与分类都为空）→ 页面 Empty（A16）。 */
    val isEmpty: Boolean get() = totalLimitMinor == null && categories.isEmpty()
}

/**
 * @param budgets 预算读取
 * @param transactions 当月交易（`listInRange` 已排除非 CONFIRMED 与软删除）
 * @param categories 分类（名称/图标/排序）
 * @param clock 时间源（测试注入固定月份）
 */
class ObserveBudgetOverviewUseCase @Inject constructor(
    private val budgets: BudgetRepository,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(): BudgetOverview {
        val now = Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.systemDefault())
        val periodKey = "%04d-%02d".format(now.year, now.monthValue)
        val range = TransactionTime.monthRangeUtc(now.zone.id, now.year, now.monthValue)

        val monthTransactions = transactions.listInRange(range.startInclusive, range.endExclusive)
        val rows = budgets.listByPeriodKey(periodKey).filter { it.enabled }
        val totalRow = rows.firstOrNull { it.scope == BudgetScope.TOTAL }
        val categoryRows = rows.filter { it.scope == BudgetScope.CATEGORY && it.categoryId != null }

        val usage = calculateBudgetUsage(
            transactions = monthTransactions,
            totalLimitMinor = totalRow?.limitMinor,
            categoryLimits = categoryRows.associate { it.categoryId!! to it.limitMinor },
        )

        val categoryById = categories.all().associateBy { it.id }
        val categoryOverviews = categoryRows
            .sortedWith(
                compareBy(
                    { categoryById[it.categoryId]?.sortOrder ?: Int.MAX_VALUE },
                    { categoryById[it.categoryId]?.name ?: "" },
                ),
            )
            .mapNotNull { row ->
                val category = categoryById[row.categoryId] ?: return@mapNotNull null
                val used = usage.categories.firstOrNull { it.categoryId == row.categoryId }?.usedMinor ?: 0L
                CategoryBudgetOverview(
                    budgetId = row.id,
                    categoryId = category.id,
                    categoryName = category.name,
                    categoryIcon = category.icon,
                    limitMinor = row.limitMinor,
                    usedMinor = used,
                )
            }

        return BudgetOverview(
            periodKey = periodKey,
            totalBudgetId = totalRow?.id,
            totalLimitMinor = totalRow?.limitMinor,
            totalUsedMinor = usage.totalUsedMinor,
            categories = categoryOverviews,
        )
    }
}
