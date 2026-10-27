/**
 * 观察账务列表（`docs/06` §4.1 首页列表区、`docs/03` §6 可见性）。
 *
 * 职责：
 * - 默认**排除软删除**（由 `TransactionRepository.observeVisible()` 保证，`docs/03` §6）；
 * - 按 `transactionTime` **倒序**（`docs/06` §4.1）；
 * - 把分类 id / 账户 id 解析成**名称**（列表需要展示名称，而不是 id）；
 * - 对外只暴露领域展示类型 [TransactionListEntry]，不把实体泄漏给 UI。
 *
 * 响应式（`M2e-1a` §4.5，吸收原 `M2b-0` §4.2）：**三表 combine**——交易、分类、账户
 * 任一变化都重新发射，因此"改分类名/账户名后列表自动更新"与"记完账立刻可见"是同一个机制。
 * 若只让交易 Flow、分类账户改用一次性 `suspend` 读取，改分类名后列表不会更新。
 *
 * 不负责：详情跳转、搜索、统计（统计见 `ObserveHomeSummaryUseCase`）、编辑、删除。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

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
     * @return 随数据变化自动重新发射的可见交易列表（软删除已排除、按交易时间倒序）
     */
    operator fun invoke(): Flow<List<TransactionListEntry>> =
        combine(
            transactions.observeVisible(),
            categories.observeAll(),
            accounts.observeAll(),
        ) { visible, allCategories, allAccounts ->
            val categoriesById = allCategories.associateBy { it.id }
            val accountsById = allAccounts.associateBy { it.id }

            visible
                // DAO 已按 transactionTime DESC 排序；这里再排一次，保证"即使未来换了数据源，
                // 列表顺序也只由本用例决定"（与 M1c' 的一次性实现行为一致）。
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
                        // 名称解析保留降级行为：分类/账户缺失时为 null，不崩溃（`M2b0` §4.2）
                        categoryName = entity.categoryId?.let { categoriesById[it]?.name },
                        accountName = accountId?.let { accountsById[it]?.name },
                        source = entity.source,
                        state = entity.state,
                    )
                }
        }
}
