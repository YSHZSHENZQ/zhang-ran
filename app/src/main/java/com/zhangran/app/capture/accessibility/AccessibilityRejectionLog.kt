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
     * @param page 拿得到页面结构时附上。`WINDOW_NULL`、`NODE_EMPTY` 等没有树时传 null。
     * 只含窗口和结构字段，不含正文、金额、人名或 contentDescription。
     * @return 新日志的 captureId；窗口内的重复返回 null，不落库。
     */
    suspend fun record(
        reason: AccessibilityRejectReason,
        eventPackage: String?,
        rootPackage: String? = null,
        fixtureId: String? = null,
        capturedAt: Long = System.currentTimeMillis(),
        page: RejectionPageFacts? = null,
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
                    rawCaptureRef = refOf(reason, eventPackage, rootPackage, fixtureId, page),
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
        page: RejectionPageFacts?,
    ): String = buildString {
        append('{')
        append("\"kind\":\"rejection\"")
        append(",\"reason\":${jsonString(reason.name)}")
        append(",\"eventPackage\":${jsonString(eventPackage)}")
        append(",\"rootPackage\":${jsonString(rootPackage)}")
        append(",\"fixtureId\":${jsonString(fixtureId)}")
        append(",\"windowId\":${page?.windowId?.toString() ?: "null"}")
        append(",\"rootClass\":${jsonString(page?.rootClass)}")
        append(",\"viewIds\":${jsonStringList(page?.viewIds)}")
        append(",\"nodeCount\":${page?.nodeCount?.toString() ?: "null"}")
        append(",\"hasAmountStyle\":${jsonBoolean(page?.hasAmountStyle)}")
        append(",\"hasPaymentKeyword\":${jsonBoolean(page?.hasPaymentKeyword)}")
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

private fun jsonBoolean(value: Boolean?): String = when (value) {
    null -> "null"
    true -> "true"
    false -> "false"
}

private fun jsonStringList(values: List<String>?): String {
    if (values == null) return "null"
    return values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }
}

/**
 * 拒绝日志能安全导出的页面结构。没有正文、金额数值、人名或 contentDescription。
 */
data class RejectionPageFacts(
    val windowId: Int?,
    val rootClass: String?,
    val viewIds: List<String>?,
    val nodeCount: Int?,
    val hasAmountStyle: Boolean?,
    val hasPaymentKeyword: Boolean?,
) {
    companion object {
        private const val MAX_IDS = 12
        private const val MAX_ID_CHARS = 80
        private const val MAX_CLASS_CHARS = 120

        private val RESOURCE_ID = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+:id/[A-Za-z0-9_]+""")
        private val CLASS_NAME = Regex("""[A-Za-z_][A-Za-z0-9_$.]*""")
        private val AMOUNT_STYLE = Regex("""[¥￥]\s*[0-9]|[0-9][0-9,，]*(?:\.[0-9]{1,2})?\s*元""")
        private val PAYMENT_WORDS = listOf(
            "支付成功",
            "付款成功",
            "支付完成",
            "已支付",
            "收款成功",
            "已收款",
            "收款到账",
            "退款成功",
        )

        fun fromSnapshot(snapshot: AccessibilityPageSnapshot): RejectionPageFacts {
            val viewIds = snapshot.nodes.mapNotNull { node ->
                val id = node.viewId?.trim().orEmpty()
                id.takeIf { it.length <= MAX_ID_CHARS && RESOURCE_ID.matches(it) }
            }.distinct().take(MAX_IDS)
            val texts = snapshot.nodes.flatMap { node ->
                listOfNotNull(node.text, node.contentDescription)
            }
            return RejectionPageFacts(
                windowId = snapshot.windowId,
                rootClass = snapshot.rootClassName?.trim()?.take(MAX_CLASS_CHARS)
                    ?.takeIf { CLASS_NAME.matches(it) },
                viewIds = viewIds,
                nodeCount = snapshot.nodes.size,
                hasAmountStyle = texts.any { AMOUNT_STYLE.containsMatchIn(it) },
                hasPaymentKeyword = texts.any { text -> PAYMENT_WORDS.any { word -> text.contains(word) } },
            )
        }
    }
}
