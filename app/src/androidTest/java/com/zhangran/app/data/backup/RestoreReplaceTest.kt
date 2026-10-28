/**
 * Replace 恢复测试（`M4b-2` A6–A10、A12）。
 *
 * 覆盖：清空重建、设置覆盖、恢复前自动备份、I1/I3/I5/I6 修复、报告内容。
 */
package com.zhangran.app.data.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.data.settings.ThemeMode
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreReplaceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: ZhangRanDatabase
    private lateinit var settings: FakeSettingsRepository

    @Before
    fun setUp() {
        database = openTestDatabase(context)
        settings = FakeSettingsRepository()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun A6_A8_A9_A10_替换清空并修复不变量() = runBlocking {
        // 本地旧数据（应被清空）
        database.categoryDao().insert(
            CategoryEntity(
                id = "old",
                name = "旧分类",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 0,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        val backupCategories = listOf(categoryDto("c1", name = "餐饮", updatedAt = 10L))
        val backupAccounts = listOf(
            accountDto("a-old", name = "旧默认", updatedAt = 100L, isDefault = true),
            accountDto("a-new", name = "新默认", updatedAt = 200L, isDefault = true),
        )
        val backupBudgets = listOf(
            budgetDto("b-old", limitMinor = 100L, updatedAt = 100L),
            budgetDto("b-new", limitMinor = 200L, updatedAt = 200L),
        )
        val backupTransactions = listOf(
            transactionDto(
                id = "t1",
                updatedAt = 10L,
                categoryId = "ghost-category",
                accountId = "ghost-account",
            ),
        )

        val report = restoreUseCase(database, settings, context).restore(
            payload(
                transactions = backupTransactions,
                categories = backupCategories,
                accounts = backupAccounts,
                budgets = backupBudgets,
                settings = SettingsDto("DARK", 0.9, 0.4),
            ),
            RestoreMode.REPLACE,
        )

        // 清空 + 导入
        assertNull("旧分类必须被清空", database.categoryDao().findById("old"))
        assertEquals("餐饮", database.categoryDao().findById("c1")?.name)
        assertEquals("a-new", database.accountDao().findById("a-new")?.id)
        assertEquals(1, database.accountDao().countDefaults())

        // I1：支出已有餐饮、收入缺「其他」→ 补建 1 条
        val fallback = database.categoryDao().listAll()
            .filter { it.type == CategoryType.INCOME && it.name == FALLBACK_CATEGORY_NAME }
        assertEquals(1, fallback.size)
        assertTrue(fallback.single().systemDefined)
        assertTrue(fallback.single().enabled)

        // I3：两个默认 → 保留 updatedAt 最新
        assertTrue(database.accountDao().findById("a-new")!!.isDefault)
        assertFalse(database.accountDao().findById("a-old")!!.isDefault)

        // I5：预算唯一 → 保留最新
        assertEquals(1, database.budgetDao().listAll().size)
        assertEquals("b-new", database.budgetDao().listAll().single().id)

        // I6：悬空外键保留原值
        val restored = database.transactionDao().findByIdIncludingDeleted("t1")
        assertEquals("ghost-category", restored?.categoryId)
        assertEquals("ghost-account", restored?.accountId)
        assertTrue(report.invariantFixes.any { it.contains("I6") && it.contains("分类") })

        // 设置按备份覆盖
        assertEquals(ThemeMode.DARK, settings.current.themeMode)
        assertEquals(0.9, settings.current.overallConfidenceThreshold, 0.0)

        // 报告
        assertEquals(RestoreMode.REPLACE, report.mode)
        assertEquals(1, report.inserted[RestoreTables.CATEGORIES])
        assertEquals(2, report.inserted[RestoreTables.ACCOUNTS])
        assertEquals(1, report.inserted[RestoreTables.TRANSACTIONS])
        assertTrue(report.settingsUpdated)
        assertNotNull(report.durationMs)

        // A7：恢复前备份落在私有目录
        val preBackup = File(context.filesDir, "restore-pre-backups/${report.preRestoreBackupFileName}")
        assertTrue("恢复前备份必须生成", preBackup.exists())
        assertTrue(preBackup.length() > 0)
    }

    @Test
    fun A7_恢复前备份可被重新解析() = runBlocking {
        val report = restoreUseCase(database, settings, context).restore(
            payload(settings = SettingsDto("LIGHT", 0.7, 0.3)),
            RestoreMode.REPLACE,
        )

        val file = File(context.filesDir, "restore-pre-backups/${report.preRestoreBackupFileName}")
        val parsed = file.inputStream().use { readBackup(it) }
        assertEquals(BACKUP_VERSION, parsed.manifest.backupVersion)
        assertEquals(11, parsed.manifest.files.size)
    }
}
