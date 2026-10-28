/**
 * 预算使用量计算（`M4a` §4.2；口径 SSOT：`docs/02` §12/§14、`docs/03` §9）。
 *
 * **纯函数**：不访问 DAO、不读系统时间、不做月份边界判断（调用方用
 * `TransactionTime.monthRangeUtc` 查好当月的已确认交易再传入）。
 *
 * 口径：
 * - 只计 `CONFIRMED`（`PENDING` 不占额度；软删除不计 → 等价于删除回滚占用）；
 * - `TRANSFER` 不计入任何预算；
 * - `REFUND` 释放"自己的分类 + 总额"：`categoryId` 匹配时同时冲减该分类与总额；
 * - `REFUND.categoryId == null` 时**只冲减总额**，不影响任何分类预算；
 * - 已用为负（退款多于消费）时**允许负值**，不得 `coerceAtLeast(0)`（A14）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType

/**
 * 某分类的预算使用情况。
 *
 * @property usedMinor 已用（净额，可能为负）
 * @property remainingMinor `limit - used`（可能大于 limit，也可能为负）
 * @property isOverBudget `used > limit`（仅视觉提示，不阻止记账）
 */
data class CategoryBudgetUsage(
    val categoryId: String,
    val usedMinor: Long,
    val limitMinor: Long,
    val remainingMinor: Long,
    val isOverBudget: Boolean,
)

/**
 * 某月的预算使用情况。
 *
 * @property totalLimitMinor `null` = 未设置总预算（此时 [totalUsedMinor] 仍然计算，供页面提示）
 */
data class BudgetUsage(
    val totalUsedMinor: Long,
    val totalLimitMinor: Long?,
    val categories: List<CategoryBudgetUsage>,
)

/**
 * 按 §1.1 的算法计算使用量。
 *
 * @param transactions 该月的**已确认**交易（`listInRange` 的返回值；这里再做一次防御性过滤）
 * @param totalLimitMinor 总预算额度（`null` 表示未设置）
 * @param categoryLimits 分类预算额度：`categoryId -> limitMinor`（保持传入顺序）
 */
fun calculateBudgetUsage(
    transactions: List<TransactionEntity>,
    totalLimitMinor: Long?,
    categoryLimits: Map<String, Long>,
): BudgetUsage {
    val eligible = transactions.filter {
        it.state == TransactionState.CONFIRMED && it.deletedAt == null
    }

    fun expenseOf(categoryId: String?): Long = eligible
        .asSequence()
        .filter { it.type == TransactionType.EXPENSE }
        .filter { categoryId == null || it.categoryId == categoryId }
        .sumOf { it.amountMinor }

    fun refundOf(categoryId: String?): Long = eligible
        .asSequence()
        .filter { it.type == TransactionType.REFUND }
        .filter { categoryId == null || it.categoryId == categoryId }
        .sumOf { it.amountMinor }

    val totalUsed = expenseOf(null) - refundOf(null)

    val categories = categoryLimits.map { (categoryId, limitMinor) ->
        val used = expenseOf(categoryId) - refundOf(categoryId)
        CategoryBudgetUsage(
            categoryId = categoryId,
            usedMinor = used,
            limitMinor = limitMinor,
            remainingMinor = limitMinor - used,
            isOverBudget = used > limitMinor,
        )
    }

    return BudgetUsage(
        totalUsedMinor = totalUsed,
        totalLimitMinor = totalLimitMinor,
        categories = categories,
    )
}
