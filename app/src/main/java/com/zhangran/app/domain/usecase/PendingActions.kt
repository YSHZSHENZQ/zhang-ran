/**
 * 待确认行的操作集（`docs/02` §5 按 reason 决定）。
 *
 * 含 [com.zhangran.app.domain.model.PendingReason.POSSIBLE_DUPLICATE] 时只能查看重复或忽略。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.domain.model.PendingReason

data class PendingActionFlags(
    val canConfirm: Boolean,
    val canEdit: Boolean,
    val canIgnore: Boolean,
    val canReview: Boolean,
)

fun pendingActionFlags(reasons: Collection<PendingReason>): PendingActionFlags =
    if (PendingReason.POSSIBLE_DUPLICATE in reasons) {
        PendingActionFlags(canConfirm = false, canEdit = false, canIgnore = true, canReview = true)
    } else {
        PendingActionFlags(canConfirm = true, canEdit = true, canIgnore = true, canReview = false)
    }

/** 从保真 JSON 里取出 fixture id。没有该字段时返回 null。 */
fun fixtureIdOf(rawCaptureRef: String?): String? {
    if (rawCaptureRef.isNullOrBlank()) return null
    val match = FIXTURE_ID.find(rawCaptureRef) ?: return null
    return match.groupValues[1].takeIf { it.isNotEmpty() }
}

private val FIXTURE_ID = Regex("\"fixtureId\"\\s*:\\s*\"([^\"]*)\"")
