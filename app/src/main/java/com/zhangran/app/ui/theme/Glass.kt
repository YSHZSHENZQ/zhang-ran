/**
 * 玻璃效果与光晕（唯一落点）。
 *
 * 原型定义（`prototype/账然_UI原型.html`）：
 *
 * ```css
 * .glass { backdrop-filter: blur(26px) saturate(1.4);
 *          background: var(--glass-bg); border: 1px solid var(--glass-border);
 *          box-shadow: var(--glass-shadow); }   /* 0 8px 32px <色>, inset 0 1px 0 <高光> */
 * .glow.g1 { width:260px; height:260px; top:-90px; left:-80px;
 *            background: radial-gradient(circle, var(--glow-1), transparent 68%); filter: blur(60px); }
 * ```
 *
 * 本文件承载：
 * - [ZhangRanGlass]：玻璃数值 token（模糊/描边/阴影/内高光）；
 * - [ZhangRanGlow]：光晕数值 token（三团光晕的尺寸与模糊半径）；
 * - `Modifier.zhangRanGlass`：玻璃卡片修饰符（含**带 Y 偏移的阴影**与**顶部内高光**）；
 * - [ZhangRanGlowLayer]：页面背景光晕层（三团圆形色块，各自模糊自己）。
 *
 * ---
 *
 * ## 降级说明（**有意识的设计取舍，非遗漏**）
 *
 * - **`backdrop-filter: blur(26px)` 未实现**：本轮玻璃元素背后均为**平滑渐变**
 *   （`.page` 有 90px 底部内边距，列表不会滚到 `.tab-bar` 下方；`--modal-bg` 为 96% 不透明），
 *   模糊的视觉收益 ≈ 0。若后续出现"玻璃叠在图片/滚动内容上"或"弹层降为低不透明度"的设计，
 *   需重新评估并引入离屏采样（`RenderEffect`，API 31+，有性能代价）。
 * - **`saturate(1.4)` 未实现**：低对比渐变上饱和度提升肉眼不可辨；Compose 亦无等价滤镜。
 *   同上，若设计变更需重评。
 *
 * 与原型**一致**的部分（本轮已补齐，不再是降级）：
 * - 光晕模糊：`Modifier.blur()` 模糊的是**自身内容**，而 glow 正是独立圆形色块 → **可真实实现**；
 * - `inset 0 1px 0` 内高光：`drawBehind` 绘制顶部 1dp 亮线；
 * - `box-shadow` 的 **8px Y 偏移**：用原生 `Paint.setShadowLayer(blur, dx, dy, color)` 精确绘制
 *   （不再用 `Modifier.shadow(elevation)` 近似，同时解决"CSS blur ≠ elevation"的量纲问题）。
 */
package com.zhangran.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.unit.dp

/**
 * 玻璃效果的数值 token。
 *
 * @property blurRadiusDp 原型 `blur(26px)`（**有意识不生效**，见文件头降级说明）
 * @property saturation 原型 `saturate(1.4)`（**有意识不生效**，见文件头降级说明）
 * @property borderWidthDp 描边宽度（1dp）
 * @property shadowOffsetYDp 外阴影 Y 偏移（8dp，已通过 `setShadowLayer` 真实生效）
 * @property shadowBlurDp 外阴影模糊半径（32dp，直接对应 CSS `blur-radius`）
 * @property innerHighlightOffsetYDp 内高光距顶距离（1dp，原型 `inset 0 1px`）
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

/** 玻璃效果 token 取值（逐条取自原型 `.glass` 与 `--glass-shadow`）。 */
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
 * 背景光晕的数值 token（原型 `.glow.g1/g2/g3`）。
 *
 * 尺寸与模糊半径逐条取自原型：
 * `g1` 260/60、`g2` 280/64、`g3` 240/66。
 *
 * @property glow1SizeDp 第 1 团直径
 * @property glow1BlurDp 第 1 团模糊半径
 * @property glow2SizeDp 第 2 团直径
 * @property glow2BlurDp 第 2 团模糊半径
 * @property glow3SizeDp 第 3 团直径
 * @property glow3BlurDp 第 3 团模糊半径
 */
data class ZhangRanGlow(
    val glow1SizeDp: Int,
    val glow1BlurDp: Int,
    val glow2SizeDp: Int,
    val glow2BlurDp: Int,
    val glow3SizeDp: Int,
    val glow3BlurDp: Int,
)

/** 背景光晕 token 取值（原型 `.glow` 的尺寸与 `filter: blur()`）。 */
val ZhangRanGlowTokens: ZhangRanGlow = ZhangRanGlow(
    glow1SizeDp = 260,
    glow1BlurDp = 60,
    glow2SizeDp = 280,
    glow2BlurDp = 64,
    glow3SizeDp = 240,
    glow3BlurDp = 66,
)

/**
 * 玻璃卡片修饰符。
 *
 * 绘制顺序（自下而上）：外阴影 → 半透明填充 → 顶部内高光 → 1dp 描边。
 *
 * @param colors 当前主题颜色（填充/描边/阴影/高光都来自它）
 * @param shape 圆角形状（通常传 `ZhangRanTheme.shapes.card`；圆角半径从 shape 反推）
 * @param tokens 玻璃数值 token
 */
fun Modifier.zhangRanGlass(
    colors: ZhangRanColors,
    shape: RoundedCornerShape,
    tokens: ZhangRanGlass = ZhangRanGlassTokens,
): Modifier = this
    // 外阴影：用原生 setShadowLayer 精确实现 Y 偏移（原型 `0 8px 32px`）
    .drawBehind {
        val radiusPx = shape.topStart.toPx(size, this)
        val paint = Paint().apply {
            isAntiAlias = true
            // 阴影的载体本身必须透明，只绘制阴影
            color = Color.Transparent
        }
        paint.asFrameworkPaint().setShadowLayer(
            tokens.shadowBlurDp.toFloat(),
            0f,
            tokens.shadowOffsetYDp.toFloat(),
            colors.glassShadowColor,
        )
        drawIntoCanvas { canvas ->
            // saveLayer 的 bounds 必须**比卡片本身大**：阴影画在卡片外侧，
            // 若 bounds 恰好等于卡片大小，阴影会被图层裁掉（真机表现为"几乎没有阴影"）。
            val inflate = tokens.shadowBlurDp.toFloat() + tokens.shadowOffsetYDp.toFloat()
            canvas.saveLayer(
                Rect(
                    left = -inflate,
                    top = -inflate,
                    right = size.width + inflate,
                    bottom = size.height + inflate,
                ),
                Paint(),
            )
            canvas.drawRoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                radiusX = radiusPx,
                radiusY = radiusPx,
                paint = paint,
            )
            canvas.restore()
        }
    }
    // 玻璃填充（原型 --glass-bg，带 alpha）
    .background(color = colors.surfaceColor, shape = shape)
    // 顶部内高光（原型 `inset 0 1px 0`）
    .drawBehind {
        if (tokens.innerHighlightHeightDp <= 0) return@drawBehind
        val radiusPx = shape.topStart.toPx(size, this)
        val lineHeightPx = tokens.innerHighlightHeightDp.dp.toPx()
        val y = lineHeightPx / 2f + tokens.innerHighlightOffsetYDp.dp.toPx()
        drawLine(
            color = colors.glassInnerHighlightColor,
            start = Offset(radiusPx, y),
            end = Offset(size.width - radiusPx, y),
            strokeWidth = lineHeightPx,
        )
    }
    // 描边（原型 --glass-border）
    .border(width = tokens.borderWidthDp.dp, color = colors.glassBorderColor, shape = shape)

/**
 * 页面背景光晕层（原型 `.glow.g1/g2/g3`）。
 *
 * 用法：放在页面最底层（`Box` 的第一个子元素），不影响布局与其他内容的点击。
 *
 * 说明：三团色块各自用 `Modifier.blur()` **模糊自身**——这正是原型 `filter: blur()` 的语义，
 * 因此与原型视觉效果一致（区别于无法实现的 `backdrop-filter`）。
 *
 * 位置为原型坐标的近似（原型用绝对定位 `top/left/right/bottom`，此处用对齐 + 偏移表达）。
 */
@Composable
fun ZhangRanGlowLayer(modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    val glow = ZhangRanTheme.glow

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (-80).dp, y = (-90).dp)
                .size(glow.glow1SizeDp.dp)
                .background(color = colors.glow1Color, shape = CircleShape)
                .blur(glow.glow1BlurDp.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 120.dp, y = 180.dp)
                .size(glow.glow2SizeDp.dp)
                .background(color = colors.glow2Color, shape = CircleShape)
                .blur(glow.glow2BlurDp.dp),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 30.dp, y = 70.dp)
                .size(glow.glow3SizeDp.dp)
                .background(color = colors.glow3Color, shape = CircleShape)
                .blur(glow.glow3BlurDp.dp),
        )
    }
}
