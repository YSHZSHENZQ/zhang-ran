/**
 * 待确认页展示模型（M6-2）。
 *
 * 只格式化金额、商户和原因。不提供编辑后入账，也不把记录改成已确认。
 */
package com.zhangran.app.ui.screen.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.usecase.LoadPendingRecordsUseCase
import com.zhangran.app.domain.usecase.PendingRecord
import com.zhangran.app.ui.common.CaptureSourceText
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingRow(
    val transactionId: String,
    val amountText: String,
    val merchantText: String,
    val reasonText: String,
    val sourceText: String,
    val timeText: String,
)

sealed interface PendingUiState {
    data object Loading : PendingUiState
    data object Empty : PendingUiState
    data class Content(val rows: List<PendingRow>) : PendingUiState
    data class Error(val message: String) : PendingUiState
}

class PendingViewModel(
    private val loadPending: LoadPendingRecordsUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow<PendingUiState>(PendingUiState.Loading)
    val uiState: StateFlow<PendingUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            mutableState.value = PendingUiState.Loading
            mutableState.value = try {
                val rows = loadPending().map { toRow(it) }
                if (rows.isEmpty()) PendingUiState.Empty else PendingUiState.Content(rows)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (thrown: Throwable) {
                PendingUiState.Error(thrown.message ?: "加载失败")
            }
        }
    }

    companion object {
        const val UNKNOWN_MERCHANT: String = "未知"

        private val severity = listOf(
            PendingReason.AMOUNT_UNCERTAIN,
            PendingReason.DIRECTION_UNCERTAIN,
            PendingReason.LOW_CONFIDENCE,
            PendingReason.POSSIBLE_DUPLICATE,
            PendingReason.CATEGORY_UNCERTAIN,
            PendingReason.ACCOUNT_UNCERTAIN,
            PendingReason.UNKNOWN_MERCHANT,
            PendingReason.REFUND_UNCERTAIN,
            PendingReason.TRANSFER_UNCERTAIN,
            PendingReason.UNSUPPORTED_NOTIFICATION,
        )

        fun toRow(record: PendingRecord, zoneId: String = ZoneId.systemDefault().id): PendingRow {
            val zone = runCatching { ZoneId.of(zoneId) }.getOrElse { ZoneId.systemDefault() }
            return PendingRow(
                transactionId = record.transactionId,
                amountText = HomeViewModel.formatMoney(
                    amountMinor = record.amountMinor,
                    sign = AmountSign.NONE,
                    withCurrencySymbol = true,
                ),
                merchantText = record.merchant?.takeIf { it.isNotBlank() } ?: UNKNOWN_MERCHANT,
                reasonText = reasonLine(record.reasons),
                sourceText = CaptureSourceText.of(record.source),
                timeText = HomeViewModel.formatTimeOfDay(record.capturedAt, zone),
            )
        }

        fun reasonLine(reasons: Collection<PendingReason>): String =
            reasons.distinct()
                .sortedBy { reason ->
                    val index = severity.indexOf(reason)
                    if (index < 0) Int.MAX_VALUE else index
                }
                .joinToString(separator = "、") { reasonText(it) }

        fun reasonText(reason: PendingReason): String = when (reason) {
            PendingReason.UNKNOWN_MERCHANT -> "商户未知"
            PendingReason.AMOUNT_UNCERTAIN -> "金额不确定"
            PendingReason.DIRECTION_UNCERTAIN -> "收支方向不确定"
            PendingReason.CATEGORY_UNCERTAIN -> "分类不确定"
            PendingReason.ACCOUNT_UNCERTAIN -> "账户不确定"
            PendingReason.POSSIBLE_DUPLICATE -> "疑似重复"
            PendingReason.REFUND_UNCERTAIN -> "退款不确定"
            PendingReason.TRANSFER_UNCERTAIN -> "转账不确定"
            PendingReason.UNSUPPORTED_NOTIFICATION -> "识别结果不可用"
            PendingReason.LOW_CONFIDENCE -> "置信度不足"
        }
    }
}
