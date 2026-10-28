/**
 * 校验与原子性测试（`M4b-2` A1、A11；V1–V7）。
 *
 * 校验：损坏 ZIP / manifest / 版本 / 缺文件 / 摘要不符 / JSON 不可解析 全部拒绝且不写数据。
 * 原子性：Replace 导入中途 SQLite 约束失败 → Room 整体回滚，恢复前数据仍在。
 */
package com.zhangran.app.data.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RestoreAtomicityTest {

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

    private fun reader() = BackupReader()

    private fun assertRejected(bytes: ByteArray, messagePart: String? = null) {
        val error = runCatching { reader().read(bytes.inputStream()) }.exceptionOrNull()
        assertTrue("必须抛出 BackupValidationException，实际：$error", error is BackupValidationException)
        if (messagePart != null) {
            assertTrue(
                "错误提示应包含「$messagePart」，实际：${error?.message}",
                error?.message?.contains(messagePart) == true,
            )
        }
    }

    // ------------------------------------------------------------------ V1–V6

    @Test
    fun V1_损坏ZIP被拒绝() {
        assertRejected("this is not a zip".toByteArray(), "损坏")
    }

    @Test
    fun V2_manifest缺失或不可解析被拒绝() {
        val entries = readZipEntries(validZipFor())
        val dataFiles = entries.filterKeys { it != BACKUP_MANIFEST_FILE_NAME }
        assertRejected(zipBytes(dataFiles, "{}".toByteArray()), "损坏")
    }

    @Test
    fun V3_更高版本被拒绝() {
        val entries = readZipEntries(validZipFor())
        val dataFiles = entries.filterKeys { it != BACKUP_MANIFEST_FILE_NAME }
        val bytes = zipBytes(dataFiles, BackupWriter.encode(manifest(backupVersion = 2)))
        assertRejected(bytes, "更新版本")
    }

    @Test
    fun V3_更旧版本被拒绝() {
        val entries = readZipEntries(validZipFor())
        val dataFiles = entries.filterKeys { it != BACKUP_MANIFEST_FILE_NAME }
        val bytes = zipBytes(dataFiles, BackupWriter.encode(manifest(backupVersion = 0)))
        assertRejected(bytes, "过旧")
    }

    @Test
    fun V4_缺少文件被拒绝() {
        val entries = readZipEntries(validZipFor())
        val manifestBytes = entries.getValue(BACKUP_MANIFEST_FILE_NAME)
        val dataFiles = entries.filterKeys { it != BACKUP_MANIFEST_FILE_NAME && it != "categories.json" }
        assertRejected(zipBytes(dataFiles, manifestBytes), "缺少")
    }

    @Test
    fun V5_摘要不符被拒绝() {
        val entries = readZipEntries(validZipFor())
        val manifestBytes = entries.getValue(BACKUP_MANIFEST_FILE_NAME)
        val mutated = LinkedHashMap(entries)
        val original = mutated.getValue("categories.json")
        val flipped = original.copyOf()
        flipped[0] = (flipped[0].toInt() xor 0x01).toByte()
        mutated["categories.json"] = flipped
        assertRejected(zipBytes(mutated.filterKeys { it != BACKUP_MANIFEST_FILE_NAME }, manifestBytes), "摘要")
    }

    @Test
    fun V6_JSON不可解析被拒绝() {
        // 手工构造：字节本身与 manifest 摘要一致，但内容不是合法 JSON
        val files = listOf(
            "transactions.json" to "not-json".toByteArray(),
            "categories.json" to BackupWriter.encode(emptyList<CategoryDto>()),
            "accounts.json" to BackupWriter.encode(emptyList<AccountDto>()),
            "budgets.json" to BackupWriter.encode(emptyList<BudgetDto>()),
            "rules.json" to BackupWriter.encode(emptyList<ParsingRuleDto>()),
            "pending.json" to BackupWriter.encode(emptyList<TransactionDto>()),
            "evidence.json" to BackupWriter.encode(emptyList<CaptureEvidenceDto>()),
            "duplicate_decisions.json" to BackupWriter.encode(emptyList<DuplicateDecisionDto>()),
            "capture_logs.json" to BackupWriter.encode(emptyList<CaptureLogDto>()),
            "field_edits.json" to BackupWriter.encode(emptyList<FieldEditDto>()),
            "settings.json" to BackupWriter.encode(listOf(SettingsDto("SYSTEM", 0.8, 0.5))),
        )
        val bytes = BackupWriter.buildZip(
            files = files.map { (name, content) -> BackupFileContent(name, content, 0) },
            appVersion = AppVersion("0.1.0", 1),
            createdAt = TEST_NOW,
            createdAtZone = "UTC",
        ).bytes
        assertRejected(bytes, "无法解析")
    }

    @Test
    fun V7_校验失败不修改任何数据() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "keep",
                name = "保留",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 0,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
        val before = database.categoryDao().listAll()

        assertRejected("broken".toByteArray())

        assertEquals(before, database.categoryDao().listAll())
    }

    // ------------------------------------------------------------------ A11 原子性

    @Test
    fun A11_导入中途失败整体回滚() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "keep",
                name = "保留分类",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 0,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )

        // 重复主键 → 导入第二条时 SQLite 约束失败（发生在清空+第一条插入之后）
        val duplicateCategories = listOf(
            categoryDto("dup", name = "第一条", updatedAt = 10L),
            categoryDto("dup", name = "第二条", updatedAt = 20L),
        )

        val result = runCatching {
            restoreUseCase(database, settings, context).restore(
                payload(categories = duplicateCategories),
                RestoreMode.REPLACE,
            )
        }

        assertTrue("重复主键应导致恢复失败", result.isFailure)
        assertEquals("旧数据必须仍在", "保留分类", database.categoryDao().findById("keep")?.name)
        assertNull("部分导入必须被回滚", database.categoryDao().findById("dup"))
    }
}
