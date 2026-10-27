/**
 * 「交易已删除」的跨页通知（`M2b` §1.2：删除后由首页提供即时撤销入口）。
 *
 * 为什么需要它：删除发生在详情页，但撤销 SnackBar 要显示在返回后的首页；
 * 导航层不在本任务白名单内，因此用**一个进程级的一次性状态**做桥：
 * 详情页删除成功后 [publishDeleted]，首页订阅 [lastDeleted] 并弹出「已删除，可撤销」，
 * 消费后 [clear] 置空，避免重复提示。
 *
 * 语义边界：只承载"即时撤销"所需的最小信息（交易 id）；
 * 30 天长期恢复入口已记入 `review/BACKLOG.md` B1，本期不做。
 */
package com.zhangran.app.ui.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 一条待撤销的删除记录。
 *
 * @property transactionId 被软删除的交易 id
 */
data class DeletedTransactionEvent(
    val transactionId: String,
)

/** 进程级「最近一次删除」事件（一次性；消费后清空）。 */
object TransactionDeletionEvents {

    private val _lastDeleted = MutableStateFlow<DeletedTransactionEvent?>(null)

    /** 最近一次删除（首页订阅）。 */
    val lastDeleted: StateFlow<DeletedTransactionEvent?> = _lastDeleted.asStateFlow()

    /** 详情页删除成功后发布。 */
    fun publishDeleted(transactionId: String) {
        _lastDeleted.value = DeletedTransactionEvent(transactionId)
    }

    /** 首页消费后清空，避免返回首页时重复弹窗。 */
    fun clear() {
        _lastDeleted.value = null
    }
}
