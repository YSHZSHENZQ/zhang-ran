/**
 * 备份恢复用例（`M4b-2` §4.3–§4.7；规格 SSOT：`docs/02` §17）。
 *
 * 流程：
 * 1. 调用方先用 [com.zhangran.app.data.backup.BackupReader] 完成全部校验；
 * 2. 本用例把 DTO 映射为 Entity（映射失败 → 无写入）；
 * 3. 按模式执行：
 *    - Settings Only：只写设置；
 *    - Merge：单事务内逐表按 `updatedAt` 合并，交易逐字段保护，最后跑不变量修复；
 *    - Replace：先自动生成"恢复前备份"→ 单事务清空+导入+修复 → 提交后覆盖设置。
 *
 * 分类合并按 `(type, name)` 识别同一逻辑分类并重映射 id（`categories` 有唯一索引，
 * 且跨设备恢复时同名默认分类 id 不同）；预算按 `(periodKey, categoryId)` 处理键冲突。
 */
package com.zhangran.app.domain.usecase

import android.content.Context
import androidx.room.withTransaction
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.backup.AppVersion
import com.zhangran.app.data.backup.BackupValidationException
import com.zhangran.app.data.backup.RestoreEntities
import com.zhangran.app.data.backup.RestoreInvariantFixer
import com.zhangran.app.data.backup.RestoreMode
import com.zhangran.app.data.backup.RestorePayload
import com.zhangran.app.data.backup.RestoreReport
import com.zhangran.app.data.backup.RestoreTables
import com.zhangran.app.data.backup.RestoreTally
import com.zhangran.app.data.backup.toEntities
import com.zhangran.app.data.backup.toEntity
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.ParsingRuleEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.settings.SettingsDefaults
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.data.settings.SettingsWriteResult
import com.zhangran.app.data.settings.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Clock
import javax.inject.Inject

class RestoreBackupUseCase @Inject constructor(
    private val database: ZhangRanDatabase,
    private val settingsRepository: SettingsRepository,
    private val exportBackup: ExportBackupUseCase,
    private val invariantFixer: RestoreInvariantFixer,
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {

    /**
     * 执行恢复。
     *
     * @throws BackupValidationException 映射校验失败（未写入任何数据）
     * @throws Exception 事务失败（Room 自动回滚）
     */
    suspend fun restore(payload: RestorePayload, mode: RestoreMode): RestoreReport {
        val started = clock.millis()
        // V7：任何写入之前完成映射校验
        val entities = payload.toEntities()
        val tally = RestoreTally()
        var preRestoreBackup: String? = null
        var settingsUpdated = false
        val fixes = mutableListOf<String>()

        when (mode) {
            RestoreMode.SETTINGS_ONLY -> {
                val invalid = applySettings(payload)
                settingsUpdated = true
                if (invalid) tally.skip(RestoreTables.SETTINGS) else tally.update(RestoreTables.SETTINGS)
            }

            RestoreMode.MERGE -> {
                database.withTransaction {
                    val categoryRemap = mergeCategories(entities.categories, tally)
                    mergeAccounts(entities.accounts, tally)
                    mergeBudgets(entities.budgets, tally, categoryRemap)
                    mergeRules(entities.rules, tally)
                    mergeTransactions(entities, payload, tally, categoryRemap)
                    mergeEvidence(payload, tally)
                    mergeDuplicateDecisions(payload, tally)
                    mergeCaptureLogs(payload, tally)
                    mergeFieldEdits(payload, tally)
                    fixes += invariantFixer.fix(started)
                }
            }

            RestoreMode.REPLACE -> {
                // §4.5：恢复前自动备份，失败则中止（不触碰数据库）
                preRestoreBackup = createPreRestoreBackup(started)
                database.withTransaction {
                    clearAllTables()
                    insertAllTables(entities, tally)
                    fixes += invariantFixer.fix(started)
                }
                // DataStore 无法加入 Room 事务：数据库提交后覆盖设置
                applySettings(payload)
                settingsUpdated = true
                tally.update(RestoreTables.SETTINGS)
            }
        }

        return RestoreReport(
            mode = mode,
            inserted = tally.inserted,
            updated = tally.updated,
            skipped = tally.skipped,
            conflicts = tally.conflicts,
            invariantFixes = fixes,
            durationMs = clock.millis() - started,
            preRestoreBackupFileName = preRestoreBackup,
            settingsUpdated = settingsUpdated,
        )
    }

    // ------------------------------------------------------------------ Settings

    /** 写入设置；阈值/主题非法时使用默认值并返回 true（记入"跳过"）。 */
    private suspend fun applySettings(payload: RestorePayload): Boolean {
        val dto = payload.settings
        val themeValid = ThemeMode.entries.any { it.name == dto.themeMode }
        val overallValid = dto.overallConfidenceThreshold in 0.0..1.0
        val fieldValid = dto.fieldConfidenceThreshold in 0.0..1.0

        val theme = ThemeMode.entries.firstOrNull { it.name == dto.themeMode } ?: ThemeMode.SYSTEM
        val overall = if (overallValid) {
            dto.overallConfidenceThreshold
        } else {
            SettingsDefaults.OVERALL_CONFIDENCE_THRESHOLD
        }
        val field = if (fieldValid) {
            dto.fieldConfidenceThreshold
        } else {
            SettingsDefaults.FIELD_CONFIDENCE_THRESHOLD
        }

        settingsRepository.setThemeMode(theme)
        val result = settingsRepository.setThresholds(overall, field)
        if (result !is SettingsWriteResult.Success) {
            throw IllegalStateException("设置写入失败：$result")
        }
        return !(themeValid && overallValid && fieldValid)
    }

    // ------------------------------------------------------------------ Merge

    /**
     * 分类合并，返回「备份分类 id → 本地分类 id」的重映射。
     *
     * 相同 id 按 `updatedAt` 取新；不同 id 但 `(type, name)` 相同 → 视为同一逻辑分类，
     * 复用本地 id（并让交易/预算的引用一起重映射）；否则插入。
     */
    private suspend fun mergeCategories(
        backup: List<CategoryEntity>,
        tally: RestoreTally,
    ): Map<String, String> {
        val dao = database.categoryDao()
        val local = dao.listAll()
        val byId = local.associateBy { it.id }.toMutableMap()
        val byName = local.associate { (it.type to it.name) to it }.toMutableMap()
        val remap = mutableMapOf<String, String>()

        backup.forEach { source ->
            val sameId = byId[source.id]
            if (sameId != null) {
                when {
                    source.updatedAt > sameId.updatedAt -> {
                        dao.update(source)
                        byId[source.id] = source
                        byName[source.type to source.name] = source
                        tally.update(RestoreTables.CATEGORIES)
                    }

                    source.updatedAt < sameId.updatedAt -> tally.skip(RestoreTables.CATEGORIES)

                    else -> {
                        tally.skip(RestoreTables.CATEGORIES)
                        tally.conflict(RestoreTables.CATEGORIES, source.id, "updatedAt 相同，保留本地记录")
                    }
                }
                return@forEach
            }

            val sameName = byName[source.type to source.name]
            if (sameName != null && sameName.id != source.id) {
                remap[source.id] = sameName.id
                if (source.updatedAt > sameName.updatedAt) {
                    val merged = source.copy(id = sameName.id)
                    dao.update(merged)
                    byId[sameName.id] = merged
                    byName[source.type to source.name] = merged
                    tally.update(RestoreTables.CATEGORIES)
                } else {
                    tally.skip(RestoreTables.CATEGORIES)
                }
                tally.conflict(
                    RestoreTables.CATEGORIES,
                    source.id,
                    "按名称合并到本地分类 ${sameName.id}",
                )
                return@forEach
            }

            dao.insert(source)
            byId[source.id] = source
            byName[source.type to source.name] = source
            tally.insert(RestoreTables.CATEGORIES)
        }
        return remap
    }

    private suspend fun mergeAccounts(backup: List<AccountEntity>, tally: RestoreTally) {
        val dao = database.accountDao()
        val local = dao.listAll().associateBy { it.id }
        backup.forEach { row ->
            val current = local[row.id]
            when {
                current == null -> {
                    dao.insert(row)
                    tally.insert(RestoreTables.ACCOUNTS)
                }

                row.updatedAt > current.updatedAt -> {
                    dao.update(row)
                    tally.update(RestoreTables.ACCOUNTS)
                }

                row.updatedAt < current.updatedAt -> tally.skip(RestoreTables.ACCOUNTS)

                else -> {
                    tally.skip(RestoreTables.ACCOUNTS)
                    tally.conflict(RestoreTables.ACCOUNTS, row.id, "updatedAt 相同，保留本地记录")
                }
            }
        }
    }

    /**
     * 预算合并：`(periodKey, categoryId)` 唯一索引。备份较新 → 删除本地旧键记录再插入；
     * 否则跳过并记冲突。`categoryId` 先按分类重映射。
     */
    private suspend fun mergeBudgets(
        backup: List<BudgetEntity>,
        tally: RestoreTally,
        categoryRemap: Map<String, String>,
    ) {
        val dao = database.budgetDao()
        val local = dao.listAll()
        val localById = local.associateBy { it.id }
        val keyOwners = local.associate { (it.periodKey to it.categoryId) to it }.toMutableMap()

        suspend fun resolveKeyConflict(row: BudgetEntity): Boolean {
            val existing = keyOwners[row.periodKey to row.categoryId] ?: return true
            if (existing.id == row.id) return true
            if (row.updatedAt > existing.updatedAt) {
                database.openHelper.writableDatabase.execSQL(
                    "DELETE FROM budgets WHERE id = ?",
                    arrayOf(existing.id),
                )
                keyOwners.remove(existing.periodKey to existing.categoryId)
                tally.conflict(
                    RestoreTables.BUDGETS,
                    row.id,
                    "预算键冲突，已用较新的备份记录替换本地记录",
                )
                return true
            }
            tally.skip(RestoreTables.BUDGETS)
            tally.conflict(RestoreTables.BUDGETS, row.id, "预算键冲突，本地记录较新，保留本地")
            return false
        }

        backup.forEach { source ->
            val row = source.copy(
                categoryId = source.categoryId?.let { categoryRemap[it] ?: it },
            )
            val current = localById[row.id]
            when {
                current == null -> {
                    if (!resolveKeyConflict(row)) return@forEach
                    dao.insert(row)
                    keyOwners[row.periodKey to row.categoryId] = row
                    tally.insert(RestoreTables.BUDGETS)
                }

                row.updatedAt > current.updatedAt -> {
                    if (!resolveKeyConflict(row)) return@forEach
                    dao.update(row)
                    keyOwners[row.periodKey to row.categoryId] = row
                    tally.update(RestoreTables.BUDGETS)
                }

                row.updatedAt < current.updatedAt -> tally.skip(RestoreTables.BUDGETS)

                else -> {
                    tally.skip(RestoreTables.BUDGETS)
                    tally.conflict(RestoreTables.BUDGETS, row.id, "updatedAt 相同，保留本地记录")
                }
            }
        }
    }

    private suspend fun mergeRules(backup: List<ParsingRuleEntity>, tally: RestoreTally) {
        val dao = database.parsingRuleDao()
        val local = dao.listAll().associateBy { it.ruleId }
        backup.forEach { row ->
            val current = local[row.ruleId]
            when {
                current == null -> {
                    dao.insert(row)
                    tally.insert(RestoreTables.RULES)
                }

                row.updatedAt > current.updatedAt -> {
                    dao.update(row)
                    tally.update(RestoreTables.RULES)
                }

                row.updatedAt < current.updatedAt -> tally.skip(RestoreTables.RULES)

                else -> {
                    tally.skip(RestoreTables.RULES)
                    tally.conflict(RestoreTables.RULES, row.ruleId, "updatedAt 相同，保留本地记录")
                }
            }
        }
    }

    /**
     * 交易合并（M4 + `03` §11）：备份更新时逐字段合并，受保护字段保留本地值；
     * `categoryId` 按分类重映射；`pending.json` 作为 id 兜底。
     */
    private suspend fun mergeTransactions(
        entities: RestoreEntities,
        payload: RestorePayload,
        tally: RestoreTally,
        categoryRemap: Map<String, String>,
    ) {
        val dao = database.transactionDao()
        val local = dao.listAllIncludingDeleted().associateBy { it.id }
        val protectedFields = database.fieldEditDao()
            .listAll()
            .groupBy({ it.transactionId }, { it.fieldName })
            .mapValues { it.value.toSet() }

        val transactionIds = payload.transactions.map { it.id }.toSet()
        val backupRows = entities.transactions +
            payload.pending.filter { it.id !in transactionIds }.map { it.toEntity() }

        backupRows.forEach { source ->
            val row = source.copy(
                categoryId = source.categoryId?.let { categoryRemap[it] ?: it },
            )
            val current = local[row.id]
            when {
                current == null -> {
                    dao.insert(row)
                    tally.insert(RestoreTables.TRANSACTIONS)
                }

                row.updatedAt > current.updatedAt -> {
                    val protected = protectedFields[row.id].orEmpty()
                    dao.update(mergeTransactionProtected(current, row, protected))
                    tally.update(RestoreTables.TRANSACTIONS)
                }

                row.updatedAt < current.updatedAt -> tally.skip(RestoreTables.TRANSACTIONS)

                else -> {
                    tally.skip(RestoreTables.TRANSACTIONS)
                    tally.conflict(
                        RestoreTables.TRANSACTIONS,
                        row.id,
                        "updatedAt 相同，保留本地记录（字段保护优先）",
                    )
                }
            }
        }
    }

    /** 逐字段合并：受保护字段取本地，其余取备份；用户编辑过的记录保持 `confirmedByUser`。 */
    private fun mergeTransactionProtected(
        local: TransactionEntity,
        backup: TransactionEntity,
        protectedFields: Set<String>,
    ): TransactionEntity {
        fun <T> pick(field: String, localValue: T, backupValue: T): T =
            if (field in protectedFields) localValue else backupValue

        return backup.copy(
            merchant = pick("merchant", local.merchant, backup.merchant),
            amountMinor = pick("amountMinor", local.amountMinor, backup.amountMinor),
            type = pick("type", local.type, backup.type),
            categoryId = pick("categoryId", local.categoryId, backup.categoryId),
            accountId = pick("accountId", local.accountId, backup.accountId),
            sourceAccountId = pick("sourceAccountId", local.sourceAccountId, backup.sourceAccountId),
            targetAccountId = pick("targetAccountId", local.targetAccountId, backup.targetAccountId),
            transactionTime = pick("transactionTime", local.transactionTime, backup.transactionTime),
            note = pick("note", local.note, backup.note),
            confirmedByUser = local.confirmedByUser || backup.confirmedByUser,
        )
    }

    private suspend fun mergeEvidence(payload: RestorePayload, tally: RestoreTally) {
        val dao = database.captureEvidenceDao()
        val local = loadAllEvidence().associateBy { it.id }
        payload.evidence.forEach { dto ->
            val row = dto.toEntity()
            val current = local[row.id]
            when {
                current == null -> {
                    dao.insert(row)
                    tally.insert(RestoreTables.EVIDENCE)
                }

                row.capturedAt > current.capturedAt -> {
                    replaceEvidence(row)
                    tally.update(RestoreTables.EVIDENCE)
                }

                row.capturedAt < current.capturedAt -> tally.skip(RestoreTables.EVIDENCE)

                else -> {
                    tally.skip(RestoreTables.EVIDENCE)
                    tally.conflict(RestoreTables.EVIDENCE, row.id, "capturedAt 相同，保留本地记录")
                }
            }
        }
    }

    private suspend fun mergeDuplicateDecisions(payload: RestorePayload, tally: RestoreTally) {
        payload.duplicateDecisions.forEach { dto ->
            val row = dto.toEntity()
            val existing = database.query(
                "SELECT decidedAt FROM duplicate_decisions WHERE id = ?",
                arrayOf(row.id),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            when {
                existing == null -> {
                    database.duplicateDecisionDao().insert(row)
                    tally.insert(RestoreTables.DUPLICATES)
                }

                row.decidedAt > existing -> {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM duplicate_decisions WHERE id = ?",
                        arrayOf(row.id),
                    )
                    database.duplicateDecisionDao().insert(row)
                    tally.update(RestoreTables.DUPLICATES)
                }

                row.decidedAt < existing -> tally.skip(RestoreTables.DUPLICATES)

                else -> {
                    tally.skip(RestoreTables.DUPLICATES)
                    tally.conflict(RestoreTables.DUPLICATES, row.id, "decidedAt 相同，保留本地记录")
                }
            }
        }
    }

    private suspend fun mergeCaptureLogs(payload: RestorePayload, tally: RestoreTally) {
        payload.captureLogs.forEach { dto ->
            val row = dto.toEntity()
            val existing = database.query(
                "SELECT capturedAt FROM capture_logs WHERE id = ?",
                arrayOf(row.id),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }
            when {
                existing == null -> {
                    database.captureLogDao().insert(row)
                    tally.insert(RestoreTables.CAPTURE_LOGS)
                }

                row.capturedAt > existing -> {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM capture_logs WHERE id = ?",
                        arrayOf(row.id),
                    )
                    database.captureLogDao().insert(row)
                    tally.update(RestoreTables.CAPTURE_LOGS)
                }

                row.capturedAt < existing -> tally.skip(RestoreTables.CAPTURE_LOGS)

                else -> {
                    tally.skip(RestoreTables.CAPTURE_LOGS)
                    tally.conflict(RestoreTables.CAPTURE_LOGS, row.id, "capturedAt 相同，保留本地记录")
                }
            }
        }
    }

    private suspend fun mergeFieldEdits(payload: RestorePayload, tally: RestoreTally) {
        val dao = database.fieldEditDao()
        payload.fieldEdits.forEach { dto ->
            val row = dto.toEntity()
            val existing = dao.find(row.transactionId, row.fieldName)
            when {
                existing == null -> {
                    dao.insert(row)
                    tally.insert(RestoreTables.FIELD_EDITS)
                }

                row.editedAt > existing.editedAt -> {
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM field_edits WHERE transactionId = ? AND fieldName = ?",
                        arrayOf(row.transactionId, row.fieldName),
                    )
                    dao.insert(row)
                    tally.update(RestoreTables.FIELD_EDITS)
                }

                row.editedAt < existing.editedAt -> tally.skip(RestoreTables.FIELD_EDITS)

                else -> {
                    tally.skip(RestoreTables.FIELD_EDITS)
                    tally.conflict(
                        RestoreTables.FIELD_EDITS,
                        "${row.transactionId}/${row.fieldName}",
                        "editedAt 相同，保留本地记录",
                    )
                }
            }
        }
    }

    /** 证据 DAO 无 update：同一事务内先删后插。 */
    private suspend fun replaceEvidence(row: com.zhangran.app.data.local.entity.CaptureEvidenceEntity) {
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM capture_evidence WHERE id = ?",
            arrayOf(row.id),
        )
        database.captureEvidenceDao().insert(row)
    }

    private suspend fun loadAllEvidence(): List<com.zhangran.app.data.local.entity.CaptureEvidenceEntity> {
        val result = mutableListOf<com.zhangran.app.data.local.entity.CaptureEvidenceEntity>()
        var offset = 0
        while (true) {
            val page = database.captureEvidenceDao().listPage(PAGE_SIZE, offset)
            result += page
            if (page.size < PAGE_SIZE) break
            offset += PAGE_SIZE
        }
        return result
    }

    // ------------------------------------------------------------------ Replace

    /** §4.5 第 2 步：私有目录生成恢复前备份；失败抛异常（中止恢复）。 */
    private suspend fun createPreRestoreBackup(started: Long): String {
        val zip = exportBackup.export(
            AppVersion(
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
            ),
        )
        val directory = File(context.filesDir, PRE_RESTORE_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "pre-restore-$started.zip")
        file.writeBytes(zip.bytes)
        return file.name
    }

    /** 清空 9 张表（子表 → 主表；`pending` 属于 transactions，无需单列）。 */
    private fun clearAllTables() {
        val writable = database.openHelper.writableDatabase
        listOf(
            "transactions",
            "capture_evidence",
            "duplicate_decisions",
            "capture_logs",
            "field_edits",
            "budgets",
            "parsing_rules",
            "categories",
            "accounts",
        ).forEach { table -> writable.execSQL("DELETE FROM $table") }
    }

    /** 导入 9 张表（主表 → 子表）。 */
    private suspend fun insertAllTables(entities: RestoreEntities, tally: RestoreTally) {
        entities.categories.forEach { database.categoryDao().insert(it) }
        tally.insert(RestoreTables.CATEGORIES, entities.categories.size)

        entities.accounts.forEach { database.accountDao().insert(it) }
        tally.insert(RestoreTables.ACCOUNTS, entities.accounts.size)

        entities.budgets.forEach { database.budgetDao().insert(it) }
        tally.insert(RestoreTables.BUDGETS, entities.budgets.size)

        entities.rules.forEach { database.parsingRuleDao().insert(it) }
        tally.insert(RestoreTables.RULES, entities.rules.size)

        database.transactionDao().insertAll(entities.transactions)
        tally.insert(RestoreTables.TRANSACTIONS, entities.transactions.size)

        entities.evidence.forEach { database.captureEvidenceDao().insert(it) }
        tally.insert(RestoreTables.EVIDENCE, entities.evidence.size)

        entities.duplicateDecisions.forEach { database.duplicateDecisionDao().insert(it) }
        tally.insert(RestoreTables.DUPLICATES, entities.duplicateDecisions.size)

        entities.captureLogs.forEach { database.captureLogDao().insert(it) }
        tally.insert(RestoreTables.CAPTURE_LOGS, entities.captureLogs.size)

        database.fieldEditDao().insertAll(entities.fieldEdits)
        tally.insert(RestoreTables.FIELD_EDITS, entities.fieldEdits.size)
    }

    private companion object {
        const val PRE_RESTORE_DIRECTORY: String = "restore-pre-backups"
        const val PAGE_SIZE: Int = 500
    }
}
