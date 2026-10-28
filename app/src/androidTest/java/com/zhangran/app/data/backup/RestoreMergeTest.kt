/**
 * Merge 恢复测试（`M4b-2` A3–A5；含公共测试支撑）。
 *
 * 使用内存 Room + 真实 `RestoreBackupUseCase`；备份载荷直接构造 DTO（格式由
 * `BackupWriterTest`/`RestoreAtomicityTest` 的解析测试覆盖）。
 */
package com.zhangran.app.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.data.settings.SettingsWriteResult
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.ExportBackupUseCase
import com.zhangran.app.domain.usecase.RestoreBackupUseCase
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// ------------------------------------------------------------------ 公共支撑

internal val TEST_NOW: Long = 1_000_000L

internal class FakeSettingsRepository : SettingsRepository {
    var current = AppSettings()
    private val state = MutableStateFlow(current)

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        current = current.copy(themeMode = mode)
        state.value = current
    }

    override suspend fun setThresholds(
        overallConfidenceThreshold: Double,
        fieldConfidenceThreshold: Double,
    ): SettingsWriteResult {
        current = current.copy(
            overallConfidenceThreshold = overallConfidenceThreshold,
            fieldConfidenceThreshold = fieldConfidenceThreshold,
        )
        state.value = current
        return SettingsWriteResult.Success
    }
}

internal fun openTestDatabase(context: Context): ZhangRanDatabase =
    Room.inMemoryDatabaseBuilder(context, ZhangRanDatabase::class.java)
        .allowMainThreadQueries()
        .build()

internal fun restoreUseCase(
    database: ZhangRanDatabase,
    settings: SettingsRepository,
    context: Context,
    clock: Clock = Clock.fixed(Instant.ofEpochMilli(TEST_NOW), ZoneOffset.UTC),
): RestoreBackupUseCase = RestoreBackupUseCase(
    database = database,
    settingsRepository = settings,
    exportBackup = ExportBackupUseCase(database, settings, clock),
    invariantFixer = RestoreInvariantFixer(database),
    context = context,
    clock = clock,
)

internal fun manifest(
    backupVersion: Int = BACKUP_VERSION,
): BackupManifest = BackupManifest(
    backupVersion = backupVersion,
    appVersionName = "0.1.0",
    appVersionCode = 1,
    createdAt = TEST_NOW,
    createdAtZone = "UTC",
    files = BACKUP_DATA_FILE_NAMES.map {
        BackupFileEntry(name = it, byteSize = 0, sha256 = "0".repeat(64), recordCount = 0)
    },
)

internal fun payload(
    transactions: List<TransactionDto> = emptyList(),
    pending: List<TransactionDto> = emptyList(),
    categories: List<CategoryDto> = emptyList(),
    accounts: List<AccountDto> = emptyList(),
    budgets: List<BudgetDto> = emptyList(),
    rules: List<ParsingRuleDto> = emptyList(),
    evidence: List<CaptureEvidenceDto> = emptyList(),
    duplicates: List<DuplicateDecisionDto> = emptyList(),
    logs: List<CaptureLogDto> = emptyList(),
    fieldEdits: List<FieldEditDto> = emptyList(),
    settings: SettingsDto = SettingsDto("SYSTEM", 0.8, 0.5),
    version: Int = BACKUP_VERSION,
): RestorePayload = RestorePayload(
    manifest = manifest(version),
    settings = settings,
    transactions = transactions,
    pending = pending,
    categories = categories,
    accounts = accounts,
    budgets = budgets,
    rules = rules,
    evidence = evidence,
    duplicateDecisions = duplicates,
    captureLogs = logs,
    fieldEdits = fieldEdits,
)

internal fun categoryDto(
    id: String,
    name: String = "餐饮",
    type: CategoryType = CategoryType.EXPENSE,
    updatedAt: Long = 100L,
    sortOrder: Int = 0,
    enabled: Boolean = true,
    systemDefined: Boolean = false,
): CategoryDto = CategoryDto(
    id = id,
    name = name,
    type = type,
    icon = "food",
    sortOrder = sortOrder,
    enabled = enabled,
    systemDefined = systemDefined,
    createdAt = 1L,
    updatedAt = updatedAt,
)

internal fun accountDto(
    id: String,
    name: String = "现金",
    updatedAt: Long = 100L,
    isDefault: Boolean = false,
    enabled: Boolean = true,
): AccountDto = AccountDto(
    id = id,
    name = name,
    type = com.zhangran.app.domain.model.AccountType.CASH,
    channel = null,
    last4 = "1234",
    enabled = enabled,
    sortOrder = 0,
    isDefault = isDefault,
    createdAt = 1L,
    updatedAt = updatedAt,
)

internal fun transactionDto(
    id: String,
    amountMinor: Long = 1_000L,
    updatedAt: Long = 100L,
    merchant: String? = "本地商户",
    note: String? = null,
    state: TransactionState = TransactionState.CONFIRMED,
    categoryId: String? = null,
    accountId: String? = null,
): TransactionDto = TransactionDto(
    id = id,
    type = TransactionType.EXPENSE,
    amountMinor = amountMinor,
    currency = Currency.CNY,
    merchant = merchant,
    categoryId = categoryId,
    accountId = accountId,
    transactionTime = 1_000L,
    transactionTimeZone = "Asia/Shanghai",
    note = note,
    state = state,
    source = CaptureSource.MANUAL,
    createdAt = 1L,
    updatedAt = updatedAt,
)

internal fun fieldEditDto(
    transactionId: String,
    fieldName: String,
    editedAt: Long = 100L,
): FieldEditDto = FieldEditDto(
    transactionId = transactionId,
    fieldName = fieldName,
    editedAt = editedAt,
    editedValueHash = "hash",
)

internal fun budgetDto(
    id: String,
    periodKey: String = "2026-10",
    categoryId: String? = null,
    limitMinor: Long = 10_000L,
    updatedAt: Long = 100L,
): BudgetDto = BudgetDto(
    id = id,
    scope = if (categoryId == null) {
        com.zhangran.app.domain.model.BudgetScope.TOTAL
    } else {
        com.zhangran.app.domain.model.BudgetScope.CATEGORY
    },
    categoryId = categoryId,
    period = com.zhangran.app.domain.model.BudgetPeriod.MONTH,
    periodKey = periodKey,
    limitMinor = limitMinor,
    enabled = true,
    createdAt = 1L,
    updatedAt = updatedAt,
)

// ------------------------------------------------------------------ Merge 测试

@RunWith(AndroidJUnit4::class)
class RestoreMergeTest {

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
    fun A3_新id插入_较新更新_相同保留本地并记冲突() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "c1",
                name = "本地餐饮",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 0,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 200L,
            ),
        )

        val report = restoreUseCase(database, settings, context).restore(
            payload(
                categories = listOf(
                    categoryDto("c1", name = "备份餐饮", updatedAt = 300L),
                    categoryDto("c1-new", name = "新增分类", updatedAt = 50L),
                ),
            ),
            RestoreMode.MERGE,
        )

        assertEquals("备份餐饮", database.categoryDao().findById("c1")?.name)
        assertEquals("新增分类", database.categoryDao().findById("c1-new")?.name)
        assertEquals(1, report.inserted[RestoreTables.CATEGORIES])
        assertEquals(1, report.updated[RestoreTables.CATEGORIES])
        assertEquals(0, report.conflicts.size)

        // 本地已有且 updatedAt 相同 → 保留本地并记冲突
        database.categoryDao().insert(
            CategoryEntity(
                id = "c2",
                name = "本地版本",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 1,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 200L,
            ),
        )
        val second = restoreUseCase(database, settings, context).restore(
            payload(
                categories = listOf(categoryDto("c2", name = "备份版本", updatedAt = 200L)),
            ),
            RestoreMode.MERGE,
        )
        assertEquals("本地版本", database.categoryDao().findById("c2")?.name)
        assertEquals(1, second.conflicts.size)
        assertEquals("c2", second.conflicts.single().id)
    }

    @Test
    fun A4_受保护字段保留本地其他字段取备份() = runBlocking {
        database.transactionDao().insert(
            TransactionEntity(
                id = "t1",
                type = TransactionType.EXPENSE,
                amountMinor = 1_000L,
                currency = Currency.CNY,
                merchant = "本地商户",
                categoryId = "cat-local",
                accountId = "acc-local",
                transactionTime = 1_000L,
                transactionTimeZone = "Asia/Shanghai",
                note = "本地备注",
                state = TransactionState.CONFIRMED,
                source = CaptureSource.MANUAL,
                confirmedByUser = true,
                createdAt = 1L,
                updatedAt = 200L,
            ),
        )
        database.fieldEditDao().insert(FieldEditEntity("t1", "amountMinor", 100L, "hash"))
        database.fieldEditDao().insert(FieldEditEntity("t1", "categoryId", 100L, "hash"))
        database.fieldEditDao().insert(FieldEditEntity("t1", "accountId", 100L, "hash"))

        restoreUseCase(database, settings, context).restore(
            payload(
                transactions = listOf(
                    transactionDto(
                        id = "t1",
                        amountMinor = 2_000L,
                        updatedAt = 300L,
                        merchant = "备份商户",
                        note = "备份备注",
                        categoryId = "cat-backup",
                        accountId = "acc-backup",
                    ),
                ),
            ),
            RestoreMode.MERGE,
        )

        val merged = database.transactionDao().findByIdIncludingDeleted("t1")
        assertNotNull(merged)
        assertEquals("受保护金额保留本地", 1_000L, merged!!.amountMinor)
        assertEquals("受保护分类保留本地", "cat-local", merged.categoryId)
        assertEquals("受保护账户保留本地", "acc-local", merged.accountId)
        assertEquals("未受保护商户取备份", "备份商户", merged.merchant)
        assertEquals("未受保护备注取备份", "备份备注", merged.note)
        assertEquals(300L, merged.updatedAt)
        assertTrue("用户编辑过的记录保持 confirmedByUser", merged.confirmedByUser)
    }

    @Test
    fun A5_field_edits表本身也合并() = runBlocking {
        database.transactionDao().insert(
            TransactionEntity(
                id = "t1",
                type = TransactionType.EXPENSE,
                amountMinor = 1_000L,
                currency = Currency.CNY,
                transactionTime = 1_000L,
                transactionTimeZone = "Asia/Shanghai",
                state = TransactionState.CONFIRMED,
                source = CaptureSource.MANUAL,
                createdAt = 1L,
                updatedAt = 200L,
            ),
        )
        database.fieldEditDao().insert(FieldEditEntity("t1", "merchant", 100L, "hash"))

        val report = restoreUseCase(database, settings, context).restore(
            payload(fieldEdits = listOf(fieldEditDto("t1", "note", editedAt = 200L))),
            RestoreMode.MERGE,
        )

        assertNotNull(database.fieldEditDao().find("t1", "note"))
        assertNotNull(database.fieldEditDao().find("t1", "merchant"))
        assertEquals(1, report.inserted[RestoreTables.FIELD_EDITS])
    }

    @Test
    fun A2_仅恢复设置不改任何表() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "c1",
                name = "餐饮",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 0,
                enabled = true,
                systemDefined = false,
                createdAt = 1L,
                updatedAt = 200L,
            ),
        )
        val before = database.categoryDao().listAll()

        val report = restoreUseCase(database, settings, context).restore(
            payload(settings = SettingsDto("DARK", 0.9, 0.4)),
            RestoreMode.SETTINGS_ONLY,
        )

        assertEquals(before, database.categoryDao().listAll())
        assertEquals(ThemeMode.DARK, settings.current.themeMode)
        assertEquals(0.9, settings.current.overallConfidenceThreshold, 0.0)
        assertTrue(report.settingsUpdated)
    }
}

// ------------------------------------------------------------------ ZIP 构造辅助（供校验测试）

internal fun zipBytes(files: Map<String, ByteArray>, manifestBytes: ByteArray): ByteArray {
    val buffer = ByteArrayOutputStream()
    ZipOutputStream(buffer).use { zip ->
        zip.putNextEntry(ZipEntry(BACKUP_MANIFEST_FILE_NAME))
        zip.write(manifestBytes)
        zip.closeEntry()
        files.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return buffer.toByteArray()
}

internal fun readZipEntries(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
    val result = linkedMapOf<String, ByteArray>()
    ZipInputStream(bytes.inputStream(), Charsets.UTF_8).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            result[entry.name] = zip.readBytes()
            zip.closeEntry()
        }
    }
    return result
}

internal fun validZipFor(
    transactions: List<TransactionDto> = emptyList(),
    categories: List<CategoryDto> = listOf(categoryDto("c1")),
): ByteArray {
    val files = listOf(
        "transactions.json" to BackupWriter.encode(transactions),
        "categories.json" to BackupWriter.encode(categories),
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
    return BackupWriter.buildZip(
        files = files.map { (name, bytes) -> BackupFileContent(name, bytes, 0) },
        appVersion = AppVersion("0.1.0", 1),
        createdAt = TEST_NOW,
        createdAtZone = "UTC",
    ).bytes
}

internal fun readBackup(input: InputStream): RestorePayload = BackupReader().read(input)
