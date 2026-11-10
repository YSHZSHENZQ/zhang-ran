/**
 * debug 专用的页面快照导出（M6-3 / AC-1）。
 *
 * 白名单窗口采样成功后，把脱敏后的节点文本写到应用私有目录，供日后用 adb 拉出并冻成 fixture。
 * 不新增产品页，不写交易，不改变 Pending / Confirmed。
 * [enabled] 为 false（release）时什么都不写。
 *
 * 未通过支付结果解析的页面也导出：当前规则认不出的真机结果页，仍然要能被收成 fixture。
 */
package com.zhangran.app.capture.accessibility

import java.io.File
import java.io.IOException

class AccessibilitySnapshotExporter(
    private val directory: File,
    private val enabled: Boolean,
    private val maxKept: Int = MAX_KEPT,
) {

    /**
     * @return 写入的快照文件名（不含目录）。未启用或写盘失败时返回 null。
     */
    fun export(snapshot: AccessibilityPageSnapshot): String? {
        if (!enabled) return null
        return try {
            if (!directory.exists() && !directory.mkdirs()) return null
            val encoded = AccessibilitySnapshotFidelity.encode(snapshot, fixtureId = null)
            val fileName = fileNameOf(snapshot)
            File(directory, fileName).writeText(encoded)
            File(directory, LATEST_JSON).writeText(encoded)
            File(directory, LATEST_TXT).writeText(indexOf(snapshot, fileName))
            prune()
            fileName
        } catch (_: IOException) {
            null
        }
    }

    private fun fileNameOf(snapshot: AccessibilityPageSnapshot): String {
        val safePackage = snapshot.packageName
            .replace(UNSAFE_FILE_CHAR, "_")
            .take(MAX_PACKAGE_CHARS)
            .ifEmpty { "unknown" }
        return "${snapshot.capturedAt}-${safePackage}-w${snapshot.windowId}.json"
    }

    /** 索引只含定位信息，不含节点文本。 */
    private fun indexOf(snapshot: AccessibilityPageSnapshot, fileName: String): String = buildString {
        append("file=").append(fileName).append('\n')
        append("capturedAt=").append(snapshot.capturedAt).append('\n')
        append("packageName=").append(snapshot.packageName).append('\n')
        append("windowId=").append(snapshot.windowId).append('\n')
        append("nodes=").append(snapshot.nodes.size).append('\n')
        append("truncated=").append(snapshot.truncated).append('\n')
    }

    private fun prune() {
        val snapshots = directory.listFiles { file ->
            file.isFile && file.name.endsWith(".json") && file.name != LATEST_JSON
        } ?: return
        val overflow = snapshots.size - maxKept
        if (overflow <= 0) return
        snapshots.sortedBy { it.name }.take(overflow).forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY: String = "m6-3-snapshots"
        const val LATEST_JSON: String = "latest.json"
        const val LATEST_TXT: String = "latest.txt"

        private const val MAX_KEPT = 20
        private const val MAX_PACKAGE_CHARS = 64
        private val UNSAFE_FILE_CHAR = Regex("[^A-Za-z0-9._-]")
    }
}
