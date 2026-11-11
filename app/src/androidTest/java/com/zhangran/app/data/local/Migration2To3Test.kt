/**
 * v2 → v3：`transactions.pendingReasons` 从捕获日志回填。
 *
 * 清空日志之后待确认原因仍在交易上，所以迁移必须把已有日志里的 reason 抄过来。
 * 同一笔有多条日志时取最新一条非空 reason。没有日志的交易保持 null。
 */
package com.zhangran.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    private val testDbName = "migration-2to3-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZhangRanDatabase::class.java,
    )

    @Test
    fun 回填最新非空原因并保留没有日志的交易() {
        val v2 = helper.createDatabase(testDbName, 2)
        insertTransaction(v2, "tx-pending", 1_000L)
        insertTransaction(v2, "tx-manual", 2_000L)
        insertTransaction(v2, "tx-newer", 3_000L)
        insertLog(v2, id = "log-old", transactionId = "tx-pending", reason = "LOW_CONFIDENCE", capturedAt = 100L)
        insertLog(
            v2,
            id = "log-new",
            transactionId = "tx-pending",
            reason = "ACCOUNT_UNCERTAIN,CATEGORY_UNCERTAIN",
            capturedAt = 200L,
        )
        insertLog(v2, id = "log-empty", transactionId = "tx-newer", reason = null, capturedAt = 300L)
        insertLog(
            v2,
            id = "log-kept",
            transactionId = "tx-newer",
            reason = "POSSIBLE_DUPLICATE",
            capturedAt = 150L,
        )
        v2.close()

        val v3 = helper.runMigrationsAndValidate(
            testDbName,
            3,
            true,
            ZhangRanDatabase.MIGRATION_2_3,
        )
        assertEquals(
            "ACCOUNT_UNCERTAIN,CATEGORY_UNCERTAIN",
            reasonOf(v3, "tx-pending"),
        )
        assertEquals("POSSIBLE_DUPLICATE", reasonOf(v3, "tx-newer"))
        assertNull(reasonOf(v3, "tx-manual"))
        v3.query("SELECT amountMinor FROM transactions WHERE id = 'tx-pending'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1_000L, cursor.getLong(0))
        }
        v3.close()
    }

    private fun insertTransaction(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String, amount: Long) {
        db.execSQL(
            "INSERT INTO transactions (id, type, amountMinor, currency, merchant, categoryId, " +
                "accountId, sourceAccountId, targetAccountId, relatedTransactionId, transactionTime, " +
                "transactionTimeZone, note, state, source, externalTransactionId, engineVersion, " +
                "matchedRuleId, evidenceId, confirmedByUser, orphan, createdAt, updatedAt, deletedAt) " +
                "VALUES (?, 'EXPENSE', ?, 'CNY', '商户', NULL, NULL, NULL, NULL, NULL, 1000, " +
                "'Asia/Shanghai', NULL, 'PENDING', 'ACCESSIBILITY', NULL, NULL, NULL, NULL, 0, 0, 100, 100, NULL)",
            arrayOf<Any?>(id, amount),
        )
    }

    private fun insertLog(
        db: androidx.sqlite.db.SupportSQLiteDatabase,
        id: String,
        transactionId: String,
        reason: String?,
        capturedAt: Long,
    ) {
        db.execSQL(
            "INSERT INTO capture_logs (id, captureId, capturedAt, captureSource, packageName, channel, " +
                "parserRuleId, engineVersion, payloadVersion, status, reason, isHistoricalReplay, " +
                "rawCaptureRef, evidenceId, transactionId) " +
                "VALUES (?, ?, ?, 'ACCESSIBILITY', 'com.tencent.mm', 'wechat', NULL, 'm6-2', 1, " +
                "'PENDING', ?, 0, NULL, NULL, ?)",
            arrayOf<Any?>(id, "cap-$id", capturedAt, reason, transactionId),
        )
    }

    private fun reasonOf(db: androidx.sqlite.db.SupportSQLiteDatabase, id: String): String? {
        db.query("SELECT pendingReasons FROM transactions WHERE id = ?", arrayOf(id)).use { cursor ->
            check(cursor.moveToFirst())
            return if (cursor.isNull(0)) null else cursor.getString(0)
        }
    }
}
