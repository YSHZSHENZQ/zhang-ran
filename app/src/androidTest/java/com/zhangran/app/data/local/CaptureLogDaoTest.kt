/**
 * 捕获日志 DAO 测试（`docs/03` §19、`02` §20、`T2b` §4.6）。
 *
 * 覆盖判据：
 * - A8：清空日志后 **Transaction 与 `field_edits` 不变**（`03` §10 明文要求）
 * - A10：证据不随交易删除而删除（`03` §10）
 * - 日志页所需的筛选/分页查询可用（`02` §20）
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureLogDaoTest {

    private val dbName = "capture-log-dao-test.db"
    private lateinit var context: Context
    private lateinit var db: ZhangRanDatabase

    private val now = 1_791_610_182_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, ZhangRanDatabase::class.java, dbName).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun logEntity(
        id: String,
        captureId: String = "cap-$id",
        status: CaptureLogStatus = CaptureLogStatus.RECEIVED,
        source: CaptureSource = CaptureSource.NOTIFICATION,
        capturedAt: Long = now,
        rawCaptureRef: String? = "脱敏文本",
    ) = CaptureLogEntity(
        id = id,
        captureId = captureId,
        capturedAt = capturedAt,
        captureSource = source,
        payloadVersion = 1,
        status = status,
        rawCaptureRef = rawCaptureRef,
    )

    private fun evidenceEntity(id: String, captureId: String = "cap-$id") = CaptureEvidenceEntity(
        id = id,
        captureId = captureId,
        captureSource = CaptureSource.NOTIFICATION,
        rawCaptureRef = "脱敏文本",
        capturedAt = now,
    )

    private fun transactionEntity(id: String = "tx-1") = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 2650L,
        currency = Currency.CNY,
        transactionTime = now,
        transactionTimeZone = "Asia/Shanghai",
        state = TransactionState.CONFIRMED,
        source = CaptureSource.NOTIFICATION,
        createdAt = now,
        updatedAt = now,
    )

    // ---------- 写入与查询 ----------

    @Test
    fun `日志可写入并按时间倒序分页`() = runBlocking {
        db.captureLogDao().insert(logEntity("l1", capturedAt = now - 1000))
        db.captureLogDao().insert(logEntity("l2", capturedAt = now))
        db.captureLogDao().insert(logEntity("l3", capturedAt = now - 500))

        val page = db.captureLogDao().listPage(limit = 10, offset = 0)
        assertEquals(3, page.size)
        assertEquals("应按时间倒序", listOf("l2", "l3", "l1"), page.map { it.id })

        val nextPage = db.captureLogDao().listPage(limit = 2, offset = 2)
        assertEquals(listOf("l1"), nextPage.map { it.id })
    }

    @Test
    fun `按状态与来源筛选`() = runBlocking {
        db.captureLogDao().insert(logEntity("l1", status = CaptureLogStatus.RECEIVED))
        db.captureLogDao().insert(logEntity("l2", status = CaptureLogStatus.FAILED))
        db.captureLogDao().insert(
            logEntity("l3", status = CaptureLogStatus.RECEIVED, source = CaptureSource.MANUAL),
        )

        assertEquals(
            listOf("l2"),
            db.captureLogDao()
                .listPageByStatus(CaptureLogStatus.FAILED, 10, 0)
                .map { it.id },
        )
        assertEquals(
            2,
            db.captureLogDao().listPageByStatus(CaptureLogStatus.RECEIVED, 10, 0).size,
        )
        assertEquals(
            listOf("l3"),
            db.captureLogDao()
                .listPageBySource(CaptureSource.MANUAL, 10, 0)
                .map { it.id },
        )
    }

    @Test
    fun `关键词搜索针对脱敏文本`() = runBlocking {
        db.captureLogDao().insert(logEntity("l1", rawCaptureRef = """{"money":26.5}"""))
        db.captureLogDao().insert(logEntity("l2", rawCaptureRef = """{"money":10}"""))

        assertEquals(
            listOf("l1"),
            db.captureLogDao().search("26.5", 10, 0).map { it.id },
        )
        assertEquals(0, db.captureLogDao().search("未出现", 10, 0).size)
    }

    @Test
    fun `按 captureId 可查且一一对应`() = runBlocking {
        db.captureLogDao().insert(logEntity("l1", captureId = "cap-x"))
        assertNotNull(db.captureLogDao().findByCaptureId("cap-x"))
        assertNull(db.captureLogDao().findByCaptureId("cap-none"))
    }

    @Test
    fun `保留策略按时间清理旧日志`() = runBlocking {
        db.captureLogDao().insert(logEntity("old", capturedAt = now - 100_000))
        db.captureLogDao().insert(logEntity("new", capturedAt = now))

        val deleted = db.captureLogDao().deleteOlderThan(now - 1_000)
        assertEquals(1, deleted)
        assertEquals(listOf("new"), db.captureLogDao().listPage(10, 0).map { it.id })
    }

    // ---------- A8：清空日志不得影响 Transaction 与 field_edits ----------

    @Test
    fun `清空日志与证据不影响交易记录与字段保护标记`() = runBlocking {
        // 既有账务数据
        db.transactionDao().insert(transactionEntity("tx-1"))
        db.fieldEditDao().insert(
            FieldEditEntity(transactionId = "tx-1", fieldName = "amountMinor", editedAt = now),
        )
        // 日志与证据
        db.captureLogDao().insert(logEntity("l1", captureId = "cap-1"))
        db.captureEvidenceDao().insert(evidenceEntity("evi-1", captureId = "cap-1"))

        // 清空日志 + 证据（03 §10：只清理日志与证据）
        db.captureLogDao().clearAll()
        db.captureEvidenceDao().clearAll()

        assertEquals(0, db.captureLogDao().count())
        assertEquals(0, db.captureEvidenceDao().count())
        // 关键断言：Transaction 与 field_edits 完全不受影响
        assertEquals("清空日志不得影响交易记录", 1, db.transactionDao().countVisible())
        assertNotNull(db.transactionDao().findById("tx-1"))
        assertEquals(
            "清空日志不得影响字段保护标记",
            1,
            db.fieldEditDao().listByTransaction("tx-1").size,
        )
    }

    // ---------- A10：证据不随交易删除而删除 ----------

    @Test
    fun `软删除交易后证据仍在`() = runBlocking {
        db.transactionDao().insert(transactionEntity("tx-1"))
        db.captureEvidenceDao().insert(evidenceEntity("evi-1", captureId = "cap-1"))

        db.transactionDao().softDelete("tx-1", deletedAt = now + 1, updatedAt = now + 1)

        assertEquals("交易应从默认列表消失", 0, db.transactionDao().countVisible())
        assertEquals("证据不得随交易删除而删除", 1, db.captureEvidenceDao().count())
        assertNotNull(db.captureEvidenceDao().findById("evi-1"))
    }

    @Test
    fun `按状态计数可用`() = runBlocking {
        db.captureLogDao().insert(logEntity("l1", status = CaptureLogStatus.RECEIVED))
        db.captureLogDao().insert(logEntity("l2", status = CaptureLogStatus.RECEIVED))
        db.captureLogDao().insert(logEntity("l3", status = CaptureLogStatus.FAILED))

        assertEquals(2, db.captureLogDao().countByStatus(CaptureLogStatus.RECEIVED))
        assertEquals(1, db.captureLogDao().countByStatus(CaptureLogStatus.FAILED))
        assertEquals(0, db.captureLogDao().countByStatus(CaptureLogStatus.RULE_TEST))
    }
}
