/**
 * 统计页（`M2e-2` 完整重写；行为 SSOT：`docs/06` §4.2，视觉：原型 `data-page="stats"`）。
 *
 * 结构（底部导航顶级页，无返回键）：
 * ```text
 * 统计                                   [2026 年 10 月 ▾]
 * [MONTHLY OVERVIEW]  ¥3,847.20
 *   日均支出 ¥128.24 | 最高单日 ¥486.00 | 记账笔数 128
 * [环形图 TOTAL ¥3.8k]  餐饮 42% / 购物 24% / 交通 20% / 其他 14%
 * [WEEKLY TREND]  ‹ 本周 ›   周一…周日 7 柱
 * ```
 *
 * 约束：
 * - 页面不计算任何指标/占比（Domain 提供，R6）；
 * - 无"预算使用情况"块（A12）；
 * - 月份只到当前月、周只到"包含今天的那一周"（A8/A10）。
 */
package com.zhangran.app.ui.screen.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.domain.model.CategoryShare
import com.zhangran.app.domain.model.MonthlyStats
import com.zhangran.app.domain.model.WeekTrend
import com.zhangran.app.domain.usecase.ObserveMonthlyStatsUseCase
import com.zhangran.app.domain.usecase.StatsMonth
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import com.zhangran.app.ui.screen.stats.components.DonutChart
import com.zhangran.app.ui.screen.stats.components.MonthPickerSheet
import com.zhangran.app.ui.screen.stats.components.TrendChart
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.incomeColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.warningColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/**
 * 统计页入口。
 *
 * @param onNavigateToAdd Empty 态「去记一笔」的跳转（由 `ZhangRanNavHost` 注入，经批准的最小接线）
 */
@Composable
fun StatsScreen(onNavigateToAdd: () -> Unit = {}) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        StatsEntryPoint::class.java,
    )
    val viewModel: StatsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                StatsViewModel(
                    observeStats = entryPoint.observeMonthlyStatsUseCase(),
                    clock = entryPoint.clock(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is StatsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StatsContent(
            state = state,
            onRetry = viewModel::refresh,
            onSelectMonth = viewModel::selectMonth,
            onPreviousWeek = viewModel::previousWeek,
            onNextWeek = viewModel::nextWeek,
            onNavigateToAdd = onNavigateToAdd,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 无状态内容（测试渲染三态/图表/切月切周）。 */
@Composable
fun StatsContent(
    state: StatsUiState,
    onRetry: () -> Unit = {},
    onSelectMonth: (StatsMonth) -> Unit = {},
    onPreviousWeek: () -> Unit = {},
    onNextWeek: () -> Unit = {},
    onNavigateToAdd: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    var showMonthPicker by remember { mutableStateOf(false) }
    val months = when (state) {
        is StatsUiState.Content -> state.months
        is StatsUiState.Empty -> state.months
        else -> emptyList()
    }
    val selectedPeriodKey = when (state) {
        is StatsUiState.Content -> state.stats.periodKey
        is StatsUiState.Empty -> state.periodKey
        else -> null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StatsHead(
                periodKey = selectedPeriodKey,
                onPickMonth = { if (months.isNotEmpty()) showMonthPicker = true },
            )

            when (state) {
                StatsUiState.Loading -> StatsSkeleton()

                is StatsUiState.Error -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(StatsLayout.itemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                is StatsUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EmptyStatsCard(periodKey = state.periodKey, onGoAdd = onNavigateToAdd)
                    Spacer(modifier = Modifier.height(StatsLayout.pageBottomPaddingDp))
                }

                is StatsUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    OverviewCard(stats = state.stats)
                    ShareCard(stats = state.stats, palette = StatsPalette.of())
                    TrendCard(
                        stats = state.stats,
                        content = state,
                        onPreviousWeek = onPreviousWeek,
                        onNextWeek = onNextWeek,
                    )
                    Spacer(modifier = Modifier.height(StatsLayout.pageBottomPaddingDp))
                }
            }
        }

        if (showMonthPicker && selectedPeriodKey != null) {
            MonthPickerSheet(
                months = months,
                selectedPeriodKey = selectedPeriodKey,
                onDismiss = { showMonthPicker = false },
                onSelect = { month ->
                    showMonthPicker = false
                    onSelectMonth(month)
                },
            )
        }
    }
}

/** 页头：统计 / MONTHLY STATS + 月份胶囊（A10/A19）。 */
@Composable
private fun StatsHead(periodKey: String?, onPickMonth: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = StatsLayout.pageHeadPaddingTopDp,
                bottom = StatsLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "统计",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = StatsLayout.logoFontSizeSp.sp,
                    letterSpacing = StatsLayout.logoLetterSpacingSp.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(StatsLayout.headSubGapDp))
            Text(
                text = "MONTHLY STATS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = StatsLayout.headSubFontSizeSp.sp,
                    letterSpacing = StatsLayout.headSubLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
        if (periodKey != null) {
            val label = periodKeyDisplay(periodKey)
            Row(
                modifier = Modifier
                    .heightIn(min = StatsLayout.pillMinHeightDp)
                    .clip(ZhangRanTheme.shapes.badge)
                    .clickable(onClick = onPickMonth)
                    .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.badge)
                    .padding(
                        horizontal = StatsLayout.pillPaddingHorizontalDp,
                        vertical = StatsLayout.pillPaddingVerticalDp,
                    )
                    .semantics { contentDescription = "当前查看 $label，双击切换月份" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(StatsLayout.pillDotGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .size(StatsLayout.pillDotSizeDp)
                        .clip(ZhangRanTheme.shapes.badge)
                        .background(color = colors.primaryColor),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = StatsLayout.pillFontSizeSp.sp,
                    ),
                    color = colors.textSecondaryColor,
                )
                Text(
                    text = "▾",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = StatsLayout.pillFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                )
            }
        }
    }
}

/** 概览卡（A1）。 */
@Composable
private fun OverviewCard(stats: MonthlyStats) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StatsLayout.cardMarginHorizontalDp,
                end = StatsLayout.cardMarginHorizontalDp,
                bottom = StatsLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(StatsLayout.cardPaddingDp)
            .semantics { contentDescription = overviewSemantics(stats) },
    ) {
        CardHead(text = "MONTHLY OVERVIEW")
        Spacer(modifier = Modifier.height(StatsLayout.cardHeadGapDp))
        Text(
            text = statsMoneyText(stats.expenseMinor),
            style = MaterialTheme.typography.titleLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = StatsLayout.overviewAmountFontSizeSp.sp,
                letterSpacing = StatsLayout.overviewAmountLetterSpacingSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.textPrimaryColor,
        )
        Spacer(modifier = Modifier.height(StatsLayout.overviewAmountGapDp))
        Row(modifier = Modifier.fillMaxWidth()) {
            StatCell(
                label = "日均支出",
                value = statsMoneyText(stats.dailyAverageMinor),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "最高单日",
                value = statsMoneyText(stats.maxDailyExpenseMinor),
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = "记账笔数",
                value = recordCountText(stats.recordCount),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = StatsLayout.statLabelFontSizeSp.sp,
                letterSpacing = StatsLayout.statLabelLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(StatsLayout.statValueGapDp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = StatsLayout.statValueFontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.textPrimaryColor,
        )
    }
}

/** 占比卡（环形图 + 图例）。 */
@Composable
private fun ShareCard(stats: MonthlyStats, palette: List<Color>) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StatsLayout.cardMarginHorizontalDp,
                end = StatsLayout.cardMarginHorizontalDp,
                bottom = StatsLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(StatsLayout.cardPaddingDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatsLayout.shareGapDp),
    ) {
        DonutChart(
            shares = stats.categoryBreakdown,
            totalMinor = stats.expenseMinor,
            colors = palette,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(StatsLayout.legendGapDp),
        ) {
            if (stats.categoryBreakdown.isEmpty()) {
                Text(
                    text = "本月消费不大于 0，暂无分类占比",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDisabledColor,
                )
            } else {
                stats.categoryBreakdown.forEachIndexed { index, share ->
                    LegendRow(share = share, color = palette[index % palette.size])
                }
            }
        }
    }
}

@Composable
private fun LegendRow(share: CategoryShare, color: Color) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(StatsLayout.legendDotGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(StatsLayout.legendDotSizeDp)
                .clip(RoundedCornerShape(StatsLayout.legendDotRadiusDp))
                .background(color = color),
        )
        Text(
            text = share.categoryName,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = StatsLayout.legendFontSizeSp.sp,
            ),
            color = colors.textSecondaryColor,
            maxLines = 1,
        )
        Text(
            text = percentText(share.percent),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = StatsLayout.legendFontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.textPrimaryColor,
        )
    }
}

/** 趋势卡（7 柱 + 周切换；A7/A8/A9）。 */
@Composable
private fun TrendCard(
    stats: MonthlyStats,
    content: StatsUiState.Content,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val canPrevious = StatsWeekRules.canGoPrevious(content.weekIndex)
    val canNext = StatsWeekRules.canGoNext(content.weekIndex, content.maxWeekIndex)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StatsLayout.cardMarginHorizontalDp,
                end = StatsLayout.cardMarginHorizontalDp,
                bottom = StatsLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(StatsLayout.cardPaddingDp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardHead(text = "WEEKLY TREND", modifier = Modifier.weight(1f))
            Text(
                text = "‹",
                modifier = Modifier
                    .clip(ZhangRanTheme.shapes.option)
                    .clickable(enabled = canPrevious, onClick = onPreviousWeek)
                    .padding(horizontal = StatsLayout.weekArrowPaddingHorizontalDp)
                    .semantics { contentDescription = "上一周" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = StatsLayout.weekArrowFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (canPrevious) colors.primaryColor else colors.textDisabledColor,
            )
            Text(
                text = weekLabel(
                    stats = stats,
                    weekIndex = content.weekIndex,
                    isCurrentMonth = content.isCurrentMonth,
                    currentWeekIndex = content.maxWeekIndex,
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = StatsLayout.weekLabelFontSizeSp.sp,
                ),
                color = colors.textSecondaryColor,
            )
            Text(
                text = "›",
                modifier = Modifier
                    .clip(ZhangRanTheme.shapes.option)
                    .clickable(enabled = canNext, onClick = onNextWeek)
                    .padding(horizontal = StatsLayout.weekArrowPaddingHorizontalDp)
                    .semantics { contentDescription = "下一周" },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = StatsLayout.weekArrowFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = if (canNext) colors.primaryColor else colors.textDisabledColor,
            )
        }
        Spacer(modifier = Modifier.height(StatsLayout.cardHeadGapDp))
        TrendPager(
            stats = stats,
            content = content,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
        )
    }
}

/**
 * 单周趋势 + 左右滑动切周。
 *
 * 用 `pointerInput` 检测水平拖动（阈值 ≈ 120px），到边界（首周 / 当前周）时手势无效，
 * 与 A8「不可到未来周」一致。
 */
@Composable
private fun TrendPager(
    stats: MonthlyStats,
    content: StatsUiState.Content,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
) {
    val week = stats.weeks[content.weekIndex]
    var dragAccumulator by remember { mutableStateOf(0f) }
    TrendChart(
        points = week.points,
        semanticsText = trendSemantics(stats, week, content),
        modifier = Modifier.pointerInput(content.weekIndex, content.maxWeekIndex) {
            detectHorizontalDragGestures(
                onDragEnd = { dragAccumulator = 0f },
                onDragCancel = { dragAccumulator = 0f },
            ) { _, dragAmount ->
                dragAccumulator += dragAmount
                if (dragAccumulator > StatsLayout.swipeThresholdPx) {
                    dragAccumulator = 0f
                    if (StatsWeekRules.canGoPrevious(content.weekIndex)) onPreviousWeek()
                } else if (dragAccumulator < -StatsLayout.swipeThresholdPx) {
                    dragAccumulator = 0f
                    if (StatsWeekRules.canGoNext(content.weekIndex, content.maxWeekIndex)) onNextWeek()
                }
            }
        },
    )
}

/** Empty（该月无记录；A15）。 */
@Composable
private fun EmptyStatsCard(periodKey: String, onGoAdd: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = StatsLayout.cardMarginHorizontalDp,
                end = StatsLayout.cardMarginHorizontalDp,
                bottom = StatsLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(StatsLayout.emptyCardPaddingDp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "📊",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = StatsLayout.emptyEmojiSizeSp.sp,
            ),
        )
        Spacer(modifier = Modifier.height(StatsLayout.itemGapDp))
        Text(
            text = "${periodKeyDisplay(periodKey)}还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimaryColor,
        )
        Spacer(modifier = Modifier.height(StatsLayout.emptyTextGapDp))
        Text(
            text = "记一笔之后，这里会显示消费、分类占比与按天趋势。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(StatsLayout.emptyActionGapDp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = StatsLayout.touchTargetDp)
                .clip(ZhangRanTheme.shapes.button)
                .background(color = colors.primaryColor)
                .clickable(onClick = onGoAdd)
                .padding(vertical = StatsLayout.confirmPaddingDp)
                .semantics { contentDescription = "去记一笔" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "去记一笔",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.backgroundColor,
            )
        }
    }
}

@Composable
private fun CardHead(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = StatsLayout.cardHeadFontSizeSp.sp,
            letterSpacing = StatsLayout.cardHeadLetterSpacingSp.sp,
        ),
        color = ZhangRanTheme.colors.textDisabledColor,
    )
}

/** 加载骨架。 */
@Composable
private fun StatsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(StatsLayout.pageBottomPaddingDp)) {
        repeat(3) { index ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = StatsLayout.cardMarginHorizontalDp)
                    .zhangRanGlass(colors = ZhangRanTheme.colors, shape = ZhangRanTheme.shapes.card)
                    .clip(ZhangRanTheme.shapes.card)
                    .padding(StatsLayout.cardPaddingDp),
                verticalArrangement = Arrangement.spacedBy(StatsLayout.itemGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(StatsLayout.skeletonHeadFraction)
                        .height(StatsLayout.skeletonLineHeight)
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = StatsLayout.skeletonAlpha },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(if (index == 0) StatsLayout.skeletonAmountFraction else 1f)
                        .height(
                            if (index == 0) {
                                StatsLayout.skeletonAmountHeight
                            } else {
                                StatsLayout.skeletonChartHeight
                            },
                        )
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = StatsLayout.skeletonAlpha },
                )
            }
        }
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

// ------------------------------------------------------------------ 无障碍文案

private fun overviewSemantics(stats: MonthlyStats): String {
    fun plain(minor: Long): String =
        HomeViewModel.formatMoney(minor, sign = AmountSign.AUTO, withCurrencySymbol = false)
    return "本月消费 ${plain(stats.expenseMinor)} 元，日均 ${plain(stats.dailyAverageMinor)} 元，" +
        "最高单日 ${plain(stats.maxDailyExpenseMinor)} 元，共 ${stats.recordCount} 笔"
}

private fun trendSemantics(
    stats: MonthlyStats,
    week: WeekTrend,
    content: StatsUiState.Content,
): String {
    fun plain(minor: Long): String =
        HomeViewModel.formatMoney(minor, sign = AmountSign.AUTO, withCurrencySymbol = false)
    val prefix = weekLabel(
        stats = stats,
        weekIndex = week.weekIndex,
        isCurrentMonth = content.isCurrentMonth,
        currentWeekIndex = content.maxWeekIndex,
    )
    val days = week.points.mapIndexed { index, point ->
        val label = WEEKDAY_LABELS.getOrElse(index) { "" }
        "$label ${plain(point.amountMinor)} 元"
    }
    return listOf(prefix).plus(days).joinToString("，")
}

/** `"2026-10"` → `"2026 年 10 月"`。 */
internal fun periodKeyDisplay(periodKey: String): String {
    val parts = periodKey.split("-")
    if (parts.size != 2) return periodKey
    val year = parts[0].toIntOrNull() ?: return periodKey
    val month = parts[1].toIntOrNull() ?: return periodKey
    return "$year 年 $month 月"
}

/** 图表配色（全部来自 `ui/theme` token；顺序与图例一致；第 5 色用中性灰表示汇合的「其他」）。 */
internal object StatsPalette {
    @Composable
    fun of(): List<Color> {
        val colors = ZhangRanTheme.colors
        return listOf(
            colors.primaryColor,
            colors.accentGradientEndColor,
            colors.incomeColor,
            colors.warningColor,
            colors.textSecondaryColor,
        )
    }
}

/**
 * 统计页的页面级尺寸（原型 `data-page="stats"` CSS；主题 token 未覆盖的集中在此）。
 */
internal object StatsLayout {

    // 页头（.app-head / .status-pill）
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 12.dp
    val headSubGapDp: Dp = 4.dp
    const val logoFontSizeSp: Float = 22f
    const val logoLetterSpacingSp: Float = 3f
    const val headSubFontSizeSp: Float = 9f
    const val headSubLetterSpacingSp: Float = 3.2f
    val pillMinHeightDp: Dp = 26.dp
    val pillPaddingHorizontalDp: Dp = 10.dp
    val pillPaddingVerticalDp: Dp = 4.dp
    val pillDotSizeDp: Dp = 5.dp
    val pillDotGapDp: Dp = 6.dp
    const val pillFontSizeSp: Float = 10f

    // 卡片（.card / .card-head）
    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 14.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    // 概览（.stat-overview-amount / .card-stats / .stat-k / .stat-v）
    const val overviewAmountFontSizeSp: Float = 30f
    const val overviewAmountLetterSpacingSp: Float = -1.4f
    val overviewAmountGapDp: Dp = 14.dp
    const val statLabelFontSizeSp: Float = 9.5f
    const val statLabelLetterSpacingSp: Float = 1.2f
    const val statValueFontSizeSp: Float = 13.5f
    val statValueGapDp: Dp = 5.dp

    // 环形图（.donut 104px / 内圈 72px / .legend）
    val donutSizeDp: Dp = 104.dp
    val donutStrokeDp: Dp = 16.dp
    val shareGapDp: Dp = 18.dp
    val donutCenterGapDp: Dp = 2.dp
    const val donutLabelFontSizeSp: Float = 9f
    const val donutLabelLetterSpacingSp: Float = 1f
    const val donutValueFontSizeSp: Float = 14f
    val legendGapDp: Dp = 10.dp
    val legendDotSizeDp: Dp = 8.dp
    val legendDotRadiusDp: Dp = 3.dp
    val legendDotGapDp: Dp = 8.dp
    const val legendFontSizeSp: Float = 12f

    // 趋势（.chart 86px / .chart-col / .chart-bar / .chart-day）
    val trendChartHeightDp: Dp = 86.dp
    const val trendBarWidthFraction: Float = 0.7f
    val trendBarRadiusDp: Dp = 6.dp
    const val trendBarMaxHeightFraction: Float = 1f
    val trendLabelTopGapDp: Dp = 6.dp
    const val trendLabelFontSizeSp: Float = 9f
    val weekArrowPaddingHorizontalDp: Dp = 10.dp
    const val weekArrowFontSizeSp: Float = 14f
    const val weekLabelFontSizeSp: Float = 11f
    const val swipeThresholdPx: Float = 120f

    // Empty
    val emptyCardPaddingDp: Dp = 22.dp
    val emptyTextGapDp: Dp = 6.dp
    val emptyActionGapDp: Dp = 16.dp
    const val emptyEmojiSizeSp: Float = 34f
    val itemGapDp: Dp = 12.dp
    val touchTargetDp: Dp = 48.dp
    val confirmPaddingDp: Dp = 14.dp
    val pageBottomPaddingDp: Dp = 24.dp

    // 月份选择弹层
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleHeightDp: Dp = 2.dp
    const val sheetHandleWidthFraction: Float = 0.3f
    val sheetHandleGapDp: Dp = 10.dp
    val monthSheetMaxHeightDp: Dp = 320.dp
    val monthRowPaddingHorizontalDp: Dp = 4.dp
    const val monthRowFontSizeSp: Float = 14f
    val hairlineWidthDp: Dp = 1.dp

    // 骨架
    const val skeletonHeadFraction: Float = 0.3f
    const val skeletonAmountFraction: Float = 0.5f
    val skeletonLineHeight: Dp = 10.dp
    val skeletonAmountHeight: Dp = 28.dp
    val skeletonChartHeight: Dp = 72.dp
    const val skeletonAlpha: Float = 0.5f
}

/** Hilt 入口（不引入 `hilt-navigation-compose`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface StatsEntryPoint {
    fun observeMonthlyStatsUseCase(): ObserveMonthlyStatsUseCase

    fun clock(): Clock
}
