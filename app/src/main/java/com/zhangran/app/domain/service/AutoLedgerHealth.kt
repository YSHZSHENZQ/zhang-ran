/**
 * 自动记账能不能用（`docs/02` §21、§22）。
 *
 * 只根据开关、无障碍和上次捕获时间计算，不写账，也不因此新建 Pending。
 */
package com.zhangran.app.domain.service

enum class AutoLedgerAvailability {
    /** 开关开，且无障碍已授权，最近 24 小时内有捕获或尚未满 24 小时。 */
    AVAILABLE,

    /** 开关关，或无障碍未授权 / 服务被关。 */
    UNAVAILABLE,

    /** 开关开、无障碍已授权，但连续 24 小时没有任何捕获。 */
    SUSPECTED_STALE,
}

data class AutoLedgerHealthInput(
    val masterEnabled: Boolean,
    val accessibilityGranted: Boolean,
    /** 最近一次真正写入捕获日志的时间。没有过捕获时为 null。 */
    val lastCaptureAtMillis: Long?,
    /** 这次把总开关打开的时间。没有打开过时为 null。 */
    val watchStartedAtMillis: Long?,
    val nowMillis: Long,
)

object AutoLedgerHealth {

    const val STALE_AFTER_MILLIS: Long = 24L * 60L * 60L * 1000L

    fun resolve(input: AutoLedgerHealthInput): AutoLedgerAvailability {
        if (!input.masterEnabled || !input.accessibilityGranted) {
            return AutoLedgerAvailability.UNAVAILABLE
        }
        val silenceSince = input.lastCaptureAtMillis ?: input.watchStartedAtMillis
        if (silenceSince != null && input.nowMillis - silenceSince >= STALE_AFTER_MILLIS) {
            return AutoLedgerAvailability.SUSPECTED_STALE
        }
        return AutoLedgerAvailability.AVAILABLE
    }

    fun label(availability: AutoLedgerAvailability): String = when (availability) {
        AutoLedgerAvailability.AVAILABLE -> "可用"
        AutoLedgerAvailability.UNAVAILABLE -> "不可用"
        AutoLedgerAvailability.SUSPECTED_STALE -> "疑似失效"
    }
}
