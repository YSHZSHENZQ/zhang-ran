/**
 * 无障碍采集诊断（`M6` 阶段 1）。
 *
 * 只记录状态、包名、节点数、耗时、是否截断；**不记录任何页面文本/截图**。
 * 阶段 1 仅走 logcat，不写 `capture_logs`（避免伪造上游 payloadVersion）。
 */
package com.zhangran.app.capture.accessibility

import android.util.Log

internal object AccessibilityDiagnostics {

    const val TAG: String = "ZhangRanAccessibility"

    fun log(
        status: String,
        packageName: String? = null,
        nodes: Int = 0,
        elapsedMs: Long = 0,
        truncated: Boolean = false,
    ) {
        val safePackage = packageName?.take(MAX_PACKAGE_CHARS)
        Log.d(
            TAG,
            "status=$status pkg=$safePackage nodes=$nodes elapsedMs=$elapsedMs truncated=$truncated",
        )
    }

    private const val MAX_PACKAGE_CHARS = 64
}
