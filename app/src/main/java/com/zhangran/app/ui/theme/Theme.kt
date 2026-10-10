/**
 * 主题组装（唯一入口）。
 *
 * 依据：`docs/05_UI设计系统.md` §3–§6。
 * 规则：
 * - 页面与组件通过 `ZhangRanTheme { … }` 获得主题，只能引用 token，不得写字面色值 / 裸数字尺寸；
 * - 明暗主题都必须可切换（`ZhangRanTheme(darkTheme = …)`）。
 *
 * TODO(视觉): 具体色值 / 字重待按 05 §3–§6 与原型 CSS 变量确定（当前为占位值）。
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** 当前生效的账然颜色 token。 */
val LocalZhangRanColors = staticCompositionLocalOf { LightColors }

/** 当前生效的账然形状 token。 */
val LocalZhangRanShapes = staticCompositionLocalOf { zhangRanShapes() }

/** 当前生效的账然排版 token（纯数据）。 */
val LocalZhangRanTypographyTokens = staticCompositionLocalOf { TypographyTokens }

/** 当前生效的账然间距 / 圆角 token。 */
val LocalZhangRanSpacing = staticCompositionLocalOf { Spacing }
val LocalZhangRanRadii = staticCompositionLocalOf { Radii }

/** 访问当前主题的颜色 token：`ZhangRanTheme.colors.primary`。 */
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
}

/** 账然字体权重 → Compose `FontWeight`。 */
private fun ZhangRanFontWeight.toComposeFontWeight(): FontWeight = when (this) {
    ZhangRanFontWeight.NORMAL -> FontWeight.Normal
    ZhangRanFontWeight.MEDIUM -> FontWeight.Medium
    ZhangRanFontWeight.BOLD -> FontWeight.Bold
}

/** 单个排版 token → `TextStyle`。 */
private fun ZhangRanTextToken.toTextStyle(): TextStyle = TextStyle(
    fontSize = fontSizeSp.sp,
    fontWeight = fontWeight.toComposeFontWeight(),
    lineHeight = lineHeightSp.sp,
)

/**
 * 由纯数据排版 token 组装 Material3 `Typography`。
 *
 * 映射：Display→displayLarge、Headline→headlineMedium、Title→titleMedium、
 * Body→bodyMedium、Label→labelMedium、Caption→bodySmall、**Amount→displayMedium**。
 */
fun zhangRanTypography(tokens: ZhangRanTypographyTokens = TypographyTokens): Typography = Typography(
    displayLarge = tokens.display.toTextStyle(),
    displayMedium = tokens.amount.toTextStyle(),
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
