/**
 * 分类管理页（`M2d-1` 完整重写；行为 SSOT：`docs/06` §4.12，视觉：原型 `data-page="categories"`）。
 *
 * 结构：
 * ```text
 * ‹ 返回   分类管理                 添加
 * [支出分类] 点击分类可编辑   🍜 餐饮  🛍 购物 …
 * [收入分类]                 💰 工资  🎉 奖金 …
 * [分类规则 ›]                → RulesScreen
 * ```
 *
 * 纪律：
 * - **没有任何删除入口**（P-011）；「其他」不可改名/禁用（V3）；
 * - 所有写操作经 `SaveCategoryUseCase`（唯一写入口），保存失败留在弹层里提示（A4）；
 * - 页面不持有实体（见 `CategoriesUiState`）；
 * - 三态 + `Empty` = 初始化异常（`06` §4.12：正常至少 12+6 条，不存在"暂无分类"）。
 */
package com.zhangran.app.ui.screen.categories

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.usecase.SaveCategoryUseCase
import com.zhangran.app.ui.screen.categories.components.CategoryEditorSheet
import com.zhangran.app.ui.screen.categories.components.CategoryGridSection
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 分类管理页入口。
 *
 * @param onNavigateToRules 「分类规则」入口（由 `ZhangRanNavHost` 注入；默认空操作便于预览/测试）
 */
@Composable
fun CategoriesScreen(onNavigateToRules: () -> Unit = {}) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val onBack: () -> Unit = { activity?.onBackPressedDispatcher?.onBackPressed() }

    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        CategoriesEntryPoint::class.java,
    )
    val viewModel: CategoriesViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                CategoriesViewModel(
                    categories = entryPoint.categoryRepository(),
                    saveCategory = entryPoint.saveCategoryUseCase(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                CategoriesEvent.Saved -> snackbarHostState.showSnackbar("已保存")
                is CategoriesEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CategoriesContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::retry,
            onAdd = viewModel::openAdd,
            onEdit = viewModel::openEdit,
            onEditCancel = viewModel::closeEditor,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onIconChange = viewModel::onIconChange,
            onEnabledChange = viewModel::onEnabledChange,
            onMove = viewModel::move,
            onSave = viewModel::save,
            onNavigateToRules = onNavigateToRules,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 分类管理内容（无状态，便于 Compose 测试渲染各态）。
 */
@Composable
fun CategoriesContent(
    state: CategoriesUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (CategoryUiItem) -> Unit = {},
    onEditCancel: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onTypeChange: (CategoryType) -> Unit = {},
    onIconChange: (String) -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    onMove: (Boolean) -> Unit = {},
    onSave: () -> Unit = {},
    onNavigateToRules: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            CategoriesPageHead(
                onBack = onBack,
                onAdd = onAdd,
                addEnabled = state is CategoriesUiState.Content,
            )

            when (state) {
                CategoriesUiState.Loading -> CategoriesSkeleton()

                is CategoriesUiState.Error -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                // `06` §4.12：正常至少有 12+6 条分类；为空说明初始化异常
                CategoriesUiState.Empty -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp),
                    ) {
                        Text(
                            text = "分类数据异常",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimaryColor,
                        )
                        Text(
                            text = "初始分类未写入（正常应包含 12 个支出分类与 6 个收入分类）。请重启应用重试；若仍为空，请检查初始数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDisabledColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重新加载") }
                    }
                }

                is CategoriesUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.groups.forEachIndexed { index, group ->
                        CategoryGridSection(
                            group = group,
                            // 原型只在支出分组显示"点击分类可编辑"
                            showEditHint = index == 0,
                            onEdit = onEdit,
                        )
                    }
                    RulesEntry(onClick = onNavigateToRules)
                    Spacer(modifier = Modifier.height(CategoriesLayout.pageBottomPaddingDp))
                }
            }
        }

        val editor = (state as? CategoriesUiState.Content)?.editor
        if (editor != null) {
            CategoryEditorSheet(
                editor = editor,
                onDismiss = onEditCancel,
                onNameChange = onNameChange,
                onTypeChange = onTypeChange,
                onIconChange = onIconChange,
                onEnabledChange = onEnabledChange,
                onMove = onMove,
                onSave = onSave,
            )
        }
    }
}

/** 顶栏：`‹ 返回 / 分类管理 / 添加`（原型 `.page-head`）。 */
@Composable
private fun CategoriesPageHead(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    addEnabled: Boolean,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = CategoriesLayout.pageHeadPaddingTopDp,
                bottom = CategoriesLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.pageHeadGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(CategoriesLayout.backButtonSizeDp)
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = CategoriesLayout.backGlyphSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Text(
            text = "分类管理",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimaryColor,
        )
        Text(
            text = "添加",
            modifier = Modifier
                .clip(RoundedCornerShape(CategoriesLayout.optionRadiusDp))
                .clickable(enabled = addEnabled, onClick = onAdd)
                .padding(
                    horizontal = CategoriesLayout.addPaddingHorizontalDp,
                    vertical = CategoriesLayout.addPaddingVerticalDp,
                )
                .semantics { contentDescription = "添加分类" },
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = CategoriesLayout.pageActionFontSizeSp.sp,
            ),
            color = if (addEnabled) colors.primaryColor else colors.textDisabledColor,
        )
    }
}

/** 底部「分类规则」入口（只跳转，不在此编辑规则）。 */
@Composable
private fun RulesEntry(onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = CategoriesLayout.cardMarginHorizontalDp,
                end = CategoriesLayout.cardMarginHorizontalDp,
            )
            .heightIn(min = CategoriesLayout.touchTargetDp)
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .clickable(onClick = onClick)
            .padding(CategoriesLayout.cardPaddingDp)
            .semantics { contentDescription = "分类规则，双击打开" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "分类规则",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = CategoriesLayout.rulesFontSizeSp.sp,
            ),
            color = colors.textPrimaryColor,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textDisabledColor,
        )
    }
}

/** 加载骨架（两组卡片占位）。 */
@Composable
private fun CategoriesSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(CategoriesLayout.pageBottomPaddingDp)) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = CategoriesLayout.cardMarginHorizontalDp)
                    .zhangRanGlass(colors = ZhangRanTheme.colors, shape = ZhangRanTheme.shapes.card)
                    .clip(ZhangRanTheme.shapes.card)
                    .padding(CategoriesLayout.cardPaddingDp),
                verticalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp),
            ) {
                SkeletonBar(
                    widthFraction = 0.3f,
                    height = CategoriesLayout.skeletonLineHeight,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.gridColumnGapDp)) {
                    repeat(3) {
                        SkeletonBar(
                            widthFraction = null,
                            height = CategoriesLayout.skeletonCellHeight,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkeletonBar(
    widthFraction: Float?,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .then(if (widthFraction != null) Modifier.fillMaxWidth(widthFraction) else Modifier.fillMaxWidth())
            .height(height)
            .clip(ZhangRanTheme.shapes.option)
            .background(color = ZhangRanTheme.colors.surfaceVariantColor)
            .graphicsLayer { alpha = CategoriesLayout.skeletonAlpha },
    )
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

/** 从 Compose 的 `Context` 找到宿主 Activity（导航层冻结，返回按键走 Activity）。 */
private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}

/**
 * 分类管理页的页面级尺寸（原型 `data-page="categories"` 的 CSS；主题 token 未覆盖的集中在此）。
 *
 * 其中「3 列网格」「名称 20 字」「禁用态降权」「上移/下移」是任务书要求的**补充设计**
 * （原型 `.cat-grid` 为 5 列、未定义禁用样式与排序交互）。
 */
internal object CategoriesLayout {

    // 顶栏（.page-head / .back-btn / .page-head-right）
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 16.dp
    val pageHeadGapDp: Dp = 14.dp
    val backButtonSizeDp: Dp = 34.dp
    val optionRadiusDp: Dp = 10.dp
    val addPaddingHorizontalDp: Dp = 10.dp
    val addPaddingVerticalDp: Dp = 6.dp
    const val backGlyphSizeSp: Float = 18f
    const val pageActionFontSizeSp: Float = 12f

    // 卡片（.card / .card-head）
    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 14.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f
    const val cardActionFontSizeSp: Float = 11f

    // 网格（.cat-grid / .cat-item / .cat-emoji / .cat-name）
    // 任务书 §4.1 明确 3 列（原型为 5 列，差异见交付说明）
    const val gridColumns: Int = 3
    val gridColumnGapDp: Dp = 6.dp
    val gridRowGapDp: Dp = 6.dp
    val cellPaddingHorizontalDp: Dp = 4.dp
    val cellPaddingVerticalDp: Dp = 6.dp
    val cellGapDp: Dp = 5.dp
    val cellEmojiBoxDp: Dp = 34.dp
    const val cellEmojiFontSizeSp: Float = 20f
    const val cellNameFontSizeSp: Float = 10f
    const val cellNameLetterSpacingSp: Float = 0f

    // 底部规则入口
    const val rulesFontSizeSp: Float = 13f
    val touchTargetDp: Dp = 48.dp
    val pageBottomPaddingDp: Dp = 24.dp

    // 编辑弹层（.modal-*）
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleWidthDp: Dp = 40.dp
    val sheetHandleHeightDp: Dp = 2.dp
    val sheetHandleGapDp: Dp = 12.dp
    val sheetTitleGapDp: Dp = 12.dp
    val sheetItemGapDp: Dp = 12.dp
    /** 字段区最大高度：超出后字段区滚动，保存按钮始终可见。 */
    val sheetFieldsMaxHeightDp: Dp = 520.dp
    val confirmPaddingDp: Dp = 14.dp
    val controlBorderWidthDp: Dp = 1.dp
    const val fieldLabelFontSizeSp: Float = 10.5f

    // 图标选择器
    const val iconPickerColumns: Int = 6
    val iconPickerGapDp: Dp = 8.dp
    val iconPickerRowGapDp: Dp = 8.dp
    val iconOptionSizeDp: Dp = 40.dp
    const val iconOptionFontSizeSp: Float = 20f

    // 骨架
    val skeletonLineHeight: Dp = 10.dp
    val skeletonCellHeight: Dp = 56.dp
    const val skeletonAlpha: Float = 0.5f
}

/**
 * Hilt 入口（本任务不引入 `hilt-navigation-compose`，故用 EntryPoint 手工构造 ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CategoriesEntryPoint {
    fun categoryRepository(): CategoryRepository

    fun saveCategoryUseCase(): SaveCategoryUseCase
}
