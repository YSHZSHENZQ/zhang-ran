/**
 * 导航行为设备测试（`M5-1` §5；A1、A2、A5–A8、A14）。
 *
 * 用 `ComponentActivity` 承载真实 NavHost（Hilt Application 由被测应用提供），
 * 以便同时验证 UI 与系统返回键。
 */
package com.zhangran.app.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationBehaviorTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(120)

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var controller: NavHostController

    private fun launchApp() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                controller = rememberNavController()
                ZhangRanApp(controller)
            }
        }
        composeRule.waitForIdle()
    }

    private fun pressBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    private fun navigateToTopLevel(destination: Destination) {
        composeRule.runOnUiThread {
            controller.navigate(destination.route) {
                popUpTo(Destination.start.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun A1_A14_首页显示5个标签且首页已选中() {
        launchApp()

        listOf("首页", "统计", "记一笔", "预算", "我的").forEach { title ->
            composeRule.onNodeWithContentDescription("$title，标签页", substring = true)
                .assertIsDisplayed()
        }
        composeRule.onNodeWithContentDescription("首页，标签页，已选中").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("统计，标签页，未选中").assertIsDisplayed()
    }

    @Test
    fun A2_二级页隐藏底栏() {
        launchApp()
        composeRule.runOnUiThread { controller.navigate(Destination.Categories.route) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("分类管理").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("标签页", substring = true)
            .assertCountEquals(0)
    }

    @Test
    fun A7_二级页返回回上一页且底栏恢复() {
        launchApp()
        composeRule.runOnUiThread { controller.navigate(Destination.Categories.route) }
        composeRule.waitForIdle()

        pressBack()

        composeRule.onNodeWithContentDescription("首页，标签页，已选中").assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("标签页", substring = true)
            .assertCountEquals(5)
    }

    @Test
    fun A6_统计页返回首页不退出() {
        launchApp()
        composeRule.onNodeWithContentDescription("统计，标签页，未选中").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("统计，标签页，已选中").assertIsDisplayed()

        pressBack()

        composeRule.onNodeWithContentDescription("首页，标签页，已选中").assertIsDisplayed()
    }

    @Test
    fun A8_首页返回退出应用() {
        launchApp()
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
        assertTrue("首页返回应结束 Activity", composeRule.activity.isFinishing)
    }

    @Test
    fun A5_切走再切回保留栈() {
        launchApp()
        // 统计 → 详情
        composeRule.onNodeWithContentDescription("统计，标签页，未选中").performClick()
        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            controller.navigate("${Destination.Detail.route.substringBefore("?")}?${Destination.ARG_TRANSACTION_ID}=t1")
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("交易详情").assertIsDisplayed()

        // 切到首页，再切回统计（与底栏点击相同的导航选项）
        navigateToTopLevel(Destination.Home)
        navigateToTopLevel(Destination.Stats)

        // 期望：回到统计栈顶（详情）
        composeRule.onNodeWithText("交易详情").assertIsDisplayed()
    }
}
