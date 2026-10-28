/**
 * 搜索页（`M4e` 完整重写；行为 SSOT：`docs/06` §4.17，口径：`docs/02` §13）。
 *
 * 结构（二级页，无底栏）：
 * ```text
 * ‹ 返回   搜索
 * [🔍 搜索商户、备注、金额…                ✕]
 * 全部 | 分类 | 类型 | 账户            高级筛选
 * 共 N 条
 * [交易行 … 复用首页 TransactionRow]
 * ```
 *
 * 状态：Idle（未搜索）/ Loading / Content / Empty / Error；关键字 300ms 防抖在 VM。
 * 结果行复用 `ui.screen.home.components.TransactionRow`（未复制实现）。
 */
package com.zhangran.app.ui.screen.search

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.SearchTransactionsUseCase
import com.zhangran.app.ui.screen.home.components.TransactionRow
import com.zhangran.app.ui.screen.search.components.AdvancedFilterSheet
import com.zhangran.app.ui.screen.search.components.FilterChips
import com.zhangran.app.ui.screen.search.components.PickerOption
import com.zhangran.app.ui.screen.search.components.SearchBar
import com.zhangran.app.ui.screen.search.components.SearchPickerSheet
import com.zhangran.app.ui.screen.search.components.typeLabel
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 搜索页入口。
 *
 * @param onOpenTransaction 结果行点击（默认空实现；本任务白名单禁止改导航层，
 *   如需"点结果进详情"，需在后续任务接线）
 */
@Composable
fun SearchScreen(onOpenTransaction: (String) -> Unit = {}) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        SearchEntryPoint::class.java,
    )
    val viewModel: SearchViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SearchViewModel(
                    searchTransactions = entryPoint.searchTransactionsUseCase(),
                    categories = entryPoint.categoryRepository(),
                    accounts = entryPoint.accountRepository(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val criteria by viewModel.criteria.collectAsState()
    val options by viewModel.options.collectAsState()
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    var sheet by remember { mutableStateOf<SearchSheet?>(null) }

    SearchContent(
        state = state,
        criteria = criteria,
        options = options,
        onKeywordChange = viewModel::onKeywordChange,
        onClearKeyword = { viewModel.onKeywordChange("") },
        onAll = viewModel::clearAll,
        onOpenCategory = { sheet = SearchSheet.CATEGORY },
        onOpenType = { sheet = SearchSheet.TYPE },
        onOpenAccount = { sheet = SearchSheet.ACCOUNT },
        onOpenAdvanced = { sheet = SearchSheet.ADVANCED },
        onClearFilters = viewModel::clearAll,
        onRetry = viewModel::retry,
        onLoadMore = viewModel::loadMore,
        onOpenTransaction = onOpenTransaction,
        onBack = { backDispatcher?.onBackPressed() },
    )

    when (sheet) {
        null -> Unit

        SearchSheet.CATEGORY -> SearchPickerSheet(
            title = "按分类筛选",
            options = options.categories.map { PickerOption(it.id, it.name) },
            selectedId = criteria.categoryId,
            onSelect = {
                viewModel.selectCategory(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        SearchSheet.TYPE -> SearchPickerSheet(
            title = "按类型筛选",
            options = TransactionType.entries.map { PickerOption(it.name, typeLabel(it)) },
            selectedId = criteria.type?.name,
            onSelect = { id ->
                viewModel.selectType(TransactionType.entries.firstOrNull { it.name == id })
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        SearchSheet.ACCOUNT -> SearchPickerSheet(
            title = "按账户筛选",
            options = options.accounts.map { PickerOption(it.id, it.name) },
            selectedId = criteria.accountId,
            onSelect = {
                viewModel.selectAccount(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        SearchSheet.ADVANCED -> AdvancedFilterSheet(
            initial = criteria,
            onApply = { exact, min, max, from, to, source, state ->
                viewModel.applyAdvancedFilters(exact, min, max, from, to, source, state)
                sheet = null
            },
            onDismiss = { sheet = null },
        )
    }
}

private enum class SearchSheet { CATEGORY, TYPE, ACCOUNT, ADVANCED }

/** 无状态内容（测试直接渲染 Idle/Content/Empty/Error）。 */
@Composable
fun SearchContent(
    state: SearchUiState,
    criteria: SearchCriteria,
    options: SearchOptions,
    onKeywordChange: (String) -> Unit = {},
    onClearKeyword: () -> Unit = {},
    onAll: () -> Unit = {},
    onOpenCategory: () -> Unit = {},
    onOpenType: () -> Unit = {},
    onOpenAccount: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
    onClearFilters: () -> Unit = {},
    onRetry: () -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenTransaction: (String) -> Unit = {},
    onBack: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SearchPageHead(onBack = onBack)
            SearchBar(
                value = criteria.keyword.orEmpty(),
                onValueChange = onKeywordChange,
                onClear = onClearKeyword,
            )
            FilterChips(
                criteria = criteria,
                categoryName = options.categories.firstOrNull { it.id == criteria.categoryId }?.name,
                accountName = options.accounts.firstOrNull { it.id == criteria.accountId }?.name,
                onAll = onAll,
                onCategory = onOpenCategory,
                onType = onOpenType,
                onAccount = onOpenAccount,
            )
            AdvancedFilterRow(criteria = criteria, onClick = onOpenAdvanced)

            Box(modifier = Modifier.weight(1f)) {
                when (state) {
                SearchUiState.Idle -> CenteredHint(
                    title = "输入关键字或选择筛选条件",
                    description = "支持商户、备注、金额、日期、分类、账户、类型、来源、状态组合搜索",
                )

                SearchUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = colors.primaryColor)
                }

                SearchUiState.Empty -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SearchLayout.resultPaddingHorizontalDp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "没有符合条件的记录",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimaryColor,
                    )
                    Spacer(modifier = Modifier.height(SearchLayout.itemGapDp))
                    Text(
                        text = "试着减少筛选条件，或清空后重新搜索",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDisabledColor,
                    )
                    Spacer(modifier = Modifier.height(SearchLayout.itemGapDp))
                    PrimaryAction(text = "清空筛选", onClick = onClearFilters)
                }

                is SearchUiState.Error -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = SearchLayout.resultPaddingHorizontalDp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.errorColor,
                    )
                    Spacer(modifier = Modifier.height(SearchLayout.itemGapDp))
                    PrimaryAction(text = "重试", onClick = onRetry)
                }

                is SearchUiState.Content -> SearchResults(
                    state = state,
                    onLoadMore = onLoadMore,
                    onOpenTransaction = onOpenTransaction,
                )
                }
            }
        }
    }
}

@Composable
private fun SearchPageHead(onBack: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = SearchLayout.pageHeadPaddingTopDp,
                bottom = SearchLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchLayout.pageHeadGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(SearchLayout.backButtonSizeDp)
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clip(ZhangRanTheme.shapes.option)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = SearchLayout.backGlyphSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Text(
            text = "搜索",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimaryColor,
        )
    }
}

@Composable
private fun AdvancedFilterRow(criteria: SearchCriteria, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    val activeCount = listOf(
        criteria.amountExactMinor != null ||
            criteria.amountMinMinor != null ||
            criteria.amountMaxMinor != null,
        criteria.dateFromInclusive != null || criteria.dateToExclusive != null,
        criteria.source != null,
        criteria.state != null,
    ).count { it }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SearchLayout.pagePaddingHorizontalDp,
                end = SearchLayout.pagePaddingHorizontalDp,
                bottom = SearchLayout.advancedRowMarginBottomDp,
            )
            .heightIn(min = SearchLayout.chipMinHeightDp)
            .clip(ZhangRanTheme.shapes.option)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription =
                    "高级筛选，${if (activeCount > 0) "已选 $activeCount 项" else "未选"}"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (activeCount > 0) {
                "高级筛选 · 已选 $activeCount 项"
            } else {
                "高级筛选（金额 / 日期 / 来源 / 状态）"
            },
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = SearchLayout.advancedFontSizeSp.sp,
            ),
            color = if (activeCount > 0) colors.primaryColor else colors.textDisabledColor,
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = SearchLayout.advancedFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState.Content,
    onLoadMore: () -> Unit,
    onOpenTransaction: (String) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val listState = rememberLazyListState()

    // 滚动到底自动加载（距离末尾 3 条时触发；hasMore=false 时 VM 忽略）
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        }.collect { lastIndex ->
            if (lastIndex >= 0 && lastIndex >= state.items.size - LOAD_MORE_THRESHOLD) {
                onLoadMore()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "共 ${state.total} 条",
            modifier = Modifier.padding(
                start = SearchLayout.pagePaddingHorizontalDp,
                end = SearchLayout.pagePaddingHorizontalDp,
                bottom = SearchLayout.resultCountPaddingBottomDp,
            ),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = SearchLayout.resultCountFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items = state.items, key = { it.id }) { item ->
                TransactionRow(
                    item = item,
                    onClick = { onOpenTransaction(item.id) },
                )
            }
            if (state.loadingMore) {
                item(key = "loading-more") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(SearchLayout.loadMorePaddingDp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(SearchLayout.loadMoreIndicatorSizeDp),
                            color = colors.primaryColor,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredHint(title: String, description: String) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = SearchLayout.resultPaddingHorizontalDp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimaryColor,
        )
        Spacer(modifier = Modifier.height(SearchLayout.itemGapDp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabledColor,
        )
    }
}

@Composable
private fun PrimaryAction(text: String, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .heightIn(min = SearchLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = colors.primaryColor)
            .clickable(onClick = onClick)
            .padding(horizontal = SearchLayout.actionPaddingHorizontalDp)
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

private const val LOAD_MORE_THRESHOLD = 3

/** 搜索页页面级尺寸（原型 `data-page="search"` + 交互补充）。 */
internal object SearchLayout {
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 12.dp
    val pageHeadGapDp: Dp = 14.dp
    val backButtonSizeDp: Dp = 34.dp
    const val backGlyphSizeSp: Float = 18f

    val pagePaddingHorizontalDp: Dp = 16.dp
    val searchBarMarginBottomDp: Dp = 12.dp
    val searchBarMinHeightDp: Dp = 46.dp
    val searchBarPaddingHorizontalDp: Dp = 16.dp
    val searchBarPaddingVerticalDp: Dp = 10.dp
    val searchBarGapDp: Dp = 10.dp
    const val searchIconSizeSp: Float = 15f
    const val searchFontSizeSp: Float = 14f
    val clearButtonSizeDp: Dp = 24.dp
    const val clearGlyphSizeSp: Float = 12f

    val chipsMarginBottomDp: Dp = 10.dp
    val chipGapDp: Dp = 8.dp
    val chipMinHeightDp: Dp = 28.dp
    val chipPaddingHorizontalDp: Dp = 12.dp
    val chipBorderWidthDp: Dp = 1.dp
    const val chipFontSizeSp: Float = 11.5f

    val advancedRowMarginBottomDp: Dp = 8.dp
    const val advancedFontSizeSp: Float = 11f

    val resultCountPaddingBottomDp: Dp = 8.dp
    const val resultCountFontSizeSp: Float = 10.5f
    val resultPaddingHorizontalDp: Dp = 24.dp
    val loadMorePaddingDp: Dp = 16.dp
    val loadMoreIndicatorSizeDp: Dp = 24.dp

    val itemGapDp: Dp = 10.dp
    val touchTargetDp: Dp = 44.dp
    val actionPaddingHorizontalDp: Dp = 22.dp

    // 弹层
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleHeightDp: Dp = 2.dp
    const val sheetHandleWidthFraction: Float = 0.3f
    val sheetMaxHeightDp: Dp = 360.dp
    val advancedSheetMaxHeightDp: Dp = 520.dp
    val pickerRowMinHeightDp: Dp = 44.dp
    val pickerRowPaddingHorizontalDp: Dp = 4.dp
    const val pickerFontSizeSp: Float = 14f
    val sectionGapDp: Dp = 10.dp
    const val hintFontSizeSp: Float = 10f
    const val sectionLabelFontSizeSp: Float = 10f
    const val sectionLabelLetterSpacingSp: Float = 1.6f
    val fieldMinHeightDp: Dp = 42.dp
    val fieldPaddingHorizontalDp: Dp = 12.dp
    const val fieldLabelFontSizeSp: Float = 11f
    val fieldLabelGapDp: Dp = 10.dp
    const val fieldInputFontSizeSp: Float = 14f
    val fieldClearPaddingDp: Dp = 6.dp
    val dateButtonPaddingHorizontalDp: Dp = 12.dp
}

/** Hilt 入口（不引入 `hilt-navigation-compose`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SearchEntryPoint {
    fun searchTransactionsUseCase(): SearchTransactionsUseCase

    fun categoryRepository(): CategoryRepository

    fun accountRepository(): AccountRepository
}
