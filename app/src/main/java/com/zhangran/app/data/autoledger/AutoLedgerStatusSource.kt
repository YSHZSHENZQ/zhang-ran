/**
 * 把开关、权限和上次捕获时间合成一页能直接画的状态。
 *
 * 疑似失效只改这里的文案，不新建 Pending。
 */
package com.zhangran.app.data.autoledger

import com.zhangran.app.data.device.DevicePermissionProbe
import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.domain.service.AutoLedgerAvailability
import com.zhangran.app.domain.service.AutoLedgerHealth
import com.zhangran.app.domain.service.AutoLedgerHealthInput
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

data class AutoLedgerSnapshot(
    val masterEnabled: Boolean,
    val accessibilityGranted: Boolean,
    val batteryOptimizationIgnored: Boolean,
    val onboardCompleted: Boolean,
    val lastCaptureAtMillis: Long?,
    val availability: AutoLedgerAvailability,
    val wechatListening: Boolean,
    val alipayListening: Boolean,
) {
    val label: String = AutoLedgerHealth.label(availability)
}

@Singleton
class AutoLedgerStatusSource @Inject constructor(
    settingsRepository: SettingsRepository,
    health: CaptureHealthStore,
    private val probe: DevicePermissionProbe,
    private val clock: Clock,
) {

    private val permissionTick = MutableStateFlow(0)

    /** 从系统设置返回后调用，重新读无障碍和电池优化。 */
    fun refreshPermissions() {
        permissionTick.value += 1
    }

    val snapshots: Flow<AutoLedgerSnapshot> = combine(
        settingsRepository.settings,
        health.state,
        permissionTick,
    ) { settings, captureHealth, _ ->
        val accessibilityGranted = probe.accessibilityGranted()
        val listening = settings.autoLedgerEnabled && accessibilityGranted
        val availability = AutoLedgerHealth.resolve(
            AutoLedgerHealthInput(
                masterEnabled = settings.autoLedgerEnabled,
                accessibilityGranted = accessibilityGranted,
                lastCaptureAtMillis = captureHealth.lastCaptureAtMillis,
                watchStartedAtMillis = captureHealth.watchStartedAtMillis,
                nowMillis = clock.millis(),
            ),
        )
        AutoLedgerSnapshot(
            masterEnabled = settings.autoLedgerEnabled,
            accessibilityGranted = accessibilityGranted,
            batteryOptimizationIgnored = probe.batteryOptimizationIgnored(),
            onboardCompleted = settings.onboardCompleted,
            lastCaptureAtMillis = captureHealth.lastCaptureAtMillis,
            availability = availability,
            wechatListening = listening,
            alipayListening = listening,
        )
    }
}
