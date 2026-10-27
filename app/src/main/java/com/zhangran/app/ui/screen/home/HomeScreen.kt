/**
 * 首页（`docs/06` §4.1 首页、`M2e-1b` 整页重写）。
 *
 * 结构（与原型 `data-page="home"` 一致，B 类）：
 * ```text
 * 顶栏「账然 / AUTO LEDGER · 状态」+ 搜索 + 待确认
 * 余额卡片（本月支出大字 + INCOME / BALANCE）
 * 日期分组（今天 / 昨天 / M月D日 …）
 * 交易行（渐变图标 + 商户 + 分类·时间·来源 + 金额）
 * ```
 *
 * 分层（`docs/04` §4、§20）：
 * - [HomeScreen] 只负责「取 ViewModel + 收集状态 + 转交导航」；
 * - [HomeContent] 是**无状态**可组合函数，便于 Compose 测试直接渲染四态；
 * - 分组、金额与时间格式化全部在 `HomeViewModel` 完成，本文件不做业务计算。
 *
 * token 纪律（`AGENTS.md` §7.2）：页面不写字面色值/裸尺寸/字面时长。原型中确实没有对应
 * token 的页面级尺寸与字号（如 34dp 圆钮、36sp 大字、38dp 图标块）**集中**在 [HomeLayout]，
 * 统一来自原型 `.app-head` / `.balance-card` / `.tx-row` / `.section-label` 的 CSS 取值。
 */
package com.zhangran.app.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.domain.usecase.DeleteTransactionUseCase
import com.zhangran.app.domain.usecase.ObserveHomeSummaryUseCase
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import com.zhangran.app.ui.common.TransactionDeletionEvents
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.screen.home.components.BalanceCard
import com.zhangran.app.ui.screen.home.components.HomeEmptyState
import com.zhangran.app.ui.screen.home.components.HomeErrorState
import com.zhangran.app.ui.screen.home.components.HomeHeader
import com.zhangran.app.ui.screen.home.components.HomeSkeleton
import com.zhangran.app.ui.screen.home.components.TransactionRow
import com.zhangran.app.ui.theme.MotionDistance
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.enterSlideDp
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 首页入口（无参，供导航直接调用）。
 *
 * @param onNavigate 导航回调（参数为 `Destination` 的 route；由 `ZhangRanNavHost` 注入）
 */
@Composable
fun HomeScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    HomeEntryPoint::class.java,
                )
                HomeViewModel(
                    observeTransactions = entryPoint.observeTransactionsUseCase(),
                    observeHomeSummary = entryPoint.observeHomeSummaryUseCase(),
                    undoDeleteTransaction = entryPoint.deleteTransactionUseCase(),
                )
            }
        },
    )
    val state by homeViewModel.uiState.collectAsState()

    // 「已删除，可撤销」（`M2b` §1.2）：详情页删除成功后返回首页，在这里弹一次性 SnackBar。
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(homeViewModel) {
        TransactionDeletionEvents.lastDeleted.collect { event ->
            if (event == null) return@collect
            // 先消费，避免返回首页/重组时重复弹出
            TransactionDeletionEvents.clear()
            val result = snackbarHostState.showSnackbar(
                message = "已删除",
                actionLabel = "撤销",
                withDismissAction = false,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                homeViewModel.undoDelete(event.transactionId)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeContent(
            state = state,
            onNavigate = onNavigate,
            onRetry = homeViewModel::refresh,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 首页内容（无状态）。
 *
 * @param state 四种 UI 状态
 * @param onNavigate 导航回调（顶栏、交易行、空态引导共用）
 * @param onRetry 错误态的重试回调
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onNavigate: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            // 背景：四段渐变（原型 `.screen` 的 165° 渐变）。
            // 说明：原型另有 `.glow.g1/g2/g3` 三团光晕；共享组件 `ZhangRanGlowLayer` 目前用
            // "实心圆 + blur" 近似原型的 "radial-gradient + blur"，实机会画出明显的色块边界
            // （Glass.kt 不在本任务文件白名单内，未改）。首页与 M2a 记账页保持一致：只铺渐变，
            // 待 UI-0 修正 Glass.kt 后再统一叠加光晕。见任务交付说明。
            .zhangRanBackground(colors),
    ) {
        when (state) {
            is HomeUiState.Content -> HomeContentBody(state = state, onNavigate = onNavigate)

            HomeUiState.Loading -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                HomeHeader(pendingCount = 0, onNavigate = onNavigate)
                HomeSkeleton()
            }

            HomeUiState.Empty -> Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(pendingCount = 0, onNavigate = onNavigate)
                HomeEmptyState(
                    onAddTransaction = { onNavigate(Destination.Add.route) },
                    onCheckAutoLedger = { onNavigate(Destination.AutoLedger.route) },
                    modifier = Modifier.weight(1f),
                )
            }

            is HomeUiState.Error -> Column(modifier = Modifier.fillMaxSize()) {
                HomeHeader(pendingCount = 0, onNavigate = onNavigate)
                HomeErrorState(
                    message = state.message,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 有数据：顶栏 + 余额卡片 + 日期分组列表（整页可滚动，与原型 `.page` 一致）。 */
@Composable
private fun HomeContentBody(
    state: HomeUiState.Content,
    onNavigate: (String) -> Unit,
) {
    // 入场动效（原型 `fadeInUp`）：卡片与各行**同时**淡入上浮，无错峰、无延迟
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "home-header") {
            HomeHeader(pendingCount = state.summary.pendingCount, onNavigate = onNavigate)
        }
        item(key = "home-balance-card") {
            HomeEntrance(visible = entered) {
                BalanceCard(summary = state.summary)
            }
        }
        state.sections.forEach { section ->
            item(key = "section:" + section.label) {
                SectionLabel(label = section.label)
            }
            items(items = section.items, key = { item -> item.id }) { item ->
                HomeEntrance(visible = entered) {
                    TransactionRow(
                        item = item,
                        onClick = { onNavigate(Destination.detailRoute(item.id)) },
                    )
                }
            }
        }
    }
}

/**
 * 入场包装：`opacity 0→1` + `translateY(10dp)→0`，380ms、`ease`（`UI-ANIMATION-SPEC` §1.1）。
 *
 * 用 `AnimatedVisibility` 的 `enter` 实现，天然从 `from` 状态开始，避免首帧闪烁（规范 §4.3）。
 */
@Composable
private fun HomeEntrance(visible: Boolean, content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val fadeSpec = tween<Float>(
        durationMillis = MotionDuration.enter,
        easing = MotionEasings.ease,
    )
    val slideSpec = tween<IntOffset>(
        durationMillis = MotionDuration.enter,
        easing = MotionEasings.ease,
    )
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = fadeSpec) + slideInVertically(
            animationSpec = slideSpec,
            initialOffsetY = { with(density) { MotionDistance.enterSlideDp.roundToPx() } },
        ),
    ) {
        content()
    }
}

/** 日期分组标题（原型 `.section-label`：小字 + 右侧渐隐分隔线）。 */
@Composable
private fun SectionLabel(label: String) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = HomeLayout.sectionLabelPaddingTopDp,
                bottom = HomeLayout.sectionLabelPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HomeLayout.sectionLabelGapDp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = HomeLayout.sectionLabelFontSizeSp.sp,
                letterSpacing = HomeLayout.sectionLabelLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(HomeLayout.sectionDividerHeightDp)
                .background(
                    Brush.horizontalGradient(
                        listOf(colors.dividerColor, colors.dividerColor.copy(alpha = 0f)),
                    ),
                ),
        )
    }
}

/**
 * 页面内按压缩放（原型各 `:active` 的 `scale()`）。
 *
 * 时长/曲线/缩放值全部来自 `ui/theme/Motion.kt`，页面不写字面值。
 */
@Composable
internal fun Modifier.homePressScale(
    interactionSource: MutableInteractionSource,
    scale: Float,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (pressed) scale else 1f,
        animationSpec = tween(
            durationMillis = MotionDuration.fast,
            easing = MotionEasings.ease,
        ),
        label = "homePressScale",
    )
    return this.graphicsLayer {
        scaleX = animatedScale
        scaleY = animatedScale
    }
}

/**
 * 首页页面级尺寸/字号（原型没有对应 token 的取值集中在此，**不得散落**）。
 *
 * 依据：`prototype/账然_UI原型.html` 的 `.app-head` / `.head-icon` / `.balance-card` /
 * `.card-amount` / `.section-label` / `.tx-row` / `.tx-icon`。
 * 颜色、圆角、间距、动效仍一律引用 `ui/theme` 的 token。
 */
internal object HomeLayout {

    // ---- 顶栏（.app-head / .app-logo / .app-sub / .head-icon）----
    val headerTopPaddingDp: Dp = 4.dp
    val headerBottomPaddingDp: Dp = 16.dp
    val headerActionsGapDp: Dp = 8.dp
    val headIconSizeDp: Dp = 34.dp
    val touchTargetDp: Dp = 48.dp
    val badgeMinSizeDp: Dp = 16.dp
    val badgeOffsetDp: Dp = 2.dp
    val subtitleTopPaddingDp: Dp = 6.dp
    const val logoFontSizeSp: Float = 22f
    const val logoLetterSpacingSp: Float = 3f
    const val subtitleFontSizeSp: Float = 9f
    const val subtitleLetterSpacingSp: Float = 3.2f
    const val badgeFontSizeSp: Float = 9f

    // ---- 余额卡片（.balance-card / .card-amount / .card-stats / .stat-v）----
    val balanceCardHorizontalMarginDp: Dp = 16.dp
    val balanceCardBottomMarginDp: Dp = 18.dp
    val balanceCardPaddingHorizontalDp: Dp = 20.dp
    val balanceCardPaddingTopDp: Dp = 22.dp
    val balanceCardPaddingBottomDp: Dp = 18.dp
    val cardTopAccentHeightDp: Dp = 3.dp
    val cardAmountTopPaddingDp: Dp = 9.dp
    val cardAmountBottomPaddingDp: Dp = 16.dp
    val cardStatsGapDp: Dp = 24.dp
    val statValueTopPaddingDp: Dp = 5.dp
    const val statLabelLetterSpacingSp: Float = 1.2f
    const val cardAmountFontSizeSp: Float = 36f
    const val cardAmountDecimalFontSizeSp: Float = 19f
    const val cardAmountLetterSpacingSp: Float = -1.6f

    // ---- 日期分组标题（.section-label）----
    val sectionLabelPaddingTopDp: Dp = 4.dp
    val sectionLabelPaddingBottomDp: Dp = 10.dp
    val sectionLabelGapDp: Dp = 10.dp
    val sectionDividerHeightDp: Dp = 1.dp
    const val sectionLabelFontSizeSp: Float = 10f
    const val sectionLabelLetterSpacingSp: Float = 2f
    const val noLetterSpacingSp: Float = 0f

    // ---- 交易行（.tx-row / .tx-icon / .tx-name / .tx-meta）----
    val rowHorizontalMarginDp: Dp = 16.dp
    val rowBottomMarginDp: Dp = 8.dp
    val rowPaddingHorizontalDp: Dp = 13.dp
    val rowPaddingVerticalDp: Dp = 11.dp
    val rowGapDp: Dp = 12.dp
    val iconSizeDp: Dp = 38.dp
    val metaTopPaddingDp: Dp = 4.dp
    const val iconFontSizeSp: Float = 16f
    const val metaFontSizeSp: Float = 10.5f

    // ---- 三态（骨架屏为补充设计，原型无对应取值）----
    const val skeletonRowCount: Int = 3
    val skeletonLabelWidthFraction: Float = 0.35f
    val skeletonLabelHeightDp: Dp = 10.dp
    val skeletonAmountWidthFraction: Float = 0.55f
    val skeletonAmountHeightDp: Dp = 28.dp
    val skeletonStatWidthFraction: Float = 0.28f
    val skeletonStatHeightDp: Dp = 12.dp
    val skeletonIconWidthFraction: Float = 0.1f
    val skeletonTitleWidthFraction: Float = 0.45f
    val skeletonMetaWidthFraction: Float = 0.3f
    val skeletonLineHeightDp: Dp = 10.dp
    val skeletonBlockCornerDp: Dp = 6.dp
    const val skeletonAlpha: Float = 0.28f
    val stateTopPaddingDp: Dp = 48.dp
    val stateTextGapDp: Dp = 12.dp
    val stateActionGapDp: Dp = 18.dp
    val actionButtonPaddingHorizontalDp: Dp = 24.dp
    val actionButtonPaddingVerticalDp: Dp = 12.dp
    const val stateHintFontSizeSp: Float = 11f
}

/**
 * Hilt 入口（本任务不引入 `hilt-navigation-compose`，故用 EntryPoint 手工构造 ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeEntryPoint {
    fun observeTransactionsUseCase(): ObserveTransactionsUseCase

    fun observeHomeSummaryUseCase(): ObserveHomeSummaryUseCase

    /** `M2b`：首页「已删除，可撤销」SnackBar 的撤销入口。 */
    fun deleteTransactionUseCase(): DeleteTransactionUseCase
}
