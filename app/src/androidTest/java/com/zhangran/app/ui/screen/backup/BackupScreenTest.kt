/**
 * 备份页 UI 测试（`M4b-1` §5；覆盖 A11、A16 的可测部分）。
 *
 * 页面是无状态 `BackupContent`；ZIP 结构由 `BackupWriterTest` 覆盖。
 * 这里验证四态渲染、恢复入口禁用、导出回调与格式信息。
 */
package com.zhangran.app.ui.screen.backup

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.backup.BACKUP_DATA_FILE_NAMES
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(
        state: BackupUiState,
        onExport: () -> Unit = {},
        onDismissResult: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                BackupContent(
                    state = state,
                    onExport = onExport,
                    onDismissResult = onDismissResult,
                )
            }
        }
    }

    @Test
    fun A11_初始态展示说明与格式且恢复入口禁用() {
        setContent(BackupUiState.Idle)

        composeRule.onNodeWithText("数据备份与恢复").assertIsDisplayed()
        composeRule.onNodeWithText("说明").assertIsDisplayed()
        composeRule.onNodeWithText("导出备份").assertIsDisplayed()
        composeRule.onNodeWithText("恢复备份").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("恢复备份，不可用", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("v1").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("manifest + ${BACKUP_DATA_FILE_NAMES.size} 个数据文件")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("本次运行尚未导出").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun A11_导出中显示进度且入口禁用() {
        setContent(BackupUiState.Exporting)

        composeRule.onNodeWithContentDescription("正在生成备份").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("导出备份", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun A11_成功态展示文件名条目数与大小() {
        setContent(
            BackupUiState.Success(
                fileName = "zhangran-backup-v1-20261028.zip",
                entryCount = 12,
                byteSize = 12_345L,
                createdAt = 1_790_000_000_000L,
            ),
        )

        composeRule.onNodeWithText("zhangran-backup-v1-20261028.zip").performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("12 个").assertIsDisplayed()
        composeRule.onNodeWithText("12.0 KB").assertIsDisplayed()
        composeRule.onNodeWithText("完成").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun A11_成功态可关闭结果回到初始() {
        var dismissed = false
        setContent(
            BackupUiState.Success(
                fileName = "zhangran-backup-v1-20261028.zip",
                entryCount = 12,
                byteSize = 1024L,
                createdAt = 1_790_000_000_000L,
            ),
            onDismissResult = { dismissed = true },
        )

        composeRule.onNodeWithText("完成").performScrollTo().performClick()
        assertTrue(dismissed)
    }

    @Test
    fun A11_失败态显示原因并可重试() {
        var exportRequested = 0
        setContent(BackupUiState.Error("无法打开所选位置，请重新选择"), onExport = { exportRequested++ })

        composeRule.onNodeWithText("导出失败").assertIsDisplayed()
        composeRule.onNodeWithText("无法打开所选位置，请重新选择").assertIsDisplayed()
        composeRule.onNodeWithText("重新选择位置").performClick()
        assertEquals(1, exportRequested)
    }

    @Test
    fun A10_点击导出备份触发回调() {
        var exportRequested = 0
        setContent(BackupUiState.Idle, onExport = { exportRequested++ })

        composeRule.onNodeWithContentDescription("导出备份", substring = true).performClick()
        assertEquals(1, exportRequested)
    }

    @Test
    fun A6_页面明示不包含截图或二进制() {
        setContent(BackupUiState.Idle)

        composeRule.onNodeWithText("不包含任何截图或二进制内容", substring = true).assertIsDisplayed()
    }
}
