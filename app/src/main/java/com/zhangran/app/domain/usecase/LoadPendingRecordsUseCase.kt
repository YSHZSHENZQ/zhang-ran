/**
 * 读取待确认列表（M6-2）。
 *
 * 交易来自 `state = PENDING`；原因来自关联捕获日志，页面不再自行推断。
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
                val reasons = parsePendingReasons(log?.reason)
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

/** 日志里的 reason 是逗号分隔的 [PendingReason] 名；无法识别的片段忽略。 */
fun parsePendingReasons(raw: String?): List<PendingReason> =
    raw.orEmpty()
        .split(',', '、', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { token -> PendingReason.entries.firstOrNull { it.name == token } }
