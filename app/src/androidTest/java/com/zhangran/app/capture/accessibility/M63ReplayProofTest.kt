/**
 * M6-3 回放取证：F-WX、F-ALI、M6-2 只进待确认，首页正式收支不增加。
 *
 * 截图来自 `screencap`（模拟器帧缓冲），不是 Compose Preview。
 * 无头模拟器上 `UiAutomation.takeScreenshot()` 会返回 null。
 */
package com.zhangran.app.capture.accessibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.BuildConfig
import com.zhangran.app.MainActivity
import com.zhangran.app.domain.model.TransactionState
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M63ReplayProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(700)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 回放三笔待确认且正式收支不变() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            M3aDebugEntryPoint::class.java,
        )
        runBlocking { entry.debugLedgerReset().clearCapturedLedger() }
        composeRule.waitForIdle()
        // 空账本是 Empty，不画余额卡片；本月支出 ¥0.00 要等有待确认行之后才出现。
        awaitText("还没有记录")
        awaitDescription("待确认")
        shoot("ac6-home-before")
        val before = JSONObject(dump("ac6-before"))
        assertEquals(0, before.getInt("pendingCount"))
        assertEquals(0L, before.getLong("monthExpenseMinor"))
        assertEquals(0L, before.getLong("monthIncomeMinor"))
        assertEquals(0, before.getJSONArray("rows").length())

        val results = runBlocking {
            val ingest = entry.accessibilitySnapshotIngest()
            listOf(
                AccessibilityFixtures.F_WX to AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_ALI to AccessibilityFixtures.alipayFufu(),
                AccessibilityFixtures.WECHAT_PAY_SUCCESS to AccessibilityFixtures.wechatPaySuccess(),
            ).map { (fixtureId, snapshot) ->
                fixtureId to ingest.ingest(snapshot, fixtureId)
            }
        }
        results.forEach { (fixtureId, result) ->
            check(result.transactionState == TransactionState.PENDING) {
                "$fixtureId 未进入待确认：${result.toProofJson()}"
            }
        }

        composeRule.waitForIdle()
        awaitDescription("待确认 3 条")
        awaitText("本月支出")
        awaitText("¥0.00")
        scrollToText("杭州深度求索")
        scrollToText("富富")
        scrollToText("星巴克咖啡")
        shoot("ac6-home-after")

        val afterText = dump("ac6-after")
        val after = JSONObject(afterText)
        assertEquals(BuildConfig.GIT_SHA, after.getString("gitSha"))
        assertEquals(before.getLong("monthExpenseMinor"), after.getLong("monthExpenseMinor"))
        assertEquals(before.getLong("monthIncomeMinor"), after.getLong("monthIncomeMinor"))
        assertEquals(0L, after.getLong("monthExpenseMinor"))
        assertEquals(0L, after.getLong("monthIncomeMinor"))
        assertEquals(3, after.getInt("pendingCount"))
        assertEquals(3, after.getJSONArray("rows").length())
        assertFalse(afterText.contains("CONFIRMED"))

        writeRow("ac4-f-wx", after, AccessibilityFixtures.F_WX, 1_000L, "杭州深度求索")
        writeRow("ac4-f-ali", after, AccessibilityFixtures.F_ALI, 12_999L, "富富")
        writeRow("ac5-m6-2", after, AccessibilityFixtures.WECHAT_PAY_SUCCESS, 3_850L, "星巴克咖啡")
        File(filesDir(), "git-sha.txt").writeText(BuildConfig.GIT_SHA)
    }

    private fun writeRow(
        name: String,
        ledger: JSONObject,
        fixtureId: String,
        amountMinor: Long,
        merchant: String,
    ) {
        val row = rows(ledger).single { it.getString("fixtureId") == fixtureId }
        assertEquals("PENDING", row.getString("state"))
        assertEquals("ACCESSIBILITY", row.getString("source"))
        assertEquals("EXPENSE", row.getString("type"))
        assertEquals(amountMinor, row.getLong("amountMinor"))
        assertEquals(merchant, row.getString("merchant"))
        assertFalse(row.getBoolean("confirmedByUser"))
        val proof = JSONObject()
        proof.put("gitSha", ledger.getString("gitSha"))
        proof.put("fixtureId", fixtureId)
        proof.put("amountMinor", row.getLong("amountMinor"))
        proof.put("merchant", row.getString("merchant"))
        proof.put("type", row.getString("type"))
        proof.put("state", row.getString("state"))
        proof.put("source", row.getString("source"))
        proof.put("confirmedByUser", row.getBoolean("confirmedByUser"))
        proof.put("pendingCount", ledger.getInt("pendingCount"))
        proof.put("monthExpenseMinor", ledger.getLong("monthExpenseMinor"))
        proof.put("monthIncomeMinor", ledger.getLong("monthIncomeMinor"))
        File(filesDir(), "$name.json").writeText(proof.toString())
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private fun scrollToText(text: String) {
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    private fun awaitDescription(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text).assertIsDisplayed()
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val file = File(filesDir(), "$name.png")
        val pipe = automation.executeShellCommand("screencap -p")
        FileInputStream(pipe.fileDescriptor).use { input ->
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        pipe.close()
        check(file.length() > 1_000L) { "截图为空：$name" }
    }

    private fun dump(name: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            M3aDebugEntryPoint::class.java,
        )
        val json = runBlocking { entry.ledgerProofQuery().json(BuildConfig.GIT_SHA) }
        File(filesDir(), "$name.json").writeText(json)
        return json
    }

    private fun filesDir(): File =
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    private fun rows(json: JSONObject): List<JSONObject> {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
    }
}
