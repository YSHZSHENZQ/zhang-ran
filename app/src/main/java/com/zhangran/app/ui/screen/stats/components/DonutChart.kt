/**
 * 分类占比环形图（`M2e-2` §4.1/§1.5）。
 *
 * 图表库选型：**自绘 Canvas**（未引入任何依赖）。原因见交付说明：
 * 当前工程为 AGP 9.4.1 / Compose BOM 2026.09，而 Vico 稳定版面向更早的 Compose/AGP；
 * 引入会造成版本对齐风险，而本页只需"环形占比 + 7 柱"两图，任务书 §1.5 明确允许
 * 回退 Canvas，且不新增依赖＝APK 体积零增长。
 *
 * 颜色全部来自 `ui/theme` 的 token（由调用方传入）。
 */
package com.zhangran.app.ui.screen.stats.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.CategoryShare
import com.zhangran.app.ui.screen.stats.StatsLayout
import com.zhangran.app.ui.screen.stats.compactMoneyText
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor

/**
 * @param shares 分类占比（Domain 已算好；按展示顺序绘制）
 * @param totalMinor 中心 TOTAL 金额
 * @param colors 各段颜色（token）；不足时循环使用
 * @param size 环形直径
 */
@Composable
fun DonutChart(
    shares: List<CategoryShare>,
    totalMinor: Long,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    size: Dp = StatsLayout.donutSizeDp,
    strokeWidth: Dp = StatsLayout.donutStrokeDp,
) {
    val themeColors = ZhangRanTheme.colors
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = MotionDuration.enter, easing = MotionEasings.ease),
        label = "donutSweep",
    )

    val semanticsText = "分类占比：" + shares.joinToString("，") {
        "${it.categoryName} ${it.percent.toInt()}%"
    }

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = semanticsText },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(inset, inset)

            // 底环（无占比时也能看到圆环）
            drawArc(
                color = themeColors.dividerColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )

            if (shares.isEmpty()) return@Canvas
            var start = -90f
            shares.forEachIndexed { index, share ->
                val sweep = (share.percent.toFloat() / 100f) * 360f * progress
                if (sweep > 0f) {
                    drawArc(
                        color = colors[index % colors.size],
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Butt),
                    )
                }
                start += (share.percent.toFloat() / 100f) * 360f
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "TOTAL",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = StatsLayout.donutLabelFontSizeSp.sp,
                    letterSpacing = StatsLayout.donutLabelLetterSpacingSp.sp,
                ),
                color = themeColors.textDisabledColor,
            )
            Spacer(modifier = Modifier.height(StatsLayout.donutCenterGapDp))
            Text(
                text = compactMoneyText(totalMinor),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = StatsLayout.donutValueFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = themeColors.textPrimaryColor,
            )
        }
    }
}
