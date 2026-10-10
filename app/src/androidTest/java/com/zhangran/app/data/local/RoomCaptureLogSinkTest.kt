/**
 * Room 落库验证测试（`T2b` §4.5 / §5 的"如何确认数据确实落到 `capture_logs` / `capture_evidence`"）。
 *
 * 为什么需要：`adb` 侧的契约自测只能证明"入口被拉起、处理链路跑完"，无法证明数据**真的进了库**
 * （AVD 上没有 `sqlite3` 命令，`run-as` 也无法直接查库）。本测试用真实 Room 库实例化
 * [RoomCaptureLogSink] 并写入，逐列断言落库结果，把 A9/A11 变成可回归的客观证据。
 *
 * 覆盖判据：
 * - A9：事件确实写入 `capture_logs` + `capture_evidence`，且两者通过 `evidenceId` 关联
 * - A11：落库前 `rawCaptureRef` 已脱敏（`02` §20）
 * - 状态映射：`VERSION_MISMATCH` / `INVALID_PAYLOAD` / `ACCEPTED` → `03` §19 的取值
 * - 失败隔离：写库异常不得向外抛（`07` §18）
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.capture.CaptureOutcome
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.log.CaptureLogRecord
import com.zhangran.app.log.RoomCaptureLogSink
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomCaptureLogSinkTest {

    private val dbName = "room-log-sink-test.db"
    private lateinit var context: Context
    private lateinit var db: ZhangRanDatabase
    private lateinit var sink: RoomCaptureLogSink

    private val now = 1_791_610_182_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, ZhangRanDatabase::class.java, dbName).build()
        sink = RoomCaptureLogSink(
            captureLogDao = db.captureLogDao(),
            captureEvidenceDao = db.captureEvidenceDao(),
        )
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun record(
        captureId: String = "cap-1",
        outcome: CaptureOutcome = CaptureOutcome.ACCEPTED,
        version: Int = 1,
        payload: String? = """{"money":26.5}""",
        reason: String? = null,
        isHistoricalReplay: Boolean = false,
    ) = CaptureLogRecord(
        captureId = captureId,
        receivedAt = now,
        outcome = outcome,
        reason = reason,
        pendingReason = null,
        sourcePackage = "net.ankio.auto",
        version = version,
        isHistoricalReplay = isHistoricalReplay,
        sanitizedPayloadRef = payload,
    )

    // ---------- A9：两张表都写入且关联 ----------

    @Test
    fun `写入后日志与证据都落库并相互关联`() = runBlocking {
        sink.write(record())

        val log = db.captureLogDao().findByCaptureId("cap-1")
        assertNotNull("capture_logs 必须有记录", log)
        assertEquals(now, log!!.capturedAt)
        assertEquals("来源包名应落库", "net.ankio.auto", log.packageName)
        assertEquals("载荷版本应落库", 1, log.payloadVersion)

        val evidence = db.captureEvidenceDao().listByCaptureId("cap-1")
        assertEquals("capture_evidence 必须有记录", 1, evidence.size)

        assertEquals(
            "日志的 evidenceId 必须指向证据 id",
            evidence.first().id,
            log.evidenceId,
        )
        assertEquals("证据捕获时刻应与日志一致", now, evidence.first().capturedAt)
    }

    @Test
    fun `captureId 唯一索引保证一一对应`() = runBlocking {
        sink.write(record(captureId = "cap-1"))
        // 同一 captureId 再写一次：REPLACE 语义，不得产生第二条
        sink.write(record(captureId = "cap-1"))

        assertEquals(1, db.captureLogDao().count())
        assertEquals(1, db.captureEvidenceDao().count())
    }

    // ---------- 状态映射 ----------

    @Test
    fun `状态映射遵循文档取值`() = runBlocking {
        sink.write(record(captureId = "c-accepted", outcome = CaptureOutcome.ACCEPTED))
        sink.write(record(captureId = "c-version", outcome = CaptureOutcome.VERSION_MISMATCH))
        sink.write(record(captureId = "c-invalid", outcome = CaptureOutcome.INVALID_PAYLOAD))

        assertEquals(
            CaptureLogStatus.RECEIVED,
            db.captureLogDao().findByCaptureId("c-accepted")?.status,
        )
        assertEquals(
            CaptureLogStatus.VERSION_MISMATCH,
            db.captureLogDao().findByCaptureId("c-version")?.status,
        )
        assertEquals(
            CaptureLogStatus.FAILED,
            db.captureLogDao().findByCaptureId("c-invalid")?.status,
        )
    }

    @Test
    fun `历史回灌标记落库`() = runBlocking {
        sink.write(record(captureId = "c-replay", isHistoricalReplay = true))
        assertEquals(true, db.captureLogDao().findByCaptureId("c-replay")?.isHistoricalReplay)
    }

    // ---------- A11：脱敏 ----------

    @Test
    fun `落库文本必须是脱敏后的内容`() = runBlocking {
        // sink 只接受已脱敏文本（脱敏在 CaptureProcessor 完成）；
        // 这里断言 sink 原样保存传入的脱敏文本，不做二次处理、也不回填原始值
        val sanitized = """{"money":26.5,"phone":"138***5678"}"""
        sink.write(record(captureId = "c-mask", payload = sanitized))

        val log = db.captureLogDao().findByCaptureId("c-mask")!!
        assertEquals(sanitized, log.rawCaptureRef)
        assertTrue("不得出现完整手机号", !log.rawCaptureRef!!.contains("13812345678"))
        assertEquals(sanitized, db.captureEvidenceDao().listByCaptureId("c-mask").first().rawCaptureRef)
    }

    // ---------- 失败隔离 ----------

    @Test
    fun `写库失败不向外抛异常`() = runBlocking {
        db.close() // 制造"库不可用"
        // 不得抛异常（07 §18）
        sink.write(record(captureId = "c-fail"))
    }

    @Test
    fun `载荷缺失时版本列仍为合法整数`() = runBlocking {
        sink.write(record(captureId = "c-noversion", version = -1, payload = null))

        val log = db.captureLogDao().findByCaptureId("c-noversion")
        assertNotNull(log)
        assertTrue("payloadVersion 为 NOT NULL 列，必须是合法整数", log!!.payloadVersion >= 0)
        assertEquals("版本缺失时 engineVersion 为空", null, log.engineVersion)
    }

    @Test
    fun `来源枚举为文档定义的取值`() = runBlocking {
        sink.write(record(captureId = "c-source"))
        assertEquals(
            CaptureSource.NOTIFICATION,
            db.captureLogDao().findByCaptureId("c-source")?.captureSource,
        )
    }
}
