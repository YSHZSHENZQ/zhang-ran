/**
 * 进程内接口契约测试（U0 §4.1 / A1、A9、A10）。
 *
 * 这些断言直接对应验收判据：
 * - A1：`CollectorOutput` / 接口**不含任何上游类型**（`docs/04` §20 禁止项）
 * - A9：`sourceType` 必填并映射到 `docs/07` §5 的 `CaptureSource`；`NODE` 仅建枚举
 * - A10：**同一交易两条产出均落 `capture_logs`**（采集侧不得合并，由 Dedup 收敛）
 */
package com.zhangran.app.capture

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.log.CaptureLogRecord
import com.zhangran.app.log.CaptureLogSink
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class RecordingSink : CaptureLogSink {
    val records = mutableListOf<CaptureLogRecord>()
    override fun write(record: CaptureLogRecord) {
        records += record
    }
}

class CollectorEventSourceTest {

    private fun processor(sink: CaptureLogSink): CaptureProcessor {
        var seq = 0
        return CaptureProcessor(logSink = sink, idGenerator = { "cap-${++seq}" })
    }

    private fun outputOf(
        collectorId: String = "bill-1",
        sourceType: CollectorSourceType = CollectorSourceType.NOTICE,
        capturedAt: Long = 1_791_610_182_000L,
        rawPayloadJson: String = """{"money":26.5,"merchant":"全家"}""",
    ) = CollectorOutput(
        collectorId = collectorId,
        interfaceVersion = COLLECTOR_INTERFACE_VERSION,
        sourceType = sourceType,
        engineVersion = "v1",
        sourcePackage = "com.tencent.mm",
        sourceChannel = "wechat",
        rawPayloadJson = rawPayloadJson,
        capturedAt = capturedAt,
    )

    // ---------- 接口形态 ----------

    @Test
    fun `接口以采集产出为输入而不是 URI`() {
        // 编译期即证明：newOutput 只接受 CollectorOutput，submit 只接受 CollectorEvent。
        // 这里再断言一次返回值语义，防止有人在实现里悄悄丢弃产出。
        val sink = RecordingSink()
        val processor = processor(sink)
        val output = outputOf()
        val event = processor.newOutput(output)

        assertEquals(output, event.output)
        assertEquals(COLLECTOR_INTERFACE_VERSION, event.interfaceVersion)
        assertTrue("当前接口版本必须自洽", event.interfaceVersionSupported)
    }

    // ---------- A10：同一交易多条产出 ----------

    @Test
    fun `同一交易的两次产出分别落库不合并`() = runBlocking {
        val sink = RecordingSink()
        val processor = processor(sink)

        // "处理中"与"成功"是同一笔交易的两次采样（同一 collectorId）
        val first = processor.newOutput(
            outputOf(rawPayloadJson = """{"money":26.5,"merchant":"全家","state":"处理中"}"""),
        )
        val second = processor.newOutput(
            outputOf(
                rawPayloadJson = """{"money":26.5,"merchant":"全家","state":"支付成功"}""",
                capturedAt = 1_791_610_182_400L,
            ),
        )

        processor.submit(first)
        processor.submit(second)

        assertEquals("同一交易的两次产出都必须留痕（由 Dedup 收敛，采集侧不得合并）", 2, sink.records.size)
        assertTrue("两条记录应有不同的 captureId", sink.records[0].captureId != sink.records[1].captureId)
        assertTrue(
            "第二次采样（成功态）必须完整保留",
            sink.records[1].sanitizedPayloadRef!!.contains("支付成功"),
        )
        assertEquals(CaptureOutcome.ACCEPTED, sink.records[0].outcome)
        assertEquals(CaptureOutcome.ACCEPTED, sink.records[1].outcome)
    }

    @Test
    fun `多条产出的落库顺序与提交顺序一致`() = runBlocking {
        val sink = RecordingSink()
        val processor = processor(sink)
        listOf(1L, 2L, 3L).forEach { i ->
            processor.submit(processor.newOutput(outputOf(capturedAt = i * 1000)))
        }
        assertEquals(listOf(1000L, 2000L, 3000L), sink.records.map { it.receivedAt })
    }

    // ---------- A9：来源类型 ----------

    @Test
    fun `sourceType 映射到 CaptureSource 枚举`() {
        assertEquals(CaptureSource.NOTIFICATION, CollectorSourceType.NOTICE.toCaptureSource())
        assertEquals(CaptureSource.HOOK, CollectorSourceType.DATA.toCaptureSource())
        assertEquals(CaptureSource.OCR, CollectorSourceType.OCR.toCaptureSource())
        assertEquals(CaptureSource.ACCESSIBILITY, CollectorSourceType.NODE.toCaptureSource())
    }

    @Test
    fun `映射结果都是既有 CaptureSource 取值不得新增`() {
        val allowed = CaptureSource.entries.toSet()
        CollectorSourceType.entries.forEach { type ->
            assertTrue("$type 的映射结果必须在 CaptureSource 既有取值内", type.toCaptureSource() in allowed)
        }
    }

    @Test
    fun `NODE 只建枚举不实现采集`() {
        // NODE 必须存在（U2 可能启用），但本任务不提供任何节点采集实现：
        // capture/ 下不得出现 NODE 采集类；此处以"构造产出可被接受"代替实现断言。
        val sink = RecordingSink()
        val processor = processor(sink)
        runBlocking { processor.submit(processor.newOutput(outputOf(sourceType = CollectorSourceType.NODE))) }
        assertEquals(1, sink.records.size)
    }

    // ---------- A1：不得暴露上游类型 ----------

    @Test
    fun `接口文件不含任何上游类型引用`() {
        val dir = File(System.getProperty("user.dir"), "src/main/java/com/zhangran/app/capture")
        val files = listOf(
            "CollectorOutput.kt",
            "CollectorEventSource.kt",
            "ExternalCaptureEvent.kt",
            "CaptureProcessor.kt",
        ).map { File(dir, it) }

        files.forEach { file ->
            assertTrue("待检查文件应存在：${file.path}", file.isFile)
            // 只检查真实代码：注释里允许出现"不得使用上游类型 X"这类说明
            val code = file.readText()
                .replace(Regex("""/\*[\s\S]*?\*/"""), "")
                .replace(Regex("""//[^\n]*"""), "")
            listOf("net.ankio", "BillInfoModel", "AutoAccounting", "IAppAdapter").forEach { forbidden ->
                assertFalse("${file.name} 的代码不得引用上游符号 $forbidden", code.contains(forbidden))
            }
        }
    }

    @Test
    fun `CollectorOutput 不出现上游字段类型`() {
        val file = File(
            System.getProperty("user.dir"),
            "src/main/java/com/zhangran/app/capture/CollectorOutput.kt",
        )
        val code = file.readText()
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")
        // 产出字段必须是账然自有类型 / 基础类型
        listOf("BillInfoModel", "DataType", "AppInfo", "net.ankio").forEach {
            assertFalse("CollectorOutput 的字段类型不得是上游类型 $it", code.contains(it))
        }
    }
}
