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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import com.zhangran.app.R
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.di.SettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
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

/** 当前生效的背景光晕数值 token。 */
val LocalZhangRanGlow = staticCompositionLocalOf { ZhangRanGlowTokens }

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

    val glow: ZhangRanGlow
        @Composable @ReadOnlyComposable get() = LocalZhangRanGlow.current
}

/**
 * 账然正文字体族：**内置微软雅黑**（`res/font/msyh.ttf`）。
 *
 * 背景：原型 `font-family` 首选 `-apple-system` / `PingFang SC` / `Microsoft YaHei`，
 * 在 Windows 上实际渲染为**微软雅黑**；Android 系统默认中文是 Noto Sans CJK（思源黑体），
 * 两者的字形、字宽、标点宽度各不相同。为与原型一致，内置雅黑。
 *
 * 说明（已知并接受）：
 * - 仅内置 Regular 一个字重文件，Medium/SemiBold/Bold 由系统**合成加粗**（不额外打包 16MB 的 Bold）；
 * - 雅黑字体文件受微软授权限制，仅用于本项目**个人学习、不分发**的构建，不进公开分发产物；
 * - 金额等宽文本仍用 [FontFamily.Monospace]（原型金额用 `'SF Mono', ui-monospace, monospace`）。
 */
val ZhangRanFontFamily: FontFamily = FontFamily(
    // **只声明 Normal 一个字重**：Medium/SemiBold/Bold 的请求会落到这里，由系统**合成加粗**。
    // 若把 4 档字重都指向同一个文件，Compose 会认为各字重"已存在"、反而不再合成，
    // 标题/金额该粗的地方会退化成 Regular（真机已复现）。
    Font(R.font.msyh, FontWeight.Normal),
)

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
 * 金额类文本使用等宽字体族，保证数字对齐（`docs/05` §9）；其余用内置雅黑（[ZhangRanFontFamily]）。
 */
private fun ZhangRanTextToken.toTextStyle(monospace: Boolean = false): TextStyle = TextStyle(
    fontSize = fontSizeSp.sp,
    fontWeight = fontWeight.toComposeFontWeight(),
    lineHeight = lineHeightSp.sp,
    letterSpacing = letterSpacingSp.sp,
    fontFamily = if (monospace) FontFamily.Monospace else ZhangRanFontFamily,
)

/**
 * 由纯数据排版 token 组装 Material3 `Typography`。
 *
 * 映射：Display→displayLarge、Amount→displayMedium、Headline→headlineMedium、
 * Title→titleMedium、Body→bodyMedium、Caption→bodySmall、Label→labelMedium。
 *
 * 其余槽位（titleLarge / bodyLarge / labelLarge / headlineSmall 等）虽未直接使用，
 * 也统一换成内置雅黑，避免任何组件回落到系统默认字体。
 */
fun zhangRanTypography(tokens: ZhangRanTypographyTokens = TypographyTokens): Typography {
    val base = Typography()
    return Typography(
        displayLarge = tokens.display.toTextStyle(monospace = true),
        displayMedium = tokens.amount.toTextStyle(monospace = true),
        displaySmall = base.displaySmall.copy(fontFamily = ZhangRanFontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = ZhangRanFontFamily),
        headlineMedium = tokens.headline.toTextStyle(),
        headlineSmall = base.headlineSmall.copy(fontFamily = ZhangRanFontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = ZhangRanFontFamily),
        titleMedium = tokens.title.toTextStyle(),
        titleSmall = base.titleSmall.copy(fontFamily = ZhangRanFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = ZhangRanFontFamily),
        bodyMedium = tokens.body.toTextStyle(),
        bodySmall = tokens.caption.toTextStyle(),
        labelLarge = base.labelLarge.copy(fontFamily = ZhangRanFontFamily),
        labelMedium = tokens.label.toTextStyle(),
        labelSmall = base.labelSmall.copy(fontFamily = ZhangRanFontFamily),
    )
}

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
 * `M4c`：四个停靠色用 `animateColorAsState`（`MotionDuration.slow`）过渡，
 * 主题切换时背景平滑变色（A10）。
 *
 * @param colors 当前主题颜色（四段渐变的四个停靠色）
 */
@Composable
fun Modifier.zhangRanBackground(colors: ZhangRanColors): Modifier {
    val spec = tween<Color>(durationMillis = MotionDuration.slow, easing = MotionEasings.ease)
    val base by animateColorAsState(colors.backgroundColor, spec, label = "bgBase")
    val mid1 by animateColorAsState(colors.backgroundGradientMid1Color, spec, label = "bgMid1")
    val mid2 by animateColorAsState(colors.backgroundGradientMid2Color, spec, label = "bgMid2")
    val end by animateColorAsState(colors.backgroundGradientEndColor, spec, label = "bgEnd")

    return drawBehind {
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
                    0f to base,
                    BACKGROUND_STOP_MID1 to mid1,
                    BACKGROUND_STOP_MID2 to mid2,
                    1f to end,
                ),
                start = Offset(center.x - dx * halfLength, center.y - dy * halfLength),
                end = Offset(center.x + dx * halfLength, center.y + dy * halfLength),
            ),
        )
    }
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
 * @param darkTheme 显式指定明暗（测试/截图用）；为 `null` 时由 [themeMode] 决定
 * @param themeMode 主题模式（`M4c`：跟随系统 / 浅色 / 深色）
 */
@Composable
fun ZhangRanTheme(
    darkTheme: Boolean? = null,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val resolvedDark = darkTheme ?: when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (resolvedDark) DarkColors else LightColors
    val shapes = zhangRanShapes()

    // 应用内切换主题时同步系统栏图标明暗，避免"深色页面 + 深色状态栏图标"不可读（A12）
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            view.context.findActivity()?.let { activity ->
                WindowCompat.getInsetsController(activity.window, view)
                    .isAppearanceLightStatusBars = !resolvedDark
            }
        }
    }

    CompositionLocalProvider(
        LocalZhangRanColors provides colors,
        LocalZhangRanShapes provides shapes,
        LocalZhangRanTypographyTokens provides TypographyTokens,
        LocalZhangRanSpacing provides Spacing,
        LocalZhangRanRadii provides Radii,
        LocalZhangRanGlass provides ZhangRanGlassTokens,
        LocalZhangRanGlow provides ZhangRanGlowTokens,
    ) {
        MaterialTheme(
            colorScheme = if (resolvedDark) {
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

/**
 * 应用级主题（组合根，`M4c` A4）。
 *
 * 主题必须包在 `ZhangRanApp` 外层，因此在这里读设置：
 * - **首帧前同步读一次**（DataStore 本地读取，毫秒级）→ 避免"先亮后暗"的主题闪烁（A10）；
 * - 之后订阅 `settings` Flow → 设置页切换主题立即生效；
 * - 读取失败时 `SettingsRepository` 已回退默认值，不会阻塞启动。
 */
@Composable
fun ZhangRanAppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java,
        ).settingsRepository()
    }
    val initial = remember(repository) { runBlocking { repository.settings.first() } }
    val settings by repository.settings.collectAsState(initial = initial)
    ZhangRanTheme(themeMode = settings.themeMode, content = content)
}

/** 从 Compose 的 `Context` 链上找到宿主 Activity（预览/测试宿主可能不是 Activity）。 */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
