/**
 * 采集编排测试（纯 JVM，使用假 log sink）。
 *
 * 覆盖 T4 §6 的 A4（版本不匹配）、A5（非法载荷 / 脱敏）、A6（历史回灌判定）：
 * - 版本缺失/非法/不受支持 → `VERSION_MISMATCH` + 建议 `UNSUPPORTED_NOTIFICATION`，且**不解码载荷**
 * - 非法 Base64 → `INVALID_PAYLOAD`，不崩溃
 * - 正常载荷 → `ACCEPTED`，且落库/落日志的文本已脱敏
 * - 历史回灌 → 标记并给 `LOW_CONFIDENCE` 建议，但不做领域决策
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
import java.util.Base64

/**
 * 假 sink：记录写入内容，且可被配置为抛异常（验证故障隔离）。
 */
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

    /** Base64URL（无填充换行）编码，与 docs/07 §3.5 的载荷约定一致。 */
    private fun payloadOf(json: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(json.toByteArray(Charsets.UTF_8))

    private fun eventOf(
        version: Int = SUPPORTED_PAYLOAD_VERSION,
        payload: String? = null,
        isHistoricalReplay: Boolean = false,
    ) = ExternalCaptureEvent(
        captureId = fixedId,
        rawUri = "zhangran://capture?v=$version",
        version = version,
        payloadBase64 = payload,
        receivedAt = fixedNow,
        sourcePackage = "net.ankio.auto",
        isHistoricalReplay = isHistoricalReplay,
    )

    // ---------- A4：版本不匹配 ----------

    @Test
    fun `版本缺失不匹配且不解码载荷`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(
            eventOf(version = VERSION_MISSING, payload = "not-base64-!!!")
        )

        assertEquals(CaptureOutcome.VERSION_MISMATCH, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_VERSION_MISMATCH, result.pendingReason)
        assertNull("版本不匹配时不得产出脱敏载荷（即未解析）", result.sanitizedPayload)
        assertEquals(1, sink.records.size)
        assertEquals(CaptureOutcome.VERSION_MISMATCH, sink.records[0].outcome)
        assertNotNull(sink.records[0].reason)
    }

    @Test
    fun `版本非法与不受支持均不匹配`() = runBlocking {
        listOf(VERSION_INVALID, 0, 2, 99).forEach { v ->
            val result = processor().submit(eventOf(version = v, payload = payloadOf("{}")))
            assertEquals("v=$v 应为版本不匹配", CaptureOutcome.VERSION_MISMATCH, result.outcome)
        }
    }

    // ---------- A5：非法载荷与脱敏 ----------

    @Test
    fun `非法 Base64 返回 INVALID_PAYLOAD 且不崩溃`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(eventOf(payload = "!!!not base64!!!"))

        assertEquals(CaptureOutcome.INVALID_PAYLOAD, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_UNSUPPORTED, result.pendingReason)
        assertEquals(1, sink.records.size)
    }

    @Test
    fun `载荷缺失返回 INVALID_PAYLOAD`() = runBlocking {
        val result = processor().submit(eventOf(payload = null))
        assertEquals(CaptureOutcome.INVALID_PAYLOAD, result.outcome)
    }

    @Test
    fun `正常载荷被接受且落日志的文本已脱敏`() = runBlocking {
        val sink = FakeLogSink()
        val json = """{"money":26.5,"phone":"13812345678","card":"6222021234567890123"}"""
        val result = processor(sink).submit(eventOf(payload = payloadOf(json)))

        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
        val stored = sink.records.single().sanitizedPayloadRef
        assertNotNull(stored)
        assertFalse("手机号必须被脱敏", stored!!.contains("13812345678"))
        assertFalse("银行卡号不得完整出现", stored.contains("6222021234567890123"))
        assertTrue("脱敏后应保留前 4 位与后 4 位", stored.contains("6222***0123"))
        assertTrue("应保留脱敏标记", stored.contains("***"))
        // 金额等业务字段不应被脱敏逻辑破坏
        assertTrue(stored.contains("26.5"))
    }

    @Test
    fun `脱敏覆盖文档清单的各类敏感信息`() {
        val sanitize: (String?) -> String? = CaptureProcessor::sanitize
        // 身份证（保留前 6 后 4）
        assertEquals("110101***123X", sanitize("11010119900307123X"))
        // 银行卡（16 位带分隔符 / 19 位无分隔符，均保留前 4 后 4；掩码后不带原分隔符）
        assertEquals("6222***7890", sanitize("6222 0212 3456 7890"))
        assertEquals("6222***0123", sanitize("6222021234567890123"))
        // 手机号（保留前 3 后 4）
        assertEquals("138***5678", sanitize("13812345678"))
        // 支付单号（保留首尾各 4 位）
        assertEquals("1000***3456", sanitize("1000200030003456"))
        // 姓名（保留姓氏 + **）
        assertEquals("收款方：张**", sanitize("收款方：张三丰"))
        // 不含敏感信息时保持原样
        assertEquals("""{"money":26.5}""", sanitize("""{"money":26.5}"""))
        assertNull(sanitize(null))
    }

    // ---------- A6：历史回灌 ----------

    @Test
    fun `历史回灌被标记并给出低置信度建议但不做领域决策`() = runBlocking {
        val sink = FakeLogSink()
        val result = processor(sink).submit(
            eventOf(payload = payloadOf("""{"money":1.0}"""), isHistoricalReplay = true)
        )

        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
        assertEquals(CaptureProcessor.PENDING_REASON_LOW_CONFIDENCE, result.pendingReason)
        assertTrue(sink.records.single().isHistoricalReplay)
    }

    @Test
    fun `markHistoricalIfNeeded 判定规则`() {
        // 早于连接时刻 → 回灌
        assertTrue(ExternalCaptureEvent.markHistoricalIfNeeded(eventTime = 1000L, listenerConnectedAt = 2000L))
        // 晚于连接时刻 → 非回灌
        assertFalse(ExternalCaptureEvent.markHistoricalIfNeeded(eventTime = 3000L, listenerConnectedAt = 2000L))
        // 相等 → 非回灌（边界取"不早于"）
        assertFalse(ExternalCaptureEvent.markHistoricalIfNeeded(eventTime = 2000L, listenerConnectedAt = 2000L))
        // 任一缺失 → 无法判定，按非回灌处理
        assertFalse(ExternalCaptureEvent.markHistoricalIfNeeded(eventTime = null, listenerConnectedAt = 2000L))
        assertFalse(ExternalCaptureEvent.markHistoricalIfNeeded(eventTime = 1000L, listenerConnectedAt = null))
    }

    // ---------- 故障隔离（docs/07 §18）----------

    @Test
    fun `sink 抛异常不影响结果返回`() = runBlocking {
        val result = processor(FakeLogSink(throwOnWrite = true))
            .submit(eventOf(payload = payloadOf("""{"money":1.0}""")))
        assertEquals(CaptureOutcome.ACCEPTED, result.outcome)
    }

    // ---------- 事件构造（入口不做解析）----------

    @Test
    fun `newEvent 从 URI 提取参数并填充默认值`() {
        val event = processor().newEvent(
            rawUri = "zhangran://capture?v=1&payload=AAA",
            sourcePackage = "net.ankio.auto",
        )
        assertEquals(fixedId, event.captureId)
        assertEquals(1, event.version)
        assertEquals("AAA", event.payloadBase64)
        assertEquals(fixedNow, event.receivedAt)
        assertEquals("net.ankio.auto", event.sourcePackage)
        assertFalse(event.isHistoricalReplay)
    }

    @Test
    fun `newEvent 对畸形 URI 不崩溃且版本不受支持`() {
        listOf(null, "", "???").forEach { uri ->
            val event = processor().newEvent(rawUri = uri, sourcePackage = null)
            assertFalse("uri=$uri 不应受支持", event.versionSupported)
        }
    }

    @Test
    fun `newEvent 依据连接时刻标记历史回灌`() {
        val event = processor().newEvent(
            rawUri = "zhangran://capture?v=1&payload=AAA",
            sourcePackage = null,
            listenerConnectedAt = 2000L,
            eventTime = 1000L,
        )
        assertTrue(event.isHistoricalReplay)
    }
}
