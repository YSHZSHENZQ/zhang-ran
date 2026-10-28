/**
 * 字段级编辑用例测试（`M2b` §4.3；对应 A5/A6/A7/A17 与 `docs/08` §5 User Edit Test）。
 *
 * 纯 JVM：仓储用内存 fake、时间用 `Clock.fixed`，逐条断言
 * "成功编辑 = 更新交易 + 一条 `FieldEdit`"、"失败不落库不写 FieldEdit"。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.ProtectedField
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ------------------------------------------------------------------ 内存 fake

private class FakeTransactionStore : TransactionRepository {
    val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun insert(transaction: TransactionEntity) {
        rows.value = rows.value.filterNot { it.id == transaction.id } + transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        rows.value.filter { it.state == state && it.deletedAt == null }

    override suspend fun listVisible(): List<TransactionEntity> =
        rows.value.filter { it.deletedAt == null }

    override fun observeVisible(): Flow<List<TransactionEntity>> = rows

    override suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity> =
        rows.value.filter { it.state == TransactionState.CONFIRMED && it.transactionTime in startInclusive until endExclusive }

    override suspend fun countByState(state: TransactionState): Int =
        rows.value.count { it.state == state && it.deletedAt == null }

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        rows.value.firstOrNull { it.id == id && (includeDeleted || it.deletedAt == null) }

    override suspend fun update(transaction: TransactionEntity) {
        rows.value = rows.value.map { if (it.id == transaction.id) transaction else it }
    }

    override suspend fun softDelete(id: String, deletedAt: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(deletedAt = deletedAt, updatedAt = deletedAt) else it
        }
    }

    override suspend fun restore(id: String, updatedAt: Long) {
        rows.value = rows.value.map {
            if (it.id == id) it.copy(deletedAt = null, updatedAt = updatedAt) else it
        }
    }

    suspend fun find(id: String): TransactionEntity? = rows.first().firstOrNull { it.id == id }
}

private class FakeCategoryStore(private val items: List<CategoryEntity>) : CategoryRepository {
    override suspend fun all(): List<CategoryEntity> = items
    override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(items)
    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        items.filter { it.type == type && it.enabled }
    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        items.firstOrNull { it.type == type && it.name == name }

    override suspend fun insert(category: CategoryEntity) = Unit

    override suspend fun update(category: CategoryEntity) = Unit
}

private class FakeAccountStore(private val items: List<AccountEntity>) : AccountRepository {
    override suspend fun all(): List<AccountEntity> = items
    override fun observeAll(): Flow<List<AccountEntity>> = MutableStateFlow(items)
    override suspend fun defaultAccount(): AccountEntity? = items.firstOrNull { it.isDefault }
    override suspend fun findByName(name: String): AccountEntity? = items.firstOrNull { it.name == name }

    override suspend fun insert(account: AccountEntity) = Unit
    override suspend fun update(account: AccountEntity) = Unit
    override suspend fun setDefault(id: String, updatedAt: Long) = Unit
    override suspend fun enable(account: AccountEntity, updatedAt: Long) = Unit
    override suspend fun disable(account: AccountEntity, updatedAt: Long) = Unit
    override suspend fun usableDefault(): AccountEntity? = items.firstOrNull { it.isDefault && it.enabled }
    override suspend fun enabledCount(): Int = items.count { it.enabled }
}

private class FakeFieldEditStore : FieldEditRepository {
    val records = mutableListOf<FieldEditEntity>()

    override suspend fun record(fieldEdit: FieldEditEntity) {
        records += fieldEdit
    }

    override suspend fun listByTransaction(transactionId: String): List<FieldEditEntity> =
        records.filter { it.transactionId == transactionId }

    override suspend fun isProtected(transactionId: String, fieldName: String): Boolean =
        records.any { it.transactionId == transactionId && it.fieldName == fieldName }
}

class UpdateTransactionFieldUseCaseTest {

    private companion object {
        const val TX_ID = "tx-1"
        const val ZONE = "Asia/Shanghai"
        const val NOW = 1_800_000_000_000L
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(ZONE))
    }

    private val transaction = TransactionEntity(
        id = TX_ID,
        type = TransactionType.EXPENSE,
        amountMinor = 3_850L,
        currency = Currency.CNY,
        merchant = "星巴克",
        categoryId = "cat-food",
        accountId = "acc-cash",
        sourceAccountId = null,
        targetAccountId = null,
        relatedTransactionId = null,
        transactionTime = NOW - 86_400_000L,
        transactionTimeZone = ZONE,
        note = "旧备注",
        state = TransactionState.CONFIRMED,
        source = CaptureSource.MANUAL,
        confirmedByUser = false,
        createdAt = NOW - 86_400_000L,
        updatedAt = NOW - 86_400_000L,
        deletedAt = null,
    )

    private val categories = FakeCategoryStore(
        listOf(
            category("cat-food", "餐饮", CategoryType.EXPENSE),
            category("cat-salary", "工资", CategoryType.INCOME),
        ),
    )
    private val accounts = FakeAccountStore(
        listOf(
            AccountEntity(
                id = "acc-cash",
                name = "现金",
                type = AccountType.CASH,
                createdAt = NOW,
                updatedAt = NOW,
                isDefault = true,
            ),
            AccountEntity(
                id = "acc-bank",
                name = "银行卡",
                type = AccountType.DEBIT_CARD,
                createdAt = NOW,
                updatedAt = NOW,
            ),
        ),
    )

    private fun useCase(
        store: FakeTransactionStore,
        fieldEdits: FakeFieldEditStore,
    ) = UpdateTransactionFieldUseCase(
        transactions = store,
        categories = categories,
        accounts = accounts,
        fieldEdits = fieldEdits,
        clock = CLOCK,
    )

    private fun storeWithTransaction(): FakeTransactionStore = FakeTransactionStore().also {
        runBlocking { it.insert(transaction) }
    }

    // ---------------------------------------------------------------- A5：成功编辑

    @Test
    fun A5_编辑商户成功并写入一条FieldEdit且置confirmedByUser() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke(
            TX_ID,
            EditableField.MERCHANT,
            FieldEditValue.Merchant("  瑞幸咖啡  "),
        )

        assertEquals(UpdateTransactionFieldResult.Success(changed = true), result)
        val updated = store.find(TX_ID)!!
        assertEquals("商户应被 trim 后写入", "瑞幸咖啡", updated.merchant)
        assertEquals("updatedAt 必须刷新", NOW, updated.updatedAt)
        assertTrue("编辑即确认（P-010）", updated.confirmedByUser)
        assertEquals("状态不得改变", TransactionState.CONFIRMED, updated.state)
        assertEquals(1, edits.records.size)
        assertEquals("merchant", edits.records.single().fieldName)
        assertEquals(NOW, edits.records.single().editedAt)
        assertTrue("应记录编辑值哈希（审计）", !edits.records.single().editedValueHash.isNullOrBlank())
    }

    @Test
    fun 编辑金额成功写入amountMinor并更新金额() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke(TX_ID, EditableField.AMOUNT, FieldEditValue.Amount(1_234L))

        assertEquals(UpdateTransactionFieldResult.Success(changed = true), result)
        assertEquals(1_234L, store.find(TX_ID)!!.amountMinor)
        assertEquals("amountMinor", edits.records.single().fieldName)
    }

    // ---------------------------------------------------------------- A6/A7：类型与分类

    @Test
    fun A6_类型可在支出与收入之间互改并清空分类() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke(
            TX_ID,
            EditableField.TYPE,
            FieldEditValue.Type(TransactionType.INCOME),
        )

        assertEquals(UpdateTransactionFieldResult.Success(changed = true), result)
        val updated = store.find(TX_ID)!!
        assertEquals(TransactionType.INCOME, updated.type)
        assertNull("类型切换后必须清空分类（A7）", updated.categoryId)
        assertEquals("type", edits.records.single().fieldName)
    }

    @Test
    fun A6_不允许改成转账或退款() = runBlocking {
        listOf(TransactionType.TRANSFER, TransactionType.REFUND).forEach { forbidden ->
            val store = storeWithTransaction()
            val edits = FakeFieldEditStore()

            val result = useCase(store, edits).invoke(
                TX_ID,
                EditableField.TYPE,
                FieldEditValue.Type(forbidden),
            )

            assertEquals(
                UpdateTransactionFieldResult.Rejected(FieldEditRejection.TYPE_NOT_SUPPORTED),
                result,
            )
            assertEquals("失败不得落库", transaction, store.find(TX_ID))
            assertTrue("失败不得写 FieldEdit", edits.records.isEmpty())
        }
    }

    @Test
    fun A6_转账或退款交易本身不允许编辑类型() = runBlocking {
        val store = FakeTransactionStore()
        runBlocking { store.insert(transaction.copy(type = TransactionType.TRANSFER, categoryId = null)) }

        val result = useCase(store, FakeFieldEditStore()).invoke(
            TX_ID,
            EditableField.TYPE,
            FieldEditValue.Type(TransactionType.EXPENSE),
        )

        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.TYPE_NOT_SUPPORTED),
            result,
        )
    }

    @Test
    fun A7_分类必须属于当前类型() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        // 支出交易选收入分类 → 拒绝
        val mismatched = useCase(store, edits).invoke(
            TX_ID,
            EditableField.CATEGORY,
            FieldEditValue.Category("cat-salary"),
        )
        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.CATEGORY_TYPE_MISMATCH),
            mismatched,
        )

        // 不存在的分类 → 拒绝
        val missing = useCase(store, edits).invoke(
            TX_ID,
            EditableField.CATEGORY,
            FieldEditValue.Category("cat-nope"),
        )
        assertEquals(UpdateTransactionFieldResult.Rejected(FieldEditRejection.CATEGORY_MISSING), missing)
        assertTrue(edits.records.isEmpty())
    }

    @Test
    fun 分类编辑成功写入categoryId() = runBlocking {
        val store = FakeTransactionStore()
        runBlocking {
            store.insert(transaction.copy(type = TransactionType.INCOME, categoryId = "cat-salary"))
        }
        // 收入交易换成另一个收入分类：这里换成同一个 → 无变化；先造第二个收入分类
        val incomeCategories = FakeCategoryStore(
            listOf(
                category("cat-salary", "工资", CategoryType.INCOME),
                category("cat-bonus", "奖金", CategoryType.INCOME),
            ),
        )
        val edits = FakeFieldEditStore()
        val subject = UpdateTransactionFieldUseCase(
            transactions = store,
            categories = incomeCategories,
            accounts = accounts,
            fieldEdits = edits,
            clock = CLOCK,
        )

        val result = subject.invoke(TX_ID, EditableField.CATEGORY, FieldEditValue.Category("cat-bonus"))

        assertEquals(UpdateTransactionFieldResult.Success(changed = true), result)
        assertEquals("cat-bonus", store.find(TX_ID)!!.categoryId)
        assertEquals("categoryId", edits.records.single().fieldName)
    }

    // ---------------------------------------------------------------- 账户 / 时间 / 备注

    @Test
    fun 账户必须存在且成功后写入accountId() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.ACCOUNT_MISSING),
            useCase(store, edits).invoke(TX_ID, EditableField.ACCOUNT, FieldEditValue.Account("acc-nope")),
        )
        assertEquals(
            UpdateTransactionFieldResult.Success(changed = true),
            useCase(store, edits).invoke(TX_ID, EditableField.ACCOUNT, FieldEditValue.Account("acc-bank")),
        )
        assertEquals("acc-bank", store.find(TX_ID)!!.accountId)
        assertEquals("accountId", edits.records.single().fieldName)
    }

    @Test
    fun 时间编辑校验正数与显式时区且不改交易时区() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.TIME_INVALID),
            useCase(store, edits).invoke(TX_ID, EditableField.TIME, FieldEditValue.Time(0L, ZONE)),
        )
        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.TIME_INVALID),
            useCase(store, edits).invoke(TX_ID, EditableField.TIME, FieldEditValue.Time(NOW, "Mars/Olympus")),
        )

        val target = NOW - 3_600_000L
        assertEquals(
            UpdateTransactionFieldResult.Success(changed = true),
            useCase(store, edits).invoke(TX_ID, EditableField.TIME, FieldEditValue.Time(target, ZONE)),
        )
        val updated = store.find(TX_ID)!!
        assertEquals(target, updated.transactionTime)
        assertEquals("交易发生时区不得被编辑改写", ZONE, updated.transactionTimeZone)
        assertEquals("transactionTime", edits.records.single().fieldName)
    }

    @Test
    fun 备注超长被拒且空备注写成null() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        assertEquals(
            UpdateTransactionFieldResult.Rejected(FieldEditRejection.NOTE_TOO_LONG),
            useCase(store, edits).invoke(TX_ID, EditableField.NOTE, FieldEditValue.Note("字".repeat(501))),
        )
        assertTrue(edits.records.isEmpty())

        assertEquals(
            UpdateTransactionFieldResult.Success(changed = true),
            useCase(store, edits).invoke(TX_ID, EditableField.NOTE, FieldEditValue.Note("  ")),
        )
        assertNull(store.find(TX_ID)!!.note)
        assertEquals("note", edits.records.single().fieldName)
    }

    // ---------------------------------------------------------------- A17：失败不落库

    @Test
    fun A17_金额为零与负值均不落库不写FieldEdit() = runBlocking {
        listOf(0L, -100L).forEach { invalid ->
            val store = storeWithTransaction()
            val edits = FakeFieldEditStore()

            val result = useCase(store, edits).invoke(TX_ID, EditableField.AMOUNT, FieldEditValue.Amount(invalid))

            assertEquals(UpdateTransactionFieldResult.Rejected(FieldEditRejection.AMOUNT_INVALID), result)
            assertEquals("失败不得落库", transaction, store.find(TX_ID))
            assertTrue("失败不得写 FieldEdit", edits.records.isEmpty())
        }
    }

    @Test
    fun A17_value类型与字段错配时拒绝() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke(TX_ID, EditableField.AMOUNT, FieldEditValue.Merchant("x"))

        assertEquals(UpdateTransactionFieldResult.Rejected(FieldEditRejection.VALUE_MISMATCH), result)
        assertTrue(edits.records.isEmpty())
    }

    @Test
    fun 交易不存在时返回NotFound() = runBlocking {
        val store = FakeTransactionStore()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke("missing", EditableField.NOTE, FieldEditValue.Note("x"))

        assertEquals(UpdateTransactionFieldResult.Rejected(FieldEditRejection.NOT_FOUND), result)
        assertTrue(edits.records.isEmpty())
    }

    // ---------------------------------------------------------------- 无变化 / 名单

    @Test
    fun 新值与旧值相同时不落库不写FieldEdit() = runBlocking {
        val store = storeWithTransaction()
        val edits = FakeFieldEditStore()

        val result = useCase(store, edits).invoke(
            TX_ID,
            EditableField.MERCHANT,
            FieldEditValue.Merchant("星巴克"),
        )

        assertEquals(UpdateTransactionFieldResult.Success(changed = false), result)
        assertEquals(transaction, store.find(TX_ID))
        assertTrue(edits.records.isEmpty())
    }

    @Test
    fun 字段名与docs03名单逐字一致() {
        val names = EditableField.entries.map { it.fieldName }.toSet()
        names.forEach { assertTrue("$it 必须在 docs/03 §11 名单内", it in ProtectedField.ALLOWED_FIELD_NAMES) }
        assertEquals(7, names.size)
        assertFalse("转账双账户字段属 M2c", names.contains("sourceAccountId"))
        assertFalse("转账双账户字段属 M2c", names.contains("targetAccountId"))
    }
}

private fun category(id: String, name: String, type: CategoryType) = CategoryEntity(
    id = id,
    name = name,
    type = type,
    icon = if (type == CategoryType.EXPENSE) "food" else "salary",
    sortOrder = 0,
    enabled = true,
    systemDefined = true,
    createdAt = 1L,
    updatedAt = 1L,
)
