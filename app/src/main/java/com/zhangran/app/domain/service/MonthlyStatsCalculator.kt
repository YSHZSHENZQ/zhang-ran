/**
 * 月度统计聚合（`M2e-2` §4.2；口径 SSOT：`docs/02` §14 + P-013）。
 *
 * **纯函数**：不访问 DAO、不读系统时间；分日按传入的设备时区，月份区间由调用方用
 * `TransactionTime.monthRangeUtc` 查好后传入。
 *
 * 规则（与任务书 §4.2 逐条对应）：
 * 1. 只计 `CONFIRMED` 且未软删除；
 * 2. `TRANSFER` 不计；
 * 3. `REFUND` 冲减消费、不计笔数、不加收入；
 * 4. 分类占比按分类净额；`categoryId == null` 归「其他」；
 * 5. 占比取前 4 + 汇合「其他」；分类 ≤ 4 时不出现汇合项；
 * 6. 消费净额 ≤ 0 → 占比列表为空；
 * 7. 日均 = 消费 ÷ 当月自然天数，**向下取整**；
 * 8. 最高单日 = 单日净额最大值（无数据为 0）；
 * 9. 笔数 = 支出 + 收入（不含退款）；
 * 10. 按天趋势按设备时区分日；周起点**周一**，每周固定 7 点；
 * 11. 负值保留，不 `coerceAtLeast(0)`。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.CategoryShare
import com.zhangran.app.domain.model.DailyPoint
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.model.WeekTrend
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** 分类占比最多展示的「非其他」项数（`06` §4.2）。 */
const val TOP_CATEGORY_SHARE_COUNT: Int = 4

/** 缺省分类名（`categoryId == null` / 分类已不可解析时归入）。 */
const val FALLBACK_CATEGORY_NAME_FOR_STATS: String = "其他"

/**
 * 计算某月统计。
 *
 * @param periodKey `YYYY-MM`
 * @param transactions 该月的已确认交易（`listInRange` 返回值；这里再做一次防御性过滤）
 * @param zoneId 设备时区（分日与周切分）
 * @param categoryNameOf 分类 id → 名称；`null` 由调用方负责映射为「其他」
 */
fun calculateMonthlyStats(
    periodKey: String,
    year: Int,
    month: Int,
    transactions: List<TransactionEntity>,
    zoneId: String,
    categoryNameOf: (String?) -> String,
): MonthlyStats {
    val zone = ZoneId.of(zoneId)
    val eligible = transactions.filter {
        it.state == TransactionState.CONFIRMED && it.deletedAt == null
    }

    var expenseMinor = 0L
    var refundMinor = 0L
    var incomeMinor = 0L
    var recordCount = 0
    val dailyNet = linkedMapOf<LocalDate, Long>()
    val categoryNet = linkedMapOf<String?, Long>()

    eligible.forEach { transaction ->
        when (transaction.type) {
            TransactionType.EXPENSE -> {
                expenseMinor += transaction.amountMinor
                recordCount++
                val date = transaction.dateIn(zone)
                dailyNet[date] = (dailyNet[date] ?: 0L) + transaction.amountMinor
                categoryNet[transaction.categoryId] =
                    (categoryNet[transaction.categoryId] ?: 0L) + transaction.amountMinor
            }

            TransactionType.REFUND -> {
                refundMinor += transaction.amountMinor
                val date = transaction.dateIn(zone)
                dailyNet[date] = (dailyNet[date] ?: 0L) - transaction.amountMinor
                categoryNet[transaction.categoryId] =
                    (categoryNet[transaction.categoryId] ?: 0L) - transaction.amountMinor
            }

            TransactionType.INCOME -> {
                incomeMinor += transaction.amountMinor
                recordCount++
            }

            // 规则 2：转账不计入消费/收入/趋势/占比/笔数
            TransactionType.TRANSFER -> Unit
        }
    }

    val netExpense = expenseMinor - refundMinor
    val yearMonth = YearMonth.of(year, month)
    val daysInMonth = yearMonth.lengthOfMonth()

    // 规则 5/6：分类占比（只保留净额为正的项；消费净额 ≤ 0 时为空）
    val categoryBreakdown = if (netExpense <= 0L) {
        emptyList()
    } else {
        buildCategoryShares(categoryNet, netExpense, categoryNameOf)
    }

    // 规则 8：最高单日（无数据为 0；有数据时可为负，不钳制）
    val maxDailyExpense = if (dailyNet.isEmpty()) 0L else dailyNet.values.max()

    return MonthlyStats(
        periodKey = periodKey,
        year = year,
        month = month,
        daysInMonth = daysInMonth,
        hasData = eligible.isNotEmpty(),
        expenseMinor = netExpense,
        incomeMinor = incomeMinor,
        balanceMinor = incomeMinor - netExpense,
        // 规则 7：除以自然天数，向下取整（负数也向下）
        dailyAverageMinor = netExpense.floorDiv(daysInMonth.toLong()),
        maxDailyExpenseMinor = maxDailyExpense,
        recordCount = recordCount,
        categoryBreakdown = categoryBreakdown,
        weeks = buildWeeks(yearMonth, dailyNet),
    )
}

private fun TransactionEntity.dateIn(zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(transactionTime).atZone(zone).toLocalDate()

/** 分类净额 → 前 4 + 「其他」；同名（含 null → 其他）先合并。 */
private fun buildCategoryShares(
    categoryNet: Map<String?, Long>,
    netExpense: Long,
    categoryNameOf: (String?) -> String,
): List<CategoryShare> {
    val byName = linkedMapOf<String, Long>()
    categoryNet.forEach { (categoryId, amount) ->
        if (amount <= 0L) return@forEach
        val name = categoryNameOf(categoryId)
        byName[name] = (byName[name] ?: 0L) + amount
    }
    if (byName.isEmpty()) return emptyList()

    val sorted = byName.entries
        .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    if (sorted.size <= TOP_CATEGORY_SHARE_COUNT) {
        return sorted.map { it.toShare(netExpense) }
    }

    val top = sorted.take(TOP_CATEGORY_SHARE_COUNT)
    val restMinor = sorted.drop(TOP_CATEGORY_SHARE_COUNT).sumOf { it.value }
    val shares = top.map { it.toShare(netExpense) }.toMutableList()
    if (restMinor > 0L) {
        val fallbackName = FALLBACK_CATEGORY_NAME_FOR_STATS
        val existingFallback = shares.indexOfFirst { it.categoryName == fallbackName }
        if (existingFallback >= 0) {
            // 前 4 已含真实的「其他」→ 把剩余并入它，避免出现两条同名图例
            val merged = shares[existingFallback]
            val amount = merged.amountMinor + restMinor
            shares[existingFallback] = CategoryShare(
                categoryName = fallbackName,
                amountMinor = amount,
                percent = amount.toPercentOf(netExpense),
            )
        } else {
            shares += CategoryShare(
                categoryName = fallbackName,
                amountMinor = restMinor,
                percent = restMinor.toPercentOf(netExpense),
            )
        }
    }
    return shares
}

private fun Map.Entry<String, Long>.toShare(netExpense: Long): CategoryShare = CategoryShare(
    categoryName = key,
    amountMinor = value,
    percent = value.toPercentOf(netExpense),
)

private fun Long.toPercentOf(netExpense: Long): Double =
    if (netExpense <= 0L) 0.0 else toDouble() / netExpense.toDouble() * 100.0

/**
 * 按周切分（周一为起点，固定 7 点）。
 *
 * 跨月周：不属于本月的天用 `dayOfMonth = 0` 占位、金额 0（不跨月取数）。
 * 月首/月末的周同样补齐 7 点。
 */
private fun buildWeeks(yearMonth: YearMonth, dailyNet: Map<LocalDate, Long>): List<WeekTrend> {
    val first = yearMonth.atDay(1)
    val last = yearMonth.atEndOfMonth()
    val mondayBeforeFirst = first.minusDays((first.dayOfWeek.value - 1).toLong())

    val weeks = mutableListOf<WeekTrend>()
    var weekStart = mondayBeforeFirst
    var weekIndex = 0
    while (!weekStart.isAfter(last)) {
        val points = (0..6).map { offset ->
            val date = weekStart.plusDays(offset.toLong())
            if (date.year == yearMonth.year && date.monthValue == yearMonth.monthValue) {
                DailyPoint(
                    dayOfMonth = date.dayOfMonth,
                    weekdayIndex = offset,
                    amountMinor = dailyNet[date] ?: 0L,
                )
            } else {
                DailyPoint(dayOfMonth = 0, weekdayIndex = offset, amountMinor = 0L)
            }
        }
        weeks += WeekTrend(weekIndex = weekIndex, points = points)
        weekStart = weekStart.plusWeeks(1)
        weekIndex++
    }
    return weeks
}
