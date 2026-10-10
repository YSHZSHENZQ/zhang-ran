/**
 * 账然 Room 数据库。
 *
 * 依据：
 * - `docs/04` §7 Database（`exportSchema = true`、schema 纳入版本控制、禁止破坏性迁移）
 * - `docs/04` §6 存储归属表
 * - `docs/08` §9 Room Test
 *
 * 版本历史：
 * - **v1**（T2a）：`transactions` / `categories` / `accounts` / `budgets` / `field_edits`
 * - **v2**（T2b）：新增 `capture_evidence`（`03` §10）、`parsing_rules`（`02` §16 + `03` §3.1）、
 *   `capture_logs`（`03` §19）、`duplicate_decisions`（`03` §20）
 *
 * 纪律：
 * - **禁止** `fallbackToDestructiveMigration`；
 * - 迁移 DDL 必须与 Room 生成的 schema **逐字一致**（否则 `MigrationTestHelper` 校验失败）；
 * - v2 的四张新表**全部集中在 [MIGRATION_1_2] 内**（一次迁移建全，不拆成多次改迁移）。
 */
package com.zhangran.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zhangran.app.data.local.dao.AccountDao
import com.zhangran.app.data.local.dao.BudgetDao
import com.zhangran.app.data.local.dao.CaptureEvidenceDao
import com.zhangran.app.data.local.dao.CaptureLogDao
import com.zhangran.app.data.local.dao.CategoryDao
import com.zhangran.app.data.local.dao.DuplicateDecisionDao
import com.zhangran.app.data.local.dao.FieldEditDao
import com.zhangran.app.data.local.dao.ParsingRuleDao
import com.zhangran.app.data.local.dao.TransactionDao
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.DuplicateDecisionEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.ParsingRuleEntity
import com.zhangran.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        FieldEditEntity::class,
        CaptureEvidenceEntity::class,
        ParsingRuleEntity::class,
        CaptureLogEntity::class,
        DuplicateDecisionEntity::class,
    ],
    version = ZhangRanDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class ZhangRanDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun categoryDao(): CategoryDao

    abstract fun accountDao(): AccountDao

    abstract fun budgetDao(): BudgetDao

    abstract fun fieldEditDao(): FieldEditDao

    abstract fun captureEvidenceDao(): CaptureEvidenceDao

    abstract fun parsingRuleDao(): ParsingRuleDao

    abstract fun captureLogDao(): CaptureLogDao

    abstract fun duplicateDecisionDao(): DuplicateDecisionDao

    companion object {
        /** 当前 schema 版本。 */
        const val VERSION: Int = 2

        /** 数据库文件名（测试与生产共用同一常量，避免文件名写错导致 schema 校验失配）。 */
        const val DATABASE_NAME: String = "zhangran.db"

        /**
         * v1 → v2：**真实迁移**，一次建全 v2 新增的四张表与索引。
         *
         * DDL 必须与 Room 生成的 `2.json` 中 `createSql` 逐字一致。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ---- capture_evidence（03 §10）----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `capture_evidence` (" +
                        "`id` TEXT NOT NULL, `captureId` TEXT NOT NULL, " +
                        "`captureSource` TEXT NOT NULL, `engineVersion` TEXT, `ruleId` TEXT, " +
                        "`confidence` REAL, `matchedKeywords` TEXT, `rejectedKeywords` TEXT, " +
                        "`rawCaptureRef` TEXT, `capturedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_evidence_captureId` " +
                        "ON `capture_evidence` (`captureId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_evidence_capturedAt` " +
                        "ON `capture_evidence` (`capturedAt`)",
                )

                // ---- parsing_rules（02 §16 + 03 §3.1）----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `parsing_rules` (" +
                        "`ruleId` TEXT NOT NULL, `priority` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL, `direction` TEXT, " +
                        "`include` TEXT NOT NULL DEFAULT '[]', " +
                        "`exclude` TEXT NOT NULL DEFAULT '[]', " +
                        "`matchMode` TEXT NOT NULL DEFAULT 'CONTAINS', " +
                        "`engineVersion` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, PRIMARY KEY(`ruleId`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_parsing_rules_enabled_priority` " +
                        "ON `parsing_rules` (`enabled`, `priority`)",
                )

                // ---- capture_logs（03 §19）----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `capture_logs` (" +
                        "`id` TEXT NOT NULL, `captureId` TEXT NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, `captureSource` TEXT NOT NULL, " +
                        "`packageName` TEXT, `channel` TEXT, `parserRuleId` TEXT, " +
                        "`engineVersion` TEXT, `payloadVersion` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, `reason` TEXT, " +
                        "`isHistoricalReplay` INTEGER NOT NULL, `rawCaptureRef` TEXT, " +
                        "`evidenceId` TEXT, `transactionId` TEXT, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_logs_capturedAt` " +
                        "ON `capture_logs` (`capturedAt`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_logs_status` " +
                        "ON `capture_logs` (`status`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_logs_captureSource` " +
                        "ON `capture_logs` (`captureSource`)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_capture_logs_captureId` " +
                        "ON `capture_logs` (`captureId`)",
                )

                // ---- duplicate_decisions（03 §20）----
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `duplicate_decisions` (" +
                        "`id` TEXT NOT NULL, `candidateId` TEXT NOT NULL, " +
                        "`candidateTransactionId` TEXT, `existingTransactionId` TEXT NOT NULL, " +
                        "`verdict` TEXT NOT NULL, `decidedBy` TEXT NOT NULL, " +
                        "`decidedAt` INTEGER NOT NULL, `reason` TEXT, `matchSignals` TEXT, " +
                        "`evidenceId` TEXT, `revokedAt` INTEGER, PRIMARY KEY(`id`))",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_duplicate_decisions_candidateId` " +
                        "ON `duplicate_decisions` (`candidateId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_duplicate_decisions_existingTransactionId` " +
                        "ON `duplicate_decisions` (`existingTransactionId`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_duplicate_decisions_candidateTransactionId` " +
                        "ON `duplicate_decisions` (`candidateTransactionId`)",
                )
            }
        }

        /** 全部迁移（顺序即执行顺序）。 */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
