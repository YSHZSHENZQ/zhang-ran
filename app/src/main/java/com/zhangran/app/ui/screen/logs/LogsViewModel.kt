/**
 * 捕获日志页的列表、筛选、导出、清空和忽略恢复。
 */
package com.zhangran.app.ui.screen.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.export.CaptureLogExportWriter
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.service.SensitiveText
import com.zhangran.app.domain.usecase.ClearCaptureLogsUseCase
import com.zhangran.app.domain.usecase.RestoreIgnoredCaptureUseCase
import com.zhangran.app.domain.usecase.RestoreIgnoredResult
import com.zhangran.app.ui.common.CaptureSourceText
import com.zhangran.app.ui.common.DeviceZone
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CaptureLogRowUi(
    val id: String,
    val timeText: String,
    val sourceText: String,
    val packageName: String,
    val statusText: String,
    val reasonText: String,
    val canRestore: Boolean,
)

sealed interface LogsUiState {
    data object Loading : LogsUiState

    data class Content(
        val rows: List<CaptureLogRowUi>,
        val filter: CaptureLogStatus?,
        val emptyBecauseFilter: Boolean,
    ) : LogsUiState

    data class Error(val message: String) : LogsUiState
}

sealed interface LogsEvent {
    data class Share(val absolutePath: String) : LogsEvent

    data class Message(val text: String) : LogsEvent
}

class LogsViewModel(
    private val logs: CaptureLogRepository,
    private val clearLogs: ClearCaptureLogsUseCase,
    private val restoreIgnored: RestoreIgnoredCaptureUseCase,
    private val exporter: CaptureLogExportWriter,
    private val zone: () -> ZoneId = { DeviceZone.current() },
) : ViewModel() {

    private val filter = MutableStateFlow<CaptureLogStatus?>(null)
    private val state = MutableStateFlow<LogsUiState>(LogsUiState.Loading)
    val uiState: StateFlow<LogsUiState> = state.asStateFlow()

    private val events = MutableSharedFlow<LogsEvent>(extraBufferCapacity = 4)
    val eventsFlow: SharedFlow<LogsEvent> = events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            state.value = load()
        }
    }

    fun setFilter(status: CaptureLogStatus?) {
        filter.value = status
        refresh()
    }

    fun export(gitSha: String) {
        viewModelScope.launch {
            val file = runCatching { exporter.write(gitSha) }.getOrElse { thrown ->
                events.emit(LogsEvent.Message(thrown.message ?: "导出失败"))
                return@launch
            }
            events.emit(LogsEvent.Share(file.absolutePath))
        }
    }

    fun clear() {
        viewModelScope.launch {
            runCatching { clearLogs() }.onFailure { thrown ->
                events.emit(LogsEvent.Message(thrown.message ?: "清空失败"))
                return@launch
            }
            state.value = load()
        }
    }

    fun restore(logId: String) {
        viewModelScope.launch {
            when (val result = restoreIgnored.invoke(logId)) {
                RestoreIgnoredResult.Restored -> state.value = load()
                is RestoreIgnoredResult.Rejected -> events.emit(LogsEvent.Message(result.reason))
            }
        }
    }

    private suspend fun load(): LogsUiState = try {
        val zoneId = zone()
        val all = logs.listAll().sortedByDescending { it.capturedAt }
        val selected = filter.value
        val shown = if (selected == null) all else all.filter { it.status == selected }
        LogsUiState.Content(
            rows = shown.map { log ->
                CaptureLogRowUi(
                    id = log.id,
                    timeText = TIME.format(Instant.ofEpochMilli(log.capturedAt).atZone(zoneId)),
                    sourceText = CaptureSourceText.of(log.captureSource) + " " + log.captureSource.name,
                    packageName = log.packageName ?: "—",
                    statusText = log.status.name,
                    reasonText = SensitiveText.redact(log.reason) ?: "—",
                    canRestore = log.status == CaptureLogStatus.IGNORED && log.transactionId != null,
                )
            },
            filter = selected,
            emptyBecauseFilter = shown.isEmpty() && all.isNotEmpty(),
        )
    } catch (thrown: kotlinx.coroutines.CancellationException) {
        throw thrown
    } catch (thrown: Throwable) {
        LogsUiState.Error(thrown.message ?: "读取日志失败")
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    }
}
