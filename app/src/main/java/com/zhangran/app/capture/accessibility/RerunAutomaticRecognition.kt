/**
 * 调试用：对一笔已落库记录再跑一次同一 fixture 的自动流程（M3a / AC-6）。
 *
 * 不出现在正式页面。新捕获仍只进待确认；已确认 / 已忽略的状态与受保护字段保持不动。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.usecase.AutomaticFieldMerge
import com.zhangran.app.domain.usecase.AutomaticFieldUpdate
import javax.inject.Inject

data class RerunAutomaticResult(
    val ok: Boolean,
    val reason: String? = null,
    val transactionId: String? = null,
    val state: String? = null,
    val confirmedByUser: Boolean? = null,
    val categoryId: String? = null,
    val note: String? = null,
    val merchant: String? = null,
    val newTransactionId: String? = null,
    val newState: String? = null,
    val refusedAutoConfirm: Boolean = false,
)

class RerunAutomaticRecognition @Inject constructor(
    private val ingest: AccessibilitySnapshotIngest,
    private val transactions: TransactionRepository,
    private val fieldEdits: FieldEditRepository,
    private val logs: CaptureLogRepository,
) {

    suspend fun rerun(transactionId: String): RerunAutomaticResult {
        val current = transactions.getById(transactionId)
            ?: return RerunAutomaticResult(ok = false, reason = "missing")
        val log = logs.findByTransactionId(transactionId)
        val document = AccessibilitySnapshotFidelity.decode(log?.rawCaptureRef)
            ?: return RerunAutomaticResult(ok = false, reason = "no-snapshot", transactionId = transactionId)
        val snapshot = AccessibilitySnapshotFidelity.toSnapshot(document)
        val ingested = ingest.ingest(snapshot, document.fixtureId)
        val created = ingested.transactionId?.let { transactions.getById(it) }
        if (created != null) {
            val protected = fieldEdits.listByTransaction(transactionId).map { it.fieldName }.toSet()
            val merged = AutomaticFieldMerge.apply(
                current = current,
                incoming = AutomaticFieldUpdate(
                    merchant = created.merchant,
                    amountMinor = created.amountMinor,
                    type = created.type,
                    categoryId = created.categoryId,
                    accountId = created.accountId,
                    transactionTime = created.transactionTime,
                    note = created.note,
                ),
                protectedFieldNames = protected,
            )
            if (merged != current) {
                transactions.update(merged)
            }
        }
        val after = transactions.getById(transactionId)
        return RerunAutomaticResult(
            ok = true,
            transactionId = transactionId,
            state = after?.state?.name,
            confirmedByUser = after?.confirmedByUser,
            categoryId = after?.categoryId,
            note = after?.note,
            merchant = after?.merchant,
            newTransactionId = ingested.transactionId,
            newState = ingested.transactionState?.name,
            refusedAutoConfirm = ingested.refusedAutoConfirm,
        )
    }
}
