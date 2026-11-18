/**
 * FX-1 取证。截图由主机 `adb exec-out screencap` 抓取：本用例写 `shot-request` 后等待 `shot-done`。
 *
 * 回放 `fx-1-f-ali-yuebao`、拆分金额变体、看错窗口，以及既有的微信 / 支付宝结果页。
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
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Fx1ProofTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(900)

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun 看错窗口余额宝金额与疑似失效() {
        check(java.time.ZoneId.systemDefault().id == "Asia/Shanghai") {
            "设备时区不是 Asia/Shanghai：${java.time.ZoneId.systemDefault()}"
        }
        enableAccessibility()
        runBlocking { entry().autoLedgerStatusSource().refreshPermissions() }
        passOnboard()
        runBlocking { entry().debugLedgerReset().clearCapturedLedger() }
        runBlocking { entry().rejectionRateLimiter().reset() }

        val base = 1_700_000_000_000L
        val healthBefore = writeHealth("ac2-health-before")
        val ledgerBefore = JSONObject(dump("ac2-ledger-before"))
        assertEquals(0, ledgerBefore.getInt("pendingCount"))
        runBlocking {
            AccessibilityRejectReason.entries.forEachIndexed { index, reason ->
                entry().accessibilityRejectionLog().record(
                    reason = reason,
                    eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
                    rootPackage = if (reason == AccessibilityRejectReason.WRONG_WINDOW) {
                        NonPaymentWindows.OWN_PACKAGE
                    } else {
                        null
                    },
                    fixtureId = "fx-1-$reason",
                    capturedAt = base + index,
                )
            }
        }
        val exportBefore = writeLogs("ac2-export-before")
        runBlocking {
            AccessibilityRejectReason.entries.forEach { reason ->
                val again = entry().accessibilityRejectionLog().record(
                    reason = reason,
                    eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
                    capturedAt = base + 1_000L,
                )
                check(again == null) { "$reason 在 60 秒窗口内又写了一条" }
            }
        }
        val exportAfter = writeLogs("ac2-export-after")
        assertEquals(exportBefore, exportAfter)
        AccessibilityRejectReason.entries.forEach { reason ->
            assertTrue(exportAfter.contains(reason.name))
            assertEquals(1, Regex("\"reason\":\"${reason.name}\"").findAll(exportAfter).count())
        }
        assertTrue(CaptureLogExport.sensitiveFindings(exportAfter).isEmpty())
        val healthAfterReject = writeHealth("ac2-health")
        assertTrue(healthBefore.contains("\"lastCaptureAtMillis\":null"))
        assertTrue(healthAfterReject.contains("\"lastCaptureAtMillis\":null"))
        assertTrue(healthAfterReject.contains("\"watchStartedAtMillis\":null"))
        assertTrue(healthAfterReject.contains("\"rejectedSamplesSinceCapture\":true"))
        val ledgerAfterReject = JSONObject(dump("ac2-ledger-after"))
        assertEquals(0, ledgerAfterReject.getInt("pendingCount"))
        assertEquals(0, ledgerAfterReject.getJSONArray("rows").length())

        openLogs()
        composeRule.onNodeWithContentDescription("按状态筛选 IGNORED_INVALID").performClick()
        composeRule.onNodeWithText("WRONG_WINDOW").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("NOT_PAYMENT_PAGE").performScrollTo().assertIsDisplayed()
        shot("ac2-logs")

        val now = System.currentTimeMillis()
        val yuebao = AccessibilityFixtures.alipayYuebao(capturedAt = now)
        val split = AccessibilityFixtures.alipayYuebaoSplit(capturedAt = now + 1_000L)
        val yuebaoIngest = runBlocking {
            entry().accessibilitySnapshotIngest().ingest(yuebao, AccessibilityFixtures.F_YUEBAO)
        }
        val splitIngest = runBlocking {
            entry().accessibilitySnapshotIngest().ingest(split, AccessibilityFixtures.F_YUEBAO_SPLIT)
        }
        writeParsed("ac1-yuebao", AccessibilityFixtures.F_YUEBAO, yuebao, yuebaoIngest.toProofJson())
        writeParsed("ac1-yuebao-split", AccessibilityFixtures.F_YUEBAO_SPLIT, split, splitIngest.toProofJson())
        check(yuebaoIngest.transactionState == TransactionState.PENDING)
        check(splitIngest.transactionState == TransactionState.PENDING)
        val yuebaoLedger = JSONObject(dump("ac3-yuebao-ledger"))
        assertEquals(2, yuebaoLedger.getInt("pendingCount"))
        listOf(AccessibilityFixtures.F_YUEBAO, AccessibilityFixtures.F_YUEBAO_SPLIT).forEach { fixtureId ->
            val row = row(yuebaoLedger, fixtureId)
            assertEquals("PENDING", row.getString("state"))
            assertEquals("ACCESSIBILITY", row.getString("source"))
            assertEquals(2_510L, row.getLong("amountMinor"))
            assertEquals("EXPENSE", row.getString("type"))
            assertTrue(row.isNull("merchant"))
            assertFalse(row.getBoolean("confirmedByUser"))
        }
        openPending()
        composeRule.onNodeWithText("¥25.10").assertIsDisplayed()
        composeRule.onNodeWithText("未知").assertIsDisplayed()
        composeRule.onNodeWithText(AccessibilityFixtures.F_YUEBAO).assertIsDisplayed()
        shot("ac3-yuebao-pending")

        val beforeWrong = JSONObject(dump("ac3-before-wrong"))
        runBlocking { entry().rejectionRateLimiter().reset() }
        val wrong = AccessibilityFixtures.wrongWindowHome()
        val wrongIngest = runBlocking {
            entry().accessibilitySnapshotIngest().ingest(wrong, AccessibilityFixtures.F_WRONG_WINDOW)
        }
        writeParsed("ac1-wrong-window", AccessibilityFixtures.F_WRONG_WINDOW, wrong, wrongIngest.toProofJson())
        assertNull(AccessibilitySnapshotParser.parse(wrong))
        val afterWrong = JSONObject(dump("ac3-after-wrong"))
        assertEquals(beforeWrong.getInt("pendingCount"), afterWrong.getInt("pendingCount"))
        assertEquals(beforeWrong.getJSONArray("rows").length(), afterWrong.getJSONArray("rows").length())
        val wrongLogs = writeLogs("ac3-wrong-window-logs")
        assertTrue(wrongLogs.contains("WRONG_WINDOW"))
        assertTrue(wrongLogs.contains(AccessibilityFixtures.F_WRONG_WINDOW))
        assertFalse(wrongLogs.contains("我的"))
        assertFalse(wrongLogs.contains("还没有记录"))

        val regression = runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            listOf(
                AccessibilityFixtures.F_WX to AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_ALI to AccessibilityFixtures.alipayFufu(),
                AccessibilityFixtures.WECHAT_PAY_SUCCESS to AccessibilityFixtures.wechatPaySuccess(),
            ).map { (fixtureId, snapshot) ->
                fixtureId to ingest.ingest(snapshot, fixtureId)
            }
        }
        regression.forEach { (fixtureId, result) ->
            check(result.transactionState == TransactionState.PENDING) {
                "$fixtureId 未进入待确认：${result.toProofJson()}"
            }
        }
        val regressionLedger = JSONObject(dump("ac3-regression"))
        assertEquals(5, regressionLedger.getInt("pendingCount"))
        assertField(regressionLedger, AccessibilityFixtures.F_WX, 1_000L, "杭州深度求索")
        assertField(regressionLedger, AccessibilityFixtures.F_ALI, 12_999L, "富富")
        assertField(regressionLedger, AccessibilityFixtures.WECHAT_PAY_SUCCESS, 3_850L, "星巴克咖啡")
        assertFalse(regressionLedger.toString().contains("\"state\":\"CONFIRMED\""))
        assertFalse(regressionLedger.toString().contains("\"state\":\"DUPLICATE\""))

        runBlocking { entry().rejectionRateLimiter().reset() }
        runBlocking {
            entry().accessibilityRejectionLog().record(
                reason = AccessibilityRejectReason.NOT_PAYMENT_PAGE,
                eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
                capturedAt = System.currentTimeMillis(),
            )
        }
        runBlocking { entry().setAutoLedgerEnabledUseCase().invoke(true) }
        runBlocking { entry().autoLedgerStatusSource().refreshPermissions() }
        val broadcast = shell(
            "am broadcast -a ${DebugCaptureHealthReceiver.ACTION} " +
                "--el ${DebugCaptureHealthReceiver.EXTRA_AGE_MILLIS} 90000000 " +
                "--el ${DebugCaptureHealthReceiver.EXTRA_WATCH_AGE_MILLIS} 90000000 " +
                "-n com.zhangran.app/.debug.DebugCaptureHealthReceiver",
        )
        File(filesDir(), "ac4-broadcast.txt").writeText(broadcast)
        assertTrue(broadcast.contains("result=0"))
        runBlocking { entry().autoLedgerStatusSource().refreshPermissions() }
        openHome()
        awaitText("AUTO LEDGER · 疑似失效")
        shot("ac4-stale-home")
        openMine()
        composeRule.onNodeWithText("自动记账状态").performScrollTo().performClick()
        awaitText("总状态 疑似失效")
        awaitText("已超过 24 小时没有捕获")
        shot("ac4-stale-auto-ledger")
        val staleLedger = JSONObject(dump("ac4-ledger"))
        assertEquals(5, staleLedger.getInt("pendingCount"))
        assertFalse(staleLedger.toString().contains("\"state\":\"CONFIRMED\""))
        assertFalse(staleLedger.toString().contains("\"state\":\"DUPLICATE\""))
        val staleExport = writeLogs("ac4-export")
        val flips = Regex(
            "SUSPECTED_STALE lastCaptureAt=\\d+ watchStartedAt=\\d+ rejectedSamples=true",
        ).findAll(staleExport).count()
        assertEquals(1, flips)
        File(filesDir(), "git-sha.txt").writeText(BuildConfig.GIT_SHA)
    }

    private fun passOnboard() {
        composeRule.waitForIdle()
        val onHome = composeRule.onAllNodesWithText("账然").fetchSemanticsNodes().isNotEmpty() &&
            composeRule.onAllNodesWithContentDescription("首页，标签页，已选中").fetchSemanticsNodes().isNotEmpty()
        if (onHome) return
        awaitText("权限引导")
        runBlocking { entry().autoLedgerStatusSource().refreshPermissions() }
        awaitText("无障碍已授权")
        composeRule.onNodeWithText("跳过").performClick()
        awaitText("已跳过电池优化")
        composeRule.onNodeWithText("完成").performClick()
        awaitText("账然")
    }

    private fun enableAccessibility() {
        shell("settings put secure accessibility_enabled 1")
        shell(
            "settings put secure enabled_accessibility_services " +
                "com.zhangran.app/com.zhangran.app.capture.accessibility.AccessibilityCaptureService",
        )
    }

    private fun writeParsed(
        name: String,
        fixtureId: String,
        snapshot: AccessibilityPageSnapshot,
        ingestJson: String,
    ) {
        val parsed = AccessibilitySnapshotParser.parse(snapshot)
        val body = buildString {
            append('{')
            append("\"fixtureId\":${quote(fixtureId)}")
            append(",\"amountMinor\":${parsed?.amountMinor ?: "null"}")
            append(",\"type\":${quote(parsed?.direction?.name)}")
            append(",\"merchantRaw\":${quote(parsed?.merchantRaw)}")
            append(",\"forward\":${AccessibilitySnapshotParser.shouldForward(snapshot)}")
            append(",\"rootPackageName\":${quote(snapshot.rootPackageName)}")
            append(",\"eventPackage\":${quote(snapshot.packageName)}")
            append(",\"ingest\":")
            append(ingestJson)
            append('}')
        }
        File(filesDir(), "$name.json").writeText(body)
    }

    private fun assertField(json: JSONObject, fixtureId: String, amountMinor: Long, merchant: String) {
        val row = row(json, fixtureId)
        assertEquals("PENDING", row.getString("state"))
        assertEquals("ACCESSIBILITY", row.getString("source"))
        assertEquals(amountMinor, row.getLong("amountMinor"))
        assertEquals("EXPENSE", row.getString("type"))
        assertEquals(merchant, row.getString("merchant"))
        assertFalse(row.getBoolean("confirmedByUser"))
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

    private fun writeLogs(name: String): String {
        val logs = runBlocking { entry().captureLogRepository().listAll() }
        val zone = java.time.ZoneId.systemDefault()
        val entries = logs.sortedByDescending { it.capturedAt }.map { CaptureLogExport.entry(it, zone) }
        val json = CaptureLogExport.json(BuildConfig.GIT_SHA, entries)
        File(filesDir(), "$name.json").writeText(json)
        return json
    }

    private fun writeHealth(name: String): String {
        val health = runBlocking { entry().captureHealthStore().state.first() }
        val json = buildString {
            append('{')
            append("\"lastCaptureAtMillis\":${health.lastCaptureAtMillis ?: "null"}")
            append(",\"watchStartedAtMillis\":${health.watchStartedAtMillis ?: "null"}")
            append(",\"rejectedSamplesSinceCapture\":${health.rejectedSamplesSinceCapture}")
            append('}')
        }
        File(filesDir(), "$name.json").writeText(json)
        return json
    }

    private fun shell(command: String): String {
        val pipe = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        val text = FileInputStream(pipe.fileDescriptor).bufferedReader().use { it.readText() }
        pipe.close()
        return text
    }

    private fun filesDir(): File = InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    private fun row(json: JSONObject, fixtureId: String): JSONObject {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
            .single { it.optString("fixtureId") == fixtureId }
    }

    private fun quote(value: String?): String =
        if (value == null) "null" else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
