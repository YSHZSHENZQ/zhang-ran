/**
 * 软删除与撤销（`M2b` §4.4；`docs/03` §6、`docs/02` §267）。
 *
 * 语义：
 * - 删除 = 写入 `deletedAt`，**`state` 不变**（可见性由 `deletedAt` 控制，不是 `DELETED` 状态）；
 * - 撤销 = 清空 `deletedAt`；
 * - **不写 `FieldEdit`**：删除不是字段编辑，不能污染字段保护名单（A12）；
 * - 幂等：重复删除/撤销不报错（只是刷新时间戳）。
 *
 * 恢复入口：本任务只做"即时撤销"；30 天长期恢复入口已记入 `review/BACKLOG.md` B1，本期不做。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.TransactionRepository
import java.time.Clock
import javax.inject.Inject

/** 删除/撤销的结果。 */
sealed interface DeleteTransactionResult {

    data object Success : DeleteTransactionResult

    /** 交易不存在（含已删除也查不到的情况）。 */
    data object NotFound : DeleteTransactionResult
}

/**
 * 软删除与撤销用例。
 *
 * @param transactions 交易仓储（`softDelete` / `restore`）
 * @param clock 时间源（`deletedAt` / `updatedAt`；测试注入固定时钟）
 */
class DeleteTransactionUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val clock: Clock,
) {

    /** 软删除（幂等）。 */
    suspend fun delete(transactionId: String): DeleteTransactionResult {
        // includeDeleted = true：重复删除时仍应返回 Success（幂等），而不是 NotFound
        transactions.getById(transactionId, includeDeleted = true)
            ?: return DeleteTransactionResult.NotFound
        transactions.softDelete(transactionId, deletedAt = clock.millis())
        return DeleteTransactionResult.Success
    }

    /** 撤销删除（幂等）。 */
    suspend fun restore(transactionId: String): DeleteTransactionResult {
        transactions.getById(transactionId, includeDeleted = true)
            ?: return DeleteTransactionResult.NotFound
        transactions.restore(transactionId, updatedAt = clock.millis())
        return DeleteTransactionResult.Success
    }
}
