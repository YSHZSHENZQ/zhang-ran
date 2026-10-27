/**
 * 首页列表项展示模型（`docs/06` §4.1 列表区）。
 *
 * 定位：**只承载已格式化好的展示值**，不含实体、不含 id 之外的领域字段，
 * 也不做任何计算——格式化在 ViewModel 完成（`docs/04` §20：UI 不自行推导业务结论）。
 *
 * 为什么把金额/时间格式化成字符串：Compose 不应接触 `amountMinor` 的换算口径
 * （`docs/03` §4 唯一转换点是 Money），也不应自己读系统时区做时间格式化。
 */
package com.zhangran.app.ui.screen.home

/**
 * 一条账务列表项。
 *
 * @property id 交易 id（后续详情跳转用；本任务不做跳转）
 * @property title 主标题：商户名，缺失时退回备注，二者都空时按类型给默认文案
 * @property amountText 已格式化金额（含符号与千分位，如 `-¥12.34` / `+¥100.00`）
 * @property typeText 类型文案（支出 / 收入 / 转账 / 退款）
 * @property timeText 已格式化时间（`yyyy-MM-dd HH:mm`）
 * @property categoryName 分类名（可能为空：转账或分类已删除）
 * @property accountName 账户名（可能为空）
 */
data class TransactionListItem(
    val id: String,
    val title: String,
    val amountText: String,
    val typeText: String,
    val timeText: String,
    val categoryName: String?,
    val accountName: String?,
)
