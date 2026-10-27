/**
 * 主题组装（唯一入口）。
 *
 * 依据：`docs/05_UI设计系统.md` §3–§6；**视觉真值来自 `prototype/账然_UI原型.html`**。
 *
 * 规则：
 * - 页面与组件通过 `ZhangRanTheme { … }` 获得主题，只能引用 token，不得写字面色值 / 裸数字尺寸；
 * - 明暗主题都必须可切换（`ZhangRanTheme(darkTheme = …)`）；
 * - 页面背景**不得自行拼接渐变**：统一用 [zhangRanBackground]。
 *
 * 本文件同时承载新增 token 的接入位置（`CompositionLocalProvider`）：
 * - 颜色扩展 token（渐变/玻璃/光晕/遮罩）随 [ZhangRanColors] 一起提供，无需另建 Local；
 * - 玻璃数值 token → [LocalZhangRanGlass]（访问器 `ZhangRanTheme.glass`）。
 */
package com.zhangran.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** 当前生效的账然颜色 token。 */
val LocalZhangRanColors = staticCompositionLocalOf { LightColors }

/** 当前生效的账然形状 token。 */
val LocalZhangRanShapes = staticCompositionLocalOf { zhangRanShapes() }

/** 当前生效的账然排版 token（纯数据）。 */
val LocalZhangRanTypographyTokens = staticCompositionLocalOf { TypographyTokens }

/** 当前生效的账然间距 / 圆角 token。 */
val LocalZhangRanSpacing = staticCompositionLocalOf { Spacing }
val LocalZhangRanRadii = staticCompositionLocalOf { Radii }

/** 当前生效的玻璃效果数值 token。 */
val LocalZhangRanGlass = staticCompositionLocalOf { ZhangRanGlassTokens }

/** 访问当前主题的 token。 */
object ZhangRanTheme {
    val colors: ZhangRanColors
        @Composable @ReadOnlyComposable get() = LocalZhangRanColors.current

    val shapes: ZhangRanShapes
        @Composable @ReadOnlyComposable get() = LocalZhangRanShapes.current

    val spacing: ZhangRanSpacing
        @Composable @ReadOnlyComposable get() = LocalZhangRanSpacing.current

    val radii: ZhangRanRadii
        @Composable @ReadOnlyComposable get() = LocalZhangRanRadii.current

    val typographyTokens: ZhangRanTypographyTokens
        @Composable @ReadOnlyComposable get() = LocalZhangRanTypographyTokens.current

    val glass: ZhangRanGlass
        @Composable @ReadOnlyComposable get() = LocalZhangRanGlass.current
}

/**
 * 账然字体权重 → Compose `FontWeight`。
 *
 * 原型用到的 400 / 500 / 600 / 700 一一对应。
 */
private fun ZhangRanFontWeight.toComposeFontWeight(): FontWeight = when (this) {
    ZhangRanFontWeight.NORMAL -> FontWeight.Normal
    ZhangRanFontWeight.MEDIUM -> FontWeight.Medium
    ZhangRanFontWeight.SEMI_BOLD -> FontWeight.SemiBold
    ZhangRanFontWeight.BOLD -> FontWeight.Bold
}

/**
 * 单个排版 token → `TextStyle`。
 *
 * 金额类文本使用等宽字体族，保证数字对齐（`docs/05` §9 金额展示要求）；其余用系统默认字体。
 */
private fun ZhangRanTextToken.toTextStyle(monospace: Boolean = false): TextStyle = TextStyle(
    fontSize = fontSizeSp.sp,
    fontWeight = fontWeight.toComposeFontWeight(),
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp,
    fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
)

/**
 * 由纯数据排版 token 组装 Material3 `Typography`。
 *
 * 映射：Display→displayLarge、Amount→displayMedium、Headline→headlineMedium、
 * Title→titleMedium、Body→bodyMedium、Caption→bodySmall、Label→labelMedium。
 */
fun zhangRanTypography(tokens: ZhangRanTypographyTokens = TypographyTokens): Typography = Typography(
    displayLarge = tokens.display.toTextStyle(monospace = true),
    displayMedium = tokens.amount.toTextStyle(monospace = true),
    headlineMedium = tokens.headline.toTextStyle(),
    titleMedium = tokens.title.toTextStyle(),
    bodyMedium = tokens.body.toTextStyle(),
    bodySmall = tokens.caption.toTextStyle(),
    labelMedium = tokens.label.toTextStyle(),
)

/** 由账然颜色 token 组装 Material3 浅色方案。 */
private fun zhangRanLightColorScheme(colors: ZhangRanColors) = lightColorScheme(
    primary = colors.primaryColor,
    secondary = colors.secondaryColor,
    background = colors.backgroundColor,
    surface = colors.surfaceColor,
    surfaceVariant = colors.surfaceVariantColor,
    onPrimary = colors.surfaceColor,
    onSecondary = colors.surfaceColor,
    onBackground = colors.textPrimaryColor,
    onSurface = colors.textPrimaryColor,
    onSurfaceVariant = colors.textSecondaryColor,
    outline = colors.dividerColor,
    error = colors.errorColor,
)

/** 由账然颜色 token 组装 Material3 深色方案。 */
private fun zhangRanDarkColorScheme(colors: ZhangRanColors) = darkColorScheme(
    primary = colors.primaryColor,
    secondary = colors.secondaryColor,
    background = colors.backgroundColor,
    surface = colors.surfaceColor,
    surfaceVariant = colors.surfaceVariantColor,
    onPrimary = colors.backgroundColor,
    onSecondary = colors.backgroundColor,
    onBackground = colors.textPrimaryColor,
    onSurface = colors.textPrimaryColor,
    onSurfaceVariant = colors.textSecondaryColor,
    outline = colors.dividerColor,
    error = colors.errorColor,
)

/**
 * 页面背景（原型 `L90`：`linear-gradient(165deg, bg1 0%, bg2 28%, bg3 62%, bg4 100%)`）。
 *
 * 页面**必须**用本修饰符绘制底色，不得自行拼渐变（`docs/05` §3）。
 *
 * @param colors 当前主题颜色（四段渐变的四个停靠色）
 */
fun Modifier.zhangRanBackground(colors: ZhangRanColors): Modifier = drawBehind {
    val width = size.width
    val height = size.height
    if (width <= 0f || height <= 0f) return@drawBehind

    // CSS 角度 → 方向向量：0deg 指向正上方，顺时针为正
    val radians = Math.toRadians(BACKGROUND_ANGLE_DEGREES)
    val dx = sin(radians).toFloat()
    val dy = -cos(radians).toFloat()
    // 以较长边作为渐变轴，保证四段停靠点覆盖整个区域
    val halfLength = maxOf(width, height) / 2f
    val center = Offset(width / 2f, height / 2f)

    drawRect(
        brush = Brush.linearGradient(
            colorStops = arrayOf(
                0f to Color(colors.background),
                BACKGROUND_STOP_MID1 to colors.backgroundGradientMid1Color,
                BACKGROUND_STOP_MID2 to colors.backgroundGradientMid2Color,
                1f to colors.backgroundGradientEndColor,
            ),
            start = Offset(center.x - dx * halfLength, center.y - dy * halfLength),
            end = Offset(center.x + dx * halfLength, center.y + dy * halfLength),
        ),
    )
}

/** 原型渐变角度（`165deg`）。 */
private const val BACKGROUND_ANGLE_DEGREES: Double = 165.0

/** 原型渐变第 2 段停靠点（`28%`）。 */
private const val BACKGROUND_STOP_MID1: Float = 0.28f

/** 原型渐变第 3 段停靠点（`62%`）。 */
private const val BACKGROUND_STOP_MID2: Float = 0.62f

/**
 * 账然主题。
 *
 * @param darkTheme 是否使用深色 token（默认 `isSystemInDarkTheme()`）
 */
@Composable
fun ZhangRanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val shapes = zhangRanShapes()

    CompositionLocalProvider(
        LocalZhangRanColors provides colors,
        LocalZhangRanShapes provides shapes,
        LocalZhangRanTypographyTokens provides TypographyTokens,
        LocalZhangRanSpacing provides Spacing,
        LocalZhangRanRadii provides Radii,
        LocalZhangRanGlass provides ZhangRanGlassTokens,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) {
                zhangRanDarkColorScheme(colors)
            } else {
                zhangRanLightColorScheme(colors)
            },
            typography = zhangRanTypography(),
            shapes = shapes.materialShapes,
            content = content,
        )
    }
}
