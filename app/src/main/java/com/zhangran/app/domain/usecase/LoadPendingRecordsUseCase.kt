/**
 * 读取待确认列表（M6-2）。
 *
 * 交易来自 `state = PENDING`。原因读交易上的 `pendingReasons`，不读捕获日志，
 * 所以清空日志之后疑似重复仍然不能直接确认。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import javax.inject.Inject

data class PendingRecord(
    val transactionId: String,
    val amountMinor: Long,
    val type: TransactionType,
    val merchant: String?,
    val reasons: List<PendingReason>,
    val capturedAt: Long,
    val transactionTime: Long = capturedAt,
    val source: CaptureSource,
    val fixtureId: String? = null,
    val canConfirm: Boolean = true,
    val canEdit: Boolean = true,
    val canIgnore: Boolean = true,
    val canReview: Boolean = false,
)

class LoadPendingRecordsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val logs: CaptureLogRepository,
) {

    suspend operator fun invoke(): List<PendingRecord> =
        transactions.listByState(TransactionState.PENDING)
            .map { entity ->
                val log = logs.findByTransactionId(entity.id)
                val reasons = parsePendingReasons(entity.pendingReasons)
                val actions = pendingActionFlags(reasons)
                PendingRecord(
                    transactionId = entity.id,
                    amountMinor = entity.amountMinor,
                    type = entity.type,
                    merchant = entity.merchant,
                    reasons = reasons,
                    capturedAt = log?.capturedAt ?: entity.transactionTime,
                    transactionTime = entity.transactionTime,
                    source = entity.source,
                    fixtureId = fixtureIdOf(log?.rawCaptureRef),
                    canConfirm = actions.canConfirm,
                    canEdit = actions.canEdit,
                    canIgnore = actions.canIgnore,
                    canReview = actions.canReview,
                )
            }
            .sortedByDescending { it.capturedAt }
}

/** 把原因集合写成交易列上的逗号分隔文本。空集合写成 null。 */
fun formatPendingReasons(reasons: Iterable<PendingReason>): String? =
    reasons.map { it.name }.distinct().joinToString(",").ifEmpty { null }

/** 交易列或日志里的 reason 是逗号分隔的 [PendingReason] 名；无法识别的片段忽略。 */
fun parsePendingReasons(raw: String?): List<PendingReason> =
    raw.orEmpty()
        .split(',', '、', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { token -> PendingReason.entries.firstOrNull { it.name == token } }
