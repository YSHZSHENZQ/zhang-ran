/**
 * 首页 ViewModel（`docs/04` §16 状态管理、`docs/06` §4.1）。
 *
 * 职责：
 * - 订阅 `ObserveTransactionsUseCase`，把领域展示类型映射为 UI 展示模型
 *   （金额/时间等**格式化只在这里做**，Compose 不参与口径计算）；
 * - 暴露 `UiState`（Loading / Content / Empty / Error）与 `refresh()`。
 *
 * 不依赖采集能力（`docs/02` §22）：本文件不引用 `capture/` 下的任何类型。
 */
package com.zhangran.app.ui.screen.home

import androidx.lifecycle.ViewModel
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import com.zhangran.app.domain.usecase.TransactionListEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 首页 UI 状态（`docs/06` §5 的页面统一状态）。
 */
sealed interface HomeUiState {

    /** 首次加载中。 */
    data object Loading : HomeUiState

    /** 有数据。 */
    data class Content(val items: List<TransactionListItem>) : HomeUiState

    /** 加载完成但没有任何记录。 */
    data object Empty : HomeUiState

    /** 加载失败（可重试）。 */
    data class Error(val message: String) : HomeUiState
}

/**
 * @param observeTransactions 列表用例
 * @param scope 协程作用域（由 DI 注入应用级作用域，避免绑定界面生命周期）
 */
class HomeViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val scope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** 重新加载（失败重试、写入后刷新都用它）。 */
    fun refresh() {
        scope.launch {
            _uiState.value = HomeUiState.Loading
            runCatching { observeTransactions() }
                .onSuccess { entries ->
                    _uiState.value = if (entries.isEmpty()) {
                        HomeUiState.Empty
                    } else {
                        HomeUiState.Content(entries.map { it.toListItem() })
                    }
                }
                .onFailure { throwable ->
                    _uiState.value = HomeUiState.Error(throwable.message ?: "加载失败")
                }
        }
    }

    /** 领域展示类型 → UI 展示模型（格式化在此完成）。 */
    private fun TransactionListEntry.toListItem(): TransactionListItem = TransactionListItem(
        id = id,
        title = titleOf(merchant, note, type),
        amountText = formatAmount(type, amountMinor),
        typeText = typeTextOf(type),
        timeText = formatTime(transactionTime),
        categoryName = categoryName,
        accountName = accountName,
    )

    companion object {

        /** 主标题：商户 → 备注 → 类型默认文案（`docs/06` §4.1）。 */
        fun titleOf(merchant: String?, note: String?, type: TransactionType): String {
            merchant?.takeIf { it.isNotBlank() }?.let { return it }
            note?.takeIf { it.isNotBlank() }?.let { return it.take(NOTE_SUMMARY_LENGTH) }
            return typeTextOf(type)
        }

        /** 类型文案（`docs/03` §5 的四种类型）。 */
        fun typeTextOf(type: TransactionType): String = when (type) {
            TransactionType.EXPENSE -> "支出"
            TransactionType.INCOME -> "收入"
            TransactionType.TRANSFER -> "转账"
            TransactionType.REFUND -> "退款"
        }

        /**
         * 金额格式化：分 → 元。
         *
         * 用 `BigDecimal` 而**不是** `Double`（`AGENTS.md` §5：禁止用 Double / Float 处理金额）。
         * 前缀符号：支出/转账为 `-`，收入/退款为 `+`（`docs/06` §4.1 的展示约定）。
         */
        fun formatAmount(type: TransactionType, amountMinor: Long): String {
            val yuan = BigDecimal.valueOf(amountMinor, 2).stripTrailingZeros().toPlainString()
            val formatted = addThousandSeparators(yuan)
            val sign = when (type) {
                TransactionType.EXPENSE, TransactionType.TRANSFER -> "-"
                TransactionType.INCOME, TransactionType.REFUND -> "+"
            }
            return "$sign¥$formatted"
        }

        /** 时间格式化：UTC millis → 设备时区的 `yyyy-MM-dd HH:mm`（`docs/03` §5）。 */
        fun formatTime(epochMillis: Long): String =
            TIME_FORMATTER.format(
                Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()),
            )

        /** 来源文案（列表暂不展示，保留给后续任务与测试断言）。 */
        fun sourceTextOf(source: CaptureSource): String = when (source) {
            CaptureSource.MANUAL -> "手工"
            CaptureSource.NOTIFICATION -> "通知"
            CaptureSource.HOOK -> "应用数据"
            CaptureSource.SMS -> "短信"
            CaptureSource.ACCESSIBILITY -> "无障碍"
            CaptureSource.AI -> "AI"
            CaptureSource.OCR -> "OCR"
        }

        /** 备注摘要最多展示的字数（`docs/06` §4.1 列表只显示摘要）。 */
        private const val NOTE_SUMMARY_LENGTH: Int = 20

        private val TIME_FORMATTER: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        /** 整数部分每三位加一个逗号（`12.34` → `12.34`；`1234.5` → `1,234.5`）。 */
        private fun addThousandSeparators(number: String): String {
            val parts = number.split('.', limit = 2)
            val integerPart = parts[0]
            val decimalPart = parts.getOrNull(1)
            val grouped = integerPart.reversed().chunked(3).joinToString(",").reversed()
            return if (decimalPart == null) grouped else "$grouped.$decimalPart"
        }
    }
}
