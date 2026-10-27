/**
 * 月边界计算测试（`M2e-1a` §4.1，对应验收 A1 / A2 / A3）。
 *
 * 覆盖：
 * - 半开区间：月初第一毫秒 / 月末最后一毫秒属于本月，下月第一毫秒与本月前一毫秒不属于；
 * - 跨年：12 月的下一个月是次年 1 月；
 * - 闰年 / 月末：2024-02 有 29 天、2023-02 有 28 天；
 * - 时区正确性：UTC+8 的 3 月 1 日 00:30（UTC 仍是 2 月）必须归入 3 月；
 * - 非法时区 / 非法月份 → 显式失败（**不回退** systemDefault）。
 */
package com.zhangran.app.domain.service

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthRangeTest {

    private companion object {
        /** 无夏令时的固定偏移时区，便于精确断言。 */
        const val CN = "Asia/Shanghai"

        /** 一天/一月的毫秒数（仅用于"整月长度"这类结构断言，不参与金额计算）。 */
        const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
    }

    /** 构造"某时区下的本地时刻"对应的 UTC millis。 */
    private fun atLocal(
        zone: String,
        year: Int,
        month: Int,
        day: Int,
        hour: Int = 0,
        minute: Int = 0,
        second: Int = 0,
        millis: Int = 0,
    ): Long = LocalDateTime.of(year, month, day, hour, minute, second, millis * 1_000_000)
        .atZone(ZoneId.of(zone))
        .toInstant()
        .toEpochMilli()

    // ---------------------------------------------------------------- A1 半开区间

    @Test
    fun 月初第一毫秒属于本月() {
        val range = TransactionTime.monthRangeUtc(CN, 2024, 3)
        assertTrue("2024-03-01T00:00:00.000 应属于 3 月", range.contains(atLocal(CN, 2024, 3, 1)))
        assertEquals(atLocal(CN, 2024, 3, 1), range.startInclusive)
    }

    @Test
    fun 月末最后一毫秒属于本月() {
        val range = TransactionTime.monthRangeUtc(CN, 2024, 3)
        assertTrue(
            "2024-03-31T23:59:59.999 应属于 3 月",
            range.contains(atLocal(CN, 2024, 3, 31, 23, 59, 59, 999)),
        )
    }

    @Test
    fun 下月第一毫秒不属于本月() {
        val range = TransactionTime.monthRangeUtc(CN, 2024, 3)
        val nextMonthFirstMillisecond = atLocal(CN, 2024, 4, 1)
        assertEquals(nextMonthFirstMillisecond, range.endExclusive)
        assertFalse("2024-04-01T00:00:00.000 不属于 3 月", range.contains(nextMonthFirstMillisecond))
    }

    @Test
    fun 本月前一毫秒不属于本月() {
        val range = TransactionTime.monthRangeUtc(CN, 2024, 3)
        assertFalse(range.contains(range.startInclusive - 1))
    }

    // ---------------------------------------------------------------- A1 跨年

    @Test
    fun 跨年_12月的下一个月是次年1月() {
        val december = TransactionTime.monthRangeUtc(CN, 2024, 12)
        assertEquals(atLocal(CN, 2024, 12, 1), december.startInclusive)
        assertEquals(atLocal(CN, 2025, 1, 1), december.endExclusive)
        assertTrue(december.contains(atLocal(CN, 2024, 12, 31, 23, 59, 59, 999)))

        val january = TransactionTime.monthRangeUtc(CN, 2025, 1)
        assertEquals(
            "相邻两个月必须首尾相接（无缝隙、无重叠）",
            december.endExclusive,
            january.startInclusive,
        )
    }

    // ---------------------------------------------------------------- A1 闰年 / 月末

    @Test
    fun 闰年2月有29天() {
        val february = TransactionTime.monthRangeUtc(CN, 2024, 2)
        assertEquals(29 * DAY_MILLIS, february.endExclusive - february.startInclusive)
        assertTrue(february.contains(atLocal(CN, 2024, 2, 29, 23, 59, 59, 999)))
        assertFalse(february.contains(atLocal(CN, 2024, 3, 1)))
    }

    @Test
    fun 平年2月有28天() {
        val february = TransactionTime.monthRangeUtc(CN, 2023, 2)
        assertEquals(28 * DAY_MILLIS, february.endExclusive - february.startInclusive)
        assertTrue(february.contains(atLocal(CN, 2023, 2, 28, 23, 59, 59, 999)))
        assertFalse(february.contains(atLocal(CN, 2023, 3, 1)))
    }

    // ---------------------------------------------------------------- A2 时区正确性

    @Test
    fun 时区正确性_UTC加8的3月1日凌晨必须归入3月() {
        // 交易本地时间：2024-03-01 00:30（Asia/Shanghai）；UTC 下仍是 2024-02-29 16:30
        val instant = atLocal(CN, 2024, 3, 1, 0, 30)

        val march = TransactionTime.monthRangeUtc(CN, 2024, 3)
        val february = TransactionTime.monthRangeUtc(CN, 2024, 2)

        assertTrue("本地视角：属于 3 月", march.contains(instant))
        assertFalse("本地视角：不属于 2 月（A2 的核心断言）", february.contains(instant))

        // 反证：若按 UTC 划分月，这条记录会被算进 2 月 —— 这就是 R1 的跨月错账
        val utcDate = Instant.ofEpochMilli(instant).atZone(ZoneOffset.UTC).toLocalDate().toString()
        assertEquals("2024-02-29", utcDate)
        assertFalse(
            "按 UTC 划分时它不在 3 月（证明本实现没有按 UTC 划分）",
            TransactionTime.monthRangeUtc("UTC", 2024, 3).contains(instant),
        )
    }

    @Test
    fun 时区不同则同一UTC时刻的归属月可以不同() {
        // 2024-03-01T00:30+08:00 = 2024-02-29T16:30Z：
        // 在 UTC 与 UTC-5 都属于 2 月，在 UTC+8 属于 3 月
        val instant = atLocal(CN, 2024, 3, 1, 0, 30)
        assertTrue(TransactionTime.monthRangeUtc(CN, 2024, 3).contains(instant))
        assertTrue(TransactionTime.monthRangeUtc("UTC", 2024, 2).contains(instant))
        assertTrue(TransactionTime.monthRangeUtc("America/New_York", 2024, 2).contains(instant))
    }

    // ---------------------------------------------------------------- A3 显式失败

    @Test
    fun 非法时区显式失败且不回退设备时区() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTime.monthRangeUtc("Mars/Olympus", 2024, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTime.monthRangeUtc("", 2024, 3)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTime.monthRangeUtc("   ", 2024, 3)
        }
    }

    @Test
    fun 非法月份显式失败() {
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTime.monthRangeUtc(CN, 2024, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TransactionTime.monthRangeUtc(CN, 2024, 13)
        }
    }
}
