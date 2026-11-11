/**
 * 自动记账页：总状态、微信/支付宝监听、权限、最近识别记录。本页没有总开关。
 */
package com.zhangran.app.ui.screen.autoLedger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.autoledger.AutoLedgerSnapshot
import com.zhangran.app.data.autoledger.AutoLedgerStatusSource
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.service.SensitiveText
import com.zhangran.app.ui.common.CaptureSourceText
import com.zhangran.app.ui.common.DeviceZone
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RecentCaptureUi(
    val id: String,
    val timeText: String,
    val sourceText: String,
    val packageName: String,
    val statusText: String,
    val summary: String,
)

sealed interface AutoLedgerUiState {
    data object Loading : AutoLedgerUiState

    data class Content(
        val snapshot: AutoLedgerSnapshot,
        val recent: List<RecentCaptureUi>,
    ) : AutoLedgerUiState

    data class Error(val message: String) : AutoLedgerUiState
}

class AutoLedgerViewModel(
    private val status: AutoLedgerStatusSource,
    private val logs: CaptureLogRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val state = MutableStateFlow<AutoLedgerUiState>(AutoLedgerUiState.Loading)
    val uiState: StateFlow<AutoLedgerUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            status.snapshots.collect { snapshot ->
                state.value = runCatching {
                    AutoLedgerUiState.Content(snapshot, loadRecent())
                }.getOrElse { thrown ->
                    AutoLedgerUiState.Error(thrown.message ?: "读取自动记账状态失败")
                }
            }
        }
    }

    fun refresh() {
        status.refreshPermissions()
    }

    private suspend fun loadRecent(): List<RecentCaptureUi> {
        val zone = DeviceZone.current()
        return logs.listAll()
            .sortedByDescending { it.capturedAt }
            .take(RECENT_LIMIT)
            .map { log ->
                val merchant = log.transactionId?.let { transactions.getById(it)?.merchant }
                RecentCaptureUi(
                    id = log.id,
                    timeText = TIME.format(Instant.ofEpochMilli(log.capturedAt).atZone(zone)),
                    sourceText = CaptureSourceText.of(log.captureSource),
                    packageName = log.packageName ?: "—",
                    statusText = log.status.name,
                    summary = SensitiveText.redact(merchant) ?: (SensitiveText.redact(log.reason) ?: "—"),
                )
            }
    }

    private companion object {
        const val RECENT_LIMIT: Int = 8
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    }
}
