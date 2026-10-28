/**
 * 预算页（`M4a` 完整重写；行为 SSOT：`docs/06` §4.4，视觉：原型 `data-page="budget"`）。
 *
 * 结构（预算页是底部导航的顶级页，无返回键）：
 * ```text
 * 预算                         点击预算金额修改
 * [月度总预算]  ¥5,000                点击修改
 *               已用 ¥3,847.20
 *               ▰▰▰▰▰▰▰▱▱▱
 *               剩余 ¥1,152.80 · 已用 77%
 * [分类预算] 添加
 *   🍜 餐饮   ¥1,620 / ¥2,000   ▰▰▰▰▰▰▱▱▱
 *   🎮 娱乐   ¥520 / ¥500       ▰▰▰▰▰（红） 已超支 ¥20
 * ```
 *
 * 约束：
 * - 点击**整卡/整行**编辑，无顶部"编辑"按钮（A12）；
 * - 超预算仅视觉提示（颜色 + "已超支 ¥X"），不阻止记账（A11）；
 * - 删除入口只在编辑弹层内（A21）；示例预算只在 Empty 态、用户点选后写入（A22）。
 */
package com.zhangran.app.ui.screen.budget

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
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
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.usecase.BudgetOverview
import com.zhangran.app.domain.usecase.CategoryBudgetOverview
import com.zhangran.app.domain.usecase.ObserveBudgetOverviewUseCase
import com.zhangran.app.domain.usecase.SaveBudgetUseCase
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.screen.budget.components.BudgetEditorSheet
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.inputFillColor
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

/** 预算页入口（底部导航顶级页，无返回键）。 */
@Composable
fun BudgetScreen() {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        BudgetEntryPoint::class.java,
    )
    val viewModel: BudgetViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BudgetViewModel(
                    observeOverview = entryPoint.observeBudgetOverviewUseCase(),
                    saveBudget = entryPoint.saveBudgetUseCase(),
                    categories = entryPoint.categoryRepository(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BudgetEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BudgetContent(
            state = state,
            onRetry = viewModel::refresh,
            onEditTotal = viewModel::openTotalEditor,
            onEditCategory = viewModel::openCategoryEditor,
            onAddCategory = viewModel::openAddCategory,
            onDismissEditor = viewModel::closeEditor,
            onSelectCategory = viewModel::selectCategory,
            onDigit = viewModel::pressDigit,
            onDot = viewModel::pressDot,
            onBackspace = viewModel::backspace,
            onClear = viewModel::clearAmount,
            onSave = viewModel::saveEditor,
            onRequestDelete = viewModel::requestDelete,
            onCancelDelete = viewModel::cancelDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onUseExample = viewModel::applyExampleBudgets,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 无状态内容（测试渲染三态、超支态与弹层）。 */
@Composable
fun BudgetContent(
    state: BudgetUiState,
    onRetry: () -> Unit = {},
    onEditTotal: () -> Unit = {},
    onEditCategory: (String) -> Unit = {},
    onAddCategory: () -> Unit = {},
    onDismissEditor: () -> Unit = {},
    onSelectCategory: (BudgetCategoryOption) -> Unit = {},
    onDigit: (Char) -> Unit = {},
    onDot: () -> Unit = {},
    onBackspace: () -> Unit = {},
    onClear: () -> Unit = {},
    onSave: () -> Unit = {},
    onRequestDelete: () -> Unit = {},
    onCancelDelete: () -> Unit = {},
    onConfirmDelete: () -> Unit = {},
    onUseExample: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BudgetHead()

            when (state) {
                BudgetUiState.Loading -> BudgetSkeleton()

                is BudgetUiState.Error -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(BudgetLayout.itemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                is BudgetUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    EmptyBudgetCard(
                        onSetTotal = onEditTotal,
                        onUseExample = onUseExample,
                    )
                    Spacer(modifier = Modifier.height(BudgetLayout.pageBottomPaddingDp))
                }

                is BudgetUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    TotalBudgetCard(overview = state.overview, onClick = onEditTotal)
                    CategoryBudgetCard(
                        categories = state.overview.categories,
                        onEdit = onEditCategory,
                        onAdd = onAddCategory,
                    )
                    Spacer(modifier = Modifier.height(BudgetLayout.pageBottomPaddingDp))
                }
            }
        }

        val editor = when (state) {
            is BudgetUiState.Content -> state.editor
            is BudgetUiState.Empty -> state.editor
            else -> null
        }
        if (editor != null) {
            val options = when (state) {
                is BudgetUiState.Content -> state.categoryOptions
                is BudgetUiState.Empty -> state.categoryOptions
                else -> emptyList()
            }
            BudgetEditorSheet(
                editor = editor,
                categoryOptions = options,
                onDismiss = onDismissEditor,
                onSelectCategory = onSelectCategory,
                onDigit = onDigit,
                onDot = onDot,
                onBackspace = onBackspace,
                onClear = onClear,
                onSave = onSave,
                onRequestDelete = onRequestDelete,
                onCancelDelete = onCancelDelete,
                onConfirmDelete = onConfirmDelete,
            )
        }
    }
}

/** 页头（原型 `.app-head`：预算 / BUDGET + 右侧提示）。 */
@Composable
private fun BudgetHead() {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = BudgetLayout.pageHeadPaddingTopDp,
                bottom = BudgetLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "预算",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = BudgetLayout.logoFontSizeSp.sp,
                    letterSpacing = BudgetLayout.logoLetterSpacingSp.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(BudgetLayout.headSubGapDp))
            Text(
                text = "BUDGET",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = BudgetLayout.headSubFontSizeSp.sp,
                    letterSpacing = BudgetLayout.headSubLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
        Text(
            text = "点击预算金额修改",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BudgetLayout.headHintFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

/** 月度总预算卡片（整卡可点，A12）。 */
@Composable
private fun TotalBudgetCard(
    overview: BudgetOverview,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val limit = overview.totalLimitMinor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BudgetLayout.cardMarginHorizontalDp,
                end = BudgetLayout.cardMarginHorizontalDp,
                bottom = BudgetLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = totalSemantics(overview)
            }
            .padding(BudgetLayout.cardPaddingDp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "月度总预算",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = BudgetLayout.cardHeadFontSizeSp.sp,
                    letterSpacing = BudgetLayout.cardHeadLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
            Text(
                text = if (limit == null) "点击设置" else "点击修改",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = BudgetLayout.cardActionFontSizeSp.sp,
                ),
                color = colors.primaryColor,
            )
        }
        Spacer(modifier = Modifier.height(BudgetLayout.cardHeadGapDp))

        if (limit == null) {
            Text(
                text = "¥ —",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = BudgetLayout.totalAmountFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimaryColor,
            )
            Spacer(modifier = Modifier.height(BudgetLayout.rowGapDp))
            Text(
                text = "已用 ${budgetMoneyText(overview.totalUsedMinor)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BudgetLayout.secondaryFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
            )
            return@Column
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(BudgetLayout.rowGapDp),
        ) {
            Text(
                text = budgetMoneyText(limit),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = BudgetLayout.totalAmountFontSizeSp.sp,
                    fontWeight = FontWeight.Bold,
                ),
                color = colors.textPrimaryColor,
            )
            Text(
                text = "已用 ${budgetMoneyText(overview.totalUsedMinor)}",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BudgetLayout.secondaryFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
        Spacer(modifier = Modifier.height(BudgetLayout.totalBarGapDp))

        BudgetProgressBar(
            usedMinor = overview.totalUsedMinor,
            limitMinor = limit,
            height = BudgetLayout.barHeightDp,
        )
        Spacer(modifier = Modifier.height(BudgetLayout.totalBarGapDp))

        Text(
            text = if (overview.totalUsedMinor > limit) {
                "${budgetOverText(overview.totalUsedMinor, limit)} · 已用 ${budgetPercent(overview.totalUsedMinor, limit)}%"
            } else {
                budgetTotalFooterText(overview.totalUsedMinor, limit)
            },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BudgetLayout.footerFontSizeSp.sp,
            ),
            color = if (overview.totalUsedMinor > limit) colors.expenseColor else colors.textDisabledColor,
        )
    }
}

/** 分类预算卡片（行整行可点；A11 超支仅视觉提示）。 */
@Composable
private fun CategoryBudgetCard(
    categories: List<CategoryBudgetOverview>,
    onEdit: (String) -> Unit,
    onAdd: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BudgetLayout.cardMarginHorizontalDp,
                end = BudgetLayout.cardMarginHorizontalDp,
                bottom = BudgetLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(BudgetLayout.cardPaddingDp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "分类预算",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = BudgetLayout.cardHeadFontSizeSp.sp,
                    letterSpacing = BudgetLayout.cardHeadLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
            Text(
                text = "添加",
                modifier = Modifier
                    .clip(RoundedCornerShape(BudgetLayout.optionRadiusDp))
                    .clickable(onClick = onAdd)
                    .padding(
                        horizontal = BudgetLayout.addPaddingHorizontalDp,
                        vertical = BudgetLayout.addPaddingVerticalDp,
                    )
                    .semantics { contentDescription = "添加分类预算" },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = BudgetLayout.cardActionFontSizeSp.sp,
                ),
                color = colors.primaryColor,
            )
        }

        if (categories.isEmpty()) {
            Spacer(modifier = Modifier.height(BudgetLayout.itemGapDp))
            Text(
                text = "还没有分类预算，点「添加」为常用分类设置上限",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabledColor,
            )
            return@Column
        }

        Spacer(modifier = Modifier.height(BudgetLayout.cardHeadGapDp))
        categories.forEachIndexed { index, category ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BudgetLayout.hairlineWidthDp)
                        .background(color = colors.dividerColor),
                )
            }
            CategoryBudgetRow(category = category, onClick = { onEdit(category.categoryId) })
        }
    }
}

@Composable
private fun CategoryBudgetRow(
    category: CategoryBudgetOverview,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val over = category.usedMinor > category.limitMinor
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = categorySemantics(category) }
            .padding(vertical = BudgetLayout.categoryRowPaddingVerticalDp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = CategoryIconMapper.toEmoji(category.categoryIcon),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = BudgetLayout.categoryEmojiSizeSp.sp,
                ),
            )
            Spacer(modifier = Modifier.width(BudgetLayout.categoryEmojiGapDp))
            Text(
                text = category.categoryName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = BudgetLayout.categoryNameFontSizeSp.sp,
                ),
                color = colors.textSecondaryColor,
                maxLines = 1,
            )
            Text(
                text = budgetCategoryAmountText(category),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = BudgetLayout.categoryAmountFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Spacer(modifier = Modifier.height(BudgetLayout.categoryBarGapDp))
        BudgetProgressBar(
            usedMinor = category.usedMinor,
            limitMinor = category.limitMinor,
            height = BudgetLayout.barHeightDp,
        )
        if (over) {
            Spacer(modifier = Modifier.height(BudgetLayout.overTextGapDp))
            Text(
                text = budgetOverText(category.usedMinor, category.limitMinor),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BudgetLayout.overTextFontSizeSp.sp,
                ),
                color = colors.expenseColor,
            )
        }
    }
}

/**
 * 进度条（`used / limit`；超支满格、超 80% 用警告色）。
 *
 * 宽度过渡用 `MotionDuration.slow`（400ms，与原型 `width .4s` 一致，§4.7）。
 */
@Composable
private fun BudgetProgressBar(
    usedMinor: Long,
    limitMinor: Long,
    height: Dp,
) {
    val colors = ZhangRanTheme.colors
    val target = budgetBarFraction(usedMinor, limitMinor)
    val fraction by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = MotionDuration.slow, easing = MotionEasings.ease),
        label = "budgetBar",
    )
    val over = usedMinor > limitMinor
    val warning = !over && limitMinor > 0 && usedMinor.toFloat() / limitMinor.toFloat() >= BudgetLayout.warningThreshold
    val fillBrush: Brush = when {
        over -> Brush.linearGradient(listOf(colors.expenseColor, colors.expenseColor))
        warning -> Brush.linearGradient(listOf(colors.warningColor, colors.expenseColor))
        else -> Brush.linearGradient(
            listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(color = colors.dividerColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(brush = fillBrush),
        )
    }
}

/** Empty：未设置任何预算 → 引导设置总预算或使用示例预算（A16/A22）。 */
@Composable
private fun EmptyBudgetCard(
    onSetTotal: () -> Unit,
    onUseExample: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BudgetLayout.cardMarginHorizontalDp,
                end = BudgetLayout.cardMarginHorizontalDp,
                bottom = BudgetLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(BudgetLayout.emptyCardPaddingDp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "🎯",
            style = MaterialTheme.typography.displaySmall.copy(
                fontSize = BudgetLayout.emptyEmojiSizeSp.sp,
            ),
        )
        Spacer(modifier = Modifier.height(BudgetLayout.itemGapDp))
        Text(
            text = "还没有设置预算",
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimaryColor,
        )
        Spacer(modifier = Modifier.height(BudgetLayout.emptyTextGapDp))
        Text(
            text = "先设置一个月度总预算，或使用一组示例预算快速开始。",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(BudgetLayout.emptyActionGapDp))
        PrimaryAction(text = "设置月度总预算", onClick = onSetTotal)
        Spacer(modifier = Modifier.height(BudgetLayout.itemGapDp))
        SecondaryAction(text = "使用示例预算", onClick = onUseExample)
        Spacer(modifier = Modifier.height(BudgetLayout.emptyNoteGapDp))
        Text(
            text = "示例预算只是起始值（总预算 ¥3,000 + 餐饮/交通/购物/娱乐），写入后可随时修改或删除。",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BudgetLayout.emptyNoteFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BudgetLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = colors.primaryColor)
            .clickable(onClick = onClick)
            .padding(vertical = BudgetLayout.confirmPaddingDp)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.backgroundColor,
        )
    }
}

@Composable
private fun SecondaryAction(text: String, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BudgetLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = colors.inputFillColor)
            .clickable(onClick = onClick)
            .padding(vertical = BudgetLayout.confirmPaddingDp)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryColor,
        )
    }
}

/** 加载骨架。 */
@Composable
private fun BudgetSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(BudgetLayout.pageBottomPaddingDp)) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = BudgetLayout.cardMarginHorizontalDp)
                    .zhangRanGlass(colors = ZhangRanTheme.colors, shape = ZhangRanTheme.shapes.card)
                    .clip(ZhangRanTheme.shapes.card)
                    .padding(BudgetLayout.cardPaddingDp),
                verticalArrangement = Arrangement.spacedBy(BudgetLayout.itemGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(BudgetLayout.skeletonHeadFraction)
                        .height(BudgetLayout.skeletonLineHeight)
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = BudgetLayout.skeletonAlpha },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(BudgetLayout.skeletonAmountFraction)
                        .height(BudgetLayout.skeletonAmountHeight)
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = BudgetLayout.skeletonAlpha },
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(BudgetLayout.barHeightDp)
                        .clip(RoundedCornerShape(BudgetLayout.barHeightDp / 2))
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = BudgetLayout.skeletonAlpha },
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

private fun totalSemantics(overview: BudgetOverview): String {
    val limit = overview.totalLimitMinor ?: return "月度总预算，未设置，双击设置"
    val used = HomeViewModel.formatMoney(overview.totalUsedMinor, withCurrencySymbol = false)
    return if (overview.totalUsedMinor > limit) {
        "月度总预算 ${plainYuan(limit)} 元，已用 $used 元，已超支 ${plainYuan(overview.totalUsedMinor - limit)} 元"
    } else {
        "月度总预算 ${plainYuan(limit)} 元，已用 $used 元，剩余 ${plainYuan(limit - overview.totalUsedMinor)} 元"
    }
}

private fun categorySemantics(category: CategoryBudgetOverview): String {
    val used = HomeViewModel.formatMoney(category.usedMinor, withCurrencySymbol = false)
    val limit = plainYuan(category.limitMinor)
    return if (category.usedMinor > category.limitMinor) {
        "${category.categoryName} 分类预算，已用 $used 元，预算 $limit 元，已超支 ${plainYuan(category.usedMinor - category.limitMinor)} 元，双击编辑"
    } else {
        "${category.categoryName} 分类预算，已用 $used 元，预算 $limit 元，剩余 ${plainYuan(category.limitMinor - category.usedMinor)} 元，双击编辑"
    }
}

/** 无障碍读数用的元金额（不带货币符号；四舍五入到分）。 */
private fun plainYuan(amountMinor: Long): String =
    HomeViewModel.formatMoney(amountMinor, sign = AmountSign.AUTO, withCurrencySymbol = false)

/**
 * 页面级尺寸（原型 `data-page="budget"` / `.budget-bar` CSS；主题 token 未覆盖的集中在此）。
 */
internal object BudgetLayout {

    // 页头（.app-head；与首页 logo 同规格：22sp / 字距 3 / 副标题 9sp / 3.2）
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 12.dp
    val headSubGapDp: Dp = 4.dp
    const val logoFontSizeSp: Float = 22f
    const val logoLetterSpacingSp: Float = 3f
    const val headSubFontSizeSp: Float = 9f
    const val headSubLetterSpacingSp: Float = 3.2f
    const val headHintFontSizeSp: Float = 11f

    // 卡片（.card / .card-head）
    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 14.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f
    const val cardActionFontSizeSp: Float = 11f
    val optionRadiusDp: Dp = 10.dp
    val addPaddingHorizontalDp: Dp = 10.dp
    val addPaddingVerticalDp: Dp = 6.dp
    val hairlineWidthDp: Dp = 1.dp

    // 总额卡片
    const val totalAmountFontSizeSp: Float = 26f
    const val secondaryFontSizeSp: Float = 12f
    const val footerFontSizeSp: Float = 11f
    val rowGapDp: Dp = 8.dp
    val totalBarGapDp: Dp = 8.dp

    // 进度条（.budget-bar：height 10px / radius 5px）
    val barHeightDp: Dp = 10.dp
    // 预警阈值：按原型两个样本（餐饮 81% 用主渐变、购物 84% 用 warn）推得 82%
    const val warningThreshold: Float = 0.82f

    // 分类行
    val categoryRowPaddingVerticalDp: Dp = 12.dp
    val categoryBarGapDp: Dp = 6.dp
    val categoryEmojiGapDp: Dp = 6.dp
    val overTextGapDp: Dp = 4.dp
    const val categoryEmojiSizeSp: Float = 14f
    const val categoryNameFontSizeSp: Float = 12.5f
    const val categoryAmountFontSizeSp: Float = 12f
    const val overTextFontSizeSp: Float = 10.5f

    // Empty 引导
    val emptyCardPaddingDp: Dp = 22.dp
    val emptyTextGapDp: Dp = 6.dp
    val emptyActionGapDp: Dp = 16.dp
    val emptyNoteGapDp: Dp = 10.dp
    const val emptyEmojiSizeSp: Float = 34f
    const val emptyNoteFontSizeSp: Float = 10.5f
    val itemGapDp: Dp = 12.dp
    val touchTargetDp: Dp = 48.dp
    val confirmPaddingDp: Dp = 14.dp
    val pageBottomPaddingDp: Dp = 24.dp

    // 编辑弹层
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleHeightDp: Dp = 2.dp
    const val sheetHandleWidthFraction: Float = 0.3f
    val sheetHandleGapDp: Dp = 10.dp
    val sheetItemGapDp: Dp = 10.dp
    val amountPaddingVerticalDp: Dp = 8.dp
    const val amountFontSizeSp: Float = 34f
    val deletePaddingVerticalDp: Dp = 6.dp
    val categoryPickerMaxHeightDp: Dp = 120.dp
    const val categoryPickerColumns: Int = 2
    val categoryChipPaddingHorizontalDp: Dp = 8.dp
    val categoryChipGapDp: Dp = 4.dp
    const val categoryChipEmojiSizeSp: Float = 14f
    val controlBorderWidthDp: Dp = 1.dp

    // 骨架
    const val skeletonHeadFraction: Float = 0.3f
    const val skeletonAmountFraction: Float = 0.5f
    val skeletonLineHeight: Dp = 10.dp
    val skeletonAmountHeight: Dp = 24.dp
    const val skeletonAlpha: Float = 0.5f
}

/** Hilt 入口（不引入 `hilt-navigation-compose`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BudgetEntryPoint {
    fun observeBudgetOverviewUseCase(): ObserveBudgetOverviewUseCase

    fun saveBudgetUseCase(): SaveBudgetUseCase

    fun categoryRepository(): CategoryRepository
}
