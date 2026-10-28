/**
 * 备份页 ViewModel（`M4b-1` 导出 + `M4b-2` 恢复）。
 *
 * 导出：生成 ZIP → 通过 `contentResolver` 写入 SAF 目标。
 * 恢复：读 SAF 文件 → `BackupReader` 全量校验（不写数据）→ 选模式 → （Replace 二次确认）
 * → `RestoreBackupUseCase` 单事务执行 → 报告。取消不报错；失败提示"数据未改变"。
 */
package com.zhangran.app.ui.screen.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.backup.AppVersion
import com.zhangran.app.data.backup.BackupReader
import com.zhangran.app.data.backup.BackupWriter
import com.zhangran.app.data.backup.RestoreMode
import com.zhangran.app.data.backup.RestorePayload
import com.zhangran.app.domain.usecase.ExportBackupUseCase
import com.zhangran.app.domain.usecase.RestoreBackupUseCase
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
    private val restoreBackup: RestoreBackupUseCase,
    private val backupReader: BackupReader,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private val _restoreState = MutableStateFlow<RestoreUiState>(RestoreUiState.Idle)
    val restoreState: StateFlow<RestoreUiState> = _restoreState.asStateFlow()

    private val _events = MutableSharedFlow<BackupEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<BackupEvent> = _events.asSharedFlow()

    /** 已校验、等待执行的备份（只在内存中，取消/关闭即丢弃）。 */
    private var pendingPayload: RestorePayload? = null

    /** SAF 默认文件名（设备时区日期）。 */
    fun suggestedFileName(): String =
        BackupWriter.fileName(clock.millis(), ZoneId.systemDefault().id)

    // ------------------------------------------------------------------ 导出

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

    /** 关闭导出结果提示，回到初始态（不影响已写出的文件）。 */
    fun dismissResult() {
        if (_uiState.value !is BackupUiState.Exporting) {
            _uiState.value = BackupUiState.Idle
        }
    }

    // ------------------------------------------------------------------ 恢复

    /** SAF 选完文件后：全量校验（V1–V6），不写任何数据。 */
    fun onBackupFileSelected(uri: Uri) {
        if (_restoreState.value is RestoreUiState.Restoring) return
        val displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "备份文件"
        viewModelScope.launch {
            _restoreState.value = RestoreUiState.Validating(displayName)
            runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选文件")
                    input.use { backupReader.read(it) }
                }
            }.onSuccess { payload ->
                pendingPayload = payload
                val summary = BackupSummary(
                    fileName = displayName,
                    backupVersion = payload.manifest.backupVersion,
                    createdAt = payload.manifest.createdAt,
                    recordCount = payload.manifest.files.sumOf { it.recordCount },
                )
                _restoreState.value = RestoreUiState.ModeSelection(displayName, summary)
            }.onFailure { throwable ->
                pendingPayload = null
                _restoreState.value = RestoreUiState.Error(
                    throwable.message ?: "备份文件损坏，无法恢复",
                )
            }
        }
    }

    /** 模式选择：Replace 需要二次确认，其余模式直接执行。 */
    fun selectMode(mode: RestoreMode) {
        val payload = pendingPayload ?: return
        val current = _restoreState.value
        if (current !is RestoreUiState.ModeSelection) return
        when (mode) {
            RestoreMode.REPLACE -> {
                _restoreState.value = RestoreUiState.ConfirmReplace(current.fileName)
            }

            else -> executeRestore(mode, payload)
        }
    }

    /** Replace 二次确认通过。 */
    fun confirmReplace() {
        val payload = pendingPayload ?: return
        if (_restoreState.value !is RestoreUiState.ConfirmReplace) return
        executeRestore(RestoreMode.REPLACE, payload)
    }

    private fun executeRestore(mode: RestoreMode, payload: RestorePayload) {
        viewModelScope.launch {
            _restoreState.value = RestoreUiState.Restoring(mode)
            runCatching {
                withContext(Dispatchers.IO) { restoreBackup.restore(payload, mode) }
            }.onSuccess { report ->
                pendingPayload = null
                _restoreState.value = RestoreUiState.Report(report)
            }.onFailure { throwable ->
                _restoreState.value = RestoreUiState.Error(
                    throwable.message ?: "恢复失败，数据未改变",
                )
            }
        }
    }

    /** 取消恢复流程（模式选择/确认/报告/错误）。执行中不可取消。 */
    fun dismissRestore() {
        if (_restoreState.value is RestoreUiState.Restoring) return
        pendingPayload = null
        _restoreState.value = RestoreUiState.Idle
    }
}
