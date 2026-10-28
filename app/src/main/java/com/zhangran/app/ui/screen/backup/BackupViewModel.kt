/**
 * 备份页 ViewModel（`M4b-1` §4.6/§4.7）。
 *
 * - `export(uri)`：生成 ZIP（`ExportBackupUseCase`）→ 通过 `contentResolver` 写入 SAF 目标；
 * - 取消由 UI 处理（不回调 = 不报错）；
 * - 写入失败（权限/空间/输出流为空）→ `Error`，可重试（重新选择位置）。
 */
package com.zhangran.app.ui.screen.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.backup.AppVersion
import com.zhangran.app.data.backup.BackupWriter
import com.zhangran.app.domain.usecase.ExportBackupUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupViewModel @Inject constructor(
    private val exportBackup: ExportBackupUseCase,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    /** SAF 默认文件名（设备时区日期）。 */
    fun suggestedFileName(): String =
        BackupWriter.fileName(clock.millis(), ZoneId.systemDefault().id)

    /** 生成并写入用户选择的位置。 */
    fun export(target: Uri) {
        if (_uiState.value is BackupUiState.Exporting) return
        viewModelScope.launch {
            _uiState.value = BackupUiState.Exporting
            runCatching {
                val zip = withContext(Dispatchers.IO) {
                    exportBackup.export(
                        AppVersion(
                            versionName = BuildConfig.VERSION_NAME,
                            versionCode = BuildConfig.VERSION_CODE.toLong(),
                        ),
                    )
                }
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(target)
                        ?: error("无法打开所选位置，请重新选择")
                    output.use { it.write(zip.bytes) }
                }
                zip
            }.onSuccess { zip ->
                _uiState.value = BackupUiState.Success(
                    fileName = zip.fileName,
                    entryCount = zip.entryCount,
                    byteSize = zip.bytes.size.toLong(),
                    createdAt = zip.manifest.createdAt,
                )
            }.onFailure { throwable ->
                _uiState.value = BackupUiState.Error(
                    throwable.message ?: "导出失败，请检查存储空间后重试",
                )
            }
        }
    }

    /** 关闭结果提示，回到初始态（不影响已写出的文件）。 */
    fun dismissResult() {
        if (_uiState.value !is BackupUiState.Exporting) {
            _uiState.value = BackupUiState.Idle
        }
    }
}
