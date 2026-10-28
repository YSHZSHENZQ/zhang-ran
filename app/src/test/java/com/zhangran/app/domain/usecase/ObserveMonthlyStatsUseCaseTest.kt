/**
 * `ObserveMonthlyStatsUseCase` 测试（`M2e-2` §4.3 / 验收 A10、A11、A15 的取数部分）。
 *
 * 覆盖：指定月份取数、默认当前月、非法 periodKey、空月、可选月份范围（不含未来）、
 * 分类名解析（`null` → 其他）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ZONE = "Asia/Shanghai"
private val ZONE_ID: ZoneId = ZoneId.of(ZONE)

private fun epoch(y: Int, m: Int, d: Int): Long =
    LocalDate.of(y, m, d).atTime(12, 0).atZone(ZONE_ID).toInstant().toEpochMilli()

private fun tx(
    type: TransactionType,
    amountMinor: Long,
    categoryId: String? = null,
    year: Int = 2026,
    month: Int = 10,
    day: Int = 5,
): TransactionEntity = TransactionEntity(
    id = UUID.randomUUID().toString(),
    type = type,
    amountMinor = amountMinor,
    currency = Currency.CNY,
    merchant = null,
    categoryId = categoryId,
    accountId = "acc-cash",
    transactionTime = epoch(year, month, day),
    transactionTimeZone = ZONE,
    note = null,
    state = TransactionState.CONFIRMED,
    source = CaptureSource.MANUAL,
    confirmedByUser = false,
    createdAt = epoch(year, month, day),
    updatedAt = epoch(year, month, day),
)

private class StatsFakeTransactionRepository(initial: List<TransactionEntity>) : TransactionRepository {
    val rows = MutableStateFlow(initial)

    override suspend fun insert(transaction: TransactionEntity) {
        rows.value = rows.value + transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        rows.value.filter { it.state == state && it.deletedAt == null }

    override suspend fun listVisible(): List<TransactionEntity> =
        rows.value.filter { it.deletedAt == null }

    override fun observeVisible(): Flow<List<TransactionEntity>> = flowOf(rows.value)

    override suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity> =
        rows.value.filter {
            it.state == TransactionState.CONFIRMED &&
                it.deletedAt == null &&
                it.transactionTime in startInclusive until endExclusive
        }

    override suspend fun countByState(state: TransactionState): Int =
        rows.value.count { it.state == state && it.deletedAt == null }

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        rows.value.firstOrNull { it.id == id && (includeDeleted || it.deletedAt == null) }

    override suspend fun update(transaction: TransactionEntity) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun softDelete(id: String, deletedAt: Long) = Unit

    override suspend fun restore(id: String, updatedAt: Long) = Unit

    override suspend fun search(
        criteria: com.zhangran.app.domain.model.SearchCriteria,
    ): List<TransactionEntity> = emptyList()

    override suspend fun countSearch(
        criteria: com.zhangran.app.domain.model.SearchCriteria,
    ): Int = 0
}

private class StatsFakeCategoryRepository(private val items: List<CategoryEntity>) : CategoryRepository {
    override suspend fun all(): List<CategoryEntity> = items
    override fun observeAll(): Flow<List<CategoryEntity>> = flowOf(items)
    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        items.filter { it.type == type && it.enabled }
    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        items.firstOrNull { it.type == type && it.name == name }
    override suspend fun insert(category: CategoryEntity) = Unit
    override suspend fun update(category: CategoryEntity) = Unit
}

private val CATEGORIES = listOf(
    CategoryEntity(
        id = "cat-food",
        name = "餐饮",
        type = CategoryType.EXPENSE,
        icon = "food",
        sortOrder = 0,
        enabled = true,
        systemDefined = true,
        createdAt = 1L,
        updatedAt = 1L,
    ),
)

private fun clockAt(y: Int, m: Int, d: Int): Clock =
    Clock.fixed(Instant.ofEpochMilli(epoch(y, m, d)), ZONE_ID)

class ObserveMonthlyStatsUseCaseTest {

    private fun useCase(
        transactions: List<TransactionEntity> = emptyList(),
        clock: Clock = clockAt(2026, 10, 15),
    ) = ObserveMonthlyStatsUseCase(
        transactions = StatsFakeTransactionRepository(transactions),
        categories = StatsFakeCategoryRepository(CATEGORIES),
        clock = clock,
    )

    @Test
    fun 指定periodKey时只统计该月() = runBlocking {
        val useCase = useCase(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L, "cat-food", month = 10),
                tx(TransactionType.EXPENSE, 99_000L, "cat-food", month = 9),
            ),
        )

        val sep = useCase("2026-09")
        assertEquals("2026-09", sep.periodKey)
        assertEquals(99_000L, sep.expenseMinor)

        val oct = useCase("2026-10")
        assertEquals(10_000L, oct.expenseMinor)
    }

    @Test
    fun 不传月份时使用设备时区当前月() = runBlocking {
        val useCase = useCase(emptyList(), clockAt(2026, 10, 15))

        val stats = useCase()
        assertEquals("2026-10", stats.periodKey)
        assertEquals(2026, stats.year)
        assertEquals(10, stats.month)
    }

    @Test
    fun 非法periodKey抛非法参数() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase().invoke("2026-13") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase().invoke("not-a-month") }
        }
    }

    @Test
    fun 空月返回hasData为false且指标为0() = runBlocking {
        val stats = useCase(emptyList()).invoke("2026-08")

        assertFalse(stats.hasData)
        assertEquals(0L, stats.expenseMinor)
        assertEquals(0L, stats.dailyAverageMinor)
        assertEquals(0L, stats.maxDailyExpenseMinor)
        assertEquals(0, stats.recordCount)
        assertTrue(stats.categoryBreakdown.isEmpty())
    }

    @Test
    fun 分类名由分类表解析且空分类归为其他() = runBlocking {
        val stats = useCase(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L, "cat-food"),
                tx(TransactionType.EXPENSE, 5_000L, null),
            ),
        ).invoke("2026-10")

        assertEquals(listOf("餐饮", "其他"), stats.categoryBreakdown.map { it.categoryName })
    }

    @Test
    fun 可选月份从最早记录月到当前月且不含未来() = runBlocking {
        val useCase = useCase(
            listOf(
                tx(TransactionType.EXPENSE, 1_000L, "cat-food", year = 2026, month = 8),
            ),
            clockAt(2026, 10, 15),
        )

        val months = useCase.availableMonths()
        assertEquals(listOf("2026-10", "2026-09", "2026-08"), months.map { it.periodKey })
        assertTrue("不得包含未来月", months.all { it.periodKey <= "2026-10" })
        assertEquals("2026 年 10 月", months.first().displayText)
    }

    @Test
    fun 无记录时可选月份只含当前月() = runBlocking {
        val months = useCase(emptyList(), clockAt(2026, 10, 15)).availableMonths()
        assertEquals(listOf("2026-10"), months.map { it.periodKey })
    }

    @Test
    fun 当前periodKey来自时钟() {
        assertEquals("2026-10", useCase(clock = clockAt(2026, 10, 15)).currentPeriodKey())
    }
}
