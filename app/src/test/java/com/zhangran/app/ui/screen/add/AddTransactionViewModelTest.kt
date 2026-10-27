/**
 * 记账表单的 JVM 测试（任务书 M2a 的 A2、A4、A5、A6、A7、A9）。
 *
 * 两类断言：
 * - [AmountInput] 是纯状态机 → 直接逐条断言输入规则（A2）；
 * - [AddTransactionViewModel] 用**真实的** `AddManualTransactionUseCase` + 假仓库，
 *   验证类型切换、默认账户、保存成功/失败与备注截断（A4–A7、A9）。
 *
 * 说明：项目未引入 `kotlinx-coroutines-test`，因此用 `runBlocking` + 注入
 * `CoroutineScope(Dispatchers.Unconfined)`（VM 支持 `externalScope`，与 `HomeViewModel` 一致）。
 */
package com.zhangran.app.ui.screen.add

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import com.zhangran.app.domain.usecase.AddManualTransactionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTransactionViewModelTest {

    private companion object {
        const val NOW = 1_791_610_182_000L
        const val DEFAULT_ACCOUNT_ID = "acc-cash"
        const val EXPENSE_CATEGORY_ID = "cat-food"
        const val INCOME_CATEGORY_ID = "cat-salary"
    }

    // ------------------------------------------------------------------ A2 金额输入

    @Test
    fun A2_最多两位小数() {
        var input = AmountInput()
        listOf('1', '2').forEach { input = input.digit(it) }
        input = input.dot()
        listOf('3', '4', '5').forEach { input = input.digit(it) }
        assertEquals("第三位小数应被忽略", "12.34", input.text)
    }

    @Test
    fun A2_只能有一个小数点() {
        var input = AmountInput().digit('5').dot().digit('2')
        input = input.dot()
        assertEquals("重复的小数点应被忽略", "5.2", input.text)
    }

    @Test
    fun A2_前导零后跟数字要替换() {
        var input = AmountInput().digit('0')
        assertEquals("0", input.text)
        input = input.digit('5')
        assertEquals("05 应显示为 5", "5", input.text)
        // 但 0 后面直接输入 0 仍保持 0
        assertEquals("0", AmountInput().digit('0').digit('0').text)
        // 0.5 形态合法
        assertEquals("0.5", AmountInput().digit('0').dot().digit('5').text)
    }

    @Test
    fun A2_退格与清空() {
        val input = AmountInput().digit('1').digit('2').dot().digit('3')
        assertEquals("12.3", input.text)
        assertEquals("12.", input.backspace().text)
        assertEquals("12", input.backspace().backspace().text)
        assertEquals("", input.clear().text)
        assertEquals("空串退格仍为空", "", AmountInput().backspace().text)
    }

    @Test
    fun A2_整数位上限为9位() {
        var input = AmountInput()
        repeat(12) { input = input.digit('9') }
        assertEquals("整数位最多 9 位（防止 Long 溢出）", 9, input.integerPart.length)
        assertEquals("999999999", input.text)
        // 9 位整数 + 2 位小数恰好等于 Money.MAX_AMOUNT_MINOR
        assertEquals(99_999_999_999L, input.dot().digit('9').digit('9').toMinorOrNull())
    }

    @Test
    fun A2_金额转分不经Double() {
        assertEquals(1L, AmountInput().digit('0').dot().digit('0').digit('1').toMinorOrNull())
        assertEquals("输入 1234 表示 1234 元", 123400L, AmountInput().digit('1').digit('2').digit('3').digit('4').toMinorOrNull())
        assertEquals(0L, AmountInput().digit('0').toMinorOrNull())
        assertNull("空输入无法解析", AmountInput().toMinorOrNull())
    }

    @Test
    fun A2_未输入时展示为0点00() {
        assertEquals("0.00", AmountInput().display())
        assertEquals("12.", AmountInput().digit('1').digit('2').dot().display())
    }

    // ------------------------------------------------------------------ A4 / A5 加载与切换

    @Test
    fun A4_初始加载为支出分类且默认选中默认账户() = runBlocking {
        val vm = newViewModel()
        val state = vm.uiState.value as AddTransactionUiState.Ready

        assertEquals(TransactionType.EXPENSE, state.type)
        assertTrue("支出分类应只有 EXPENSE", state.categories.isNotEmpty())
        assertEquals(EXPENSE_CATEGORY_ID, state.categories.first().id)
        assertEquals("A5：默认选中 isDefault 账户", DEFAULT_ACCOUNT_ID, state.selectedAccountId)
        assertEquals("现金", state.selectedAccountName)
    }

    @Test
    fun A4_切换类型后分类按类型重新加载且清空选中() = runBlocking {
        val vm = newViewModel()
        val before = vm.uiState.value as AddTransactionUiState.Ready
        vm.selectCategory(EXPENSE_CATEGORY_ID)
        assertEquals(EXPENSE_CATEGORY_ID, (vm.uiState.value as AddTransactionUiState.Ready).selectedCategoryId)

        vm.toggleType()
        val after = vm.uiState.value as AddTransactionUiState.Ready

        assertEquals(TransactionType.INCOME, after.type)
        assertTrue("收入分类不得混入支出分类", after.categories.all { it.id != EXPENSE_CATEGORY_ID })
        assertEquals(INCOME_CATEGORY_ID, after.categories.first().id)
        assertNull("切换类型后必须清空已选分类", after.selectedCategoryId)
        assertTrue("支出侧原本有分类", before.categories.isNotEmpty())
    }

    // ------------------------------------------------------------------ A6 保存成功

    @Test
    fun A6_保存成功落库为CONFIRMED且清空表单() = runBlocking {
        val transactions = FakeTransactionRepository()
        val vm = newViewModel(transactions = transactions)

        vm.pressDigit('1')
        vm.pressDigit('2')
        vm.pressDigit('0')
        vm.pressDigit('0')
        vm.selectCategory(EXPENSE_CATEGORY_ID)
        vm.setNote("午饭")
        vm.save()

        assertEquals("应落库一条", 1, transactions.inserted.size)
        val saved = transactions.inserted.single()
        assertEquals("键盘输入 1200 表示 1200 元 → 120000 分", 120000L, saved.amountMinor)
        assertEquals(TransactionState.CONFIRMED, saved.state)
        assertTrue("手工记账必须标记为用户确认", saved.confirmedByUser)
        assertEquals("午饭", saved.note)

        val state = vm.uiState.value as AddTransactionUiState.Ready
        assertEquals("保存成功后金额应清空", "", state.amount.text)
        assertEquals("保存成功后备注应清空", "", state.note)
        assertNull("保存成功后分类选中应清空", state.selectedCategoryId)
        assertNull("保存成功后不应有错误", state.error)
        assertEquals("类型应保留（便于连续记账）", TransactionType.EXPENSE, state.type)
        assertEquals("账户应保留", DEFAULT_ACCOUNT_ID, state.selectedAccountId)
    }

    // ------------------------------------------------------------------ A7 保存失败不落库

    @Test
    fun A7_金额为零时就地报错且不落库() = runBlocking {
        val transactions = FakeTransactionRepository()
        val vm = newViewModel(transactions = transactions)

        vm.selectCategory(EXPENSE_CATEGORY_ID)
        vm.save()

        assertEquals(FormError.AMOUNT_INVALID, (vm.uiState.value as AddTransactionUiState.Ready).error)
        assertTrue("失败不得落库", transactions.inserted.isEmpty())
    }

    @Test
    fun A7_未选分类时就地报错且不落库() = runBlocking {
        val transactions = FakeTransactionRepository()
        val vm = newViewModel(transactions = transactions)

        vm.pressDigit('5')
        vm.save()

        assertEquals(FormError.CATEGORY_REQUIRED, (vm.uiState.value as AddTransactionUiState.Ready).error)
        assertTrue("失败不得落库", transactions.inserted.isEmpty())
    }

    @Test
    fun A7_保存成功后_error被清空() = runBlocking {
        val transactions = FakeTransactionRepository()
        val vm = newViewModel(transactions = transactions)

        // 先制造一次错误
        vm.save()
        assertEquals(FormError.AMOUNT_INVALID, (vm.uiState.value as AddTransactionUiState.Ready).error)

        // 修正后保存成功
        vm.pressDigit('9')
        vm.selectCategory(EXPENSE_CATEGORY_ID)
        vm.save()

        assertNull((vm.uiState.value as AddTransactionUiState.Ready).error)
        assertEquals(1, transactions.inserted.size)
    }

    // ------------------------------------------------------------------ A9 备注

    @Test
    fun A9_备注超过500字符被截断() = runBlocking {
        val vm = newViewModel()
        vm.setNote("字".repeat(600))
        assertEquals(500, (vm.uiState.value as AddTransactionUiState.Ready).note.length)
    }

    // ------------------------------------------------------------------ 无分类数据

    @Test
    fun A1_无分类数据时状态标记为空() = runBlocking {
        val vm = newViewModel(categories = emptyList())
        val state = vm.uiState.value as AddTransactionUiState.Ready
        assertTrue(state.hasNoCategories)
        assertTrue(state.categories.isEmpty())
    }

    // ------------------------------------------------------------------ 辅助

    private fun newViewModel(
        transactions: FakeTransactionRepository = FakeTransactionRepository(),
        categories: List<CategoryEntity> = sampleCategories(),
    ): AddTransactionViewModel = AddTransactionViewModel(
        addTransaction = AddManualTransactionUseCase(
            normalize = Normalize(
                categoryResolver = CategoryResolver { null },
                accountResolver = AccountResolver { null },
                defaultZoneId = "Asia/Shanghai",
            ),
            transactionRepository = transactions,
            clock = { NOW },
            idGenerator = { "tx-1" },
        ),
        categoryRepository = FakeCategoryRepository(categories),
        accountRepository = FakeAccountRepository(sampleAccounts()),
        clock = { NOW },
        externalScope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun sampleCategories(): List<CategoryEntity> = listOf(
        CategoryEntity(
            id = EXPENSE_CATEGORY_ID,
            name = "餐饮",
            type = CategoryType.EXPENSE,
            icon = "food",
            sortOrder = 0,
            enabled = true,
            systemDefined = true,
            createdAt = NOW,
            updatedAt = NOW,
        ),
        CategoryEntity(
            id = INCOME_CATEGORY_ID,
            name = "工资",
            type = CategoryType.INCOME,
            icon = "salary",
            sortOrder = 0,
            enabled = true,
            systemDefined = true,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )

    private fun sampleAccounts(): List<AccountEntity> = listOf(
        AccountEntity(
            id = DEFAULT_ACCOUNT_ID,
            name = "现金",
            type = AccountType.CASH,
            channel = null,
            last4 = null,
            enabled = true,
            sortOrder = 0,
            isDefault = true,
            createdAt = NOW,
            updatedAt = NOW,
        ),
        AccountEntity(
            id = "acc-wechat",
            name = "微信",
            type = AccountType.THIRD_PARTY,
            channel = null,
            last4 = null,
            enabled = true,
            sortOrder = 1,
            isDefault = false,
            createdAt = NOW,
            updatedAt = NOW,
        ),
    )
}

// ---------------------------------------------------------------------- 假仓库

private class FakeTransactionRepository : TransactionRepository {
    val inserted = mutableListOf<TransactionEntity>()

    override suspend fun insert(transaction: TransactionEntity) {
        inserted += transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        inserted.filter { it.state == state }

    override suspend fun listVisible(): List<TransactionEntity> =
        inserted.filter { it.deletedAt == null }

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        inserted.firstOrNull { it.id == id }
}

private class FakeCategoryRepository(
    private val items: List<CategoryEntity>,
) : CategoryRepository {
    override suspend fun all(): List<CategoryEntity> = items

    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        items.filter { it.type == type && it.enabled }

    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        items.firstOrNull { it.type == type && it.name == name }
}

private class FakeAccountRepository(
    private val items: List<AccountEntity>,
) : AccountRepository {
    override suspend fun all(): List<AccountEntity> = items

    override suspend fun defaultAccount(): AccountEntity? = items.firstOrNull { it.isDefault }

    override suspend fun findByName(name: String): AccountEntity? = items.firstOrNull { it.name == name }
}
