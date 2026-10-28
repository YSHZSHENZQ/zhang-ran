/**
 * 备份页 UI 状态（`M4b-1` §4.7）。
 *
 * 四态：`Idle` / `Exporting` / `Success` / `Error`；恢复入口不在此状态机内（固定禁用）。
 */
package com.zhangran.app.ui.screen.backup

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
