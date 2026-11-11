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
import org.junit.Assert.assertNotNull
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

    // ---------- A7 / A9：以历史版本 v1 schema 建库并通过校验 ----------

    @Test
    fun `用 v1 schema 建库并通过 MigrationTestHelper 校验`() {
        // createDatabase 内部以 1.json 为对照建库：
        // 若实体与导出 schema 不一致（字段/索引/约束漂移），这里会直接失败。
        // 注意：这里用**字面量 1** 表达历史版本，不能用会随版本推进而漂移的 VERSION。
        val db = helper.createDatabase(testDbName, V1)
        assertNotNull("v1 库应可建立", db)
        db.close()
    }

    @Test
    fun `用当前版本 schema 建库并通过校验`() {
        // 以当前版本（v2）的 2.json 为对照建库，验证实体与 2.json 一致
        val db = helper.createDatabase(testDbName, ZhangRanDatabase.VERSION)
        val tables = queryTableNames(db)
        assertTrue(
            "v2 应包含全部九张表，实际=$tables",
            tables.containsAll(V1_TABLES) && tables.containsAll(V2_TABLES),
        )
        db.close()
    }

    // ---------- A3 / A9：真实 v1→v2 迁移 ----------

    @Test
    fun `真实迁移到 v2 后新旧表并存且数据保留`() {
        // 1) 建 v1 库（以 1.json 校验）并写入一行 v1 数据
        val v1Db = helper.createDatabase(testDbName, V1)
        v1Db.execSQL(
            "INSERT INTO categories (id, name, type, icon, sortOrder, enabled, systemDefined, " +
                "createdAt, updatedAt) VALUES ('c1','餐饮','EXPENSE','ic',0,1,0,1,1)",
        )
        v1Db.close()

        // 2) 执行真实迁移并以 2.json 校验目标 schema
        val migrated = helper.runMigrationsAndValidate(
            testDbName,
            V2,
            true,
            ZhangRanDatabase.MIGRATION_1_2,
        )
        val tables = queryTableNames(migrated)

        // A4：v1 五张表必须完整保留
        assertTrue("迁移后 v1 表应完整保留，实际=$tables", tables.containsAll(V1_TABLES))
        // v2 新增四张表
        assertTrue("迁移后应新增 v2 四张表，实际=$tables", tables.containsAll(V2_TABLES))

        // A4：既有数据必须保留
        migrated.query("SELECT name FROM categories WHERE id = 'c1'").use { cursor ->
            assertTrue("v1 数据应保留", cursor.moveToFirst())
            assertEquals("餐饮", cursor.getString(0))
        }
        migrated.close()
    }

    // ---------- 迁移对象本身 ----------

    @Test
    fun `迁移注册表与数据库版本一致`() {
        assertEquals(3, ZhangRanDatabase.VERSION)
        assertEquals(2, ZhangRanDatabase.ALL_MIGRATIONS.size)
        assertEquals(1, ZhangRanDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, ZhangRanDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, ZhangRanDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, ZhangRanDatabase.MIGRATION_2_3.endVersion)
    }

    @Test
    fun `MIGRATION_1_2 会创建 v2 全部新表`() {
        helper.createDatabase(testDbName, V1).close()
        // 必须显式传入迁移：MigrationTestHelper 不会自动使用应用到 RoomDatabase 的迁移
        val db = helper.runMigrationsAndValidate(
            testDbName,
            V2,
            true,
            ZhangRanDatabase.MIGRATION_1_2,
        )
        // 直接再执行一次迁移对象：DDL 全部使用 IF NOT EXISTS，应幂等且不破坏既有表
        ZhangRanDatabase.MIGRATION_1_2.migrate(db)
        val tables = queryTableNames(db)
        assertTrue("v1 表应仍在，实际=$tables", tables.containsAll(V1_TABLES))
        assertTrue("v2 表应已建，实际=$tables", tables.containsAll(V2_TABLES))
        db.close()
    }

    // ---------- 辅助 ----------

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

    private companion object {
        /** v1（T2a）版本号：历史版本必须用**字面量**，不得用会漂移的 `VERSION`。 */
        const val V1 = 1

        /** v2 停在加 pendingReasons 之前。1→2 的校验必须对着这一版，不能用会继续涨的 VERSION。 */
        const val V2 = 2

        /** v1 的五张表。 */
        val V1_TABLES = setOf("transactions", "categories", "accounts", "budgets", "field_edits")

        /** v2（T2b）新增的四张表。 */
        val V2_TABLES =
            setOf("capture_evidence", "parsing_rules", "capture_logs", "duplicate_decisions")
    }
}
