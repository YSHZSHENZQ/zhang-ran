/**
 * 首页 ViewModel（`docs/04` §16 状态管理、`docs/06` §4.1）。
 *
 * 职责：
 * - 订阅 `ObserveTransactionsUseCase` 与 `ObserveHomeSummaryUseCase`，把领域展示类型映射为
 *   UI 展示模型（金额/时间/分组等**格式化只在这里做**，Compose 不参与口径计算）；
 * - 暴露 `UiState`（Loading / Content / Empty / Error）与 `refresh()`。
 *
 * 响应式（`M2e-1a` §4.7）：`Flow` 收集 + `stateIn + WhileSubscribed(5_000)`；
 * **异常必须在这里被捕获**并转成 `Error`，不得让 Flow 异常冒泡。
 *
 * 日期分组（`M2e-1b` §4.4）：分组逻辑放在本文件的纯函数 [HomeViewModel.groupIntoSections] 中
 * （不在 Composable 里），因此可被 JVM 单测直接断言；分组与"今天/昨天"判定一律使用
 * **设备本地时区**（任务书 §7 R11，与 M2e-1a 的月边界同源）。
 *
 * 金额格式化（`M2e-1b` §4.3 / A7）：**只有一处实现** [HomeViewModel.formatMoney]，
 * 余额卡片与交易行都调用它（千分位 + 固定 2 位小数）。
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
import java.time.LocalDate
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
     * @property sections 按日期分组的交易列表（组间与组内均按 `transactionTime` 倒序）
     * @property summary 本月汇总（消费/收入/结余/待确认数，口径见 `docs/02` §14）
     */
    data class Content(
        val sections: List<HomeSection>,
        val summary: HomeSummary,
    ) : HomeUiState

    /** 加载完成但没有任何记录。 */
    data object Empty : HomeUiState

    /** 加载失败（可重试）。 */
    data class Error(val message: String) : HomeUiState
}

/**
 * 金额符号策略（`M2e-1b` §4.3/§4.4）。
 *
 * - [AUTO]：负数加 `-`，非负不加（用于"本月支出"与"本月结余"）；
 * - [PLUS] / [MINUS]：强制符号（用于交易行的收入/支出方向）。
 */
enum class AmountSign {
    AUTO,
    PLUS,
    MINUS,
    NONE,
}

/**
 * @param observeTransactions 列表用例（响应式，三表 combine）
 * @param observeHomeSummary 本月汇总用例（响应式，交易变化即重算）
 * @param clock 当前时间源（毫秒）。默认系统时间；测试可注入固定"现在"以稳定"今天/昨天"分组
 */
class HomeViewModel(
    private val observeTransactions: ObserveTransactionsUseCase,
    private val observeHomeSummary: ObserveHomeSummaryUseCase,
    private val clock: () -> Long = { System.currentTimeMillis() },
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
            ) { entries, summary -> toUiState(entries, summary, clock()) }
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

    /** 空列表 → `Empty`；否则按日期分组映射为 `Content`（判定条件与既有实现一致）。 */
    private fun toUiState(
        entries: List<TransactionListEntry>,
        summary: HomeSummary,
        nowEpochMillis: Long,
    ): HomeUiState = if (entries.isEmpty()) {
        HomeUiState.Empty
    } else {
        HomeUiState.Content(
            sections = groupIntoSections(
                entries = entries,
                zoneId = ZoneId.systemDefault().id,
                nowEpochMillis = nowEpochMillis,
            ),
            summary = summary,
        )
    }

    companion object {

        /** 无人订阅后保留上游收集的时长（`M2b0` §4.3：避免退到后台立即断开又重订阅而闪动）。 */
        private const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L

        /** 备注摘要最多展示的字数（`docs/06` §4.1 列表只显示摘要）。 */
        private const val NOTE_SUMMARY_LENGTH: Int = 20

        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * 按**本地日期**把交易分组（`M2e-1b` §4.4）。
         *
         * 规则：
         * - 同一天 = 今天 → `今天`；= 昨天 → `昨天`；其他 → `M月D日`，跨年 → `YYYY年M月D日`；
         * - 组间按日期**倒序**，组内按 `transactionTime` **倒序**；
         * - **空分组不显示**（只对实际存在的日期建组）。
         *
         * 时区（任务书 §7 R11）：一律用传入的 `zoneId`（生产为设备本地时区）判定"这一天是几号"，
         * **不得**用 UTC 划分日期，否则 UTC+8 的清晨记录会被算到前一天。
         *
         * @param entries 交易列表（顺序不限，本函数会排序）
         * @param zoneId 判定日期与时区所用的 IANA id（生产：`ZoneId.systemDefault().id`）
         * @param nowEpochMillis 判定"今天/昨天"用的当前时刻（注入以便测试，避免 `System.currentTimeMillis()`）
         */
        fun groupIntoSections(
            entries: List<TransactionListEntry>,
            zoneId: String,
            nowEpochMillis: Long,
        ): List<HomeSection> {
            if (entries.isEmpty()) return emptyList()
            val zone = ZoneId.of(zoneId)
            val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zone).toLocalDate()
            val yesterday = today.minusDays(1)

            return entries
                .sortedByDescending { it.transactionTime }
                .groupBy { Instant.ofEpochMilli(it.transactionTime).atZone(zone).toLocalDate() }
                .entries
                .sortedByDescending { it.key }
                .map { (date, rows) ->
                    HomeSection(
                        label = sectionLabelOf(date = date, today = today, yesterday = yesterday),
                        items = rows.map { it.toListItem(zone) },
                    )
                }
        }

        /** 分组标题（今天 / 昨天 / `M月D日` / 跨年时 `YYYY年M月D日`）。 */
        fun sectionLabelOf(date: LocalDate, today: LocalDate, yesterday: LocalDate): String = when {
            date == today -> "今天"
            date == yesterday -> "昨天"
            date.year == today.year -> "${date.monthValue}月${date.dayOfMonth}日"
            else -> "${date.year}年${date.monthValue}月${date.dayOfMonth}日"
        }

        /**
         * 金额格式化：分 → 元（**唯一实现**，`M2e-1b` A7）。
         *
         * - 固定 **2 位小数** + 千分位（`1234567` → `12,345.67`）；
         * - 用 `BigDecimal` 而**不是** `Double`（`AGENTS.md` §5）；
         * - 符号与货币符号由调用方选择：原型余额卡片的大字带 `¥`，交易行金额不带 `¥`。
         *
         * @param amountMinor 最小货币单位（分），可为负（`M2e-1a` 允许"退款多于消费"的负消费）
         * @param sign 符号策略（见 [AmountSign]）
         * @param withCurrencySymbol 是否加 `¥`
         */
        fun formatMoney(
            amountMinor: Long,
            sign: AmountSign = AmountSign.AUTO,
            withCurrencySymbol: Boolean = false,
        ): String {
            val negative = amountMinor < 0
            val absolute = if (negative) -amountMinor else amountMinor
            val yuan = BigDecimal.valueOf(absolute, 2).setScale(2).toPlainString()
            val signText = when (sign) {
                AmountSign.AUTO -> if (negative) "-" else ""
                AmountSign.PLUS -> "+"
                AmountSign.MINUS -> "-"
                AmountSign.NONE -> ""
            }
            val symbol = if (withCurrencySymbol) "¥" else ""
            return signText + symbol + addThousandSeparators(yuan)
        }

        /** 主标题：商户 → 备注摘要 → 分类名 → 类型默认文案（`M2e-1b` §4.4）。 */
        fun titleOf(
            merchant: String?,
            note: String?,
            categoryName: String?,
            type: TransactionType,
        ): String {
            merchant?.takeIf { it.isNotBlank() }?.let { return it }
            note?.takeIf { it.isNotBlank() }?.let { return it.take(NOTE_SUMMARY_LENGTH) }
            categoryName?.takeIf { it.isNotBlank() }?.let { return it }
            return typeTextOf(type)
        }

        /** 类型文案（`docs/03` §5 的四种类型）。 */
        fun typeTextOf(type: TransactionType): String = when (type) {
            TransactionType.EXPENSE -> "支出"
            TransactionType.INCOME -> "收入"
            TransactionType.TRANSFER -> "转账"
            TransactionType.REFUND -> "退款"
        }

        /** 列表行金额文本（`-38.50` / `+1,200.00`，无货币符号，原型 `.tx-amt`）。 */
        fun rowAmountOf(type: TransactionType, amountMinor: Long): String = formatMoney(
            amountMinor = amountMinor,
            sign = when (type) {
                TransactionType.EXPENSE, TransactionType.TRANSFER -> AmountSign.MINUS
                TransactionType.INCOME, TransactionType.REFUND -> AmountSign.PLUS
            },
            withCurrencySymbol = false,
        )

        /** 行内时间：`HH:mm`（设备/指定时区）。 */
        fun formatTimeOfDay(epochMillis: Long, zone: ZoneId): String =
            TIME_FORMATTER.format(Instant.ofEpochMilli(epochMillis).atZone(zone))

        /** 来源文案（`docs/07` §5 的 7 个枚举 → 中文；MANUAL 依既有实现为"手工"）。 */
        fun sourceTextOf(source: CaptureSource): String = when (source) {
            CaptureSource.MANUAL -> "手工"
            CaptureSource.NOTIFICATION -> "通知"
            CaptureSource.HOOK -> "应用数据"
            CaptureSource.SMS -> "短信"
            CaptureSource.ACCESSIBILITY -> "无障碍"
            CaptureSource.AI -> "AI"
            CaptureSource.OCR -> "OCR"
        }

        /** 领域展示类型 → UI 展示模型（格式化在此完成，Compose 不再做计算）。 */
        private fun TransactionListEntry.toListItem(zone: ZoneId): TransactionListItem {
            val timeText = formatTimeOfDay(transactionTime, zone)
            val typeText = typeTextOf(type)
            return TransactionListItem(
                id = id,
                title = titleOf(merchant, note, categoryName, type),
                amountText = rowAmountOf(type, amountMinor),
                typeText = typeText,
                timeText = timeText,
                categoryName = categoryName,
                categoryIcon = categoryIcon,
                sourceText = sourceTextOf(source),
                type = type,
                // 无障碍朗读文本（`M2e-1b` §4.8）："星巴克咖啡，餐饮，14:23，支出 38.50 元"
                accessibilityText = accessibilityTextOf(
                    title = titleOf(merchant, note, categoryName, type),
                    categoryName = categoryName,
                    timeText = timeText,
                    typeText = typeText,
                    amountMinor = amountMinor,
                ),
            )
        }

        private fun accessibilityTextOf(
            title: String,
            categoryName: String?,
            timeText: String,
            typeText: String,
            amountMinor: Long,
        ): String {
            val amount = formatMoney(amountMinor, AmountSign.NONE, withCurrencySymbol = false)
            val parts = listOfNotNull(
                title,
                categoryName?.takeIf { it.isNotBlank() },
                timeText,
                "$typeText $amount 元",
            )
            return parts.joinToString(separator = "，")
        }

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
