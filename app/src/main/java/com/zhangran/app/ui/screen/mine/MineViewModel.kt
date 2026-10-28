/**
 * 「我的」页 ViewModel（`M4c` §4.2）。
 *
 * - 设置来自 `SettingsRepository.settings`（DataStore `Flow`）→ 主题切换即时反映；
 * - 待确认数量来自 `TransactionRepository.countByState(PENDING)`（`06` §4.5 的要求）；
 * - 只在"设置流 + 数量"都就绪后进入 Content；读取失败进入 Error 并可重试。
 */
package com.zhangran.app.ui.screen.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.domain.model.TransactionState
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param settingsRepository 设置读写（DataStore）
 * @param transactions 待确认数量
 */
class MineViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val transactions: TransactionRepository,
) : ViewModel() {

    private val refreshTrigger = MutableStateFlow(0)

    private val _events = MutableSharedFlow<MineEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<MineEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val pendingCount: Flow<Int> = refreshTrigger.flatMapLatest {
        flow { emit(transactions.countByState(TransactionState.PENDING)) }
    }

    val uiState: StateFlow<MineUiState> = combine<AppSettings, Int, MineUiState>(
        settingsRepository.settings,
        pendingCount,
    ) { settings, count ->
        MineUiState.Content(settings = settings, pendingCount = count)
    }
        .catch { throwable ->
            emit(MineUiState.Error(throwable.message ?: "读取设置失败"))
        }
        .onStart { emit(MineUiState.Loading) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = MineUiState.Loading,
        )

    /** 切换主题（持久化到 DataStore；失败给出提示，不静默）。 */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            runCatching { settingsRepository.setThemeMode(mode) }
                .onFailure { _events.emit(MineEvent.Message("主题保存失败，请重试")) }
        }
    }

    /** 错误态重试 / 刷新待确认数量。 */
    fun refresh() {
        refreshTrigger.value += 1
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L
    }
}
