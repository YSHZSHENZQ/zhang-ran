/**
 * 规范化测试（任务书 M1b §6 的 A2–A8）。
 *
 * 全部为纯 JVM 断言：resolver 用 fake 注入，时区用固定 `Asia/Shanghai` 注入，
 * 不依赖设备环境（`docs/09` 关于"隐式读环境导致测试不可复现"的教训）。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.RawCandidate
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeTest {

    private companion object {
        const val ZONE = "Asia/Shanghai"
        const val TIME = 1_791_610_182_000L

        fun rawOf(
            amountMinor: Long? = 2650L,
            currency: String? = null,
            direction: TransactionType? = TransactionType.EXPENSE,
            merchantRaw: String? = "全家便利店",
            noteRaw: String? = null,
            categoryNameRaw: String? = "餐饮/早餐",
            accountNameRaw: String? = "微信零钱",
            transactionTimeRaw: Long? = TIME,
            transactionTimeZoneRaw: String? = null,
            textClue: String? = "支付成功",
            upstreamResultUsable: Boolean = true,
            directionSemanticsTrusted: Boolean = true,
        ) = RawCandidate(
            packageName = "com.tencent.mm",
            sourceChannel = "wechat",
            captureSource = CaptureSource.NOTIFICATION,
            amountMinor = amountMinor,
            currency = currency,
            direction = direction,
            merchantRaw = merchantRaw,
            noteRaw = noteRaw,
            categoryNameRaw = categoryNameRaw,
            accountNameRaw = accountNameRaw,
            transactionTimeRaw = transactionTimeRaw,
            transactionTimeZoneRaw = transactionTimeZoneRaw,
            externalTransactionId = "ext-1",
            channelOrderId = "ch-1",
            capturedAt = TIME,
            textClue = textClue,
            upstreamResultUsable = upstreamResultUsable,
            directionSemanticsTrusted = directionSemanticsTrusted,
        )
    }

    private fun normalizeWith(
        categoryMap: Map<String, String> = emptyMap(),
        accountMap: Map<String, String> = mapOf("微信零钱" to "acc-1"),
    ) = Normalize(
        categoryResolver = CategoryResolver { categoryMap[it] },
        accountResolver = AccountResolver { accountMap[it] },
        defaultZoneId = ZONE,
    )

    // ------------------------------------------------------------- A2 商户规范化

    @Test
    fun `商户去首尾空白`() {
        val n = normalizeWith()
        assertEquals("全家便利店", n.normalize(rawOf(merchantRaw = "  全家便利店  ")).merchant)
    }

    @Test
    fun `商户合并连续空白`() {
        val n = normalizeWith()
        assertEquals("全家 便利店", n.normalize(rawOf(merchantRaw = "全家   便利店")).merchant)
        assertEquals("全家 便利店", n.normalize(rawOf(merchantRaw = "全家\u3000\u3000便利店")).merchant)
    }

    @Test
    fun `商户去除渠道前后缀`() {
        val n = normalizeWith()
        assertEquals("张三", n.normalize(rawOf(merchantRaw = "收款方：张三")).merchant)
        assertEquals("张三", n.normalize(rawOf(merchantRaw = "收款方: 张三")).merchant)
        assertEquals("张三", n.normalize(rawOf(merchantRaw = "付款方 张三")).merchant)
        assertEquals("便利店", n.normalize(rawOf(merchantRaw = "便利店-")).merchant)
    }

    @Test
    fun `B6 收款方加空格前缀被剥离`() {
        val n = normalizeWith()
        assertEquals("星巴克", n.normalize(rawOf(merchantRaw = "收款方 星巴克")).merchant)
    }

    @Test
    fun `B7 商户服务中心 保持原样`() {
        // `商户` 是通用词，可能是真实店名前缀：裁定单 §2.1 已把 `商户` 移出清单
        val n = normalizeWith()
        assertEquals("商户服务中心", n.normalize(rawOf(merchantRaw = "商户服务中心")).merchant)
    }

    @Test
    fun `B8 星巴克收款方店 保持原样`() {
        // 仅前缀匹配：角色标签出现在中间不得剥离
        val n = normalizeWith()
        assertEquals("星巴克收款方店", n.normalize(rawOf(merchantRaw = "星巴克收款方店")).merchant)
    }

    @Test
    fun `B9 仅标签 收款方 剥离后为空时保留原值`() {
        // 剥离后为空不得把商户变成 null（否则会被误判为 UNKNOWN_MERCHANT）
        val n = normalizeWith()
        assertEquals("收款方", n.normalize(rawOf(merchantRaw = "收款方")).merchant)
        assertEquals("收款方", n.normalize(rawOf(merchantRaw = "  收款方  ")).merchant)
    }

    @Test
    fun `B10 商户前后缀清单不含商户且标注三条约束`() {
        // 裁定单 §2.3 B10：清单 KDoc 说明"仅前缀/后缀、仅一次、剥离后不得为空"。
        // KDoc 无法在运行期断言，此处以"清单内容 + 行为"固化，KDoc 由代码评审保证。
        assertEquals(
            listOf("收款方", "付款方", "收款人", "付款人", "对方"),
            Normalize.MERCHANT_PREFIXES,
        )
        assertFalse(Normalize.MERCHANT_PREFIXES.contains("商户"))
    }

    @Test
    fun `商户前缀仅剥离一次`() {
        // 裁定单 §2.2 第三条约束：不连续剥离多个标签
        val n = normalizeWith()
        assertEquals("付款方星巴克", n.normalize(rawOf(merchantRaw = "收款方付款方星巴克")).merchant)
    }

    @Test
    fun `商户为空或纯空白时为 null`() {
        val n = normalizeWith()
        assertNull(n.normalize(rawOf(merchantRaw = null)).merchant)
        assertNull(n.normalize(rawOf(merchantRaw = "   ")).merchant)
        assertNull(n.normalize(rawOf(merchantRaw = "-")).merchant)
    }

    // ------------------------------------------------------------- A3 备注长度

    @Test
    fun `备注 499 与 500 字符不截断`() {
        val n = normalizeWith()
        val n499 = "字".repeat(499)
        val n500 = "字".repeat(500)
        assertEquals(n499, n.normalize(rawOf(noteRaw = n499)).note)
        assertEquals(n500, n.normalize(rawOf(noteRaw = n500)).note)
        assertFalse(n.normalize(rawOf(noteRaw = n500)).issues.contains(NormalizeIssue.NOTE_TRUNCATED))
    }

    @Test
    fun `备注 501 字符截断到 500 并记录问题`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(noteRaw = "字".repeat(501)))
        assertEquals(500, result.note!!.length)
        assertTrue(result.issues.contains(NormalizeIssue.NOTE_TRUNCATED))
    }

    @Test
    fun `备注按码点截断不劈开 emoji`() {
        val n = normalizeWith()
        val emoji = "😀" // 2 个 UTF-16 单元、1 个码点
        val text = emoji.repeat(501)
        val result = n.normalize(rawOf(noteRaw = text))
        assertEquals(500, result.note!!.codePointCount(0, result.note.length))
        assertFalse("不得截断出半个代理对", result.note.last().isHighSurrogate())
    }

    // ------------------------------------------------------------- A4 分类三级映射

    @Test
    fun `分类先命中完整名`() {
        val n = normalizeWith(categoryMap = mapOf("餐饮/早餐" to "cat-full", "早餐" to "cat-child"))
        val result = n.normalize(rawOf(categoryNameRaw = "餐饮/早餐"))
        assertEquals("cat-full", result.categoryId)
        assertEquals("餐饮/早餐", result.categoryNameHint)
    }

    @Test
    fun `分类完整名未命中且父子都命中时命中子名`() {
        val n = normalizeWith(
            categoryMap = mapOf("餐饮" to "cat-parent", "早餐" to "cat-child"),
        )
        val result = n.normalize(rawOf(categoryNameRaw = "餐饮/早餐"))
        assertEquals("cat-child", result.categoryId)
        assertEquals("早餐", result.categoryNameHint)
    }

    @Test
    fun `分类父或子任一段未命中时不拆开且保留完整名线索`() {
        val n = normalizeWith(categoryMap = emptyMap())
        val result = n.normalize(rawOf(categoryNameRaw = "餐饮/早餐"))
        assertNull(result.categoryId)
        assertEquals("餐饮/早餐", result.categoryNameHint)
    }

    @Test
    fun `无父子分隔符的分类按完整名匹配`() {
        val n = normalizeWith(categoryMap = mapOf("交通" to "cat-1"))
        val result = n.normalize(rawOf(categoryNameRaw = "交通"))
        assertEquals("cat-1", result.categoryId)
        assertEquals("交通", result.categoryNameHint)
    }

    @Test
    fun `仅斜杠与大于号作为父子分隔符`() {
        val n = normalizeWith(
            categoryMap = mapOf("餐饮" to "cat-parent", "早餐" to "cat-child"),
        )
        // 保留的分隔符：`/` 与 `>`
        assertEquals("cat-child", n.normalize(rawOf(categoryNameRaw = "餐饮/早餐")).categoryId)
        assertEquals("cat-child", n.normalize(rawOf(categoryNameRaw = "餐饮>早餐")).categoryId)
        // 已移除的分隔符：`：` / `:` / `-` —— 不拆分，保留完整名线索
        val colonFullWidth = n.normalize(rawOf(categoryNameRaw = "餐饮：早餐"))
        assertNull(colonFullWidth.categoryId)
        assertEquals("餐饮：早餐", colonFullWidth.categoryNameHint)
        val colonHalfWidth = n.normalize(rawOf(categoryNameRaw = "餐饮: 早餐"))
        assertNull(colonHalfWidth.categoryId)
        assertEquals("餐饮: 早餐", colonHalfWidth.categoryNameHint)
        val hyphen = n.normalize(rawOf(categoryNameRaw = "餐饮-早餐"))
        assertNull(hyphen.categoryId)
        assertEquals("餐饮-早餐", hyphen.categoryNameHint)
    }

    @Test
    fun `B1 话费-充值 不被拆分且完整名作为线索保留`() {
        // 即使父段与子段都能命中，`-` 已从分隔符集合移除，不得拆开（裁定单 §1.4 B1）
        val n = normalizeWith(
            categoryMap = mapOf("话费" to "cat-phone", "充值" to "cat-recharge"),
        )
        val result = n.normalize(rawOf(categoryNameRaw = "话费-充值"))
        assertNull("不得把 话费-充值 误拆成 话费 + 充值", result.categoryId)
        assertEquals("话费-充值", result.categoryNameHint)
    }

    @Test
    fun `B4 A大于B 双段都命中时拆开`() {
        val n = normalizeWith(categoryMap = mapOf("A" to "cat-a", "B" to "cat-b"))
        val result = n.normalize(rawOf(categoryNameRaw = "A>B"))
        assertEquals("cat-b", result.categoryId)
        assertEquals("B", result.categoryNameHint)
    }

    @Test
    fun `B5 分类分隔符常量已收窄并标注待 P0 校准`() {
        // 裁定单 §1.4 B5：常量存在，且源码 KDoc 标注"待 P0 真实载荷校准（docs/10 §8 #1）"。
        // KDoc 无法在运行期断言，此处以"常量可见 + 内容收窄"固化，KDoc 由代码评审保证。
        assertEquals(listOf("/", ">"), Normalize.CATEGORY_SEPARATORS)
        assertFalse(Normalize.CATEGORY_SEPARATORS.contains("-"))
        assertFalse(Normalize.CATEGORY_SEPARATORS.contains("："))
        assertFalse(Normalize.CATEGORY_SEPARATORS.contains(":"))
    }

    @Test
    fun `无分类名时 id 与线索均为 null`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(categoryNameRaw = null))
        assertNull(result.categoryId)
        assertNull(result.categoryNameHint)
    }

    // ------------------------------------------------------------- A5 账户映射

    @Test
    fun `账户命中时返回 id 并保留名字`() {
        val n = normalizeWith(accountMap = mapOf("微信零钱" to "acc-1"))
        val result = n.normalize(rawOf(accountNameRaw = "  微信零钱 "))
        assertEquals("acc-1", result.accountId)
        assertEquals("微信零钱", result.accountNameHint)
    }

    @Test
    fun `账户未命中时 id 为空且保留线索`() {
        val n = normalizeWith(accountMap = emptyMap())
        val result = n.normalize(rawOf(accountNameRaw = "招商银行储蓄卡"))
        assertNull(result.accountId)
        assertEquals("招商银行储蓄卡", result.accountNameHint)
    }

    // ------------------------------------------------------------- A6 时区

    @Test
    fun `上游无时区时补默认时区`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(transactionTimeZoneRaw = null))
        assertEquals(TIME, result.transactionTime)
        assertEquals(ZONE, result.transactionTimeZone)
    }

    @Test
    fun `上游提供合法时区时采用它`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(transactionTimeZoneRaw = "America/New_York"))
        assertEquals("America/New_York", result.transactionTimeZone)
    }

    @Test
    fun `上游时区非法时显式失败不静默回退`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(transactionTimeZoneRaw = "Mars/Olympus"))
        assertNull(result.transactionTime)
        assertNull(result.transactionTimeZone)
        assertTrue(result.issues.contains(NormalizeIssue.TIME_INVALID_ZONE))
        assertFalse("关键字段失败必须使上游结果不可用", result.upstreamResultUsable)
    }

    @Test
    fun `时间为 0 时视为非正数失败`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(transactionTimeRaw = 0L))
        assertNull(result.transactionTime)
        assertTrue(result.issues.contains(NormalizeIssue.TIME_NON_POSITIVE))
        assertFalse(result.upstreamResultUsable)
    }

    @Test
    fun `时间缺失时置不可用`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(transactionTimeRaw = null))
        assertTrue(result.issues.contains(NormalizeIssue.TIME_MISSING))
        assertFalse(result.upstreamResultUsable)
    }

    // ------------------------------------------------------------- A7 金额

    @Test
    fun `负金额被拒绝且置不可用`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(amountMinor = -1L))
        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(NormalizeIssue.AMOUNT_NEGATIVE))
        assertFalse(result.upstreamResultUsable)
    }

    @Test
    fun `超过上限的金额被拒绝`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(amountMinor = MAX_AMOUNT_MINOR + 1))
        assertNull(result.amountMinor)
        assertTrue(result.issues.contains(NormalizeIssue.AMOUNT_OVER_LIMIT))
        assertFalse(result.upstreamResultUsable)
    }

    @Test
    fun `合法金额原样保留`() {
        val n = normalizeWith()
        assertEquals(2650L, n.normalize(rawOf(amountMinor = 2650L)).amountMinor)
        assertEquals(0L, n.normalize(rawOf(amountMinor = 0L)).amountMinor)
    }

    // ------------------------------------------------------------- A8 关键字段缺失

    @Test
    fun `金额缺失时置不可用`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(amountMinor = null))
        assertNull(result.amountMinor)
        assertFalse(result.upstreamResultUsable)
    }

    @Test
    fun `上游已标记不可用时保持不可用`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf(upstreamResultUsable = false))
        assertFalse(result.upstreamResultUsable)
    }

    @Test
    fun `全部合法时上游结果可用`() {
        val n = normalizeWith(
            categoryMap = mapOf("餐饮/早餐" to "cat-1"),
            accountMap = mapOf("微信零钱" to "acc-1"),
        )
        assertTrue(n.normalize(rawOf()).upstreamResultUsable)
    }

    // ------------------------------------------------------------- 币种

    @Test
    fun `空币种视为 CNY 非空币种保留原文`() {
        val n = normalizeWith()
        assertEquals("CNY", n.normalize(rawOf(currency = null)).currency)
        assertEquals("CNY", n.normalize(rawOf(currency = "  ")).currency)
        assertEquals("CNY", n.normalize(rawOf(currency = "cny")).currency)
        assertEquals("usd", n.normalize(rawOf(currency = "usd")).currency)
    }

    // ------------------------------------------------------------- 杂项

    @Test
    fun `来源与订单字段被 trim 且空串归一为 null`() {
        val n = normalizeWith()
        val result = n.normalize(rawOf().copy(packageName = "  ", externalTransactionId = " ext-1 "))
        assertNull(result.packageName)
        assertEquals("ext-1", result.externalTransactionId)
    }

    @Test
    fun `规范化不修改入参对象`() {
        val n = normalizeWith()
        val raw = rawOf(merchantRaw = "  收款方：张三  ")
        n.normalize(raw)
        assertEquals("  收款方：张三  ", raw.merchantRaw)
    }
}
