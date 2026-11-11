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
 * `ageMillis` 是往回拨的毫秒数，省略时为 24 小时。只有 debug 包有这个接收器。
 */
package com.zhangran.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zhangran.app.data.settings.CaptureHealthStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class DebugCaptureHealthReceiver : BroadcastReceiver() {

    @Inject lateinit var health: CaptureHealthStore

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val ageMillis = intent.getLongExtra(EXTRA_AGE_MILLIS, DEFAULT_AGE_MILLIS)
        val pending = goAsync()
        try {
            runBlocking {
                health.setLastCaptureAt(System.currentTimeMillis() - ageMillis)
            }
        } finally {
            pending.finish()
        }
    }

    companion object {
        const val ACTION: String = "com.zhangran.app.debug.BACKDATE_LAST_CAPTURE"
        const val EXTRA_AGE_MILLIS: String = "ageMillis"
        const val DEFAULT_AGE_MILLIS: Long = 24L * 60L * 60L * 1000L
    }
}
