/**
 * 无障碍快照 → 保真日志 → 候选 → 既有领域链 → 只写待确认（M6-2）。
 *
 * 顺序：白名单 → 脱敏保真 → 最小解析 → Normalize → Confidence → Dedup → Decision
 * → [AccessibilityPendingGate]。
 *
 * 本类不写 `TransactionState.CONFIRMED`。Decision 若给出 CONFIRMED，闸门会改成待确认。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.capture.COLLECTOR_INTERFACE_VERSION
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.RawCandidate
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.usecase.formatPendingReasons
import com.zhangran.app.domain.service.CaptureDebounceResult
import com.zhangran.app.domain.service.CaptureIdentity
import com.zhangran.app.domain.service.Confidence
import com.zhangran.app.domain.service.Decision
import com.zhangran.app.domain.service.Dedup
import com.zhangran.app.domain.service.ExistingTransaction
import com.zhangran.app.domain.service.Normalize
import java.util.UUID
import kotlinx.coroutines.CancellationException

enum class AccessibilityIngestOutcome {
    NOT_WHITELIST,
    IGNORED_INVALID,
    FAILED,
    PENDING,
}

data class AccessibilityIngestResult(
    val captureId: String?,
    val outcome: AccessibilityIngestOutcome,
    val status: CaptureLogStatus?,
    val captureSource: CaptureSource?,
    val capturedAt: Long?,
    val fixtureId: String?,
    val transactionId: String?,
    val transactionState: TransactionState?,
    val reasons: Set<PendingReason>,
    val evidenceWritten: Boolean,
    val refusedAutoConfirm: Boolean,
) {
    fun toProofJson(): String = buildString {
        append('{')
        append("\"captureId\":${jsonString(captureId)}")
        append(",\"outcome\":${jsonString(outcome.name)}")
        append(",\"status\":${jsonString(status?.name)}")
        append(",\"source\":${jsonString(captureSource?.name)}")
        append(",\"capturedAt\":${capturedAt ?: "null"}")
        append(",\"fixtureId\":${jsonString(fixtureId)}")
        append(",\"transactionId\":${jsonString(transactionId)}")
        append(",\"transactionState\":${jsonString(transactionState?.name)}")
        append(",\"reasons\":[")
        append(reasons.joinToString(",") { jsonString(it.name) })
        append("],\"evidenceWritten\":$evidenceWritten")
        append(",\"refusedAutoConfirm\":$refusedAutoConfirm")
        append('}')
    }

    companion object {
        fun notWhitelist(fixtureId: String?) = AccessibilityIngestResult(
            captureId = null,
            outcome = AccessibilityIngestOutcome.NOT_WHITELIST,
            status = null,
            captureSource = null,
            capturedAt = null,
            fixtureId = fixtureId,
            transactionId = null,
            transactionState = null,
            reasons = emptySet(),
            evidenceWritten = false,
            refusedAutoConfirm = false,
        )
    }
}

class AccessibilitySnapshotIngest(
    private val normalize: Normalize,
    private val transactions: TransactionRepository,
    private val records: CaptureLogRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val decision: Decision = Decision(supportedPackages = AccessibilityTargets.packages),
    private val onCaptured: suspend (Long) -> Unit = {},
) {

    suspend fun ingest(
        snapshot: AccessibilityPageSnapshot,
        fixtureId: String? = null,
        notificationId: String? = null,
        postTime: Long? = null,
    ): AccessibilityIngestResult {
        if (snapshot.packageName !in AccessibilityTargets.packages) {
            return AccessibilityIngestResult.notWhitelist(fixtureId)
        }
        val identity = CaptureIdentity(
            packageName = snapshot.packageName,
            notificationId = notificationId,
            postTime = postTime,
        )
        findSameEvent(identity)?.let { return it }
        val fidelity = AccessibilitySnapshotFidelity.encode(
            snapshot = snapshot,
            fixtureId = fixtureId,
            notificationId = notificationId,
            postTime = postTime,
        )
        return try {
            val parsed = AccessibilitySnapshotParser.parse(snapshot)
            if (parsed == null) {
                return writeLogOnly(
                    snapshot = snapshot,
                    fixtureId = fixtureId,
                    fidelity = fidelity,
                    status = CaptureLogStatus.IGNORED_INVALID,
                    reason = "快照缺少可用的支付结果字段",
                    outcome = AccessibilityIngestOutcome.IGNORED_INVALID,
                )
            }
            val raw = rawCandidate(snapshot, parsed)
            val normalized = normalize.normalize(raw)
            val candidate = Confidence.derive(normalized)
            val dedup = Dedup.check(candidate, loadExisting())
            val admission = AccessibilityPendingGate.admit(decision.decide(candidate, dedup), dedup)
            persist(
                snapshot = snapshot,
                fixtureId = fixtureId,
                fidelity = fidelity,
                admission = admission,
                candidate = candidate,
                zoneId = normalized.transactionTimeZone,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (thrown: Throwable) {
            writeFailed(snapshot, fixtureId, fidelity)
        }
    }

    /**
     * 把闸门结论落库。调用方只能传入 [AccessibilityAdmission]，没有 CONFIRMED 分支。
     */
    internal suspend fun persist(
        snapshot: AccessibilityPageSnapshot,
        fixtureId: String?,
        fidelity: String,
        admission: AccessibilityAdmission,
        candidate: Candidate,
        zoneId: String?,
    ): AccessibilityIngestResult {
        if (admission is AccessibilityAdmission.Invalid) {
            return writeLogOnly(
                snapshot = snapshot,
                fixtureId = fixtureId,
                fidelity = fidelity,
                status = CaptureLogStatus.IGNORED_INVALID,
                reason = "ClearlyInvalid",
                outcome = AccessibilityIngestOutcome.IGNORED_INVALID,
            )
        }
        val pending = admission as AccessibilityAdmission.Pending
        val captureId = newId()
        val evidenceId = "evi-$captureId"
        val amountMinor = candidate.amountMinor
        val direction = candidate.direction
        val transactionTime = when {
            candidate.transactionTime > 0L -> candidate.transactionTime
            snapshot.capturedAt > 0L -> snapshot.capturedAt
            else -> 0L
        }
        val transactionId = if (
            direction != null && amountMinor != null && amountMinor > 0L && transactionTime > 0L
        ) {
            val id = newId()
            val now = clock()
            val entity = TransactionEntity(
                id = id,
                type = direction,
                amountMinor = amountMinor,
                currency = Currency.CNY,
                merchant = candidate.merchant,
                categoryId = candidate.categoryId,
                accountId = candidate.accountId,
                transactionTime = transactionTime,
                transactionTimeZone = zoneId ?: "Asia/Shanghai",
                note = null,
                state = TransactionState.PENDING,
                source = CaptureSource.ACCESSIBILITY,
                externalTransactionId = candidate.externalTransactionId,
                engineVersion = ENGINE_VERSION,
                matchedRuleId = null,
                evidenceId = evidenceId,
                confirmedByUser = false,
                orphan = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
                pendingReasons = formatPendingReasons(pending.reasons),
            )
            transactions.insert(entity)
            id
        } else {
            null
        }
        records.insert(
            log = logEntity(
                captureId = captureId,
                snapshot = snapshot,
                status = CaptureLogStatus.PENDING,
                reason = pending.reasons.joinToString(",") { it.name },
                fidelity = fidelity,
                evidenceId = evidenceId,
                transactionId = transactionId,
            ),
            evidence = evidenceEntity(
                evidenceId = evidenceId,
                captureId = captureId,
                snapshot = snapshot,
                fidelity = fidelity,
                confidence = candidate.overallConfidence,
            ),
        )
        onCaptured(snapshot.capturedAt)
        return AccessibilityIngestResult(
            captureId = captureId,
            outcome = AccessibilityIngestOutcome.PENDING,
            status = CaptureLogStatus.PENDING,
            captureSource = CaptureSource.ACCESSIBILITY,
            capturedAt = snapshot.capturedAt,
            fixtureId = fixtureId,
            transactionId = transactionId,
            transactionState = if (transactionId != null) TransactionState.PENDING else null,
            reasons = pending.reasons,
            evidenceWritten = true,
            refusedAutoConfirm = pending.refusedAutoConfirm,
        )
    }

    private suspend fun writeLogOnly(
        snapshot: AccessibilityPageSnapshot,
        fixtureId: String?,
        fidelity: String,
        status: CaptureLogStatus,
        reason: String,
        outcome: AccessibilityIngestOutcome,
    ): AccessibilityIngestResult {
        val captureId = newId()
        val evidenceId = "evi-$captureId"
        records.insert(
            log = logEntity(
                captureId = captureId,
                snapshot = snapshot,
                status = status,
                reason = reason,
                fidelity = fidelity,
                evidenceId = evidenceId,
                transactionId = null,
            ),
            evidence = evidenceEntity(
                evidenceId = evidenceId,
                captureId = captureId,
                snapshot = snapshot,
                fidelity = fidelity,
                confidence = null,
            ),
        )
        onCaptured(snapshot.capturedAt)
        return AccessibilityIngestResult(
            captureId = captureId,
            outcome = outcome,
            status = status,
            captureSource = CaptureSource.ACCESSIBILITY,
            capturedAt = snapshot.capturedAt,
            fixtureId = fixtureId,
            transactionId = null,
            transactionState = null,
            reasons = emptySet(),
            evidenceWritten = true,
            refusedAutoConfirm = false,
        )
    }

    private suspend fun writeFailed(
        snapshot: AccessibilityPageSnapshot,
        fixtureId: String?,
        fidelity: String,
    ): AccessibilityIngestResult {
        return try {
            writeLogOnly(
                snapshot = snapshot,
                fixtureId = fixtureId,
                fidelity = fidelity,
                status = CaptureLogStatus.FAILED,
                reason = "落库失败",
                outcome = AccessibilityIngestOutcome.FAILED,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            AccessibilityIngestResult(
                captureId = null,
                outcome = AccessibilityIngestOutcome.FAILED,
                status = CaptureLogStatus.FAILED,
                captureSource = CaptureSource.ACCESSIBILITY,
                capturedAt = snapshot.capturedAt,
                fixtureId = fixtureId,
                transactionId = null,
                transactionState = null,
                reasons = emptySet(),
                evidenceWritten = false,
                refusedAutoConfirm = false,
            )
        }
    }

    /**
     * 同一 `packageName + notificationId + postTime` 已经落过库时，不再写第二条捕获或交易。
     * 三要素缺一就不在这里合并。
     */
    private suspend fun findSameEvent(identity: CaptureIdentity): AccessibilityIngestResult? {
        if (identity.packageName.isNullOrBlank() ||
            identity.notificationId.isNullOrBlank() ||
            identity.postTime == null
        ) {
            return null
        }
        val match = records.listAll().firstOrNull { log ->
            val stored = identityOf(log) ?: return@firstOrNull false
            Dedup.checkCaptureDebounce(stored, identity) == CaptureDebounceResult.SameEvent
        } ?: return null
        val transaction = match.transactionId?.let { transactions.getById(it) }
        return AccessibilityIngestResult(
            captureId = match.captureId,
            outcome = when (match.status) {
                CaptureLogStatus.IGNORED_INVALID -> AccessibilityIngestOutcome.IGNORED_INVALID
                CaptureLogStatus.FAILED -> AccessibilityIngestOutcome.FAILED
                else -> AccessibilityIngestOutcome.PENDING
            },
            status = match.status,
            captureSource = match.captureSource,
            capturedAt = match.capturedAt,
            fixtureId = fixtureIdOfRaw(match.rawCaptureRef),
            transactionId = match.transactionId,
            transactionState = transaction?.state,
            reasons = reasonsOf(match.reason),
            evidenceWritten = false,
            refusedAutoConfirm = false,
        )
    }

    private fun identityOf(log: CaptureLogEntity): CaptureIdentity? {
        val document = AccessibilitySnapshotFidelity.decode(log.rawCaptureRef) ?: return null
        return CaptureIdentity(
            packageName = log.packageName ?: document.packageName,
            notificationId = document.notificationId,
            postTime = document.postTime,
        )
    }

    private suspend fun loadExisting(): List<ExistingTransaction> =
        transactions.listVisible().map { entity ->
            ExistingTransaction(
                id = entity.id,
                type = entity.type,
                amountMinor = entity.amountMinor,
                transactionTime = entity.transactionTime,
                state = entity.state,
                merchant = entity.merchant,
                accountId = entity.accountId,
                externalTransactionId = entity.externalTransactionId,
                capturedAt = entity.transactionTime,
            )
        }

    private fun rawCandidate(
        snapshot: AccessibilityPageSnapshot,
        parsed: AccessibilityParsedFields,
    ) = RawCandidate(
        packageName = snapshot.packageName,
        sourceChannel = channelOf(snapshot.packageName),
        captureSource = CaptureSource.ACCESSIBILITY,
        amountMinor = parsed.amountMinor,
        currency = Currency.CNY.name,
        direction = parsed.direction,
        merchantRaw = parsed.merchantRaw,
        noteRaw = null,
        categoryNameRaw = null,
        accountNameRaw = null,
        transactionTimeRaw = snapshot.capturedAt.takeIf { it > 0L },
        transactionTimeZoneRaw = null,
        externalTransactionId = null,
        channelOrderId = null,
        capturedAt = snapshot.capturedAt,
        textClue = parsed.textClue,
        upstreamResultUsable = true,
        directionSemanticsTrusted = parsed.directionSemanticsTrusted,
    )

    private fun logEntity(
        captureId: String,
        snapshot: AccessibilityPageSnapshot,
        status: CaptureLogStatus,
        reason: String,
        fidelity: String,
        evidenceId: String,
        transactionId: String?,
    ) = CaptureLogEntity(
        id = "log-$captureId",
        captureId = captureId,
        capturedAt = snapshot.capturedAt,
        captureSource = CaptureSource.ACCESSIBILITY,
        packageName = snapshot.packageName,
        channel = channelOf(snapshot.packageName),
        parserRuleId = null,
        engineVersion = ENGINE_VERSION,
        payloadVersion = COLLECTOR_INTERFACE_VERSION,
        status = status,
        reason = reason,
        isHistoricalReplay = false,
        rawCaptureRef = fidelity,
        evidenceId = evidenceId,
        transactionId = transactionId,
    )

    private fun evidenceEntity(
        evidenceId: String,
        captureId: String,
        snapshot: AccessibilityPageSnapshot,
        fidelity: String,
        confidence: Double?,
    ) = CaptureEvidenceEntity(
        id = evidenceId,
        captureId = captureId,
        captureSource = CaptureSource.ACCESSIBILITY,
        engineVersion = ENGINE_VERSION,
        ruleId = null,
        confidence = confidence,
        matchedKeywords = null,
        rejectedKeywords = null,
        rawCaptureRef = fidelity,
        capturedAt = snapshot.capturedAt,
    )

    private fun reasonsOf(raw: String?): Set<PendingReason> =
        raw.orEmpty()
            .split(',', '、', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { token -> PendingReason.entries.firstOrNull { it.name == token } }
            .toSet()

    private fun fixtureIdOfRaw(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val match = FIXTURE_ID.find(raw) ?: return null
        return match.groupValues[1].takeIf { it.isNotEmpty() }
    }

    private fun channelOf(packageName: String): String? = when (packageName) {
        AccessibilityTargets.WECHAT_PACKAGE -> "wechat"
        AccessibilityTargets.ALIPAY_PACKAGE -> "alipay"
        else -> null
    }

    private companion object {
        const val ENGINE_VERSION = "m6-2"
        val FIXTURE_ID = Regex("\"fixtureId\"\\s*:\\s*\"([^\"]*)\"")
    }
}

private fun jsonString(value: String?): String =
    if (value == null) "null" else "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
