/**
 * `StatsViewModel` 的边界规则测试（`M2e-2` A8/A10/A20）。
 *
 * 规则抽在 `StatsWeekRules`（纯函数、无 Android 依赖），因此 JVM 可直接验证：
 * - 当前月最多滑到"包含今天的那一周"；
 * - 历史月可以滑到该月最后一周；
 * - 初始周：当前月 = 本周，历史月 = 第 1 周；
 * - 未来月份不可选。
 */
package com.zhangran.app.ui.screen.stats

import com.zhangran.app.domain.model.DailyPoint
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.WeekTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun week(index: Int, days: List<Int>): WeekTrend = WeekTrend(
    weekIndex = index,
    points = (0..6).map { offset ->
        val day = days.getOrElse(offset) { 0 }
        DailyPoint(dayOfMonth = day, weekdayIndex = offset, amountMinor = day.toLong() * 100)
    },
)

/** 2026-10：周 0 = 9/28–10/4，周 1 = 10/5–11，周 2 = 10/12–18，周 3 = 10/19–25，周 4 = 10/26–11/1。 */
private fun octoberWeeks(): List<WeekTrend> = listOf(
    week(0, listOf(0, 0, 0, 1, 2, 3, 4)),
    week(1, listOf(5, 6, 7, 8, 9, 10, 11)),
    week(2, listOf(12, 13, 14, 15, 16, 17, 18)),
    week(3, listOf(19, 20, 21, 22, 23, 24, 25)),
    week(4, listOf(26, 27, 28, 29, 30, 31, 0)),
)

private fun monthStats(): MonthlyStats = MonthlyStats(
    periodKey = "2026-10",
    year = 2026,
    month = 10,
    daysInMonth = 31,
    hasData = true,
    expenseMinor = 100_000L,
    incomeMinor = 0L,
    balanceMinor = -100_000L,
    dailyAverageMinor = 3_225L,
    maxDailyExpenseMinor = 10_000L,
    recordCount = 3,
    categoryBreakdown = emptyList(),
    weeks = octoberWeeks(),
)

class StatsViewModelTest {

    // ---------------------------------------------------------------- A8 周边界

    @Test
    fun A8_当前月最多到包含今天的那一周() {
        val weeks = octoberWeeks()

        assertEquals(2, StatsWeekRules.maxWeekIndex(weeks, isCurrentMonth = true, todayDayOfMonth = 15))
        assertEquals(4, StatsWeekRules.maxWeekIndex(weeks, isCurrentMonth = true, todayDayOfMonth = 31))
        assertEquals(0, StatsWeekRules.maxWeekIndex(weeks, isCurrentMonth = true, todayDayOfMonth = 1))
    }

    @Test
    fun A8_历史月可以到该月最后一周() {
        assertEquals(
            4,
            StatsWeekRules.maxWeekIndex(octoberWeeks(), isCurrentMonth = false, todayDayOfMonth = 15),
        )
    }

    @Test
    fun A8_下一周只在未到上限时可用() {
        assertTrue(StatsWeekRules.canGoNext(weekIndex = 1, maxWeekIndex = 2))
        assertFalse("当前周就是上限", StatsWeekRules.canGoNext(weekIndex = 2, maxWeekIndex = 2))
    }

    @Test
    fun A8_上一周只在非首周时可用() {
        assertTrue(StatsWeekRules.canGoPrevious(weekIndex = 1))
        assertFalse(StatsWeekRules.canGoPrevious(weekIndex = 0))
    }

    // ---------------------------------------------------------------- A11 初始周

    @Test
    fun A11_当前月初始为本周历史月初始为第1周() {
        val weeks = octoberWeeks()

        assertEquals(
            "当前月：包含今天的那一周",
            2,
            StatsWeekRules.initialWeekIndex(weeks, isCurrentMonth = true, todayDayOfMonth = 15),
        )
        assertEquals(
            "历史月：该月第 1 周",
            0,
            StatsWeekRules.initialWeekIndex(weeks, isCurrentMonth = false, todayDayOfMonth = 15),
        )
    }

    @Test
    fun 今天不在该月周内时回退到最后一周() {
        assertEquals(
            4,
            StatsWeekRules.maxWeekIndex(octoberWeeks(), isCurrentMonth = true, todayDayOfMonth = 99),
        )
    }

    // ---------------------------------------------------------------- A10 月份边界

    @Test
    fun A10_未来月份不可选() {
        assertTrue(StatsWeekRules.isMonthSelectable("2026-10", "2026-10"))
        assertTrue(StatsWeekRules.isMonthSelectable("2026-09", "2026-10"))
        assertTrue(StatsWeekRules.isMonthSelectable("2025-12", "2026-10"))
        assertFalse(StatsWeekRules.isMonthSelectable("2026-11", "2026-10"))
        assertFalse(StatsWeekRules.isMonthSelectable("2027-01", "2026-10"))
    }

    // ---------------------------------------------------------------- 展示辅助

    @Test
    fun 周标签当前周显示本周历史周显示第N周() {
        val stats = monthStats()
        assertEquals("本周", weekLabel(stats, 2, isCurrentMonth = true, currentWeekIndex = 2))
        assertEquals("10 月第 2 周", weekLabel(stats, 1, isCurrentMonth = true, currentWeekIndex = 2))
        assertEquals("10 月第 1 周", weekLabel(stats, 0, isCurrentMonth = false, currentWeekIndex = 2))
    }

    @Test
    fun 月份文案与百分比格式化() {
        assertEquals("2026 年 10 月", periodKeyDisplay("2026-10"))
        assertEquals("百分比四舍五入", "67%", percentText(66.666))
        assertEquals("34%", percentText(33.6))
    }

    @Test
    fun 环形图中心金额紧凑格式() {
        assertEquals("不足千元显示完整金额", "¥999.00", compactMoneyText(99_900L))
        assertEquals("¥3.8k", compactMoneyText(384_720L))
        assertEquals("¥12.3k", compactMoneyText(1_234_500L))
    }
}
