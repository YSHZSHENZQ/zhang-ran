/**
 * 颜色 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §3 Design Tokens。
 * 规则：页面与组件内**禁止**出现字面色值（如 `Color(0xFF…)`），只能引用本文件的 token。
 *
 * 设计说明：token 的**真值以 ARGB Int 存储**（`ZhangRanColors` 数据类），`Color` 属性只是
 * 其在 Compose 侧的投影。这样纯 JVM 单元测试可以真正断言 token 取值（`Color` 是
 * `androidx.compose.ui.graphics` 的 value class，在 JVM 单测中不可靠）。
 *
 * 视觉取值状态：以下为**占位值**，仅保证可编译与可渲染。
 * TODO(视觉): 取值待按 05 §3 与原型 CSS 变量确定（原型为 glass/霓虹风格，`05` §1）。
 */
package com.zhangran.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 一套颜色 token 的纯数据表示。
 *
 * @property background 页面背景
 * @property surface 卡片 / 表面
 * @property surfaceVariant 次级表面（分组、输入框底色）
 * @property primary 主色（强调、主要 CTA）
 * @property secondary 次色
 * @property textPrimary 主要文本
 * @property textSecondary 次要文本
 * @property textDisabled 禁用态文本
 * @property divider 分隔线
 * @property expense 支出语义色
 * @property income 收入语义色
 * @property transfer 转账语义色
 * @property warning 警告语义色
 * @property error 错误语义色
 */
data class ZhangRanColors(
    val background: Int,
    val surface: Int,
    val surfaceVariant: Int,
    val primary: Int,
    val secondary: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val textDisabled: Int,
    val divider: Int,
    val expense: Int,
    val income: Int,
    val transfer: Int,
    val warning: Int,
    val error: Int,
)

/**
 * `docs/05` §3 要求的 token 名 → 取值的映射（用于完整性自查与单元测试）。
 * 新增 token 时必须同时加入本映射，否则测试会失败。
 */
fun ZhangRanColors.asTokenMap(): Map<String, Int> = linkedMapOf(
    "Background" to background,
    "Surface" to surface,
    "SurfaceVariant" to surfaceVariant,
    "Primary" to primary,
    "Secondary" to secondary,
    "TextPrimary" to textPrimary,
    "TextSecondary" to textSecondary,
    "TextDisabled" to textDisabled,
    "Divider" to divider,
    "Expense" to expense,
    "Income" to income,
    "Transfer" to transfer,
    "Warning" to warning,
    "Error" to error,
)

/** 浅色 token（占位值）。 */
val LightColors: ZhangRanColors = ZhangRanColors(
    background = 0xFFF7F8FA.toInt(),
    surface = 0xFFFFFFFF.toInt(),
    surfaceVariant = 0xFFEDEFF3.toInt(),
    primary = 0xFF4F46E5.toInt(),
    secondary = 0xFF06B6D4.toInt(),
    textPrimary = 0xFF1A1C1E.toInt(),
    textSecondary = 0xFF5A5F66.toInt(),
    textDisabled = 0xFFA6ACB4.toInt(),
    divider = 0xFFE3E6EA.toInt(),
    expense = 0xFFE5484D.toInt(),
    income = 0xFF16A34A.toInt(),
    transfer = 0xFF6366F1.toInt(),
    warning = 0xFFF59E0B.toInt(),
    error = 0xFFDC2626.toInt(),
)

/** 深色 token（占位值；键与浅色一致）。 */
val DarkColors: ZhangRanColors = ZhangRanColors(
    background = 0xFF121316.toInt(),
    surface = 0xFF1B1D21.toInt(),
    surfaceVariant = 0xFF2A2D33.toInt(),
    primary = 0xFFA5B4FC.toInt(),
    secondary = 0xFF67E8F9.toInt(),
    textPrimary = 0xFFECEDEF.toInt(),
    textSecondary = 0xFFB4B9C0.toInt(),
    textDisabled = 0xFF6C7178.toInt(),
    divider = 0xFF34383F.toInt(),
    expense = 0xFFE5484D.toInt(),
    income = 0xFF16A34A.toInt(),
    transfer = 0xFF6366F1.toInt(),
    warning = 0xFFF59E0B.toInt(),
    error = 0xFFDC2626.toInt(),
)

// ---- Compose 侧投影（页面/组件只能引用这些属性，不得写字面值）----

val ZhangRanColors.backgroundColor: Color get() = Color(background)
val ZhangRanColors.surfaceColor: Color get() = Color(surface)
val ZhangRanColors.surfaceVariantColor: Color get() = Color(surfaceVariant)
val ZhangRanColors.primaryColor: Color get() = Color(primary)
val ZhangRanColors.secondaryColor: Color get() = Color(secondary)
val ZhangRanColors.textPrimaryColor: Color get() = Color(textPrimary)
val ZhangRanColors.textSecondaryColor: Color get() = Color(textSecondary)
val ZhangRanColors.textDisabledColor: Color get() = Color(textDisabled)
val ZhangRanColors.dividerColor: Color get() = Color(divider)
val ZhangRanColors.expenseColor: Color get() = Color(expense)
val ZhangRanColors.incomeColor: Color get() = Color(income)
val ZhangRanColors.transferColor: Color get() = Color(transfer)
val ZhangRanColors.warningColor: Color get() = Color(warning)
val ZhangRanColors.errorColor: Color get() = Color(error)
