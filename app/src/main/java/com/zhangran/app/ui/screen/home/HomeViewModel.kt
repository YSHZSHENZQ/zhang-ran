/**
 * 首页 ViewModel（`docs/04` §16 状态管理、`docs/06` §4.1）。
 *
 * 职责：
 * - 订阅 `ObserveTransactionsUseCase` 与 `ObserveHomeSummaryUseCase`，把领域展示类型映射为
 *   UI 展示模型（金额/时间等**格式化只在这里做**，Compose 不参与口径计算）；
 * - 暴露 `UiState`（Loading / Content / Empty / Error）与 `refresh()`。
 *
 * 响应式（`M2e-1a` §4.7）：数据获取由 `suspend` 一次性拉取改为 `Flow` 收集，
 * 用 `stateIn + WhileSubscribed(5_000)` 共享，退到后台 5 秒后停止上游查询、回到前台自动恢复；
 * **异常必须在这里被捕获**并转成 `Error`，不得让 Flow 异常冒泡（否则页面崩溃）。
 *
 * 本任务只做数据契约，**不改任何视觉结构**：列表项与四态语义保持不变，
 * 仅在 `Content` 上新增 [HomeUiState.Content.summary] 供 M2e-1b 展示。
 *
 * 不依赖采集能力（`docs/02` §22）：本文件不引用 `capture/` 下的任何类型。
 */
package com.zhangran.app.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.HomeSummary
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.ObserveHomeSummaryUseCase
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import com.zhangran.app.domain.usecase.TransactionListEntry
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/**
 * 首页 UI 状态（`docs/06` §5 的页面统一状态）。
 */
sealed interface HomeUiState {

    /** 首次加载中（上游 Flow 尚未发射）。 */
    data object Loading : HomeUiState

    /**
     * 有数据。
     *
     * @property items 最近交易（按交易时间倒序）
     * @property summary 本月汇总（消费/收入/结余/待确认数，口径见 `docs/02` §14）
     */
    data class Content(
        val items: List<TransactionListItem>,
        val summary: HomeSummary,
    ) : HomeUiState

    /** 加载完成但没有任何记录。 */
    data object Empty : HomeUiState

    /** 加载失败（可重试）。 */
    data class Error(val message: String) : HomeUiState
}

/**
 * @param observeTransactions 列表用例（响应式，三表 combine）
 * @param observeHomeSummary 本月汇总用例（响应式，交易变化即重算）
 */
class HomeViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeHomeSummary: ObserveHomeSummaryUseCase,
) : ViewModel() {

    /** `refresh()` 的触发计数：每次递增都让上游 Flow **重新订阅**（失败重试）。 */
    private val refreshTrigger = MutableStateFlow(0)

    /**
     * 首页状态。
     *
     * 组合方式：
     * - `flatMapLatest`：`refresh()` 递增触发时取消旧收集、重新订阅（否则 `catch` 之后
     *   上游已结束，重试不会生效）；
     * - `onStart` 发 `Loading`：首次发射前与重试时都保持 `Loading`（`docs/06` §5）；
     * - `catch` 捕获**任何**上游异常 → `Error`，异常不冒泡（`M2b0` §4.3）；
     * - `stateIn(WhileSubscribed(5_000))`：无人订阅 5 秒后停止查询，避免后台空转。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<HomeUiState> = refreshTrigger
        .flatMapLatest {
            combine(
                observeTransactions(),
                observeHomeSummary(),
            ) { entries, summary -> toUiState(entries, summary) }
                .catch { throwable ->
                    emit(HomeUiState.Error(throwable.message ?: "加载失败"))
                }
                .onStart { emit(HomeUiState.Loading) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = HomeUiState.Loading,
        )

    /**
     * 重新加载（错误态"重试"用）。
     *
     * 数据已由 `Flow` 驱动，本方法**不再**直接拉取，只负责触发一次重新订阅；
     * 对外语义（"让页面重新加载一次"）保持不变。
     */
    fun refresh() {
        refreshTrigger.value += 1
    }

    /** 空列表 → `Empty`；否则映射为 `Content`（判定条件与既有实现一致）。 */
    private fun toUiState(
        entries: List<TransactionListEntry>,
        summary: HomeSummary,
    ): HomeUiState = if (entries.isEmpty()) {
        HomeUiState.Empty
    } else {
        HomeUiState.Content(
            items = entries.map { it.toListItem() },
            summary = summary,
        )
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

        /** 无人订阅后保留上游收集的时长（`M2b0` §4.3：避免退到后台立即断开又重订阅而闪动）。 */
        private const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L

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
