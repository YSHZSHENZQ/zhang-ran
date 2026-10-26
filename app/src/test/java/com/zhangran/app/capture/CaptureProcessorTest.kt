/**
 * 采集接入层编排测试（U0：基于 [CollectorOutput]，不再有 URI / Base64 语义）。
 *
 * 覆盖：
 * - A3 接口版本不匹配（缺失 / 非法 / 不受支持）→ `VERSION_MISMATCH`，且不带脱敏内容
 * - A4 脱敏仍生效（`docs/02` §20 清单）
 * - A5 落库仍经 `CaptureLogSink`（不直接写 Transaction）
 * - A6 `interfaceVersion` 写入既有 `payloadVersion` 语义（`CaptureLogRecord.version`）
 * - 历史回灌 / 空产出 / sink 故障隔离
 */
package com.zhangran.app.capture

import com.zhangran.app.log.CaptureLogRecord
import com.zhangran.app.log.CaptureLogSink
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 假 sink：记录写入内容，且可被配置为抛异常（验证故障隔离）。 */
private class FakeLogSink(
    private val throwOnWrite: Boolean = false,
) : CaptureLogSink {
    val records = mutableListOf<CaptureLogRecord>()

    override fun write(record: CaptureLogRecord) {
        if (throwOnWrite) throw IllegalStateException("模拟 sink 故障")
        records += record
    }
}

class CaptureProcessorTest {

    private val fixedNow = 1_791_610_182_000L
    private val fixedId = "test-capture-id"

    private fun processor(sink: CaptureLogSink = FakeLogSink()): CaptureProcessor =
        CaptureProcessor(
            logSink = sink,
            clock = { fixedNow },
            idGenerator = { fixedId },
        )

    private fun outputOf(
        interfaceVersion: Int = COLLECTOR_INTERFACE_VERSION,
        sourceType: CollectorSourceType = CollectorSourceType.NOTICE,
        rawPayloadJson: String = """{"money":26.5}""",
        isHistoricalReplay: Boolean = false,
        sourcePackage: String? = "com.tencent.mm",
    ) = CollectorOutput(
        collectorId = "collector-1",
        interfaceVersion = interfaceVersion,
        sourceType = sourceType,
        engineVersion = "v1",
        sourcePackage = sourcePackage,
        sourceChannel = "wechat",
        rawPayloadJson = rawPayloadJson,
        capturedAt = fixedNow,
        isHistoricalReplay = isHistoricalReplay,
    )

    private fun eventOf(output: CollectorOutput = outputOf()) = CollectorEvent(
        captureId = fixedId,
        output = output,
    )

    // ---------- A3：接口版本不匹配 ----------

    @Test
    fun `接口版本缺失不匹配且不产出脱敏内容`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(eventOf(outputOf(interfaceVersion = VERSION_MISSING)))

        assertEquals(CaptureOutcome.VERSION_MISMATCH, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_VERSION_MISMATCH, result.pendingReason)
        assertNull("版本不匹配时不得产出脱敏内容", result.sanitizedPayload)
        assertEquals(1, sink.records.size)
        assertEquals(CaptureOutcome.VERSION_MISMATCH, sink.records[0].outcome)
        assertNotNull(sink.records[0].reason)
    }

    @Test
    fun `接口版本非法与不受支持均不匹配`() = runBlocking {
        listOf(VERSION_INVALID, 0, 2, 99).forEach { v ->
            val result = processor().submit(eventOf(outputOf(interfaceVersion = v)))
            assertEquals("v=$v 应为版本不匹配", CaptureOutcome.VERSION_MISMATCH, result.outcome)
            assertNull("v=$v 不应产出脱敏内容", result.sanitizedPayload)
        }
    }

    @Test
    fun `版本不受支持时即使产出也不解析`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink)
            .submit(eventOf(outputOf(interfaceVersion = 9, rawPayloadJson = """{"money":99}""")))

        assertEquals(CaptureOutcome.VERSION_MISMATCH, result.outcome)
        assertNull(result.sanitizedPayload)
        assertEquals(1, sink.records.size)
    }

    // ---------- 空产出 ----------

    @Test
    fun `空产出视为非法且不崩溃`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(eventOf(outputOf(rawPayloadJson = "   ")))

        assertEquals(CaptureOutcome.INVALID_PAYLOAD, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_UNSUPPORTED, result.pendingReason)
        assertEquals(1, sink.records.size)
    }

    // ---------- A4：脱敏 ----------

    @Test
    fun `正常产出被接受且落日志的文本已脱敏`() = runBlocking {
        val sink = FakeLogSink()
        val json = """{"money":26.5,"phone":"13812345678","card":"6222021234567890123"}"""
        val result = processor(sink).submit(eventOf(outputOf(rawPayloadJson = json)))

        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
        val stored = sink.records.single().sanitizedPayloadRef
        assertNotNull(stored)
        assertFalse("手机号必须被脱敏", stored!!.contains("13812345678"))
        assertFalse("银行卡号不得完整出现", stored.contains("6222021234567890123"))
        assertTrue("脱敏后应保留前 4 位与后 4 位", stored.contains("6222***0123"))
        assertTrue("金额不得被脱敏逻辑破坏", stored.contains("26.5"))
    }

    @Test
    fun `脱敏覆盖文档清单的各类敏感信息`() {
        val sanitize: (String?) -> String? = CaptureProcessor::sanitize
        assertEquals("110101***123X", sanitize("11010119900307123X"))
        assertEquals("6222***7890", sanitize("6222 0212 3456 7890"))
        assertEquals("6222***0123", sanitize("6222021234567890123"))
        assertEquals("138***5678", sanitize("13812345678"))
        assertEquals("1000***3456", sanitize("1000200030003456"))
        assertEquals("收款方：张**", sanitize("收款方：张三丰"))
        assertEquals("""{"money":26.5}""", sanitize("""{"money":26.5}"""))
        assertNull(sanitize(null))
    }

    // ---------- A5 / A6：落库调用与版本映射 ----------

    @Test
    fun `落库记录携带接口版本与来源包名`() = runBlocking {
        val sink = FakeLogSink()
        processor(sink).submit(eventOf(outputOf(interfaceVersion = 1)))

        val record = sink.records.single()
        assertEquals("interfaceVersion 写入既有 payloadVersion 语义", 1, record.version)
        assertEquals("com.tencent.mm", record.sourcePackage)
        assertEquals(fixedNow, record.receivedAt)
        assertEquals(fixedId, record.captureId)
        assertFalse(record.isHistoricalReplay)
    }

    @Test
    fun `经由 sink 写入且不接触 Transaction`() = runBlocking {
        val sink = FakeLogSink()
        val processor = processor(sink)
        processor.submit(eventOf())
        // 采集接入层只依赖 CaptureLogSink 接口（编译期即证明不持有 DAO/Repository）
        assertEquals(1, sink.records.size)
    }

    // ---------- 历史回灌 ----------

    @Test
    fun `历史回灌被标记并给出低置信度建议`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(eventOf(outputOf(isHistoricalReplay = true)))

        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_LOW_CONFIDENCE, result.pendingReason)
        assertTrue(sink.records.single().isHistoricalReplay)
    }

    @Test
    fun `markHistoricalIfNeeded 判定规则`() {
        assertTrue(CollectorEvent.markHistoricalIfNeeded(eventTime = 1000L, listenerConnectedAt = 2000L))
        assertFalse(CollectorEvent.markHistoricalIfNeeded(eventTime = 3000L, listenerConnectedAt = 2000L))
        assertFalse(CollectorEvent.markHistoricalIfNeeded(eventTime = 2000L, listenerConnectedAt = 2000L))
        assertFalse(CollectorEvent.markHistoricalIfNeeded(eventTime = null, listenerConnectedAt = 2000L))
        assertFalse(CollectorEvent.markHistoricalIfNeeded(eventTime = 1000L, listenerConnectedAt = null))
    }

    // ---------- 故障隔离 ----------

    @Test
    fun `sink 抛异常不影响结果返回`() = runBlocking {
        val result = processor(FakeLogSink(throwOnWrite = true)).submit(eventOf())
        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
    }

    // ---------- 事件构造 ----------

    @Test
    fun `newOutput 生成 captureId 并原样保留产出`() {
        val output = outputOf()
        val event = processor().newOutput(output)

        assertEquals(fixedId, event.captureId)
        assertEquals(output, event.output)
        assertTrue(event.interfaceVersionSupported)
        assertEquals(CollectorSourceType.NOTICE, event.sourceType)
    }
}
