/**
 * 备份导出用例（M4b-1 §4.1/§4.5）：只读聚合 11 个数据文件并生成 ZIP。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.backup.AppVersion
import com.zhangran.app.data.backup.BackupFileContent
import com.zhangran.app.data.backup.BackupWriter
import com.zhangran.app.data.backup.BackupZip
import com.zhangran.app.data.backup.toDto
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.DuplicateDecisionEntity
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.domain.model.DecisionActor
import com.zhangran.app.domain.model.DuplicateVerdict
import com.zhangran.app.domain.model.TransactionState
import java.time.Clock
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class ExportBackupUseCase @Inject constructor(
    private val database: ZhangRanDatabase,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
) {
    private suspend fun loadEvidence(): List<com.zhangran.app.data.local.entity.CaptureEvidenceEntity> {
        val result = mutableListOf<com.zhangran.app.data.local.entity.CaptureEvidenceEntity>()
        var offset = 0
        while (true) {
            val page = database.captureEvidenceDao().listPage(500, offset)
            result += page
            if (page.size < 500) break
            offset += 500
        }
        return result
    }

    private suspend fun loadCaptureLogs(): List<com.zhangran.app.data.local.entity.CaptureLogEntity> {
        val result = mutableListOf<com.zhangran.app.data.local.entity.CaptureLogEntity>()
        var offset = 0
        while (true) {
            val page = database.captureLogDao().listPage(500, offset)
            result += page
            if (page.size < 500) break
            offset += 500
        }
        return result
    }

    private fun loadDuplicateDecisions(): List<DuplicateDecisionEntity> =
        database.query("SELECT * FROM duplicate_decisions", null).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        DuplicateDecisionEntity(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            candidateId = cursor.getString(cursor.getColumnIndexOrThrow("candidateId")),
                            candidateTransactionId = cursor.getNullableString("candidateTransactionId"),
                            existingTransactionId = cursor.getString(cursor.getColumnIndexOrThrow("existingTransactionId")),
                            verdict = DuplicateVerdict.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("verdict"))),
                            decidedBy = DecisionActor.valueOf(cursor.getString(cursor.getColumnIndexOrThrow("decidedBy"))),
                            decidedAt = cursor.getLong(cursor.getColumnIndexOrThrow("decidedAt")),
                            reason = cursor.getNullableString("reason"),
                            matchSignals = cursor.getNullableString("matchSignals"),
                            evidenceId = cursor.getNullableString("evidenceId"),
                            revokedAt = cursor.getNullableLong("revokedAt"),
                        ),
                    )
                }
            }
        }

    private fun android.database.Cursor.getNullableString(name: String): String? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getString(index)
    }

    private fun android.database.Cursor.getNullableLong(name: String): Long? {
        val index = getColumnIndexOrThrow(name)
        return if (isNull(index)) null else getLong(index)
    }

    suspend fun export(appVersion: AppVersion): BackupZip {
        val createdAt = clock.millis()
        val zoneId = ZoneId.systemDefault().id
        val transactions = database.transactionDao().listAllIncludingDeleted()
        val pending = transactions.filter { it.state == TransactionState.PENDING }
        val categories = database.categoryDao().listAll()
        val accounts = database.accountDao().listAll()
        val budgets = database.budgetDao().listAll()
        val rules = database.parsingRuleDao().listAll()
        val evidence = loadEvidence()
        val logs = loadCaptureLogs()
        val duplicates = loadDuplicateDecisions()
        val fieldEdits = database.fieldEditDao().listAll()
        val settings = settingsRepository.settings.first()
        val files = listOf(
            BackupFileContent("transactions.json", BackupWriter.encode(transactions.map { it.toDto() }), transactions.size),
            BackupFileContent("categories.json", BackupWriter.encode(categories.map { it.toDto() }), categories.size),
            BackupFileContent("accounts.json", BackupWriter.encode(accounts.map { it.toDto() }), accounts.size),
            BackupFileContent("budgets.json", BackupWriter.encode(budgets.map { it.toDto() }), budgets.size),
            BackupFileContent("rules.json", BackupWriter.encode(rules.map { it.toDto() }), rules.size),
            BackupFileContent("pending.json", BackupWriter.encode(pending.map { it.toDto() }), pending.size),
            BackupFileContent("evidence.json", BackupWriter.encode(evidence.map { it.toDto() }), evidence.size),
            BackupFileContent("duplicate_decisions.json", BackupWriter.encode(duplicates.map { it.toDto() }), duplicates.size),
            BackupFileContent("capture_logs.json", BackupWriter.encode(logs.map { it.toDto() }), logs.size),
            BackupFileContent("field_edits.json", BackupWriter.encode(fieldEdits.map { it.toDto() }), fieldEdits.size),
            BackupFileContent("settings.json", BackupWriter.encode(listOf(settings.toDto())), 1),
        )
        return BackupWriter.buildZip(files, appVersion, createdAt, zoneId)
    }
}
