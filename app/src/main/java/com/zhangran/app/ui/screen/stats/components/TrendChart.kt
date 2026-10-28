/**
 * 7 柱按天趋势（`M2e-2` §1.3/§4.3）。
 *
 * 自绘 Canvas（未引入依赖，理由见 `DonutChart.kt` 文件头）。
 * 每个点 = 一天（周一→周日）；跨月缺省天金额为 0（不画柱）。入场时长
 * `MotionDuration.enter`（380ms）。
 */
package com.zhangran.app.ui.screen.stats.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.DailyPoint
import com.zhangran.app.ui.screen.stats.StatsLayout
import com.zhangran.app.ui.screen.stats.WEEKDAY_LABELS
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.textDisabledColor

/**
 * @param points 固定 7 个点（`weekdayIndex` 0..6）
 * @param semanticsText 读屏文案（由调用方拼好，含每天金额）
 */
@Composable
fun TrendChart(
    points: List<DailyPoint>,
    semanticsText: String,
    modifier: Modifier = Modifier,
    height: Dp = StatsLayout.trendChartHeightDp,
) {
    val colors = ZhangRanTheme.colors
    var played by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { played = true }
    val progress by animateFloatAsState(
        targetValue = if (played) 1f else 0f,
        animationSpec = tween(durationMillis = MotionDuration.enter, easing = MotionEasings.ease),
        label = "trendBars",
    )
    val maxAmount = points.maxOfOrNull { it.amountMinor.coerceAtLeast(0L) } ?: 0L
    val brush = Brush.verticalGradient(
        colors = listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsText },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            if (maxAmount <= 0L) return@Canvas
            val slot = size.width / points.size
            val barWidth = slot * StatsLayout.trendBarWidthFraction
            val radius = CornerRadius(StatsLayout.trendBarRadiusDp.toPx())
            val maxBarHeight = size.height * StatsLayout.trendBarMaxHeightFraction

            points.forEachIndexed { index, point ->
                val positive = point.amountMinor.coerceAtLeast(0L)
                if (positive <= 0L) return@forEachIndexed
                val fraction = positive.toFloat() / maxAmount.toFloat()
                val barHeight = maxBarHeight * fraction * progress
                val left = index * slot + (slot - barWidth) / 2f
                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = radius,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = StatsLayout.trendLabelTopGapDp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            WEEKDAY_LABELS.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = StatsLayout.trendLabelFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
