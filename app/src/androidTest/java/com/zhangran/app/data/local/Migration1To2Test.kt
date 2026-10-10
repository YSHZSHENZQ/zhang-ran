/**
 * v1 → v2 真实迁移测试（`docs/08` §9、`T2b` §4.4）。
 *
 * 覆盖判据：
 * - A3：`MIGRATION_1_2` 为真实迁移（创建 v2 四张表与索引），`MigrationTestHelper` 以 `2.json` 校验通过
 * - A4：迁移后 v1 五张表的数据**完整保留**
 * - A5：无 destructive migration（迁移以显式 DDL 完成，库文件保留）
 *
 * 说明：`MigrationTest`（T2a 建的）负责"迁移对象与版本注册表"层面的断言；
 * 本文件专注"数据保留"这一条，用真实 v1 数据贯穿迁移前后。
 */
package com.zhangran.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val testDbName = "migration-1to2-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZhangRanDatabase::class.java,
    )

    /** 向 v1 的五张表各写入一行，作为"迁移前数据"基线。 */
    private fun seedV1() {
        val db = helper.createDatabase(testDbName, V1)
        db.execSQL(
            "INSERT INTO categories (id, name, type, icon, sortOrder, enabled, systemDefined, " +
                "createdAt, updatedAt) VALUES ('cat-1','餐饮','EXPENSE','food',1,1,0,100,100)",
        )
        db.execSQL(
            "INSERT INTO accounts (id, name, type, channel, last4, enabled, sortOrder, isDefault, " +
                "createdAt, updatedAt) VALUES ('acc-1','现金','CASH','CASH',NULL,1,1,1,100,100)",
        )
        db.execSQL(
            "INSERT INTO transactions (id, type, amountMinor, currency, merchant, categoryId, " +
                "accountId, sourceAccountId, targetAccountId, relatedTransactionId, transactionTime, " +
                "transactionTimeZone, note, state, source, externalTransactionId, engineVersion, " +
                "matchedRuleId, evidenceId, confirmedByUser, orphan, createdAt, updatedAt, deletedAt) " +
                "VALUES ('tx-1','EXPENSE',2650,'CNY','全家','cat-1','acc-1',NULL,NULL,NULL,1000," +
                "'Asia/Shanghai',NULL,'CONFIRMED','NOTIFICATION',NULL,NULL,NULL,NULL,1,0,100,100,NULL)",
        )
        db.execSQL(
            "INSERT INTO budgets (id, scope, categoryId, period, periodKey, limitMinor, enabled, " +
                "createdAt, updatedAt) VALUES ('bud-1','TOTAL',NULL,'MONTH','2026-10',300000,1,100,100)",
        )
        db.execSQL(
            "INSERT INTO field_edits (transactionId, fieldName, editedAt, editedValueHash) " +
                "VALUES ('tx-1','amountMinor',100,NULL)",
        )
        db.close()
    }

    @Test
    fun `迁移后 v1 五张表数据完整保留`() {
        seedV1()

        val db = helper.runMigrationsAndValidate(
            testDbName,
            ZhangRanDatabase.VERSION,
            true,
            ZhangRanDatabase.MIGRATION_1_2,
        )

        // 逐表核对行数与关键字段
        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            c.moveToFirst()
            assertEquals("transactions 行数", 1, c.getInt(0))
        }
        db.query("SELECT amountMinor, merchant FROM transactions WHERE id = 'tx-1'").use { c ->
            c.moveToFirst()
            assertEquals("金额必须原样保留", 2650L, c.getLong(0))
            assertEquals("商户必须原样保留", "全家", c.getString(1))
        }
        db.query("SELECT COUNT(*) FROM categories").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM accounts").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM budgets").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM field_edits").use { c ->
            c.moveToFirst(); assertEquals(1, c.getInt(0))
        }
        db.close()
    }

    @Test
    fun `迁移后 v2 四张新表可写入`() {
        seedV1()

        val db = helper.runMigrationsAndValidate(
            testDbName,
            ZhangRanDatabase.VERSION,
            true,
            ZhangRanDatabase.MIGRATION_1_2,
        )

        // capture_evidence（03 §10 字段）
        db.execSQL(
            "INSERT INTO capture_evidence (id, captureId, captureSource, engineVersion, ruleId, " +
                "confidence, matchedKeywords, rejectedKeywords, rawCaptureRef, capturedAt) " +
                "VALUES ('evi-1','cap-1','NOTIFICATION','v1',NULL,0.9,NULL,NULL,'脱敏文本',1000)",
        )
        // parsing_rules（02 §16 + 03 §3.1，含默认值列）
        db.execSQL(
            "INSERT INTO parsing_rules (ruleId, priority, enabled, direction, engineVersion, " +
                "createdAt, updatedAt) VALUES ('rule-1',1,1,NULL,NULL,1000,1000)",
        )
        db.query("SELECT include, exclude, matchMode FROM parsing_rules WHERE ruleId = 'rule-1'")
            .use { c ->
                c.moveToFirst()
                assertEquals("include 默认应为空数组", "[]", c.getString(0))
                assertEquals("exclude 默认应为空数组", "[]", c.getString(1))
                assertEquals("matchMode 默认应为 CONTAINS", "CONTAINS", c.getString(2))
            }
        // capture_logs（03 §19）
        db.execSQL(
            "INSERT INTO capture_logs (id, captureId, capturedAt, captureSource, packageName, " +
                "channel, parserRuleId, engineVersion, payloadVersion, status, reason, " +
                "isHistoricalReplay, rawCaptureRef, evidenceId, transactionId) " +
                "VALUES ('log-1','cap-1',1000,'NOTIFICATION',NULL,NULL,NULL,NULL,1,'RECEIVED'," +
                "NULL,0,'脱敏文本','evi-1',NULL)",
        )
        // duplicate_decisions（03 §20）
        db.execSQL(
            "INSERT INTO duplicate_decisions (id, candidateId, candidateTransactionId, " +
                "existingTransactionId, verdict, decidedBy, decidedAt, reason, matchSignals, " +
                "evidenceId, revokedAt) " +
                "VALUES ('dd-1','cap-1',NULL,'tx-1','SAME_TRANSACTION','USER',1000,NULL,NULL,NULL,NULL)",
        )

        db.query("SELECT COUNT(*) FROM capture_evidence").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM parsing_rules").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM capture_logs").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM duplicate_decisions").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
        db.close()
    }

    private companion object {
        /** v1 版本号：历史版本用字面量。 */
        const val V1 = 1
    }
}
