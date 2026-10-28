/**
 * 月度统计聚合口径测试（`M2e-2` §4.2 / 验收 A1–A9、A11）。
 *
 * 覆盖：日均除以自然天数、最高单日净额、笔数口径、占比前 4 + 其他、
 * 空/负消费、PENDING/软删除/TRANSFER 排除、周切分（周一为起点、跨月占位）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.CategoryShare
import com.zhangran.app.domain.model.DailyPoint
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.model.WeekTrend
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ZONE = "Asia/Shanghai"
private val ZONE_ID: ZoneId = ZoneId.of(ZONE)

private fun epoch(y: Int, m: Int, d: Int, hour: Int = 12): Long =
    LocalDate.of(y, m, d).atTime(hour, 0).atZone(ZONE_ID).toInstant().toEpochMilli()

private fun tx(
    type: TransactionType,
    amountMinor: Long,
    categoryId: String? = null,
    day: Int = 1,
    month: Int = 10,
    year: Int = 2026,
    state: TransactionState = TransactionState.CONFIRMED,
    deletedAt: Long? = null,
): TransactionEntity = TransactionEntity(
    id = UUID.randomUUID().toString(),
    type = type,
    amountMinor = amountMinor,
    currency = com.zhangran.app.domain.model.Currency.CNY,
    merchant = null,
    categoryId = categoryId,
    accountId = "acc-cash",
    transactionTime = epoch(year, month, day),
    transactionTimeZone = ZONE,
    note = null,
    state = state,
    source = com.zhangran.app.domain.model.CaptureSource.MANUAL,
    confirmedByUser = false,
    createdAt = epoch(year, month, day),
    updatedAt = epoch(year, month, day),
    deletedAt = deletedAt,
)

private fun stats(
    transactions: List<TransactionEntity>,
    year: Int = 2026,
    month: Int = 10,
): MonthlyStats = calculateMonthlyStats(
    periodKey = "%04d-%02d".format(year, month),
    year = year,
    month = month,
    transactions = transactions,
    zoneId = ZONE,
    categoryNameOf = { id -> id ?: "其他" },
)

class MonthlyStatsCalculatorTest {

    // ---------------------------------------------------------------- A2 日均

    @Test
    fun A2_日均按当月自然天数向下取整() {
        // 2026-02 是 28 天
        val feb = stats(listOf(tx(TransactionType.EXPENSE, 100_000L, day = 3, month = 2)), month = 2)
        assertEquals(28, feb.daysInMonth)
        assertEquals(3_571L, feb.dailyAverageMinor)

        // 2026-04 是 30 天
        val apr = stats(listOf(tx(TransactionType.EXPENSE, 100_000L, day = 3, month = 4)), month = 4)
        assertEquals(30, apr.daysInMonth)
        assertEquals(3_333L, apr.dailyAverageMinor)

        // 2026-01 是 31 天
        val jan = stats(listOf(tx(TransactionType.EXPENSE, 100_000L, day = 3, month = 1)), month = 1)
        assertEquals(31, jan.daysInMonth)
        assertEquals(3_225L, jan.dailyAverageMinor)
    }

    @Test
    fun A2_无数据时日均与最高单日和笔数都是0() {
        val empty = stats(emptyList())

        assertEquals(0L, empty.expenseMinor)
        assertEquals(0L, empty.dailyAverageMinor)
        assertEquals(0L, empty.maxDailyExpenseMinor)
        assertEquals(0, empty.recordCount)
        assertFalse(empty.hasData)
    }

    // ---------------------------------------------------------------- A3 最高单日

    @Test
    fun A3_最高单日为单日净额最大值且退款冲减当日() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 100_000L, day = 5),
                tx(TransactionType.REFUND, 30_000L, day = 5),
                tx(TransactionType.EXPENSE, 20_000L, day = 6),
            ),
        )

        // 5 日净额 700.00，6 日 200.00
        assertEquals(70_000L, result.maxDailyExpenseMinor)
        assertEquals(90_000L, result.expenseMinor)
    }

    // ---------------------------------------------------------------- A4 笔数

    @Test
    fun A4_笔数只含支出与收入不含退款与转账() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L),
                tx(TransactionType.EXPENSE, 20_000L),
                tx(TransactionType.INCOME, 30_000L),
                tx(TransactionType.REFUND, 5_000L),
                tx(TransactionType.TRANSFER, 99_000L),
            ),
        )

        assertEquals("支出 2 + 收入 1", 3, result.recordCount)
        assertEquals("支出 300 − 退款 50", 25_000L, result.expenseMinor)
        assertEquals(30_000L, result.incomeMinor)
        assertEquals("结余 = 收入 − 消费", 5_000L, result.balanceMinor)
    }

    @Test
    fun A3_A4_退款不加收入也不占笔数() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 100_000L),
                tx(TransactionType.REFUND, 40_000L),
            ),
        )

        assertEquals(60_000L, result.expenseMinor)
        assertEquals(0L, result.incomeMinor)
        assertEquals("退款不计笔数", 1, result.recordCount)
    }

    // ---------------------------------------------------------------- A5 占比

    @Test
    fun A5_占比取前4项其余汇合为其他() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 50_000L, "cat-a"),
                tx(TransactionType.EXPENSE, 30_000L, "cat-b"),
                tx(TransactionType.EXPENSE, 15_000L, "cat-c"),
                tx(TransactionType.EXPENSE, 3_000L, "cat-d"),
                tx(TransactionType.EXPENSE, 2_000L, "cat-e"),
            ),
        )

        assertEquals("前 4 + 汇合的「其他」", 5, result.categoryBreakdown.size)
        assertEquals(
            listOf("cat-a", "cat-b", "cat-c", "cat-d", "其他"),
            result.categoryBreakdown.map { it.categoryName },
        )
        val fallback = result.categoryBreakdown.last()
        assertEquals(2_000L, fallback.amountMinor)
        assertEquals(2.0, fallback.percent, 0.0001)
    }

    @Test
    fun A5_分类数不超过4时不出现其他() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 40_000L, "cat-a"),
                tx(TransactionType.EXPENSE, 30_000L, "cat-b"),
                tx(TransactionType.EXPENSE, 20_000L, "cat-c"),
                tx(TransactionType.EXPENSE, 10_000L, "cat-d"),
            ),
        )

        assertEquals(4, result.categoryBreakdown.size)
        assertTrue(result.categoryBreakdown.none { it.categoryName == "其他" })
        assertEquals(40.0, result.categoryBreakdown.first().percent, 0.0001)
    }

    @Test
    fun A5_消费净额不大于0时占比列表为空() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L, "cat-a"),
                tx(TransactionType.REFUND, 20_000L, "cat-a"),
            ),
        )

        assertEquals(-10_000L, result.expenseMinor)
        assertTrue(result.categoryBreakdown.isEmpty())
    }

    // ---------------------------------------------------------------- A6 排除项

    @Test
    fun A6_Pending与软删除不计入任何统计() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L, "cat-a"),
                tx(TransactionType.EXPENSE, 99_000L, "cat-a", state = TransactionState.PENDING),
                tx(TransactionType.EXPENSE, 88_000L, "cat-a", deletedAt = 1L),
                tx(TransactionType.INCOME, 77_000L, state = TransactionState.PENDING),
            ),
        )

        assertEquals(10_000L, result.expenseMinor)
        assertEquals(0L, result.incomeMinor)
        assertEquals(1, result.recordCount)
        assertEquals(10_000L, result.maxDailyExpenseMinor)
    }

    @Test
    fun A6_占比按净额计算() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 100_000L, "cat-a"),
                tx(TransactionType.REFUND, 20_000L, "cat-a"),
                tx(TransactionType.EXPENSE, 40_000L, "cat-b"),
            ),
        )

        // 净额：a=800, b=400，总 1200 → 66.67% / 33.33%
        val a = result.categoryBreakdown.first { it.categoryName == "cat-a" }
        assertEquals(80_000L, a.amountMinor)
        assertEquals(80_000.0 / 120_000.0 * 100.0, a.percent, 0.0001)
    }

    @Test
    fun A6_分类为空的退款只冲减总额不进入分类占比() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 100_000L, "cat-a"),
                tx(TransactionType.REFUND, 30_000L, null),
            ),
        )

        assertEquals(70_000L, result.expenseMinor)
        val a = result.categoryBreakdown.first { it.categoryName == "cat-a" }
        assertEquals("分类不被无分类退款冲减", 100_000L, a.amountMinor)
    }

    // ---------------------------------------------------------------- A7/A9 周切分

    @Test
    fun A7_每周固定7点且周一为起点() {
        val result = stats(listOf(tx(TransactionType.EXPENSE, 10_000L, day = 8)))

        val week = result.weeks.first { week -> week.points.any { it.dayOfMonth == 8 } }
        assertEquals(7, week.points.size)
        assertEquals((0..6).toList(), week.points.map { it.weekdayIndex })
        // 2026-10-08 是周四 → weekdayIndex 3（该周为 10/5–10/11）
        assertEquals(10_000L, week.points[3].amountMinor)
    }

    @Test
    fun A9_跨月周只保留属于本月天数其余为0() {
        // 2026-09-01 是周二 → 首周从 8/31（周一）开始
        val result = stats(emptyList(), month = 9)

        val firstWeek = result.weeks.first()
        assertEquals(7, firstWeek.points.size)
        assertEquals("8/31 不属于 9 月", 0, firstWeek.points[0].dayOfMonth)
        assertEquals(0L, firstWeek.points[0].amountMinor)
        assertEquals(1, firstWeek.points[1].dayOfMonth)

        val lastWeek = result.weeks.last()
        // 9 月 30 天：9/28 周一 → 最后一周 9/28..10/4
        assertEquals(28, lastWeek.points[0].dayOfMonth)
        assertEquals(30, lastWeek.points[2].dayOfMonth)
        assertEquals("10/1 不属于 9 月", 0, lastWeek.points[3].dayOfMonth)
        assertEquals(0L, lastWeek.points[3].amountMinor)
    }

    @Test
    fun A9_周数与月首月尾不足7天补齐() {
        // 2026-02-01 是周日 → 首周只有 2/1 属于本月
        val result = stats(emptyList(), month = 2)
        assertEquals(7, result.weeks.first().points.size)
        assertEquals(0, result.weeks.first().points[0].dayOfMonth)
        assertEquals(1, result.weeks.first().points[6].dayOfMonth)
        assertTrue(result.weeks.all { it.points.size == 7 })
    }

    // ---------------------------------------------------------------- A11 负值

    @Test
    fun A11_净额为负保留负值不钳制() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L),
                tx(TransactionType.REFUND, 50_000L),
            ),
        )

        assertEquals(-40_000L, result.expenseMinor)
        assertEquals("结余 = 收入 − 消费 = 0 − (−400)", 40_000L, result.balanceMinor)
        assertEquals(-40_000L, result.maxDailyExpenseMinor)
        assertEquals(-1_291L, result.dailyAverageMinor) // floor(-40000/31)
        assertTrue(result.categoryBreakdown.isEmpty())
    }

    @Test
    fun 每天净额合计等于当月消费净额() {
        val result = stats(
            listOf(
                tx(TransactionType.EXPENSE, 10_000L, day = 2),
                tx(TransactionType.EXPENSE, 20_000L, day = 3),
                tx(TransactionType.REFUND, 5_000L, day = 3),
            ),
        )
        val weekTotal = result.weeks.sumOf { week -> week.points.sumOf { it.amountMinor } }
        assertEquals(result.expenseMinor, weekTotal)
    }

    private fun CategoryShare.percentIs(expected: Double) =
        assertEquals(expected, percent, 0.0001)

    @Test
    fun 占比是Domain提供的Double不要求UI再算() {
        val result = stats(listOf(tx(TransactionType.EXPENSE, 25_000L, "cat-a")))
        result.categoryBreakdown.first().percentIs(100.0)
    }

    private fun WeekTrend.unused() = Unit
}
