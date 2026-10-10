/**
 * 18 个占位页面的渲染冒烟测试（T1b，instrumentation）。
 *
 * 断言依据任务书 §4.6 与 `docs/08` §13：每个占位页面能渲染并显示页面名文本；明暗主题均可渲染。
 *
 * 设计要点（T1b 实测踩坑后修正，三条都必须遵守）：
 *
 * 1. **每个测试方法只允许一次 `setContent`**。同一方法内循环 `setContent` 是非法用法，
 *    第二次起会永久挂起（表现为 Activity 已启动但再无任何测试日志）。
 * 2. **不要用轮询替代标准断言**：曾把 `assertIsDisplayed` 换成 `waitUntil` 轮询，结果更糟——
 *    `waitUntil` 在测试线程轮询，而组合推进需要主线程空闲，导致用例根本不再启动。
 *    保留标准断言，用 `Timeout` 规则兜底“挂死而非失败”的情况。
 * 3. 每个用例都带 `Timeout` 规则，避免将来再出现“挂死而不是失败”。
 */
package com.zhangran.app

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class ScreensSmokeTest(
    private val destination: Destination,
) {

    companion object {
        /** 参数化输入：`docs/06` §4 的全部 18 个页面。 */
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun destinations(): List<Destination> = Destination.entries.toList()
    }

    /** 兜底超时：任何情况下 60s 内必须给出结论（通过或失败）。 */
    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    /** 浅色主题：页面渲染并显示页面名。 */
    @Test
    fun 页面可渲染并显示页面名() {
        assertPageRenders(darkTheme = false)
    }

    /** 深色主题：页面渲染并显示页面名（`docs/05` §3、任务书 §4.2）。 */
    @Test
    fun 深色主题下可渲染并显示页面名() {
        assertPageRenders(darkTheme = true)
    }

    /**
     * 渲染当前 [destination] 的占位页面，并确认页面名文本已显示。
     *
     * 使用标准断言 `onNodeWithText(...).assertIsDisplayed()`（内部走 idle 同步）。
     * 曾尝试用 `waitUntil` 轮询替代，实测更糟：它在测试线程上轮询，而组合需要主线程空闲才能推进，
     * 结果连测试用例都不再启动。故此处保留标准写法，超时由上面的 `Timeout` 规则兜底。
     */
    private fun assertPageRenders(darkTheme: Boolean) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = darkTheme) { screenFor(destination) }
        }
        composeRule.onNodeWithText(destination.title).assertIsDisplayed()
    }
}

/**
 * 按目的地渲染对应占位页面。
 *
 * 显式列出 18 个页面（不用反射）：新增页面若忘记接线，编译期即可发现。
 */
@Composable
private fun screenFor(destination: Destination) {
    when (destination) {
        Destination.Home -> com.zhangran.app.ui.screen.home.HomeScreen()
        Destination.Stats -> com.zhangran.app.ui.screen.stats.StatsScreen()
        Destination.Add -> com.zhangran.app.ui.screen.add.AddTransactionScreen()
        Destination.Budget -> com.zhangran.app.ui.screen.budget.BudgetScreen()
        Destination.Mine -> com.zhangran.app.ui.screen.mine.MineScreen()
        Destination.Detail -> com.zhangran.app.ui.screen.detail.TransactionDetailScreen()
        Destination.Pending -> com.zhangran.app.ui.screen.pending.PendingScreen()
        Destination.PendingEdit -> com.zhangran.app.ui.screen.pendingEdit.PendingEditScreen()
        Destination.DuplicateReview -> com.zhangran.app.ui.screen.duplicateReview.DuplicateReviewScreen()
        Destination.AutoLedger -> com.zhangran.app.ui.screen.autoLedger.AutoLedgerScreen()
        Destination.Onboard -> com.zhangran.app.ui.screen.onboard.OnboardScreen()
        Destination.Categories -> com.zhangran.app.ui.screen.categories.CategoriesScreen()
        Destination.Accounts -> com.zhangran.app.ui.screen.accounts.AccountsScreen()
        Destination.Rules -> com.zhangran.app.ui.screen.rules.RulesScreen()
        Destination.Backup -> com.zhangran.app.ui.screen.backup.BackupScreen()
        Destination.Logs -> com.zhangran.app.ui.screen.logs.LogsScreen()
        Destination.Search -> com.zhangran.app.ui.screen.search.SearchScreen()
        Destination.About -> com.zhangran.app.ui.screen.about.AboutScreen()
    }
}
