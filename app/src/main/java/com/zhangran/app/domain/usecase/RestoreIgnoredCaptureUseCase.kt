/**
 * 把用户忽略的捕获恢复成待确认。
 *
 * 只接受日志状态 `IGNORED` 且交易仍是 `IGNORED` 的记录。
 * 不产生 `CONFIRMED` 或 `DUPLICATE`，也不改统计口径（待确认不进统计）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.TransactionState
import java.time.Clock
import javax.inject.Inject

sealed interface RestoreIgnoredResult {
    data object Restored : RestoreIgnoredResult

    data class Rejected(val reason: String) : RestoreIgnoredResult
}

class RestoreIgnoredCaptureUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val logs: CaptureLogRepository,
    private val clock: Clock,
) {

    suspend fun invoke(logId: String): RestoreIgnoredResult {
        val log = logs.findById(logId) ?: return RestoreIgnoredResult.Rejected("日志不存在")
        if (log.status != CaptureLogStatus.IGNORED) {
            return RestoreIgnoredResult.Rejected("只能恢复已忽略的记录")
        }
        val transactionId = log.transactionId
            ?: return RestoreIgnoredResult.Rejected("没有对应的账")
        val current = transactions.getById(transactionId)
            ?: return RestoreIgnoredResult.Rejected("记录不存在")
        if (current.state != TransactionState.IGNORED) {
            return RestoreIgnoredResult.Rejected("记录不是已忽略")
        }
        val now = clock.millis()
        transactions.update(
            current.copy(
                state = TransactionState.PENDING,
                confirmedByUser = false,
                updatedAt = now,
            ),
        )
        logs.update(log.copy(status = CaptureLogStatus.PENDING))
        return RestoreIgnoredResult.Restored
    }
}
