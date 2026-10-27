/**
 * 初始数据清单的完整性断言（M2d-0 的 A1，纯 JVM）。
 *
 * 依据 `docs/02` §9「初始数据」：数量、顺序、兜底项、icon 语义标识、默认账户。
 * 这些断言是"清单与文档一致"的唯一护栏——清单被误改时测试立刻失败。
 */
package com.zhangran.app.data.local

import com.zhangran.app.domain.model.AccountType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultDataTest {

    @Test
    fun A1_支出分类数量与顺序与文档一致() {
        val names = DEFAULT_EXPENSE_CATEGORIES.map { it.name }
        assertEquals(12, names.size)
        assertEquals(
            listOf("餐饮", "交通", "购物", "居住", "娱乐", "医疗", "学习", "通讯", "人情", "旅行", "宠物", "其他"),
            names,
        )
    }

    @Test
    fun A1_收入分类数量与顺序与文档一致() {
        val names = DEFAULT_INCOME_CATEGORIES.map { it.name }
        assertEquals(6, names.size)
        assertEquals(listOf("工资", "奖金", "理财", "兼职", "红包", "其他"), names)
    }

    @Test
    fun A1_两个type下各有一个其他兜底分类() {
        val expenseFallback = DEFAULT_EXPENSE_CATEGORIES.filter { it.isFallback }
        val incomeFallback = DEFAULT_INCOME_CATEGORIES.filter { it.isFallback }

        assertEquals(1, expenseFallback.size)
        assertEquals(1, incomeFallback.size)
        assertEquals("其他", expenseFallback.single().name)
        assertEquals("其他", incomeFallback.single().name)
        // 「其他」必须是该清单的最后一项（兜底项排在末尾，与文档顺序一致）
        assertEquals("其他", DEFAULT_EXPENSE_CATEGORIES.last().name)
        assertEquals("其他", DEFAULT_INCOME_CATEGORIES.last().name)
    }

    @Test
    fun A1_同一type下分类名唯一() {
        assertEquals(
            DEFAULT_EXPENSE_CATEGORIES.size,
            DEFAULT_EXPENSE_CATEGORIES.map { it.name }.toSet().size,
        )
        assertEquals(
            DEFAULT_INCOME_CATEGORIES.size,
            DEFAULT_INCOME_CATEGORIES.map { it.name }.toSet().size,
        )
    }

    @Test
    fun A1_icon为语义标识且非emoji() {
        val all = DEFAULT_EXPENSE_CATEGORIES + DEFAULT_INCOME_CATEGORIES
        all.forEach { category ->
            assertTrue("${category.name} 的 icon 不能为空", category.icon.isNotBlank())
            assertTrue(
                "${category.name} 的 icon 必须是语义标识（仅小写字母/下划线），不能是 emoji：${category.icon}",
                category.icon.all { it in 'a'..'z' || it == '_' },
            )
        }
    }

    @Test
    fun A1_默认账户唯一且为现金() {
        assertEquals("现金", DEFAULT_ACCOUNT.name)
        assertEquals(AccountType.CASH, DEFAULT_ACCOUNT.type)
        assertTrue("默认账户必须 isDefault = true", DEFAULT_ACCOUNT.isDefault)
    }

    @Test
    fun A1_分类总数常量为18() {
        assertEquals(DEFAULT_CATEGORY_COUNT, DEFAULT_EXPENSE_CATEGORIES.size + DEFAULT_INCOME_CATEGORIES.size)
        assertEquals(18, DEFAULT_CATEGORY_COUNT)
    }

    @Test
    fun A1_默认清单不含微信支付宝等用户自建账户() {
        // 02 §9：只预置一个账户；微信/支付宝/银行卡由用户自行添加
        assertFalse(DEFAULT_ACCOUNT.name.contains("微信"))
        assertFalse(DEFAULT_ACCOUNT.name.contains("支付宝"))
    }
}
