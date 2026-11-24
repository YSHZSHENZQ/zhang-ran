/**
 * 自动记账从「可用」变成「疑似失效」时写一条可导出的日志。
 *
 * 阈值仍是 24 小时。日志里只有上次成功捕获时间（或 null）、开始监听时间，
 * 以及这期间有没有被拒绝的采样。不写页面正文，不新建 Pending，不改上次捕获时间。
 * 同一次静默只写一条；离开疑似失效之后，下一次再进入才再写。
 */
package com.zhangran.app.data.autoledger

import com.zhangran.app.capture.COLLECTOR_INTERFACE_VERSION
import com.zhangran.app.data.device.DevicePermissionProbe
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.settings.CaptureHealth
import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.di.ApplicationScope
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.service.AutoLedgerAvailability
import com.zhangran.app.domain.service.AutoLedgerHealth
import com.zhangran.app.domain.service.AutoLedgerHealthInput
import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object AutoLedgerStaleFlip {

    const val KIND: String = "SUSPECTED_STALE"

    /** 同一次静默只记一次。离开疑似失效后清掉，下次再进入才再写。 */
    const val EPISODE: String = "logged"

    fun reason(
        lastCaptureAtMillis: Long?,
        watchStartedAtMillis: Long?,
        rejectedSamples: Boolean,
    ): String {
        val last = formatTime(lastCaptureAtMillis)
        val watch = formatTime(watchStartedAtMillis)
        return "SUSPECTED_STALE lastCaptureAt=$last watchStartedAt=$watch rejectedSamples=$rejectedSamples"
    }

    /**
     * 用带分隔符的 UTC 时间，避免 13 位毫秒被导出脱敏当成单号打码。
     * null 保持字面 null。
     */
    fun formatTime(millis: Long?): String =
        if (millis == null) "null" else DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(millis))
}

@Singleton
class AutoLedgerStaleFlipMonitor @Inject constructor(
    private val settings: SettingsRepository,
    private val health: CaptureHealthStore,
    private val probe: DevicePermissionProbe,
    private val records: CaptureLogRepository,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val started = AtomicBoolean(false)
    private val gate = Mutex()
    private val newId: () -> String = { UUID.randomUUID().toString() }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            combine(settings.settings, health.state) { _, _ -> Unit }.collect {
                try {
                    evaluate()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Throwable) {
                    // 健康度日志写失败不影响首页。
                }
            }
        }
    }

    suspend fun evaluateNow() {
        evaluate()
    }

    private suspend fun evaluate() {
        gate.withLock {
            val enabled = settings.settings.first().autoLedgerEnabled
            val current = health.state.first()
            val input = AutoLedgerHealthInput(
                masterEnabled = enabled,
                accessibilityGranted = probe.accessibilityGranted(),
                lastCaptureAtMillis = current.lastCaptureAtMillis,
                watchStartedAtMillis = current.watchStartedAtMillis,
                nowMillis = clock.millis(),
            )
            val availability = AutoLedgerHealth.resolve(input)
            if (availability != AutoLedgerAvailability.SUSPECTED_STALE) {
                if (current.staleFlipMarker != null) health.clearStaleFlipMarker()
                return
            }
            if (current.staleFlipMarker != null) return
            write(current, input)
            health.markStaleFlip(AutoLedgerStaleFlip.EPISODE)
        }
    }

    private suspend fun write(current: CaptureHealth, input: AutoLedgerHealthInput) {
        val captureId = newId()
        val capturedAt = clock.millis()
        val reason = AutoLedgerStaleFlip.reason(
            lastCaptureAtMillis = input.lastCaptureAtMillis,
            watchStartedAtMillis = input.watchStartedAtMillis,
            rejectedSamples = current.rejectedSamplesSinceCapture,
        )
        records.insert(
            log = CaptureLogEntity(
                id = "log-$captureId",
                captureId = captureId,
                capturedAt = capturedAt,
                captureSource = CaptureSource.ACCESSIBILITY,
                packageName = null,
                channel = null,
                parserRuleId = null,
                engineVersion = "m6-2",
                payloadVersion = COLLECTOR_INTERFACE_VERSION,
                status = CaptureLogStatus.RECEIVED,
                reason = reason,
                isHistoricalReplay = false,
                rawCaptureRef = refOf(input, current.rejectedSamplesSinceCapture),
                evidenceId = null,
                transactionId = null,
            ),
            evidence = null,
        )
    }

    private fun refOf(input: AutoLedgerHealthInput, rejectedSamples: Boolean): String = buildString {
        append('{')
        append("\"kind\":\"${AutoLedgerStaleFlip.KIND}\"")
        append(",\"lastCaptureAt\":${input.lastCaptureAtMillis ?: "null"}")
        append(",\"watchStartedAt\":${input.watchStartedAtMillis ?: "null"}")
        append(",\"rejectedSamples\":$rejectedSamples")
        append('}')
    }
}
