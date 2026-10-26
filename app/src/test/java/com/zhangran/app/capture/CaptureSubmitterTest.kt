/**
 * 应用级提交作用域测试（`T2b` §4.5.1 验收 1；U0 仅调整类型引用，4 个用例全部保留）。
 *
 * 证明的核心命题：**调用方作用域被取消后，提交仍会完成**。
 *
 * 这正是 T4 遗留缺陷的反例：原来用 `lifecycleScope.launch { submit(...) }`，
 * 页面结束触发作用域取消，在途的 Room 落库（挂起点）会被取消，
 * 而 `runCatching` **捕不到** `CancellationException` → 事件静默丢失。
 */
package com.zhangran.app.capture

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSubmitterTest {

    /**
     * 记录被提交的事件，并可选地用 [done] 通知"处理已完成"。
     *
     * @param delayMillis 模拟真实实现里的挂起点（Room 落库）
     * @param done 每次处理完成后置位（用于断言"真的跑完了"，而不是睡够时间就断言）
     */
    private class RecordingEventSource(
        private val delayMillis: Long = 0,
        private val done: CompletableDeferred<String>? = null,
        private val failOnId: String? = null,
    ) : CollectorEventSource {
        val submitted = mutableListOf<String>()

        override fun newOutput(output: CollectorOutput): CollectorEvent =
            CollectorEvent(captureId = "built", output = output)

        override suspend fun submit(event: CollectorEvent): CaptureResult {
            if (delayMillis > 0) delay(delayMillis)
            if (event.captureId == failOnId) throw IllegalStateException("模拟落库失败")
            submitted += event.captureId
            done?.complete(event.captureId)
            return CaptureResult(captureId = event.captureId, outcome = CaptureOutcome.ACCEPTED)
        }
    }

    private companion object {
        fun eventOf(id: String) = CollectorEvent(
            captureId = id,
            output = CollectorOutput(
                collectorId = "collector-$id",
                interfaceVersion = COLLECTOR_INTERFACE_VERSION,
                sourceType = CollectorSourceType.NOTICE,
                rawPayloadJson = """{"money":1.0}""",
                capturedAt = 0L,
            ),
        )
    }

    // ---------- A13 的核心断言 ----------

    @Test
    fun `调用方作用域已取消时提交仍完成`() = runBlocking {
        val done = CompletableDeferred<String>()
        val source = RecordingEventSource(delayMillis = 100, done = done)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitter = CaptureSubmitter(eventSource = source, scope = appScope)

        // 调用方作用域：等价于被销毁页面的作用域
        val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            callerScope.launch { submitter.submit(eventOf("evt-1")) }
            // 立刻取消调用方作用域：等价于页面结束
            callerScope.cancel()

            // 若提交仍绑定调用方作用域，这里会超时（提交被取消）
            val processed = withTimeout(5_000) { done.await() }
            assertEquals("evt-1", processed)
        } finally {
            appScope.cancel()
        }

        assertTrue("应用级作用域应已完成落库，实际=$($source.submitted)", source.submitted.contains("evt-1"))
    }

    @Test
    fun `提交为非挂起调用且立即返回`() {
        val source = RecordingEventSource(delayMillis = 200)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitter = CaptureSubmitter(eventSource = source, scope = appScope)

        val start = System.nanoTime()
        submitter.submit(eventOf("evt-2")) // 非挂起：不等待处理完成
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        appScope.cancel()
        assertTrue("submit 必须立即返回（非挂起），实际耗时 ${elapsedMs}ms", elapsedMs < 100)
    }

    // ---------- SupervisorJob 语义 ----------

    @Test
    fun `单个提交失败不影响其它提交`() = runBlocking {
        val done = CompletableDeferred<String>()
        val source = RecordingEventSource(failOnId = "bad", done = done)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitter = CaptureSubmitter(eventSource = source, scope = appScope)

        try {
            submitter.submit(eventOf("bad")) // 抛异常
            submitter.submit(eventOf("good")) // 仍应完成

            val processed = withTimeout(5_000) { done.await() }
            assertEquals("good", processed)
            assertEquals(listOf("good"), source.submitted)
        } finally {
            appScope.cancel()
        }
    }

    @Test
    fun `提交不依赖调用方协程是否仍在运行`() = runBlocking {
        val done = CompletableDeferred<String>()
        val source = RecordingEventSource(delayMillis = 60, done = done)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitter = CaptureSubmitter(eventSource = source, scope = appScope)

        try {
            // 调用方作用域在提交前就已经取消
            val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            callerScope.cancel()

            submitter.submit(eventOf("evt-3"))
            assertEquals("evt-3", withTimeout(5_000) { done.await() })
        } finally {
            appScope.cancel()
        }
    }
}
