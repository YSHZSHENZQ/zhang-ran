/**
 * Dedup 判重服务测试（`docs/03` §12、`docs/08` §2.5）。
 *
 * 覆盖任务书 M1a 的验收判据：
 * - A2：`docs/08` §2.5 表格 11 行逐条；
 * - A7：窗口为“交易时间差的绝对值 ≤ 180 秒”（+179s / +180s / +181s / −179s 边界）；
 * - A8：禁止跨 direction 判重（EXPENSE 与 REFUND 永不互判）；
 * - A9：7 天前的交易不参与判重；
 * - A11：纯 Kotlin/JVM（本测试在 `test` 源集运行即为证明）；
 * - §12.1 捕获级去抖（不产出判重结论）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedupTest {

    private companion object {
        const val SECOND: Long = 1_000L
        const val MINUTE: Long = 60L * SECOND
        const val HOUR: Long = 60L * MINUTE
        const val DAY: Long = 24L * HOUR

        /** 固定基准时间（UTC epoch millis），避免测试依赖系统时钟。 */
        const val BASE_TIME: Long = 1_760_000_000_000L
    }

    /** 构造“金额、方向、商户、账户均可用”的基线候选；各用例只改需要变化的字段。 */
    private fun candidate(
        amountMinor: Long? = 1_200L,
        direction: TransactionType? = TransactionType.EXPENSE,
        merchant: String? = "星巴克",
        accountId: String? = "acc-alipay",
        transactionTime: Long = BASE_TIME,
        externalTransactionId: String? = null,
        channelOrderId: String? = null,
        capturedAt: Long? = null,
    ): Candidate = Candidate(
        currency = "CNY",
        transactionTime = transactionTime,
        overallConfidence = 0.9,
        amountMinor = amountMinor,
        amountConfidence = 0.95,
        direction = direction,
        directionConfidence = 0.95,
        merchant = merchant,
        merchantConfidence = 0.9,
        categoryId = "cat-food",
        categoryConfidence = 0.9,
        accountId = accountId,
        accountConfidence = 0.9,
        externalTransactionId = externalTransactionId,
        channelOrderId = channelOrderId,
        capturedAt = capturedAt,
    )

    private fun existing(
        id: String = "tx-existing",
        amountMinor: Long = 1_200L,
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String? = "星巴克",
        accountId: String? = "acc-alipay",
        transactionTime: Long = BASE_TIME,
        state: TransactionState = TransactionState.CONFIRMED,
        externalTransactionId: String? = null,
        channelOrderId: String? = null,
        capturedAt: Long? = null,
    ): ExistingTransaction = ExistingTransaction(
        id = id,
        type = type,
        amountMinor = amountMinor,
        transactionTime = transactionTime,
        state = state,
        merchant = merchant,
        accountId = accountId,
        externalTransactionId = externalTransactionId,
        channelOrderId = channelOrderId,
        capturedAt = capturedAt,
    )

    private fun duplicateId(result: DedupResult): String {
        assertTrue("期望 Duplicate，实际 ${result::class.simpleName}", result is DedupResult.Duplicate)
        return (result as DedupResult.Duplicate).matchedTransactionId
    }

    private fun possibleIds(result: DedupResult): List<String> {
        assertTrue(
            "期望 PossibleDuplicate，实际 ${result::class.simpleName}",
            result is DedupResult.PossibleDuplicate,
        )
        return (result as DedupResult.PossibleDuplicate).candidateTransactionIds
    }

    // ---------- A2：docs/08 §2.5 表格逐行 ----------

    @Test
    fun `externalTransactionId 相同判为 Duplicate`() {
        val result = Dedup.check(
            candidate(externalTransactionId = "EXT-1", transactionTime = BASE_TIME),
            listOf(existing(externalTransactionId = "EXT-1", transactionTime = BASE_TIME - 2 * DAY)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `渠道单号相同且捕获时间差在 24 小时内判为 Duplicate`() {
        val result = Dedup.check(
            candidate(
                channelOrderId = "ORDER-1",
                capturedAt = BASE_TIME,
                // 交易时间差 1 小时：语义兜底不会命中，确保命中的是渠道单号规则。
                transactionTime = BASE_TIME,
            ),
            listOf(
                existing(
                    channelOrderId = "ORDER-1",
                    capturedAt = BASE_TIME + 2 * HOUR,
                    transactionTime = BASE_TIME - 1 * HOUR,
                ),
            ),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `渠道单号相同但捕获时间差超过 24 小时不判重`() {
        val result = Dedup.check(
            candidate(channelOrderId = "ORDER-1", capturedAt = BASE_TIME),
            listOf(
                existing(
                    channelOrderId = "ORDER-1",
                    capturedAt = BASE_TIME + 24 * HOUR + 1,
                    transactionTime = BASE_TIME - 1 * HOUR,
                ),
            ),
        )
        assertEquals(DedupResult.None, result)
    }

    @Test
    fun `金额相同时间差不超过 180 秒且规范化商户相同判为 Duplicate`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME + 180 * SECOND)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `金额相同时间差不超过 180 秒且方向与账户相同判为 Duplicate`() {
        val result = Dedup.check(
            candidate(merchant = "星巴克", transactionTime = BASE_TIME),
            listOf(
                existing(
                    merchant = "瑞幸",
                    accountId = "acc-alipay",
                    transactionTime = BASE_TIME - 180 * SECOND,
                ),
            ),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `金额相同时间差不超过 180 秒但商户与账户都不同时为 POSSIBLE_DUPLICATE`() {
        val result = Dedup.check(
            candidate(merchant = "星巴克", accountId = "acc-alipay"),
            listOf(existing(merchant = "瑞幸", accountId = "acc-wechat")),
        )
        assertEquals(listOf("tx-existing"), possibleIds(result))
    }

    @Test
    fun `同商户同金额但时间差 10 分钟不判重`() {
        val result = Dedup.check(
            candidate(),
            listOf(existing(transactionTime = BASE_TIME + 10 * MINUTE)),
        )
        assertEquals(DedupResult.None, result)
    }

    @Test
    fun `金额相同商户不同且时间相近时不判为 DUPLICATE`() {
        // `docs/08` §2.5 此行写“不判重”；结合 `docs/03` §12.2
        // “任一条件缺失 → 不判 Duplicate，标记 POSSIBLE_DUPLICATE 并进入 Pending”，
        // 语义为“不判为重复”，即不得产出 Duplicate，而应标记疑似。
        val result = Dedup.check(
            candidate(merchant = "星巴克", accountId = "acc-alipay"),
            listOf(existing(merchant = "瑞幸", accountId = "acc-wechat", transactionTime = BASE_TIME + 100 * SECOND)),
        )
        assertFalse(result is DedupResult.Duplicate)
        assertEquals(listOf("tx-existing"), possibleIds(result))
    }

    @Test
    fun `时间相近但金额不同不判重`() {
        val result = Dedup.check(
            candidate(amountMinor = 1_200L),
            listOf(existing(amountMinor = 800L, transactionTime = BASE_TIME + 100 * SECOND)),
        )
        assertEquals(DedupResult.None, result)
    }

    @Test
    fun `EXPENSE 与 REFUND 之间永不互相判重`() {
        val result = Dedup.check(
            candidate(direction = TransactionType.EXPENSE),
            listOf(existing(type = TransactionType.REFUND)),
        )
        assertEquals(DedupResult.None, result)

        val reverse = Dedup.check(
            candidate(direction = TransactionType.REFUND),
            listOf(existing(type = TransactionType.EXPENSE)),
        )
        assertEquals(DedupResult.None, reverse)
    }

    @Test
    fun `与 7 天前的交易比较不参与判重`() {
        // 用 externalTransactionId（唯一可跨时间命中的规则）隔离 7 天窗口：
        // 正好 7 天前的既有交易必须被排除，否则会命中规则 1。
        val result = Dedup.check(
            candidate(externalTransactionId = "EXT-1", transactionTime = BASE_TIME),
            listOf(
                existing(
                    externalTransactionId = "EXT-1",
                    transactionTime = BASE_TIME - 7 * DAY,
                ),
            ),
        )
        assertEquals(DedupResult.None, result)
    }

    // ---------- A7：±180 秒边界 ----------

    @Test
    fun `交易时间差为加 179 秒时判为 Duplicate`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME + 179 * SECOND)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `交易时间差为减 179 秒时判为 Duplicate`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME - 179 * SECOND)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `交易时间差为正好 180 秒时判为 Duplicate`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME + 180 * SECOND)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `交易时间差为加 181 秒时不判重`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME + 181 * SECOND)),
        )
        assertEquals(DedupResult.None, result)
    }

    @Test
    fun `交易时间差为减 181 秒时不判重`() {
        val result = Dedup.check(
            candidate(transactionTime = BASE_TIME),
            listOf(existing(transactionTime = BASE_TIME - 181 * SECOND)),
        )
        assertEquals(DedupResult.None, result)
    }

    // ---------- A9：7 天窗口 ----------

    @Test
    fun `差 1 毫秒满 7 天的交易仍参与判重`() {
        val result = Dedup.check(
            candidate(externalTransactionId = "EXT-1", transactionTime = BASE_TIME),
            listOf(
                existing(
                    externalTransactionId = "EXT-1",
                    transactionTime = BASE_TIME - 7 * DAY + 1,
                ),
            ),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `超过 7 天的交易不参与判重`() {
        val result = Dedup.check(
            candidate(externalTransactionId = "EXT-1", transactionTime = BASE_TIME),
            listOf(
                existing(
                    externalTransactionId = "EXT-1",
                    transactionTime = BASE_TIME - 7 * DAY - 1,
                ),
            ),
        )
        assertEquals(DedupResult.None, result)
    }

    // ---------- 状态过滤 ----------

    @Test
    fun `PENDING 与 DUPLICATE 状态的既有交易参与判重`() {
        assertEquals(
            "tx-pending",
            duplicateId(
                Dedup.check(
                    candidate(),
                    listOf(existing(id = "tx-pending", state = TransactionState.PENDING)),
                ),
            ),
        )
        assertEquals(
            "tx-duplicate",
            duplicateId(
                Dedup.check(
                    candidate(),
                    listOf(existing(id = "tx-duplicate", state = TransactionState.DUPLICATE)),
                ),
            ),
        )
    }

    @Test
    fun `IGNORED 与 DELETED 状态的既有交易不参与判重`() {
        assertEquals(
            DedupResult.None,
            Dedup.check(candidate(), listOf(existing(state = TransactionState.IGNORED))),
        )
        assertEquals(
            DedupResult.None,
            Dedup.check(candidate(), listOf(existing(state = TransactionState.DELETED))),
        )
    }

    // ---------- 语义条件缺失 ----------

    @Test
    fun `金额相同时间相近但商户与账户都缺失时为 POSSIBLE_DUPLICATE`() {
        val result = Dedup.check(
            candidate(merchant = null, accountId = null),
            listOf(existing(merchant = null, accountId = null, transactionTime = BASE_TIME + 60 * SECOND)),
        )
        assertEquals(listOf("tx-existing"), possibleIds(result))
    }

    @Test
    fun `渠道单号相同但捕获时间缺失时为 POSSIBLE_DUPLICATE`() {
        val result = Dedup.check(
            candidate(channelOrderId = "ORDER-1", capturedAt = null),
            listOf(
                existing(
                    channelOrderId = "ORDER-1",
                    capturedAt = BASE_TIME,
                    transactionTime = BASE_TIME - 1 * HOUR,
                ),
            ),
        )
        assertEquals(listOf("tx-existing"), possibleIds(result))
    }

    @Test
    fun `商户仅大小写或首尾空白不同视为规范化后相同`() {
        val result = Dedup.check(
            candidate(merchant = " 星巴克 "),
            listOf(existing(merchant = "星巴克", accountId = null, transactionTime = BASE_TIME + 60 * SECOND)),
        )
        assertEquals("tx-existing", duplicateId(result))
    }

    @Test
    fun `金额缺失时不判重`() {
        val result = Dedup.check(
            candidate(amountMinor = null),
            listOf(existing(transactionTime = BASE_TIME + 60 * SECOND)),
        )
        assertEquals(DedupResult.None, result)
    }

    @Test
    fun `多个疑似命中时全部返回`() {
        val result = Dedup.check(
            candidate(merchant = "星巴克", accountId = "acc-alipay"),
            listOf(
                existing(id = "tx-1", merchant = "瑞幸", accountId = "acc-wechat", transactionTime = BASE_TIME + 30 * SECOND),
                existing(id = "tx-2", merchant = "瑞幸", accountId = "acc-wechat", transactionTime = BASE_TIME - 30 * SECOND),
            ),
        )
        assertEquals(listOf("tx-1", "tx-2"), possibleIds(result))
    }

    // ---------- §12.1 捕获级去抖（不产出判重结论） ----------

    @Test
    fun `包名通知 id 与投递时间全等视为同一事件`() {
        val result = Dedup.checkCaptureDebounce(
            CaptureIdentity(packageName = "com.tencent.mm", notificationId = "n-1", postTime = BASE_TIME),
            CaptureIdentity(packageName = "com.tencent.mm", notificationId = "n-1", postTime = BASE_TIME),
        )
        assertEquals(CaptureDebounceResult.SameEvent, result)
    }

    @Test
    fun `包名通知 id 或投递时间任一不同视为不同事件`() {
        val base = CaptureIdentity(packageName = "com.tencent.mm", notificationId = "n-1", postTime = BASE_TIME)
        assertEquals(
            CaptureDebounceResult.DifferentEvent,
            Dedup.checkCaptureDebounce(base, base.copy(postTime = BASE_TIME + 1)),
        )
        assertEquals(
            CaptureDebounceResult.DifferentEvent,
            Dedup.checkCaptureDebounce(base, base.copy(notificationId = "n-2")),
        )
        assertEquals(
            CaptureDebounceResult.DifferentEvent,
            Dedup.checkCaptureDebounce(base, base.copy(packageName = "com.eg.android.AlipayGphone")),
        )
    }

    @Test
    fun `identity 缺失时商户金额相同且时间差在 180 秒内为疑似同一次投递`() {
        val result = Dedup.checkCaptureDebounce(
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME),
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME - 179 * SECOND),
        )
        assertEquals(CaptureDebounceResult.PossibleSameEvent, result)
    }

    @Test
    fun `identity 缺失时商户金额相同但时间差超过 180 秒视为不同事件`() {
        val result = Dedup.checkCaptureDebounce(
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME),
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME - 181 * SECOND),
        )
        assertEquals(CaptureDebounceResult.DifferentEvent, result)
    }

    @Test
    fun `identity 缺失且时间不可判定时仍为疑似同一次投递`() {
        val result = Dedup.checkCaptureDebounce(
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = null),
            CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = null),
        )
        assertEquals(CaptureDebounceResult.PossibleSameEvent, result)
    }

    @Test
    fun `identity 缺失时商户或金额不同视为不同事件`() {
        assertEquals(
            CaptureDebounceResult.DifferentEvent,
            Dedup.checkCaptureDebounce(
                CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME),
                CaptureIdentity(merchant = "瑞幸", amountMinor = 1_200L, transactionTime = BASE_TIME),
            ),
        )
        assertEquals(
            CaptureDebounceResult.DifferentEvent,
            Dedup.checkCaptureDebounce(
                CaptureIdentity(merchant = "星巴克", amountMinor = 1_200L, transactionTime = BASE_TIME),
                CaptureIdentity(merchant = "星巴克", amountMinor = 800L, transactionTime = BASE_TIME),
            ),
        )
    }
}
