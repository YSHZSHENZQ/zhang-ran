/**
 * 备份页 UI 状态（`M4b-1` §4.7）。
 *
 * 导出四态：`Idle` / `Exporting` / `Success` / `Error`；
 * 恢复流程独立为 [RestoreUiState]（M4b-2：校验 → 选模式 → 危险确认 → 执行 → 报告）。
 */
package com.zhangran.app.ui.screen.backup

import com.zhangran.app.data.backup.RestoreMode
import com.zhangran.app.data.backup.RestoreReport
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

sealed interface BackupUiState {

    data object Idle : BackupUiState

    data object Exporting : BackupUiState

    /**
     * @property fileName 实际写入的文件名（含日期）
     * @property entryCount 条目数（manifest + 11 个数据文件 = 12）
     * @property byteSize ZIP 字节数
     * @property createdAt 生成时间（UTC epoch millis）
     */
    data class Success(
        val fileName: String,
        val entryCount: Int,
        val byteSize: Long,
        val createdAt: Long,
    ) : BackupUiState

    data class Error(val message: String) : BackupUiState
}

/** 一次性事件（当前仅用于保底提示）。 */
sealed interface BackupEvent {
    data class Message(val text: String) : BackupEvent
}

/** 生成时间文案（设备时区）。 */
fun backupTimeText(createdAt: Long, zoneId: String = ZoneId.systemDefault().id): String {
    val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
    return Instant.ofEpochMilli(createdAt)
        .atZone(zone)
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

/** 文件大小文案（整数运算，KB 保留 1 位小数）。 */
fun backupSizeText(byteSize: Long): String = when {
    byteSize < 1024L -> "$byteSize B"
    byteSize < 1024L * 1024L -> {
        val tenths = byteSize * 10L / 1024L
        "${tenths / 10}.${tenths % 10} KB"
    }

    else -> {
        val tenths = byteSize * 10L / (1024L * 1024L)
        "${tenths / 10}.${tenths % 10} MB"
    }
}


/** 恢复前的备份摘要（模式选择弹层展示）。 */
data class BackupSummary(
    val fileName: String,
    val backupVersion: Int,
    val createdAt: Long,
    val recordCount: Int,
)

/**
 * 恢复流程状态（`M4b-2` §4.9）。
 *
 * `Validating` 只做 V1–V6 校验；校验通过后才进入 `ModeSelection`。
 */
sealed interface RestoreUiState {

    data object Idle : RestoreUiState

    data class Validating(val fileName: String) : RestoreUiState

    data class ModeSelection(
        val fileName: String,
        val summary: BackupSummary,
    ) : RestoreUiState

    data class ConfirmReplace(val fileName: String) : RestoreUiState

    data class Restoring(val mode: RestoreMode) : RestoreUiState

    data class Report(val report: RestoreReport) : RestoreUiState

    data class Error(val message: String) : RestoreUiState
}