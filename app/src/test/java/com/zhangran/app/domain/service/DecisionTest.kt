/**
 * Decision 决策服务测试（`docs/03` §16、`docs/08` §2.3）。
 *
 * 覆盖任务书 M1a 的验收判据：
 * - A1：`docs/08` §2.3 表格 10 行逐条；
 * - A3：Decision 是唯一产出状态处（Dedup/Adapter 不产出状态）；
 * - A4：多 reason 并存时全部保留；
 * - A5：被保护字段冲突时保留用户值、冲突记入证据、状态按规则判定；
 * - A6：阈值可注入（非 0.8 / 非 0.5 时行为随之变化）；
 * - A10：`PendingReason` 覆盖 `docs/02` §4 全部 10 项且无自造值。
 *
 * 说明：本测试只构造 `Candidate`（规范化后的输入），不模拟 Normalize / Confidence（M1b）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.DecisionResult
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.ProtectedField
import com.zhangran.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DecisionTest {

    private companion object {
        /** 固定基准时间（UTC epoch millis），避免测试依赖系统时钟。 */
        const val BASE_TIME: Long = 1_760_000_000_000L
    }

    /** 构造“各字段齐备且 confidence 达标”的基线候选；各用例只覆盖需要变化的字段。 */
    private fun candidate(
        currency: String = "CNY",
        transactionTime: Long = BASE_TIME,
        overallConfidence: Double = 0.9,
        amountMinor: Long? = 1_200L,
        amountConfidence: Double? = 0.95,
        direction: TransactionType? = TransactionType.EXPENSE,
        directionConfidence: Double? = 0.95,
        merchant: String? = "星巴克",
        merchantConfidence: Double? = 0.9,
        categoryId: String? = "cat-food",
        categoryConfidence: Double? = 0.9,
        accountId: String? = "acc-alipay",
        accountConfidence: Double? = 0.9,
        textClue: String? = null,
        packageName: String? = null,
        externalTransactionId: String? = null,
        channelOrderId: String? = null,
        capturedAt: Long? = null,
        upstreamResultUsable: Boolean = true,
        directionSemanticsTrusted: Boolean = true,
    ): Candidate = Candidate(
        currency = currency,
        transactionTime = transactionTime,
        overallConfidence = overallConfidence,
        amountMinor = amountMinor,
        amountConfidence = amountConfidence,
        direction = direction,
        directionConfidence = directionConfidence,
        merchant = merchant,
        merchantConfidence = merchantConfidence,
        categoryId = categoryId,
        categoryConfidence = categoryConfidence,
        accountId = accountId,
        accountConfidence = accountConfidence,
        textClue = textClue,
        packageName = packageName,
        externalTransactionId = externalTransactionId,
        channelOrderId = channelOrderId,
        capturedAt = capturedAt,
        upstreamResultUsable = upstreamResultUsable,
        directionSemanticsTrusted = directionSemanticsTrusted,
    )

    private fun pendingOf(result: DecisionResult): DecisionResult.Pending {
        assertTrue("期望 PENDING，实际 ${result::class.simpleName}", result is DecisionResult.Pending)
        return result as DecisionResult.Pending
    }

    private fun confirmedOf(result: DecisionResult): DecisionResult.Confirmed {
        assertTrue("期望 CONFIRMED，实际 ${result::class.simpleName}", result is DecisionResult.Confirmed)
        return result as DecisionResult.Confirmed
    }

    // ---------- A1：docs/08 §2.3 表格逐行 ----------

    @Test
    fun `全字段齐备且 overallConfidence 为 0_8 时 CONFIRMED`() {
        val result = Decision().decide(candidate(overallConfidence = 0.8))
        val confirmed = confirmedOf(result)
        assertEquals(candidate(overallConfidence = 0.8), confirmed.candidate)
        assertTrue(confirmed.conflicts.isEmpty())
    }

    @Test
    fun `overallConfidence 为 0_79 时 PENDING 且 LOW_CONFIDENCE`() {
        val pending = pendingOf(Decision().decide(candidate(overallConfidence = 0.79)))
        assertEquals(setOf(PendingReason.LOW_CONFIDENCE), pending.reasons)
    }

    @Test
    fun `amountMinor 缺失时 PENDING 且 AMOUNT_UNCERTAIN`() {
        val pending = pendingOf(
            Decision().decide(candidate(amountMinor = null, amountConfidence = null)),
        )
        assertEquals(setOf(PendingReason.AMOUNT_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `amountConfidence 为 0_49 时 PENDING 且 AMOUNT_UNCERTAIN`() {
        val pending = pendingOf(Decision().decide(candidate(amountConfidence = 0.49)))
        assertEquals(setOf(PendingReason.AMOUNT_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `direction 缺失时 PENDING 且 DIRECTION_UNCERTAIN`() {
        val pending = pendingOf(
            Decision().decide(candidate(direction = null, directionConfidence = null)),
        )
        assertEquals(setOf(PendingReason.DIRECTION_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `分类未确定时 PENDING 且 CATEGORY_UNCERTAIN`() {
        val pending = pendingOf(Decision().decide(candidate(categoryId = null, categoryConfidence = null)))
        assertEquals(setOf(PendingReason.CATEGORY_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `账户无法匹配时 PENDING 且 ACCOUNT_UNCERTAIN`() {
        val pending = pendingOf(Decision().decide(candidate(accountId = null, accountConfidence = null)))
        assertEquals(setOf(PendingReason.ACCOUNT_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `币种为 USD 时 PENDING 且 AMOUNT_UNCERTAIN`() {
        val pending = pendingOf(Decision().decide(candidate(currency = "USD")))
        assertEquals(setOf(PendingReason.AMOUNT_UNCERTAIN), pending.reasons)
    }

    @Test
    fun `无任何可用字段时 ClearlyInvalid 不创建交易`() {
        val empty = candidate(
            amountMinor = null,
            amountConfidence = null,
            direction = null,
            directionConfidence = null,
            merchant = null,
            merchantConfidence = null,
            categoryId = null,
            categoryConfidence = null,
            accountId = null,
            accountConfidence = null,
            textClue = null,
        )
        val result = Decision().decide(empty)
        assertTrue("期望 ClearlyInvalid，实际 ${result::class.simpleName}", result is DecisionResult.ClearlyInvalid)
        assertEquals(empty, (result as DecisionResult.ClearlyInvalid).candidate)
    }

    @Test
    fun `候选值与被保护字段冲突时保留用户值且冲突记入证据`() {
        val input = candidate(merchant = "星巴克")
        val result = Decision().decide(
            input,
            protectedFields = listOf(ProtectedField(fieldName = "merchant", userValue = "瑞幸")),
        )
        val confirmed = confirmedOf(result)
        // 状态按规则判定：其余字段达标 → CONFIRMED，冲突本身不改变状态。
        // 候选不得因冲突被改写。
        assertEquals("星巴克", confirmed.candidate.merchant)
        assertEquals(1, confirmed.conflicts.size)
        val conflict = confirmed.conflicts.single()
        assertEquals("merchant", conflict.fieldName)
        assertEquals("星巴克", conflict.candidateValue)
        // 用户值保留（后续装配以该值为准写入）。
        assertEquals("瑞幸", conflict.userValue)
    }

    // ---------- A4：多 reason 全部保留 ----------

    @Test
    fun `同时命中规则 3 与规则 8 时两个 reason 全部保留`() {
        val pending = pendingOf(
            Decision().decide(candidate(overallConfidence = 0.7, categoryId = null, categoryConfidence = null)),
        )
        assertEquals(2, pending.reasons.size)
        assertTrue(pending.reasons.contains(PendingReason.LOW_CONFIDENCE))
        assertTrue(pending.reasons.contains(PendingReason.CATEGORY_UNCERTAIN))
        // 顺序按规则顺序：规则 3 在规则 8 之前。
        assertEquals(
            listOf(PendingReason.LOW_CONFIDENCE, PendingReason.CATEGORY_UNCERTAIN),
            pending.reasons.toList(),
        )
    }

    @Test
    fun `关键字段与非关键字段同时不确定时 reason 全部保留`() {
        val pending = pendingOf(
            Decision().decide(
                candidate(
                    amountConfidence = 0.49,
                    merchant = null,
                    merchantConfidence = null,
                    accountId = null,
                    accountConfidence = null,
                ),
            ),
        )
        assertTrue(pending.reasons.containsAll(
            setOf(
                PendingReason.AMOUNT_UNCERTAIN,
                PendingReason.ACCOUNT_UNCERTAIN,
                PendingReason.UNKNOWN_MERCHANT,
            ),
        ))
    }

    // ---------- A5：被保护字段（更多冲突路径） ----------

    @Test
    fun `受保护金额与候选金额冲突时保留用户值`() {
        val result = Decision().decide(
            candidate(amountMinor = 1_200L),
            protectedFields = listOf(ProtectedField(fieldName = "amountMinor", userValue = "1500")),
        )
        val confirmed = confirmedOf(result)
        assertEquals(1_200L, confirmed.candidate.amountMinor)
        assertEquals("1200", confirmed.conflicts.single().candidateValue)
        assertEquals("1500", confirmed.conflicts.single().userValue)
    }

    @Test
    fun `候选值与受保护值相同时不产生冲突`() {
        val confirmed = confirmedOf(
            Decision().decide(
                candidate(merchant = "星巴克"),
                protectedFields = listOf(ProtectedField(fieldName = "merchant", userValue = "星巴克")),
            ),
        )
        assertTrue(confirmed.conflicts.isEmpty())
    }

    @Test
    fun `用户将受保护字段置空时候选值也不得覆盖`() {
        val confirmed = confirmedOf(
            Decision().decide(
                candidate(merchant = "星巴克"),
                protectedFields = listOf(ProtectedField(fieldName = "merchant", userValue = null)),
            ),
        )
        assertEquals("星巴克", confirmed.candidate.merchant)
        assertNull(confirmed.conflicts.single().userValue)
    }

    @Test
    fun `未知受保护字段名被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedField(fieldName = "rawText", userValue = "x")
        }
    }

    // ---------- 规则 4 / 5：去重结论 ----------

    @Test
    fun `判重结论为 Duplicate 时产出 DUPLICATE 并透传既有交易 id`() {
        val result = Decision().decide(
            candidate(),
            dedup = DedupResult.Duplicate(matchedTransactionId = "tx-existing"),
        )
        assertTrue("期望 DUPLICATE，实际 ${result::class.simpleName}", result is DecisionResult.Duplicate)
        assertEquals("tx-existing", (result as DecisionResult.Duplicate).decisionId)
    }

    @Test
    fun `判重结论为 PossibleDuplicate 时 PENDING 且 POSSIBLE_DUPLICATE`() {
        val pending = pendingOf(
            Decision().decide(
                candidate(),
                dedup = DedupResult.PossibleDuplicate(candidateTransactionIds = listOf("tx-1")),
            ),
        )
        assertEquals(setOf(PendingReason.POSSIBLE_DUPLICATE), pending.reasons)
    }

    @Test
    fun `前置条件不满足时规则 4 也让位给 Pending`() {
        val pending = pendingOf(
            Decision().decide(
                candidate(overallConfidence = 0.7),
                dedup = DedupResult.Duplicate(matchedTransactionId = "tx-existing"),
            ),
        )
        assertEquals(setOf(PendingReason.LOW_CONFIDENCE), pending.reasons)
    }

    @Test
    fun `商户为空时 PENDING 且 UNKNOWN_MERCHANT`() {
        val pending = pendingOf(Decision().decide(candidate(merchant = null, merchantConfidence = null)))
        assertEquals(setOf(PendingReason.UNKNOWN_MERCHANT), pending.reasons)
    }

    @Test
    fun `merchantConfidence 为 0_49 时 PENDING 且 UNKNOWN_MERCHANT`() {
        val pending = pendingOf(Decision().decide(candidate(merchantConfidence = 0.49)))
        assertEquals(setOf(PendingReason.UNKNOWN_MERCHANT), pending.reasons)
    }

    // ---------- 规则 10 / 11 ----------

    @Test
    fun `上游结果不可用时 PENDING 且 UNSUPPORTED_NOTIFICATION`() {
        val pending = pendingOf(Decision().decide(candidate(upstreamResultUsable = false)))
        assertEquals(setOf(PendingReason.UNSUPPORTED_NOTIFICATION), pending.reasons)
    }

    @Test
    fun `上游类型语义不可信时 PENDING 且 DIRECTION_UNCERTAIN`() {
        val pending = pendingOf(Decision().decide(candidate(directionSemanticsTrusted = false)))
        assertEquals(setOf(PendingReason.DIRECTION_UNCERTAIN), pending.reasons)
    }

    // ---------- 规则 1：来源包名支持列表 ----------

    @Test
    fun `来源包名不在支持列表时 ClearlyInvalid`() {
        val result = Decision(supportedPackages = setOf("com.example.supported"))
            .decide(candidate(packageName = "com.example.unknown"))
        assertTrue(result is DecisionResult.ClearlyInvalid)
    }

    @Test
    fun `来源包名在支持列表时按其余规则正常判定`() {
        val result = Decision(supportedPackages = setOf("com.example.supported"))
            .decide(candidate(packageName = "com.example.supported"))
        assertTrue(result is DecisionResult.Confirmed)
    }

    // ---------- A6：阈值可注入 ----------

    @Test
    fun `整体阈值可注入且行为随之变化`() {
        val input = candidate(overallConfidence = 0.85)
        assertTrue(Decision().decide(input) is DecisionResult.Confirmed)

        val strict = Decision(overallConfidenceThreshold = 0.9).decide(input)
        assertEquals(
            setOf(PendingReason.LOW_CONFIDENCE),
            pendingOf(strict).reasons,
        )
    }

    @Test
    fun `字段阈值可注入且行为随之变化`() {
        val input = candidate(amountConfidence = 0.55)
        assertTrue(Decision().decide(input) is DecisionResult.Confirmed)

        val strict = Decision(fieldConfidenceThreshold = 0.6).decide(input)
        assertEquals(setOf(PendingReason.AMOUNT_UNCERTAIN), pendingOf(strict).reasons)
    }

    @Test
    fun `非法阈值被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            Decision(overallConfidenceThreshold = 1.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Decision(fieldConfidenceThreshold = -0.1)
        }
    }

    // ---------- 领域约束 ----------

    @Test
    fun `TRANSFER 不要求分类因此不会产生 CATEGORY_UNCERTAIN`() {
        val transfer = candidate(
            direction = TransactionType.TRANSFER,
            categoryId = null,
            categoryConfidence = null,
        )
        assertTrue(Decision().decide(transfer) is DecisionResult.Confirmed)
    }

    @Test
    fun `负金额候选在构造期被拒绝`() {
        assertThrows(IllegalArgumentException::class.java) {
            candidate(amountMinor = -1L)
        }
    }

    // ---------- A10：PendingReason 取值完整性 ----------

    @Test
    fun `PendingReason 覆盖 02 第 4 节的 10 项且无自造值`() {
        val expected = setOf(
            "UNKNOWN_MERCHANT",
            "AMOUNT_UNCERTAIN",
            "DIRECTION_UNCERTAIN",
            "CATEGORY_UNCERTAIN",
            "ACCOUNT_UNCERTAIN",
            "POSSIBLE_DUPLICATE",
            "REFUND_UNCERTAIN",
            "TRANSFER_UNCERTAIN",
            "UNSUPPORTED_NOTIFICATION",
            "LOW_CONFIDENCE",
        )
        assertEquals(expected, PendingReason.entries.map { it.name }.toSet())
    }

    @Test
    fun `Pending 结果至少包含一个 reason`() {
        assertThrows(IllegalArgumentException::class.java) {
            DecisionResult.Pending(candidate = candidate(), reasons = emptySet())
        }
    }

    @Test
    fun `Decision 结果类型不含 TransactionState`() {
        // A3 的编译期证明：DecisionResult 的每个分支都不携带 TransactionState，
        // 状态映射只能由 M1c 装配层依据结果类型显式完成。
        val resultClasses = listOf(
            DecisionResult.Confirmed::class.java,
            DecisionResult.Pending::class.java,
            DecisionResult.Duplicate::class.java,
            DecisionResult.ClearlyInvalid::class.java,
        )
        val fieldTypes = resultClasses.flatMap { clazz -> clazz.declaredFields.map { it.type.name } }
        assertFalse(fieldTypes.any { it.contains("TransactionState") })
    }

    // ------------------------------------------------------------------
    // P-007：transactionTime 作为关键字段（docs/03 §5/§14/§16 规则 2）
    // ------------------------------------------------------------------

    @Test
    fun `P1 交易时间为零时进入 Pending 且原因含 UNSUPPORTED_NOTIFICATION`() {
        val result = Decision().decide(
            candidate(
                transactionTime = 0L,
                amountMinor = 1_200L,
                amountConfidence = 1.0,
                direction = TransactionType.EXPENSE,
                directionConfidence = 1.0,
                overallConfidence = 1.0,
            ),
        )
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.UNSUPPORTED_NOTIFICATION))
    }

    @Test
    fun `P2 时间不可用不得误伤金额与方向的置信度判定`() {
        val result = Decision().decide(
            candidate(
                transactionTime = 0L,
                amountMinor = 1_200L,
                amountConfidence = 1.0,
                direction = TransactionType.EXPENSE,
                directionConfidence = 1.0,
                overallConfidence = 1.0,
            ),
        )
        val reasons = (result as DecisionResult.Pending).reasons
        assertFalse("金额与方向都齐备，不应报 AMOUNT_UNCERTAIN", reasons.contains(PendingReason.AMOUNT_UNCERTAIN))
        assertFalse("金额与方向都齐备，不应报 DIRECTION_UNCERTAIN", reasons.contains(PendingReason.DIRECTION_UNCERTAIN))
        assertFalse("overall=1.0，不应报 LOW_CONFIDENCE", reasons.contains(PendingReason.LOW_CONFIDENCE))
    }

    @Test
    fun `P3 交易时间为负值时同 P1`() {
        val result = Decision().decide(
            candidate(
                transactionTime = -1L,
                amountConfidence = 1.0,
                directionConfidence = 1.0,
                overallConfidence = 1.0,
            ),
        )
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.UNSUPPORTED_NOTIFICATION))
    }

    @Test
    fun `P4 交易时间有效时仍可 Confirmed`() {
        val result = Decision().decide(
            candidate(
                transactionTime = BASE_TIME,
                amountConfidence = 1.0,
                directionConfidence = 1.0,
                overallConfidence = 1.0,
            ),
        )
        assertTrue("实际=${result::class.simpleName}", result is DecisionResult.Confirmed)
    }

    @Test
    fun `P5 时间不可用与分类未命中时两个 reason 都保留`() {
        val result = Decision().decide(
            candidate(
                transactionTime = 0L,
                amountConfidence = 1.0,
                directionConfidence = 1.0,
                overallConfidence = 1.0,
                categoryId = null,
            ),
        )
        val reasons = (result as DecisionResult.Pending).reasons
        assertTrue(reasons.contains(PendingReason.UNSUPPORTED_NOTIFICATION))
        assertTrue("多 reason 必须全部保留（03 §16 优先级说明）", reasons.contains(PendingReason.CATEGORY_UNCERTAIN))
    }
}
