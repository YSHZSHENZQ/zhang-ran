/**
 * M6-4 取证。截图由主机 `adb exec-out screencap` 抓取：本用例写 `shot-request` 后等待 `shot-done`。
 *
 * 回放 fixture：`m6-3-f-wx`、`m6-3-f-ali`。忽略走 M3a 的待确认忽略。
 */
package com.zhangran.app.capture.accessibility

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
import com.zhangran.app.debug.DebugCaptureHealthReceiver
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.usecase.CaptureLogExport
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.domain.usecase.FieldEditValue
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M64StatusProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(900)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 状态引导日志导出清空恢复与疑似失效() {
        check(java.time.ZoneId.systemDefault().id == "Asia/Shanghai") {
            "设备时区不是 Asia/Shanghai：${java.time.ZoneId.systemDefault()}"
        }
        awaitText("权限引导")
        awaitText("无障碍未授权")
        shot("ac2-onboard-before")
        assertTrue(composeRule.onAllNodesWithText("LSPosed").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("OCR").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("短信").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("悬浮窗").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("无障碍未授权").assertIsDisplayed()
        composeRule.onNodeWithText("前往授权").performClick()
        Thread.sleep(1_500)
        shot("ac2-app-settings")
        systemBack()
        awaitText("无障碍已授权")
        shot("ac2-onboard-refreshed")
        composeRule.onNodeWithText("跳过").performClick()
        awaitText("已跳过电池优化")
        shot("ac2-battery-skipped")
        composeRule.onNodeWithText("完成").performClick()
        awaitText("账然")

        runBlocking { entry().debugLedgerReset().clearCapturedLedger() }
        val replay = runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            listOf(
                AccessibilityFixtures.F_WX to AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_ALI to AccessibilityFixtures.alipayFufu(),
            ).map { (fixtureId, snapshot) ->
                fixtureId to ingest.ingest(snapshot, fixtureId)
            }
        }
        replay.forEach { (fixtureId, result) ->
            check(result.transactionState == TransactionState.PENDING) {
                "$fixtureId 未进入待确认：${result.toProofJson()}"
            }
        }
        val ac8 = JSONObject(dump("ac8-replay"))
        assertEquals(BuildConfig.GIT_SHA, ac8.getString("gitSha"))
        assertEquals(2, ac8.getInt("pendingCount"))
        assertEquals(0L, ac8.getLong("monthExpenseMinor"))
        assertFalse(ac8.toString().contains("\"state\":\"CONFIRMED\""))
        assertFalse(ac8.toString().contains("\"state\":\"DUPLICATE\""))
        writeLogs("ac3-logs")

        openMine()
        composeRule.onNodeWithContentDescription("自动记账，当前已关闭").performClick()
        awaitDescription("自动记账，当前已开启")
        openHome()
        awaitText("AUTO LEDGER · 可用")
        assertTrue(composeRule.onAllNodesWithText("未接入").fetchSemanticsNodes().isEmpty())
        shot("ac1-available-home")
        openMine()
        composeRule.onNodeWithContentDescription("自动记账，当前已开启").assertIsDisplayed()
        composeRule.onNodeWithText("无障碍已授权").assertIsDisplayed()
        shot("ac1-available-mine")
        composeRule.onNodeWithText("自动记账状态").performClick()
        awaitText("总状态 可用")
        awaitText("微信监听 可用")
        awaitText("支付宝监听 可用")
        assertTrue(composeRule.onAllNodesWithText("总开关").fetchSemanticsNodes().isEmpty())
        shot("ac1-available-auto-ledger")

        openMine()
        composeRule.onNodeWithContentDescription("自动记账，当前已开启").performClick()
        awaitDescription("自动记账，当前已关闭")
        openHome()
        awaitText("AUTO LEDGER · 不可用")
        val unavailable = JSONObject(dump("ac1-unavailable"))
        assertEquals(2, unavailable.getInt("pendingCount"))
        shot("ac1-unavailable-home")
        openMine()
        shot("ac1-unavailable-mine")
        composeRule.onNodeWithText("自动记账状态").performClick()
        awaitText("总状态 不可用")
        shot("ac1-unavailable-auto-ledger")

        openLogs()
        awaitText("11月11日 13:05")
        awaitText("11月11日 10:09")
        awaitAny("无障碍 ACCESSIBILITY")
        awaitText("com.tencent.mm")
        awaitText("com.eg.android.AlipayGphone")
        awaitAny("PENDING")
        shot("ac3-logs-list")
        composeRule.onNodeWithContentDescription("按状态筛选 PENDING").performClick()
        awaitText("11月11日 13:05")
        awaitText("11月11日 10:09")
        shot("ac3-filter-pending")
        composeRule.onNodeWithContentDescription("按状态筛选 CONFIRMED").performClick()
        awaitText("没有该状态的日志")
        shot("ac3-filter-empty")
        composeRule.onNodeWithContentDescription("按状态筛选 全部").performClick()

        composeRule.onNodeWithText("导出").performClick()
        val exported = waitForExport()
        File(filesDir(), exported.name).writeBytes(exported.readBytes())
        val exportText = exported.readText()
        assertTrue(exported.name.startsWith("zhangran-logs-"))
        assertTrue(exportText.contains(BuildConfig.GIT_SHA))
        assertTrue(exportText.contains("m6-3-f-wx"))
        assertTrue(exportText.contains("m6-3-f-ali"))
        assertTrue(exportText.contains("2026-11-11 13:05"))
        assertTrue(exportText.contains("2026-11-11 10:09"))
        val findings = CaptureLogExport.sensitiveFindings(exportText)
        File(filesDir(), "ac4-sensitive-scan.txt").writeText(
            if (findings.isEmpty()) "no plaintext sensitive fields" else findings.joinToString("\n"),
        )
        assertTrue(findings.isEmpty())
        shot("ac4-share-sheet")
        systemBack()

        val wxId = transactionId(ac8, AccessibilityFixtures.F_WX)
        runBlocking {
            entry().updateTransactionFieldUseCase().invoke(
                wxId,
                EditableField.NOTE,
                FieldEditValue.Note("m6-4-field-edit"),
            )
        }
        val beforeClear = JSONObject(dump("ac5-before-clear"))
        assertTrue(row(beforeClear, AccessibilityFixtures.F_WX).getJSONArray("fieldEdits").toString().contains("note"))

        openPending()
        composeRule.onNodeWithContentDescription("m6-3-f-ali 忽略").performScrollTo().performClick()
        composeRule.waitForIdle()
        val ignored = JSONObject(dump("ac6-ignored"))
        assertEquals("IGNORED", row(ignored, AccessibilityFixtures.F_ALI).getString("state"))
        assertEquals(1, ignored.getInt("pendingCount"))
        val expenseWhileIgnored = ignored.getLong("monthExpenseMinor")
        openLogs()
        composeRule.onNodeWithContentDescription("按状态筛选 IGNORED").performClick()
        awaitAny("IGNORED")
        shot("ac6-log-ignored")
        composeRule.onNodeWithText("恢复").performClick()
        composeRule.waitForIdle()
        val restored = JSONObject(dump("ac6-restored"))
        assertEquals("PENDING", row(restored, AccessibilityFixtures.F_ALI).getString("state"))
        assertEquals(2, restored.getInt("pendingCount"))
        assertEquals(expenseWhileIgnored, restored.getLong("monthExpenseMinor"))
        assertEquals(0L, restored.getLong("monthIncomeMinor"))
        openPending()
        composeRule.onNodeWithText("m6-3-f-ali").assertIsDisplayed()
        composeRule.onNodeWithText("富富").assertIsDisplayed()
        shot("ac6-pending-restored")
        openHome()
        shot("ac6-home-stats")

        openMine()
        composeRule.onNodeWithContentDescription("自动记账，当前已关闭").performClick()
        awaitDescription("自动记账，当前已开启")
        openHome()
        awaitText("AUTO LEDGER · 可用")
        val broadcast = shell(
            "am broadcast -a ${DebugCaptureHealthReceiver.ACTION} " +
                "--el ${DebugCaptureHealthReceiver.EXTRA_AGE_MILLIS} 90000000 " +
                "-n com.zhangran.app/.debug.DebugCaptureHealthReceiver",
        )
        File(filesDir(), "ac7-broadcast.txt").writeText(broadcast)
        assertTrue(broadcast.contains("result=0"))
        openHome()
        awaitText("疑似失效")
        val stale = JSONObject(dump("ac7-stale"))
        assertEquals(2, stale.getInt("pendingCount"))
        shot("ac7-stale-home")
        openMine()
        composeRule.onNodeWithText("自动记账状态").performClick()
        awaitText("总状态 疑似失效")
        awaitText("已超过 24 小时没有捕获")
        shot("ac7-stale-auto-ledger")
        openMine()
        composeRule.onNodeWithContentDescription("自动记账，当前已开启").performClick()
        awaitDescription("自动记账，当前已关闭")
        openHome()
        awaitText("AUTO LEDGER · 不可用")
        assertTrue(composeRule.onAllNodesWithText("疑似失效").fetchSemanticsNodes().isEmpty())
        shot("ac7-switch-off-home")

        composeRule.onNodeWithContentDescription("记一笔，标签页，未选中").performClick()
        awaitDescription("保存")
        composeRule.onNodeWithContentDescription("1").performClick()
        composeRule.onNodeWithContentDescription("保存").performClick()
        awaitText("账然")
        awaitText("¥1.00")
        val manual = JSONObject(dump("ac1-manual"))
        assertEquals(2, manual.getInt("pendingCount"))
        assertTrue(manual.toString().contains("\"source\":\"MANUAL\""))
        assertTrue(manual.toString().contains("\"state\":\"CONFIRMED\""))
        val manualRow = rows(manual).single { it.getString("source") == "MANUAL" }
        assertEquals("CONFIRMED", manualRow.getString("state"))
        rows(manual).filter { it.getString("source") == "ACCESSIBILITY" }.forEach {
            assertEquals("PENDING", it.getString("state"))
        }
        shot("ac1-manual-home")

        openLogs()
        composeRule.onNodeWithText("清空日志").performClick()
        awaitText("只清日志和证据，不影响已记的账")
        shot("ac5-confirm")
        composeRule.onNodeWithText("清空").performClick()
        awaitText("还没有捕获日志")
        shot("ac5-empty")
        val afterClear = JSONObject(dump("ac5-after-clear"))
        assertEquals(manual.getInt("pendingCount"), afterClear.getInt("pendingCount"))
        assertEquals(manual.getLong("monthExpenseMinor"), afterClear.getLong("monthExpenseMinor"))
        assertEquals(rows(manual).size, rows(afterClear).size)
        rows(manual).forEach { before ->
            val after = rows(afterClear).single { it.getString("transactionId") == before.getString("transactionId") }
            assertEquals(0, after.getInt("evidenceCount"))
            assertEquals(0, after.getInt("captureLogCount"))
            assertEquals(before.getString("state"), after.getString("state"))
            assertEquals(before.getLong("amountMinor"), after.getLong("amountMinor"))
            assertEquals(before.getString("note"), after.getString("note"))
            assertEquals(before.getBoolean("confirmedByUser"), after.getBoolean("confirmedByUser"))
            assertEquals(before.getJSONArray("fieldEdits").toString(), after.getJSONArray("fieldEdits").toString())
        }
        val wxAfter = rows(afterClear).single { it.getString("transactionId") == wxId }
        assertTrue(wxAfter.getJSONArray("fieldEdits").toString().contains("note"))
        assertEquals("m6-4-field-edit", wxAfter.getString("note"))
        writeLogs("ac5-logs-after")
        File(filesDir(), "git-sha.txt").writeText(BuildConfig.GIT_SHA)
    }

    private fun openHome() {
        backToTabs()
        val unselected = composeRule.onAllNodesWithContentDescription("首页，标签页，未选中")
        if (unselected.fetchSemanticsNodes().isNotEmpty()) {
            unselected[0].performClick()
        }
        awaitText("账然")
    }

    private fun openMine() {
        backToTabs()
        val unselected = composeRule.onAllNodesWithContentDescription("我的，标签页，未选中")
        if (unselected.fetchSemanticsNodes().isNotEmpty()) {
            unselected[0].performClick()
        }
        composeRule.waitUntil(20_000) {
            composeRule.onAllNodesWithText("权限状态").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("权限状态").performScrollTo().assertIsDisplayed()
    }

    private fun systemBack() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("input keyevent 4")
            .close()
        Thread.sleep(800)
        composeRule.waitForIdle()
    }

    private fun backToTabs() {
        composeRule.waitForIdle()
        val onTabs = composeRule.onAllNodesWithContentDescription("首页，标签页，已选中")
            .fetchSemanticsNodes().isNotEmpty() ||
            composeRule.onAllNodesWithContentDescription("首页，标签页，未选中")
                .fetchSemanticsNodes().isNotEmpty()
        if (!onTabs) {
            Espresso.pressBack()
            composeRule.waitForIdle()
        }
    }

    private fun openLogs() {
        openMine()
        composeRule.onNodeWithText("捕获日志").performScrollTo().performClick()
        awaitText("捕获日志")
    }

    private fun openPending() {
        openHome()
        composeRule.onNodeWithContentDescription("待确认", substring = true).performClick()
        awaitText("待确认")
    }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText(text, substring = true).assertIsDisplayed()
    }

    private fun awaitAny(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitDescription(text: String) {
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shot(name: String) {
        composeRule.waitForIdle()
        File(filesDir(), "shot-done").delete()
        File(filesDir(), "shot-request").writeText(name)
        val done = File(filesDir(), "shot-done")
        val start = System.currentTimeMillis()
        while (!done.exists() && System.currentTimeMillis() - start < 60_000) {
            Thread.sleep(200)
        }
        check(done.exists()) { "截图主机没有回应 $name" }
    }

    private fun entry(): M3aDebugEntryPoint {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return EntryPointAccessors.fromApplication(context.applicationContext, M3aDebugEntryPoint::class.java)
    }

    private fun dump(name: String): String {
        val json = runBlocking { entry().ledgerProofQuery().json(BuildConfig.GIT_SHA) }
        File(filesDir(), "$name.json").writeText(json)
        return json
    }

    private fun writeLogs(name: String) {
        val logs = runBlocking { entry().captureLogRepository().listAll() }
        val zone = java.time.ZoneId.systemDefault()
        val entries = logs.sortedByDescending { it.capturedAt }.map { CaptureLogExport.entry(it, zone) }
        val json = CaptureLogExport.json(BuildConfig.GIT_SHA, entries)
        File(filesDir(), "$name.json").writeText(json)
    }

    private fun waitForExport(): File {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "log-exports")
        val start = System.currentTimeMillis()
        var file: File? = null
        while (file == null && System.currentTimeMillis() - start < 20_000) {
            file = dir.listFiles()?.filter { it.name.startsWith("zhangran-logs-") }?.maxByOrNull { it.lastModified() }
            if (file == null) Thread.sleep(200)
        }
        return checkNotNull(file) { "没有导出文件" }
    }

    private fun shell(command: String): String {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val text = FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }
        pipe.close()
        return text
    }

    private fun filesDir(): File = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    private fun rows(json: JSONObject): List<JSONObject> {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private fun row(json: JSONObject, fixtureId: String): JSONObject =
        rows(json).single { it.optString("fixtureId") == fixtureId }

    private fun transactionId(json: JSONObject, fixtureId: String): String =
        row(json, fixtureId).getString("transactionId")
}
