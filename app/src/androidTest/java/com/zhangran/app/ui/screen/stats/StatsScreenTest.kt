/**
 * 统计页 UI 测试（`M2e-2` §5；覆盖 A1、A7–A13、A15、A19 的可测部分）。
 *
 * 页面是无状态 `StatsContent`；口径由 `MonthlyStatsCalculatorTest` / `ObserveMonthlyStatsUseCaseTest`
 * 保证，这里验证展示、图表可读语义与切月/切周交互。
 */
package com.zhangran.app.ui.screen.stats

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.domain.model.CategoryShare
import com.zhangran.app.domain.model.DailyPoint
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.WeekTrend
import com.zhangran.app.domain.usecase.StatsMonth
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatsScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private val months = listOf(
        StatsMonth(periodKey = "2026-10", year = 2026, month = 10),
        StatsMonth(periodKey = "2026-09", year = 2026, month = 9),
    )

    private fun week(index: Int, startDay: Int): WeekTrend = WeekTrend(
        weekIndex = index,
        points = (0..6).map { offset ->
            DailyPoint(
                dayOfMonth = if (startDay + offset <= 31) startDay + offset else 0,
                weekdayIndex = offset,
                amountMinor = offset.toLong() * 1_000L,
            )
        },
    )

    private fun stats() = MonthlyStats(
        periodKey = "2026-10",
        year = 2026,
        month = 10,
        daysInMonth = 31,
        hasData = true,
        expenseMinor = 384_720L,
        incomeMinor = 1_200_000L,
        balanceMinor = 815_280L,
        dailyAverageMinor = 12_410L,
        maxDailyExpenseMinor = 48_600L,
        recordCount = 128,
        categoryBreakdown = listOf(
            CategoryShare("餐饮", 161_000L, 41.85),
            CategoryShare("购物", 92_000L, 23.91),
            CategoryShare("交通", 77_000L, 20.0),
            CategoryShare("其他", 54_720L, 14.24),
        ),
        weeks = listOf(week(0, 1), week(1, 12)),
    )

    private fun content(weekIndex: Int = 1, maxWeekIndex: Int = 1) = StatsUiState.Content(
        stats = stats(),
        months = months,
        weekIndex = weekIndex,
        maxWeekIndex = maxWeekIndex,
        isCurrentMonth = true,
    )

    private fun setContent(
        state: StatsUiState,
        onSelectMonth: (StatsMonth) -> Unit = {},
        onPreviousWeek: () -> Unit = {},
        onNextWeek: () -> Unit = {},
        onNavigateToAdd: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                StatsContent(
                    state = state,
                    onRetry = onRetry,
                    onSelectMonth = onSelectMonth,
                    onPreviousWeek = onPreviousWeek,
                    onNextWeek = onNextWeek,
                    onNavigateToAdd = onNavigateToAdd,
                )
            }
        }
    }

    // ------------------------------------------------------------------ A1 概览

    @Test
    fun A1_概览展示消费与三项指标() {
        setContent(content())

        composeRule.onNodeWithText("MONTHLY OVERVIEW").assertIsDisplayed()
        composeRule.onNodeWithText("¥3,847.20").assertIsDisplayed()
        composeRule.onNodeWithText("日均支出").assertIsDisplayed()
        composeRule.onNodeWithText("¥124.10").assertIsDisplayed()
        composeRule.onNodeWithText("最高单日").assertIsDisplayed()
        composeRule.onNodeWithText("¥486.00").assertIsDisplayed()
        composeRule.onNodeWithText("记账笔数").assertIsDisplayed()
        composeRule.onNodeWithText("128").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A5/A13 环形图

    @Test
    fun A5_环形图与图例展示四项占比() {
        setContent(content())

        composeRule.onNodeWithText("餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("42%").assertIsDisplayed()
        composeRule.onNodeWithText("购物").assertIsDisplayed()
        composeRule.onNodeWithText("24%").assertIsDisplayed()
        composeRule.onNodeWithText("交通").assertIsDisplayed()
        composeRule.onNodeWithText("20%").assertIsDisplayed()
        composeRule.onNodeWithText("其他").assertIsDisplayed()
        composeRule.onNodeWithText("14%").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("分类占比：", substring = true).assertIsDisplayed()
    }

    @Test
    fun A13_最大值为0时图表仍渲染空环() {
        val zero = content().copy(stats = stats().copy(categoryBreakdown = emptyList()))
        setContent(zero)

        composeRule.onNodeWithText("本月消费不大于 0，暂无分类占比").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A7 7 柱与标签

    @Test
    fun A7_趋势展示7个周标签() {
        setContent(content())

        composeRule.onNodeWithText("WEEKLY TREND").assertIsDisplayed()
        listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日").forEach { label ->
            composeRule.onNodeWithText(label).assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("本周", substring = true).assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A8 切周

    @Test
    fun A8_点击上一周触发切周下一周在当前周禁用() {
        var previous = 0
        var next = 0
        setContent(content(weekIndex = 1, maxWeekIndex = 1), onPreviousWeek = { previous++ }, onNextWeek = { next++ })

        composeRule.onNodeWithContentDescription("上一周").performClick()
        composeRule.onNodeWithContentDescription("下一周").performClick()

        assertEquals(1, previous)
        assertEquals("当前周为上限，不可到未来周", 0, next)
    }

    @Test
    fun A8_历史周可以点击下一周() {
        var next = 0
        setContent(content(weekIndex = 0, maxWeekIndex = 1), onNextWeek = { next++ })

        composeRule.onNodeWithContentDescription("下一周").performClick()
        assertEquals(1, next)
    }

    // ------------------------------------------------------------------ A10/A11 切月

    @Test
    fun A10_月份胶囊显示当前月并可打开选择器() {
        var selected: StatsMonth? = null
        setContent(content(), onSelectMonth = { selected = it })

        composeRule.onNodeWithContentDescription("当前查看 2026 年 10 月，双击切换月份").performClick()
        composeRule.onNodeWithText("选择月份").assertIsDisplayed()
        composeRule.onNodeWithText("2026 年 9 月").performClick()

        assertEquals("2026-09", selected?.periodKey)
    }

    @Test
    fun A11_历史月可展示且胶囊文案来自状态() {
        setContent(
            StatsUiState.Content(
                stats = stats().copy(periodKey = "2026-09", month = 9, weeks = listOf(week(0, 1), week(1, 8))),
                months = months,
                weekIndex = 0,
                maxWeekIndex = 1,
                isCurrentMonth = false,
            ),
        )
        composeRule.onNodeWithContentDescription("当前查看 2026 年 9 月，双击切换月份").assertIsDisplayed()
        composeRule.onNodeWithText("9 月第 1 周").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A12 无预算块

    @Test
    fun A12_不出现预算使用情况() {
        setContent(content())
        composeRule.onAllNodesWithText("预算", substring = true).assertCountEquals(0)
    }

    // ------------------------------------------------------------------ A15 状态

    @Test
    fun A15_Empty引导去记一笔() {
        var goAdd = false
        setContent(
            StatsUiState.Empty(periodKey = "2026-10", months = months),
            onNavigateToAdd = { goAdd = true },
        )

        composeRule.onNodeWithText("2026 年 10 月还没有记录").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("去记一笔").performClick()
        assertTrue(goAdd)
    }

    @Test
    fun A15_错误态可重试() {
        var retried = false
        setContent(StatsUiState.Error("读取统计失败"), onRetry = { retried = true })

        composeRule.onNodeWithText("读取统计失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun A15_加载态渲染标题() {
        setContent(StatsUiState.Loading)
        composeRule.onNodeWithText("统计").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A19 无障碍

    @Test
    fun A19_概览与趋势有整句读屏语义() {
        setContent(content())

        composeRule.onNodeWithContentDescription("本月消费", substring = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("最高单日", substring = true).assertIsDisplayed()
    }
}
