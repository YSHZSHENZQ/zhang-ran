/**
 * 首页列表项展示模型（`docs/06` §4.1 列表区、`M2e-1b` §4.4）。
 *
 * 定位：**只承载已格式化好的展示值**，不含实体、不含 id 之外的领域字段，
 * 也不做任何计算——格式化与分组在 ViewModel 完成（`docs/04` §20：UI 不自行推导业务结论）。
 *
 * 为什么把金额/时间格式化成字符串：Compose 不应接触 `amountMinor` 的换算口径
 * （`docs/03` §4 唯一转换点是 Money），也不应自己读系统时区做时间格式化。
 */
package com.zhangran.app.ui.screen.home

import com.zhangran.app.domain.model.TransactionType

/**
 * 一条账务列表项。
 *
 * @property id 交易 id（详情跳转用）
 * @property title 主标题：商户名，缺失时退回备注摘要，再退回分类名，都空时按类型给默认文案
 * @property amountText 已格式化金额（含方向符号与千分位、固定 2 位小数，如 `-38.50` / `+1,200.00`）
 * @property typeText 类型文案（支出 / 收入 / 转账 / 退款）
 * @property timeText 行内时间 `HH:mm`（已按设备/指定时区格式化）
 * @property categoryName 分类名（可能为空：转账或分类已删除）
 * @property categoryIcon 分类语义图标标识（供渐变组与 emoji 选择；为空时用默认）
 * @property sourceText 来源文案（手工 / 通知 / …）
 * @property type 交易类型（决定金额语义色）
 * @property accessibilityText 无障碍朗读文本（`M2e-1b` §4.8）
 */
data class TransactionListItem(
    val id: String,
    val title: String,
    val amountText: String,
    val typeText: String,
    val timeText: String,
    val categoryName: String?,
    val categoryIcon: String?,
    val sourceText: String,
    val type: TransactionType,
    val accessibilityText: String,
)
