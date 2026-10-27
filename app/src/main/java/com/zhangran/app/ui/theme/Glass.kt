/**
 * 玻璃效果 token（唯一落点）。
 *
 * 原型定义（`prototype/账然_UI原型.html` `.glass`）：
 *
 * ```css
 * .glass {
 *   backdrop-filter: blur(26px) saturate(1.4);
 *   background: var(--glass-bg);
 *   border: 1px solid var(--glass-border);
 *   box-shadow: var(--glass-shadow);  // 0 8px 32px <色>, inset 0 1px 0 <白色高光>
 * }
 * ```
 *
 * **与原型的具体差异（必须知晓，不得伪称一致）**：
 * - Compose 的 `Modifier.blur()` 只模糊**自身内容**，无法对"卡片背后"的画面做背景模糊
 *   （`backdrop-filter` 在 Compose 中**没有等价实现**）。因此本文件采用**降级方案**：
 *   半透明填充 + 1dp 描边 + 外阴影 + 顶部内高光，视觉上接近但**不是真实背景模糊**。
 * - `box-shadow` 的 `0 8px 32px`：Compose `Modifier.shadow` 只接受 elevation，**不支持 Y 偏移**，
 *   故以 `shadowBlurDp` 作为 elevation 近似；`shadowOffsetYDp` 仅作为 token 记录，供将来若改用
 *   `drawBehind` 自绘阴影时使用。
 * - `saturate(1.4)`：Compose 无饱和度滤镜（需 `RenderEffect`，API 31+ 且要作用于图层），
 *   以 token 记录但**不应用**。
 *
 * 颜色来自 [ZhangRanColors]（`glassFill`/`surface`、`glassBorder`、`glassShadowColor`、
 * `glassInnerHighlight`），本文件只承载**与颜色无关的数值**。
 */
package com.zhangran.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp

/**
 * 玻璃效果的数值 token。
 *
 * @property blurRadiusDp 原型 `blur(26px)`（**当前不生效**：Compose 无法做背景模糊）
 * @property saturation 原型 `saturate(1.4)`（**当前不生效**：无饱和度滤镜）
 * @property borderWidthDp 描边宽度（1dp）
 * @property shadowOffsetYDp 外阴影 Y 偏移（8dp，目前仅记录）
 * @property shadowBlurDp 外阴影模糊半径 / Compose elevation 近似值（32dp）
 * @property innerHighlightOffsetYDp 内高光 Y 偏移（1dp，用于顶部亮线）
 * @property innerHighlightHeightDp 内高光线高度（1dp）
 */
data class ZhangRanGlass(
    val blurRadiusDp: Int,
    val saturation: Float,
    val borderWidthDp: Int,
    val shadowOffsetYDp: Int,
    val shadowBlurDp: Int,
    val innerHighlightOffsetYDp: Int,
    val innerHighlightHeightDp: Int,
)

/** 玻璃效果 token 取值（逐条取自原型 `.glass`）。 */
val ZhangRanGlassTokens: ZhangRanGlass = ZhangRanGlass(
    blurRadiusDp = 26,
    saturation = 1.4f,
    borderWidthDp = 1,
    shadowOffsetYDp = 8,
    shadowBlurDp = 32,
    innerHighlightOffsetYDp = 1,
    innerHighlightHeightDp = 1,
)

/**
 * 玻璃卡片修饰符（**降级实现**：无真实背景模糊）。
 *
 * 用法（页面/组件）：
 * ```kotlin
 * Box(
 *     modifier = Modifier.zhangRanGlass(
 *         colors = ZhangRanTheme.colors,
 *         shape = ZhangRanTheme.shapes.card,
 *     ),
 * ) { … }
 * ```
 *
 * @param colors 当前主题的颜色 token（填充/描边/阴影色都来自它）
 * @param shape 圆角形状（默认 `ZhangRanTheme.shapes.card`；调用方通常传当前主题的 card）
 * @param tokens 玻璃效果的数值 token
 */
fun Modifier.zhangRanGlass(
    colors: ZhangRanColors,
    shape: RoundedCornerShape,
    tokens: ZhangRanGlass = ZhangRanGlassTokens,
): Modifier = this
    // 外阴影：以 elevation 近似（Compose 不支持 Y 偏移与颜色精确控制）
    .shadow(
        elevation = tokens.shadowBlurDp.dp,
        shape = shape,
        ambientColor = colors.glassShadowColorValue,
        spotColor = colors.glassShadowColorValue,
    )
    // 玻璃填充（原型 --glass-bg，带 alpha）
    .background(color = colors.surfaceColor, shape = shape)
    // 描边（原型 --glass-border）
    .border(width = tokens.borderWidthDp.dp, color = colors.glassBorderColor, shape = shape)
