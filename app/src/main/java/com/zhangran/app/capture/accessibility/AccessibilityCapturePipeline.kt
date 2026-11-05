/**
 * 把无障碍采样交给应用级作用域（M6-2）。
 *
 * 服务销毁不能取消已经采到的快照落库，因此这里不用服务自己的协程作用域。
 */
package com.zhangran.app.capture.accessibility

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class AccessibilityCapturePipeline(
    private val ingest: AccessibilitySnapshotIngest,
    private val scope: CoroutineScope,
) {

    fun submit(snapshot: AccessibilityPageSnapshot) {
        scope.launch {
            try {
                ingest.ingest(snapshot)
            } catch (cancellation: CancellationException) {
                Log.w(TAG, "无障碍提交被取消")
                throw cancellation
            } catch (thrown: Throwable) {
                Log.w(TAG, "无障碍提交失败 ${thrown.javaClass.simpleName}")
            }
        }
    }

    private companion object {
        const val TAG = "ZhangRanAccessibility"
    }
}
