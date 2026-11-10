/**
 * 自动流程写回已有交易时的字段合并（`docs/03` §11）。
 *
 * 规则：
 * - 被保护的字段保持用户值，候选没有新值时也不得清空；
 * - 不改交易状态；
 * - 不改 `confirmedByUser`（不得改回 false，也不得由自动流程设为 true）；
 * - IGNORED / DELETED / DUPLICATE 整笔不动。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType

data class AutomaticFieldUpdate(
    val merchant: String? = null,
    val amountMinor: Long? = null,
    val type: TransactionType? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val transactionTime: Long? = null,
    val note: String? = null,
)

object AutomaticFieldMerge {

    private val frozenStates: Set<TransactionState> = setOf(
        TransactionState.IGNORED,
        TransactionState.DELETED,
        TransactionState.DUPLICATE,
    )

    fun apply(
        current: TransactionEntity,
        incoming: AutomaticFieldUpdate,
        protectedFieldNames: Set<String>,
    ): TransactionEntity {
        if (current.state in frozenStates) return current
        var next = current
        if ("merchant" !in protectedFieldNames && incoming.merchant != null) {
            next = next.copy(merchant = incoming.merchant)
        }
        if (
            "amountMinor" !in protectedFieldNames &&
            incoming.amountMinor != null &&
            incoming.amountMinor > 0L
        ) {
            next = next.copy(amountMinor = incoming.amountMinor)
        }
        if ("type" !in protectedFieldNames && incoming.type != null) {
            next = next.copy(type = incoming.type)
        }
        if ("categoryId" !in protectedFieldNames && incoming.categoryId != null) {
            next = next.copy(categoryId = incoming.categoryId)
        }
        if ("accountId" !in protectedFieldNames && incoming.accountId != null) {
            next = next.copy(accountId = incoming.accountId)
        }
        if (
            "transactionTime" !in protectedFieldNames &&
            incoming.transactionTime != null &&
            incoming.transactionTime > 0L
        ) {
            next = next.copy(transactionTime = incoming.transactionTime)
        }
        if ("note" !in protectedFieldNames && incoming.note != null) {
            next = next.copy(note = incoming.note)
        }
        return next
    }
}
