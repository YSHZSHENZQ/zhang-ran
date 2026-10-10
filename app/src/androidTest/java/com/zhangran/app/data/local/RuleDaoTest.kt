/**
 * 解析规则 DAO 测试（`docs/02` §16、`T2b` §4.2/§6）。
 *
 * 覆盖判据：
 * - A6：规则查询按 `priority` 升序返回 enabled 规则（决策在 Domain，本层只提供查询契约）
 * - A7：`include` / `exclude` 的 JSON 数组存储与解析可用（含转义与默认空数组）
 * - `priority` 唯一性校验入口可用
 */
package com.zhangran.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.common.RuleListCodec
import com.zhangran.app.common.RuleListResult
import com.zhangran.app.data.local.entity.ParsingRuleEntity
import com.zhangran.app.domain.model.MatchMode
import com.zhangran.app.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuleDaoTest {

    private val dbName = "rule-dao-test.db"
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

    private fun rule(
        ruleId: String,
        priority: Int,
        enabled: Boolean = true,
        include: String = "[]",
        exclude: String = "[]",
        matchMode: MatchMode = MatchMode.DEFAULT,
        direction: TransactionType? = null,
    ) = ParsingRuleEntity(
        ruleId = ruleId,
        priority = priority,
        enabled = enabled,
        direction = direction,
        include = include,
        exclude = exclude,
        matchMode = matchMode,
        engineVersion = "1.0.0",
        createdAt = now,
        updatedAt = now,
    )

    // ---------- A6：priority 升序 + 只取 enabled ----------

    @Test
    fun `按 priority 升序返回启用规则`() = runBlocking {
        db.parsingRuleDao().insert(rule("r3", priority = 30))
        db.parsingRuleDao().insert(rule("r1", priority = 10))
        db.parsingRuleDao().insert(rule("r2", priority = 20))
        db.parsingRuleDao().insert(rule("r-disabled", priority = 5, enabled = false))

        val ordered = db.parsingRuleDao().listEnabledByPriority()
        assertEquals("必须按 priority 升序且排除禁用规则", listOf("r1", "r2", "r3"), ordered.map { it.ruleId })
    }

    @Test
    fun `规则页列表含禁用规则`() = runBlocking {
        db.parsingRuleDao().insert(rule("r1", priority = 10))
        db.parsingRuleDao().insert(rule("r-disabled", priority = 5, enabled = false))

        assertEquals(2, db.parsingRuleDao().listAll().size)
        assertEquals(1, db.parsingRuleDao().listEnabledByPriority().size)
    }

    // ---------- priority 唯一性校验入口 ----------

    @Test
    fun `可检测启用规则间的 priority 冲突`() = runBlocking {
        db.parsingRuleDao().insert(rule("r1", priority = 10))
        db.parsingRuleDao().insert(rule("r2", priority = 20))
        db.parsingRuleDao().insert(rule("r-disabled", priority = 10, enabled = false))

        // r1 占用 10；检查"新增一个 priority=10 的规则"是否冲突（排除自身 id）
        assertEquals(
            "启用规则中 priority=10 已被占用",
            1,
            db.parsingRuleDao().countEnabledWithPriority(10, excludeRuleId = "new-rule"),
        )
        // 禁用规则不参与唯一性
        assertEquals(
            "禁用规则不占用 priority",
            0,
            db.parsingRuleDao().countEnabledWithPriority(10, excludeRuleId = "r1"),
        )
        assertEquals(
            "未被占用的 priority 不冲突",
            0,
            db.parsingRuleDao().countEnabledWithPriority(99, excludeRuleId = "new-rule"),
        )
    }

    // ---------- A7：include / exclude 的 JSON 数组存储与解析 ----------

    @Test
    fun `默认空集合存为空数组且 matchMode 默认 CONTAINS`() = runBlocking {
        db.parsingRuleDao().insert(rule("r1", priority = 10))
        val loaded = db.parsingRuleDao().findById("r1")!!

        assertEquals("[]", loaded.include)
        assertEquals("[]", loaded.exclude)
        assertEquals(MatchMode.CONTAINS, loaded.matchMode)
        assertNull("direction 为空表示未指定", loaded.direction)
    }

    @Test
    fun `include 可存含分隔符与正则的内容并原样取回`() = runBlocking {
        val keywords = listOf("付款成功", "支付成功", "含,逗号", "含|竖线")
        val regex = """^付款(成功|完成),\s*\d+\.\d{2}$"""
        db.parsingRuleDao().insert(
            rule(
                "r1",
                priority = 10,
                include = RuleListCodec.encode(keywords),
                exclude = RuleListCodec.encode(listOf(regex)),
                matchMode = MatchMode.REGEX,
                direction = TransactionType.EXPENSE,
            ),
        )

        val loaded = db.parsingRuleDao().findById("r1")!!
        val include = RuleListCodec.decode(loaded.include) as RuleListResult.Success
        val exclude = RuleListCodec.decode(loaded.exclude) as RuleListResult.Success

        assertEquals(keywords, include.items)
        assertEquals(listOf(regex), exclude.items)
        assertEquals(MatchMode.REGEX, loaded.matchMode)
        assertEquals(TransactionType.EXPENSE, loaded.direction)
    }

    @Test
    fun `存储值往返后逐字节稳定`() = runBlocking {
        val encoded = RuleListCodec.encode(listOf("微信", "支付宝", "含\"引号"))
        db.parsingRuleDao().insert(rule("r1", priority = 10, include = encoded))

        val loaded = db.parsingRuleDao().findById("r1")!!
        val items = (RuleListCodec.decode(loaded.include) as RuleListResult.Success).items
        assertEquals("解析后再序列化必须与库中值完全相同", encoded, RuleListCodec.encode(items))
    }

    // ---------- 基本 CRUD ----------

    @Test
    fun `规则可更新与计数`() = runBlocking {
        db.parsingRuleDao().insert(rule("r1", priority = 10))
        val loaded = db.parsingRuleDao().findById("r1")!!
        db.parsingRuleDao().update(loaded.copy(priority = 99, enabled = false, updatedAt = now + 1))

        val updated = db.parsingRuleDao().findById("r1")!!
        assertEquals(99, updated.priority)
        assertEquals(false, updated.enabled)
        assertEquals(1, db.parsingRuleDao().count())
        assertTrue(db.parsingRuleDao().listEnabledByPriority().isEmpty())
    }
}
