/**
 * 搜索页 UI 状态（`M4e` §4.5；行为 SSOT：`docs/06` §4.17）。
 *
 * 状态：`Idle`（未搜索）/ `Loading` / `Content`（结果）/ `Empty`（无结果）/ `Error`。
 * `Content` 带分页信息，供滚动加载与"到底不再加载"。
 */
package com.zhangran.app.ui.screen.search

import com.zhangran.app.ui.screen.home.TransactionListItem

/** 筛选弹层用的选项（分类/账户）。 */
data class SearchOptions(
    val categories: List<SearchCategoryOption> = emptyList(),
    val accounts: List<SearchAccountOption> = emptyList(),
)

data class SearchCategoryOption(val id: String, val name: String)

data class SearchAccountOption(val id: String, val name: String)

sealed interface SearchUiState {

    /** 首次进入 / 条件清空：不发起搜索，显示引导。 */
    data object Idle : SearchUiState

    data object Loading : SearchUiState

    /**
     * @property items 已加载的所有页（第 0 页 + 后续页）
     * @property total 同条件总条数
     * @property hasMore 是否还有下一页
     * @property loadingMore 正在加载下一页
     * @property page 已加载到的页（0-based）
     */
    data class Content(
        val items: List<TransactionListItem>,
        val total: Int,
        val hasMore: Boolean,
        val loadingMore: Boolean = false,
        val page: Int = 0,
    ) : SearchUiState

    data object Empty : SearchUiState

    data class Error(val message: String) : SearchUiState
}

/** 一次性事件（加载更多失败等）。 */
sealed interface SearchEvent {
    data class Message(val text: String) : SearchEvent
}
