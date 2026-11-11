/**
 * 「我的」页 UI 测试（`M4c` §5；覆盖 A2、A4、A5、A10、A12 的可测部分）。
 *
 * 页面是无状态 `MineContent`；设置持久化由 `SettingsRepositoryTest` 保证，这里验证
 * 三态、主题三选一、采集未接入项的禁用与说明、以及各入口的跳转回调。
 */
package com.zhangran.app.ui.screen.mine

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MineScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun content(
        themeMode: ThemeMode = ThemeMode.SYSTEM,
        pendingCount: Int = 0,
    ) = MineUiState.Content(
        settings = AppSettings(themeMode = themeMode),
        pendingCount = pendingCount,
    )

    private fun setContent(
        state: MineUiState,
        onThemeChange: (ThemeMode) -> Unit = {},
        onNavigate: (String) -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                MineContent(
                    state = state,
                    onThemeChange = onThemeChange,
                    onNavigate = onNavigate,
                    onRetry = onRetry,
                )
            }
        }
    }

    // ------------------------------------------------------------------ 三态

    @Test
    fun A10_加载态只显示页头() {
        setContent(MineUiState.Loading)

        composeRule.onNodeWithText("我的").assertIsDisplayed()
        composeRule.onNodeWithText("SETTINGS").assertIsDisplayed()
        composeRule.onAllNodesWithText("权限状态").assertCountEquals(0)
    }

    @Test
    fun A2_错误态显示原因并可重试() {
        var retried = false
        setContent(MineUiState.Error("读取设置失败"), onRetry = { retried = true })

        composeRule.onNodeWithText("读取设置失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    // ------------------------------------------------------------------ 内容

    @Test
    fun A1_展示设置分组与全部入口() {
        setContent(content(pendingCount = 3))

        listOf(
            "权限状态",
            "记账提醒",
            "待确认记录",
            "外观",
            "暗色主题",
            "数据管理",
            "分类管理",
            "账户管理",
            "解析规则",
            "数据备份与恢复",
            "捕获日志",
            "关于账然",
            "版本",
        ).forEach { label ->
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
        }
        composeRule.onNodeWithText("3 笔待处理").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun A2_采集未接入项禁用但可见且说明原因() {
        setContent(content())

        composeRule.onNodeWithContentDescription("自动记账，当前已关闭").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("记账提醒，当前已关闭，不可用").assertIsDisplayed()
        composeRule.onNodeWithText("总开关只在本页。关闭后不再自动记，已有待确认保留").assertIsDisplayed()
        composeRule.onNodeWithText("无障碍未授权").assertIsDisplayed()
        composeRule.onAllNodesWithText("未接入").assertCountEquals(0)
    }

    @Test
    fun A2_页头不伪装已连接() {
        setContent(content())
        composeRule.onNodeWithContentDescription("自动记账状态：不可用").assertIsDisplayed()
        composeRule.onAllNodesWithText("已连接").assertCountEquals(0)
        composeRule.onAllNodesWithText("未接入").assertCountEquals(0)
    }

    @Test
    fun A5_规则备份日志标注即将支持() {
        setContent(content())

        composeRule.onNodeWithText("即将支持 · 采集核心接入后可用").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("即将支持 · 本地备份与 settings.json").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("时间、来源、包名、状态和原因").performScrollTo().assertIsDisplayed()
    }

    // ------------------------------------------------------------------ 主题

    @Test
    fun A4_选择深色触发主题切换() {
        var selected: ThemeMode? = null
        setContent(content(themeMode = ThemeMode.SYSTEM), onThemeChange = { selected = it })

        composeRule.onNodeWithContentDescription("深色，未选中").performClick()
        assertEquals(ThemeMode.DARK, selected)
    }

    @Test
    fun A4_当前主题高亮且文案随状态变化() {
        setContent(content(themeMode = ThemeMode.DARK))

        composeRule.onNodeWithContentDescription("深色，已选中").assertIsDisplayed()
        composeRule.onNodeWithText("始终使用深色霓虹玻璃风格").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("浅色，未选中").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ 跳转

    @Test
    fun A5_数据管理入口跳转到既有路由() {
        val routes = mutableListOf<String>()
        setContent(content(), onNavigate = { routes += it })

        composeRule.onNodeWithContentDescription("分类管理", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("账户管理", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("解析规则", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("数据备份与恢复", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("捕获日志", substring = true).performScrollTo().performClick()

        assertEquals(
            listOf("categories", "accounts", "rules", "backup", "logs"),
            routes,
        )
    }

    @Test
    fun A5_待确认与关于入口可跳转() {
        val routes = mutableListOf<String>()
        setContent(content(pendingCount = 3), onNavigate = { routes += it })

        composeRule.onNodeWithContentDescription("待确认记录", substring = true).performClick()
        composeRule.onNodeWithContentDescription("关于账然", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("自动记账状态", substring = true).performScrollTo().performClick()

        assertEquals(listOf("pending", "about", "autoLedger"), routes)
    }
}
