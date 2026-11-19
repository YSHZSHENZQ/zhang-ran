package com.zhangran.app.capture.accessibility

import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.usecase.CaptureLogExport
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityRejectionLogTest {

    private val zone = ZoneId.of("Asia/Shanghai")
    private val base = 1_700_000_000_000L

    @Test
    fun 六种原因都进导出且同窗口内只留一条() = runBlocking {
        val logs = MemoryLogs()
        val health = MemoryHealth()
        health.setLastCaptureAt(base)
        val limiter = RejectionRateLimiter()
        val rejection = AccessibilityRejectionLog(
            records = logs,
            health = health,
            rateLimiter = limiter,
            newId = IdSeq()::next,
        )
        val reasons = AccessibilityRejectReason.entries
        reasons.forEachIndexed { index, reason ->
            val id = rejection.record(
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
            assertTrue(id != null)
        }
        val before = export(logs)
        reasons.forEach { reason ->
            val again = rejection.record(
                reason = reason,
                eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
                capturedAt = base + 1_000L,
            )
            assertNull(again)
        }
        val after = export(logs)
        assertEquals(before, after)
        assertEquals(reasons.size, logs.logs.size)
        assertTrue(logs.logs.all { it.status == CaptureLogStatus.IGNORED_INVALID })
        reasons.forEach { reason ->
            assertTrue(after.contains(reason.name))
        }
        assertTrue(after.contains("WRONG_WINDOW"))
        assertTrue(after.contains("NO_MATCHING_WINDOW"))
        assertEquals(base, health.current.lastCaptureAtMillis)
        assertEquals(0, health.captureCalls)
        assertTrue(health.current.rejectedSamplesSinceCapture)
        assertEquals(0, logs.evidence.size)
        assertTrue(logs.logs.none { it.transactionId != null })
        assertFalse(after.contains("我的"))
        assertFalse(after.contains("支付成功"))
        assertTrue(after.contains("\"windowId\":null"))
        assertTrue(after.contains("\"rootClass\":null"))
        assertTrue(after.contains("\"viewIds\":null"))
        assertTrue(after.contains("\"nodeCount\":null"))
        assertTrue(after.contains("\"hasAmountStyle\":null"))
        assertTrue(after.contains("\"hasPaymentKeyword\":null"))
        assertTrue(CaptureLogExport.sensitiveFindings(after).isEmpty())
    }

    @Test
    fun 窗口刚好结束时可以再记一条() = runBlocking {
        val logs = MemoryLogs()
        val rejection = AccessibilityRejectionLog(
            records = logs,
            health = MemoryHealth(),
            rateLimiter = RejectionRateLimiter(),
        )
        rejection.record(
            reason = AccessibilityRejectReason.WINDOW_NULL,
            eventPackage = AccessibilityTargets.WECHAT_PACKAGE,
            capturedAt = base,
        )
        rejection.record(
            reason = AccessibilityRejectReason.WINDOW_NULL,
            eventPackage = AccessibilityTargets.WECHAT_PACKAGE,
            capturedAt = base + RejectionRateLimiter.WINDOW_MILLIS,
        )
        assertEquals(2, logs.logs.count { it.reason == "WINDOW_NULL" })
    }

    private fun export(logs: MemoryLogs): String =
        CaptureLogExport.json(
            gitSha = "test",
            entries = logs.logs.map { CaptureLogExport.entry(it, zone) },
        )
}
