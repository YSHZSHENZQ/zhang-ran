/**
 * 无障碍采集服务（M6 阶段 1 采样 + M6-2 待确认竖切）。
 *
 * 白名单支付结果页：延迟采样后交给 [AccessibilityCapturePipeline]，只写待确认。
 * 非白名单、以及不像支付结果的页面：不落业务候选。
 * 服务绑定时写一条连通诊断（状态 + 时间戳），不写交易。
 */
package com.zhangran.app.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class AccessibilityCaptureService : AccessibilityService() {

    @Inject lateinit var pipeline: AccessibilityCapturePipeline

    @Inject lateinit var connectivity: AccessibilityConnectivityRecorder

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val reader = NodeTreeReader()
    private val scheduler = DelayedSampleScheduler(scope)

    override fun onServiceConnected() {
        super.onServiceConnected()
        val capturedAt = connectivity.recordConnected()
        AccessibilityDiagnostics.log(status = "SERVICE_CONNECTED", capturedAt = capturedAt)
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

                is NodeReadResult.Success -> {
                    val forward = AccessibilitySnapshotParser.shouldForward(result.snapshot)
                    AccessibilityDiagnostics.log(
                        status = when {
                            !forward -> "NOT_PAYMENT_PAGE"
                            result.snapshot.truncated -> "SNAPSHOT_TRUNCATED"
                            else -> "SNAPSHOT_OK"
                        },
                        packageName = packageName,
                        nodes = result.snapshot.nodes.size,
                        elapsedMs = elapsed,
                        truncated = result.snapshot.truncated,
                        capturedAt = capturedAt,
                    )
                    if (forward) pipeline.submit(result.snapshot)
                }
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
