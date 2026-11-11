/**
 * 「我的」页的 UI 状态与展示映射（`M4c` §4.2；内容项 SSOT：`docs/06` §4.5）。
 *
 * 页面只展示设置与入口；数据来自 `MineViewModel`（设置 Flow + 待确认数量）。
 */
package com.zhangran.app.ui.screen.mine

import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.domain.service.AutoLedgerAvailability
import com.zhangran.app.domain.service.AutoLedgerHealth

/** 「我的」页状态（三态）。 */
sealed interface MineUiState {

    data object Loading : MineUiState

    /**
     * @property settings 主题与阈值
     * @property pendingCount 待确认记录数（`PENDING`；当前无采集核心，通常为 0）
     */
    data class Content(
        val settings: AppSettings,
        val pendingCount: Int,
        val availability: AutoLedgerAvailability = AutoLedgerAvailability.UNAVAILABLE,
        val accessibilityGranted: Boolean = false,
    ) : MineUiState {

        val statusLabel: String = AutoLedgerHealth.label(availability)
    }

    data class Error(val message: String) : MineUiState
}

/** 一次性事件（保存失败等）。 */
sealed interface MineEvent {
    data class Message(val text: String) : MineEvent
}

/** 主题模式展示名。 */
fun themeModeLabelOf(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

/** 主题当前值的副文案。 */
fun themeModeDescriptionOf(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统的明暗设置"
    ThemeMode.LIGHT -> "始终使用浅色霓虹玻璃风格"
    ThemeMode.DARK -> "始终使用深色霓虹玻璃风格"
}

/** 待确认数量文案（`06` §4.5）。 */
fun pendingSummaryText(count: Int): String =
    if (count <= 0) "暂无待确认记录" else "$count 笔待处理"

/** 权限行副文案。点这一行进入权限引导。 */
fun permissionStatusText(accessibilityGranted: Boolean): String =
    if (accessibilityGranted) "无障碍已授权" else "无障碍未授权"
