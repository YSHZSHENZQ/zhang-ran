/**
 * 置信度推导与组装测试（任务书 M1b §6 的 A9–A11）。
 *
 * A11 是本套件的重点：把本层产出的 `Candidate` 直接喂给 `Decision.decide()`，
 * 证明两个阶段真的能联通（而不是各自单测通过、拼起来不成立）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.DecisionResult
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.RawCandidate
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceTest {

    private companion object {
        const val ZONE = "Asia/Shanghai"
        const val TIME = 1_791_610_182_000L

        fun rawOf(
            amountMinor: Long? = 2650L,
            direction: TransactionType? = TransactionType.EXPENSE,
            merchantRaw: String? = "全家便利店",
            categoryNameRaw: String? = "餐饮/早餐",
            accountNameRaw: String? = "微信零钱",
            currency: String? = null,
            transactionTimeRaw: Long? = TIME,
            textClue: String? = "支付成功",
            directionSemanticsTrusted: Boolean = true,
        ) = RawCandidate(
            packageName = "com.tencent.mm",
            sourceChannel = "wechat",
            captureSource = CaptureSource.NOTIFICATION,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchantRaw = merchantRaw,
            noteRaw = null,
            categoryNameRaw = categoryNameRaw,
            accountNameRaw = accountNameRaw,
            transactionTimeRaw = transactionTimeRaw,
            transactionTimeZoneRaw = null,
            externalTransactionId = null,
            channelOrderId = null,
            capturedAt = TIME,
            textClue = textClue,
            upstreamResultUsable = true,
            directionSemanticsTrusted = directionSemanticsTrusted,
        )
    }

    /** 默认解析器：分类与账户都命中（构造"全绿"基线）。 */
    private fun normalizer(
        categoryMap: Map<String, String> = mapOf("餐饮/早餐" to "cat-1"),
        accountMap: Map<String, String> = mapOf("微信零钱" to "acc-1"),
    ) = Normalize(
        categoryResolver = CategoryResolver { categoryMap[it] },
        accountResolver = AccountResolver { accountMap[it] },
        defaultZoneId = ZONE,
    )

    private fun derive(
        raw: RawCandidate,
        categoryMap: Map<String, String> = mapOf("餐饮/早餐" to "cat-1"),
        accountMap: Map<String, String> = mapOf("微信零钱" to "acc-1"),
    ): Candidate = Confidence.derive(
        normalizer(categoryMap, accountMap).normalize(raw),
    )

    // ------------------------------------------------------------- A9 字段置信度

    @Test
    fun `金额置信度 有值且大于零为一档 零为零档 缺失为空`() {
        assertEquals(1.0, derive(rawOf(amountMinor = 2650L)).amountConfidence!!, 0.0)
        assertEquals(0.0, derive(rawOf(amountMinor = 0L)).amountConfidence!!, 0.0)
        assertNull(derive(rawOf(amountMinor = null)).amountConfidence)
    }

    @Test
    fun `方向置信度 可信一档 不可信零点七 缺失为空`() {
        assertEquals(1.0, derive(rawOf(directionSemanticsTrusted = true)).directionConfidence!!, 0.0)
        assertEquals(0.7, derive(rawOf(directionSemanticsTrusted = false)).directionConfidence!!, 0.0)
        assertNull(derive(rawOf(direction = null)).directionConfidence)
    }

    @Test
    fun `商户置信度 长度达标一档 过短零点七 缺失为空`() {
        assertEquals(1.0, derive(rawOf(merchantRaw = "全家")).merchantConfidence!!, 0.0)
        assertEquals(0.7, derive(rawOf(merchantRaw = "全")).merchantConfidence!!, 0.0)
        assertNull(derive(rawOf(merchantRaw = null)).merchantConfidence)
    }

    @Test
    fun `分类置信度 命中一档 未命中零点七 无名为空`() {
        assertEquals(1.0, derive(rawOf()).categoryConfidence!!, 0.0)
        assertEquals(
            0.7,
            derive(rawOf(), categoryMap = emptyMap()).categoryConfidence!!,
            0.0,
        )
        assertNull(derive(rawOf(categoryNameRaw = null)).categoryConfidence)
    }

    @Test
    fun `账户置信度 命中一档 未命中零点七 无名为空`() {
        assertEquals(1.0, derive(rawOf()).accountConfidence!!, 0.0)
        assertEquals(0.7, derive(rawOf(), accountMap = emptyMap()).accountConfidence!!, 0.0)
        assertNull(derive(rawOf(accountNameRaw = null)).accountConfidence)
    }

    // ------------------------------------------------------------- A10 overall 语义

    @Test
    fun `整体置信度取关键字段最小值`() {
        // 金额 1.0、方向 1.0 → 1.0
        assertEquals(1.0, derive(rawOf()).overallConfidence, 0.0)
        // 金额 1.0、方向 0.7（语义不可信）→ 0.7
        assertEquals(0.7, derive(rawOf(directionSemanticsTrusted = false)).overallConfidence, 0.0)
        // 金额 0（0.0）、方向 1.0 → 0.0
        assertEquals(0.0, derive(rawOf(amountMinor = 0L)).overallConfidence, 0.0)
    }

    @Test
    fun `关键字段缺失时整体置信度为零`() {
        assertEquals(0.0, derive(rawOf(amountMinor = null)).overallConfidence, 0.0)
        assertEquals(0.0, derive(rawOf(direction = null)).overallConfidence, 0.0)
    }

    @Test
    fun `非关键字段不参与整体置信度`() {
        // 分类与账户都未命中（各自 0.7），但关键字段都是 1.0 → overall 仍 1.0
        val candidate = derive(rawOf(), categoryMap = emptyMap(), accountMap = emptyMap())
        assertEquals(1.0, candidate.overallConfidence, 0.0)
        assertEquals(0.7, candidate.categoryConfidence!!, 0.0)
        assertEquals(0.7, candidate.accountConfidence!!, 0.0)
    }

    // ------------------------------------------------------------- 组装约束

    @Test
    fun `组装的候选满足 amountMinor 非负约束`() {
        assertFalse(derive(rawOf(amountMinor = -1L)).amountMinor != null)
    }

    @Test
    fun `时间缺失时用零占位并标记上游结果不可用`() {
        val candidate = derive(rawOf(transactionTimeRaw = null))
        assertEquals(0L, candidate.transactionTime)
        assertFalse(
            "0L 只是占位：必须同时置 upstreamResultUsable=false，M1c 不得把它写入交易时间",
            candidate.upstreamResultUsable,
        )
    }

    @Test
    fun `方向与语义标记被透传到候选`() {
        val candidate = derive(rawOf(direction = TransactionType.REFUND, directionSemanticsTrusted = false))
        assertEquals(TransactionType.REFUND, candidate.direction)
        assertFalse(candidate.directionSemanticsTrusted)
    }

    // ------------------------------------------------------------- A11 与 Decision 联通

    @Test
    fun `A11 全绿候选被判定为 CONFIRMED（规则 12）`() {
        val result = Decision().decide(derive(rawOf()))
        assertTrue("实际=${result::class.simpleName}", result is DecisionResult.Confirmed)
    }

    @Test
    fun `A11 金额缺失命中规则 2 的 AMOUNT_UNCERTAIN`() {
        val result = Decision().decide(derive(rawOf(amountMinor = null)))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.AMOUNT_UNCERTAIN))
    }

    @Test
    fun `A11 语义不可信命中规则 3 的 LOW_CONFIDENCE 且同时给出方向原因`() {
        val result = Decision().decide(derive(rawOf(directionSemanticsTrusted = false)))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.LOW_CONFIDENCE))
        assertTrue(
            "语义不可信还应命中规则 11 的 DIRECTION_UNCERTAIN（Decision 采用 reason 累加）",
            pending.reasons.contains(PendingReason.DIRECTION_UNCERTAIN),
        )
    }

    @Test
    fun `A11 账户未命中命中规则 7 的 ACCOUNT_UNCERTAIN`() {
        val result = Decision().decide(derive(rawOf(), accountMap = emptyMap()))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.ACCOUNT_UNCERTAIN))
        assertFalse(
            "关键字段与分类都可靠时不应误加 LOW_CONFIDENCE",
            pending.reasons.contains(PendingReason.LOW_CONFIDENCE),
        )
    }

    @Test
    fun `A11 分类未命中命中规则 8 的 CATEGORY_UNCERTAIN`() {
        val result = Decision().decide(derive(rawOf(), categoryMap = emptyMap()))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.CATEGORY_UNCERTAIN))
        assertFalse(
            "关键字段可靠但分类不可靠时，overall 仍应 >= 0.8（规则 3 不应命中）",
            pending.reasons.contains(PendingReason.LOW_CONFIDENCE),
        )
    }

    @Test
    fun `A11 商户缺失命中规则 9 的 UNKNOWN_MERCHANT`() {
        val result = Decision().decide(derive(rawOf(merchantRaw = null)))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.UNKNOWN_MERCHANT))
    }

    @Test
    fun `A11 非 CNY 命中规则 6 的 AMOUNT_UNCERTAIN`() {
        val result = Decision().decide(derive(rawOf(currency = "usd")))
        val pending = result as DecisionResult.Pending
        assertTrue(pending.reasons.contains(PendingReason.AMOUNT_UNCERTAIN))
    }

    @Test
    fun `A11 产出状态只来自 Decision`() {
        // 本层返回的是 Candidate（纯数据），不含任何状态字段：
        // 这里以"Candidate 没有 state 属性可引用"的编译期事实配合运行期断言说明职责边界。
        val candidate = derive(rawOf())
        assertTrue(candidate.overallConfidence >= 0.0)
        assertTrue(Decision().decide(candidate) is DecisionResult.Confirmed)
    }
}
