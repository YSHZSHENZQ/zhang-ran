/**
 * 应用级提交作用域测试（`T2b` §4.5.1 验收 1）。
 *
 * 证明的核心命题：**调用方作用域被取消后，提交仍会完成**。
 *
 * 这正是 T4 遗留缺陷的反例：原来用 `lifecycleScope.launch { submit(...) }`，
 * `finish()` 触发 `onDestroy` 后作用域被取消，在途的 Room 落库（挂起点）会被取消，
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
    ) : CaptureEventSource {
        val submitted = mutableListOf<String>()

        override fun newEvent(
            rawUri: String?,
            sourcePackage: String?,
            listenerConnectedAt: Long?,
            eventTime: Long?,
        ): ExternalCaptureEvent = eventOf("built")

        override suspend fun submit(event: ExternalCaptureEvent): CaptureResult {
            if (delayMillis > 0) delay(delayMillis)
            if (event.captureId == failOnId) throw IllegalStateException("模拟落库失败")
            submitted += event.captureId
            done?.complete(event.captureId)
            return CaptureResult(captureId = event.captureId, outcome = CaptureOutcome.ACCEPTED)
        }
    }

    private companion object {
        fun eventOf(id: String) = ExternalCaptureEvent(
            captureId = id,
            rawUri = "zhangran://capture?v=1",
            version = SUPPORTED_PAYLOAD_VERSION,
            payloadBase64 = null,
            receivedAt = 0L,
        )
    }

    // ---------- A13 的核心断言 ----------

    @Test
    fun `调用方作用域已取消时提交仍完成`() = runBlocking {
        val done = CompletableDeferred<String>()
        val source = RecordingEventSource(delayMillis = 100, done = done)
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val submitter = CaptureSubmitter(eventSource = source, scope = appScope)

        // 调用方作用域：等价于 Activity 的 lifecycleScope
        val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        try {
            callerScope.launch {
                // 真实实现里这一步是 lifecycleScope.launch { submit(...) }
                submitter.submit(eventOf("evt-1"))
            }
            // 立刻取消调用方作用域：等价于 finish() → onDestroy → lifecycleScope 取消
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
            // 调用方作用域在提交前就已经取消（等价于一个已销毁的 Activity 调 submit）
            val callerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            callerScope.cancel()

            submitter.submit(eventOf("evt-3"))
            assertEquals("evt-3", withTimeout(5_000) { done.await() })
        } finally {
            appScope.cancel()
        }
    }
}
