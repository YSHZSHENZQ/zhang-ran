/**
 * 疑似重复的对照与用户判定（M3b）。
 *
 * 对照对象来自 [com.zhangran.app.domain.service.Dedup]，页面不自己算是不是同一笔。
 * 「同一笔」把新记录标成 DUPLICATE 并写一条判定，候选、证据、捕获日志都留着。
 * 「两笔」只去掉 POSSIBLE_DUPLICATE，记录回到普通待确认。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.DuplicateDecisionEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.DuplicateDecisionRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.DecisionActor
import com.zhangran.app.domain.model.DuplicateVerdict
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Dedup
import com.zhangran.app.domain.service.DedupResult
import com.zhangran.app.domain.service.ExistingTransaction
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

data class DuplicateSide(
    val transactionId: String,
    val merchant: String?,
    val amountMinor: Long,
    val type: TransactionType,
    val transactionTime: Long,
    val source: CaptureSource,
)

data class DuplicateComparison(
    val fresh: DuplicateSide,
    val existing: DuplicateSide,
)

sealed interface DuplicateActionResult {
    data object Done : DuplicateActionResult

    data class Rejected(val reason: String) : DuplicateActionResult
}

class DuplicateReviewUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val logs: CaptureLogRepository,
    private val decisions: DuplicateDecisionRepository,
    private val clock: Clock,
) {

    suspend fun load(transactionId: String): DuplicateComparison? {
        val fresh = transactions.getById(transactionId) ?: return null
        if (fresh.state != TransactionState.PENDING) return null
        val matched = matchedTransaction(fresh) ?: return null
        return DuplicateComparison(
            fresh = fresh.toSide(),
            existing = matched.toSide(),
        )
    }

    suspend fun markSame(transactionId: String): DuplicateActionResult {
        val fresh = transactions.getById(transactionId)
            ?: return DuplicateActionResult.Rejected("记录不存在")
        if (fresh.state != TransactionState.PENDING) {
            return DuplicateActionResult.Rejected("只能处理待确认记录")
        }
        val reasons = parsePendingReasons(logs.findByTransactionId(transactionId)?.reason)
        if (PendingReason.POSSIBLE_DUPLICATE !in reasons) {
            return DuplicateActionResult.Rejected("这条不是疑似重复")
        }
        val matched = matchedTransaction(fresh)
            ?: return DuplicateActionResult.Rejected("没有找到被比对的记录")
        val now = clock.millis()
        transactions.update(
            fresh.copy(
                state = TransactionState.DUPLICATE,
                updatedAt = now,
            ),
        )
        decisions.insert(decision(fresh, matched.id, DuplicateVerdict.SAME_TRANSACTION, now))
        return DuplicateActionResult.Done
    }

    suspend fun markDifferent(transactionId: String): DuplicateActionResult {
        val fresh = transactions.getById(transactionId)
            ?: return DuplicateActionResult.Rejected("记录不存在")
        if (fresh.state != TransactionState.PENDING) {
            return DuplicateActionResult.Rejected("只能处理待确认记录")
        }
        val log = logs.findByTransactionId(transactionId)
            ?: return DuplicateActionResult.Rejected("找不到捕获记录")
        val reasons = parsePendingReasons(log.reason)
        if (PendingReason.POSSIBLE_DUPLICATE !in reasons) {
            return DuplicateActionResult.Rejected("这条不是疑似重复")
        }
        val matched = matchedTransaction(fresh)
            ?: return DuplicateActionResult.Rejected("没有找到被比对的记录")
        val kept = reasons.filter { it != PendingReason.POSSIBLE_DUPLICATE }
        logs.update(log.copy(reason = kept.joinToString(",") { it.name }.ifEmpty { null }))
        val now = clock.millis()
        decisions.insert(decision(fresh, matched.id, DuplicateVerdict.DIFFERENT_TRANSACTION, now))
        return DuplicateActionResult.Done
    }

    private suspend fun matchedTransaction(fresh: TransactionEntity): TransactionEntity? {
        val others = transactions.listVisible()
            .filter { it.id != fresh.id }
            .map { it.toExisting() }
        val matchedId = when (val result = Dedup.check(fresh.toCandidate(), others)) {
            is DedupResult.Duplicate -> result.matchedTransactionId
            is DedupResult.PossibleDuplicate -> result.candidateTransactionIds.firstOrNull()
            DedupResult.None -> null
        } ?: return null
        return transactions.getById(matchedId)
    }

    private suspend fun decision(
        fresh: TransactionEntity,
        existingId: String,
        verdict: DuplicateVerdict,
        decidedAt: Long,
    ): DuplicateDecisionEntity {
        val log = logs.findByTransactionId(fresh.id)
        return DuplicateDecisionEntity(
            id = UUID.randomUUID().toString(),
            candidateId = log?.captureId ?: fresh.id,
            candidateTransactionId = fresh.id,
            existingTransactionId = existingId,
            verdict = verdict,
            decidedBy = DecisionActor.USER,
            decidedAt = decidedAt,
            reason = if (verdict == DuplicateVerdict.SAME_TRANSACTION) "同一笔" else "两笔",
            evidenceId = fresh.evidenceId,
        )
    }
}

private fun TransactionEntity.toSide(): DuplicateSide = DuplicateSide(
    transactionId = id,
    merchant = merchant,
    amountMinor = amountMinor,
    type = type,
    transactionTime = transactionTime,
    source = source,
)

private fun TransactionEntity.toExisting(): ExistingTransaction = ExistingTransaction(
    id = id,
    type = type,
    amountMinor = amountMinor,
    transactionTime = transactionTime,
    state = state,
    merchant = merchant,
    accountId = accountId,
    externalTransactionId = externalTransactionId,
    capturedAt = transactionTime,
)

private fun TransactionEntity.toCandidate(): Candidate = Candidate(
    currency = currency.name,
    transactionTime = transactionTime,
    overallConfidence = 1.0,
    amountMinor = amountMinor,
    amountConfidence = 1.0,
    direction = type,
    directionConfidence = 1.0,
    merchant = merchant,
    merchantConfidence = if (merchant.isNullOrBlank()) null else 1.0,
    accountId = accountId,
    externalTransactionId = externalTransactionId,
    capturedAt = transactionTime,
    textClue = merchant,
)
