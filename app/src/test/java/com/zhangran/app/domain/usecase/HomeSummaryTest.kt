/**
 * 首页统计口径测试（`M2e-1a` §4.2，对应验收 A4–A10 与 R9）。
 *
 * 口径来源是 `docs/02` §14，逐条断言：
 * - A4 当月消费 = `ΣEXPENSE − ΣREFUND`（**净额**）；
 * - A5 当月收入 = `ΣINCOME`，**REFUND 不加进收入**；
 * - A6 当月结余 = 收入 − 消费；
 * - A7 `TRANSFER` 不计入消费/收入/结余；
 * - A8 `PENDING` / `IGNORED` / `DELETED` / `DUPLICATE` / 软删除均不计入；
 * - A9 待确认数 = 全时段 `PENDING` 条数（排除软删除）；
 * - A10 跨月隔离：上月/下月记录不计入本月；
 * - R9 退款多于消费时，当月消费**按实际为负**，不截断到 0。
 *
 * 纯 JVM：Repository 用内存 fake，时间用 `Clock.fixed` 固定在 2024-03（不回退系统时间）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// ---------------------------------------------------------------------- 假仓库

/**
 * 内存交易仓库：`observeVisible` 用 `MutableStateFlow`（可在测试中推新数据），
 * `listInRange` / `countByState` 的过滤条件与 DAO 的 SQL **逐条对应**
 * （`state = CONFIRMED` / `deletedAt IS NULL` / 半开区间）。
 */
private class FakeSummaryTransactionRepository(
    initial: List<TransactionEntity> = emptyList(),
) : TransactionRepository {

    private val rows = MutableStateFlow(initial)

    override suspend fun insert(transaction: TransactionEntity) {
        rows.value += transaction
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        rows.value.filter { it.state == state && it.deletedAt == null }

    override suspend fun listVisible(): List<TransactionEntity> =
        rows.value.filter { it.deletedAt == null }

    override fun observeVisible(): Flow<List<TransactionEntity>> = rows

    override suspend fun listInRange(
        startInclusive: Long,
        endExclusive: Long,
    ): List<TransactionEntity> = rows.value.filter {
        it.state == TransactionState.CONFIRMED &&
            it.deletedAt == null &&
            it.transactionTime >= startInclusive &&
            it.transactionTime < endExclusive
    }

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
}

private class FakeSummaryCategoryRepository(
    initial: List<CategoryEntity> = emptyList(),
) : CategoryRepository {

    private val rows = MutableStateFlow(initial)

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

    /** 模拟 M2d 的分类重命名（只改名字，不改 id）。 */
    fun rename(id: String, newName: String) {
        rows.value = rows.value.map { if (it.id == id) it.copy(name = newName) else it }
    }
}

private class FakeSummaryAccountRepository(
    initial: List<AccountEntity> = emptyList(),
) : AccountRepository {

    private val rows = MutableStateFlow(initial)

    override suspend fun all(): List<AccountEntity> = rows.value

    override fun observeAll(): Flow<List<AccountEntity>> = rows

    override suspend fun defaultAccount(): AccountEntity? = rows.value.firstOrNull { it.isDefault }

    override suspend fun findByName(name: String): AccountEntity? =
        rows.value.firstOrNull { it.name == name }

    override suspend fun insert(account: AccountEntity) = Unit

    override suspend fun update(account: AccountEntity) = Unit

    override suspend fun setDefault(id: String, updatedAt: Long) = Unit

    override suspend fun enable(account: AccountEntity, updatedAt: Long) = Unit

    override suspend fun disable(account: AccountEntity, updatedAt: Long) = Unit

    override suspend fun usableDefault(): AccountEntity? =
        rows.value.firstOrNull { it.isDefault && it.enabled }

    override suspend fun enabledCount(): Int = rows.value.count { it.enabled }
}

// ---------------------------------------------------------------------- 统计口径

class HomeSummaryTest {

    private companion object {
        const val CN = "Asia/Shanghai"

        /** 固定"当前时刻"：2024-03-15 12:00 +08:00 → 当前月为 2024-03。 */
        val MARCH_CLOCK: Clock = Clock.fixed(
            Instant.parse("2024-03-15T04:00:00Z"),
            ZoneId.of(CN),
        )
    }

    private var deletedAtMarker: Long? = null

    /** 构造"Asia/Shanghai 本地时刻"对应的 UTC millis。 */
    private fun at(
        month: Int,
        day: Int,
        hour: Int = 12,
        minute: Int = 0,
        second: Int = 0,
        millis: Int = 0,
        year: Int = 2024,
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000)
        .atZone(ZoneId.of(CN))
        .toInstant()
        .toEpochMilli()

    private fun tx(
        id: String,
        type: TransactionType,
        amountMinor: Long,
        time: Long,
        state: TransactionState = TransactionState.CONFIRMED,
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = id,
        type = type,
        amountMinor = amountMinor,
        currency = Currency.CNY,
        merchant = "测试商户-$id",
        categoryId = "cat-1",
        accountId = "acc-1",
        sourceAccountId = if (type == TransactionType.TRANSFER) "acc-1" else null,
        targetAccountId = if (type == TransactionType.TRANSFER) "acc-2" else null,
        relatedTransactionId = if (type == TransactionType.REFUND) "tx-original" else null,
        transactionTime = time,
        transactionTimeZone = CN,
        note = null,
        state = state,
        source = CaptureSource.MANUAL,
        externalTransactionId = null,
        engineVersion = null,
        matchedRuleId = null,
        evidenceId = null,
        confirmedByUser = state == TransactionState.CONFIRMED,
        orphan = false,
        createdAt = time,
        updatedAt = time,
        deletedAt = deletedAt,
    )

    private fun summaryOf(vararg rows: TransactionEntity) = runBlocking {
        ObserveHomeSummaryUseCase(FakeSummaryTransactionRepository(rows.toList()))(MARCH_CLOCK).first()
    }

    // ---------------------------------------------------------------- A4

    @Test
    fun A4_当月消费是支出合计减去退款合计() {
        val summary = summaryOf(
            tx("exp-1", TransactionType.EXPENSE, 10_000L, at(3, 5)),
            tx("exp-2", TransactionType.EXPENSE, 2_650L, at(3, 6)),
            tx("refund-1", TransactionType.REFUND, 1_000L, at(3, 7)),
        )
        // 只取 EXPENSE 合计会得到 12_650（未冲减退款）；净额必须是 11_650
        assertEquals(11_650L, summary.monthExpenseMinor)
    }

    // ---------------------------------------------------------------- A5

    @Test
    fun A5_当月收入只算收入且退款不进收入() {
        val summary = summaryOf(
            tx("income-1", TransactionType.INCOME, 50_000L, at(3, 8)),
            tx("income-2", TransactionType.INCOME, 1_234L, at(3, 9)),
            tx("refund-1", TransactionType.REFUND, 1_000L, at(3, 7)),
        )
        assertEquals(51_234L, summary.monthIncomeMinor)
    }

    // ---------------------------------------------------------------- A6

    @Test
    fun A6_当月结余是收入减消费() {
        val summary = summaryOf(
            tx("exp-1", TransactionType.EXPENSE, 10_000L, at(3, 5)),
            tx("refund-1", TransactionType.REFUND, 1_000L, at(3, 7)),
            tx("income-1", TransactionType.INCOME, 50_000L, at(3, 8)),
        )
        assertEquals(9_000L, summary.monthExpenseMinor)
        assertEquals(50_000L, summary.monthIncomeMinor)
        assertEquals(41_000L, summary.monthBalanceMinor)
    }

    // ---------------------------------------------------------------- A7

    @Test
    fun A7_转账不计入消费收入与结余() {
        val summary = summaryOf(
            tx("exp-1", TransactionType.EXPENSE, 10_000L, at(3, 5)),
            tx("income-1", TransactionType.INCOME, 50_000L, at(3, 8)),
            tx("transfer-1", TransactionType.TRANSFER, 999_999L, at(3, 10)),
        )
        assertEquals(10_000L, summary.monthExpenseMinor)
        assertEquals(50_000L, summary.monthIncomeMinor)
        assertEquals(40_000L, summary.monthBalanceMinor)
    }

    // ---------------------------------------------------------------- A8

    @Test
    fun A8_未确认已忽略已删除重复与软删除均不计入统计() {
        val summary = summaryOf(
            tx("exp-confirmed", TransactionType.EXPENSE, 10_000L, at(3, 5)),
            tx("exp-pending", TransactionType.EXPENSE, 700L, at(3, 6), TransactionState.PENDING),
            tx("exp-ignored", TransactionType.EXPENSE, 800L, at(3, 7), TransactionState.IGNORED),
            tx("exp-deleted", TransactionType.EXPENSE, 900L, at(3, 8), TransactionState.DELETED),
            tx("exp-duplicate", TransactionType.EXPENSE, 950L, at(3, 9), TransactionState.DUPLICATE),
            // 软删除：即使 state 仍是 CONFIRMED，也不得计入（`docs/03` §6）
            tx(
                "exp-soft-deleted",
                TransactionType.EXPENSE,
                1_111L,
                at(3, 11),
                TransactionState.CONFIRMED,
                deletedAt = at(3, 12),
            ),
            tx("income-confirmed", TransactionType.INCOME, 20_000L, at(3, 13)),
            tx("income-pending", TransactionType.INCOME, 300L, at(3, 14), TransactionState.PENDING),
        )
        assertEquals(10_000L, summary.monthExpenseMinor)
        assertEquals(20_000L, summary.monthIncomeMinor)
        assertEquals(10_000L, summary.monthBalanceMinor)
    }

    // ---------------------------------------------------------------- A9

    @Test
    fun A9_待确认数量是全时段PENDING条数且排除软删除() {
        val summary = summaryOf(
            tx("exp-confirmed", TransactionType.EXPENSE, 10_000L, at(3, 5)),
            tx("pending-mar", TransactionType.EXPENSE, 100L, at(3, 6), TransactionState.PENDING),
            // 上月的 PENDING 也算（待办不分月份）
            tx("pending-feb", TransactionType.EXPENSE, 100L, at(2, 6), TransactionState.PENDING),
            // 软删除的 PENDING 不算
            tx(
                "pending-deleted",
                TransactionType.EXPENSE,
                100L,
                at(3, 7),
                TransactionState.PENDING,
                deletedAt = at(3, 8),
            ),
            tx("ignored", TransactionType.EXPENSE, 100L, at(3, 9), TransactionState.IGNORED),
        )
        assertEquals(2, summary.pendingCount)
    }

    // ---------------------------------------------------------------- A10

    @Test
    fun A10_上月与下月记录不计入本月且月边界精确到毫秒() {
        val summary = summaryOf(
            // 上月最后一毫秒：不计
            tx("feb-last-ms", TransactionType.EXPENSE, 1L, at(2, 29, 23, 59, 59, 999)),
            // 本月第一毫秒：计
            tx("mar-first-ms", TransactionType.EXPENSE, 10L, at(3, 1, 0, 0, 0, 0)),
            // 本月最后一毫秒：计
            tx("mar-last-ms", TransactionType.EXPENSE, 20L, at(3, 31, 23, 59, 59, 999)),
            // 下月第一毫秒：不计
            tx("apr-first-ms", TransactionType.EXPENSE, 1_000L, at(4, 1, 0, 0, 0, 0)),
            // 上月与下月的普通记录
            tx("feb-mid", TransactionType.INCOME, 5_000L, at(2, 15)),
            tx("apr-mid", TransactionType.INCOME, 7_000L, at(4, 15)),
        )
        assertEquals(30L, summary.monthExpenseMinor)
        assertEquals(0L, summary.monthIncomeMinor)
        assertEquals(-30L, summary.monthBalanceMinor)
    }

    // ---------------------------------------------------------------- R9 负数

    @Test
    fun R9_退款多于消费时当月消费为负且不截断() {
        val summary = summaryOf(
            tx("exp-1", TransactionType.EXPENSE, 500L, at(3, 5)),
            tx("refund-1", TransactionType.REFUND, 2_000L, at(3, 6)),
        )
        assertEquals("允许负值：−2000 + 500", -1_500L, summary.monthExpenseMinor)
        assertEquals(0L, summary.monthIncomeMinor)
        assertEquals("结余 = 0 − (−1500)", 1_500L, summary.monthBalanceMinor)
    }

    // ---------------------------------------------------------------- 响应式

    @Test
    fun 交易变化后汇总自动重算() = runBlocking {
        val repository = FakeSummaryTransactionRepository(
            listOf(tx("exp-1", TransactionType.EXPENSE, 10_000L, at(3, 5))),
        )
        val useCase = ObserveHomeSummaryUseCase(repository)

        val emissions = mutableListOf<Long>()
        val job = launch(Dispatchers.Unconfined) {
            useCase(MARCH_CLOCK).collect { emissions += it.monthExpenseMinor }
        }
        yield()
        assertEquals(listOf(10_000L), emissions)

        // 新增一笔支出 → 汇总必须自动重算（不需要重新订阅）
        repository.insert(tx("exp-2", TransactionType.EXPENSE, 2_500L, at(3, 6)))
        yield()
        assertEquals(listOf(10_000L, 12_500L), emissions)

        job.cancel()
    }
}

// ---------------------------------------------------------------------- 三表 combine（A12）

/**
 * `ObserveTransactionsUseCase` 的三表 combine 测试（`M2e-1a` §4.5，对应 A12）。
 *
 * 断言的是**已订阅中的列表会因分类改名而重新发射**——只让交易 Flow 响应式是做不到的。
 */
class ObserveTransactionsCombineTest {

    private val cn = ZoneId.of("Asia/Shanghai")

    private fun transaction(id: String, time: Long) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 1_200L,
        currency = Currency.CNY,
        merchant = "便利店",
        categoryId = "cat-1",
        accountId = "acc-1",
        sourceAccountId = null,
        targetAccountId = null,
        relatedTransactionId = null,
        transactionTime = time,
        transactionTimeZone = "Asia/Shanghai",
        note = null,
        state = TransactionState.CONFIRMED,
        source = CaptureSource.MANUAL,
        externalTransactionId = null,
        engineVersion = null,
        matchedRuleId = null,
        evidenceId = null,
        confirmedByUser = true,
        orphan = false,
        createdAt = time,
        updatedAt = time,
        deletedAt = null,
    )

    private fun category(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        icon = "food",
        sortOrder = 0,
        enabled = true,
        systemDefined = true,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun account(id: String, name: String) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        channel = null,
        last4 = null,
        enabled = true,
        sortOrder = 0,
        isDefault = true,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun A12_改分类名后列表重新发射新名称() = runBlocking {
        val time = LocalDateTime.of(2024, 3, 5, 12, 0).atZone(cn).toInstant().toEpochMilli()
        val transactions = FakeSummaryTransactionRepository(listOf(transaction("tx-1", time)))
        val categories = FakeSummaryCategoryRepository(listOf(category("cat-1", "餐饮")))
        val accounts = FakeSummaryAccountRepository(listOf(account("acc-1", "现金")))

        val useCase = ObserveTransactionsUseCase(
            transactions = transactions,
            categories = categories,
            accounts = accounts,
        )

        val emissions = mutableListOf<String?>()
        val job = launch(Dispatchers.Unconfined) {
            useCase().collect { entries -> emissions += entries.single().categoryName }
        }
        yield()
        assertEquals(listOf("餐饮"), emissions)

        categories.rename(id = "cat-1", newName = "餐费")
        yield()
        assertEquals("分类改名后列表必须自动更新", listOf("餐饮", "餐费"), emissions)

        job.cancel()
    }

    @Test
    fun 分类或账户缺失时名称为null且不崩溃() = runBlocking {
        val time = LocalDateTime.of(2024, 3, 5, 12, 0).atZone(cn).toInstant().toEpochMilli()
        val useCase = ObserveTransactionsUseCase(
            transactions = FakeSummaryTransactionRepository(listOf(transaction("tx-1", time))),
            categories = FakeSummaryCategoryRepository(emptyList()),
            accounts = FakeSummaryAccountRepository(emptyList()),
        )

        val entries = useCase().first()
        assertEquals(1, entries.size)
        assertNull(entries.single().categoryName)
        assertNull(entries.single().accountName)
        assertFalse(entries.single().id.isBlank())
        assertTrue(entries.single().amountMinor > 0)
    }
}
