/**
 * 把采样被拒的原因写进 `capture_logs`，供日志页和导出 JSON 查看。
 *
 * 同一原因在 [RejectionRateLimiter.WINDOW_MILLIS] 内只留一条，避免
 * `TYPE_WINDOW_CONTENT_CHANGED` 把日志打满。
 * 不写页面正文，不更新上次捕获时间，不创建交易。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.capture.COLLECTOR_INTERFACE_VERSION
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class AccessibilityRejectReason {
    WRONG_WINDOW,
    NO_MATCHING_WINDOW,
    NOT_PAYMENT_PAGE,
    WINDOW_NULL,
    NODE_EMPTY,
    SWITCH_OFF,
}

/**
 * 同一原因的去重窗口。窗口内后到的同名原因直接丢掉。
 * 60 秒：盖住结果页上连续的内容变化，又不会把下一次独立失败吞掉。
 */
class RejectionRateLimiter(
    private val windowMillis: Long = WINDOW_MILLIS,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {

    private val lastAccepted = HashMap<String, Long>()
    private val lock = Any()

    fun shouldRecord(reason: String, now: Long = clock()): Boolean = synchronized(lock) {
        val previous = lastAccepted[reason]
        if (previous != null && now - previous < windowMillis) return false
        lastAccepted[reason] = now
        true
    }

    fun reset() = synchronized(lock) {
        lastAccepted.clear()
    }

    companion object {
        const val WINDOW_MILLIS: Long = 60_000L
    }
}

class AccessibilityRejectionLog(
    private val records: CaptureLogRepository,
    private val health: CaptureHealthStore,
    private val rateLimiter: RejectionRateLimiter = RejectionRateLimiter(),
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * @return 新日志的 captureId；窗口内的重复返回 null，不落库。
     */
    suspend fun record(
        reason: AccessibilityRejectReason,
        eventPackage: String?,
        rootPackage: String? = null,
        fixtureId: String? = null,
        capturedAt: Long = System.currentTimeMillis(),
    ): String? {
        if (!rateLimiter.shouldRecord(reason.name, capturedAt)) return null
        return try {
            val captureId = newId()
            records.insert(
                log = CaptureLogEntity(
                    id = "log-$captureId",
                    captureId = captureId,
                    capturedAt = capturedAt,
                    captureSource = CaptureSource.ACCESSIBILITY,
                    packageName = eventPackage?.take(MAX_PACKAGE),
                    channel = channelOf(eventPackage),
                    parserRuleId = null,
                    engineVersion = ENGINE_VERSION,
                    payloadVersion = COLLECTOR_INTERFACE_VERSION,
                    status = CaptureLogStatus.IGNORED_INVALID,
                    reason = reason.name,
                    isHistoricalReplay = false,
                    rawCaptureRef = refOf(reason, eventPackage, rootPackage, fixtureId),
                    evidenceId = null,
                    transactionId = null,
                ),
                evidence = null,
            )
            health.recordRejectedSample()
            captureId
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
    }

    private fun refOf(
        reason: AccessibilityRejectReason,
        eventPackage: String?,
        rootPackage: String?,
        fixtureId: String?,
    ): String = buildString {
        append('{')
        append("\"kind\":\"rejection\"")
        append(",\"reason\":${jsonString(reason.name)}")
        append(",\"eventPackage\":${jsonString(eventPackage)}")
        append(",\"rootPackage\":${jsonString(rootPackage)}")
        append(",\"fixtureId\":${jsonString(fixtureId)}")
        append('}')
    }

    private fun channelOf(packageName: String?): String? = when (packageName) {
        AccessibilityTargets.WECHAT_PACKAGE -> "wechat"
        AccessibilityTargets.ALIPAY_PACKAGE -> "alipay"
        else -> null
    }

    private companion object {
        const val ENGINE_VERSION = "m6-2"
        const val MAX_PACKAGE = 128
    }
}

private fun jsonString(value: String?): String =
    if (value == null) "null" else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
