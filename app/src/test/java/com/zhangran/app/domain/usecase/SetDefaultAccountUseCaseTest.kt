/**
 * `SetDefaultAccountUseCase` 测试（`M2d-2` §4.3 / 验收 A5）。
 *
 * 重点：默认唯一（`countDefaults() <= 1`）、禁用账户不得设为默认、幂等。
 * fake 仓储的 `setDefault` 用"单次写入先清后置"模拟 `AccountDao.setAsDefault` 的 `@Transaction`。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.domain.model.AccountType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetDefaultAccountUseCaseTest {

    private fun store() = InMemoryAccountRepository(
        listOf(
            accountEntity("acc-cash", "现金", AccountType.CASH, isDefault = true, sortOrder = 0),
            accountEntity(
                id = "acc-alipay",
                name = "支付宝",
                type = AccountType.THIRD_PARTY,
                sortOrder = 1,
            ),
            accountEntity(
                id = "acc-disabled",
                name = "停用卡",
                type = AccountType.DEBIT_CARD,
                enabled = false,
                sortOrder = 2,
            ),
        ),
    )

    private fun useCase(store: InMemoryAccountRepository) =
        SetDefaultAccountUseCase(accounts = store, clock = ACCOUNT_TEST_CLOCK)

    @Test
    fun A5_切换默认后全库恰好一个默认且目标为默认() = runBlocking {
        val store = store()

        val result = useCase(store)("acc-alipay")

        assertTrue(result is SetDefaultAccountResult.Success)
        assertEquals(1, store.countDefaults())
        assertTrue(store.find("acc-alipay")!!.isDefault)
        assertFalse(store.find("acc-cash")!!.isDefault)
        assertEquals(ACCOUNT_NOW, store.find("acc-alipay")!!.updatedAt)
    }

    @Test
    fun A5_连续切换多个账户默认数量始终不超过1() = runBlocking {
        val store = store()
        val useCase = useCase(store)
        useCase("acc-alipay")
        useCase("acc-cash")
        useCase("acc-alipay")

        assertEquals(1, store.countDefaults())
        assertEquals("acc-alipay", store.usableDefault()?.id)
    }

    @Test
    fun 对已是默认的账户重复设置是幂等的() = runBlocking {
        val store = store()

        val result = useCase(store)("acc-cash")

        assertTrue(result is SetDefaultAccountResult.Success)
        assertEquals(1, store.countDefaults())
        assertEquals(
            "幂等不应改写 updatedAt",
            1_000L,
            store.find("acc-cash")!!.updatedAt,
        )
    }

    @Test
    fun 禁用账户不得设为默认() = runBlocking {
        val store = store()

        val result = useCase(store)("acc-disabled")

        assertEquals(SetDefaultAccountResult.AccountDisabled, result)
        assertFalse(store.find("acc-disabled")!!.isDefault)
        assertEquals("原默认不变", "acc-cash", store.usableDefault()?.id)
    }

    @Test
    fun 账户不存在时返回NotFound() = runBlocking {
        val store = store()

        assertEquals(SetDefaultAccountResult.NotFound, useCase(store)("missing"))
        assertEquals(1, store.countDefaults())
    }
}
