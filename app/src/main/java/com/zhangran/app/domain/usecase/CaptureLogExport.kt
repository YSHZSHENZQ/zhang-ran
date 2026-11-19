/**
 * 捕获日志导出成一份脱敏 JSON。
 *
 * 文件名 `zhangran-logs-yyyyMMdd-HHmmss.json`。正文含 GIT_SHA 和日志条目。
 * 字符串字段先按 `02` §20 脱敏，时间戳保持数字，避免被当成单号打码。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.domain.service.SensitiveFieldScan
import com.zhangran.app.domain.service.SensitiveText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CaptureLogExportEntry(
    val id: String,
    val capturedAt: Long,
    val time: String,
    val source: String,
    val packageName: String?,
    val status: String,
    val reason: String?,
    val fixtureId: String?,
    val windowId: Long? = null,
    val rootClass: String? = null,
    val viewIds: List<String>? = null,
    val nodeCount: Int? = null,
    val hasAmountStyle: Boolean? = null,
    val hasPaymentKeyword: Boolean? = null,
)

object CaptureLogExport {

    private val fileStamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    private val entryTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun fileName(nowMillis: Long, zone: ZoneId): String {
        val stamp = fileStamp.format(Instant.ofEpochMilli(nowMillis).atZone(zone))
        return "zhangran-logs-$stamp.json"
    }

    fun entry(log: CaptureLogEntity, zone: ZoneId): CaptureLogExportEntry {
        val time = entryTime.format(Instant.ofEpochMilli(log.capturedAt).atZone(zone))
        val facts = rejectionFactsOf(log.rawCaptureRef)
        return CaptureLogExportEntry(
            id = log.id,
            capturedAt = log.capturedAt,
            time = time,
            source = log.captureSource.name,
            packageName = SensitiveText.redact(log.packageName),
            status = log.status.name,
            reason = SensitiveText.redact(log.reason),
            fixtureId = SensitiveText.redact(fixtureIdOf(log.rawCaptureRef)),
            windowId = facts.windowId,
            rootClass = SensitiveText.redact(facts.rootClass),
            viewIds = facts.viewIds?.map { SensitiveText.redact(it).orEmpty() },
            nodeCount = facts.nodeCount,
            hasAmountStyle = facts.hasAmountStyle,
            hasPaymentKeyword = facts.hasPaymentKeyword,
        )
    }

    fun json(gitSha: String, entries: List<CaptureLogExportEntry>): String = buildString {
        append('{')
        append("\"gitSha\":${jsonString(gitSha)}")
        append(",\"entries\":[")
        append(entries.joinToString(",") { entry -> entryJson(entry) })
        append("]}")
    }

    /** 只检查字符串字段，不把 `capturedAt` 这种数字时间戳算成单号。 */
    fun sensitiveFindings(document: String): List<String> {
        val hits = mutableListOf<String>()
        STRING_FIELD.findAll(document).forEach { match ->
            val value = unescape(match.groupValues[1])
            SensitiveFieldScan.findings(value).forEach { kind ->
                hits += "$kind in ${match.groupValues[0].take(24)}"
            }
        }
        return hits
    }

    private fun entryJson(entry: CaptureLogExportEntry): String = buildString {
        append('{')
        append("\"id\":${jsonString(entry.id)}")
        append(",\"capturedAt\":${entry.capturedAt}")
        append(",\"time\":${jsonString(entry.time)}")
        append(",\"source\":${jsonString(entry.source)}")
        append(",\"packageName\":${jsonString(entry.packageName)}")
        append(",\"status\":${jsonString(entry.status)}")
        append(",\"reason\":${jsonString(entry.reason)}")
        append(",\"fixtureId\":${jsonString(entry.fixtureId)}")
        append(",\"windowId\":${entry.windowId?.toString() ?: "null"}")
        append(",\"rootClass\":${jsonString(entry.rootClass)}")
        append(",\"viewIds\":${jsonStringList(entry.viewIds)}")
        append(",\"nodeCount\":${entry.nodeCount?.toString() ?: "null"}")
        append(",\"hasAmountStyle\":${jsonBoolean(entry.hasAmountStyle)}")
        append(",\"hasPaymentKeyword\":${jsonBoolean(entry.hasPaymentKeyword)}")
        append('}')
    }

    private fun rejectionFactsOf(raw: String?): RejectionFacts {
        if (raw.isNullOrBlank() || !raw.contains("\"kind\":\"rejection\"")) return RejectionFacts.EMPTY
        return RejectionFacts(
            windowId = jsonNumber(raw, "windowId"),
            rootClass = jsonText(raw, "rootClass"),
            viewIds = jsonTextArray(raw, "viewIds"),
            nodeCount = jsonNumber(raw, "nodeCount")?.toInt(),
            hasAmountStyle = jsonFlag(raw, "hasAmountStyle"),
            hasPaymentKeyword = jsonFlag(raw, "hasPaymentKeyword"),
        )
    }

    private fun jsonNumber(raw: String, key: String): Long? {
        val match = Regex(""""$key"\s*:\s*(-?\d+)""").find(raw) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun jsonFlag(raw: String, key: String): Boolean? {
        val match = Regex(""""$key"\s*:\s*(true|false|null)""").find(raw) ?: return null
        return when (match.groupValues[1]) {
            "true" -> true
            "false" -> false
            else -> null
        }
    }

    private fun jsonText(raw: String, key: String): String? {
        val match = Regex(""""$key"\s*:\s*("((?:[^"\\]|\\.)*)"|null)""").find(raw) ?: return null
        if (match.groupValues[1] == "null") return null
        return unescape(match.groupValues[2])
    }

    private fun jsonTextArray(raw: String, key: String): List<String>? {
        val match = Regex(""""$key"\s*:\s*(\[[^\]]*\]|null)""").find(raw) ?: return null
        if (match.groupValues[1] == "null") return null
        return Regex(""""((?:[^"\\]|\\.)*)"""").findAll(match.groupValues[1]).map { unescape(it.groupValues[1]) }.toList()
    }

    private fun jsonStringList(values: List<String>?): String {
        if (values == null) return "null"
        return values.joinToString(prefix = "[", postfix = "]") { jsonString(it) }
    }

    private fun jsonBoolean(value: Boolean?): String = when (value) {
        null -> "null"
        true -> "true"
        false -> "false"
    }

    private data class RejectionFacts(
        val windowId: Long?,
        val rootClass: String?,
        val viewIds: List<String>?,
        val nodeCount: Int?,
        val hasAmountStyle: Boolean?,
        val hasPaymentKeyword: Boolean?,
    ) {
        companion object {
            val EMPTY = RejectionFacts(null, null, null, null, null, null)
        }
    }

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private val STRING_FIELD = Regex(""""((?:[^"\\]|\\.)*)"""")
}
