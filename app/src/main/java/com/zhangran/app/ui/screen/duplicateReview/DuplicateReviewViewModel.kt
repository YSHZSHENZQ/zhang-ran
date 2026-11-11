/**
 * 疑似重复对照页。展示字段由 [com.zhangran.app.domain.usecase.DuplicateReviewUseCase] 给出。
 */
package com.zhangran.app.ui.screen.duplicateReview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.domain.usecase.DuplicateActionResult
import com.zhangran.app.domain.usecase.DuplicateComparison
import com.zhangran.app.domain.usecase.DuplicateReviewUseCase
import com.zhangran.app.domain.usecase.DuplicateSide
import com.zhangran.app.ui.common.CaptureSourceText
import com.zhangran.app.ui.common.DeviceZone
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DuplicateSideUi(
    val heading: String,
    val merchant: String,
    val amount: String,
    val direction: String,
    val time: String,
    val source: String,
)

data class DuplicateReviewContent(
    val fresh: DuplicateSideUi,
    val existing: DuplicateSideUi,
    val busy: Boolean = false,
    val message: String? = null,
)

sealed interface DuplicateReviewUiState {
    data object Loading : DuplicateReviewUiState

    data class Ready(val content: DuplicateReviewContent) : DuplicateReviewUiState

    data class Missing(val message: String) : DuplicateReviewUiState
}

class DuplicateReviewViewModel(
    private val transactionId: String,
    private val review: DuplicateReviewUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow<DuplicateReviewUiState>(DuplicateReviewUiState.Loading)
    val uiState: StateFlow<DuplicateReviewUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun markSame(onDone: () -> Unit) = act(onDone) { review.markSame(transactionId) }

    fun markDifferent(onDone: () -> Unit) = act(onDone) { review.markDifferent(transactionId) }

    private fun act(onDone: () -> Unit, action: suspend () -> DuplicateActionResult) {
        val ready = mutableState.value as? DuplicateReviewUiState.Ready ?: return
        if (ready.content.busy) return
        mutableState.value = DuplicateReviewUiState.Ready(ready.content.copy(busy = true, message = null))
        viewModelScope.launch {
            when (val result = action()) {
                DuplicateActionResult.Done -> onDone()
                is DuplicateActionResult.Rejected -> {
                    val current = mutableState.value as? DuplicateReviewUiState.Ready ?: return@launch
                    mutableState.value = DuplicateReviewUiState.Ready(
                        current.content.copy(busy = false, message = result.reason),
                    )
                }
            }
        }
    }

    private suspend fun load() {
        mutableState.value = try {
            val comparison = review.load(transactionId)
            if (comparison == null) {
                DuplicateReviewUiState.Missing("没有找到可对照的两笔记录")
            } else {
                DuplicateReviewUiState.Ready(comparison.toContent())
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (thrown: Throwable) {
            DuplicateReviewUiState.Missing(thrown.message ?: "加载失败")
        }
    }

    private fun DuplicateComparison.toContent(): DuplicateReviewContent {
        val zone = DeviceZone.current()
        return DuplicateReviewContent(
            fresh = fresh.toUi("新记录", zone),
            existing = existing.toUi("原记录", zone),
        )
    }

    private fun DuplicateSide.toUi(heading: String, zone: java.time.ZoneId): DuplicateSideUi =
        DuplicateSideUi(
            heading = heading,
            merchant = merchant?.takeIf { it.isNotBlank() } ?: "未知",
            amount = HomeViewModel.formatMoney(
                amountMinor = amountMinor,
                sign = AmountSign.NONE,
                withCurrencySymbol = true,
            ),
            direction = HomeViewModel.typeTextOf(type),
            time = TIME_FORMAT.format(Instant.ofEpochMilli(transactionTime).atZone(zone)),
            source = CaptureSourceText.of(source),
        )

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    }
}
