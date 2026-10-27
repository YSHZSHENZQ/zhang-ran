/**
 * 响应式读取的设备侧测试（`M2e-1a` §4.6，对应验收 A11 / A12 / A13）。
 *
 * 覆盖：
 * - **A11**：`TransactionDao.observeVisible()` 在 insert / update / softDelete 后**各自重新发射**；
 * - **A12**：三表 combine——改分类名后 `ObserveTransactionsUseCase` 的列表发射新名称；
 * - **A13**：响应式查询的过滤与排序与既有 suspend 版**完全一致**（`observeVisible` vs `listAll`，
 *   `observeAll` vs `listAll`，分类与账户同理）。
 *
 * 说明：用 **in-memory Room**，不触碰设备上的真实数据库，也不污染真实库。
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.RoomAccountRepository
import com.zhangran.app.data.repository.RoomCategoryRepository
import com.zhangran.app.data.repository.RoomTransactionRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import java.util.Collections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ObserveFlowTest {

    private companion object {
        const val NOW = 1_791_610_182_000L
        const val TIMEOUT_MILLIS = 5_000L
    }

    private lateinit var context: Context
    private lateinit var db: ZhangRanDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ZhangRanDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------------------------------------------------------------- 辅助

    private fun transaction(
        id: String,
        time: Long = NOW,
        deletedAt: Long? = null,
        merchant: String = "便利店",
    ) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 1_200L,
        currency = Currency.CNY,
        merchant = merchant,
        categoryId = null,
        accountId = null,
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
        deletedAt = deletedAt,
    )

    private fun category(id: String, name: String) = CategoryEntity(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        icon = "food",
        sortOrder = 0,
        enabled = true,
        systemDefined = true,
        createdAt = NOW,
        updatedAt = NOW,
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
        createdAt = NOW,
        updatedAt = NOW,
    )

    /** 轮询等待条件成立；超时即失败（避免"挂死而不是失败"，`docs/09` §5）。 */
    private suspend fun awaitUntil(condition: () -> Boolean) {
        withTimeout(TIMEOUT_MILLIS) {
            while (!condition()) delay(10)
        }
    }

    // ---------------------------------------------------------------- A11

    @Test
    fun A11_观察流在插入更新软删除后各发射() = runBlocking {
        val dao = db.transactionDao()
        val emissions = Collections.synchronizedList(mutableListOf<List<TransactionEntity>>())
        val job = launch(Dispatchers.IO) {
            dao.observeVisible().collect { emissions += it }
        }
        try {
            // 1) 订阅后的首次查询（空表）
            awaitUntil { emissions.isNotEmpty() }
            assertTrue("空表时首次发射应为空列表", emissions.last().isEmpty())

            // 2) insert → 重新发射并包含新记录
            dao.insert(transaction("tx-1", time = NOW))
            awaitUntil { emissions.lastOrNull()?.size == 1 }
            assertEquals("tx-1", emissions.last().single().id)

            // 3) update → 重新发射并带出新值
            dao.update(transaction("tx-1", time = NOW, merchant = "改后的商户"))
            awaitUntil { emissions.lastOrNull()?.singleOrNull()?.merchant == "改后的商户" }

            // 4) softDelete → 重新发射且不再包含该记录
            dao.softDelete(id = "tx-1", deletedAt = NOW + 1, updatedAt = NOW + 1)
            awaitUntil { emissions.lastOrNull()?.isEmpty() == true }
        } finally {
            job.cancel()
        }
    }

    @Test
    fun 分类与账户的观察流在改名字后发射新值() = runBlocking {
        val categoryDao = db.categoryDao()
        val accountDao = db.accountDao()

        categoryDao.insert(category("cat-1", "餐饮"))
        accountDao.insert(account("acc-1", "现金"))

        val categoryNames = Collections.synchronizedList(mutableListOf<String>())
        val accountNames = Collections.synchronizedList(mutableListOf<String>())
        val categoryJob = launch(Dispatchers.IO) {
            categoryDao.observeAll().collect { rows -> categoryNames += rows.single().name }
        }
        val accountJob = launch(Dispatchers.IO) {
            accountDao.observeAll().collect { rows -> accountNames += rows.single().name }
        }
        try {
            awaitUntil { categoryNames.isNotEmpty() && accountNames.isNotEmpty() }
            assertEquals("餐饮", categoryNames.last())
            assertEquals("现金", accountNames.last())

            categoryDao.update(category("cat-1", "餐费"))
            accountDao.update(account("acc-1", "微信零钱"))

            awaitUntil { categoryNames.lastOrNull() == "餐费" }
            awaitUntil { accountNames.lastOrNull() == "微信零钱" }
        } finally {
            categoryJob.cancel()
            accountJob.cancel()
        }
    }

    // ---------------------------------------------------------------- A13

    @Test
    fun A13_交易观察流的过滤与排序与一次性查询一致() = runBlocking {
        val dao = db.transactionDao()
        dao.insert(transaction("tx-old", time = NOW - 3_000))
        dao.insert(transaction("tx-new", time = NOW - 1_000))
        dao.insert(transaction("tx-mid", time = NOW - 2_000))
        // 软删除：两条查询都必须排除
        dao.insert(transaction("tx-deleted", time = NOW, deletedAt = NOW + 1))

        val oneShot = dao.listAll()
        val observed = dao.observeVisible().first()

        assertEquals(oneShot.map { it.id }, observed.map { it.id })
        assertEquals(
            "按 transactionTime 倒序且排除软删除",
            listOf("tx-new", "tx-mid", "tx-old"),
            observed.map { it.id },
        )
    }

    @Test
    fun A13_分类与账户观察流的过滤与排序与一次性查询一致() = runBlocking {
        val categoryDao = db.categoryDao()
        val accountDao = db.accountDao()

        categoryDao.insert(category("cat-enabled", "餐饮"))
        categoryDao.insert(category("cat-disabled", "旧的").copy(enabled = false))
        accountDao.insert(account("acc-enabled", "现金"))
        accountDao.insert(account("acc-disabled", "旧卡").copy(enabled = false))

        // 含已禁用（历史交易要显示禁用分类/账户名），按 sortOrder 升序
        assertEquals(
            categoryDao.listAll().map { it.id },
            categoryDao.observeAll().first().map { it.id },
        )
        assertEquals(
            accountDao.listAll().map { it.id },
            accountDao.observeAll().first().map { it.id },
        )
        assertEquals(2, categoryDao.observeAll().first().size)
        assertEquals(2, accountDao.observeAll().first().size)
    }

    // ---------------------------------------------------------------- A12（真实 Room + 三表 combine）

    @Test
    fun A12_改分类名后列表自动发射新名称() = runBlocking {
        db.categoryDao().insert(category("cat-1", "餐饮"))
        db.accountDao().insert(account("acc-1", "现金"))
        db.transactionDao().insert(
            transaction("tx-1", time = NOW).copy(categoryId = "cat-1", accountId = "acc-1"),
        )

        val useCase = ObserveTransactionsUseCase(
            transactions = RoomTransactionRepository(db.transactionDao()),
            categories = RoomCategoryRepository(db.categoryDao()),
            accounts = RoomAccountRepository(db.accountDao()),
        )

        val categoryNames = Collections.synchronizedList(mutableListOf<String?>())
        val job = launch(Dispatchers.IO) {
            useCase().collect { entries -> categoryNames += entries.singleOrNull()?.categoryName }
        }
        try {
            awaitUntil { categoryNames.lastOrNull() == "餐饮" }

            db.categoryDao().update(category("cat-1", "餐费"))
            awaitUntil { categoryNames.lastOrNull() == "餐费" }

            assertEquals("餐费", categoryNames.last())
        } finally {
            job.cancel()
        }
    }
}
