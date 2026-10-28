/**
 * 月度统计的展示模型（`M2e-2` §4.1；口径 SSOT：`docs/02` §14 + P-013）。
 *
 * 所有聚合值（消费/收入/结余/三项指标/占比/按天趋势）都由
 * `MonthlyStatsCalculator` 计算完毕，**UI 不再做二次计算**（R6）。
 */
package com.zhangran.app.domain.model

/**
 * 分类占比的一项。
 *
 * @property percent 域层算好的百分比（0–100）；UI 只负责格式化
 */
data class CategoryShare(
    val categoryName: String,
    val amountMinor: Long,
    val percent: Double,
)

/**
 * 某一天的消费净额。
 *
 * @property dayOfMonth `1..31`；**0 表示该天不属于当前查看月**（跨月周占位，金额恒为 0）
 * @property weekdayIndex 该周内的位置：0=周一 … 6=周日
 */
data class DailyPoint(
    val dayOfMonth: Int,
    val weekdayIndex: Int,
    val amountMinor: Long,
) {
    val inMonth: Boolean get() = dayOfMonth > 0
}

/**
 * 一周的 7 个点（周一→周日，固定 7 个；跨月/月首月尾缺失天按 0 占位）。
 *
 * @property weekIndex 该月内的周序号（0 起，按周一起点）
 */
data class WeekTrend(
    val weekIndex: Int,
    val points: List<DailyPoint>,
)

/**
 * 一个月的统计结果。
 *
 * @property daysInMonth 当月自然天数（28–31；日均的除数）
 * @property hasData 当月是否有任何已确认、未删除的交易（含退款；空态判定用）
 * @property expenseMinor 本月消费（净额 = EXPENSE − REFUND，可为负）
 * @property maxDailyExpenseMinor 当月单日净额最大值（无数据为 0，可为负）
 * @property recordCount 笔数（CONFIRMED 的支出 + 收入，不含退款/转账）
 * @property categoryBreakdown 分类占比（最多 4 项 + 汇合的「其他」）
 * @property weeks 按周切分后的 7 点趋势（UI 可直接取第 N 周）
 */
data class MonthlyStats(
    val periodKey: String,
    val year: Int,
    val month: Int,
    val daysInMonth: Int,
    val hasData: Boolean,
    val expenseMinor: Long,
    val incomeMinor: Long,
    val balanceMinor: Long,
    val dailyAverageMinor: Long,
    val maxDailyExpenseMinor: Long,
    val recordCount: Int,
    val categoryBreakdown: List<CategoryShare>,
    val weeks: List<WeekTrend>,
)
