package com.zhangran.app.data.autoledger

import com.zhangran.app.capture.accessibility.MemoryHealth
import com.zhangran.app.capture.accessibility.MemoryLogs
import com.zhangran.app.data.device.DevicePermissionProbe
import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.data.settings.SettingsWriteResult
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.service.AutoLedgerHealth
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLedgerStaleFlipMonitorTest {

    private val now = 1_800_000_000_000L
    private val day = AutoLedgerHealth.STALE_AFTER_MILLIS

    @Test
    fun 跨过二十四小时写一条且不更新上次捕获也不建交易() = runBlocking {
        val health = MemoryHealth()
        val logs = MemoryLogs()
        health.markWatchStarted(now - day)
        health.recordRejectedSample()
        val settings = FakeSettings(enabled = true)
        val monitor = monitor(settings, health, logs, granted = true)
        monitor.evaluateNow()
        monitor.evaluateNow()
        assertEquals(1, logs.logs.size)
        val log = logs.logs.single()
        assertEquals(CaptureLogStatus.RECEIVED, log.status)
        assertTrue(log.reason.orEmpty().contains("SUSPECTED_STALE"))
        assertTrue(log.reason.orEmpty().contains("lastCaptureAt=null"))
        assertTrue(
            log.reason.orEmpty().contains(
                "watchStartedAt=${AutoLedgerStaleFlip.formatTime(now - day)}",
            ),
        )
        assertTrue(log.reason.orEmpty().contains("rejectedSamples=true"))
        assertNull(log.transactionId)
        assertEquals(0, logs.evidence.size)
        assertNull(health.current.lastCaptureAtMillis)
        assertFalse(log.rawCaptureRef.orEmpty().contains("我的"))
        assertFalse(log.rawCaptureRef.orEmpty().contains("支付成功"))
    }

    @Test
    fun 离开疑似失效后再次进入会再写一条() = runBlocking {
        val health = MemoryHealth()
        val logs = MemoryLogs()
        val settings = FakeSettings(enabled = true)
        val monitor = monitor(settings, health, logs, granted = true)
        health.markWatchStarted(now - day)
        monitor.evaluateNow()
        settings.enabled = false
        monitor.evaluateNow()
        assertNull(health.current.staleFlipMarker)
        assertEquals(1, logs.logs.size)
        settings.enabled = true
        health.setLastCaptureAt(now - day - 1_000L)
        monitor.evaluateNow()
        assertEquals(2, logs.logs.size)
        assertTrue(
            logs.logs.last().reason.orEmpty().contains(
                "lastCaptureAt=${AutoLedgerStaleFlip.formatTime(now - day - 1_000L)}",
            ),
        )
        assertNull(health.current.lastCaptureAtMillis?.let { if (it == now) it else null })
        assertEquals(now - day - 1_000L, health.current.lastCaptureAtMillis)
    }

    @Test
    fun 未满二十四小时不写日志() = runBlocking {
        val health = MemoryHealth()
        val logs = MemoryLogs()
        health.markWatchStarted(now - day + 1L)
        val monitor = monitor(FakeSettings(enabled = true), health, logs, granted = true)
        monitor.evaluateNow()
        assertEquals(0, logs.logs.size)
        assertNull(health.current.staleFlipMarker)
    }

    private fun monitor(
        settings: FakeSettings,
        health: MemoryHealth,
        logs: MemoryLogs,
        granted: Boolean,
    ) = AutoLedgerStaleFlipMonitor(
        settings = settings,
        health = health,
        probe = object : DevicePermissionProbe {
            override fun accessibilityGranted(): Boolean = granted
            override fun batteryOptimizationIgnored(): Boolean = true
        },
        records = logs,
        clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock = this
            override fun instant(): Instant = Instant.ofEpochMilli(now)
        },
        scope = CoroutineScope(SupervisorJob()),
    )

    private class FakeSettings(var enabled: Boolean) : SettingsRepository {
        private val flow = MutableStateFlow(AppSettings(autoLedgerEnabled = enabled))

        override val settings: Flow<AppSettings>
            get() {
                flow.value = AppSettings(autoLedgerEnabled = enabled)
                return flow
            }

        override suspend fun setThemeMode(mode: ThemeMode) = Unit

        override suspend fun setThresholds(
            overallConfidenceThreshold: Double,
            fieldConfidenceThreshold: Double,
        ): SettingsWriteResult = SettingsWriteResult.Success

        override suspend fun setAutoLedgerEnabled(enabled: Boolean) {
            this.enabled = enabled
        }

        override suspend fun setOnboardCompleted(completed: Boolean) = Unit
    }
}
