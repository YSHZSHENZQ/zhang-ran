/**
 * 待确认「忽略」：PENDING → IGNORED。
 *
 * 不删除 evidence / capture_logs，不写 FieldEdit，不把记录改成已确认。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.TransactionState
import java.time.Clock
import javax.inject.Inject

sealed interface IgnorePendingResult {
    data object Ignored : IgnorePendingResult

    data class Rejected(val reason: String) : IgnorePendingResult
}

class IgnorePendingUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val logs: CaptureLogRepository,
    private val clock: Clock,
) {

    suspend fun invoke(transactionId: String): IgnorePendingResult {
        val current = transactions.getById(transactionId)
            ?: return IgnorePendingResult.Rejected("记录不存在")
        if (current.state != TransactionState.PENDING) {
            return IgnorePendingResult.Rejected("只能忽略待确认记录")
        }
        val now = clock.millis()
        transactions.update(
            current.copy(
                state = TransactionState.IGNORED,
                updatedAt = now,
            ),
        )
        val log = logs.findByTransactionId(transactionId)
        if (log != null) {
            logs.update(log.copy(status = CaptureLogStatus.IGNORED))
        }
        return IgnorePendingResult.Ignored
    }
}
