/**
 * 交易时间与时区规范化测试。
 *
 * 覆盖任务书 T3 §6 的 A6（非法时区显式失败、不回退默认时区）与 A7（输出 UTC millis + 非空时区，
 * 跨日用例正确），依据 `docs/03` §5、`docs/10` §5、`docs/08` §17.2。
 *
 * 说明：纯 JVM 测试，使用固定时间戳常量，不依赖设备当前时间/时区。
 */
package com.zhangran.app.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTimeTest {

    /** 2026-10-10T05:29:42Z 的毫秒时间戳（固定常量，避免依赖当前时间）。 */
    private val fixedMillis = 1_791_610_182_000L

    private fun success(result: TransactionTimeResult): TransactionTimeResult.Success =
        result as? TransactionTimeResult.Success
            ?: throw AssertionError("期望成功，实际=$result")

    // ---------- A7：正常路径 ----------

    @Test
    fun `给定 zone 时输出毫秒与 IANA 时区 id`() {
        val ok = success(TransactionTime.fromEpochMillis(fixedMillis, "Asia/Shanghai"))
        assertEquals(fixedMillis, ok.epochMillis)
        assertEquals("Asia/Shanghai", ok.zoneId)
    }

    @Test
    fun `时区 id 带空白可容忍`() {
        val ok = success(TransactionTime.fromEpochMillis(fixedMillis, "  Asia/Shanghai  "))
        assertEquals("Asia/Shanghai", ok.zoneId)
    }

    @Test
    fun `UTC 时区可用`() {
        val ok = success(TransactionTime.fromEpochMillis(fixedMillis, "UTC"))
        assertEquals("UTC", ok.zoneId)
    }

    // ---------- A7：跨日归属 ----------

    @Test
    fun `跨日归属按传入时区计算`() {
        // 2026-10-10T16:30:00Z → 上海（UTC+8）为 10-11 00:30，UTC 仍为 10-10
        // 常量经 .NET DateTimeOffset 换算核对：1791649800000
        val millis = 1_791_649_800_000L
        val shanghai = success(TransactionTime.fromEpochMillis(millis, "Asia/Shanghai"))
        val utc = success(TransactionTime.fromEpochMillis(millis, "UTC"))

        assertEquals("2026-10-11", shanghai.localDateIn())
        assertEquals("2026-10-10", utc.localDateIn())
        // 同一时刻，仅时区不同 → 毫秒值必须相同
        assertEquals(shanghai.epochMillis, utc.epochMillis)
    }

    @Test
    fun `跨月边界按传入时区计算`() {
        // 2026-10-31T16:30:00Z → 上海为 11-01 00:30；常量经换算核对：1793464200000
        val millis = 1_793_464_200_000L
        val shanghai = success(TransactionTime.fromEpochMillis(millis, "Asia/Shanghai"))
        assertEquals("2026-11-01", shanghai.localDateIn())
        assertEquals("2026-10-31", success(TransactionTime.fromEpochMillis(millis, "UTC")).localDateIn())
    }

    // ---------- A6：非法时区必须显式失败 ----------

    @Test
    fun `非法时区 id 显式失败`() {
        listOf("Not/AZone", "Mars/Olympus", "12345", "Asia/Shanghai/Extra").forEach { zone ->
            val result = TransactionTime.fromEpochMillis(fixedMillis, zone)
            assertTrue("时区「$zone」必须失败，实际=$result", result is TransactionTimeResult.Failure)
            assertEquals(TimeIssue.INVALID_ZONE, (result as TransactionTimeResult.Failure).issue)
        }
    }

    @Test
    fun `时区缺失或空串显式失败且不回退默认时区`() {
        listOf(null, "", "   ").forEach { zone ->
            val result = TransactionTime.fromEpochMillis(fixedMillis, zone)
            assertTrue("时区「$zone」必须失败", result is TransactionTimeResult.Failure)
            assertEquals(TimeIssue.INVALID_ZONE, (result as TransactionTimeResult.Failure).issue)
        }
    }

    // ---------- 非法时间 ----------

    @Test
    fun `时间缺失显式失败`() {
        val result = TransactionTime.fromEpochMillis(null, "Asia/Shanghai")
        assertEquals(TimeIssue.MISSING, (result as TransactionTimeResult.Failure).issue)
    }

    @Test
    fun `非正数时间显式失败`() {
        listOf(0L, -1L, -1_000L).forEach { millis ->
            val result = TransactionTime.fromEpochMillis(millis, "Asia/Shanghai")
            assertEquals(
                "时间 $millis 必须失败",
                TimeIssue.NON_POSITIVE,
                (result as TransactionTimeResult.Failure).issue,
            )
        }
    }

    @Test
    fun `超出合理范围的时间显式失败`() {
        // 2101-01-01T00:00:00Z；常量经换算核对：4133980800000（上限为 2100-01-01）
        val tooLate = 4_133_980_800_000L
        val result = TransactionTime.fromEpochMillis(tooLate, "Asia/Shanghai")
        assertEquals(TimeIssue.OUT_OF_RANGE, (result as TransactionTimeResult.Failure).issue)
    }

    // ---------- 秒/毫秒容错 ----------

    @Test
    fun `秒级时间戳被规范化为毫秒`() {
        val seconds = fixedMillis / 1000
        val ok = success(TransactionTime.fromEpochMaybeSeconds(seconds, "Asia/Shanghai"))
        assertEquals(fixedMillis, ok.epochMillis)
    }

    @Test
    fun `毫秒级时间戳不被误乘`() {
        val ok = success(TransactionTime.fromEpochMaybeSeconds(fixedMillis, "Asia/Shanghai"))
        assertEquals(fixedMillis, ok.epochMillis)
    }

    @Test
    fun `秒级入口同样拒绝非法时区与缺失时间`() {
        assertEquals(
            TimeIssue.MISSING,
            (TransactionTime.fromEpochMaybeSeconds(null, "UTC") as TransactionTimeResult.Failure).issue,
        )
        assertEquals(
            TimeIssue.INVALID_ZONE,
            (TransactionTime.fromEpochMaybeSeconds(1_791_610_182L, "Bad/Zone") as TransactionTimeResult.Failure).issue,
        )
    }

    // ---------- 设备时区入口 ----------

    @Test
    fun `设备时区入口返回非空 id`() {
        val id = TransactionTime.deviceZoneId()
        assertTrue("设备时区 id 不得为空", id.isNotBlank())
        // 且该 id 必须能被 ZoneId 解析（避免返回非法字符串）
        assertTrue(
            "设备时区 id 必须可解析：$id",
            TransactionTime.fromEpochMillis(fixedMillis, id) is TransactionTimeResult.Success,
        )
    }
}
