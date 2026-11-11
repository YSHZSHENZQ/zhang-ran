/**
 * 把脱敏日志写到缓存目录，供系统分享。
 */
package com.zhangran.app.data.export

import android.content.Context
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.domain.usecase.CaptureLogExport
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import java.util.TimeZone
import javax.inject.Inject

class CaptureLogExportWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logs: CaptureLogRepository,
    private val clock: Clock,
) {

    suspend fun write(gitSha: String): File {
        val zone = TimeZone.getDefault().toZoneId()
        val now = clock.millis()
        val entries = logs.listAll()
            .sortedByDescending { it.capturedAt }
            .map { CaptureLogExport.entry(it, zone) }
        val json = CaptureLogExport.json(gitSha, entries)
        val directory = File(context.cacheDir, DIRECTORY)
        directory.mkdirs()
        val file = File(directory, CaptureLogExport.fileName(now, zone))
        file.writeText(json)
        return file
    }

    companion object {
        const val DIRECTORY: String = "log-exports"
    }
}
