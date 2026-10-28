/**
 * 关于页 UI 测试（`M4d` §5；A1/A2/A4/A5/A10 的可测部分）。
 *
 * 纯静态页：验证真实版本、隐私四条、开源摘要展开、三个网络入口不存在、返回按钮可用。
 */
package com.zhangran.app.ui.screen.about

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.BuildConfig
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AboutScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun launchAbout() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                AboutScreen()
            }
        }
        composeRule.waitForIdle()
    }

    @Test
    fun A1_A2_展示真实版本且来自BuildConfig() {
        launchAbout()

        val version = "版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        composeRule.onNodeWithText(version).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "版本 ${BuildConfig.VERSION_NAME}，构建号 ${BuildConfig.VERSION_CODE}",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("账然").assertIsDisplayed()
    }

    @Test
    fun A3_隐私四条全部展示() {
        launchAbout()

        AboutInfo.PRIVACY_LINES.forEach { line ->
            composeRule.onNodeWithText("· $line").performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun A4_A5_开源摘要可展开且无网络入口() {
        launchAbout()

        composeRule.onNodeWithContentDescription("开源协议，双击查看", substring = true)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("账然自身未声明开源许可", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        // 三个网络入口与原型示例版本都不得出现
        listOf("GitHub 仓库", "反馈与建议", "检查更新", "版本 1.0.0").forEach { forbidden ->
            composeRule.onAllNodesWithText(forbidden, substring = true).assertCountEquals(0)
        }
    }

    @Test
    fun A4_采集核心含来源与commit() {
        launchAbout()

        composeRule.onNodeWithContentDescription("采集核心，双击查看", substring = true)
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText("冻结 commit：34b38f8", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("AutoAccounting", substring = true).assertIsDisplayed()
    }

    @Test
    fun 返回按钮可关闭页面() {
        launchAbout()

        composeRule.onNodeWithContentDescription("返回").performClick()
        composeRule.waitForIdle()
        assertTrue("返回按钮应由 Activity 返回栈处理", composeRule.activity.isFinishing)
    }
}
