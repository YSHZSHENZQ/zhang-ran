/**
 * 首页汇总数据（`docs/06` §4.1 的五项内容中，除列表外的四项；`M2e-1a` §4.3）。
 *
 * 口径唯一来源是 `docs/02` §14「统计」，本类只承载**结果**，不参与计算：
 * - 当月消费 = 当月 `EXPENSE` 合计 − 当月 `REFUND` 合计（净额，**可能为负**）；
 * - 当月收入 = 当月 `INCOME` 合计（`REFUND` **不**计入收入）；
 * - 当月结余 = 当月收入 − 当月消费；
 * - 待确认数量 = `state = PENDING` 的条数（**全时段**，不限月）。
 *
 * 约束：
 * - 纯 Kotlin/纯领域，**无 Android 依赖**（可 JVM 单测）；
 * - 金额一律 `Long`（最小货币单位，分），**禁止** `Double` / `Float`（`AGENTS.md` §5）；
 * - 「本月」按设备/指定时区划分，计算在 `domain/service/TransactionTime.monthRangeUtc`。
 *
 * @property monthExpenseMinor 当月消费（净额；退款多于消费时为负，**不**截断到 0）
 * @property monthIncomeMinor 当月收入（`INCOME` 合计）
 * @property monthBalanceMinor 当月结余 = [monthIncomeMinor] − [monthExpenseMinor]
 * @property pendingCount 待确认条数（全时段）
 */
package com.zhangran.app.domain.model

data class HomeSummary(
    val monthExpenseMinor: Long,
    val monthIncomeMinor: Long,
    val monthBalanceMinor: Long,
    val pendingCount: Int,
)
