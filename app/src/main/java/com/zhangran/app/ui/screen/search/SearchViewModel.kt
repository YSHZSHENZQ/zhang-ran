/**
 * 搜索页 ViewModel（`M4e` §4.3/§4.5/§4.6）。
 *
 * - 条件（[SearchCriteria]）为单一事实源；关键字输入 **300ms 防抖**；
 * - 首次进入/条件清空 → `Idle`，不查库（A11）；
 * - 分页：`page` 递增，`hasMore = (page+1)*size < total`，到底不再加载（A6）；
 * - 结果行复用首页 `TransactionListItem`（展示格式化复用 `HomeViewModel` 公共函数）。
 */
package com.zhangran.app.ui.screen.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.SearchTransactionsUseCase
import com.zhangran.app.domain.usecase.TransactionListEntry
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import com.zhangran.app.ui.screen.home.TransactionListItem
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel @Inject constructor(
    private val searchTransactions: SearchTransactionsUseCase,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) : ViewModel() {

    private val _criteria = MutableStateFlow(SearchCriteria())
    val criteria: StateFlow<SearchCriteria> = _criteria.asStateFlow()

    private val _options = MutableStateFlow(SearchOptions())
    val options: StateFlow<SearchOptions> = _options.asStateFlow()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SearchEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<SearchEvent> = _events.asSharedFlow()

    private var searchJob: Job? = null
    private var debounceJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                SearchOptions(
                    categories = categories.all().map { SearchCategoryOption(it.id, it.name) },
                    accounts = accounts.all().map { SearchAccountOption(it.id, it.name) },
                )
            }.onSuccess { _options.value = it }
        }
    }

    /** 关键字变更：立即更新输入框，300ms 后再查询（A12）。 */
    fun onKeywordChange(value: String) {
        val next = _criteria.value.copy(keyword = value, page = 0)
        _criteria.value = next
        debounceJob?.cancel()
        if (!next.hasAnyFilter()) {
            searchJob?.cancel()
            _uiState.value = SearchUiState.Idle
            return
        }
        debounceJob = viewModelScope.launch {
            delay(KEYWORD_DEBOUNCE_MILLIS)
            search(next, append = false)
        }
    }

    /** chips：分类（传 null 清除）。 */
    fun selectCategory(categoryId: String?) = updateAndSearch(
        _criteria.value.copy(categoryId = categoryId, page = 0),
    )

    /** chips：类型（传 null 清除）。 */
    fun selectType(type: TransactionType?) = updateAndSearch(
        _criteria.value.copy(type = type, page = 0),
    )

    /** chips：账户（传 null 清除）。 */
    fun selectAccount(accountId: String?) = updateAndSearch(
        _criteria.value.copy(accountId = accountId, page = 0),
    )

    /** 高级筛选（金额/日期/来源/状态）应用。 */
    fun applyAdvancedFilters(
        amountExactMinor: Long?,
        amountMinMinor: Long?,
        amountMaxMinor: Long?,
        dateFromInclusive: Long?,
        dateToExclusive: Long?,
        source: com.zhangran.app.domain.model.CaptureSource?,
        state: com.zhangran.app.domain.model.TransactionState?,
    ) = updateAndSearch(
        _criteria.value.copy(
            amountExactMinor = amountExactMinor,
            amountMinMinor = amountMinMinor,
            amountMaxMinor = amountMaxMinor,
            dateFromInclusive = dateFromInclusive,
            dateToExclusive = dateToExclusive,
            source = source,
            state = state,
            page = 0,
        ),
    )

    /** 清空所有筛选（Empty 的「清空筛选」/「全部」chip）。 */
    fun clearAll() {
        debounceJob?.cancel()
        searchJob?.cancel()
        _criteria.value = SearchCriteria()
        _uiState.value = SearchUiState.Idle
    }

    /** 错误重试。 */
    fun retry() {
        val current = _criteria.value
        if (current.hasAnyFilter()) {
            search(current.copy(page = 0), append = false)
        }
    }

    /** 滚动到底加载下一页。 */
    fun loadMore() {
        val content = _uiState.value as? SearchUiState.Content ?: return
        if (!content.hasMore || content.loadingMore) return
        search(_criteria.value.copy(page = content.page + 1), append = true)
    }

    private fun updateAndSearch(next: SearchCriteria) {
        _criteria.value = next
        debounceJob?.cancel()
        if (!next.hasAnyFilter()) {
            searchJob?.cancel()
            _uiState.value = SearchUiState.Idle
            return
        }
        search(next, append = false)
    }

    private fun search(criteria: SearchCriteria, append: Boolean) {
        searchJob?.cancel()
        val normalized = criteria.normalized()
        if (!append) {
            _uiState.value = SearchUiState.Loading
        } else {
            val content = _uiState.value as? SearchUiState.Content
            if (content != null) _uiState.value = content.copy(loadingMore = true)
        }

        searchJob = viewModelScope.launch {
            runCatching { searchTransactions(normalized) }
                .onSuccess { result ->
                    val zone = ZoneId.systemDefault()
                    val newItems = result.items.map { it.toListItem(zone) }
                    if (!append && newItems.isEmpty()) {
                        _uiState.value = SearchUiState.Empty
                        return@onSuccess
                    }
                    val previous = if (append) {
                        (_uiState.value as? SearchUiState.Content)?.items.orEmpty()
                    } else {
                        emptyList()
                    }
                    _uiState.value = SearchUiState.Content(
                        items = previous + newItems,
                        total = result.total,
                        hasMore = result.hasMore,
                        loadingMore = false,
                        page = normalized.page,
                    )
                }
                .onFailure { throwable ->
                    if (append) {
                        val content = _uiState.value as? SearchUiState.Content
                        if (content != null) _uiState.value = content.copy(loadingMore = false)
                        _events.emit(SearchEvent.Message(throwable.message ?: "加载更多失败"))
                    } else {
                        _uiState.value = SearchUiState.Error(
                            throwable.message ?: "搜索失败，请重试",
                        )
                    }
                }
        }
    }

    /** 与首页同源的展示模型（复用公共格式化函数；`TransactionRow` 直接消费）。 */
    private fun TransactionListEntry.toListItem(zone: ZoneId): TransactionListItem {
        val timeText = HomeViewModel.formatTimeOfDay(transactionTime, zone)
        val typeText = HomeViewModel.typeTextOf(type)
        val title = HomeViewModel.titleOf(merchant, note, categoryName, type)
        val plainAmount = HomeViewModel.formatMoney(
            amountMinor,
            sign = AmountSign.NONE,
            withCurrencySymbol = false,
        )
        return TransactionListItem(
            id = id,
            title = title,
            amountText = HomeViewModel.rowAmountOf(type, amountMinor),
            typeText = typeText,
            timeText = timeText,
            categoryName = categoryName,
            categoryIcon = categoryIcon,
            sourceText = HomeViewModel.sourceTextOf(source),
            type = type,
            accessibilityText = listOfNotNull(
                title,
                categoryName?.takeIf { it.isNotBlank() },
                timeText,
                "$typeText $plainAmount 元",
            ).joinToString(separator = "，"),
        )
    }

    private companion object {
        /** 关键字防抖（`M4e` §4.6）。 */
        const val KEYWORD_DEBOUNCE_MILLIS: Long = 300L
    }
}
