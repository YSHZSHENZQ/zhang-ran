/**
 * 首页 UI 的设备侧测试（`M2e-1b` §5，对应验收 A1/A3/A9/A10/A15）。
 *
 * 覆盖：
 * - 四态渲染（骨架屏 / 空态 / 错误态 / 内容态）；
 * - 顶栏两个入口 + 自动记账状态入口 + 空态两个引导 + 交易行详情入口（**路由与 id 均断言**）；
 * - 待确认红点仅在 `pendingCount > 0` 时出现，且 `> 99` 显示 `99+`；
 * - **不实现 `RECORDS`**（`docs/06` §131）；
 * - 余额卡片的无障碍描述。
 *
 * 纪律（`docs/09` §5）：每个测试方法只 `setContent` 一次，标准断言 + `Timeout` 兜底。
 */
package com.zhangran.app.ui.screen.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.HomeSummary
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.screen.home.components.HOME_SKELETON_TAG
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    /** 兜底超时：任何情况下 60s 内必须给出结论（避免"挂死而不是失败"）。 */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(id: String = "tx-1") = TransactionListItem(
        id = id,
        title = "星巴克咖啡",
        amountText = "-38.50",
        typeText = "支出",
        timeText = "14:23",
        categoryName = "餐饮",
        categoryIcon = "food",
        sourceText = "手动",
        type = TransactionType.EXPENSE,
        accessibilityText = "星巴克咖啡，餐饮，14:23，支出 38.50 元",
    )

    private fun content(
        pendingCount: Int = 3,
        sections: List<HomeSection> = listOf(HomeSection(label = "今天", items = listOf(item()))),
    ) = HomeUiState.Content(
        sections = sections,
        summary = HomeSummary(
            monthExpenseMinor = 384_720L,
            monthIncomeMinor = 1_200_000L,
            monthBalanceMinor = 815_280L,
            pendingCount = pendingCount,
        ),
    )

    // ------------------------------------------------------------------ 四态

    @Test
    fun 加载态显示骨架屏与顶栏() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = HomeUiState.Loading) }
        }
        composeRule.onNodeWithText("账然").assertIsDisplayed()
        composeRule.onNodeWithTag(HOME_SKELETON_TAG).assertIsDisplayed()
    }

    @Test
    fun 空态显示说明与两个引导() {
        var navigated: String? = null
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(state = HomeUiState.Empty, onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithText("还没有记录").assertIsDisplayed()
        composeRule.onNodeWithText("记一笔").assertIsDisplayed().performClick()
        assertEquals(Destination.Add.route, navigated)
    }

    @Test
    fun 空态的检查自动记账引导跳转自动记账页() {
        var navigated: String? = null
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(state = HomeUiState.Empty, onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithText("检查自动记账").assertIsDisplayed().performClick()
        assertEquals(Destination.AutoLedger.route, navigated)
    }

    @Test
    fun 错误态显示信息并可重试() {
        var retried = false
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(
                    state = HomeUiState.Error("数据库打开失败"),
                    onNavigate = {},
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed().performClick()
        assertTrue("重试必须回调 ViewModel", retried)
    }

    // ------------------------------------------------------------------ 内容态

    @Test
    fun 内容态显示余额卡片分组标题与交易行() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = content()) }
        }
        composeRule.onNodeWithText("本月支出").assertIsDisplayed()
        // 大字金额（整数与小数分行内渲染，断言包含千分位整数部分即可）
        composeRule.onNodeWithText("¥3,847", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("INCOME").assertIsDisplayed()
        composeRule.onNodeWithText("BALANCE").assertIsDisplayed()
        composeRule.onNodeWithText("今天").assertIsDisplayed()
        composeRule.onNodeWithText("星巴克咖啡").assertIsDisplayed()
        composeRule.onNodeWithText("餐饮 · 14:23 · 手动").assertIsDisplayed()
        composeRule.onNodeWithText("-38.50").assertIsDisplayed()
    }

    @Test
    fun 内容态不显示RECORDS指标() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = content()) }
        }
        composeRule.onNodeWithText("RECORDS").assertDoesNotExist()
        composeRule.onNodeWithText("128").assertDoesNotExist()
    }

    @Test
    fun 余额卡片有整句无障碍描述() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = content()) }
        }
        composeRule
            .onNodeWithContentDescription("本月支出 3,847.20 元，本月收入 12,000.00 元，本月结余 8,152.80 元")
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------------ 入口（A10）

    @Test
    fun 顶栏搜索与待确认入口可点且带数量语义() {
        var navigated: String? = null
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(state = content(pendingCount = 3), onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithContentDescription("待确认 3 条").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("搜索").performClick()
        assertEquals(Destination.Search.route, navigated)
        composeRule.onNodeWithContentDescription("待确认 3 条").performClick()
        assertEquals(Destination.Pending.route, navigated)
    }

    @Test
    fun 待确认数量超过99显示99加() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = content(pendingCount = 120)) }
        }
        composeRule.onNodeWithContentDescription("待确认 120 条").assertIsDisplayed()
        composeRule.onNodeWithText("99+").assertIsDisplayed()
    }

    @Test
    fun 无待确认时红点不显示() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) { HomeContent(state = content(pendingCount = 0)) }
        }
        composeRule.onNodeWithContentDescription("待确认").assertIsDisplayed()
        composeRule.onNodeWithText("99+").assertDoesNotExist()
    }

    @Test
    fun 自动记账状态入口可点且不显示正常运行() {
        var navigated: String? = null
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(state = content(), onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithText("AUTO LEDGER · 不可用").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("自动记账状态：不可用").performClick()
        assertEquals(Destination.AutoLedger.route, navigated)
        assertTrue(composeRule.onAllNodesWithText("未接入").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun 点击交易行导航到详情并带交易id() {
        var navigated: String? = null
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                HomeContent(state = content(), onNavigate = { navigated = it })
            }
        }
        composeRule.onNodeWithText("星巴克咖啡").performClick()
        assertEquals(Destination.detailRoute("tx-1"), navigated)
    }
}
