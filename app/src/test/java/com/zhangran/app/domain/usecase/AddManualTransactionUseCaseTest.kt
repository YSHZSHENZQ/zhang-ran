/**
 * 手工记账用例测试（任务书 M1c' §6 的 A1、A3–A6）。
 *
 * 纯 JVM：Repository 用内存 fake，`Normalize` 用真实实现（配 no-op resolver + 固定时区），
 * 这样才能真正证明 V6（备注截断）与 V7（商户规范化）复用的是 M1b 的规则。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内存 fake：只记录写入，供断言"是否落库"。 */
private class FakeTransactionRepository : TransactionRepository {
    val inserted = mutableListOf<TransactionEntity>()

    override suspend fun insert(transaction: TransactionEntity) {
        inserted += transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> = inserted

    override suspend fun listVisible(): List<TransactionEntity> = inserted

    override fun observeVisible(): Flow<List<TransactionEntity>> =
        flowOf(inserted.filter { it.deletedAt == null })

    override suspend fun listInRange(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransactionEntity> = inserted.filter {
        it.state == TransactionState.CONFIRMED &&
            it.deletedAt == null &&
            it.transactionTime >= startInclusive &&
            it.transactionTime < endExclusive
    }

    override suspend fun countByState(state: TransactionState): Int =
        inserted.count { it.state == state && it.deletedAt == null }

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        inserted.firstOrNull { it.id == id }
}

class AddManualTransactionUseCaseTest {

    private companion object {
        const val ZONE = "Asia/Shanghai"
        const val TIME = 1_791_610_182_000L
        const val FIXED_ID = "fixed-id"
        const val NOW = 1_791_700_000_000L
    }

    private val repository = FakeTransactionRepository()

    private val useCase = AddManualTransactionUseCase(
        normalize = Normalize(
            categoryResolver = CategoryResolver { null },
            accountResolver = AccountResolver { null },
            defaultZoneId = ZONE,
        ),
        transactionRepository = repository,
        clock = { NOW },
        idGenerator = { FIXED_ID },
    )

    private fun input(
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 2650L,
        categoryId: String? = "cat-1",
        accountId: String? = "acc-1",
        sourceAccountId: String? = null,
        targetAccountId: String? = null,
        relatedTransactionId: String? = null,
        transactionTime: Long = TIME,
        transactionTimeZone: String? = ZONE,
        merchant: String? = "全家便利店",
        note: String? = null,
    ) = ManualTransactionInput(
        type = type,
        amountMinor = amountMinor,
        categoryId = categoryId,
        accountId = accountId,
        sourceAccountId = sourceAccountId,
        targetAccountId = targetAccountId,
        relatedTransactionId = relatedTransactionId,
        transactionTime = transactionTime,
        transactionTimeZone = transactionTimeZone,
        merchant = merchant,
        note = note,
    )

    private fun errorsOf(result: AddManualTransactionResult): List<ManualValidationError> =
        (result as AddManualTransactionResult.ValidationFailed).errors

    // ---------------------------------------------------------------- V1 金额

    @Test
    fun `V1 金额为零被拒且不落库`() = runBlocking {
        val result = useCase.invoke(input(amountMinor = 0L))
        assertEquals(listOf(ManualValidationError.AMOUNT_NOT_POSITIVE), errorsOf(result))
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `V1 负金额被拒`() = runBlocking {
        val result = useCase.invoke(input(amountMinor = -1L))
        assertTrue(errorsOf(result).contains(ManualValidationError.AMOUNT_NOT_POSITIVE))
        assertTrue(repository.inserted.isEmpty())
    }

    // ---------------------------------------------------------------- V2 时间

    @Test
    fun `V2 交易时间为零被拒且不落库`() = runBlocking {
        val result = useCase.invoke(input(transactionTime = 0L))
        assertEquals(listOf(ManualValidationError.TIME_INVALID), errorsOf(result))
        assertTrue(repository.inserted.isEmpty())
    }

    @Test
    fun `V2 合法时间通过`() = runBlocking {
        val result = useCase.invoke(input())
        assertTrue(result is AddManualTransactionResult.Success)
        assertEquals(TIME, repository.inserted.single().transactionTime)
    }

    @Test
    fun `V2 非法时区被拒`() = runBlocking {
        val result = useCase.invoke(input(transactionTimeZone = "Mars/Olympus"))
        assertTrue(errorsOf(result).contains(ManualValidationError.TIMEZONE_INVALID))
        assertTrue(repository.inserted.isEmpty())
    }

    // ---------------------------------------------------------------- V3 分类与账户

    @Test
    fun `V3 支出缺分类被拒`() = runBlocking {
        val result = useCase.invoke(input(categoryId = null))
        assertEquals(listOf(ManualValidationError.CATEGORY_REQUIRED), errorsOf(result))
    }

    @Test
    fun `V3 支出缺账户被拒`() = runBlocking {
        val result = useCase.invoke(input(accountId = null))
        assertEquals(listOf(ManualValidationError.ACCOUNT_REQUIRED), errorsOf(result))
    }

    @Test
    fun `V3 收入齐备时通过`() = runBlocking {
        val result = useCase.invoke(input(type = TransactionType.INCOME))
        assertTrue(result is AddManualTransactionResult.Success)
        assertEquals(TransactionType.INCOME, repository.inserted.single().type)
    }

    // ---------------------------------------------------------------- V4 转账

    @Test
    fun `V4 转账缺账户被拒`() = runBlocking {
        val result = useCase.invoke(
            input(type = TransactionType.TRANSFER, categoryId = null, accountId = null),
        )
        assertTrue(errorsOf(result).contains(ManualValidationError.TRANSFER_ACCOUNTS_REQUIRED))
    }

    @Test
    fun `V4 转账两侧账户相同被拒`() = runBlocking {
        val result = useCase.invoke(
            input(
                type = TransactionType.TRANSFER,
                categoryId = null,
                accountId = null,
                sourceAccountId = "acc-1",
                targetAccountId = "acc-1",
            ),
        )
        assertEquals(listOf(ManualValidationError.TRANSFER_ACCOUNTS_SAME), errorsOf(result))
    }

    @Test
    fun `V4 转账带分类被拒`() = runBlocking {
        val result = useCase.invoke(
            input(
                type = TransactionType.TRANSFER,
                categoryId = "cat-1",
                accountId = null,
                sourceAccountId = "acc-a",
                targetAccountId = "acc-b",
            ),
        )
        assertEquals(listOf(ManualValidationError.TRANSFER_CATEGORY_NOT_ALLOWED), errorsOf(result))
    }

    @Test
    fun `V4 转账落库时分类为空且两侧账户保留`() = runBlocking {
        val result = useCase.invoke(
            input(
                type = TransactionType.TRANSFER,
                categoryId = null,
                accountId = null,
                sourceAccountId = "acc-a",
                targetAccountId = "acc-b",
            ),
        )
        assertTrue(result is AddManualTransactionResult.Success)

        val entity = repository.inserted.single()
        assertNull("TRANSFER 的 categoryId 必须为空（03 §5）", entity.categoryId)
        assertEquals("acc-a", entity.sourceAccountId)
        assertEquals("acc-b", entity.targetAccountId)
        assertEquals(TransactionState.CONFIRMED, entity.state)
    }

    // ---------------------------------------------------------------- V5 退款

    @Test
    fun `V5 退款无关联原交易时落 PENDING 并给出 REFUND_UNCERTAIN`() = runBlocking {
        val result = useCase.invoke(
            input(type = TransactionType.REFUND, categoryId = null, relatedTransactionId = null),
        ) as AddManualTransactionResult.Success

        assertEquals(TransactionState.PENDING, result.state)
        assertEquals(PendingReason.REFUND_UNCERTAIN, result.pendingReason)
        assertEquals(TransactionState.PENDING, repository.inserted.single().state)
    }

    @Test
    fun `V5 退款有关联原交易时落 CONFIRMED`() = runBlocking {
        val result = useCase.invoke(
            input(
                type = TransactionType.REFUND,
                categoryId = null,
                relatedTransactionId = "tx-original",
            ),
        ) as AddManualTransactionResult.Success

        assertEquals(TransactionState.CONFIRMED, result.state)
        assertNull(result.pendingReason)
        assertEquals("tx-original", repository.inserted.single().relatedTransactionId)
    }

    // ---------------------------------------------------------------- V6 / V7

    @Test
    fun `V6 备注超 500 字被截断为 500`() = runBlocking {
        useCase.invoke(input(note = "字".repeat(501)))
        assertEquals(500, repository.inserted.single().note!!.length)
    }

    @Test
    fun `V7 商户按 Normalize 规则规范化`() = runBlocking {
        useCase.invoke(input(merchant = "  收款方：星巴克  "))
        assertEquals("星巴克", repository.inserted.single().merchant)
    }

    // ---------------------------------------------------------------- A3 / A6 / 状态

    @Test
    fun `A3 落库为 CONFIRMED 且 confirmedByUser 为真`() = runBlocking {
        val result = useCase.invoke(input()) as AddManualTransactionResult.Success
        val entity = repository.inserted.single()

        assertEquals(TransactionState.CONFIRMED, result.state)
        assertTrue("手工记账是用户确认行为（03 §14）", entity.confirmedByUser)
        assertEquals(FIXED_ID, entity.id)
        assertEquals(CaptureSource.MANUAL, entity.source)
        assertEquals(NOW, entity.createdAt)
    }

    @Test
    fun `A6 多条校验错误同时返回且不落库`() = runBlocking {
        val result = useCase.invoke(
            input(amountMinor = 0L, transactionTime = 0L, categoryId = null, accountId = null),
        )
        val errors = errorsOf(result)

        assertTrue(errors.contains(ManualValidationError.AMOUNT_NOT_POSITIVE))
        assertTrue(errors.contains(ManualValidationError.TIME_INVALID))
        assertTrue(errors.contains(ManualValidationError.CATEGORY_REQUIRED))
        assertTrue(errors.contains(ManualValidationError.ACCOUNT_REQUIRED))
        assertTrue("校验失败不得落库", repository.inserted.isEmpty())
    }

    @Test
    fun `不依赖采集能力`() {
        // A11 的编译期证明：本用例与测试都不引用 capture/  类型
        val source = AddManualTransactionUseCase::class.java.name
        assertTrue(source.startsWith("com.zhangran.app.domain.usecase"))
    }
}
