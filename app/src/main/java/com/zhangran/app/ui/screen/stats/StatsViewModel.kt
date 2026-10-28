/**
 * 统计页 ViewModel（`M2e-2` §4.3/§4.4）。
 *
 * 职责：
 * - 按月份取数（`ObserveMonthlyStatsUseCase`），维护"当前查看月"；
 * - 维护趋势的"当前周索引"，并**限制不能到未来周**；
 * - 月份只能选到当前月（未来月不可选）。
 *
 * 边界规则抽在 [StatsWeekRules]（纯函数），便于 JVM 单测证明"不能到未来"。
 */
package com.zhangran.app.ui.screen.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.domain.model.WeekTrend
import com.zhangran.app.domain.usecase.ObserveMonthlyStatsUseCase
import com.zhangran.app.domain.usecase.StatsMonth
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 切周/选月的边界规则（`M2e-2` A8/A10/A20）。
 *
 * 周索引从 0 开始；当前月允许的最大周 = **包含今天的那一周**（未来周不可达）。
 */
internal object StatsWeekRules {

    /** 允许滑到的最大周索引。 */
    fun maxWeekIndex(
        weeks: List<WeekTrend>,
        isCurrentMonth: Boolean,
        todayDayOfMonth: Int,
    ): Int {
        if (weeks.isEmpty()) return 0
        if (!isCurrentMonth) return weeks.lastIndex
        val containingToday = weeks.indexOfFirst { week ->
            week.points.any { it.inMonth && it.dayOfMonth == todayDayOfMonth }
        }
        return if (containingToday >= 0) containingToday else weeks.lastIndex
    }

    /**
     * 初始周：当前月 = 包含今天的那一周；历史月 = 该月第 1 周（§1.4 裁定）。
     */
    fun initialWeekIndex(
        weeks: List<WeekTrend>,
        isCurrentMonth: Boolean,
        todayDayOfMonth: Int,
    ): Int = if (isCurrentMonth) {
        maxWeekIndex(weeks, isCurrentMonth = true, todayDayOfMonth = todayDayOfMonth)
    } else {
        0
    }

    fun canGoPrevious(weekIndex: Int): Boolean = weekIndex > 0

    fun canGoNext(weekIndex: Int, maxWeekIndex: Int): Boolean = weekIndex < maxWeekIndex

    /** 月份能否选择：`YYYY-MM` 字典序即时间序，未来月不可选。 */
    fun isMonthSelectable(candidatePeriodKey: String, currentPeriodKey: String): Boolean =
        candidatePeriodKey <= currentPeriodKey
}

/**
 * @param observeStats 月度统计读取
 * @param clock 时间源（"今天"用于本周定位）
 */
class StatsViewModel @Inject constructor(
    private val observeStats: ObserveMonthlyStatsUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<StatsUiState>(StatsUiState.Loading)
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<StatsEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<StatsEvent> = _events.asSharedFlow()

    /** 当前查看月（`null` = 当前月）；重试时沿用它。 */
    private var viewingPeriodKey: String? = null

    init {
        load()
    }

    /** 加载指定月份（`null` = 当前月）。 */
    fun load(periodKey: String? = viewingPeriodKey) {
        viewModelScope.launch {
            _uiState.value = StatsUiState.Loading
            runCatching {
                val stats = observeStats(periodKey)
                val months = observeStats.availableMonths()
                stats to months
            }.onSuccess { (stats, months) ->
                viewingPeriodKey = stats.periodKey
                if (!stats.hasData) {
                    _uiState.value = StatsUiState.Empty(periodKey = stats.periodKey, months = months)
                    return@onSuccess
                }
                val isCurrentMonth = stats.periodKey == observeStats.currentPeriodKey()
                val today = Instant.ofEpochMilli(clock.millis())
                    .atZone(ZoneId.systemDefault())
                    .dayOfMonth
                val maxWeekIndex = StatsWeekRules.maxWeekIndex(
                    weeks = stats.weeks,
                    isCurrentMonth = isCurrentMonth,
                    todayDayOfMonth = today,
                )
                _uiState.value = StatsUiState.Content(
                    stats = stats,
                    months = months,
                    weekIndex = StatsWeekRules.initialWeekIndex(
                        weeks = stats.weeks,
                        isCurrentMonth = isCurrentMonth,
                        todayDayOfMonth = today,
                    ),
                    maxWeekIndex = maxWeekIndex,
                    isCurrentMonth = isCurrentMonth,
                )
            }.onFailure {
                _uiState.value = StatsUiState.Error(it.message ?: "读取统计失败")
            }
        }
    }

    fun refresh() = load(viewingPeriodKey)

    /** 选择历史月份（未来月被忽略）。 */
    fun selectMonth(month: StatsMonth) {
        if (!StatsWeekRules.isMonthSelectable(month.periodKey, observeStats.currentPeriodKey())) return
        load(month.periodKey)
    }

    fun previousWeek() = shiftWeek(delta = -1)

    fun nextWeek() = shiftWeek(delta = 1)

    /** 横向滑动选择周（越界会被夹到允许范围）。 */
    fun selectWeek(index: Int) {
        val content = _uiState.value as? StatsUiState.Content ?: return
        val clamped = index.coerceIn(0, content.maxWeekIndex)
        if (clamped != content.weekIndex) {
            _uiState.value = content.copy(weekIndex = clamped)
        }
    }

    private fun shiftWeek(delta: Int) {
        val content = _uiState.value as? StatsUiState.Content ?: return
        val target = (content.weekIndex + delta).coerceIn(0, content.maxWeekIndex)
        if (target != content.weekIndex) {
            _uiState.value = content.copy(weekIndex = target)
        }
    }
}
