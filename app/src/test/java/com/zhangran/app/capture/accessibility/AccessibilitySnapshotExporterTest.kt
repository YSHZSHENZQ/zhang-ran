/**
 * debug 快照导出（M6-3 / AC-1）。
 *
 * 证明：未识别为支付结果的页面也会留下可拉走的脱敏 JSON；索引文件不含节点文本；release 开关关闭时不写盘。
 */
package com.zhangran.app.capture.accessibility

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySnapshotExporterTest {

    @Test
    fun 关闭时不写任何文件() {
        val dir = tempDir()
        val exporter = AccessibilitySnapshotExporter(directory = dir, enabled = false, maxKept = 3)
        assertNull(exporter.export(chatSnapshot(capturedAt = 1_700_000_000_000L)))
        assertFalse(dir.exists())
    }

    @Test
    fun 未识别为支付结果的页面也会导出节点文本且索引不含正文() {
        val dir = tempDir()
        val exporter = AccessibilitySnapshotExporter(directory = dir, enabled = true, maxKept = 3)
        val capturedAt = 1_700_000_000_123L
        val snapshot = chatSnapshot(capturedAt).copy(
            nodes = listOf(
                SnapshotNode("你好", null, null, null, 1),
                SnapshotNode("13812345678", null, null, null, 2),
                SnapshotNode("¥38.50", null, null, null, 2),
            ),
        )

        val fileName = exporter.export(snapshot)

        assertEquals("1700000000123-com.tencent.mm-w9.json", fileName)
        val json = File(dir, fileName!!).readText()
        val latest = File(dir, AccessibilitySnapshotExporter.LATEST_JSON).readText()
        val index = File(dir, AccessibilitySnapshotExporter.LATEST_TXT).readText()
        assertEquals(json, latest)
        assertTrue(json.contains("\"text\":\"你好\""))
        assertTrue(json.contains("¥38.50"))
        assertTrue(json.contains("\"capturedAt\":$capturedAt"))
        assertTrue(json.contains("com.tencent.mm"))
        assertFalse(json.contains("13812345678"))
        assertTrue(json.contains("***"))
        assertTrue(index.contains("capturedAt=$capturedAt"))
        assertTrue(index.contains("file=$fileName"))
        assertFalse(index.contains("你好"))
        assertFalse(index.contains("138"))
        assertFalse(AccessibilitySnapshotParser.shouldForward(snapshot))
    }

    @Test
    fun 只保留最近若干份快照且不删最新指针() {
        val dir = tempDir()
        val exporter = AccessibilitySnapshotExporter(directory = dir, enabled = true, maxKept = 2)
        exporter.export(chatSnapshot(capturedAt = 1_700_000_000_001L))
        exporter.export(chatSnapshot(capturedAt = 1_700_000_000_002L))
        exporter.export(chatSnapshot(capturedAt = 1_700_000_000_003L))

        val names = dir.listFiles()?.map { it.name }?.sorted().orEmpty()
        assertFalse(names.any { it.startsWith("1700000000001-") })
        assertTrue(names.any { it.startsWith("1700000000002-") })
        assertTrue(names.any { it.startsWith("1700000000003-") })
        assertTrue(File(dir, AccessibilitySnapshotExporter.LATEST_JSON).exists())
        assertTrue(File(dir, AccessibilitySnapshotExporter.LATEST_TXT).readText().contains("1700000000003"))
    }

    private fun chatSnapshot(capturedAt: Long) = AccessibilityPageSnapshot(
        packageName = AccessibilityTargets.WECHAT_PACKAGE,
        windowId = 9,
        capturedAt = capturedAt,
        nodes = listOf(SnapshotNode("你好", null, null, null, 1)),
        truncated = false,
        textChars = 2,
    )

    private fun tempDir(): File =
        File(System.getProperty("java.io.tmpdir"), "m63-export-${System.nanoTime()}").apply { deleteRecursively() }
}
