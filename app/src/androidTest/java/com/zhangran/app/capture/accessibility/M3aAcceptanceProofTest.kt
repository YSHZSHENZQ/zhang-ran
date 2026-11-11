/**
 * M3a 验收取证：在真机/模拟器上走确认、改完再记、忽略，并导出 JSON 与截图。
 *
 * 截图来自 UiAutomation.takeScreenshot，不是 Compose Preview。
 */
package com.zhangran.app.capture.accessibility

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.BuildConfig
import com.zhangran.app.MainActivity
import com.zhangran.app.dismissColdStartOnboard
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.TransactionState
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3aAcceptanceProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(600)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 待确认三种处置与统计保护取证() {
        dismissColdStartOnboard(composeRule)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            M3aDebugEntryPoint::class.java,
        )
        val base = System.currentTimeMillis()
        val ids = linkedMapOf<String, String>()
        runBlocking {
            entry.debugLedgerReset().clearCapturedLedger()
            val ingest = entry.accessibilitySnapshotIngest()
            suspend fun seed(fixtureId: String, capturedAt: Long, merchant: String = "星巴克咖啡") {
                val result = ingest.ingest(
                    AccessibilityFixtures.wechatPaySuccess(capturedAt = capturedAt, merchant = merchant),
                    fixtureId,
                )
                check(result.transactionState == TransactionState.PENDING) {
                    "fixture 未进入待确认：$fixtureId ${result.toProofJson()}"
                }
                ids[fixtureId] = result.transactionId!!
            }
            seed(F_A, base - 50 * MINUTE)
            seed(F_B, base - 40 * MINUTE)
            seed(F_C, base - 30 * MINUTE)
            seed(F_D, base - 30 * MINUTE + 30_000L, merchant = "瑞幸咖啡")
            seed(F_E, base - 20 * MINUTE)
        }

        composeRule.waitForIdle()
        awaitDescription("待确认 5 条")
        composeRule.onNodeWithText("¥0.00", substring = true).assertIsDisplayed()
        shoot("ac5-home-before")
        val before = dump("ac5-before")
        val beforeExpense = JSONObject(before).getLong("monthExpenseMinor")
        val beforeIncome = JSONObject(before).getLong("monthIncomeMinor")

        composeRule.onNodeWithContentDescription("待确认 5 条").performClick()
        composeRule.onNodeWithText(F_A).assertIsDisplayed()
        composeRule.onNodeWithText(F_D).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$F_A 确认").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$F_A 改完再记").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$F_A 忽略").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$F_D 忽略").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("$F_D 确认").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("$F_D 改完再记").assertDoesNotExist()
        composeRule.onNodeWithText("查看重复").assertDoesNotExist()
        shoot("ac1-pending-actions")

        val fd = row(dump("ac1"), F_D)
        assertTrue(fd.getString("reasons").contains("POSSIBLE_DUPLICATE"))
        assertEquals("PENDING", fd.getString("state"))

        composeRule.onNodeWithContentDescription("$F_A 确认").performScrollTo().performClick()
        awaitGone(F_A)
        composeRule.onNodeWithText(F_B).assertIsDisplayed()
        shoot("ac2-pending-after-confirm")
        val ac2 = row(dump("ac2"), F_A)
        assertEquals("CONFIRMED", ac2.getString("state"))
        assertTrue(ac2.getBoolean("confirmedByUser"))
        assertEquals(0, ac2.getJSONArray("fieldEdits").length())

        composeRule.onNodeWithContentDescription("$F_B 改完再记").performScrollTo().performClick()
        composeRule.onNodeWithText("保存并入账").assertIsDisplayed()
        composeRule.onNodeWithText("餐饮").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("备注输入").performScrollTo().performTextReplacement(NOTE)
        composeRule.onNodeWithText("保存并入账").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(NOTE).assertIsDisplayed()
        shoot("ac3-edit-before-save")
        composeRule.onNodeWithText("保存并入账").performClick()
        awaitText("待确认记录")
        awaitGone(F_B)
        shoot("ac3-pending-after-save")
        val foodId = runBlocking {
            entry.categoryRepository().findByName(CategoryType.EXPENSE, "餐饮")!!.id
        }
        val ac3 = row(dump("ac3"), F_B)
        assertEquals("CONFIRMED", ac3.getString("state"))
        assertTrue(ac3.getBoolean("confirmedByUser"))
        assertEquals(foodId, ac3.getString("categoryId"))
        assertEquals(NOTE, ac3.getString("note"))
        assertEquals(listOf("categoryId", "note"), jsonStrings(ac3.getJSONArray("fieldEdits")).sorted())

        val ac4Before = row(dump("ac4-before"), F_C)
        val evidenceBefore = ac4Before.getInt("evidenceCount")
        val logsBefore = ac4Before.getInt("captureLogCount")
        assertTrue(evidenceBefore >= 1)
        assertTrue(logsBefore >= 1)
        composeRule.onNodeWithContentDescription("$F_C 忽略").performScrollTo().performClick()
        awaitGone(F_C)
        val ac4 = row(dump("ac4"), F_C)
        assertEquals("IGNORED", ac4.getString("state"))
        assertEquals(evidenceBefore, ac4.getInt("evidenceCount"))
        assertEquals(logsBefore, ac4.getInt("captureLogCount"))
        assertFalse(rows(JSONObject(dump("ac4-confirmed-check"))).any { row ->
            row.getString("transactionId") == ids[F_C] && row.getString("state") == "CONFIRMED"
        })

        Espresso.pressBack()
        awaitText("本月支出")
        awaitText("¥77.00")
        awaitDescription("待确认 2 条")
        shoot("ac5-home-after")
        val after = JSONObject(dump("ac5-after"))
        val fa = row(before, F_A).getLong("amountMinor")
        val fb = row(before, F_B).getLong("amountMinor")
        val fc = row(before, F_C).getLong("amountMinor")
        assertEquals(fa + fb, after.getLong("monthExpenseMinor") - beforeExpense)
        assertEquals(beforeIncome, after.getLong("monthIncomeMinor"))
        assertEquals(2, after.getInt("pendingCount"))
        assertTrue(fc > 0L)
        assertEquals("IGNORED", row(after.toString(), F_C).getString("state"))

        val ac6Before = dump("ac6-before")
        val rerun = runBlocking {
            val tool = entry.rerunAutomaticRecognition()
            val parts = mutableListOf<String>()
            for (fixture in listOf(F_A, F_B, F_C)) {
                val result = tool.rerun(ids.getValue(fixture))
                parts += buildString {
                    append('{')
                    append("\"fixtureId\":\"$fixture\"")
                    append(",\"ok\":${result.ok}")
                    append(",\"state\":\"${result.state}\"")
                    append(",\"confirmedByUser\":${result.confirmedByUser}")
                    append(",\"categoryId\":${jsonOrNull(result.categoryId)}")
                    append(",\"note\":${jsonOrNull(result.note)}")
                    append(",\"newState\":${jsonOrNull(result.newState)}")
                    append(",\"refusedAutoConfirm\":${result.refusedAutoConfirm}")
                    append('}')
                }
            }
            parts.joinToString(prefix = "[", postfix = "]")
        }
        File(context.filesDir, "ac6-rerun.json").writeText(rerun)
        val ac6After = dump("ac6-after")
        val aAfter = row(ac6After, F_A, "CONFIRMED")
        val bAfter = row(ac6After, F_B, "CONFIRMED")
        val cAfter = row(ac6After, F_C, "IGNORED")
        assertTrue(aAfter.getBoolean("confirmedByUser"))
        assertTrue(bAfter.getBoolean("confirmedByUser"))
        assertEquals(foodId, bAfter.getString("categoryId"))
        assertEquals(NOTE, bAfter.getString("note"))
        assertEquals(listOf("categoryId", "note"), jsonStrings(bAfter.getJSONArray("fieldEdits")).sorted())
        assertEquals("IGNORED", cAfter.getString("state"))
        assertEquals(0, row(ac6Before, F_A, "CONFIRMED").getJSONArray("fieldEdits").length())
        rows(JSONObject(ac6After)).forEach { row ->
            if (row.getString("state") == "CONFIRMED") {
                assertTrue(row.getBoolean("confirmedByUser"))
                assertTrue(
                    row.getString("fixtureId") == F_A || row.getString("fixtureId") == F_B,
                )
            }
        }
        val newStates = JSONObject("{\"items\":$rerun}").getJSONArray("items")
        for (index in 0 until newStates.length()) {
            assertEquals("PENDING", newStates.getJSONObject(index).getString("newState"))
        }

        val confirmedBeforeNew = rows(JSONObject(ac6After)).count { it.getString("state") == "CONFIRMED" }
        runBlocking {
            val result = entry.accessibilitySnapshotIngest().ingest(
                AccessibilityFixtures.wechatPaySuccess(capturedAt = base - 10 * MINUTE),
                F_NEW,
            )
            check(result.transactionState == TransactionState.PENDING) { result.toProofJson() }
        }
        val ac7 = dump("ac7")
        val ac7Json = JSONObject(ac7)
        assertEquals(BuildConfig.GIT_SHA, ac7Json.getString("gitSha"))
        assertEquals(confirmedBeforeNew, rows(ac7Json).count { it.getString("state") == "CONFIRMED" })
        assertEquals("PENDING", row(ac7, F_NEW).getString("state"))
        assertFalse(row(ac7, F_NEW).getBoolean("confirmedByUser"))
        File(context.filesDir, "git-sha.txt").writeText(BuildConfig.GIT_SHA)

        val pendingAfterNew = JSONObject(ac7).getInt("pendingCount")
        awaitDescription("待确认 $pendingAfterNew 条")
        composeRule.onNodeWithContentDescription("待确认 $pendingAfterNew 条").performClick()
        composeRule.onNodeWithContentDescription("$F_E 改完再记").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("金额输入").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithText("保存并入账").performScrollTo().performClick()
        composeRule.onNodeWithText("金额不能为空").assertIsDisplayed()
        shoot("ac8-empty-amount")

        composeRule.onNodeWithContentDescription("金额输入").performTextReplacement("0")
        composeRule.onNodeWithText("保存并入账").performScrollTo().performClick()
        composeRule.onNodeWithText("金额不能为 0").assertIsDisplayed()
        shoot("ac8-zero-amount")

        composeRule.onNodeWithContentDescription("金额输入").performTextReplacement("abc")
        composeRule.onNodeWithText("保存并入账").performScrollTo().performClick()
        composeRule.onNodeWithText("金额不合法").assertIsDisplayed()
        shoot("ac8-illegal-amount")

        composeRule.onNodeWithContentDescription("金额输入").performTextReplacement("38.50")
        composeRule.onNodeWithContentDescription("时间输入").performScrollTo().performTextReplacement("")
        composeRule.onNodeWithText("保存并入账").performScrollTo().performClick()
        composeRule.onNodeWithText("时间不能为空").assertIsDisplayed()
        shoot("ac8-empty-time")

        val ac8 = row(dump("ac8"), F_E)
        assertEquals("PENDING", ac8.getString("state"))
        assertEquals(0, ac8.getJSONArray("fieldEdits").length())
        assertFalse(ac8.getBoolean("confirmedByUser"))
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private fun awaitDescription(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text).assertIsDisplayed()
    }

    private fun awaitGone(text: String) {
        composeRule.waitUntil(timeoutMillis = 15_000) {
            composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun shoot(name: String) {
        composeRule.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val bitmap = automation.takeScreenshot()
        val file = File(filesDir(), "$name.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        check(file.length() > 0L) { "截图为空：$name" }
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

    private fun row(json: String, fixtureId: String, state: String? = null): JSONObject =
        rows(JSONObject(json)).first { row ->
            row.optString("fixtureId") == fixtureId && (state == null || row.getString("state") == state)
        }

    private fun rows(json: JSONObject): List<JSONObject> {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private fun jsonStrings(array: JSONArray): List<String> =
        List(array.length()) { index -> array.getString(index) }

    private fun jsonOrNull(value: String?): String =
        if (value == null) "null" else "\"${value.replace("\"", "\\\"")}\""

    private companion object {
        const val F_A = "m6-2-wechat-pay-success:F-A"
        const val F_B = "m6-2-wechat-pay-success:F-B"
        const val F_C = "m6-2-wechat-pay-success:F-C"
        const val F_D = "m6-2-wechat-pay-success:F-D"
        const val F_E = "m6-2-wechat-pay-success:F-E"
        const val F_NEW = "m6-2-wechat-pay-success:F-NEW"
        const val NOTE = "改完再记备注"
        const val MINUTE = 60_000L
    }
}
