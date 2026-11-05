/**
 * 无障碍服务绑定后的连通诊断（M6-2 / AC-1）。
 *
 * 只记状态与时间，不记页面文本。落在 `capture_logs`，不产生交易。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.capture.COLLECTOR_INTERFACE_VERSION
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AccessibilityConnectivityRecorder(
    private val records: CaptureLogRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /** 返回本次诊断的捕获时间，供 logcat 打上同一时间戳。 */
    fun recordConnected(): Long {
        val capturedAt = clock()
        scope.launch {
            try {
                write(capturedAt)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // 诊断写库失败不影响服务继续采样。
            }
        }
        return capturedAt
    }

    suspend fun write(capturedAt: Long) {
        val captureId = newId()
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
                reason = SERVICE_CONNECTED,
                isHistoricalReplay = false,
                rawCaptureRef = """{"kind":"connectivity","status":"$SERVICE_CONNECTED","capturedAt":$capturedAt}""",
                evidenceId = null,
                transactionId = null,
            ),
            evidence = null,
        )
    }

    companion object {
        const val SERVICE_CONNECTED: String = "SERVICE_CONNECTED"
    }
}
