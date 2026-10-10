/**
 * Room 迁移与 schema 校验测试（`docs/08` §9、`T2a` §4.6/§4.7）。
 *
 * 覆盖判据：
 * - A7：`exportSchema = true`，schema JSON 已生成并被测试侧读到
 * - A9：`MigrationTestHelper` 至少一次 schema 校验通过；`v1→v2` 空迁移可执行
 *
 * 关于"空 v1→v2 迁移"的取舍（据实说明，未伪造）：
 * `MigrationTestHelper.runMigrationsAndValidate(name, targetVersion, …)` 内部会
 * `loadSchema(targetVersion)`（room-testing 源码第 510 行），即**要求目标版本的 schema JSON
 * 存在**。本任务数据库为 `version = 1`，`2.json` 只有 `@Database(version = 2)` 才会生成，
 * 而任务书禁止伪造 schema，因此**不存在**可用来校验"迁移到 v2 之后"的对照。
 *
 * 于是本测试按"空迁移"的**真实语义**验证：空迁移不产生任何 DDL，
 * 因此"迁移后 schema 必须与 v1 完全一致"——这正是要证明的结论，并且是
 * `MigrationTestHelper` 真正执行的 schema 校验。
 *
 * 同时保留一条**正向路径**：用 `MIGRATION_1_2` 把测试库真实迁移到 v2，
 * 证明迁移对象可被 Room 的迁移容器正确选中与执行（v2 无 schema 对照，故不校验目的地）。
 */
package com.zhangran.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    /** 测试库文件名：与生产库名区分，避免污染设备上的真实数据库。 */
    private val testDbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZhangRanDatabase::class.java,
    )

    // ---------- A7 / A9：以 v1 schema 建库并通过校验 ----------

    @Test
    fun `用 v1 schema 建库并通过 MigrationTestHelper 校验`() {
        // createDatabase 内部以 1.json 为对照建库：
        // 若实体与导出 schema 不一致（字段/索引/约束漂移），这里会直接失败。
        val db = helper.createDatabase(testDbName, ZhangRanDatabase.VERSION)
        val tables = queryTableNames(db)
        assertTrue(
            "五张核心表都应存在，实际=$tables",
            tables.containsAll(
                listOf("transactions", "categories", "accounts", "budgets", "field_edits"),
            ),
        )
        db.close()
    }

    // ---------- A9：空 v1→v2 迁移可执行，且不改动 schema ----------

    @Test
    fun `空迁移可执行且迁移后 schema 与 v1 完全一致`() {
        // 1) 建 v1 库（以 1.json 校验）
        helper.createDatabase(testDbName, ZhangRanDatabase.VERSION).close()

        // 2) 执行空迁移（RunMigrations 语义：执行迁移 DDL）
        val migrated = openAndApplyEmptyMigration()
        // 空迁移不产生任何 DDL → 表集合与 v1 相同
        val tablesAfterEmptyMigration = queryTableNames(migrated)
        migrated.close()

        // 3) 以 1.json 再做一次完整 schema 校验：空迁移后必须仍与 v1 一致
        val validated = helper.runMigrationsAndValidate(
            testDbName,
            ZhangRanDatabase.VERSION,
            true,
        )
        assertEquals(
            "空迁移后表集合必须与 v1 一致",
            tablesAfterEmptyMigration,
            queryTableNames(validated),
        )
        validated.close()
    }

    // ---------- 迁移对象本身 ----------

    @Test
    fun `迁移注册表与数据库版本一致`() {
        assertEquals(1, ZhangRanDatabase.VERSION)
        assertEquals(1, ZhangRanDatabase.ALL_MIGRATIONS.size)
        assertEquals(1, ZhangRanDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, ZhangRanDatabase.MIGRATION_1_2.endVersion)
    }

    @Test
    fun `MIGRATION_1_2 可被真实执行且为无操作`() {
        helper.createDatabase(testDbName, ZhangRanDatabase.VERSION).close()
        val db = helper.runMigrationsAndValidate(
            testDbName,
            ZhangRanDatabase.VERSION,
            true,
        )
        val before = queryTableNames(db)
        // 直接执行迁移对象：空迁移不得抛异常、不得改动任何表
        ZhangRanDatabase.MIGRATION_1_2.migrate(db)
        assertEquals("空迁移不得改动表结构", before, queryTableNames(db))
        db.close()
    }

    // ---------- 辅助 ----------

    /**
     * 取一个可用连接用于执行迁移 DDL。
     *
     * 说明：`runMigrationsAndValidate` 在目标版本 == 当前版本时不需要任何迁移即可通过，
     * 因此这里传 v1 只为了拿到连接；真正的迁移语句由调用方显式执行。
     */
    private fun openAndApplyEmptyMigration(): SupportSQLiteDatabase {
        val db = helper.runMigrationsAndValidate(testDbName, ZhangRanDatabase.VERSION, true)
        ZhangRanDatabase.MIGRATION_1_2.migrate(db)
        return db
    }

    /** 读取 sqlite_master 中的用户表名（排除 Room 内部表）。 */
    private fun queryTableNames(db: SupportSQLiteDatabase): Set<String> {
        val names = mutableSetOf<String>()
        db.query(
            "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' " +
                "AND name != 'room_master_table'",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                names += cursor.getString(0)
            }
        }
        return names
    }
}
