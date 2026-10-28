/**
 * `SaveBudgetUseCase` 校验与唯一性测试（`M4a` §4.4 / 验收 A8、A9、A20、A22）。
 *
 * 覆盖 V1–V6、TOTAL/CATEGORY 的唯一性（重复保存走更新）、删除（物理删除）、示例预算幂等。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.repository.BudgetRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.model.CategoryType
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ------------------------------------------------------------------ 内存 fake

internal class InMemoryBudgetRepository(initial: List<BudgetEntity> = emptyList()) : BudgetRepository {

    val rows: MutableStateFlow<List<BudgetEntity>> = MutableStateFlow(initial)

    override suspend fun listByPeriodKey(periodKey: String): List<BudgetEntity> =
        rows.value.filter { it.periodKey == periodKey }

    override suspend fun findById(id: String): BudgetEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun findTotal(periodKey: String): BudgetEntity? =
        rows.value.firstOrNull { it.periodKey == periodKey && it.scope == BudgetScope.TOTAL }

    override suspend fun insertTotalIfAbsent(budget: BudgetEntity): Boolean {
        if (findTotal(budget.periodKey) != null) return false
        rows.value = rows.value + budget
        return true
    }

    override suspend fun insert(budget: BudgetEntity) {
        rows.value = rows.value + budget
    }

    override suspend fun update(budget: BudgetEntity) {
        rows.value = rows.value.map { if (it.id == budget.id) budget else it }
    }

    override suspend fun delete(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    fun totalCount(periodKey: String): Int =
        rows.value.count { it.periodKey == periodKey && it.scope == BudgetScope.TOTAL }
}

private class FakeBudgetCategoryRepository(private val items: List<CategoryEntity>) : CategoryRepository {
    override suspend fun all(): List<CategoryEntity> = items
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(items)
    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        items.filter { it.type == type && it.enabled }
    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        items.firstOrNull { it.type == type && it.name == name }
    override suspend fun insert(category: CategoryEntity) = Unit
    override suspend fun update(category: CategoryEntity) = Unit
}

private fun category(id: String, name: String, type: CategoryType = CategoryType.EXPENSE) = CategoryEntity(
    id = id,
    name = name,
    type = type,
    icon = "food",
    sortOrder = 0,
    enabled = true,
    systemDefined = true,
    createdAt = 1L,
    updatedAt = 1L,
)

private val CATEGORIES = listOf(
    category("cat-food", "餐饮"),
    category("cat-traffic", "交通"),
    category("cat-shop", "购物"),
    category("cat-fun", "娱乐"),
    category("cat-salary", "工资", CategoryType.INCOME),
)

private const val PERIOD_KEY = "2026-10"
private const val NOW = 1_800_000_000_000L
private val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("Asia/Shanghai"))

class SaveBudgetUseCaseTest {

    private fun store(initial: List<BudgetEntity> = emptyList()) = InMemoryBudgetRepository(initial)

    private fun useCase(store: InMemoryBudgetRepository) = SaveBudgetUseCase(
        budgets = store,
        categories = FakeBudgetCategoryRepository(CATEGORIES),
        clock = CLOCK,
    )

    private fun totalInput(limitMinor: Long = 500_000L, id: String? = null, periodKey: String = PERIOD_KEY) =
        BudgetInput(id = id, scope = BudgetScope.TOTAL, periodKey = periodKey, limitMinor = limitMinor)

    private fun categoryInput(
        categoryId: String = "cat-food",
        limitMinor: Long = 100_000L,
        id: String? = null,
        periodKey: String = PERIOD_KEY,
    ) = BudgetInput(
        id = id,
        scope = BudgetScope.CATEGORY,
        categoryId = categoryId,
        periodKey = periodKey,
        limitMinor = limitMinor,
    )

    // ---------------------------------------------------------------- V1

    @Test
    fun V1_额度必须为正且不超过Money上限() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        listOf(0L, -1L, 100_000_000_000L).forEach { invalid ->
            val result = useCase(totalInput(limitMinor = invalid))
            assertEquals(
                SaveBudgetResult.Rejected(BudgetSaveError.LIMIT_INVALID),
                result,
            )
        }
        assertTrue("失败不得落库", store.rows.value.isEmpty())
    }

    // ---------------------------------------------------------------- V2 / V5

    @Test
    fun V2_总预算不得带分类_分类预算必须有分类() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        assertEquals(
            SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_NOT_ALLOWED),
            useCase(
                BudgetInput(
                    scope = BudgetScope.TOTAL,
                    categoryId = "cat-food",
                    periodKey = PERIOD_KEY,
                    limitMinor = 100_000L,
                ),
            ),
        )
        assertEquals(
            SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_REQUIRED),
            useCase(
                BudgetInput(
                    scope = BudgetScope.CATEGORY,
                    categoryId = null,
                    periodKey = PERIOD_KEY,
                    limitMinor = 100_000L,
                ),
            ),
        )
    }

    @Test
    fun V5_分类不存在或不是支出分类时拒绝() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        assertEquals(
            SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_MISSING),
            useCase(categoryInput(categoryId = "missing")),
        )
        assertEquals(
            "收入分类不应成为消费预算",
            SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_MISSING),
            useCase(categoryInput(categoryId = "cat-salary")),
        )
        assertTrue(store.rows.value.isEmpty())
    }

    // ---------------------------------------------------------------- V6

    @Test
    fun V6_periodKey必须是YYYY_MM() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        listOf("2026-13", "2026-1", "2026-00", "", "2026/10", "abcd-ef").forEach { invalid ->
            assertEquals(
                invalid,
                SaveBudgetResult.Rejected(BudgetSaveError.PERIOD_KEY_INVALID),
                useCase(totalInput(periodKey = invalid)),
            )
        }
        assertTrue(store.rows.value.isEmpty())
    }

    // ---------------------------------------------------------------- V3 / A8

    @Test
    fun A8_总预算每periodKey唯一_重复保存走更新() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        val first = useCase(totalInput(limitMinor = 500_000L)) as SaveBudgetResult.Success
        assertTrue(first.created)

        val second = useCase(totalInput(limitMinor = 800_000L)) as SaveBudgetResult.Success
        assertFalse("第二次必须是更新", second.created)
        assertEquals(first.budget.id, second.budget.id)
        assertEquals(800_000L, second.budget.limitMinor)
        assertEquals(1, store.totalCount(PERIOD_KEY))
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun A8_不同月份的总预算互不影响() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        useCase(totalInput(periodKey = "2026-10"))
        useCase(totalInput(periodKey = "2026-11"))

        assertEquals(1, store.totalCount("2026-10"))
        assertEquals(1, store.totalCount("2026-11"))
    }

    // ---------------------------------------------------------------- V4 / A9

    @Test
    fun A9_分类预算每periodKey加分类唯一_重复保存走更新() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        val first = useCase(categoryInput(limitMinor = 100_000L)) as SaveBudgetResult.Success
        assertTrue(first.created)

        val second = useCase(categoryInput(limitMinor = 150_000L)) as SaveBudgetResult.Success
        assertFalse(second.created)
        assertEquals(first.budget.id, second.budget.id)
        assertEquals(150_000L, second.budget.limitMinor)
        assertEquals(1, store.rows.value.size)
    }

    @Test
    fun A9_不同分类可以各自建立预算() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        useCase(categoryInput(categoryId = "cat-food", limitMinor = 100_000L))
        useCase(categoryInput(categoryId = "cat-traffic", limitMinor = 30_000L))

        assertEquals(2, store.rows.value.size)
    }

    @Test
    fun A10_总额与分类预算独立不叠加校验() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        // 分类预算之和远超总预算也允许（`02` §307：不做叠加校验）
        useCase(totalInput(limitMinor = 100_000L))
        useCase(categoryInput(categoryId = "cat-food", limitMinor = 200_000L))
        useCase(categoryInput(categoryId = "cat-shop", limitMinor = 300_000L))

        assertEquals(3, store.rows.value.size)
    }

    // ---------------------------------------------------------------- 更新 / 删除

    @Test
    fun 按id更新不得改scope或periodKey() = runBlocking {
        val store = store()
        val useCase = useCase(store)
        val created = useCase(categoryInput()) as SaveBudgetResult.Success

        assertEquals(
            SaveBudgetResult.Rejected(BudgetSaveError.SCOPE_CHANGE_NOT_ALLOWED),
            useCase(
                BudgetInput(
                    id = created.budget.id,
                    scope = BudgetScope.CATEGORY,
                    categoryId = "cat-shop",
                    periodKey = PERIOD_KEY,
                    limitMinor = 1_000L,
                ),
            ),
        )
        assertEquals(
            SaveBudgetResult.Rejected(BudgetSaveError.PERIOD_CHANGE_NOT_ALLOWED),
            useCase(
                BudgetInput(
                    id = created.budget.id,
                    scope = BudgetScope.CATEGORY,
                    categoryId = "cat-food",
                    periodKey = "2026-11",
                    limitMinor = 1_000L,
                ),
            ),
        )
    }

    @Test
    fun 按id更新不存在的预算返回NOT_FOUND() = runBlocking {
        val store = store()

        val result = useCase(store)(
            BudgetInput(
                id = "missing",
                scope = BudgetScope.TOTAL,
                periodKey = PERIOD_KEY,
                limitMinor = 1_000L,
            ),
        )
        assertEquals(SaveBudgetResult.Rejected(BudgetSaveError.NOT_FOUND), result)
    }

    @Test
    fun A20_删除为物理删除且二次确认由UI负责() = runBlocking {
        val store = store()
        val useCase = useCase(store)
        val created = useCase(categoryInput()) as SaveBudgetResult.Success

        assertEquals(DeleteBudgetResult.DELETED, useCase.delete(created.budget.id))
        assertNull("删除后回到未设置", store.findById(created.budget.id))
        assertTrue(store.rows.value.isEmpty())

        assertEquals(DeleteBudgetResult.NOT_FOUND, useCase.delete(created.budget.id))
    }

    @Test
    fun 删除总预算不影响分类预算() = runBlocking {
        val store = store()
        val useCase = useCase(store)
        val total = useCase(totalInput()) as SaveBudgetResult.Success
        useCase(categoryInput())

        useCase.delete(total.budget.id)

        assertEquals(0, store.totalCount(PERIOD_KEY))
        assertEquals(1, store.rows.value.size)
    }

    // ---------------------------------------------------------------- A22 示例预算

    @Test
    fun A22_示例预算首次写入五项且再次调用不重复创建() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        val created = useCase.applyExampleBudgets(PERIOD_KEY)
        assertEquals("总预算 + 4 个分类预算", 5, created)
        assertEquals(1, store.totalCount(PERIOD_KEY))
        assertEquals(5, store.rows.value.size)

        val again = useCase.applyExampleBudgets(PERIOD_KEY)
        assertEquals("已存在时不得重复创建", 0, again)
        assertEquals(5, store.rows.value.size)
    }

    @Test
    fun A22_示例预算不覆盖用户已手设的金额() = runBlocking {
        val store = store()
        val useCase = useCase(store)
        val mine = useCase(totalInput(limitMinor = 900_000L)) as SaveBudgetResult.Success

        useCase.applyExampleBudgets(PERIOD_KEY)

        val total = store.findTotal(PERIOD_KEY)!!
        assertEquals("不得覆盖用户金额", 900_000L, total.limitMinor)
        assertEquals(mine.budget.id, total.id)
        assertEquals("其余四项照常创建", 4, store.rows.value.size - 1)
    }

    @Test
    fun A22_示例预算只写指定月份() = runBlocking {
        val store = store()
        useCase(store).applyExampleBudgets("2026-10")

        assertTrue(store.rows.value.all { it.periodKey == "2026-10" })
        assertEquals(0, store.totalCount("2026-11"))
    }

    @Test
    fun A22_分类缺失时跳过对应示例项() = runBlocking {
        val store = store()
        val useCase = SaveBudgetUseCase(
            budgets = store,
            categories = FakeBudgetCategoryRepository(listOf(category("cat-food", "餐饮"))),
            clock = CLOCK,
        )

        val created = useCase.applyExampleBudgets(PERIOD_KEY)

        // 只有总预算 + 餐饮（交通/购物/娱乐 分类不存在 → 跳过）
        assertEquals(2, created)
        assertEquals(2, store.rows.value.size)
        assertNotEquals(0, store.totalCount(PERIOD_KEY))
    }

    @Test
    fun 新预算时间戳来自时钟且period为MONTH() = runBlocking {
        val store = store()
        val created = useCase(store)(totalInput()) as SaveBudgetResult.Success

        assertEquals(NOW, created.budget.createdAt)
        assertEquals(NOW, created.budget.updatedAt)
        assertEquals(com.zhangran.app.domain.model.BudgetPeriod.MONTH, created.budget.period)
        assertTrue(created.budget.enabled)
    }
}
