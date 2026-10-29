/**
 * 延迟采样调度测试（`M6` 阶段 1）。
 *
 * 纯 JVM：验证同 key 合并、不同 key 独立、销毁时取消。
 */
package com.zhangran.app.capture.accessibility

import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DelayedSampleSchedulerTest {

    @Test
    fun 同一窗口只执行最后一次采样() = runBlocking {
        val executed = CopyOnWriteArrayList<Int>()
        val scheduler = DelayedSampleScheduler(this, delayMillis = 40)

        scheduler.schedule("wechat:1") { executed += 1 }
        delay(10)
        scheduler.schedule("wechat:1") { executed += 2 }
        delay(120)

        assertEquals(listOf(2), executed)
    }

    @Test
    fun 不同窗口互不取消() = runBlocking {
        val executed = CopyOnWriteArrayList<String>()
        val scheduler = DelayedSampleScheduler(this, delayMillis = 40)

        scheduler.schedule("wechat:1") { executed += "wechat" }
        scheduler.schedule("alipay:2") { executed += "alipay" }
        delay(120)

        assertEquals(setOf("wechat", "alipay"), executed.toSet())
        assertEquals(2, executed.size)
    }

    @Test
    fun 取消全部后不再执行() = runBlocking {
        val executed = CopyOnWriteArrayList<Int>()
        val scheduler = DelayedSampleScheduler(this, delayMillis = 50)

        scheduler.schedule("wechat:1") { executed += 1 }
        scheduler.cancelAll()
        delay(120)

        assertTrue(executed.isEmpty())
        assertEquals(0, scheduler.pendingCount())
    }
}
