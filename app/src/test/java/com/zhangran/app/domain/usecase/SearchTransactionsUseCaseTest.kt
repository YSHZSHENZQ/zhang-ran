/**
 * 搜索用例测试（`M4e` §5；A6 分页、A11 空条件、条件归一化、名称解析）。
 *
 * 纯 JVM：用内存假 Repository 验证编排逻辑（SQL 由 `SearchQueryTest` 在设备上覆盖）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun tx(
    id: String,
    merchant: String? = null,
    categoryId: String? = null,
    accountId: String? = null,
    time: Long = 1_000L,
): TransactionEntity = TransactionEntity(
    id = id,
    type = TransactionType.EXPENSE,
    amountMinor = 1_000L,
    currency = Currency.CNY,
    merchant = merchant,
    categoryId = categoryId,
    accountId = accountId,
    transactionTime = time,
    transactionTimeZone = "Asia/Shanghai",
    state = TransactionState.CONFIRMED,
    source = CaptureSource.MANUAL,
    createdAt = 1L,
    updatedAt = 1L,
)

private class FakeSearchRepository(
    private val dataset: List<TransactionEntity>,
) : TransactionRepository {

    var lastCriteria: SearchCriteria? = null
    var searchCalls = 0

    override suspend fun search(criteria: SearchCriteria): List<TransactionEntity> {
        searchCalls++
        lastCriteria = criteria
        val offset = criteria.page * criteria.pageSize
        return dataset.drop(offset).take(criteria.pageSize)
    }

    override suspend fun countSearch(criteria: SearchCriteria): Int = dataset.size

    // 其余方法不参与本测试
    override suspend fun insert(transaction: TransactionEntity) = Unit
    override suspend fun listByState(state: TransactionState): List<TransactionEntity> = dataset
    override suspend fun listVisible(): List<TransactionEntity> = dataset
    override fun observeVisible(): Flow<List<TransactionEntity>> = flowOf(dataset)
    override suspend fun listInRange(startInclusive: Long, endExclusive: Long) = dataset
    override suspend fun countByState(state: TransactionState) = dataset.size
    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? = null
    override suspend fun update(transaction: TransactionEntity) = Unit
    override suspend fun softDelete(id: String, deletedAt: Long) = Unit
    override suspend fun restore(id: String, updatedAt: Long) = Unit
}

private class FakeCategoryRepository : CategoryRepository {
    override suspend fun all(): List<com.zhangran.app.data.local.entity.CategoryEntity> = listOf(
        com.zhangran.app.data.local.entity.CategoryEntity(
            id = "cat-food",
            name = "餐饮",
            type = CategoryType.EXPENSE,
            icon = "food",
            sortOrder = 0,
            enabled = true,
            systemDefined = true,
            createdAt = 1L,
            updatedAt = 1L,
        ),
    )

    override fun observeAll(): Flow<List<com.zhangran.app.data.local.entity.CategoryEntity>> =
        flowOf(emptyList())

    override suspend fun enabledByType(type: CategoryType) = emptyList<com.zhangran.app.data.local.entity.CategoryEntity>()
    override suspend fun findByName(type: CategoryType, name: String) = null
    override suspend fun insert(category: com.zhangran.app.data.local.entity.CategoryEntity) = Unit
    override suspend fun update(category: com.zhangran.app.data.local.entity.CategoryEntity) = Unit
}

private class FakeAccountRepository : AccountRepository {
    private val cash = com.zhangran.app.data.local.entity.AccountEntity(
        id = "acc-cash",
        name = "现金",
        type = com.zhangran.app.domain.model.AccountType.CASH,
        channel = null,
        last4 = null,
        enabled = true,
        sortOrder = 0,
        isDefault = true,
        createdAt = 1L,
        updatedAt = 1L,
    )

    override suspend fun all(): List<com.zhangran.app.data.local.entity.AccountEntity> = listOf(cash)

    override fun observeAll(): Flow<List<com.zhangran.app.data.local.entity.AccountEntity>> =
        flowOf(listOf(cash))

    override suspend fun defaultAccount() = cash
    override suspend fun findByName(name: String) = cash.takeIf { it.name == name }
    override suspend fun insert(account: com.zhangran.app.data.local.entity.AccountEntity) = Unit
    override suspend fun update(account: com.zhangran.app.data.local.entity.AccountEntity) = Unit
    override suspend fun setDefault(id: String, updatedAt: Long) = Unit
    override suspend fun enable(
        account: com.zhangran.app.data.local.entity.AccountEntity,
        updatedAt: Long,
    ) = Unit
    override suspend fun disable(
        account: com.zhangran.app.data.local.entity.AccountEntity,
        updatedAt: Long,
    ) = Unit
    override suspend fun usableDefault() = cash.takeIf { it.enabled }
    override suspend fun enabledCount(): Int = 1
}

class SearchTransactionsUseCaseTest {

    private fun useCase(dataset: List<TransactionEntity>) = SearchTransactionsUseCase(
        transactions = FakeSearchRepository(dataset),
        categories = FakeCategoryRepository(),
        accounts = FakeAccountRepository(),
    )

    @Test
    fun A6_分页与hasMore() = runBlocking {
        val dataset = (1..51).map { tx("t$it", merchant = "商户$it") }
        val repository = FakeSearchRepository(dataset)
        val useCase = SearchTransactionsUseCase(repository, FakeCategoryRepository(), FakeAccountRepository())

        val first = useCase(SearchCriteria(keyword = "商户", page = 0))
        assertEquals(50, first.items.size)
        assertEquals(51, first.total)
        assertTrue("第一页后应还有更多", first.hasMore)

        val second = useCase(SearchCriteria(keyword = "商户", page = 1))
        assertEquals(1, second.items.size)
        assertFalse("最后一页 hasMore=false", second.hasMore)
        assertEquals(50, repository.lastCriteria?.pageSize)
    }

    @Test
    fun A2_keyword去空白且空串视为未填() = runBlocking {
        val repository = FakeSearchRepository(emptyList())
        val useCase = SearchTransactionsUseCase(repository, FakeCategoryRepository(), FakeAccountRepository())

        useCase(SearchCriteria(keyword = "  星巴克  "))
        assertEquals("星巴克", repository.lastCriteria?.keyword)

        // 全空条件：直接返回空且不查库
        val empty = useCase(SearchCriteria(keyword = "   "))
        assertTrue(empty.items.isEmpty())
        assertEquals(1, repository.searchCalls)
    }

    @Test
    fun A3_金额等值优先清空区间() = runBlocking {
        val repository = FakeSearchRepository(emptyList())
        val useCase = SearchTransactionsUseCase(repository, FakeCategoryRepository(), FakeAccountRepository())

        useCase(
            SearchCriteria(
                amountExactMinor = 1_500L,
                amountMinMinor = 100L,
                amountMaxMinor = 9_900L,
            ),
        )
        val sent = repository.lastCriteria
        assertEquals(1_500L, sent?.amountExactMinor)
        assertNull("等值优先：min 应被清空", sent?.amountMinMinor)
        assertNull("等值优先：max 应被清空", sent?.amountMaxMinor)
    }

    @Test
    fun 名称解析成功且缺失时降级null() = runBlocking {
        val useCase = useCase(
            listOf(
                tx("t1", merchant = "星巴克", categoryId = "cat-food", accountId = "acc-cash"),
                tx("t2", merchant = "缺失分类", categoryId = "cat-missing", accountId = "acc-missing"),
            ),
        )

        val result = useCase(SearchCriteria(keyword = "星巴克"))
        val first = result.items.first { it.id == "t1" }
        assertEquals("餐饮", first.categoryName)
        assertEquals("food", first.categoryIcon)
        assertEquals("现金", first.accountName)

        val second = result.items.first { it.id == "t2" }
        assertNull(second.categoryName)
        assertNull(second.accountName)
    }

    @Test
    fun 页码为负归一化为0() {
        assertEquals(0, SearchCriteria(page = -3).normalized().page)
    }
}
