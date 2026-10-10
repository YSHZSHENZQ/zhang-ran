/**
 * 唯一约束与"禁用后历史交易仍可读"测试（`docs/08` §9、`T2a` §4.4/§4.5/§4.7）。
 *
 * 覆盖判据：
 * - A5：三条唯一约束生效
 *   ① 同一 `type` 下分类 `name` 唯一（库级唯一索引）
 *   ② 分类预算 `periodKey + categoryId` 唯一（库级唯一索引）
 *   ③ **默认账户唯一** —— 口径**引用 `docs/03` §8**（P-005 落地文本）：
 *      「Room 不提供部分唯一索引……该约束由**应用层**保证：任何"设为默认"的写入必须在
 *      **同一事务**内先把其它账户的 `isDefault` 置为 `false`，再置目标账户为 `true`；
 *      禁止依赖数据库唯一索引实现该约束。」
 *      因此断言的是 `setAsDefault` 的事务行为，而**不是**库级唯一索引冲突。
 * - A6：分类或账户被禁用后，历史交易仍可正常读取（`docs/03` §7/§8）
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.BudgetPeriod
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConstraintTest {

    private val dbName = "constraint-test.db"
    private lateinit var context: Context
    private lateinit var db: ZhangRanDatabase

    private val now = 1_791_610_182_000L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.databaseBuilder(context, ZhangRanDatabase::class.java, dbName).build()
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private fun category(id: String, name: String, type: CategoryType = CategoryType.EXPENSE) =
        CategoryEntity(
            id = id,
            name = name,
            type = type,
            icon = "ic",
            sortOrder = 0,
            createdAt = now,
            updatedAt = now,
        )

    private fun account(id: String, name: String, isDefault: Boolean = false) = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        enabled = true,
        isDefault = isDefault,
        createdAt = now,
        updatedAt = now,
    )

    private fun transaction(id: String, categoryId: String?, accountId: String?) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 1000L,
        currency = Currency.CNY,
        categoryId = categoryId,
        accountId = accountId,
        transactionTime = now,
        transactionTimeZone = "Asia/Shanghai",
        state = TransactionState.CONFIRMED,
        source = CaptureSource.MANUAL,
        createdAt = now,
        updatedAt = now,
    )

    private fun budget(id: String, periodKey: String, categoryId: String?, scope: BudgetScope) =
        BudgetEntity(
            id = id,
            scope = scope,
            categoryId = categoryId,
            period = BudgetPeriod.MONTH,
            periodKey = periodKey,
            limitMinor = 100_000L,
            createdAt = now,
            updatedAt = now,
        )

    // ---------- A5 ①：同一 type 下分类名唯一 ----------

    @Test
    fun `同一类型下分类名重复被拒绝`() = runBlocking {
        db.categoryDao().insert(category("c1", "餐饮", CategoryType.EXPENSE))

        try {
            db.categoryDao().insert(category("c2", "餐饮", CategoryType.EXPENSE))
            fail("同一 type 下同名分类应被唯一索引拒绝")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // 期望：库级唯一索引拦截
        }

        // 不同 type 下同名允许（收入/支出互不影响）
        db.categoryDao().insert(category("c3", "餐饮", CategoryType.INCOME))
        assertEquals(2, db.categoryDao().listAll().size)
    }

    // ---------- A5 ②：分类预算 periodKey + categoryId 唯一 ----------

    @Test
    fun `同一月份同一分类的预算重复被拒绝`() = runBlocking {
        db.budgetDao().insert(budget("b1", "2026-10", "c1", BudgetScope.CATEGORY))

        try {
            db.budgetDao().insert(budget("b2", "2026-10", "c1", BudgetScope.CATEGORY))
            fail("同 periodKey + categoryId 应被唯一索引拒绝")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            // 期望
        }

        // 不同月份 / 不同分类均允许
        db.budgetDao().insert(budget("b3", "2026-11", "c1", BudgetScope.CATEGORY))
        db.budgetDao().insert(budget("b4", "2026-10", "c2", BudgetScope.CATEGORY))
        assertEquals(3, db.budgetDao().listAll().size)
    }

    // ---------- A5 ③：默认账户唯一（口径 = docs/03 §8 的应用层事务）----------

    @Test
    fun `设为默认账户时旧默认在同一事务内被清除`() = runBlocking {
        db.accountDao().insert(account("a1", "现金", isDefault = true))
        db.accountDao().insert(account("a2", "招商银行"))
        assertEquals("初始应恰好一个默认", 1, db.accountDao().countDefaults())

        // docs/03 §8：同一事务内先清后置
        db.accountDao().setAsDefault("a2", updatedAt = now + 1)

        assertEquals("切换后仍必须恰好一个默认", 1, db.accountDao().countDefaults())
        assertEquals("默认账户应已切换", "a2", db.accountDao().findDefault()?.id)
        assertEquals("旧默认必须被清除", false, db.accountDao().findById("a1")?.isDefault)
    }

    @Test
    fun `禁用默认账户后仍保留其默认取值`() = runBlocking {
        db.accountDao().insert(account("a1", "现金", isDefault = true))
        db.accountDao().disable("a1", updatedAt = now + 1)

        // docs/03 §8：被禁用账户仍保留 isDefault 取值（但不得作为新交易的默认账户）
        val disabled = db.accountDao().findById("a1")
        assertEquals(true, disabled?.isDefault)
        assertEquals(false, disabled?.enabled)
        assertEquals("禁用后不出现在启用列表", 0, db.accountDao().listEnabled().size)
    }

    // ---------- A5 ④：总预算同月唯一（应用层事务）----------

    @Test
    fun `同一月份的总预算第二条被拒绝`() = runBlocking {
        val first = db.budgetDao()
            .insertTotalBudgetIfAbsent(budget("t1", "2026-10", null, BudgetScope.TOTAL))
        val second = db.budgetDao()
            .insertTotalBudgetIfAbsent(budget("t2", "2026-10", null, BudgetScope.TOTAL))

        assertTrue("第一条总预算应写入成功", first)
        assertEquals("第二条总预算必须被拒绝", false, second)
        assertEquals(1, db.budgetDao().countTotalByPeriodKey("2026-10"))
    }

    // ---------- A6：禁用后历史交易仍可读出 ----------

    @Test
    fun `分类与账户禁用后历史交易仍可正常读取`() = runBlocking {
        db.categoryDao().insert(category("c1", "餐饮"))
        db.accountDao().insert(account("a1", "现金"))
        db.transactionDao().insert(transaction("tx-1", categoryId = "c1", accountId = "a1"))

        // 禁用分类与账户
        db.categoryDao().disable("c1", updatedAt = now + 1)
        db.accountDao().disable("a1", updatedAt = now + 1)

        // 历史交易仍在默认列表中，且其关联对象仍可按 id 读出
        val visible = db.transactionDao().listAll()
        assertEquals("禁用不得影响历史交易可见性", 1, visible.size)
        val tx = visible.first()
        assertEquals("c1", tx.categoryId)
        assertEquals("a1", tx.accountId)
        assertNotNull("禁用分类仍应按 id 可查（供历史展示）", db.categoryDao().findById("c1"))
        assertNotNull("禁用账户仍应按 id 可查", db.accountDao().findById("a1"))
        assertNull("但不应出现在启用列表", db.categoryDao().listEnabled().firstOrNull())
    }
}
