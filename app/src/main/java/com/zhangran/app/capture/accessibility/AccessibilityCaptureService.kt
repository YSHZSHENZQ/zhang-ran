/**
 * 无障碍采集服务（M6 阶段 1 采样 + M6-2 待确认竖切 + M6-3 debug 快照导出）。
 *
 * 白名单支付结果页：延迟采样后交给 [AccessibilityCapturePipeline]，只写待确认。
 * 非白名单、以及不像支付结果的页面：不落业务候选。
 * 服务绑定时写一条连通诊断（状态 + 时间戳），不写交易。
 * debug 包额外把白名单采样写成私有目录里的脱敏 JSON，供 adb 拉出后冻 fixture；release 不写。
 */
package com.zhangran.app.capture.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.zhangran.app.BuildConfig
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
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
    private val exporter by lazy {
        AccessibilitySnapshotExporter(
            directory = File(filesDir, AccessibilitySnapshotExporter.DIRECTORY),
            enabled = BuildConfig.DEBUG,
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val capturedAt = connectivity.recordConnected()
        AccessibilityDiagnostics.log(status = "SERVICE_CONNECTED", capturedAt = capturedAt)
        AccessibilityDiagnostics.log(status = "BUILD", detail = BuildConfig.GIT_SHA)
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
                    exportDebugSnapshot(result.snapshot)
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

    /**
     * debug：无论当前解析是否认为这是支付结果页，都导出脱敏快照。
     * 解析门槛只决定要不要进待确认，不决定这份取证文件在不在。
     */
    private fun exportDebugSnapshot(snapshot: AccessibilityPageSnapshot) {
        if (!BuildConfig.DEBUG) return
        val fileName = exporter.export(snapshot)
        if (fileName == null) {
            AccessibilityDiagnostics.log(
                status = "SNAPSHOT_EXPORT_FAILED",
                packageName = snapshot.packageName,
                nodes = snapshot.nodes.size,
                truncated = snapshot.truncated,
                capturedAt = snapshot.capturedAt,
            )
            return
        }
        AccessibilityDiagnostics.log(
            status = "SNAPSHOT_EXPORTED",
            packageName = snapshot.packageName,
            nodes = snapshot.nodes.size,
            truncated = snapshot.truncated,
            capturedAt = snapshot.capturedAt,
            fileName = fileName,
        )
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
