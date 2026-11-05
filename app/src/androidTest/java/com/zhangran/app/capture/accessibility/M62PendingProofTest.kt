/**
 * 把固定支付结果 fixture 写入真实库，并停在「待确认」页，供设备截图（M6-2 / AC-4）。
 */
package com.zhangran.app.capture.accessibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M62PendingProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(120)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 待确认页展示支付结果fixture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ingest = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AccessibilityIngestEntryPoint::class.java,
        ).accessibilitySnapshotIngest()
        val result = runBlocking {
            ingest.ingest(
                AccessibilityFixtures.wechatPaySuccess(),
                AccessibilityFixtures.WECHAT_PAY_SUCCESS,
            )
        }
        check(result.transactionState == com.zhangran.app.domain.model.TransactionState.PENDING) {
            "fixture 未进入待确认：${result.toProofJson()}"
        }

        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("待确认", substring = true).performClick()
        composeRule.onNodeWithText("星巴克咖啡").assertIsDisplayed()
        composeRule.onNodeWithText("¥38.50").assertIsDisplayed()
        composeRule.onNodeWithText("分类不确定", substring = true).assertIsDisplayed()
        composeRule.waitForIdle()
        Thread.sleep(HOLD_FOR_SCREENCAP_MS)
    }

    @Test
    fun 首页正式收支不因待确认增加() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val ingest = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AccessibilityIngestEntryPoint::class.java,
        ).accessibilitySnapshotIngest()
        val result = runBlocking {
            ingest.ingest(
                AccessibilityFixtures.wechatPaySuccess(capturedAt = System.currentTimeMillis()),
                AccessibilityFixtures.WECHAT_PAY_SUCCESS,
            )
        }
        check(result.transactionState == com.zhangran.app.domain.model.TransactionState.PENDING) {
            "fixture 未进入待确认：${result.toProofJson()}"
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("本月支出").assertIsDisplayed()
        composeRule.onNodeWithText("¥0.00", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("+0.00").assertIsDisplayed()
        composeRule.waitForIdle()
        Thread.sleep(HOLD_FOR_SCREENCAP_MS)
    }

    private companion object {
        const val HOLD_FOR_SCREENCAP_MS: Long = 20_000L
    }
}
