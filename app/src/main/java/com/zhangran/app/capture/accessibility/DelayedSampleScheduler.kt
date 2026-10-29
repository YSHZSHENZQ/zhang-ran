/**
 * 延迟采样调度（`M6` 阶段 1；参考钱迹“结果页二次渲染”的做法，参数为实验值）。
 *
 * 同一窗口在短时间内的多个事件只保留**最后一次**采样，避免任务堆积；
 * 阶段 1 默认 300ms，后续按真机数据调参。
 */
package com.zhangran.app.capture.accessibility

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DelayedSampleScheduler(
    private val scope: CoroutineScope,
    private val delayMillis: Long = DEFAULT_DELAY_MILLIS,
) {

    private val jobs = LinkedHashMap<String, Job>()
    private val lock = Any()

    /**
     * 安排一次延迟采样；同 key 的旧任务会被取消。
     */
    fun schedule(key: String, block: suspend () -> Unit) {
        synchronized(lock) {
            jobs.remove(key)?.cancel()
            jobs[key] = scope.launch {
                val self = coroutineContext[Job]
                try {
                    delay(delayMillis)
                    block()
                } finally {
                    // 仅当自己仍是当前任务时才移除；避免旧任务的 finally 误删新任务
                    synchronized(lock) {
                        if (jobs[key] === self) jobs.remove(key)
                    }
                }
            }
        }
    }

    /** 服务销毁/关闭时取消所有待采样任务。 */
    fun cancelAll() {
        synchronized(lock) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
        }
    }

    internal fun pendingCount(): Int = synchronized(lock) { jobs.size }

    private companion object {
        const val DEFAULT_DELAY_MILLIS = 300L
    }
}
