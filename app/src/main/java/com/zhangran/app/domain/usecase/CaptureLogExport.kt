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
        return CaptureLogExportEntry(
            id = log.id,
            capturedAt = log.capturedAt,
            time = time,
            source = log.captureSource.name,
            packageName = SensitiveText.redact(log.packageName),
            status = log.status.name,
            reason = SensitiveText.redact(log.reason),
            fixtureId = SensitiveText.redact(fixtureIdOf(log.rawCaptureRef)),
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
        append('}')
    }

    private fun unescape(value: String): String =
        value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")

    private val STRING_FIELD = Regex(""""((?:[^"\\]|\\.)*)"""")
}
