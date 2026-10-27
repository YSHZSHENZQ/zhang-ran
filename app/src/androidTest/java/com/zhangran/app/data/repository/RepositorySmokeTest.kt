/**
 * 数据层与首页的 instrumented 测试（任务书 M1c' 的 A2、A7、A8、A9）。
 *
 * 覆盖：
 * - 3 个 Repository 的基本读写；
 * - 默认列表**排除软删除**（`docs/03` §6）；禁用分类/账户**仍可读**（`docs/08` §9）；
 * - 端到端：`AddManualTransactionUseCase` 落库 → `ObserveTransactionsUseCase` 能读到（A2）；
 * - 列表按 `transactionTime` **倒序**（A8）；
 * - `HomeContent` 四种 `UiState` 均可渲染（A9）。
 *
 * 说明：Repository 用 **in-memory Room**，不触碰设备上的真实库。
 */
package com.zhangran.app.data.repository

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.HomeSummary
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import com.zhangran.app.domain.usecase.AddManualTransactionResult
import com.zhangran.app.domain.usecase.AddManualTransactionUseCase
import com.zhangran.app.domain.usecase.ManualTransactionInput
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import com.zhangran.app.domain.usecase.TransactionListEntry
import com.zhangran.app.ui.screen.home.HomeContent
import com.zhangran.app.ui.screen.home.HomeUiState
import com.zhangran.app.ui.screen.home.TransactionListItem
import com.zhangran.app.ui.theme.ZhangRanTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositorySmokeTest {

    private lateinit var database: ZhangRanDatabase
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var categoryRepository: CategoryRepository
    private lateinit var accountRepository: AccountRepository

    private companion object {
        const val TIME_1 = 1_791_610_182_000L
        const val TIME_2 = 1_791_649_800_000L
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZhangRanDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionRepository = RoomTransactionRepository(database.transactionDao())
        categoryRepository = RoomCategoryRepository(database.categoryDao())
        accountRepository = RoomAccountRepository(database.accountDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ---------------------------------------------------------- 基本读写

    @Test
    fun 三个仓库可写入并读回() = runBlocking {
        categoryRepository.let { repo ->
            // categoryRepository 只读接口，用 DAO 造数据（测试内允许）
            database.categoryDao().insert(
                CategoryEntity(
                    id = "cat-1",
                    name = "餐饮",
                    type = CategoryType.EXPENSE,
                    icon = "food",
                    sortOrder = 1,
                    createdAt = TIME_1,
                    updatedAt = TIME_1,
                ),
            )
            assertEquals(1, repo.all().size)
            assertEquals("cat-1", repo.findByName(CategoryType.EXPENSE, "餐饮")?.id)
        }

        database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                name = "微信零钱",
                type = AccountType.THIRD_PARTY,
                createdAt = TIME_1,
                updatedAt = TIME_1,
            ),
        )
        assertEquals(1, accountRepository.all().size)
        assertEquals("acc-1", accountRepository.findByName("微信零钱")?.id)

        transactionRepository.insert(transaction(id = "tx-1", time = TIME_1))
        assertEquals(1, transactionRepository.listVisible().size)
        assertNotNull(transactionRepository.getById("tx-1"))
    }

    // ---------------------------------------------------------- A7 软删除与禁用项

    @Test
    fun 默认列表排除软删除() = runBlocking {
        transactionRepository.insert(transaction(id = "tx-1", time = TIME_1))
        transactionRepository.insert(transaction(id = "tx-2", time = TIME_2))

        database.transactionDao().softDelete("tx-1", deletedAt = TIME_2, updatedAt = TIME_2)

        val visible = transactionRepository.listVisible()
        assertEquals(1, visible.size)
        assertEquals("tx-2", visible.single().id)

        assertNull("默认查询不得返回已删除记录", transactionRepository.getById("tx-1"))
        assertNotNull(
            "含已删除查询必须能取回（恢复流程用）",
            transactionRepository.getById("tx-1", includeDeleted = true),
        )
    }

    @Test
    fun 禁用分类与账户仍可读出() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "cat-1",
                name = "餐饮",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 1,
                createdAt = TIME_1,
                updatedAt = TIME_1,
            ),
        )
        database.categoryDao().disable("cat-1", TIME_2)

        database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                name = "微信零钱",
                type = AccountType.THIRD_PARTY,
                createdAt = TIME_1,
                updatedAt = TIME_1,
            ),
        )
        database.accountDao().disable("acc-1", TIME_2)

        assertEquals("禁用分类仍必须可读（历史交易展示）", 1, categoryRepository.all().size)
        assertEquals("禁用账户仍必须可读", 1, accountRepository.all().size)
        assertTrue(categoryRepository.enabledByType(CategoryType.EXPENSE).isEmpty())
    }

    // ---------------------------------------------------------- A2 / A8 端到端与排序

    @Test
    fun 手工记账落库后列表可见且按时间倒序() = runBlocking {
        database.categoryDao().insert(
            CategoryEntity(
                id = "cat-1",
                name = "餐饮",
                type = CategoryType.EXPENSE,
                icon = "food",
                sortOrder = 1,
                createdAt = TIME_1,
                updatedAt = TIME_1,
            ),
        )
        database.accountDao().insert(
            AccountEntity(
                id = "acc-1",
                name = "微信零钱",
                type = AccountType.THIRD_PARTY,
                createdAt = TIME_1,
                updatedAt = TIME_1,
            ),
        )

        val addUseCase = AddManualTransactionUseCase(
            normalize = Normalize(
                categoryResolver = CategoryResolver { null },
                accountResolver = AccountResolver { null },
                defaultZoneId = "Asia/Shanghai",
            ),
            transactionRepository = transactionRepository,
        )

        val older = addUseCase.invoke(
            ManualTransactionInput(
                type = TransactionType.EXPENSE,
                amountMinor = 1200L,
                categoryId = "cat-1",
                accountId = "acc-1",
                transactionTime = TIME_1,
                transactionTimeZone = "Asia/Shanghai",
                merchant = "全家便利店",
            ),
        ) as AddManualTransactionResult.Success
        assertEquals(TransactionState.CONFIRMED, older.state)

        addUseCase.invoke(
            ManualTransactionInput(
                type = TransactionType.INCOME,
                amountMinor = 10000L,
                categoryId = "cat-1",
                accountId = "acc-1",
                transactionTime = TIME_2,
                transactionTimeZone = "Asia/Shanghai",
                merchant = "公司",
            ),
        )

        val observeUseCase = ObserveTransactionsUseCase(
            transactions = transactionRepository,
            categories = categoryRepository,
            accounts = accountRepository,
        )
        val entries: List<TransactionListEntry> = observeUseCase().first()

        assertEquals("端到端：落库的两笔都应可见", 2, entries.size)
        assertTrue(
            "A8：必须按 transactionTime 倒序（最近的在前）",
            entries[0].transactionTime > entries[1].transactionTime,
        )
        assertEquals("分类名应被解析出来", "餐饮", entries[0].categoryName)
        assertEquals("账户名应被解析出来", "微信零钱", entries[0].accountName)
    }

    // ---------------------------------------------------------- 辅助

    private fun transaction(id: String, time: Long) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 1200L,
        currency = com.zhangran.app.domain.model.Currency.CNY,
        merchant = "便利店",
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
        deletedAt = null,
    )
}

/**
 * 首页四态渲染测试（A9）。
 *
 * 直接渲染无状态的 [HomeContent]，不依赖 Hilt / 数据库；
 * 每个测试方法只 `setContent` 一次（`docs/09` 的 Compose 测试坑）。
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenStatesTest {

    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private fun item(id: String) = TransactionListItem(
        id = id,
        title = "全家便利店",
        amountText = "-¥12.00",
        typeText = "支出",
        timeText = "2026-10-27 10:30",
        categoryName = "餐饮",
        accountName = "微信零钱",
    )

    @Test
    fun 加载态可渲染并显示页面名() {
        composeRule.setContent {
            ZhangRanTheme { HomeContent(state = HomeUiState.Loading) }
        }
        composeRule.onNodeWithText("首页").assertIsDisplayed()
    }

    @Test
    fun 空态可渲染并提示无记录() {
        composeRule.setContent {
            ZhangRanTheme { HomeContent(state = HomeUiState.Empty) }
        }
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("还没有记录").assertIsDisplayed()
    }

    @Test
    fun 错误态可渲染并可重试() {
        composeRule.setContent {
            ZhangRanTheme {
                HomeContent(state = HomeUiState.Error("数据库打开失败"), onRetry = {})
            }
        }
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun 内容态可渲染并显示列表项() {
        composeRule.setContent {
            ZhangRanTheme {
                HomeContent(
                    state = HomeUiState.Content(
                        items = listOf(item("tx-1")),
                        // 汇总字段与列表语义无关，本渲染测试用 0 值占位
                        summary = HomeSummary(0L, 0L, 0L, 0),
                    ),
                )
            }
        }
        composeRule.onNodeWithText("首页").assertIsDisplayed()
        composeRule.onNodeWithText("全家便利店").assertIsDisplayed()
        composeRule.onNodeWithText("-¥12.00").assertIsDisplayed()
    }
}
