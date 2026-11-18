/**
 * debug 专用：把「上次捕获时间」拨到现在以前。
 *
 * 不改捕获日志，不新建 Pending。总开关和无障碍都开着时，拨够 24 小时首页和自动记账页会显示「疑似失效」。
 *
 * ```
 * adb shell am broadcast \
 *   -a com.zhangran.app.debug.BACKDATE_LAST_CAPTURE \
 *   --el ageMillis 90000000 \
 *   -n com.zhangran.app/.debug.DebugCaptureHealthReceiver
 * ```
 *
 * `ageMillis` 是上次捕获往回拨的毫秒数，省略时为 24 小时。
 * `watchAgeMillis` 大于等于 0 时，开始监听时间也按同样方式往回拨。
 * 拨完立刻按当前健康度写一条「疑似失效」日志（若确实跨过 24 小时）。只有 debug 包有这个接收器。
 */
package com.zhangran.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhangran.app.data.autoledger.AutoLedgerStaleFlipMonitor
import com.zhangran.app.data.settings.CaptureHealthStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class DebugCaptureHealthReceiver : BroadcastReceiver() {

    @Inject lateinit var health: CaptureHealthStore

    @Inject lateinit var staleFlip: AutoLedgerStaleFlipMonitor

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val ageMillis = intent.getLongExtra(EXTRA_AGE_MILLIS, DEFAULT_AGE_MILLIS)
        val watchAgeMillis = intent.getLongExtra(EXTRA_WATCH_AGE_MILLIS, UNSET_WATCH_AGE)
        val pending = goAsync()
        try {
            runBlocking {
                val now = System.currentTimeMillis()
                health.setLastCaptureAt(now - ageMillis)
                if (watchAgeMillis >= 0L) {
                    health.markWatchStarted(now - watchAgeMillis)
                }
                staleFlip.evaluateNow()
            }
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val ACTION: String = "com.zhangran.app.debug.BACKDATE_LAST_CAPTURE"
        const val EXTRA_AGE_MILLIS: String = "ageMillis"
        const val EXTRA_WATCH_AGE_MILLIS: String = "watchAgeMillis"
        const val UNSET_WATCH_AGE: Long = -1L
        const val DEFAULT_AGE_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
