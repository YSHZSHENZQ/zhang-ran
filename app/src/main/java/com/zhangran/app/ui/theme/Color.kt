/**
 * 颜色 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §3；**视觉真值以 `prototype/账然_UI原型.html` 为准**
 * （亮色 token 块 L42–L63、暗色 L65–L88）。
 * 规则：页面与组件内**禁止**出现字面色值（如 `Color(0xFF…)`），只能引用本文件的 token。
 *
 * 设计说明：
 * - token 的**真值以 ARGB Int 存储**（`ZhangRanColors` 数据类），`Color` 属性只是 Compose 侧投影；
 *   这样纯 JVM 单测可以真正断言取值（`Color` 是 value class，在 JVM 单测中不可靠）。
 * - 带透明度的原型值（`rgba(...)`）**保留 alpha**，不预乘为不透明色——透明度是玻璃效果的前提。
 * - 原型是"四段渐变背景 + 玻璃卡片 + 霓虹光晕"，因此除 `docs/05` §3 的 14 个基础 token 外，
 *   另有一组**扩展 token**（渐变/玻璃/光晕/遮罩）。扩展 token **不进** [asTokenMap]，
 *   以免破坏既有的"14 项完整性"约定；它们由 [asExtendedTokenMap] 覆盖并在 `TokenValueTest` 中断言。
 */
package com.zhangran.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * 一套颜色 token 的纯数据表示。
 *
 * 基础 14 项（`docs/05` §3，语义不变）：
 * @property background 页面背景基色（对应原型 `--bg-1`；整页背景是四段渐变，见 [backgroundGradient...]）
 * @property surface 卡片/表面（对应 `--glass-bg`，带 alpha）
 * @property surfaceVariant 次级表面（对应 `--row-bg`，带 alpha）
 * @property primary 主强调色（`--accent-1`）
 * @property secondary 次强调色（`--accent-2`）
 * @property textPrimary 主要文本（`--text`）
 * @property textSecondary 次要文本（`--text-2`）
 * @property textDisabled 三级文本/禁用（`--text-3`）
 * @property divider 分隔线（`--divider`）
 * @property expense 支出语义（`--red`）
 * @property income 收入语义（`--green`）
 * @property transfer 转账语义（原型无独立色，取 `--accent-2`）
 * @property warning 警告语义（`--amber`）
 * @property error 错误语义（`--red`）
 *
 * 扩展 token（原型的渐变 / 玻璃 / 光晕 / 遮罩）：
 * @property backgroundGradientMid1 渐变第 2 段（`--bg-2`）
 * @property backgroundGradientMid2 渐变第 3 段（`--bg-3`）
 * @property backgroundGradientEnd 渐变第 4 段（`--bg-4`）
 * @property glassBorder 玻璃卡片描边（`--glass-border`）
 * @property rowFill 列表行底色（`--row-bg`）
 * @property inputFill 输入框底色（`--input-bg`）
 * @property modalFill 弹层底色（`--modal-bg`）
 * @property scrim 遮罩（`--overlay`）
 * @property glow1/2/3 背景光晕（`--glow-1/2/3`）
 * @property accentGradientStart/End CTA 渐变两端（`--accent-grad`）
 * @property cardTopAccentStart/Mid/End 卡片顶部强调线三段；**暗色为 `null`**（原型 `--card-top: none`）
 * @property glassShadowColor 玻璃外阴影颜色（`--glass-shadow` 的投影部分）
 * @property glassInnerHighlight 玻璃内高光颜色（`--glass-shadow` 的 `inset` 部分）
 */
data class ZhangRanColors(
    // ---- 基础 14 项（docs/05 §3）----
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
    // ---- 扩展：渐变 / 玻璃 / 光晕 / 遮罩 ----
    val backgroundGradientMid1: Int,
    val backgroundGradientMid2: Int,
    val backgroundGradientEnd: Int,
    val glassBorder: Int,
    val rowFill: Int,
    val inputFill: Int,
    val modalFill: Int,
    val scrim: Int,
    val glow1: Int,
    val glow2: Int,
    val glow3: Int,
    val accentGradientStart: Int,
    val accentGradientEnd: Int,
    /** 卡片顶部强调线第 1 段；`null` 表示**不绘制**（原型暗色 `--card-top: none`）。 */
    val cardTopAccentStart: Int?,
    /** 卡片顶部强调线第 2 段；`null` 表示不绘制。 */
    val cardTopAccentMid: Int?,
    /** 卡片顶部强调线第 3 段；`null` 表示不绘制。 */
    val cardTopAccentEnd: Int?,
    val glassShadowColor: Int,
    val glassInnerHighlight: Int,
)

/**
 * `docs/05` §3 要求的 **14 项基础 token** 名 → 取值。
 *
 * 注意：这里刻意**只含 14 项**——既有 `ThemeTokensTest` 以"恰好 14 项"作为完整性判据；
 * 扩展 token 见 [asExtendedTokenMap]。
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

/** 全部颜色 token（基础 14 + 扩展）；供 `TokenValueTest` 逐项断言。 */
fun ZhangRanColors.asExtendedTokenMap(): Map<String, Int> = asTokenMap() + linkedMapOf(
    "BackgroundGradientMid1" to backgroundGradientMid1,
    "BackgroundGradientMid2" to backgroundGradientMid2,
    "BackgroundGradientEnd" to backgroundGradientEnd,
    "GlassBorder" to glassBorder,
    "RowFill" to rowFill,
    "InputFill" to inputFill,
    "ModalFill" to modalFill,
    "Scrim" to scrim,
    "Glow1" to glow1,
    "Glow2" to glow2,
    "Glow3" to glow3,
    "AccentGradientStart" to accentGradientStart,
    "AccentGradientEnd" to accentGradientEnd,
    "GlassShadowColor" to glassShadowColor,
    "GlassInnerHighlight" to glassInnerHighlight,
)

/**
 * 浅色 token（取自原型 `.screen` 块 L42–L63）。
 *
 * alpha 说明（`rgba` 保留 alpha，按 `round(a * 255)` 换算）：
 * `--glass-bg` 0.55→0x8C、`--glass-border` 0.92→0xEB、`--row-bg`/`--input-bg` 0.7→0xB3、
 * `--modal-bg` 0.96→0xF5、`--divider` 0.05→0x0D、`--overlay` 0.4→0x66。
 */
val LightColors: ZhangRanColors = ZhangRanColors(
    background = 0xFFEEF3FF.toInt(),
    surface = 0x8CFFFFFF.toInt(),
    surfaceVariant = 0xB3FFFFFF.toInt(),
    primary = 0xFF4F46E5.toInt(),
    secondary = 0xFF06B6D4.toInt(),
    textPrimary = 0xFF1A2233.toInt(),
    textSecondary = 0xFF475569.toInt(),
    textDisabled = 0xFF94A3B8.toInt(),
    divider = 0x0D000000,
    expense = 0xFFEF4444.toInt(),
    income = 0xFF10B981.toInt(),
    transfer = 0xFF06B6D4.toInt(),
    warning = 0xFFF59E0B.toInt(),
    error = 0xFFEF4444.toInt(),
    backgroundGradientMid1 = 0xFFF7F0FF.toInt(),
    backgroundGradientMid2 = 0xFFE8F6FF.toInt(),
    backgroundGradientEnd = 0xFFECFDF7.toInt(),
    glassBorder = 0xEBFFFFFF.toInt(),
    rowFill = 0xB3FFFFFF.toInt(),
    inputFill = 0xB3FFFFFF.toInt(),
    modalFill = 0xF5FFFFFF.toInt(),
    scrim = 0x660A0E14,
    glow1 = 0x524F46E5,
    glow2 = 0x4706B6D4,
    glow3 = 0x38A855F7,
    accentGradientStart = 0xFF4F46E5.toInt(),
    accentGradientEnd = 0xFF06B6D4.toInt(),
    cardTopAccentStart = 0xFF4F46E5.toInt(),
    cardTopAccentMid = 0xFF06B6D4.toInt(),
    cardTopAccentEnd = 0xFF10B981.toInt(),
    glassShadowColor = 0x1A4F46E5,
    glassInnerHighlight = 0xBFFFFFFF.toInt(),
)

/**
 * 深色 token（取自原型 `.screen[data-theme="dark"]` 块 L65–L88）。
 *
 * 与浅色的关键差异：`--card-top: none` → [ZhangRanColors.cardTopAccentStart] 等三项为 `null`；
 * 玻璃填充/描边更淡（0.07/0.11），阴影改为纯黑。
 */
val DarkColors: ZhangRanColors = ZhangRanColors(
    background = 0xFF070A12.toInt(),
    surface = 0x12FFFFFF,
    surfaceVariant = 0x12FFFFFF,
    primary = 0xFF00F0FF.toInt(),
    secondary = 0xFF7C5CFF.toInt(),
    textPrimary = 0xFFE6EDF7.toInt(),
    textSecondary = 0xFF7F8B9B.toInt(),
    textDisabled = 0xFF4D6480.toInt(),
    divider = 0x0FFFFFFF,
    expense = 0xFFFF6B81.toInt(),
    income = 0xFF00FFB3.toInt(),
    transfer = 0xFF7C5CFF.toInt(),
    warning = 0xFFFFB84D.toInt(),
    error = 0xFFFF6B81.toInt(),
    backgroundGradientMid1 = 0xFF0C1322.toInt(),
    backgroundGradientMid2 = 0xFF08141A.toInt(),
    backgroundGradientEnd = 0xFF08141A.toInt(),
    glassBorder = 0x1CFFFFFF,
    rowFill = 0x12FFFFFF,
    inputFill = 0x14FFFFFF,
    modalFill = 0xF5141A24.toInt(),
    scrim = 0x99000000.toInt(),
    glow1 = 0x6B00F0FF,
    glow2 = 0x667C5CFF,
    glow3 = 0x3800FFAA,
    accentGradientStart = 0xFF00F0FF.toInt(),
    accentGradientEnd = 0xFF7C5CFF.toInt(),
    // 原型 `--card-top: none`：暗色不绘制卡片顶部彩色线
    cardTopAccentStart = null,
    cardTopAccentMid = null,
    cardTopAccentEnd = null,
    glassShadowColor = 0x59000000,
    glassInnerHighlight = 0x14FFFFFF,
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

val ZhangRanColors.backgroundGradientMid1Color: Color get() = Color(backgroundGradientMid1)
val ZhangRanColors.backgroundGradientMid2Color: Color get() = Color(backgroundGradientMid2)
val ZhangRanColors.backgroundGradientEndColor: Color get() = Color(backgroundGradientEnd)
val ZhangRanColors.glassBorderColor: Color get() = Color(glassBorder)
val ZhangRanColors.rowFillColor: Color get() = Color(rowFill)
val ZhangRanColors.inputFillColor: Color get() = Color(inputFill)
val ZhangRanColors.modalFillColor: Color get() = Color(modalFill)
val ZhangRanColors.scrimColor: Color get() = Color(scrim)
val ZhangRanColors.glow1Color: Color get() = Color(glow1)
val ZhangRanColors.glow2Color: Color get() = Color(glow2)
val ZhangRanColors.glow3Color: Color get() = Color(glow3)
val ZhangRanColors.accentGradientStartColor: Color get() = Color(accentGradientStart)
val ZhangRanColors.accentGradientEndColor: Color get() = Color(accentGradientEnd)
val ZhangRanColors.glassShadowColorValue: Color get() = Color(glassShadowColor)
val ZhangRanColors.glassInnerHighlightColor: Color get() = Color(glassInnerHighlight)

/** 卡片顶部强调线的三段（`null` = 不绘制，见原型暗色 `--card-top: none`）。 */
val ZhangRanColors.cardTopAccentColors: List<Color>?
    get() {
        val start = cardTopAccentStart ?: return null
        val mid = cardTopAccentMid ?: return null
        val end = cardTopAccentEnd ?: return null
        return listOf(Color(start), Color(mid), Color(end))
    }
