/**
 * 采集事件提交入口（应用级作用域）。
 *
 * 依据 `T2b` §4.5.1：接收入口必须把提交交给**应用级**作用域，避免 `finish()` 之后
 * `lifecycleScope` 被取消导致事件静默丢失。
 *
 * 职责边界：
 * - 对外只暴露**非挂起**的 [submit]；调用方（Activity）不需要协程能力；
 * - **不做**任何解析 / 落库 / 业务判断——真正的处理在 [CaptureEventSource] 实现里；
 * - 失败必须**显式留痕**（取消之外的异常要打日志），不得静默吞掉。
 */
package com.zhangran.app.capture

import android.util.Log
import com.zhangran.app.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 应用级提交器。
 *
 * @param eventSource 事件处理入口（`CaptureProcessor`）
 * @param scope 应用级作用域（进程生命周期）
 */
@Singleton
class CaptureSubmitter @Inject constructor(
    private val eventSource: CaptureEventSource,
    @ApplicationScope private val scope: CoroutineScope,
) {

    /**
     * 提交事件（非挂起，立即返回）。
     *
     * 调用方（接收入口）可以在调用后立刻 `finish()`：
     * 工作在应用级作用域内继续执行，不随 Activity 销毁而取消。
     */
    fun submit(event: ExternalCaptureEvent) {
        scope.launch {
            try {
                eventSource.submit(event)
            } catch (cancellation: CancellationException) {
                // 应用级作用域被取消（进程收尾）：如实上报取消，不伪装成成功
                Log.w(TAG, "采集提交被取消：captureId=${event.captureId}")
                throw cancellation
            } catch (t: Throwable) {
                // 显式留痕：不得静默吞掉（07 §18 故障隔离）
                Log.w(
                    TAG,
                    "采集提交失败：captureId=${event.captureId} ${t.javaClass.simpleName}",
                )
            }
        }
    }

    private companion object {
        private const val TAG = "ZhangRanCapture"
    }
}
