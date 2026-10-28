/**
 * 月度统计读取（`M2e-2` §4.3）。
 *
 * 一次性 `suspend` 取数 + 计算（UI 在切月/重试时调用），月份参数可选：
 * - `null` → 设备时区当前月；
 * - 否则解析 `YYYY-MM`。
 *
 * 月份边界复用 `TransactionTime.monthRangeUtc`（M2e-1a），因此与首页口径一致、按设备时区。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.service.FALLBACK_CATEGORY_NAME_FOR_STATS
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.calculateMonthlyStats
import java.time.Clock
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

/**
 * 月份选项（供月份选择器）。
 *
 * @property periodKey `YYYY-MM`
 */
data class StatsMonth(
    val periodKey: String,
    val year: Int,
    val month: Int,
) {
    /** 展示文案：「2026 年 10 月」（不补零，与任务书一致）。 */
    val displayText: String get() = "$year 年 $month 月"
}

/**
 * @param transactions 当月交易（`listInRange`）
 * @param categories 分类 id → 名称
 * @param clock 时间源（测试注入固定"今天"）
 */
class ObserveMonthlyStatsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    /**
     * @param periodKey `YYYY-MM`；`null` = 当前月
     * @throws IllegalArgumentException periodKey 非法
     */
    suspend operator fun invoke(periodKey: String? = null): MonthlyStats {
        val month = periodKey?.let(::parseMonth) ?: currentYearMonth()
        val zone = ZoneId.systemDefault()
        val range = TransactionTime.monthRangeUtc(zone.id, month.year, month.monthValue)
        val monthTransactions = transactions.listInRange(range.startInclusive, range.endExclusive)
        val names = categories.all().associate { it.id to it.name }
        return calculateMonthlyStats(
            periodKey = "%04d-%02d".format(month.year, month.monthValue),
            year = month.year,
            month = month.monthValue,
            transactions = monthTransactions,
            zoneId = zone.id,
            categoryNameOf = { id ->
                id?.let { names[it] } ?: FALLBACK_CATEGORY_NAME_FOR_STATS
            },
        )
    }

    /**
     * 可选月份：**最早有记录的月份 → 当前月**（最新在前）；无记录时只含当前月。
     *
     * 用 `listVisible()`（含 Pending）判断"有记录"——只要用户记过账，就允许查看该月。
     */
    suspend fun availableMonths(): List<StatsMonth> {
        val zone = ZoneId.systemDefault()
        val now = currentYearMonth()
        val earliest = transactions.listVisible()
            .minOfOrNull { it.transactionTime }
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        val start = earliest?.let { YearMonth.of(it.year, it.monthValue) } ?: now

        val months = mutableListOf<StatsMonth>()
        var cursor = start
        while (!cursor.isAfter(now)) {
            months += StatsMonth(
                periodKey = "%04d-%02d".format(cursor.year, cursor.monthValue),
                year = cursor.year,
                month = cursor.monthValue,
            )
            cursor = cursor.plusMonths(1)
        }
        return months.asReversed()
    }

    /** 设备时区当前月（`YYYY-MM`）。 */
    fun currentPeriodKey(): String = currentYearMonth().toPeriodKey()

    private fun currentYearMonth(): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(clock.millis()).atZone(ZoneId.systemDefault()))

    private fun parseMonth(periodKey: String): YearMonth = runCatching {
        YearMonth.parse(periodKey)
    }.getOrElse { throw IllegalArgumentException("periodKey 非法：$periodKey") }
}

/** `YearMonth` → `YYYY-MM`（`03` §9 的 periodKey 格式）。 */
internal fun YearMonth.toPeriodKey(): String = "%04d-%02d".format(year, monthValue)
