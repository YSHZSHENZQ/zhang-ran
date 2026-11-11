package com.zhangran.app.domain.service

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLedgerHealthTest {

    private val now = 1_800_000_000_000L

    @Test
    fun 总开关关闭时不可用即使无障碍已授权() {
        val result = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = false,
                accessibilityGranted = true,
                lastCaptureAtMillis = now,
                watchStartedAtMillis = now,
                nowMillis = now,
            ),
        )
        assertEquals(AutoLedgerAvailability.UNAVAILABLE, result)
        assertEquals("不可用", AutoLedgerHealth.label(result))
    }

    @Test
    fun 无障碍缺失时不可用() {
        val result = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = true,
                accessibilityGranted = false,
                lastCaptureAtMillis = now,
                watchStartedAtMillis = now,
                nowMillis = now,
            ),
        )
        assertEquals(AutoLedgerAvailability.UNAVAILABLE, result)
    }

    @Test
    fun 开关开启且无障碍已授权且刚开始监听为可用() {
        val result = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = true,
                accessibilityGranted = true,
                lastCaptureAtMillis = null,
                watchStartedAtMillis = now,
                nowMillis = now,
            ),
        )
        assertEquals(AutoLedgerAvailability.AVAILABLE, result)
        assertEquals("可用", AutoLedgerHealth.label(result))
    }

    @Test
    fun 最近一次捕获满二十四小时为疑似失效且不产生待确认() {
        val result = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = true,
                accessibilityGranted = true,
                lastCaptureAtMillis = now - AutoLedgerHealth.STALE_AFTER_MILLIS,
                watchStartedAtMillis = now,
                nowMillis = now,
            ),
        )
        assertEquals(AutoLedgerAvailability.SUSPECTED_STALE, result)
        assertEquals("疑似失效", AutoLedgerHealth.label(result))
    }

    @Test
    fun 差一毫秒不到二十四小时仍为可用() {
        val result = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = true,
                accessibilityGranted = true,
                lastCaptureAtMillis = now - AutoLedgerHealth.STALE_AFTER_MILLIS + 1,
                watchStartedAtMillis = now - AutoLedgerHealth.STALE_AFTER_MILLIS,
                nowMillis = now,
            ),
        )
        assertEquals(AutoLedgerAvailability.AVAILABLE, result)
    }
}
