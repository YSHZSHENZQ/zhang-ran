/**
 * 金额解析与转分测试。
 *
 * 覆盖任务书 T3 §6 的 A1（`docs/08` §2.1 全部 8 行）、A2（负数拒绝）、A3（Double 陷阱）、
 * A4（无 Double/Float 运算）、A5（非 CNY → 币种不支持）。
 *
 * 说明：本文件是金额语义的**回归闸门**——任何改动都必须让这里的断言继续全绿。
 */
package com.zhangran.app.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {

    /** 成功结果取金额；失败时让测试以明确信息失败。 */
    private fun minorOf(result: MoneyResult): Long = when (result) {
        is MoneyResult.Success -> result.amountMinor
        is MoneyResult.Invalid -> throw AssertionError("期望成功，实际非法：${result.reason}（raw=${result.raw}）")
        is MoneyResult.CurrencyNotSupported -> throw AssertionError("期望成功，实际币种不支持：${result.currency}")
    }

    // ---------- A1：docs/08 §2.1 表格逐行 ----------

    @Test
    fun `¥0 转 0`() {
        assertEquals(0L, minorOf(Money.fromText("¥0")))
    }

    @Test
    fun `¥0_01 转 1`() {
        assertEquals(1L, minorOf(Money.fromText("¥0.01")))
    }

    @Test
    fun `¥1 转 100`() {
        assertEquals(100L, minorOf(Money.fromText("¥1")))
    }

    @Test
    fun `¥12_34 转 1234`() {
        assertEquals(1234L, minorOf(Money.fromText("¥12.34")))
    }

    @Test
    fun `¥100 转 10000`() {
        assertEquals(10000L, minorOf(Money.fromText("¥100")))
    }

    @Test
    fun `¥999999_99 转 99999999`() {
        assertEquals(99999999L, minorOf(Money.fromText("¥999999.99")))
    }

    @Test
    fun `千分位 1,234_56 转 123456`() {
        assertEquals(123456L, minorOf(Money.fromText("1,234.56")))
    }

    @Test
    fun `全角千分位 1，234_56 转 123456`() {
        assertEquals(123456L, minorOf(Money.fromText("1，234.56")))
    }

    @Test
    fun `全角货币符号 ￥12_34 转 1234`() {
        assertEquals(1234L, minorOf(Money.fromText("￥12.34")))
    }

    @Test
    fun `非法输入解析失败`() {
        val invalids = listOf("abc", "--1", "", "  ", "¥", "1.2.3", "-1", "1e3", "12a", "1..2")
        invalids.forEach { input ->
            val result = Money.fromText(input)
            assertTrue("输入「$input」必须解析失败，实际=$result", result is MoneyResult.Invalid)
        }
    }

    // ---------- A2：负数与上限 ----------

    @Test
    fun `负数被拒绝`() {
        listOf("-1", "-0.01", "¥-5", "-1234.56").forEach { input ->
            val result = Money.fromText(input)
            assertTrue("输入「$input」必须被拒绝，实际=$result", result is MoneyResult.Invalid)
        }
    }

    @Test
    fun `超过上限被拒绝`() {
        // 上限 = 99_999_999_999 分（约 100 亿元）
        val overLimit = "1000000000.00" // 1000 亿元 → 100_000_000_000 分
        val result = Money.fromText(overLimit)
        assertTrue("超限金额必须被拒绝，实际=$result", result is MoneyResult.Invalid)

        // 边界值恰好等于上限应成功
        assertEquals(MAX_AMOUNT_MINOR, minorOf(Money.fromText("999999999.99")))
    }

    // ---------- A3：Double 陷阱 ----------

    @Test
    fun `Double 陷阱 8_7 转 870`() {
        // 若用 8.7 * 100 会得到 869.9999...，进而截断成 869；此处必须为 870
        assertEquals(870L, minorOf(Money.fromUpstream(8.7)))
    }

    @Test
    fun `Double 陷阱 0_29 转 29`() {
        assertEquals(29L, minorOf(Money.fromUpstream(0.29)))
    }

    @Test
    fun `Double 陷阱 26_5 转 2650`() {
        assertEquals(2650L, minorOf(Money.fromUpstream(26.5)))
    }

    @Test
    fun `Double 边界与小数`() {
        assertEquals(1L, minorOf(Money.fromUpstream(0.01)))
        assertEquals(100L, minorOf(Money.fromUpstream(1.0)))
        assertEquals(99999999L, minorOf(Money.fromUpstream(999999.99)))
        assertEquals(-1L, (Money.fromUpstream(-1.0) as MoneyResult.Invalid).amountMinor)
    }

    @Test
    fun `Double 非有限值与缺失`() {
        assertTrue(Money.fromUpstream(Double.NaN) is MoneyResult.Invalid)
        assertTrue(Money.fromUpstream(Double.POSITIVE_INFINITY) is MoneyResult.Invalid)
        assertTrue(Money.fromUpstream(null) is MoneyResult.Invalid)
    }

    @Test
    fun `多余小数位按 HALF_UP 舍入`() {
        // 3 位小数：1.005 → 100.5 分 → HALF_UP → 101；1.004 → 100.4 → 100
        assertEquals(101L, minorOf(Money.fromText("1.005")))
        assertEquals(100L, minorOf(Money.fromText("1.004")))
    }

    // ---------- A5：币种 ----------

    @Test
    fun `空币种视为 CNY`() {
        assertEquals(1234L, minorOf(Money.fromText("12.34", currency = null)))
        assertEquals(1234L, minorOf(Money.fromText("12.34", currency = "")))
        assertEquals(1234L, minorOf(Money.fromText("12.34", currency = "   ")))
    }

    @Test
    fun `CNY 大小写均接受`() {
        assertEquals(1234L, minorOf(Money.fromText("12.34", currency = "CNY")))
        assertEquals(1234L, minorOf(Money.fromText("12.34", currency = "cny")))
    }

    @Test
    fun `非 CNY 返回币种不支持且不抛异常`() {
        listOf("USD", "EUR", "JPY", "HKD").forEach { currency ->
            val result = Money.fromText("12.34", currency = currency)
            assertTrue("币种 $currency 必须返回币种不支持，实际=$result", result is MoneyResult.CurrencyNotSupported)
            assertEquals(currency, (result as MoneyResult.CurrencyNotSupported).currency)
        }
    }

    @Test
    fun `币种不支持优先于金额非法`() {
        // 币种不受支持时，即使金额也非法，也应返回 CurrencyNotSupported（上层 reason 归因更明确）
        val result = Money.fromText("abc", currency = "USD")
        assertTrue(result is MoneyResult.CurrencyNotSupported)
    }

    @Test
    fun `上游入口透传币种`() {
        assertTrue(Money.fromUpstream(12.34, currency = "USD") is MoneyResult.CurrencyNotSupported)
        assertEquals(1234L, minorOf(Money.fromUpstream(12.34, currency = "CNY")))
    }

    // ---------- 结果类型与占位值 ----------

    @Test
    fun `失败结果的金额占位值不得被当真`() {
        val invalid = Money.fromText("abc") as MoneyResult.Invalid
        assertEquals(MoneyResult.UNUSED_AMOUNT, invalid.amountMinor)
        assertTrue("占位值必须为负数，避免误用", invalid.amountMinor < 0)
    }

    @Test
    fun `成功结果恒非负`() {
        listOf("0", "0.0", "0.00", "1", "0.01").forEach { input ->
            val minor = minorOf(Money.fromText(input))
            assertTrue("成功结果必须非负：$input", minor >= 0)
        }
    }
}
