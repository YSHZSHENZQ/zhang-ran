/**
 * 最小无障碍采集服务（`M6` 阶段 1）。
 *
 * 只做四件事：
 * 1. 过滤白名单包名与两类窗口事件；
 * 2. 300ms 延迟采样（同窗口合并，取消旧任务）；
 * 3. 读取节点文本快照（仅内存，限深/限量/限文本）；
 * 4. 输出脱敏诊断（无页面文本、无截图、无磁盘/数据库写入）。
 *
 * **不解析、不入账、不建 Pending、不调用 Decision/Dedup**——这些属阶段 2/3。
 */
package com.zhangran.app.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

class AccessibilityCaptureService : AccessibilityService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reader = NodeTreeReader()
    private val scheduler = DelayedSampleScheduler(scope)

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityDiagnostics.log(status = "SERVICE_CONNECTED")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        if (packageName !in AccessibilityTargets.packages) {
            AccessibilityDiagnostics.log(status = "WHITELIST_IGNORED", packageName = packageName)
            return
        }

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        scheduleSample(packageName = packageName, windowId = event.windowId)
    }

    private fun scheduleSample(packageName: String, windowId: Int) {
        val windowKey = "$packageName:$windowId"
        scheduler.schedule(windowKey) {
            val startedAt = System.currentTimeMillis()
            val capturedAt = startedAt
            val result = withContext(Dispatchers.Main) {
                reader.read(
                    root = rootInActiveWindow,
                    packageName = packageName,
                    windowId = windowId,
                    capturedAt = capturedAt,
                )
            }
            val elapsed = System.currentTimeMillis() - startedAt
            when (result) {
                is NodeReadResult.WindowUnavailable ->
                    AccessibilityDiagnostics.log(
                        status = "WINDOW_NULL",
                        packageName = packageName,
                        elapsedMs = elapsed,
                    )

                is NodeReadResult.Empty ->
                    AccessibilityDiagnostics.log(
                        status = "NODE_EMPTY",
                        packageName = packageName,
                        elapsedMs = elapsed,
                    )

                is NodeReadResult.Success ->
                    AccessibilityDiagnostics.log(
                        status = if (result.snapshot.truncated) "SNAPSHOT_TRUNCATED" else "SNAPSHOT_OK",
                        packageName = packageName,
                        nodes = result.snapshot.nodes.size,
                        elapsedMs = elapsed,
                        truncated = result.snapshot.truncated,
                    )
            }
        }
    }

    override fun onInterrupt() {
        AccessibilityDiagnostics.log(status = "SERVICE_INTERRUPTED")
    }

    override fun onDestroy() {
        scheduler.cancelAll()
        scope.cancel()
        AccessibilityDiagnostics.log(status = "SERVICE_DESTROYED")
        super.onDestroy()
    }
}
