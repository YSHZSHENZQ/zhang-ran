/**
 * `EnsureCategoryUseCase` 三条路径测试（`M2d-1` §4.3 / 验收 A9–A11）。
 *
 * - 不存在 → 创建；
 * - 存在且启用 → 复用；
 * - 存在但禁用 → 复用并重新启用；
 * 另含幂等、类型隔离、名称规范化与非法名称。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.domain.model.CategoryType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnsureCategoryUseCaseTest {

    private fun store() = InMemoryCategoryRepository(
        listOf(
            categoryEntity("cat-food", "餐饮", sortOrder = 0, icon = "food"),
            categoryEntity("cat-shop", "购物", sortOrder = 1, icon = "shopping"),
            categoryEntity("cat-other", FALLBACK_CATEGORY_NAME, sortOrder = 2, icon = "other"),
            categoryEntity("cat-salary", "工资", CategoryType.INCOME, sortOrder = 0, icon = "salary"),
        ),
    )

    private fun useCase(store: InMemoryCategoryRepository) = EnsureCategoryUseCase(
        categories = store,
        saveCategory = SaveCategoryUseCase(categories = store, clock = CATEGORY_TEST_CLOCK),
    )

    @Test
    fun A9_不存在时按名创建并返回新分类() = runBlocking {
        val store = store()
        val before = store.rows.value.size

        val ensured = useCase(store).invoke(CategoryType.EXPENSE, "咖啡")

        assertEquals(before + 1, store.rows.value.size)
        assertEquals("咖啡", ensured.name)
        assertEquals(CategoryType.EXPENSE, ensured.type)
        assertFalse("用户自建（V5）", ensured.systemDefined)
        assertTrue(ensured.enabled)
        assertEquals("默认图标用语义标识", DEFAULT_CATEGORY_ICON, ensured.icon)
        assertEquals("追加到支出组末尾", 3, ensured.sortOrder)
    }

    @Test
    fun A10_存在且启用时复用不新增() = runBlocking {
        val store = store()
        val before = store.rows.value.size

        val ensured = useCase(store).invoke(CategoryType.EXPENSE, "餐饮")

        assertEquals("cat-food", ensured.id)
        assertEquals("不得新增", before, store.rows.value.size)
    }

    @Test
    fun A11_存在但禁用时复用并重新启用() = runBlocking {
        val store = store()
        store.rows.value = store.rows.value.map {
            if (it.id == "cat-shop") it.copy(enabled = false) else it
        }
        val before = store.rows.value.size

        val ensured = useCase(store).invoke(CategoryType.EXPENSE, "购物")

        assertEquals("复用原分类", "cat-shop", ensured.id)
        assertTrue("必须自动重新启用（否则交易会引用禁用分类）", ensured.enabled)
        assertEquals("不得新增", before, store.rows.value.size)
        assertEquals(CATEGORY_NOW, ensured.updatedAt)
        assertTrue(
            "库中记录也应已启用",
            store.rows.value.first { it.id == "cat-shop" }.enabled,
        )
    }

    @Test
    fun A10_连续调用同名具有幂等性() = runBlocking {
        val store = store()
        val useCase = useCase(store)

        val first = useCase.invoke(CategoryType.EXPENSE, "咖啡")
        val second = useCase.invoke(CategoryType.EXPENSE, "咖啡")

        assertEquals(first.id, second.id)
        assertEquals(1, store.rows.value.count { it.name == "咖啡" })
    }

    @Test
    fun 同名但不同类型互不影响() = runBlocking {
        val store = store()

        val incomeCoffee = useCase(store).invoke(CategoryType.INCOME, "咖啡")

        assertEquals(CategoryType.INCOME, incomeCoffee.type)
        assertEquals(1, store.rows.value.count { it.name == "咖啡" })
        assertNotEquals("cat-salary", incomeCoffee.id)
    }

    @Test
    fun 名称去首尾空白后创建() = runBlocking {
        val store = store()

        val ensured = useCase(store).invoke(CategoryType.EXPENSE, "  咖啡  ")

        assertEquals("咖啡", ensured.name)
    }

    @Test
    fun 空名称抛非法参数() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(store()).invoke(CategoryType.EXPENSE, "   ") }
        }
    }

    @Test
    fun 超长名称抛非法参数() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking { useCase(store()).invoke(CategoryType.EXPENSE, "字".repeat(21)) }
        }
    }
}
