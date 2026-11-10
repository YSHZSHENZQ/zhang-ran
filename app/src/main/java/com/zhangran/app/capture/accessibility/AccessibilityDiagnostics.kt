/**
 * 无障碍采集诊断（`M6` 阶段 1，M6-3 只追加调试文件名）。
 *
 * 只记录状态、包名、节点数、耗时、是否截断、调试快照文件名；
 * **不记录任何页面文本/截图**。页面文本只进 debug 快照文件（见 [AccessibilitySnapshotExporter]）。
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
        capturedAt: Long = 0,
        fileName: String? = null,
        detail: String? = null,
    ) {
        val safePackage = packageName?.take(MAX_PACKAGE_CHARS)
        val safeFile = fileName?.take(MAX_FILE_CHARS)?.replace(WHITESPACE, "_")
        val safeDetail = detail?.take(MAX_DETAIL_CHARS)?.replace(WHITESPACE, "_")
        val filePart = if (safeFile.isNullOrEmpty()) "" else " file=$safeFile"
        val detailPart = if (safeDetail.isNullOrEmpty()) "" else " detail=$safeDetail"
        Log.d(
            TAG,
            "status=$status pkg=$safePackage nodes=$nodes elapsedMs=$elapsedMs " +
                "truncated=$truncated capturedAt=$capturedAt$filePart$detailPart",
        )
    }

    private const val MAX_PACKAGE_CHARS = 64
    private const val MAX_FILE_CHARS = 128
    private const val MAX_DETAIL_CHARS = 64
    private val WHITESPACE = Regex("\\s+")
}
