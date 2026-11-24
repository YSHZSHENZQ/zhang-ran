/**
 * 首页最近交易里，待确认行与已确认行的视觉差（UX-1）。
 *
 * 待确认包括仍为 [com.zhangran.app.domain.model.TransactionState.PENDING] 的疑似重复。
 * 金额色只在这里决定语义，Compose 只负责把语义映射到已有 token，不另造色值。
 */
package com.zhangran.app.ui.screen.home

import com.zhangran.app.domain.model.TransactionType

/** 首页待确认行上的可见文案。已确认行不得使用。 */
const val HOME_PENDING_LABEL: String = "待确认"

/**
 * 交易行金额语义色。
 *
 * [PENDING] 覆盖全部交易类型：待确认支出不得再用已确认支出的入账红，
 * 待确认收入 / 转账 / 退款同样不用已入账色，避免看起来已经入账。
 */
enum class HomeRowAmountTone {
    EXPENSE,
    INCOME,
    TRANSFER,
    PENDING,
}

fun homeRowAmountTone(pending: Boolean, type: TransactionType): HomeRowAmountTone {
    if (pending) return HomeRowAmountTone.PENDING
    return when (type) {
        TransactionType.EXPENSE -> HomeRowAmountTone.EXPENSE
        TransactionType.INCOME -> HomeRowAmountTone.INCOME
        TransactionType.TRANSFER -> HomeRowAmountTone.TRANSFER
        TransactionType.REFUND -> HomeRowAmountTone.INCOME
    }
}
