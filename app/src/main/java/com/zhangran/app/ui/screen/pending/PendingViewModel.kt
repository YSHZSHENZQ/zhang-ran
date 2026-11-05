/**
 * 待确认页展示模型（M3a）。
 *
 * 按 reason 给出确认 / 改完再记 / 忽略。疑似重复行只有忽略。
 */
package com.zhangran.app.ui.screen.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.usecase.ConfirmPendingResult
import com.zhangran.app.domain.usecase.ConfirmPendingUseCase
import com.zhangran.app.domain.usecase.IgnorePendingResult
import com.zhangran.app.domain.usecase.IgnorePendingUseCase
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
    val fixtureId: String? = null,
    val canConfirm: Boolean = true,
    val canEdit: Boolean = true,
    val canIgnore: Boolean = true,
)

sealed interface PendingUiState {
    data object Loading : PendingUiState
    data object Empty : PendingUiState
    data class Content(val rows: List<PendingRow>, val banner: String? = null) : PendingUiState
    data class Error(val message: String) : PendingUiState
}

class PendingViewModel(
    private val loadPending: LoadPendingRecordsUseCase,
    private val confirmPending: ConfirmPendingUseCase,
    private val ignorePending: IgnorePendingUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow<PendingUiState>(PendingUiState.Loading)
    val uiState: StateFlow<PendingUiState> = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            if (mutableState.value !is PendingUiState.Content) {
                mutableState.value = PendingUiState.Loading
            }
            mutableState.value = loadRows()
        }
    }

    fun confirm(transactionId: String) {
        viewModelScope.launch {
            when (val result = confirmPending.invoke(transactionId)) {
                ConfirmPendingResult.Confirmed -> mutableState.value = loadRows()
                is ConfirmPendingResult.Rejected -> showBanner(result.reason)
            }
        }
    }

    fun ignore(transactionId: String) {
        viewModelScope.launch {
            when (val result = ignorePending.invoke(transactionId)) {
                IgnorePendingResult.Ignored -> mutableState.value = loadRows()
                is IgnorePendingResult.Rejected -> showBanner(result.reason)
            }
        }
    }

    private suspend fun loadRows(): PendingUiState = try {
        val rows = loadPending().map { toRow(it) }
        if (rows.isEmpty()) PendingUiState.Empty else PendingUiState.Content(rows)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (thrown: Throwable) {
        PendingUiState.Error(thrown.message ?: "加载失败")
    }

    private fun showBanner(message: String) {
        val current = mutableState.value
        mutableState.value = if (current is PendingUiState.Content) {
            current.copy(banner = message)
        } else {
            PendingUiState.Error(message)
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
                fixtureId = record.fixtureId,
                canConfirm = record.canConfirm,
                canEdit = record.canEdit,
                canIgnore = record.canIgnore,
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
