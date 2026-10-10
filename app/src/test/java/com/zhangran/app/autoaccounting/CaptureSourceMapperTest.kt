/**
 * 上游来源 → `CaptureSource` 映射测试。
 *
 * 覆盖任务书 T3 §6 的 A8：映射覆盖 `docs/07` §5 全部 7 个枚举；多来源命中时按可信度排序，
 * 其余来源记录为“被丢弃”；映射不到时保留原始值且**不静默回退**。
 */
package com.zhangran.app.autoaccounting

import com.zhangran.app.domain.model.CaptureSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureSourceMapperTest {

    // ---------- A8-1：枚举与文档一一对应 ----------

    @Test
    fun `枚举恰好是文档定义的 7 个值`() {
        assertEquals(
            listOf("ACCESSIBILITY", "AI", "HOOK", "MANUAL", "NOTIFICATION", "OCR", "SMS"),
            CaptureSource.entries.map { it.name }.sorted(),
        )
    }

    @Test
    fun `每个枚举都能被别名表命中`() {
        // docs/07 §5 的映射表：每一类来源都必须存在可命中的别名
        val probes = mapOf(
            CaptureSource.MANUAL to "manual",
            CaptureSource.NOTIFICATION to "notification",
            CaptureSource.HOOK to "hook",
            CaptureSource.SMS to "sms",
            CaptureSource.ACCESSIBILITY to "accessibility",
            CaptureSource.AI to "ai",
            CaptureSource.OCR to "ocr",
        )
        probes.forEach { (expected, key) ->
            assertEquals("别名 $key 应映射为 $expected", expected, CaptureSourceMapper.resolveSingle(key).source)
        }
    }

    @Test
    fun `可信度排序为唯一且完整`() {
        assertEquals(7, CaptureSource.byPriority.size)
        assertEquals(7, CaptureSource.byPriority.map { it.priority }.toSet().size)
        assertEquals(CaptureSource.MANUAL, CaptureSource.byPriority.first())
    }

    // ---------- A8-2：别名与归一化 ----------

    @Test
    fun `大小写与分隔符差异可容忍`() {
        assertEquals(CaptureSource.NOTIFICATION, CaptureSourceMapper.resolveSingle("NOTIFICATION").source)
        assertEquals(CaptureSource.NOTIFICATION, CaptureSourceMapper.resolveSingle("notification_listener").source)
        assertEquals(CaptureSource.NOTIFICATION, CaptureSourceMapper.resolveSingle(" notification ").source)
        assertEquals(CaptureSource.HOOK, CaptureSourceMapper.resolveSingle("LSPosed").source)
        assertEquals(CaptureSource.OCR, CaptureSourceMapper.resolveSingle("Shizuku").source)
    }

    // ---------- A8-3：多来源与可信度 ----------

    @Test
    fun `多来源取可信度最高者其余记为被丢弃`() {
        // app=app（HOOK，优先级 2）+ channel=notification（NOTIFICATION，优先级 1）
        val result = CaptureSourceMapper.resolve(
            appKeys = listOf("app"),
            channelKeys = listOf("notification"),
        )
        assertEquals(CaptureSource.NOTIFICATION, result.source)
        assertEquals(2, result.matched.size)
        assertEquals(listOf(CaptureSource.HOOK), result.discarded.map { it.source })
        assertTrue(result.unmapped.isEmpty())
    }

    @Test
    fun `手工来源优先级最高`() {
        val result = CaptureSourceMapper.resolve(
            appKeys = listOf("ocr"),
            channelKeys = listOf("manual"),
        )
        assertEquals(CaptureSource.MANUAL, result.source)
        assertEquals(listOf(CaptureSource.OCR), result.discarded.map { it.source })
    }

    @Test
    fun `被丢弃列表按可信度排序`() {
        val result = CaptureSourceMapper.resolve(
            appKeys = listOf("ocr", "accessibility", "hook"),
        )
        assertEquals(CaptureSource.HOOK, result.source)
        assertEquals(
            listOf(CaptureSource.HOOK, CaptureSource.ACCESSIBILITY, CaptureSource.OCR),
            result.matched.map { it.source },
        )
        assertEquals(
            listOf(CaptureSource.ACCESSIBILITY, CaptureSource.OCR),
            result.discarded.map { it.source },
        )
    }

    @Test
    fun `重复别名去重后只保留一个命中`() {
        val result = CaptureSourceMapper.resolve(appKeys = listOf("hook", "HOOK", " hook "))
        assertEquals(CaptureSource.HOOK, result.source)
        assertEquals(1, result.matched.size)
        assertTrue(result.discarded.isEmpty())
    }

    // ---------- A8-4：未映射 ----------

    @Test
    fun `未映射时保留原始值且不猜默认枚举`() {
        val result = CaptureSourceMapper.resolve(appKeys = listOf("some-unknown-app"), channelKeys = listOf("???"))
        assertNull("未映射时不得返回任何枚举", result.source)
        assertFalse(result.resolved)
        assertTrue(result.matched.isEmpty())
        assertEquals(setOf("some-unknown-app", "???"), result.unmapped.toSet())
    }

    @Test
    fun `部分未映射时仍返回已命中来源并保留未映射项`() {
        val result = CaptureSourceMapper.resolve(appKeys = listOf("wechat-unknown", "notification"))
        assertEquals(CaptureSource.NOTIFICATION, result.source)
        assertEquals(listOf("wechat-unknown"), result.unmapped)
    }

    @Test
    fun `空输入返回未解析`() {
        val result = CaptureSourceMapper.resolve()
        assertNull(result.source)
        assertTrue(result.matched.isEmpty())
        assertTrue(result.unmapped.isEmpty())
        assertFalse(result.resolved)
    }

    @Test
    fun `空白取值被忽略`() {
        val result = CaptureSourceMapper.resolve(appKeys = listOf("   ", ""), channelKeys = listOf("\t"))
        assertNull(result.source)
        assertTrue(result.unmapped.isEmpty())
    }

    @Test
    fun `别名表键均已归一化`() {
        // 归一化键：小写、无空白、无 - 与 _
        CaptureSourceMapper.aliasKeys.forEach { key ->
            assertEquals("别名键必须已归一化：$key", key.lowercase(), key)
            assertFalse("别名键不得含空白：$key", key.contains(" "))
            assertFalse("别名键不得含 -：$key", key.contains("-"))
            assertFalse("别名键不得含 _：$key", key.contains("_"))
        }
    }
}
