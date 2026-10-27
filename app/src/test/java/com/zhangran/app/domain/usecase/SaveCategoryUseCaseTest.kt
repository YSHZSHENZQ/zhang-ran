/**
 * `SaveCategoryUseCase` 校验规则测试（`M2d-1` §4.2 / 验收 A2–A5、A12；`docs/02` §9、`docs/03` §7）。
 *
 * 覆盖 V1–V8 每条规则，并断言"失败不落库"。
 * 另有一条守卫测试：领域层的 `KNOWN_CATEGORY_ICONS` 与 UI 的 `CategoryIconMapper.supportedIcons`
 * 必须逐项一致（防止两份清单漂移）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.ui.common.CategoryIconMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ------------------------------------------------------------------ 内存 fake

internal class InMemoryCategoryRepository(initial: List<CategoryEntity>) : CategoryRepository {

    val rows: MutableStateFlow<List<CategoryEntity>> = MutableStateFlow(initial)

    override suspend fun all(): List<CategoryEntity> = rows.value

    override fun observeAll(): Flow<List<CategoryEntity>> = rows

    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        rows.value.filter { it.type == type && it.enabled }

    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        rows.value.firstOrNull { it.type == type && it.name == name }

    override suspend fun insert(category: CategoryEntity) {
        rows.value = rows.value + category
    }

    override suspend fun update(category: CategoryEntity) {
        rows.value = rows.value.map { if (it.id == category.id) category else it }
    }

    fun byName(type: CategoryType, name: String): CategoryEntity? =
        rows.value.firstOrNull { it.type == type && it.name == name }
}

/** 测试固定时钟（与生产 `Clock` 同一抽象）。 */
internal val CATEGORY_TEST_CLOCK: Clock =
    Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneId.of("Asia/Shanghai"))

internal const val CATEGORY_NOW: Long = 1_800_000_000_000L

internal fun categoryEntity(
    id: String,
    name: String,
    type: CategoryType = CategoryType.EXPENSE,
    icon: String = "food",
    sortOrder: Int = 0,
    enabled: Boolean = true,
    systemDefined: Boolean = true,
): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    type = type,
    icon = icon,
    sortOrder = sortOrder,
    enabled = enabled,
    systemDefined = systemDefined,
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

private fun initialCategories(): List<CategoryEntity> = listOf(
    categoryEntity("cat-food", "餐饮", sortOrder = 0, icon = "food"),
    categoryEntity("cat-shop", "购物", sortOrder = 1, icon = "shopping"),
    categoryEntity("cat-other", FALLBACK_CATEGORY_NAME, sortOrder = 2, icon = "other"),
    categoryEntity("cat-salary", "工资", CategoryType.INCOME, sortOrder = 0, icon = "salary"),
    categoryEntity("cat-bonus", "奖金", CategoryType.INCOME, sortOrder = 1, icon = "bonus"),
)

class SaveCategoryUseCaseTest {

    private fun useCase(store: InMemoryCategoryRepository) =
        SaveCategoryUseCase(categories = store, clock = CATEGORY_TEST_CLOCK)

    private fun store() = InMemoryCategoryRepository(initialCategories())

    // ---------------------------------------------------------------- V1 / 长度

    @Test
    fun V1_名称为空或全空白被拒且不落库() = runBlocking {
        listOf("", "   ", "\t\n").forEach { blank ->
            val store = store()
            val before = store.rows.value.size

            val result = useCase(store).invoke(
                CategoryInput(id = null, name = blank, type = CategoryType.EXPENSE, icon = "food"),
            )

            assertEquals(SaveCategoryResult.Rejected(CategorySaveError.NAME_EMPTY), result)
            assertEquals("失败不得落库", before, store.rows.value.size)
        }
    }

    @Test
    fun 名称上限20字_21字被拒20字通过() = runBlocking {
        val store = store()

        val tooLong = useCase(store).invoke(
            CategoryInput(id = null, name = "字".repeat(21), type = CategoryType.EXPENSE, icon = "food"),
        )
        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.NAME_TOO_LONG), tooLong)
        assertEquals(initialCategories().size, store.rows.value.size)

        val twenty = useCase(store).invoke(
            CategoryInput(id = null, name = "字".repeat(20), type = CategoryType.EXPENSE, icon = "food"),
        )
        assertTrue("20 字应通过", twenty is SaveCategoryResult.Success)
        assertNotNull(store.byName(CategoryType.EXPENSE, "字".repeat(20)))
    }

    // ---------------------------------------------------------------- V2 唯一

    @Test
    fun V2_同type下重名新增被拒且不落库() = runBlocking {
        val store = store()
        val before = store.rows.value.size

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "餐饮", type = CategoryType.EXPENSE, icon = "food"),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.DUPLICATE_NAME), result)
        assertEquals(before, store.rows.value.size)
    }

    @Test
    fun V2_不同type同名允许() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "餐饮", type = CategoryType.INCOME, icon = "food"),
        )

        assertTrue(result is SaveCategoryResult.Success)
        assertEquals(2, store.rows.value.count { it.name == "餐饮" })
    }

    @Test
    fun V2_更新改名撞同type其它分类被拒且原名保留() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-shop", name = "餐饮", type = CategoryType.EXPENSE, icon = "shopping"),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.DUPLICATE_NAME), result)
        assertEquals("购物", store.rows.value.first { it.id == "cat-shop" }.name)
    }

    @Test
    fun V2_更新保持自身名称不算重名() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-shop", name = "购物", type = CategoryType.EXPENSE, icon = "shopping", enabled = false),
        )

        assertTrue(result is SaveCategoryResult.Success)
        assertFalse(store.rows.value.first { it.id == "cat-shop" }.enabled)
    }

    // ---------------------------------------------------------------- V3 兜底

    @Test
    fun V3_其他分类不可改名() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-other", name = "杂项", type = CategoryType.EXPENSE, icon = "other"),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.FALLBACK_PROTECTED), result)
        assertEquals(FALLBACK_CATEGORY_NAME, store.rows.value.first { it.id == "cat-other" }.name)
    }

    @Test
    fun V3_其他分类不可禁用() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-other", name = FALLBACK_CATEGORY_NAME, type = CategoryType.EXPENSE, icon = "other", enabled = false),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.FALLBACK_PROTECTED), result)
        assertTrue(store.rows.value.first { it.id == "cat-other" }.enabled)
    }

    @Test
    fun V3_其他分类仍可改图标与排序() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-other", name = FALLBACK_CATEGORY_NAME, type = CategoryType.EXPENSE, icon = "gift", sortOrder = 9),
        )

        assertTrue(result is SaveCategoryResult.Success)
        val updated = store.rows.value.first { it.id == "cat-other" }
        assertEquals("gift", updated.icon)
        assertEquals(9, updated.sortOrder)
        assertTrue("仍启用", updated.enabled)
    }

    // ---------------------------------------------------------------- V4 无删除

    @Test
    fun V4_仓储与用例均不提供删除能力() {
        val repositoryMethods = CategoryRepository::class.java.methods.map { it.name.lowercase() }
        assertTrue(
            "P-011：分类不提供物理删除",
            repositoryMethods.none { it.contains("delete") || it.contains("remove") },
        )
        val useCaseMethods = SaveCategoryUseCase::class.java.methods.map { it.name.lowercase() }
        assertTrue(
            "写用例不得有删除入口",
            useCaseMethods.none { it.contains("delete") || it.contains("remove") },
        )
    }

    // ---------------------------------------------------------------- V5 新增

    @Test
    fun V5_新分类为用户自建且时间戳来自时钟() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "咖啡", type = CategoryType.EXPENSE, icon = "food"),
        )

        val success = result as SaveCategoryResult.Success
        assertTrue(success.created)
        assertEquals(false, success.category.systemDefined)
        assertTrue(success.category.enabled)
        assertEquals(CATEGORY_NOW, success.category.createdAt)
        assertEquals(CATEGORY_NOW, success.category.updatedAt)
        assertNotNull(store.byName(CategoryType.EXPENSE, "咖啡"))
    }

    // ---------------------------------------------------------------- V6 排序

    @Test
    fun V6_未指定排序时追加到同type末尾() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "咖啡", type = CategoryType.EXPENSE, icon = "food"),
        ) as SaveCategoryResult.Success

        // 支出组现有 0/1/2 → 新分类应为 3
        assertEquals(3, result.category.sortOrder)
    }

    @Test
    fun V6_指定排序时按指定值落库() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "咖啡", type = CategoryType.EXPENSE, icon = "food", sortOrder = 7),
        ) as SaveCategoryResult.Success

        assertEquals(7, result.category.sortOrder)
    }

    @Test
    fun V6_追加排序只看同type不影响另一组() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "分红", type = CategoryType.INCOME, icon = "investment"),
        ) as SaveCategoryResult.Success

        // 收入组现有 0/1 → 新分类应为 2（支出组的 2 不参与）
        assertEquals(2, result.category.sortOrder)
    }

    // ---------------------------------------------------------------- V7 图标

    @Test
    fun V7_未知图标回退默认语义标识() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "咖啡", type = CategoryType.EXPENSE, icon = "🍜"),
        ) as SaveCategoryResult.Success

        assertEquals(
            "不得把 emoji 写入数据库（A12）",
            DEFAULT_CATEGORY_ICON,
            result.category.icon,
        )
    }

    @Test
    fun V7_已知图标原样保存() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "咖啡", type = CategoryType.EXPENSE, icon = "entertainment"),
        ) as SaveCategoryResult.Success

        assertEquals("entertainment", result.category.icon)
    }

    @Test
    fun 领域图标清单与UI映射逐项一致() {
        assertEquals(
            "KNOWN_CATEGORY_ICONS 与 CategoryIconMapper.supportedIcons 必须一致",
            CategoryIconMapper.supportedIcons,
            KNOWN_CATEGORY_ICONS,
        )
    }

    // ---------------------------------------------------------------- V8 类型

    @Test
    fun V8_更新时不得改type() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-food", name = "餐饮", type = CategoryType.INCOME, icon = "food"),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.TYPE_CHANGE_NOT_ALLOWED), result)
        assertEquals(CategoryType.EXPENSE, store.rows.value.first { it.id == "cat-food" }.type)
    }

    @Test
    fun 更新成功保留createdAt并刷新updatedAt() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "cat-food", name = "吃饭", type = CategoryType.EXPENSE, icon = "entertainment", enabled = false),
        ) as SaveCategoryResult.Success

        assertFalse(result.created)
        val updated = store.rows.value.first { it.id == "cat-food" }
        assertEquals("吃饭", updated.name)
        assertEquals("entertainment", updated.icon)
        assertFalse(updated.enabled)
        assertEquals("createdAt 不得被更新改写", 1_000L, updated.createdAt)
        assertEquals(CATEGORY_NOW, updated.updatedAt)
    }

    @Test
    fun 更新目标不存在时返回NOT_FOUND() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = "missing", name = "餐饮", type = CategoryType.EXPENSE, icon = "food"),
        )

        assertEquals(SaveCategoryResult.Rejected(CategorySaveError.NOT_FOUND), result)
    }

    @Test
    fun 名称首尾空白被去除() = runBlocking {
        val store = store()

        val result = useCase(store).invoke(
            CategoryInput(id = null, name = "  咖啡  ", type = CategoryType.EXPENSE, icon = "food"),
        ) as SaveCategoryResult.Success

        assertEquals("咖啡", result.category.name)
    }
}
