/**
 * 观察账务列表（`docs/06` §4.1 首页列表区、`docs/03` §6 可见性）。
 *
 * 职责：
 * - 默认**排除软删除**（由 `TransactionRepository.listVisible()` 保证，`docs/03` §6）；
 * - 按 `transactionTime` **倒序**（`docs/06` §4.1）；
 * - 把分类 id / 账户 id 解析成**名称**（列表需要展示名称，而不是 id）；
 * - 对外只暴露领域展示类型 [TransactionListEntry]，不把实体泄漏给 UI。
 *
 * 不负责：详情跳转、搜索、统计、编辑、删除（属后续任务）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import javax.inject.Inject

/**
 * 账务列表项（展示所需的纯领域类型，`docs/06` §4.1）。
 *
 * @property categoryName 分类名（分类缺失或已删除时为 `null`）
 * @property accountName 账户名（账户缺失时为 `null`；转账用转出账户）
 */
data class TransactionListEntry(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val merchant: String?,
    val note: String?,
    val transactionTime: Long,
    val categoryName: String?,
    val accountName: String?,
    val source: CaptureSource,
    val state: TransactionState,
)

/**
 * 观察可见交易列表。
 *
 * @param transactions 交易读取（排除软删除，按交易时间倒序）
 * @param categories 分类名解析（**含已禁用**：历史交易引用的禁用分类仍要显示名称）
 * @param accounts 账户名解析（同上）
 */
class ObserveTransactionsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {

    /**
     * @return 按 `transactionTime` 倒序的可见交易（软删除已排除）
     */
    suspend operator fun invoke(): List<TransactionListEntry> {
        val visible = transactions.listVisible()
        val categoriesById = categories.all().associateBy { it.id }
        val accountsById = accounts.all().associateBy { it.id }

        return visible
            .sortedByDescending { it.transactionTime }
            .map { entity ->
                val accountId = entity.sourceAccountId ?: entity.accountId
                TransactionListEntry(
                    id = entity.id,
                    type = entity.type,
                    amountMinor = entity.amountMinor,
                    merchant = entity.merchant,
                    note = entity.note,
                    transactionTime = entity.transactionTime,
                    categoryName = entity.categoryId?.let { categoriesById[it]?.name },
                    accountName = accountId?.let { accountsById[it]?.name },
                    source = entity.source,
                    state = entity.state,
                )
            }
    }
}
