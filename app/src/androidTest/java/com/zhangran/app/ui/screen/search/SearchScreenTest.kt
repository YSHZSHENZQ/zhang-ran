/**
 * 搜索页 UI 测试（`M4e` §5；A18 三态、输入、chips、高级筛选入口）。
 *
 * 直接渲染无状态 `SearchContent`，不依赖 Hilt/数据库。
 */
package com.zhangran.app.ui.screen.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.screen.home.TransactionListItem
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class SearchScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: SearchUiState,
        criteria: SearchCriteria = SearchCriteria(),
        onKeywordChange: (String) -> Unit = {},
        onAll: () -> Unit = {},
        onOpenCategory: () -> Unit = {},
        onClearFilters: () -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                SearchContent(
                    state = state,
                    criteria = criteria,
                    options = SearchOptions(),
                    onKeywordChange = onKeywordChange,
                    onAll = onAll,
                    onOpenCategory = onOpenCategory,
                    onClearFilters = onClearFilters,
                    onRetry = onRetry,
                    onBack = onBack,
                )
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun A11_首次进入为Idle且不显示结果() {
        setContent(SearchUiState.Idle)
        composeRule.onNodeWithText("输入关键字或选择筛选条件").assertIsDisplayed()
        composeRule.onNodeWithText("共 0 条").assertDoesNotExist()
    }

    @Test
    fun A18_Content显示条数与结果行() {
        setContent(
            SearchUiState.Content(
                items = listOf(item("t1", "星巴克咖啡")),
                total = 1,
                hasMore = false,
            ),
        )
        composeRule.onNodeWithText("共 1 条").assertIsDisplayed()
        composeRule.onNodeWithText("星巴克咖啡").assertIsDisplayed()
    }

    @Test
    fun A18_Empty显示清空筛选并可点击() {
        var cleared = false
        setContent(SearchUiState.Empty, onClearFilters = { cleared = true })
        composeRule.onNodeWithText("没有符合条件的记录").assertIsDisplayed()
        composeRule.onNodeWithText("清空筛选").performClick()
        assertTrue(cleared)
    }

    @Test
    fun A18_Error显示重试() {
        var retried = false
        setContent(SearchUiState.Error("搜索失败"), onRetry = { retried = true })
        composeRule.onNodeWithText("搜索失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun A12_输入关键字触发回调() {
        var keyword = ""
        setContent(SearchUiState.Idle, onKeywordChange = { keyword = it })
        composeRule.onNode(hasSetTextAction()).performTextInput("咖啡")
        assertTrue(keyword.contains("咖啡"))
    }

    @Test
    fun A9_分类chip打开筛选弹层() {
        var opened = false
        setContent(SearchUiState.Idle, onOpenCategory = { opened = true })
        composeRule.onNodeWithContentDescription("筛选：分类，未选中，双击选择").performClick()
        assertTrue(opened)
    }

    @Test
    fun A9_类型chip在已选中时显示值() {
        setContent(
            SearchUiState.Idle,
            criteria = SearchCriteria(type = TransactionType.EXPENSE),
        )
        composeRule.onNodeWithContentDescription("筛选：类型，已选中，双击选择").assertIsDisplayed()
        composeRule.onNodeWithText("类型：支出").assertIsDisplayed()
    }

    @Test
    fun 全部chip清空筛选() {
        var all = false
        setContent(
            SearchUiState.Idle,
            criteria = SearchCriteria(categoryId = "cat-food"),
            onAll = { all = true },
        )
        composeRule.onNodeWithContentDescription("筛选：全部，未选中，双击选择").performClick()
        assertTrue(all)
    }

    @Test
    fun 返回按钮回调() {
        var backed = false
        setContent(SearchUiState.Idle, onBack = { backed = true })
        composeRule.onNodeWithContentDescription("返回").performClick()
        assertTrue(backed)
    }

    private fun item(id: String, title: String) = TransactionListItem(
        id = id,
        title = title,
        amountText = "-38.50",
        typeText = "支出",
        timeText = "14:23",
        categoryName = "餐饮",
        categoryIcon = "food",
        sourceText = "手动",
        type = TransactionType.EXPENSE,
        accessibilityText = "$title，餐饮，14:23，支出 38.50 元",
    )
}
