/**
 * 搜索用例（`M4e` §4.3；口径 SSOT：`docs/02` §13）。
 *
 * 职责：
 * - 归一化条件（空关键字、金额等值优先、页码）后调用 Repository（SQL 在 DAO）；
 * - 解析分类名/图标与账户名（与 `ObserveTransactionsUseCase` 的降级语义一致）；
 * - 计算 `hasMore`（`total` 来自同条件 COUNT）。
 *
 * 名称解析与 `ObserveTransactionsUseCase` 的私有映射重复了一份：目标方法为私有且
 * 该文件不在本任务白名单，无法提取公共函数（已在交付报告中说明）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.SearchCriteria
import javax.inject.Inject

/**
 * @property items 当前页（≤ pageSize）
 * @property hasMore 是否还有下一页（`(page+1)*pageSize < total`）
 * @property total 同条件总条数
 */
data class SearchResult(
    val items: List<TransactionListEntry>,
    val hasMore: Boolean,
    val total: Int,
)

class SearchTransactionsUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
) {

    /**
     * 执行搜索；空条件直接返回空结果（不查库，A11 的兜底）。
     */
    suspend operator fun invoke(criteria: SearchCriteria): SearchResult {
        val normalized = criteria.normalized()
        if (!normalized.hasAnyFilter()) {
            return SearchResult(items = emptyList(), hasMore = false, total = 0)
        }

        val entities = transactions.search(normalized)
        val total = transactions.countSearch(normalized)
        val categoriesById = categories.all().associateBy { it.id }
        val accountsById = accounts.all().associateBy { it.id }

        val items = entities.map { entity ->
            // 与首页一致：转账用转出账户展示（`M2e-1a` §4.6）
            val accountId = entity.sourceAccountId ?: entity.accountId
            TransactionListEntry(
                id = entity.id,
                type = entity.type,
                amountMinor = entity.amountMinor,
                merchant = entity.merchant,
                note = entity.note,
                transactionTime = entity.transactionTime,
                // 降级：分类/账户缺失时为 null（历史交易容错，不崩溃）
                categoryName = entity.categoryId?.let { categoriesById[it]?.name },
                categoryIcon = entity.categoryId?.let { categoriesById[it]?.icon },
                accountName = accountId?.let { accountsById[it]?.name },
                source = entity.source,
                state = entity.state,
            )
        }

        val hasMore = (normalized.page + 1) * normalized.pageSize < total
        return SearchResult(items = items, hasMore = hasMore, total = total)
    }
}
