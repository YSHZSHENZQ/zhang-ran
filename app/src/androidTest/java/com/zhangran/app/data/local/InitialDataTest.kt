/**
 * 初始数据的 instrumented 断言（M2d-0 的 A2–A7、A10）。
 *
 * 对应 `docs/08` §9 新增的 5 条断言：
 * - A2 首次创建后：支出 12 / 收入 6 / 账户 1 且 `isDefault = true`
 * - A3 幂等：再次启动不重复插入
 * - A4 删除不复活：删掉预置分类后重启，不得回来
 * - A5 升级不重跑：migration 本身不写初始数据
 * - A6 `systemDefined = true`，「其他」在两个 type 下各一个
 *
 * 用**真实文件库**（而非 in-memory）：因为要验证"关闭再打开"（模拟重启）之后的行为，
 * 而"是否已初始化"的标记存在 SharedPreferences（文件级）。
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.domain.model.CategoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InitialDataTest {

    private companion object {
        const val DB_NAME = "test-initial-data.db"
        const val PREFS_NAME = "test-initial-data-prefs"
        const val FIXED_NOW = 1_791_610_182_000L
        const val MIGRATION_DB = "test-initial-data-migration.db"
    }

    private lateinit var context: Context
    private lateinit var database: ZhangRanDatabase
    private lateinit var marker: InitializationMarker
    private var idSeq = 0

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ZhangRanDatabase::class.java,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
        context.deleteDatabase(MIGRATION_DB)
        marker = InitializationMarker(context, PREFS_NAME)
        marker.reset()
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DB_NAME)
        context.deleteDatabase(MIGRATION_DB)
        marker.reset()
    }

    /** 打开（或重开）真实文件库；带 onCreate 钩子（与生产一致）。 */
    private fun openDatabase(): ZhangRanDatabase = Room.databaseBuilder(
        context,
        ZhangRanDatabase::class.java,
        DB_NAME,
    )
        .addCallback(ZhangRanDatabase.initializationCallback(marker))
        .addMigrations(*ZhangRanDatabase.ALL_MIGRATIONS)
        .allowMainThreadQueries()
        .build()

    private fun newInitializer() = DatabaseInitializer(
        marker = marker,
        clock = { FIXED_NOW },
        idGenerator = { "id-${++idSeq}" },
    )

    // ------------------------------------------------------------ A2 首次写入

    @Test
    fun A2_首次初始化写入12加6个分类与1个默认账户() = runBlocking {
        val result = newInitializer().ensureInitialized(database)

        assertEquals(18, result.insertedCategories)
        assertEquals(1, result.insertedAccounts)

        val categories = database.categoryDao().listAll()
        assertEquals(18, categories.size)
        assertEquals(12, categories.count { it.type == CategoryType.EXPENSE })
        assertEquals(6, categories.count { it.type == CategoryType.INCOME })

        val accounts = database.accountDao().listAll()
        assertEquals(1, accounts.size)
        assertTrue("初始账户必须是默认账户", accounts.single().isDefault)
        assertEquals("现金", accounts.single().name)
        assertEquals(1, database.accountDao().countDefaults())
    }

    @Test
    fun A2_支出分类按文档顺序落库() = runBlocking {
        newInitializer().ensureInitialized(database)

        val expense = database.categoryDao().listEnabledByType(CategoryType.EXPENSE)
            .sortedBy { it.sortOrder }
            .map { it.name }
        assertEquals(
            listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "学习", "通讯", "人情", "旅行", "宠物", "其他"),
            expense,
        )
    }

    // ------------------------------------------------------------ A6 systemDefined 与兜底项

    @Test
    fun A6_预置分类标记为systemDefined且其他在两个type下各一个() = runBlocking {
        newInitializer().ensureInitialized(database)

        val all = database.categoryDao().listAll()
        assertTrue("所有预置分类都必须 systemDefined = true", all.all { it.systemDefined })

        val others = all.filter { it.name == "其他" }
        assertEquals("「其他」在支出与收入下各一个", 2, others.size)
        assertEquals(1, others.count { it.type == CategoryType.EXPENSE })
        assertEquals(1, others.count { it.type == CategoryType.INCOME })
    }

    // ------------------------------------------------------------ A3 幂等

    @Test
    fun A3_已完成时再次初始化不再插入() = runBlocking {
        newInitializer().ensureInitialized(database)
        val before = database.categoryDao().listAll().size
        val accountsBefore = database.accountDao().listAll().size

        val second = newInitializer().ensureInitialized(database)

        assertTrue("已完成时应整体跳过", second.skipped)
        assertEquals(0, second.insertedCategories)
        assertEquals(before, database.categoryDao().listAll().size)
        assertEquals(accountsBefore, database.accountDao().listAll().size)
    }

    @Test
    fun A3_标记被重置时按name判重也不重复插入() = runBlocking {
        newInitializer().ensureInitialized(database)
        val before = database.categoryDao().listAll().size

        // 模拟"标记丢失但数据已在"（例如偏好被清）：判重必须兜住
        marker.reset()
        val retry = newInitializer().ensureInitialized(database)

        assertEquals(0, retry.insertedCategories)
        assertEquals(0, retry.insertedAccounts)
        assertEquals(before, database.categoryDao().listAll().size)
    }

    // ------------------------------------------------------------ A4 删除不复活

    @Test
    fun A4_删除预置分类后重启不复活() = runBlocking {
        newInitializer().ensureInitialized(database)

        val target = database.categoryDao().listAll()
            .first { it.type == CategoryType.EXPENSE && it.name == "宠物" }
        // CategoryDao 未提供物理删除（`data/local/dao` 本任务不可改），用原生 SQL 模拟"用户删除"
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM categories WHERE id = ?",
            arrayOf<Any>(target.id),
        )
        assertEquals(17, database.categoryDao().listAll().size)

        // 关闭并重开（模拟重启）；标记仍为已完成
        database.close()
        database = openDatabase()

        val restart = newInitializer().ensureInitialized(database)

        assertTrue("重启后应跳过（标记已完成）", restart.skipped)
        assertNull(
            "被用户删除的预置分类不得复活",
            database.categoryDao().listAll().firstOrNull { it.name == "宠物" },
        )
        assertEquals(17, database.categoryDao().listAll().size)
    }

    // ------------------------------------------------------------ A5 升级不重跑

    @Test
    fun A5_migration本身不写入初始数据() {
        // v1 建库（不含 v2 的四张表），随后迁移到 v2
        migrationHelper.createDatabase(MIGRATION_DB, 1).close()
        migrationHelper.runMigrationsAndValidate(
            MIGRATION_DB,
            2,
            true,
            ZhangRanDatabase.MIGRATION_1_2,
        ).use { migrated ->
            migrated.query("SELECT COUNT(*) FROM categories").use { cursor ->
                cursor.moveToFirst()
                assertEquals("migration 不得触发初始数据写入", 0, cursor.getInt(0))
            }
            migrated.query("SELECT COUNT(*) FROM accounts").use { cursor ->
                cursor.moveToFirst()
                assertEquals("migration 不得触发初始数据写入", 0, cursor.getInt(0))
            }
        }
    }

    // ------------------------------------------------------------ A10 初始化后可用

    @Test
    fun A10_初始化后记账所需的分类与账户都可读到() = runBlocking {
        newInitializer().ensureInitialized(database)

        val expenseCategories = database.categoryDao().listEnabledByType(CategoryType.EXPENSE)
        val incomeCategories = database.categoryDao().listEnabledByType(CategoryType.INCOME)
        val defaultAccount = database.accountDao().findDefault()

        assertFalse("支出分类不得为空（否则记账无法选择）", expenseCategories.isEmpty())
        assertFalse("收入分类不得为空", incomeCategories.isEmpty())
        assertNotNull("必须存在默认账户", defaultAccount)
        assertEquals("现金", defaultAccount!!.name)
    }
}
