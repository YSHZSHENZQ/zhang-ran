/**
 * 待确认处置：确认 / 改完再记 / 忽略，以及自动流程不得覆盖受保护字段。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.RestoreIgnoredCaptureUseCase
import com.zhangran.app.domain.usecase.RestoreIgnoredResult
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDispositionTest {

    @Test
    fun 普通原因可以确认改完再记和忽略() {
        val flags = pendingActionFlags(listOf(PendingReason.CATEGORY_UNCERTAIN, PendingReason.LOW_CONFIDENCE))
        assertTrue(flags.canConfirm)
        assertTrue(flags.canEdit)
        assertTrue(flags.canIgnore)
    }

    @Test
    fun 疑似重复只有忽略() {
        val flags = pendingActionFlags(
            listOf(PendingReason.CATEGORY_UNCERTAIN, PendingReason.POSSIBLE_DUPLICATE),
        )
        assertFalse(flags.canConfirm)
        assertFalse(flags.canEdit)
        assertTrue(flags.canIgnore)
    }

    @Test
    fun 确认不写FieldEdit且标记用户确认() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))

        val result = harness.confirm.invoke(TX)

        assertEquals(ConfirmPendingResult.Confirmed, result)
        val saved = harness.transactions.getById(TX)!!
        assertEquals(TransactionState.CONFIRMED, saved.state)
        assertTrue(saved.confirmedByUser)
        assertTrue(harness.edits.records.isEmpty())
        assertEquals("星巴克咖啡", saved.merchant)
        assertEquals(3_850L, saved.amountMinor)
    }

    @Test
    fun 疑似重复不能直接确认() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "POSSIBLE_DUPLICATE,CATEGORY_UNCERTAIN"))

        val result = harness.confirm.invoke(TX)

        assertTrue(result is ConfirmPendingResult.Rejected)
        assertEquals(TransactionState.PENDING, harness.transactions.getById(TX)!!.state)
        assertTrue(harness.edits.records.isEmpty())
    }

    @Test
    fun 只改分类和备注时FieldEdit恰好两条() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))

        val result = harness.save.invoke(
            TX,
            draft(categoryId = "cat-food", note = "改完再记备注"),
        )

        val savedResult = result as SavePendingResult.Saved
        assertEquals(listOf("categoryId", "note"), savedResult.fieldNames)
        val saved = harness.transactions.getById(TX)!!
        assertEquals(TransactionState.CONFIRMED, saved.state)
        assertTrue(saved.confirmedByUser)
        assertEquals("cat-food", saved.categoryId)
        assertEquals("改完再记备注", saved.note)
        assertEquals(setOf("categoryId", "note"), harness.edits.records.map { it.fieldName }.toSet())
    }

    @Test
    fun 未改字段直接保存不产生FieldEdit() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))

        val result = harness.save.invoke(TX, draft())

        assertEquals(emptyList<String>(), (result as SavePendingResult.Saved).fieldNames)
        assertTrue(harness.transactions.getById(TX)!!.confirmedByUser)
        assertTrue(harness.edits.records.isEmpty())
    }

    @Test
    fun 金额为空为零或非法以及时间为空时保持待确认() = runBlocking {
        val cases = listOf(
            draft(amountText = "") to "金额不能为空",
            draft(amountText = "0") to "金额不能为 0",
            draft(amountText = "0.00") to "金额不能为 0",
            draft(amountText = "abc") to "金额不合法",
            draft(timeEpochMillis = null) to "时间不能为空",
            draft(timeEpochMillis = 0L) to "时间不能为空",
        )
        cases.forEach { (book, expected) ->
            val harness = Harness()
            harness.transactions.insert(pending())
            harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))
            val result = harness.save.invoke(TX, book) as SavePendingResult.Invalid
            val messages = listOfNotNull(
                result.errors.amount,
                result.errors.time,
                result.errors.type,
                result.errors.category,
                result.errors.note,
            )
            assertTrue("$expected 不在 $messages", expected in messages)
            assertEquals(TransactionState.PENDING, harness.transactions.getById(TX)!!.state)
            assertFalse(harness.transactions.getById(TX)!!.confirmedByUser)
            assertTrue(harness.edits.records.isEmpty())
        }
    }

    @Test
    fun 忽略后状态为IGNORED且日志仍在() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))

        assertEquals(IgnorePendingResult.Ignored, harness.ignore.invoke(TX))

        val saved = harness.transactions.getById(TX)!!
        assertEquals(TransactionState.IGNORED, saved.state)
        assertFalse(saved.confirmedByUser)
        assertEquals(1, harness.logs.logs.size)
        assertEquals(TX, harness.logs.logs.single().transactionId)
        assertTrue(harness.edits.records.isEmpty())
    }

    @Test
    fun 忽略后捕获日志记为IGNORED() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))

        assertEquals(IgnorePendingResult.Ignored, harness.ignore.invoke(TX))
        assertEquals(CaptureLogStatus.IGNORED, harness.logs.logs.single().status)
    }

    @Test
    fun 从日志恢复忽略记录回到待确认且不改金额() = runBlocking {
        val harness = Harness()
        harness.transactions.insert(pending())
        harness.logs.put(log(reason = "CATEGORY_UNCERTAIN"))
        harness.ignore.invoke(TX)

        val restore = RestoreIgnoredCaptureUseCase(
            transactions = harness.transactions,
            logs = harness.logs,
            clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(ZONE)),
        )
        assertEquals(RestoreIgnoredResult.Restored, restore.invoke("log-1"))

        val saved = harness.transactions.getById(TX)!!
        assertEquals(TransactionState.PENDING, saved.state)
        assertEquals(3_850L, saved.amountMinor)
        assertFalse(saved.confirmedByUser)
        assertEquals(CaptureLogStatus.PENDING, harness.logs.logs.single().status)
    }

    @Test
    fun 自动合并不覆盖受保护字段且不回退确认标记() {
        val current = pending().copy(
            state = TransactionState.CONFIRMED,
            confirmedByUser = true,
            categoryId = "cat-food",
            note = "改完再记备注",
        )
        val merged = AutomaticFieldMerge.apply(
            current = current,
            incoming = AutomaticFieldUpdate(
                merchant = "自动商户",
                amountMinor = 1L,
                categoryId = "cat-other",
                note = "自动备注",
            ),
            protectedFieldNames = setOf("categoryId", "note"),
        )
        assertEquals("自动商户", merged.merchant)
        assertEquals(1L, merged.amountMinor)
        assertEquals("cat-food", merged.categoryId)
        assertEquals("改完再记备注", merged.note)
        assertEquals(TransactionState.CONFIRMED, merged.state)
        assertTrue(merged.confirmedByUser)
    }

    @Test
    fun 自动合并不改已忽略记录() {
        val current = pending().copy(state = TransactionState.IGNORED, merchant = "星巴克咖啡")
        val merged = AutomaticFieldMerge.apply(
            current = current,
            incoming = AutomaticFieldUpdate(merchant = "自动商户", note = "自动备注"),
            protectedFieldNames = emptySet(),
        )
        assertEquals(current, merged)
    }

    @Test
    fun 空的自动字段不得清空受保护分类() {
        val current = pending().copy(
            state = TransactionState.CONFIRMED,
            confirmedByUser = true,
            categoryId = "cat-food",
            note = "改完再记备注",
        )
        val merged = AutomaticFieldMerge.apply(
            current = current,
            incoming = AutomaticFieldUpdate(merchant = "星巴克咖啡", amountMinor = 3_850L),
            protectedFieldNames = setOf("categoryId", "note"),
        )
        assertEquals("cat-food", merged.categoryId)
        assertEquals("改完再记备注", merged.note)
        assertTrue(merged.confirmedByUser)
        assertEquals(TransactionState.CONFIRMED, merged.state)
    }

    private class Harness {
        val transactions = MemoryTransactions()
        val categories = MemoryCategories(
            listOf(
                CategoryEntity(
                    id = "cat-food",
                    name = "餐饮",
                    type = CategoryType.EXPENSE,
                    icon = "food",
                    sortOrder = 0,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        val accounts = MemoryAccounts(
            listOf(
                AccountEntity(
                    id = "acc-cash",
                    name = "现金",
                    type = AccountType.CASH,
                    createdAt = NOW,
                    updatedAt = NOW,
                ),
            ),
        )
        val edits = MemoryFieldEdits()
        val logs = MemoryLogs()
        private val clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of(ZONE))
        val confirm = ConfirmPendingUseCase(transactions, logs, clock)
        val ignore = IgnorePendingUseCase(transactions, logs, clock)
        val save = SavePendingAndBookUseCase(transactions, categories, accounts, edits, logs, clock)
    }

    private class MemoryTransactions : TransactionRepository {
        private val rows = mutableListOf<TransactionEntity>()

        override suspend fun insert(transaction: TransactionEntity) {
            rows.removeAll { it.id == transaction.id }
            rows += transaction
        }

        override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
            rows.filter { it.state == state && it.deletedAt == null }

        override suspend fun listVisible(): List<TransactionEntity> = rows.filter { it.deletedAt == null }

        override fun observeVisible(): Flow<List<TransactionEntity>> =
            MutableStateFlow(rows.filter { it.deletedAt == null })

        override suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity> =
            rows.filter {
                it.state == TransactionState.CONFIRMED &&
                    it.deletedAt == null &&
                    it.transactionTime in startInclusive until endExclusive
            }

        override suspend fun countByState(state: TransactionState): Int =
            rows.count { it.state == state && it.deletedAt == null }

        override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
            rows.firstOrNull { it.id == id && (includeDeleted || it.deletedAt == null) }

        override suspend fun update(transaction: TransactionEntity) {
            rows.removeAll { it.id == transaction.id }
            rows += transaction
        }

        override suspend fun softDelete(id: String, deletedAt: Long) = Unit

        override suspend fun restore(id: String, updatedAt: Long) = Unit

        override suspend fun search(criteria: com.zhangran.app.domain.model.SearchCriteria) = emptyList<TransactionEntity>()

        override suspend fun countSearch(criteria: com.zhangran.app.domain.model.SearchCriteria) = 0
    }

    private class MemoryCategories(private val items: List<CategoryEntity>) : CategoryRepository {
        override suspend fun all(): List<CategoryEntity> = items
        override fun observeAll(): Flow<List<CategoryEntity>> = MutableStateFlow(items)
        override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
            items.filter { it.type == type && it.enabled }
        override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
            items.firstOrNull { it.type == type && it.name == name }
        override suspend fun insert(category: CategoryEntity) = Unit
        override suspend fun update(category: CategoryEntity) = Unit
    }

    private class MemoryAccounts(private val items: List<AccountEntity>) : AccountRepository {
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

    private class MemoryFieldEdits : FieldEditRepository {
        val records = mutableListOf<FieldEditEntity>()
        override suspend fun record(fieldEdit: FieldEditEntity) {
            records += fieldEdit
        }
        override suspend fun listByTransaction(transactionId: String): List<FieldEditEntity> =
            records.filter { it.transactionId == transactionId }
        override suspend fun isProtected(transactionId: String, fieldName: String): Boolean =
            records.any { it.transactionId == transactionId && it.fieldName == fieldName }
    }

    private class MemoryLogs : CaptureLogRepository {
        val logs = mutableListOf<CaptureLogEntity>()
        fun put(log: CaptureLogEntity) {
            logs += log
        }
        override suspend fun insert(log: CaptureLogEntity, evidence: CaptureEvidenceEntity?) {
            logs += log
        }
        override suspend fun findByCaptureId(captureId: String): CaptureLogEntity? =
            logs.firstOrNull { it.captureId == captureId }
        override suspend fun findByTransactionId(transactionId: String): CaptureLogEntity? =
            logs.filter { it.transactionId == transactionId }.maxByOrNull { it.capturedAt }
        override suspend fun listAll(): List<CaptureLogEntity> = logs.toList()
        override suspend fun listEvidence(captureId: String): List<CaptureEvidenceEntity> = emptyList()

        override suspend fun update(log: CaptureLogEntity) {
            val index = logs.indexOfFirst { it.id == log.id }
            if (index >= 0) logs[index] = log
        }

        override suspend fun findById(id: String): CaptureLogEntity? = logs.firstOrNull { it.id == id }

        override suspend fun clearLogsAndEvidence() {
            logs.clear()
        }

        override suspend fun countEvidence(): Int = 0
    }

    private companion object {
        const val TX = "tx-pending"
        const val ZONE = "Asia/Shanghai"
        const val NOW = 1_762_888_000_000L

        fun pending() = TransactionEntity(
            id = TX,
            type = TransactionType.EXPENSE,
            amountMinor = 3_850L,
            currency = Currency.CNY,
            merchant = "星巴克咖啡",
            categoryId = null,
            accountId = null,
            transactionTime = NOW,
            transactionTimeZone = ZONE,
            note = null,
            state = TransactionState.PENDING,
            source = CaptureSource.ACCESSIBILITY,
            confirmedByUser = false,
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun log(reason: String) = CaptureLogEntity(
            id = "log-1",
            captureId = "cap-1",
            capturedAt = NOW,
            captureSource = CaptureSource.ACCESSIBILITY,
            packageName = "com.tencent.mm",
            channel = "wechat",
            parserRuleId = null,
            engineVersion = "m6-2",
            payloadVersion = 1,
            status = CaptureLogStatus.PENDING,
            reason = reason,
            rawCaptureRef = """{"fixtureId":"m6-2-wechat-pay-success:F-A"}""",
            evidenceId = "evi-1",
            transactionId = TX,
        )

        fun draft(
            amountText: String = "38.50",
            categoryId: String? = null,
            note: String? = null,
            timeEpochMillis: Long? = NOW,
        ) = PendingBookDraft(
            merchant = "星巴克咖啡",
            amountText = amountText,
            type = TransactionType.EXPENSE,
            categoryId = categoryId,
            accountId = null,
            transactionTimeEpochMillis = timeEpochMillis,
            timeZoneId = ZONE,
            note = note,
        )
    }
}
