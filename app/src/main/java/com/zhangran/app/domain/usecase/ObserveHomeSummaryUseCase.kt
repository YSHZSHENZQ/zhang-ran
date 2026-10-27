/**
 * 观察首页汇总（当月消费 / 当月收入 / 当月结余 / 待确认数量）。
 *
 * 口径**唯一来源**是 `docs/02` §14「统计」，本用例只做取数与求和，不得自行解释：
 * - 只计 `CONFIRMED`（`PENDING` / `IGNORED` / `DELETED` / `DUPLICATE` / 软删除均不计）；
 * - 当月消费 = 当月 `EXPENSE` 合计 − 当月 `REFUND` 合计（**净额**，退款冲减消费，不是收入）；
 * - 当月收入 = 当月 `INCOME` 合计（**`REFUND` 不计入收入**）；
 * - 当月结余 = 当月收入 − 当月消费；
 * - `TRANSFER` 不计入消费/收入/结余（`docs/02` §8、`docs/03` §14）；
 * - 待确认数量 = `state = PENDING` 的条数，**全时段**（待办概念，`docs/06` §4.1）。
 *
 * 月份边界：`transactionTime` 是 UTC millis，因此「本月」必须按**时区**换算成 UTC 半开区间
 * （`TransactionTime.monthRangeUtc`）。若按 UTC 划分，UTC+8 用户在 3 月 1 日 00:30 的记录
 * 会被算进 2 月（见该函数 KDoc）。
 *
 * 响应式：以交易的 `Flow`（`observeVisible`，覆盖所有非软删除交易）作为**变化信号**，
 * 每次变化重新按当前月区间取数并求和；分类/账户变化不影响统计（统计只用交易）。
 *
 * 负数：若某月 `REFUND` 多于 `EXPENSE`（退款跨月、原交易在上月等），当月消费**按实际为负**，
 * 结余随之 > 收入。这里**不** `coerceAtLeast(0)`：截断会把"账务异常"伪装成"本月没花钱"。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.HomeSummary
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.TransactionTime
import java.time.Clock
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 观察首页汇总的用例。
 *
 * @param transactions 交易读取（`observeVisible` 作变化信号；`listInRange` 取当月已确认交易；
 *   `countByState` 取待确认条数）
 */
class ObserveHomeSummaryUseCase @Inject constructor(
    private val transactions: TransactionRepository,
) {

    /**
     * @param clock 时间源与时区来源。默认 `Clock.systemDefaultZone()`（设备当前时区 + 系统时钟）；
     *   测试用 `Clock.fixed(instant, zone)` 固定"当前月"，避免 `System.currentTimeMillis()`
     *   造成的跨月不稳定（`M2e-1a` §7 R5）。
     *   **每次重新计算都重新向 `clock` 取时间**，因此跨月后数据变化会用新月份，不需要重新订阅。
     * @return 随交易数据变化自动重新发射的 [HomeSummary]
     */
    operator fun invoke(clock: Clock = Clock.systemDefaultZone()): Flow<HomeSummary> =
        transactions.observeVisible().map { compute(clock) }

    /**
     * 按 `clock` 所在时区的**当前月**计算汇总。
     *
     * 必须是 `suspend` 而不是纯函数：取数与计数都走 Repository（一次事务内的一致性由
     * SQLite 查询本身保证；两次查询之间若发生写入，最坏是下一次 Flow 发射纠正）。
     */
    private suspend fun compute(clock: Clock): HomeSummary {
        val zoneId = clock.zone.id
        val currentMonth = YearMonth.now(clock)
        val range = TransactionTime.monthRangeUtc(zoneId, currentMonth.year, currentMonth.monthValue)

        val confirmedInMonth = transactions.listInRange(range.startInclusive, range.endExclusive)

        var expenseMinor = 0L
        var refundMinor = 0L
        var incomeMinor = 0L
        for (transaction in confirmedInMonth) {
            when (transaction.type) {
                TransactionType.EXPENSE -> expenseMinor += transaction.amountMinor
                TransactionType.REFUND -> refundMinor += transaction.amountMinor
                TransactionType.INCOME -> incomeMinor += transaction.amountMinor
                // 转账不计入消费/收支统计（`docs/02` §14、`docs/03` §14），只影响账户余额流水
                TransactionType.TRANSFER -> Unit
            }
        }

        val monthExpenseMinor = expenseMinor - refundMinor
        return HomeSummary(
            monthExpenseMinor = monthExpenseMinor,
            monthIncomeMinor = incomeMinor,
            monthBalanceMinor = incomeMinor - monthExpenseMinor,
            pendingCount = transactions.countByState(TransactionState.PENDING),
        )
    }
}
