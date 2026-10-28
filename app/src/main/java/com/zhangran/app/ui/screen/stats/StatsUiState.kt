/**
 * 统计页的 UI 状态与展示映射（`M2e-2` §4.4；行为 SSOT：`docs/06` §4.2）。
 *
 * 页面**不做聚合**：消费/收入/三项指标/占比/每周 7 点都由 Domain 提供（R6）。
 * 这里只做"取第 N 周""格式化金额/百分比/周标签"等展示换算。
 */
package com.zhangran.app.ui.screen.stats

import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.WeekTrend
import com.zhangran.app.domain.usecase.StatsMonth
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import kotlin.math.roundToInt

/** 统计页状态。 */
sealed interface StatsUiState {

    data object Loading : StatsUiState

    /**
     * @property stats 已聚合的当月数据
     * @property months 可选月份（最早记录月→当前月，最新在前）
     * @property weekIndex 当前展示的周序号（0 起）
     * @property maxWeekIndex 允许滑到的最大周序号（当前月 = 包含今天的那一周）
     * @property isCurrentMonth 是否正在查看当前月（影响初始周与"本周"标签）
     */
    data class Content(
        val stats: MonthlyStats,
        val months: List<StatsMonth>,
        val weekIndex: Int,
        val maxWeekIndex: Int,
        val isCurrentMonth: Boolean,
    ) : StatsUiState {
        val currentWeek: WeekTrend get() = stats.weeks[weekIndex]
    }

    /**
     * 该月没有任何已确认记录。
     *
     * @property periodKey 当前查看月
     */
    data class Empty(
        val periodKey: String,
        val months: List<StatsMonth>,
    ) : StatsUiState

    data class Error(val message: String) : StatsUiState
}

/** 统计页的一次性事件。 */
sealed interface StatsEvent {
    data class Message(val text: String) : StatsEvent
}

// ------------------------------------------------------------------ 展示换算

/** 周标签：「本周」/「10 月第 3 周」。 */
fun weekLabel(
    stats: MonthlyStats,
    weekIndex: Int,
    isCurrentMonth: Boolean,
    currentWeekIndex: Int,
): String = if (isCurrentMonth && weekIndex == currentWeekIndex) {
    "本周"
} else {
    "${stats.month} 月第 ${weekIndex + 1} 周"
}

/** 周内 7 天的固定标签（周一→周日）。 */
val WEEKDAY_LABELS: List<String> = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 金额文案（带 `¥`、千分位、2 位小数）。 */
fun statsMoneyText(amountMinor: Long): String =
    HomeViewModel.formatMoney(amountMinor, sign = AmountSign.AUTO, withCurrencySymbol = true)

/**
 * 环形图中心的总览金额（紧凑：千元起 `¥3.8k`；不足千元显示完整金额）。
 *
 * 只用整数运算（`10_000 minor = 0.1k`），不引入 `Double` 处理金额。
 */
fun compactMoneyText(amountMinor: Long): String {
    if (amountMinor >= 100_000L) {
        val tenthsOfThousand = amountMinor / 10_000L
        return "¥${tenthsOfThousand / 10}.${tenthsOfThousand % 10}k"
    }
    return statsMoneyText(amountMinor)
}

/** 百分比文案（Domain 给 Double，这里只做四舍五入到整数）。 */
fun percentText(percent: Double): String = "${percent.roundToInt()}%"

/** 笔数文案。 */
fun recordCountText(count: Int): String = count.toString()
