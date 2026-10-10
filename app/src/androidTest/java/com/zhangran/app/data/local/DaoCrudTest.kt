/**
 * DAO 基础 CRUD 与"关联读取"测试（`docs/08` §9、`T2a` §4.7）。
 *
 * 覆盖判据：
 * - A2：Insert / Update / Delete（软删除）/ Query / Relation 可用
 * - A3：软删除后不出现在默认列表
 * - A4：软删除记录可通过显式查询取回（供恢复使用）
 *
 * 使用**临时文件库**并显式 `close()` + 删除，避免污染设备上的真实数据库
 * （任务书 §4.7：不得污染真实库）。
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoCrudTest {

    private val dbName = "dao-crud-test.db"
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

    // ---------- 辅助数据 ----------

    private fun transactionEntity(
        id: String = "tx-1",
        categoryId: String? = "cat-1",
        accountId: String? = "acc-1",
        state: TransactionState = TransactionState.CONFIRMED,
        deletedAt: Long? = null,
    ) = TransactionEntity(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 2650L,
        currency = Currency.CNY,
        merchant = "全家便利店",
        categoryId = categoryId,
        accountId = accountId,
        transactionTime = now,
        transactionTimeZone = "Asia/Shanghai",
        state = state,
        source = CaptureSource.NOTIFICATION,
        createdAt = now,
        updatedAt = now,
        deletedAt = deletedAt,
    )

    private fun categoryEntity(id: String = "cat-1", name: String = "餐饮") = CategoryEntity(
        id = id,
        name = name,
        type = CategoryType.EXPENSE,
        icon = "food",
        sortOrder = 1,
        createdAt = now,
        updatedAt = now,
    )

    private fun accountEntity(id: String = "acc-1", name: String = "现金") = AccountEntity(
        id = id,
        name = name,
        type = AccountType.CASH,
        channel = PaymentChannel.CASH,
        sortOrder = 1,
        createdAt = now,
        updatedAt = now,
    )

    // ---------- A2：CRUD ----------

    @Test
    fun `插入_更新_查询可用`() = runBlocking {
        db.transactionDao().insert(transactionEntity())

        val loaded = db.transactionDao().findById("tx-1")
        assertNotNull("插入后应能按 id 查到", loaded)
        assertEquals(2650L, loaded!!.amountMinor)
        assertEquals(CaptureSource.NOTIFICATION, loaded.source)

        // 更新：改商户与金额
        db.transactionDao().update(loaded.copy(merchant = "罗森", amountMinor = 1200L, updatedAt = now + 1))
        val updated = db.transactionDao().findById("tx-1")
        assertEquals("罗森", updated?.merchant)
        assertEquals(1200L, updated?.amountMinor)
    }

    @Test
    fun `五张表都能写入与读出`() = runBlocking {
        db.categoryDao().insert(categoryEntity())
        db.accountDao().insert(accountEntity())
        db.transactionDao().insert(transactionEntity())
        db.fieldEditDao().insert(
            com.zhangran.app.data.local.entity.FieldEditEntity(
                transactionId = "tx-1",
                fieldName = "amountMinor",
                editedAt = now,
            ),
        )
        db.budgetDao().insert(
            com.zhangran.app.data.local.entity.BudgetEntity(
                id = "bud-1",
                scope = com.zhangran.app.domain.model.BudgetScope.TOTAL,
                periodKey = "2026-10",
                limitMinor = 300_000L,
                createdAt = now,
                updatedAt = now,
            ),
        )

        assertEquals(1, db.transactionDao().countVisible())
        assertEquals("餐饮", db.categoryDao().findById("cat-1")?.name)
        assertEquals("现金", db.accountDao().findById("acc-1")?.name)
        assertEquals(1, db.fieldEditDao().listByTransaction("tx-1").size)
        assertEquals("2026-10", db.budgetDao().findById("bud-1")?.periodKey)
    }

    // ---------- A3 / A4：软删除 ----------

    @Test
    fun `软删除后不在默认列表但可显式取回并恢复`() = runBlocking {
        db.transactionDao().insert(transactionEntity(id = "tx-1"))
        db.transactionDao().insert(transactionEntity(id = "tx-2"))

        db.transactionDao().softDelete(id = "tx-1", deletedAt = now + 10, updatedAt = now + 10)

        // A3：默认列表排除软删除
        val visible = db.transactionDao().listAll()
        assertEquals("默认列表只剩 1 条", 1, visible.size)
        assertEquals("tx-2", visible.first().id)
        assertEquals(1, db.transactionDao().countVisible())

        // 按 id 查询（未删除）取不到
        assertNull("软删除记录不应被 findById 命中", db.transactionDao().findById("tx-1"))

        // A4：显式查询可取回（供恢复流程使用）
        val deleted = db.transactionDao().findByIdIncludingDeleted("tx-1")
        assertNotNull("含已删除的查询应能取回", deleted)
        assertEquals("tx-1", deleted!!.id)
        assertNotNull("deletedAt 应已写入", deleted.deletedAt)

        // 恢复
        db.transactionDao().restore(id = "tx-1", updatedAt = now + 20)
        val restored = db.transactionDao().findById("tx-1")
        assertNotNull("恢复后应回到默认列表可见集合", restored)
        assertNull("恢复后 deletedAt 应为空", restored!!.deletedAt)
        assertEquals(2, db.transactionDao().countVisible())
    }

    @Test
    fun `按状态与时间区间查询可用`() = runBlocking {
        db.transactionDao().insert(transactionEntity(id = "p-1", state = TransactionState.PENDING))
        db.transactionDao().insert(transactionEntity(id = "c-1", state = TransactionState.CONFIRMED))

        assertEquals(1, db.transactionDao().listByState(TransactionState.PENDING).size)
        assertEquals(1, db.transactionDao().listByState(TransactionState.CONFIRMED).size)

        val inRange = db.transactionDao().listInTimeRange(now - 1, now + 1)
        assertEquals(2, inRange.size)
        assertEquals(0, db.transactionDao().listInTimeRange(now + 1, now + 2).size)
    }

    // ---------- A2：Relation（按关联读取分类/账户）----------

    @Test
    fun `按交易引用的分类与账户可独立读出`() = runBlocking {
        db.categoryDao().insert(categoryEntity())
        db.accountDao().insert(accountEntity())
        db.transactionDao().insert(transactionEntity())

        val tx = db.transactionDao().findById("tx-1")!!
        // 这里显式按外键值读取关联对象（本任务不建 FK，见 TransactionEntity 注释）
        val category = tx.categoryId?.let { db.categoryDao().findById(it) }
        val account = tx.accountId?.let { db.accountDao().findById(it) }

        assertNotNull(category)
        assertNotNull(account)
        assertEquals("餐饮", category!!.name)
        assertEquals("现金", account!!.name)
    }

    // ---------- 字段级保护记录 ----------

    @Test
    fun `字段保护记录按交易与字段查询且同字段只保留一条`() = runBlocking {
        db.fieldEditDao().insert(
            com.zhangran.app.data.local.entity.FieldEditEntity(
                transactionId = "tx-1",
                fieldName = "merchant",
                editedAt = now,
            ),
        )
        db.fieldEditDao().insert(
            com.zhangran.app.data.local.entity.FieldEditEntity(
                transactionId = "tx-1",
                fieldName = "amountMinor",
                editedAt = now,
            ),
        )
        // 同交易同字段再写一次：复合主键 + REPLACE → 仍只有一条
        db.fieldEditDao().insert(
            com.zhangran.app.data.local.entity.FieldEditEntity(
                transactionId = "tx-1",
                fieldName = "merchant",
                editedAt = now + 5,
            ),
        )

        val edits = db.fieldEditDao().listByTransaction("tx-1")
        assertEquals(2, edits.size)
        assertEquals(now + 5, db.fieldEditDao().find("tx-1", "merchant")?.editedAt)
    }
}
