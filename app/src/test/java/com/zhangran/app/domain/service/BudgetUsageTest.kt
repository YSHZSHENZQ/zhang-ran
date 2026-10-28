/**
 * `BudgetUsage` 口径测试（`M4a` §4.2 / 验收 A2–A7、A14）。
 *
 * 每条判据一个测试方法，逐项对应任务书 §1.1 的规则表。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val FOOD = "cat-food"
private const val TRAFFIC = "cat-traffic"

private fun tx(
    type: TransactionType,
    amountMinor: Long,
    categoryId: String? = null,
    state: TransactionState = TransactionState.CONFIRMED,
    deletedAt: Long? = null,
): TransactionEntity = TransactionEntity(
    id = UUID.randomUUID().toString(),
    type = type,
    amountMinor = amountMinor,
    currency = Currency.CNY,
    merchant = null,
    categoryId = categoryId,
    accountId = "acc-cash",
    transactionTime = 1_791_000_000_000L,
    transactionTimeZone = "Asia/Shanghai",
    note = null,
    state = state,
    source = CaptureSource.MANUAL,
    confirmedByUser = false,
    createdAt = 1_791_000_000_000L,
    updatedAt = 1_791_000_000_000L,
    deletedAt = deletedAt,
)

class BudgetUsageTest {

    @Test
    fun A2_已用为支出减退款的净额而非原始合计() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.REFUND, 30_000L, FOOD),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = mapOf(FOOD to 150_000L),
        )

        assertEquals("总额 = 1000 - 300", 70_000L, usage.totalUsedMinor)
        assertEquals("分类 = 1000 - 300", 70_000L, usage.categories.single().usedMinor)
        assertEquals(80_000L, usage.categories.single().remainingMinor)
        assertFalse(usage.categories.single().isOverBudget)
    }

    @Test
    fun A3_Pending交易不计入已用() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.EXPENSE, 50_000L, FOOD, state = TransactionState.PENDING),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = mapOf(FOOD to 150_000L),
        )

        assertEquals(100_000L, usage.totalUsedMinor)
        assertEquals(100_000L, usage.categories.single().usedMinor)
    }

    @Test
    fun A4_软删除交易不计入等价于回滚占用() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.EXPENSE, 50_000L, FOOD, deletedAt = 1_791_000_100_000L),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = mapOf(FOOD to 150_000L),
        )

        assertEquals(100_000L, usage.totalUsedMinor)
        assertEquals(100_000L, usage.categories.single().usedMinor)
    }

    @Test
    fun A5_转账与收入不计入任何预算() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.TRANSFER, 80_000L),
                tx(TransactionType.INCOME, 200_000L, "cat-salary"),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = mapOf(FOOD to 150_000L),
        )

        assertEquals(100_000L, usage.totalUsedMinor)
        assertEquals(100_000L, usage.categories.single().usedMinor)
    }

    @Test
    fun A6_退款分类为空时只冲减总额不影响分类() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.REFUND, 40_000L, categoryId = null),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = mapOf(FOOD to 150_000L),
        )

        assertEquals("总额 = 1000 - 400", 60_000L, usage.totalUsedMinor)
        assertEquals("分类不受无分类退款影响", 100_000L, usage.categories.single().usedMinor)
    }

    @Test
    fun A7_分类预算只统计该分类不混入其他分类() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.EXPENSE, 50_000L, TRAFFIC),
                tx(TransactionType.REFUND, 20_000L, TRAFFIC),
            ),
            totalLimitMinor = 200_000L,
            categoryLimits = linkedMapOf(FOOD to 150_000L, TRAFFIC to 60_000L),
        )

        val food = usage.categories.first { it.categoryId == FOOD }
        val traffic = usage.categories.first { it.categoryId == TRAFFIC }
        assertEquals(100_000L, food.usedMinor)
        assertEquals("交通 = 500 - 200", 30_000L, traffic.usedMinor)
        assertEquals("总额 = 1000 + 500 - 200", 130_000L, usage.totalUsedMinor)
    }

    @Test
    fun A14_已用为负时允许负值且剩余大于额度() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 10_000L, FOOD),
                tx(TransactionType.REFUND, 50_000L, FOOD),
            ),
            totalLimitMinor = 100_000L,
            categoryLimits = mapOf(FOOD to 100_000L),
        )

        assertEquals(-40_000L, usage.totalUsedMinor)
        val food = usage.categories.single()
        assertEquals(-40_000L, food.usedMinor)
        assertEquals("remaining = limit - used = 1000 - (-400)", 140_000L, food.remainingMinor)
        assertFalse(food.isOverBudget)
    }

    @Test
    fun 超支判定为已用大于额度且恰好相等不算超支() {
        val equal = calculateBudgetUsage(
            transactions = listOf(tx(TransactionType.EXPENSE, 100_000L, FOOD)),
            totalLimitMinor = 100_000L,
            categoryLimits = mapOf(FOOD to 100_000L),
        )
        assertFalse(equal.categories.single().isOverBudget)

        val over = calculateBudgetUsage(
            transactions = listOf(tx(TransactionType.EXPENSE, 120_000L, FOOD)),
            totalLimitMinor = 100_000L,
            categoryLimits = mapOf(FOOD to 100_000L),
        )
        assertTrue(over.categories.single().isOverBudget)
        assertEquals(-20_000L, over.categories.single().remainingMinor)
    }

    @Test
    fun 未设置总预算时仍计算已用且totalLimit为null() {
        val usage = calculateBudgetUsage(
            transactions = listOf(tx(TransactionType.EXPENSE, 100_000L, FOOD)),
            totalLimitMinor = null,
            categoryLimits = emptyMap(),
        )

        assertEquals(100_000L, usage.totalUsedMinor)
        assertEquals(null, usage.totalLimitMinor)
        assertTrue(usage.categories.isEmpty())
    }

    @Test
    fun 其他分类的退款不影响本分类() {
        val usage = calculateBudgetUsage(
            transactions = listOf(
                tx(TransactionType.EXPENSE, 100_000L, FOOD),
                tx(TransactionType.REFUND, 30_000L, TRAFFIC),
            ),
            totalLimitMinor = null,
            categoryLimits = mapOf(FOOD to 100_000L),
        )

        assertEquals(100_000L, usage.categories.single().usedMinor)
        assertEquals(70_000L, usage.totalUsedMinor)
    }

    @Test
    fun 分类顺序与传入的分类额度顺序一致() {
        val usage = calculateBudgetUsage(
            transactions = emptyList(),
            totalLimitMinor = null,
            categoryLimits = linkedMapOf(TRAFFIC to 1L, FOOD to 2L),
        )
        assertEquals(listOf(TRAFFIC, FOOD), usage.categories.map { it.categoryId })
    }
}
