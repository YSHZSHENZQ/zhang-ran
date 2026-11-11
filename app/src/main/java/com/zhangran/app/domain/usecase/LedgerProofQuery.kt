/**
 * 调试取证：把账本行、FieldEdit、证据条数和本月汇总打成 JSON。
 *
 * 只给仪器测试 / debug 入口使用，不出现在正式页面。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class LedgerProofQuery @Inject constructor(
    private val transactions: TransactionRepository,
    private val fieldEdits: FieldEditRepository,
    private val logs: CaptureLogRepository,
    private val homeSummary: ObserveHomeSummaryUseCase,
    private val clock: Clock,
) {

    suspend fun json(gitSha: String): String {
        val summary = homeSummary(clock).first()
        val allLogs = logs.listAll()
        val parts = mutableListOf<String>()
        for (entity in transactions.listVisible().sortedBy { it.createdAt }) {
            val edits = fieldEdits.listByTransaction(entity.id).map { it.fieldName }.sorted()
            val relatedLogs = allLogs.filter { it.transactionId == entity.id }
            var evidenceCount = 0
            for (log in relatedLogs) {
                evidenceCount += logs.listEvidence(log.captureId).size
            }
            val fixtureId = relatedLogs.firstOrNull()?.rawCaptureRef?.let(::fixtureIdOf)
            parts += buildString {
                    append('{')
                    append("\"fixtureId\":${jsonString(fixtureId)}")
                    append(",\"transactionId\":${jsonString(entity.id)}")
                    append(",\"state\":${jsonString(entity.state.name)}")
                    append(",\"source\":${jsonString(entity.source.name)}")
                    append(",\"confirmedByUser\":${entity.confirmedByUser}")
                    append(",\"amountMinor\":${entity.amountMinor}")
                    append(",\"type\":${jsonString(entity.type.name)}")
                    append(",\"merchant\":${jsonString(entity.merchant)}")
                    append(",\"categoryId\":${jsonString(entity.categoryId)}")
                    append(",\"accountId\":${jsonString(entity.accountId)}")
                    append(",\"note\":${jsonString(entity.note)}")
                    append(",\"transactionTime\":${entity.transactionTime}")
                    append(",\"reasons\":${jsonString(entity.pendingReasons)}")
                    append(",\"fieldEdits\":[")
                    append(edits.joinToString(",") { jsonString(it) })
                    append("],\"evidenceCount\":$evidenceCount")
                    append(",\"captureLogCount\":${relatedLogs.size}")
                    append('}')
                }
        }
        val rows = parts.joinToString(separator = ",")
        return buildString {
            append('{')
            append("\"gitSha\":${jsonString(gitSha)}")
            append(",\"pendingCount\":${summary.pendingCount}")
            append(",\"monthExpenseMinor\":${summary.monthExpenseMinor}")
            append(",\"monthIncomeMinor\":${summary.monthIncomeMinor}")
            append(",\"monthBalanceMinor\":${summary.monthBalanceMinor}")
            append(",\"rows\":[")
            append(rows)
            append("]}")
        }
    }
}

internal fun jsonString(value: String?): String =
    if (value == null) {
        "null"
    } else {
        "\"" + value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }
