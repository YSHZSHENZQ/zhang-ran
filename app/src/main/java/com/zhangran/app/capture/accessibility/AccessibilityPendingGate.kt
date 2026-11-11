/**
 * M6-2 装配闸门：这条路径不允许自动入账。
 *
 * Decision 仍是唯一给出业务结论的地方。本闸门只拒绝把 `CONFIRMED` / `DUPLICATE`
 * 写成正式账或静默丢掉：前者改记为待确认，后者改记为待确认并带上疑似重复原因。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.domain.model.DecisionResult
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.service.DedupResult

sealed interface AccessibilityAdmission {

    data class Pending(
        val reasons: Set<PendingReason>,
        /** Decision 给出了 CONFIRMED，本路径拒绝自动入账。 */
        val refusedAutoConfirm: Boolean = false,
    ) : AccessibilityAdmission {
        init {
            require(reasons.isNotEmpty()) { "待确认至少需要一个 reason" }
        }
    }

    data object Invalid : AccessibilityAdmission
}

object AccessibilityPendingGate {

    /**
     * @param dedup 判重结论。Duplicate / PossibleDuplicate 都只进待确认并带上疑似重复，
     * 不在这条路径写成 DUPLICATE。其它待确认原因仍然保留。
     */
    fun admit(
        decision: DecisionResult,
        dedup: DedupResult = DedupResult.None,
    ): AccessibilityAdmission {
        val review = dedup is DedupResult.Duplicate || dedup is DedupResult.PossibleDuplicate
        return when (decision) {
            is DecisionResult.ClearlyInvalid -> AccessibilityAdmission.Invalid
            is DecisionResult.Confirmed -> AccessibilityAdmission.Pending(
                reasons = reviewReasons(setOf(PendingReason.LOW_CONFIDENCE), review),
                refusedAutoConfirm = true,
            )
            is DecisionResult.Duplicate -> AccessibilityAdmission.Pending(
                reasons = setOf(PendingReason.POSSIBLE_DUPLICATE),
            )
            is DecisionResult.Pending -> AccessibilityAdmission.Pending(
                reasons = reviewReasons(decision.reasons, review),
            )
        }
    }

    private fun reviewReasons(reasons: Set<PendingReason>, review: Boolean): Set<PendingReason> =
        if (review) reasons + PendingReason.POSSIBLE_DUPLICATE else reasons
}
