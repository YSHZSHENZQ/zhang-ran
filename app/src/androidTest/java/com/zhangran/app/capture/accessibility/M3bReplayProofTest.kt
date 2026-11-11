/**
 * M3b 回放取证。截图来自设备 `screencap -p`（帧缓冲），不是 Compose Preview。
 *
 * 时区规则包在 Compose 规则外面，Activity 启动前把进程默认时区设成 Asia/Shanghai。
 */
package com.zhangran.app.capture.accessibility

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.BuildConfig
import com.zhangran.app.MainActivity
import com.zhangran.app.dismissColdStartOnboard
import com.zhangran.app.domain.model.DuplicateVerdict
import com.zhangran.app.domain.model.TransactionState
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.TimeZone
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.rules.Timeout
import org.junit.runner.Description
import org.junit.runners.model.Statement

class M3bReplayProofTest {

    private val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity> =
        createAndroidComposeRule()

    @get:Rule
    val rules: TestRule = RuleChain
        .outerRule(Timeout.seconds(900))
        .around(ShanghaiZoneRule())
        .around(composeRule)

    @Test
    fun 回放去重与时区() {
        dismissColdStartOnboard(composeRule)
        ac1()
        ac2()
        ac3()
        ac4Through8()
        File(filesDir(), "git-sha.txt").writeText(BuildConfig.GIT_SHA)
    }

    private fun ac1() {
        reset()
        val entry = entry()
        val snapshot = AccessibilityFixtures.wechatDepthSeek()
        val results = runBlocking {
            val ingest = entry.accessibilitySnapshotIngest()
            listOf(
                ingest.ingest(
                    snapshot,
                    AccessibilityFixtures.AC1_WX,
                    notificationId = AccessibilityFixtures.AC1_NOTIFICATION_ID,
                    postTime = AccessibilityFixtures.AC1_POST_TIME,
                ),
                ingest.ingest(
                    snapshot,
                    AccessibilityFixtures.AC1_WX,
                    notificationId = AccessibilityFixtures.AC1_NOTIFICATION_ID,
                    postTime = AccessibilityFixtures.AC1_POST_TIME,
                ),
            )
        }
        assertEquals(results[0].transactionId, results[1].transactionId)
        assertEquals(results[0].captureId, results[1].captureId)
        val ledger = JSONObject(dump("ac1-ledger"))
        assertEquals(1, ledger.getJSONArray("rows").length())
        assertEquals("PENDING", ledger.getJSONArray("rows").getJSONObject(0).getString("state"))
        assertFalse(ledger.toString().contains("CONFIRMED"))
        assertFalse(ledger.toString().contains("\"state\":\"DUPLICATE\""))
        val counts = counts()
        assertEquals(1, counts.logs)
        assertEquals(1, counts.transactions)
        assertEquals(1, counts.evidence)
        writeJson(
            "ac1",
            mapOf(
                "gitSha" to BuildConfig.GIT_SHA,
                "fixtureId" to AccessibilityFixtures.AC1_WX,
                "notificationId" to AccessibilityFixtures.AC1_NOTIFICATION_ID,
                "postTime" to AccessibilityFixtures.AC1_POST_TIME,
                "captureLogCount" to counts.logs,
                "transactionCount" to counts.transactions,
                "evidenceCount" to counts.evidence,
                "duplicateDecisionCount" to counts.decisions,
                "state" to "PENDING",
            ),
        )
    }

    private fun ac2() {
        reset()
        val entry = entry()
        runBlocking {
            val ingest = entry.accessibilitySnapshotIngest()
            val first = ingest.ingest(
                AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_WX,
            )
            val second = ingest.ingest(
                AccessibilityFixtures.wechatDepthSeek(),
                AccessibilityFixtures.F_WX,
            )
            check(first.transactionState == TransactionState.PENDING)
            check(second.transactionState == TransactionState.PENDING)
            check(com.zhangran.app.domain.model.PendingReason.POSSIBLE_DUPLICATE !in first.reasons)
            check(com.zhangran.app.domain.model.PendingReason.POSSIBLE_DUPLICATE in second.reasons)
        }
        composeRule.waitForIdle()
        awaitDescription("待确认 2 条")
        awaitText("本月支出")
        awaitDescription("本月支出 0.00 元，本月收入 0.00 元，本月结余 0.00 元")
        scrollToText("杭州深度求索")
        hideIme()
        shoot("ac2-home")
        openPending()
        scrollToDescription("m6-3-f-wx 查看重复")
        awaitText("13:05")
        assertAbsent("05:05")
        hideIme()
        shoot("ac2-pending")
        val ledger = JSONObject(dump("ac2"))
        assertEquals(0L, ledger.getLong("monthExpenseMinor"))
        assertEquals(2, ledger.getJSONArray("rows").length())
        val rows = rows(ledger)
        assertEquals(1, rows.count { it.optString("reasons").contains("POSSIBLE_DUPLICATE") })
        assertTrue(rows.all { it.getString("state") == "PENDING" })
        assertFalse(ledger.toString().contains("CONFIRMED"))
        assertFalse(ledger.toString().contains("\"state\":\"DUPLICATE\""))
        back()
    }

    private fun ac3() {
        ac3a()
        ac3b()
        ac3c()
    }

    private fun ac3a() {
        reset()
        runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
            ingest.ingest(
                AccessibilityFixtures.wechatDepthSeek(
                    AccessibilityFixtures.WX_CAPTURED_AT + AccessibilityFixtures.AC3A_DELTA_MILLIS,
                ),
                AccessibilityFixtures.AC3A_WX,
            )
        }
        val ledger = JSONObject(dump("ac3a"))
        assertNoPossibleDuplicate(ledger)
        assertEquals(2, ledger.getJSONArray("rows").length())
    }

    private fun ac3b() {
        reset()
        runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
            ingest.ingest(
                AccessibilityFixtures.alipayFufuTenYuan(),
                AccessibilityFixtures.AC3B_FUFU,
            )
        }
        val ledger = JSONObject(dump("ac3b"))
        assertNoPossibleDuplicate(ledger)
        val merchants = rows(ledger).map { it.getString("merchant") }.toSet()
        assertEquals(setOf("杭州深度求索", "富富"), merchants)
        assertTrue(rows(ledger).all { it.getLong("amountMinor") == 1_000L })
    }

    private fun ac3c() {
        reset()
        runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
            ingest.ingest(AccessibilityFixtures.wechatRefund(), AccessibilityFixtures.AC3C_REFUND)
        }
        val ledger = JSONObject(dump("ac3c"))
        assertNoPossibleDuplicate(ledger)
        val types = rows(ledger).map { it.getString("type") }.toSet()
        assertEquals(setOf("EXPENSE", "REFUND"), types)
    }

    private fun ac4Through8() {
        reset()
        runBlocking {
            val ingest = entry().accessibilitySnapshotIngest()
            ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
            ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
            ingest.ingest(AccessibilityFixtures.alipayFufu(), AccessibilityFixtures.F_ALI)
            ingest.ingest(AccessibilityFixtures.alipayFufu(), AccessibilityFixtures.AC6_ALI)
        }
        composeRule.waitForIdle()
        awaitDescription("待确认 4 条")
        awaitDescription("本月支出 0.00 元，本月收入 0.00 元，本月结余 0.00 元")
        scrollToText("本月支出")
        scrollToText("富富")
        awaitText("13:05")
        awaitText("10:09")
        assertAbsent("05:05")
        assertAbsent("02:09")
        hideIme()
        shoot("ac7-home")
        val beforeTaps = JSONObject(dump("ac8-before-taps"))
        assertTrue(rows(beforeTaps).all { it.getString("state") == "PENDING" })
        assertFalse(beforeTaps.toString().contains("\"state\":\"CONFIRMED\""))
        assertFalse(beforeTaps.toString().contains("\"state\":\"DUPLICATE\""))
        assertEquals(0, counts().decisions)

        openPending()
        scrollToDescription("m6-3-f-wx 查看重复")
        awaitText("13:05")
        scrollToDescription("m6-3-f-ali 改完再记")
        awaitText("10:09")
        assertAbsent("05:05")
        assertAbsent("02:09")
        hideIme()
        shoot("ac7-pending")
        shoot("ac4-pending")

        scrollToDescription("m6-3-f-wx 改完再记")
        composeRule.onNodeWithContentDescription("m6-3-f-wx 改完再记").performClick()
        awaitText("2026-11-11 13:05:22")
        assertAbsent("05:05")
        hideIme()
        scrollToText("2026-11-11 13:05:22")
        shoot("ac7-edit")
        back()
        awaitText("待确认记录")

        val beforeSame = counts()
        scrollToDescription("m6-3-f-wx 查看重复")
        composeRule.onNodeWithContentDescription("m6-3-f-wx 查看重复").performClick()
        awaitText("疑似重复")
        awaitText("新记录")
        awaitText("原记录")
        awaitText("同一笔")
        awaitText("两笔")
        awaitText("杭州深度求索")
        awaitText("支出")
        awaitText("无障碍")
        awaitText("2026-11-11 13:05")
        hideIme()
        scrollToText("同一笔")
        shoot("ac4-review")
        dump("ac5-before")
        composeRule.onNodeWithContentDescription("同一笔").performClick()
        awaitText("待确认记录")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithContentDescription("m6-3-f-wx 查看重复")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        hideIme()
        shoot("ac5-pending")
        val afterSame = JSONObject(dump("ac5-after"))
        val afterCounts = counts()
        assertEquals(1, afterCounts.decisions)
        assertEquals(beforeSame.logs, afterCounts.logs)
        assertEquals(beforeSame.evidence, afterCounts.evidence)
        assertEquals(beforeSame.transactions, afterCounts.transactions)
        val duplicate = rows(afterSame).single { it.getString("state") == "DUPLICATE" }
        assertEquals(AccessibilityFixtures.F_WX, duplicate.getString("fixtureId"))
        assertEquals(1, decisions().count { it.verdict == DuplicateVerdict.SAME_TRANSACTION })
        assertEquals(0L, afterSame.getLong("monthExpenseMinor"))
        writeJson(
            "ac5-counts",
            mapOf(
                "gitSha" to BuildConfig.GIT_SHA,
                "beforeLogs" to beforeSame.logs,
                "afterLogs" to afterCounts.logs,
                "beforeEvidence" to beforeSame.evidence,
                "afterEvidence" to afterCounts.evidence,
                "duplicateDecisions" to afterCounts.decisions,
                "sameVerdict" to 1,
                "monthExpenseMinor" to afterSame.getLong("monthExpenseMinor"),
                "pendingCount" to afterSame.getInt("pendingCount"),
            ),
        )
        back()
        awaitDescription("待确认 3 条")
        hideIme()
        shoot("ac5-home")

        openPending()
        scrollToDescription("m3b-ac6-ali 查看重复")
        composeRule.onNodeWithContentDescription("m3b-ac6-ali 查看重复").performClick()
        awaitText("同一笔")
        composeRule.onNodeWithContentDescription("两笔").performClick()
        awaitText("待确认记录")
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithContentDescription("m3b-ac6-ali 确认")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        scrollToDescription("m3b-ac6-ali 确认")
        awaitText("改完再记")
        hideIme()
        shoot("ac6-pending-ordinary")
        val afterDifferent = JSONObject(dump("ac6-after-different"))
        val ali = rows(afterDifferent).single { it.getString("fixtureId") == AccessibilityFixtures.AC6_ALI }
        assertEquals("PENDING", ali.getString("state"))
        assertFalse(ali.getString("reasons").contains("POSSIBLE_DUPLICATE"))
        assertEquals(2, counts().decisions)
        composeRule.onNodeWithContentDescription("m3b-ac6-ali 确认").performClick()
        composeRule.waitUntil(timeoutMillis = 30_000) {
            composeRule.onAllNodesWithContentDescription("m3b-ac6-ali 确认")
                .fetchSemanticsNodes()
                .isEmpty()
        }
        val confirmed = JSONObject(dump("ac6-after-confirm"))
        val booked = rows(confirmed).single { it.getString("fixtureId") == AccessibilityFixtures.AC6_ALI }
        assertEquals("CONFIRMED", booked.getString("state"))
        assertTrue(booked.getBoolean("confirmedByUser"))
        assertEquals(12_999L, booked.getLong("amountMinor"))
        assertEquals(12_999L, confirmed.getLong("monthExpenseMinor"))
        back()
        awaitDescription("本月支出 129.99 元，本月收入 0.00 元，本月结余 129.99 元")
        hideIme()
        shoot("ac6-home")
        val finalCounts = counts()
        writeJson(
            "ac8",
            mapOf(
                "gitSha" to BuildConfig.GIT_SHA,
                "confirmedOnlyAfterTap" to true,
                "duplicateOnlyAfterSame" to true,
                "duplicateStateCount" to rows(confirmed).count { it.getString("state") == "DUPLICATE" },
                "confirmedStateCount" to rows(confirmed).count { it.getString("state") == "CONFIRMED" },
                "sameVerdict" to finalCounts.same,
                "differentVerdict" to finalCounts.different,
                "monthExpenseMinor" to confirmed.getLong("monthExpenseMinor"),
            ),
        )
    }

    private fun assertNoPossibleDuplicate(ledger: JSONObject) {
        assertTrue(rows(ledger).none { it.optString("reasons").contains("POSSIBLE_DUPLICATE") })
        assertTrue(rows(ledger).all { it.getString("state") == "PENDING" })
        assertFalse(ledger.toString().contains("CONFIRMED"))
        assertFalse(ledger.toString().contains("\"state\":\"DUPLICATE\""))
    }

    private fun reset() {
        runBlocking { entry().debugLedgerReset().clearCapturedLedger() }
        composeRule.waitForIdle()
    }

    private fun openPending() {
        val descriptions = listOf("待确认 4 条", "待确认 3 条", "待确认 2 条", "待确认 1 条")
        val match = descriptions.firstOrNull {
            composeRule.onAllNodesWithContentDescription(it).fetchSemanticsNodes().isNotEmpty()
        } ?: error("找不到待确认入口")
        composeRule.onNodeWithContentDescription(match).performClick()
        awaitText("待确认记录")
    }

    private fun entry(): M3aDebugEntryPoint {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return EntryPointAccessors.fromApplication(context.applicationContext, M3aDebugEntryPoint::class.java)
    }

    private fun counts(): ProofCounts = runBlocking {
        val entry = entry()
        val logs = entry.captureLogRepository().listAll()
        val evidence = logs.sumOf { entry.captureLogRepository().listEvidence(it.captureId).size }
        val decisions = entry.duplicateDecisionRepository().listAll()
        ProofCounts(
            logs = logs.size,
            transactions = entry.transactionRepository().listVisible().size,
            evidence = evidence,
            decisions = decisions.size,
            same = decisions.count { it.verdict == DuplicateVerdict.SAME_TRANSACTION },
            different = decisions.count { it.verdict == DuplicateVerdict.DIFFERENT_TRANSACTION },
        )
    }

    private fun decisions() = runBlocking { entry().duplicateDecisionRepository().listAll() }

    private fun awaitText(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertAbsent(text: String) {
        composeRule.waitForIdle()
        assertEquals(0, composeRule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().size)
    }

    private fun scrollToText(text: String) {
        composeRule.onAllNodesWithText(text, substring = true)[0].performScrollTo().assertIsDisplayed()
    }

    private fun scrollToDescription(text: String) {
        composeRule.onNodeWithContentDescription(text, substring = true).performScrollTo().assertIsDisplayed()
    }

    private fun awaitDescription(text: String) {
        composeRule.waitUntil(timeoutMillis = 60_000) {
            composeRule.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription(text).assertIsDisplayed()
    }

    private fun back() {
        Espresso.pressBack()
        composeRule.waitForIdle()
    }

    private fun hideIme() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("input keyevent 111").close()
        composeRule.waitForIdle()
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
        val json = runBlocking { entry().ledgerProofQuery().json(BuildConfig.GIT_SHA) }
        File(filesDir(), "$name.json").writeText(json)
        assertEquals(BuildConfig.GIT_SHA, JSONObject(json).getString("gitSha"))
        return json
    }

    private fun writeJson(name: String, values: Map<String, Any>) {
        val json = JSONObject()
        values.forEach { (key, value) -> json.put(key, value) }
        File(filesDir(), "$name.json").writeText(json.toString())
    }

    private fun filesDir(): File =
        InstrumentationRegistry.getInstrumentation().targetContext.filesDir

    private fun rows(json: JSONObject): List<JSONObject> {
        val array = json.getJSONArray("rows")
        return List(array.length()) { index -> array.getJSONObject(index) }
    }

    private data class ProofCounts(
        val logs: Int,
        val transactions: Int,
        val evidence: Int,
        val decisions: Int,
        val same: Int,
        val different: Int,
    )
}

private class ShanghaiZoneRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val zone = TimeZone.getTimeZone("Asia/Shanghai")
            TimeZone.setDefault(zone)
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            automation.executeShellCommand("service call alarm 3 s16 Asia/Shanghai").close()
            base.evaluate()
        }
    }
}
