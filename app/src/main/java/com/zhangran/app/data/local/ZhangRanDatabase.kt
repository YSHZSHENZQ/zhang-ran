/**
 * 账然 Room 数据库（v0.1 第一个 schema 版本）。
 *
 * 依据：
 * - `docs/04` §7 Database（`exportSchema = true`、schema 纳入版本控制、禁止破坏性迁移）
 * - `docs/04` §6 存储归属表（本任务只建第一批 5 张核心表）
 * - `docs/08` §9 Room Test
 *
 * 重要：
 * - `version = 1` 是本项目**第一次产生 schema JSON**（`app/schemas/…/1.json`），
 *   此后任何字段变化都必须配 migration；
 * - **禁止** `fallbackToDestructiveMigration`；
 * - `MIGRATION_1_2` 是 T2a 任务书 §4.6 要求的**空迁移脚手架**：
 *   v1→v2 当前没有真实字段变化，它只用于证明 migration 工具链可用。
 *   真正发生字段变化时，必须补真实迁移语句，不得保留空实现。
 */
package com.zhangran.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zhangran.app.data.local.dao.AccountDao
import com.zhangran.app.data.local.dao.BudgetDao
import com.zhangran.app.data.local.dao.CategoryDao
import com.zhangran.app.data.local.dao.FieldEditDao
import com.zhangran.app.data.local.dao.TransactionDao
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.TransactionEntity

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        FieldEditEntity::class,
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

    companion object {
        /** 当前 schema 版本。 */
        const val VERSION: Int = 1

        /** 数据库文件名（测试与生产共用同一常量，避免文件名写错导致 schema 校验失配）。 */
        const val DATABASE_NAME: String = "zhangran.db"

        /**
         * v1 → v2 空迁移脚手架（`T2a` §4.6）。
         *
         * 当前无真实字段变化，因此只做版本推进；
         * **真正改字段时必须在下面补 DDL，并同步提升 [VERSION]**。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 空迁移：v0.1 暂无 v2 的字段变化（脚手架，仅证明工具链可用）
            }
        }

        /** 全部迁移（顺序即执行顺序）。 */
        val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
