/**
 * 软删除 / 撤销用例测试（`M2b` §4.4；对应 A9/A10/A12）。
 *
 * 关键断言：
 * - 删除只写 `deletedAt`、**不改 `state`**（`docs/03` §6）；
 * - 撤销清空 `deletedAt`；
 * - 重复删除/撤销幂等；
 * - A12：删除路径**不接触** `FieldEditRepository`（本用例的依赖里根本没有它，属结构性保证）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class DeleteFakeTransactionStore : TransactionRepository {
    val rows = MutableStateFlow<List<TransactionEntity>>(emptyList())

    override suspend fun insert(transaction: TransactionEntity) {
        rows.value = rows.value.filterNot { it.id == transaction.id } + transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        rows.value.filter { it.state == state && it.deletedAt == null }

    override suspend fun listVisible(): List<TransactionEntity> = rows.value.filter { it.deletedAt == null }

    override fun observeVisible(): Flow<List<TransactionEntity>> = rows

    override suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity> =
        rows.value.filter { it.transactionTime in startInclusive until endExclusive }

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

class DeleteTransactionUseCaseTest {

    private companion object {
        const val TX_ID = "tx-1"
        const val ZONE = "Asia/Shanghai"
        const val NOW = 1_800_000_000_000L
        val CLOCK: Clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(ZONE))
    }

    private val transaction = TransactionEntity(
        id = TX_ID,
        type = TransactionType.EXPENSE,
        amountMinor = 1_200L,
        currency = Currency.CNY,
        merchant = "便利店",
        categoryId = "cat-food",
        accountId = "acc-cash",
        transactionTime = NOW - 3_600_000L,
        transactionTimeZone = ZONE,
        note = null,
        state = TransactionState.CONFIRMED,
        source = CaptureSource.MANUAL,
        confirmedByUser = true,
        createdAt = NOW - 3_600_000L,
        updatedAt = NOW - 3_600_000L,
        deletedAt = null,
    )

    private fun store(): DeleteFakeTransactionStore = DeleteFakeTransactionStore().also {
        runBlocking { it.insert(transaction) }
    }

    private fun useCase(store: DeleteFakeTransactionStore) =
        DeleteTransactionUseCase(transactions = store, clock = CLOCK)

    @Test
    fun A9_软删除只写deletedAt且state不变() = runBlocking {
        val store = store()

        val result = useCase(store).delete(TX_ID)

        assertEquals(DeleteTransactionResult.Success, result)
        val updated = store.find(TX_ID)!!
        assertEquals(NOW, updated.deletedAt)
        assertEquals("updatedAt 必须刷新", NOW, updated.updatedAt)
        assertEquals("删除不改变 state（可见性由 deletedAt 控制）", TransactionState.CONFIRMED, updated.state)
        assertEquals("其他字段不得被删除影响", "便利店", updated.merchant)
        assertEquals(1_200L, updated.amountMinor)
    }

    @Test
    fun A10_撤销删除清空deletedAt() = runBlocking {
        val store = store()
        useCase(store).delete(TX_ID)

        val result = useCase(store).restore(TX_ID)

        assertEquals(DeleteTransactionResult.Success, result)
        val restored = store.find(TX_ID)!!
        assertNull(restored.deletedAt)
        assertEquals(NOW, restored.updatedAt)
    }

    @Test
    fun 重复删除与撤销均幂等() = runBlocking {
        val store = store()
        val subject = useCase(store)

        assertEquals(DeleteTransactionResult.Success, subject.delete(TX_ID))
        assertEquals(DeleteTransactionResult.Success, subject.delete(TX_ID))
        assertNotNull(store.find(TX_ID)!!.deletedAt)

        assertEquals(DeleteTransactionResult.Success, subject.restore(TX_ID))
        assertEquals(DeleteTransactionResult.Success, subject.restore(TX_ID))
        assertNull(store.find(TX_ID)!!.deletedAt)
    }

    @Test
    fun 交易不存在时返回NotFound() = runBlocking {
        val store = DeleteFakeTransactionStore()
        val subject = useCase(store)

        assertEquals(DeleteTransactionResult.NotFound, subject.delete("missing"))
        assertEquals(DeleteTransactionResult.NotFound, subject.restore("missing"))
    }

    @Test
    fun A12_删除用例不依赖FieldEditRepository从而不可能写保护记录() {
        // 结构性保证：DeleteTransactionUseCase 的构造参数只有 TransactionRepository + Clock。
        // 一旦有人给删除路径接入 FieldEditRepository，本断言（反射构造参数名）会失败。
        val parameterTypeNames = DeleteTransactionUseCase::class.java.declaredConstructors
            .flatMap { it.parameterTypes.toList() }
            .map { it.simpleName }
        assertEquals(
            "删除路径不得触碰 FieldEditRepository",
            listOf("TransactionRepository", "Clock"),
            parameterTypeNames,
        )
    }
}
