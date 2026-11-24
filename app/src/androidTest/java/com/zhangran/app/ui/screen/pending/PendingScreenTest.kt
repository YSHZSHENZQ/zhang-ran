/**
 * 待确认页展示（M6-2 / AC-4）：同一屏能读到金额、商户和原因。
 */
package com.zhangran.app.ui.screen.pending

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class PendingScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun 待确认行同时展示金额商户和原因() {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                PendingContent(
                    state = PendingUiState.Content(
                        rows = listOf(
                            PendingRow(
                                transactionId = "tx-1",
                                amountText = "¥38.50",
                                merchantText = "星巴克咖啡",
                                reasonText = "分类不确定、账户不确定",
                                sourceText = "无障碍",
                                timeText = "08:00",
                            ),
                        ),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("待确认记录").assertIsDisplayed()
        composeRule.onNodeWithText("¥38.50").assertIsDisplayed()
        composeRule.onNodeWithText("星巴克咖啡").assertIsDisplayed()
        composeRule.onNodeWithText("分类不确定、账户不确定").assertIsDisplayed()
        composeRule.onNodeWithText("确认").assertIsDisplayed()
        composeRule.onNodeWithText("改完再记").assertIsDisplayed()
        composeRule.onNodeWithText("忽略").assertIsDisplayed()
    }

    @Test
    fun 聚焦普通待确认可见确认且疑似重复不得直接确认() {
        val normal = PendingRow(
            transactionId = "normal",
            amountText = "¥25.10",
            merchantText = "示例早餐店",
            reasonText = "分类不确定",
            sourceText = "无障碍",
            timeText = "08:00",
        )
        val duplicate = PendingRow(
            transactionId = "dup",
            amountText = "¥25.10",
            merchantText = "示例早餐店",
            reasonText = "疑似重复",
            sourceText = "无障碍",
            timeText = "08:01",
            canConfirm = false,
            canEdit = false,
            canReview = true,
        )
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                PendingContent(
                    state = PendingUiState.Content(rows = listOf(normal, duplicate)),
                    focusTransactionId = "dup",
                )
            }
        }
        composeRule.onNodeWithText("示例早餐店").assertIsDisplayed()
        composeRule.onNodeWithText("查看重复").assertIsDisplayed()
        composeRule.onNodeWithText("忽略").assertIsDisplayed()
        composeRule.onNodeWithText("确认").assertDoesNotExist()
        composeRule.onNodeWithText("改完再记").assertDoesNotExist()
    }
}
