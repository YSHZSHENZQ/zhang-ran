/**
 * 打开或关闭自动记账总开关，并记下这次开始监听的时间。
 *
 * 关掉时清掉「开始监听」的时间，疑似失效提示随之消失。上次捕获时间保留。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.data.settings.SettingsRepository
import java.time.Clock
import javax.inject.Inject

class SetAutoLedgerEnabledUseCase @Inject constructor(
    private val settings: SettingsRepository,
    private val health: CaptureHealthStore,
    private val clock: Clock,
) {

    suspend operator fun invoke(enabled: Boolean) {
        settings.setAutoLedgerEnabled(enabled)
        if (enabled) {
            health.markWatchStarted(clock.millis())
        } else {
            health.clearWatchStarted()
        }
    }
}
