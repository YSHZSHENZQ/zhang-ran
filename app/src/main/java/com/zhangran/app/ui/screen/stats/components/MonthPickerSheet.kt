/**
 * 月份选择器（`M2e-2` §1.4/§4.3）。
 *
 * 底部弹层：列出"最早有记录的月份 → 当前月"（最新在前）；未来月不会出现在列表里（Domain 只给
 * 到当前月，VM 还会再挡一次）。样式与分类/账户编辑弹层一致。
 */
package com.zhangran.app.ui.screen.stats.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.usecase.StatsMonth
import com.zhangran.app.ui.screen.stats.StatsLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.modalFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.scrimColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor

/**
 * @param months 可选月份（最新在前）
 * @param selectedPeriodKey 当前查看月
 * @param onSelect 选中某月（已保证不是未来月）
 */
@Composable
fun MonthPickerSheet(
    months: List<StatsMonth>,
    selectedPeriodKey: String,
    onDismiss: () -> Unit,
    onSelect: (StatsMonth) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭月份选择" },
        )
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
            ) + slideInVertically(
                animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
                initialOffsetY = { fullHeight -> fullHeight },
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZhangRanTheme.shapes.dialog)
                    .background(color = colors.backgroundColor)
                    .background(color = colors.modalFillColor)
                    .padding(StatsLayout.sheetPaddingDp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(StatsLayout.sheetHandleHeightDp)
                        .fillMaxWidth(StatsLayout.sheetHandleWidthFraction)
                        .clip(ZhangRanTheme.shapes.handle)
                        .background(color = colors.textDisabledColor),
                )
                Spacer(modifier = Modifier.height(StatsLayout.sheetHandleGapDp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "选择月份",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimaryColor,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", color = colors.textDisabledColor)
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = StatsLayout.monthSheetMaxHeightDp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    months.forEachIndexed { index, month ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(StatsLayout.hairlineWidthDp)
                                    .background(color = colors.dividerColor),
                            )
                        }
                        val selected = month.periodKey == selectedPeriodKey
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = StatsLayout.touchTargetDp)
                                .clip(ZhangRanTheme.shapes.option)
                                .clickable { onSelect(month) }
                                .padding(horizontal = StatsLayout.monthRowPaddingHorizontalDp)
                                .semantics {
                                    contentDescription =
                                        "${month.displayText}${if (selected) "，当前查看" else ""}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = month.displayText,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = StatsLayout.monthRowFontSizeSp.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (selected) colors.primaryColor else colors.textPrimaryColor,
                            )
                            if (selected) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = colors.primaryColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
