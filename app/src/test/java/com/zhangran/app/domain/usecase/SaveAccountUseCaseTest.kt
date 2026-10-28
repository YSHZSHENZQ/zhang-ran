/**
 * `SaveAccountUseCase` 校验规则测试（`M2d-2` §4.2 / 验收 A2–A6、A10；`docs/03` §8）。
 *
 * 覆盖 V1–V8、D-1（至少保留一个启用账户）与默认账户事务语义（fake 的 `setDefault`
 * 用"先清后置"实现，与 `AccountDao.setAsDefault` 的 `@Transaction` 语义一致）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ------------------------------------------------------------------ 内存 fake

internal class InMemoryAccountRepository(initial: List<AccountEntity>) : AccountRepository {

    val rows: MutableStateFlow<List<AccountEntity>> = MutableStateFlow(initial)

    override suspend fun all(): List<AccountEntity> = rows.value

    override fun observeAll(): Flow<List<AccountEntity>> = rows

    override suspend fun defaultAccount(): AccountEntity? = rows.value.firstOrNull { it.isDefault }

    override suspend fun findByName(name: String): AccountEntity? =
        rows.value.firstOrNull { it.name == name }

    override suspend fun insert(account: AccountEntity) {
        rows.value = rows.value + account
    }

    override suspend fun update(account: AccountEntity) {
        rows.value = rows.value.map { if (it.id == account.id) account else it }
    }

    /** 与 DAO `@Transaction` 同语义：先在**一次写入**里清全部默认，再置目标。 */
    override suspend fun setDefault(id: String, updatedAt: Long) {
        rows.value = rows.value.map {
            when {
                it.id == id -> it.copy(isDefault = true, updatedAt = updatedAt)
                it.isDefault -> it.copy(isDefault = false)
                else -> it
            }
        }
    }

    override suspend fun enable(account: AccountEntity, updatedAt: Long) =
        update(account.copy(enabled = true, updatedAt = updatedAt))

    override suspend fun disable(account: AccountEntity, updatedAt: Long) =
        update(account.copy(enabled = false, updatedAt = updatedAt))

    override suspend fun usableDefault(): AccountEntity? =
        rows.value.firstOrNull { it.isDefault && it.enabled }

    override suspend fun enabledCount(): Int = rows.value.count { it.enabled }

    fun countDefaults(): Int = rows.value.count { it.isDefault }

    fun find(id: String): AccountEntity? = rows.value.firstOrNull { it.id == id }
}

internal val ACCOUNT_TEST_CLOCK: Clock =
    Clock.fixed(Instant.ofEpochMilli(1_800_000_000_000L), ZoneId.of("Asia/Shanghai"))

internal const val ACCOUNT_NOW: Long = 1_800_000_000_000L

internal fun accountEntity(
    id: String,
    name: String,
    type: AccountType = AccountType.CASH,
    channel: PaymentChannel? = null,
    last4: String? = null,
    enabled: Boolean = true,
    isDefault: Boolean = false,
    sortOrder: Int = 0,
): AccountEntity = AccountEntity(
    id = id,
    name = name,
    type = type,
    channel = channel,
    last4 = last4,
    enabled = enabled,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = 1_000L,
    updatedAt = 1_000L,
)

private fun initialAccounts(): List<AccountEntity> = listOf(
    accountEntity("acc-cash", "现金", AccountType.CASH, sortOrder = 0, isDefault = true),
    accountEntity(
        id = "acc-bank",
        name = "招商银行储蓄卡",
        type = AccountType.DEBIT_CARD,
        channel = PaymentChannel.BANK,
        last4 = "8823",
        sortOrder = 1,
    ),
    accountEntity(
        id = "acc-alipay",
        name = "支付宝",
        type = AccountType.THIRD_PARTY,
        channel = PaymentChannel.ALIPAY,
        sortOrder = 2,
    ),
)

class SaveAccountUseCaseTest {

    private fun store() = InMemoryAccountRepository(initialAccounts())

    private fun useCase(store: InMemoryAccountRepository) =
        SaveAccountUseCase(accounts = store, clock = ACCOUNT_TEST_CLOCK)

    // ---------------------------------------------------------------- V1 名称

    @Test
    fun V1_名称为空或全空白被拒且不落库() = runBlocking {
        listOf("", "   ", "\t").forEach { blank ->
            val store = store()
            val before = store.rows.value.size

            val result = useCase(store)(
                AccountInput(id = null, name = blank, type = AccountType.CASH),
            )

            assertEquals(SaveAccountResult.Rejected(AccountSaveError.NAME_EMPTY), result)
            assertEquals(before, store.rows.value.size)
        }
    }

    @Test
    fun V1_新增重名被拒且不落库() = runBlocking {
        val store = store()
        val before = store.rows.value.size

        val result = useCase(store)(
            AccountInput(id = null, name = "现金", type = AccountType.CASH),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.DUPLICATE_NAME), result)
        assertEquals(before, store.rows.value.size)
    }

    @Test
    fun V1_更新改名撞其它账户被拒且原名保留() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(id = "acc-bank", name = "现金", type = AccountType.DEBIT_CARD, channel = PaymentChannel.BANK, last4 = "8823"),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.DUPLICATE_NAME), result)
        assertEquals("招商银行储蓄卡", store.find("acc-bank")!!.name)
    }

    @Test
    fun V1_更新保持自身名称不算重名() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(
                id = "acc-bank",
                name = "招商银行储蓄卡",
                type = AccountType.DEBIT_CARD,
                channel = PaymentChannel.BANK,
                last4 = "8823",
            ),
        )

        assertTrue(result is SaveAccountResult.Success)
    }

    @Test
    fun V8_名称上限20字_超长被拒() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(id = null, name = "字".repeat(21), type = AccountType.CASH),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.NAME_TOO_LONG), result)
        assertEquals(initialAccounts().size, store.rows.value.size)
    }

    // ---------------------------------------------------------------- V2 尾号

    @Test
    fun V2_现金与第三方支付不得填写尾号() = runBlocking {
        listOf(AccountType.CASH, AccountType.THIRD_PARTY).forEach { type ->
            val store = store()

            val result = useCase(store)(
                AccountInput(id = null, name = "临时", type = type, last4 = "1234"),
            )

            assertEquals(SaveAccountResult.Rejected(AccountSaveError.LAST4_NOT_ALLOWED), result)
        }
    }

    @Test
    fun V2_银行卡尾号必须是4位数字() = runBlocking {
        listOf("123", "12345", "12a4", "四位数").forEach { invalid ->
            val store = store()

            val result = useCase(store)(
                AccountInput(
                    id = null,
                    name = "测试卡",
                    type = AccountType.DEBIT_CARD,
                    last4 = invalid,
                ),
            )

            assertEquals(SaveAccountResult.Rejected(AccountSaveError.INVALID_LAST4), result)
        }
    }

    @Test
    fun V2_合法尾号保存且空白视为未填() = runBlocking {
        val store = store()

        val ok = useCase(store)(
            AccountInput(
                id = null,
                name = "建设银行储蓄卡",
                type = AccountType.DEBIT_CARD,
                last4 = " 0042 ",
            ),
        ) as SaveAccountResult.Success
        assertEquals("0042", ok.account.last4)

        val blank = useCase(store)(
            AccountInput(
                id = null,
                name = "工商银行储蓄卡",
                type = AccountType.DEBIT_CARD,
                last4 = "   ",
            ),
        ) as SaveAccountResult.Success
        assertNull(blank.account.last4)
    }

    // ---------------------------------------------------------------- V3 渠道

    @Test
    fun V3_渠道可为空也可为合法枚举() = runBlocking {
        val store = store()

        val noChannel = useCase(store)(
            AccountInput(id = null, name = "无名渠道", type = AccountType.DEBIT_CARD),
        ) as SaveAccountResult.Success
        assertNull(noChannel.account.channel)

        val wechat = useCase(store)(
            AccountInput(
                id = null,
                name = "微信零钱",
                type = AccountType.THIRD_PARTY,
                channel = PaymentChannel.WECHAT,
            ),
        ) as SaveAccountResult.Success
        assertEquals(PaymentChannel.WECHAT, wechat.account.channel)
    }

    // ---------------------------------------------------------------- V4 无删除

    @Test
    fun V4_仓储与用例均不提供删除能力() {
        val repoMethods = AccountRepository::class.java.methods.map { it.name.lowercase() }
        assertTrue(
            "06 §4.13：账户不提供物理删除",
            repoMethods.none { it.contains("delete") || it.contains("remove") },
        )
        val useCaseMethods = SaveAccountUseCase::class.java.methods.map { it.name.lowercase() }
        assertTrue(useCaseMethods.none { it.contains("delete") || it.contains("remove") })
    }

    // ---------------------------------------------------------------- V5 / D-1

    @Test
    fun V5_不能禁用最后一个启用账户() = runBlocking {
        val store = InMemoryAccountRepository(
            listOf(accountEntity("acc-cash", "现金", isDefault = true)),
        )
        val cash = store.find("acc-cash")!!

        val result = useCase(store)(
            AccountInput(
                id = cash.id,
                name = cash.name,
                type = cash.type,
                enabled = false,
            ),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.LAST_ENABLED_ACCOUNT), result)
        assertTrue("失败不得落库", store.find("acc-cash")!!.enabled)
    }

    @Test
    fun V5_还有其它启用账户时可以禁用() = runBlocking {
        val store = store()
        val cash = store.find("acc-cash")!!

        val result = useCase(store)(
            AccountInput(id = cash.id, name = cash.name, type = cash.type, enabled = false),
        )

        assertTrue(result is SaveAccountResult.Success)
        assertFalse(store.find("acc-cash")!!.enabled)
        assertEquals("D-2：禁用仍保留 isDefault", true, store.find("acc-cash")!!.isDefault)
    }

    @Test
    fun A7_禁用默认账户后保留isDefault但不再可用作默认() = runBlocking {
        val store = store()
        val cash = store.find("acc-cash")!!

        useCase(store)(
            AccountInput(id = cash.id, name = cash.name, type = cash.type, enabled = false),
        )

        val disabledDefault = store.find("acc-cash")!!
        assertTrue("isDefault 必须保留（D-2）", disabledDefault.isDefault)
        assertTrue("但启用列表里不应再出现", store.rows.value.none { it.id == "acc-cash" && it.enabled })
        assertNull("usableDefault 必须为 null", store.usableDefault())
        assertEquals("默认数量仍为 1（只是不可用）", 1, store.countDefaults())
    }

    // ---------------------------------------------------------------- V6 默认账户

    @Test
    fun V6_新增即默认时先清旧默认() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(
                id = null,
                name = "微信零钱",
                type = AccountType.THIRD_PARTY,
                channel = PaymentChannel.WECHAT,
                isDefault = true,
            ),
        ) as SaveAccountResult.Success

        assertTrue(result.account.isDefault)
        assertEquals("全库必须恰好一个默认", 1, store.countDefaults())
        assertFalse("旧默认必须被清除", store.find("acc-cash")!!.isDefault)
        assertEquals(result.account.id, store.usableDefault()?.id)
    }

    @Test
    fun V6_新增不设默认时保持原默认() = runBlocking {
        val store = store()

        useCase(store)(
            AccountInput(id = null, name = "微信零钱", type = AccountType.THIRD_PARTY),
        )

        assertEquals(1, store.countDefaults())
        assertEquals("acc-cash", store.usableDefault()?.id)
    }

    @Test
    fun 更新默认账户不会意外取消其默认() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(
                id = "acc-cash",
                name = "现金（改名）",
                type = AccountType.CASH,
                isDefault = false,
            ),
        ) as SaveAccountResult.Success

        assertTrue("编辑默认账户时必须保留默认", result.account.isDefault)
        assertEquals("acc-cash", store.usableDefault()?.id)
    }

    @Test
    fun 更新非默认账户并置为默认时切换正确() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(
                id = "acc-alipay",
                name = "支付宝",
                type = AccountType.THIRD_PARTY,
                channel = PaymentChannel.ALIPAY,
                isDefault = true,
            ),
        ) as SaveAccountResult.Success

        assertTrue(result.account.isDefault)
        assertEquals(1, store.countDefaults())
        assertFalse(store.find("acc-cash")!!.isDefault)
    }

    // ---------------------------------------------------------------- V7 类型

    @Test
    fun V7_更新时不得改type() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(
                id = "acc-cash",
                name = "现金",
                type = AccountType.DEBIT_CARD,
            ),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.TYPE_CHANGE_NOT_ALLOWED), result)
        assertEquals(AccountType.CASH, store.find("acc-cash")!!.type)
    }

    // ---------------------------------------------------------------- 其它

    @Test
    fun 新增追加到末尾_更新保留createdAt并刷新updatedAt() = runBlocking {
        val store = store()

        val created = useCase(store)(
            AccountInput(id = null, name = "微信零钱", type = AccountType.THIRD_PARTY),
        ) as SaveAccountResult.Success
        assertEquals("max+1", 3, created.account.sortOrder)
        assertEquals(ACCOUNT_NOW, created.account.createdAt)
        assertEquals(ACCOUNT_NOW, created.account.updatedAt)

        val updated = useCase(store)(
            AccountInput(
                id = created.account.id,
                name = "微信零钱（改名）",
                type = AccountType.THIRD_PARTY,
                sortOrder = 9,
            ),
        ) as SaveAccountResult.Success
        assertFalse(updated.created)
        assertEquals("createdAt 不得被更新改写", ACCOUNT_NOW, updated.account.createdAt)
        assertEquals(ACCOUNT_NOW, updated.account.updatedAt)
        assertEquals(9, updated.account.sortOrder)
    }

    @Test
    fun 名称首尾空白被去除() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(id = null, name = "  微信零钱  ", type = AccountType.THIRD_PARTY),
        ) as SaveAccountResult.Success

        assertEquals("微信零钱", result.account.name)
    }

    @Test
    fun 更新目标不存在时返回NOT_FOUND() = runBlocking {
        val store = store()

        val result = useCase(store)(
            AccountInput(id = "missing", name = "现金", type = AccountType.CASH),
        )

        assertEquals(SaveAccountResult.Rejected(AccountSaveError.NOT_FOUND), result)
    }
}
