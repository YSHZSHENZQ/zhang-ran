/**
 * 待确认「确认」：不改字段，PENDING → CONFIRMED，confirmedByUser = true，不写 FieldEdit。
 *
 * 含疑似重复的记录不能走这条路径（M3a：该行只有忽略）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.service.MAX_AMOUNT_MINOR
import java.time.Clock
import javax.inject.Inject

sealed interface ConfirmPendingResult {
    data object Confirmed : ConfirmPendingResult

    data class Rejected(val reason: String) : ConfirmPendingResult
}

class ConfirmPendingUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val clock: Clock,
) {

    suspend fun invoke(transactionId: String): ConfirmPendingResult {
        val current = transactions.getById(transactionId)
            ?: return ConfirmPendingResult.Rejected("记录不存在")
        if (current.state != TransactionState.PENDING) {
            return ConfirmPendingResult.Rejected("只能确认待确认记录")
        }
        val reasons = parsePendingReasons(current.pendingReasons)
        if (PendingReason.POSSIBLE_DUPLICATE in reasons) {
            return ConfirmPendingResult.Rejected("疑似重复不能直接确认")
        }
        if (current.amountMinor <= 0L || current.amountMinor > MAX_AMOUNT_MINOR) {
            return ConfirmPendingResult.Rejected("金额不合法")
        }
        if (current.transactionTime <= 0L) {
            return ConfirmPendingResult.Rejected("时间不能为空")
        }
        val now = clock.millis()
        transactions.update(
            current.copy(
                state = TransactionState.CONFIRMED,
                confirmedByUser = true,
                updatedAt = now,
            ),
        )
        return ConfirmPendingResult.Confirmed
    }
}
