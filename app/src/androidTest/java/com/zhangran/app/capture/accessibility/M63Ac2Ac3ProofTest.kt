/**
 * M6-3 AC-2 / AC-3：回放 F-WX 与 F-ALI，待确认列表与「改完再记」方向。
 *
 * 不点「保存并入账」。截图来自 `screencap`，不是 Compose Preview。
 */
package com.zhangran.app.capture.accessibility

import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.BuildConfig
import com.zhangran.app.MainActivity
import com.zhangran.app.dismissColdStartOnboard
import com.zhangran.app.domain.model.TransactionState
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M63Ac2Ac3ProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(500)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 回放两笔待确认并打开改完再记不入账() {
        dismissColdStartOnboard(composeRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            M3aDebugEntryPoint::class.java,
        )
        runBlocking { entry.debugLedgerReset().clearCapturedLedger() }
        val results = runBlocking {
            val ingest = entry.accessibilitySnapshotIngest()
            listOf(
                AccessibilityFixtures.F_WX to AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_ALI to AccessibilityFixtures.alipayFufu(),
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
        awaitDescription("待确认 2 条")
        composeRule.onNodeWithContentDescription("待确认 2 条").performClick()
        awaitText("待确认记录")
        awaitText("杭州深度求索")
        awaitText("¥10.00")
        awaitText("富富")
        awaitText("¥129.99")
        dismissKeyboard()
        shoot("ac2-f-wx-pending-list")

        openEdit(AccessibilityFixtures.F_WX, "杭州深度求索", "10.00", "ac2-f-wx-edit-direction")
        openEdit(AccessibilityFixtures.F_ALI, "富富", "129.99", "ac3-f-ali-edit-direction")

        val ledgerText = dump()
        val ledger = JSONObject(ledgerText)
        assertEquals(BuildConfig.GIT_SHA, ledger.getString("gitSha"))
        assertEquals(2, ledger.getInt("pendingCount"))
        assertEquals(0L, ledger.getLong("monthExpenseMinor"))
        assertEquals(0L, ledger.getLong("monthIncomeMinor"))
        assertEquals(2, ledger.getJSONArray("rows").length())
        assertFalse(ledgerText.contains("CONFIRMED"))

        val proof = JSONObject()
        proof.put("gitSha", ledger.getString("gitSha"))
        proof.put("pendingCount", ledger.getInt("pendingCount"))
        proof.put("monthExpenseMinor", ledger.getLong("monthExpenseMinor"))
        proof.put("monthIncomeMinor", ledger.getLong("monthIncomeMinor"))
        proof.put("booked", false)
        val shots = JSONObject()
        shots.put("pendingList", "ac2-f-wx-pending-list.png")
        shots.put(AccessibilityFixtures.F_WX, "ac2-f-wx-edit-direction.png")
        shots.put(AccessibilityFixtures.F_ALI, "ac3-f-ali-edit-direction.png")
        proof.put("screenshots", shots)
        val rows = JSONArray()
        listOf(
            AccessibilityFixtures.F_WX to 1_000L,
            AccessibilityFixtures.F_ALI to 12_999L,
        ).forEach { (fixtureId, amountMinor) ->
            val row = rowsOf(ledger).single { it.getString("fixtureId") == fixtureId }
            assertEquals("PENDING", row.getString("state"))
            assertEquals("ACCESSIBILITY", row.getString("source"))
            assertEquals("EXPENSE", row.getString("type"))
            assertEquals(amountMinor, row.getLong("amountMinor"))
            assertFalse(row.getBoolean("confirmedByUser"))
            val item = JSONObject()
            item.put("fixtureId", fixtureId)
            item.put("amountMinor", row.getLong("amountMinor"))
            item.put("merchant", row.getString("merchant"))
            item.put("type", row.getString("type"))
            item.put("state", row.getString("state"))
            item.put("source", row.getString("source"))
            item.put("confirmedByUser", false)
            rows.put(item)
        }
        proof.put("rows", rows)
        File(filesDir(), "ac2-ac3-replay.json").writeText(proof.toString())
    }

    private fun openEdit(fixtureId: String, merchant: String, amount: String, shot: String) {
        composeRule.onNodeWithContentDescription("$fixtureId 改完再记")
            .performScrollTo()
            .performClick()
        awaitText("编辑后入账")
        awaitText(merchant)
        awaitText(amount)
        scrollToText("支出")
        dismissKeyboard()
        shoot(shot)
        Espresso.pressBack()
        awaitText("待确认记录")
    }

    private fun dismissKeyboard() {
        composeRule.waitForIdle()
        val activity = composeRule.activity
        val imm = activity.getSystemService(InputMethodManager::class.java)
        val token = activity.currentFocus?.windowToken ?: activity.window.decorView.windowToken
        imm.hideSoftInputFromWindow(token, 0)
        composeRule.waitForIdle()
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true).performScrollTo().assertIsDisplayed()
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

    private fun dump(): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            M3aDebugEntryPoint::class.java,
        )
        return runBlocking { entry.ledgerProofQuery().json(BuildConfig.GIT_SHA) }
    }

    private fun filesDir(): File =
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    private fun rowsOf(json: JSONObject): List<JSONObject> {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
    }
}
