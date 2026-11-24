/**
 * M6-3：真机结果页的商户、金额、方向，以及不是结果页的页面。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySnapshotParserTest {

    @Test
    fun F_WX_金额商户方向为支出且不把更多信息当商户() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatDepthSeek())
        assertEquals(1_000L, parsed?.amountMinor)
        assertEquals("杭州深度求索", parsed?.merchantRaw)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertTrue(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatDepthSeek()))
    }

    @Test
    fun F_ALI_金额商户方向为支出且不把回首页完成我的当商户() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.alipayFufu())
        assertEquals(12_999L, parsed?.amountMinor)
        assertEquals("富富", parsed?.merchantRaw)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertFalse(parsed?.merchantRaw.orEmpty().contains("回首页"))
        assertFalse(parsed?.merchantRaw.orEmpty().contains("完成"))
        assertFalse(parsed?.merchantRaw.orEmpty().contains("我的"))
    }

    @Test
    fun M6_2_星巴克仍是支出3850() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatPaySuccess())
        assertEquals(3_850L, parsed?.amountMinor)
        assertEquals("星巴克咖啡", parsed?.merchantRaw)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
    }

    @Test
    fun 找不到商户时留空不拿按钮充数() {
        val page = AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 20,
            capturedAt = AccessibilityFixtures.CAPTURED_AT,
            nodes = listOf(
                SnapshotNode("支付成功", null, null, null, 1),
                SnapshotNode("¥10.00", null, null, null, 2),
                SnapshotNode("完成", null, null, null, 3),
                SnapshotNode("回首页", null, null, null, 4),
                SnapshotNode("我的", null, null, null, 5),
                SnapshotNode("更多信息", null, null, null, 6),
            ),
            truncated = false,
            textChars = 20,
        )
        val parsed = AccessibilitySnapshotParser.parse(page)
        assertEquals(1_000L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertNull(parsed?.merchantRaw)
    }

    @Test
    fun 退款成功解析为退款且不和支出混在一起() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatRefund())
        assertEquals(1_000L, parsed?.amountMinor)
        assertEquals("杭州深度求索", parsed?.merchantRaw)
        assertEquals(TransactionType.REFUND, parsed?.direction)
        val mixed = AccessibilityFixtures.wechatRefund().copy(
            nodes = AccessibilityFixtures.wechatRefund().nodes +
                SnapshotNode("支付成功", null, null, null, 4),
        )
        assertNull(AccessibilitySnapshotParser.parse(mixed))
    }

    @Test
    fun 收支用语同时出现时不默认支出() {
        val page = AccessibilityFixtures.wechatPaySuccess().copy(
            nodes = AccessibilityFixtures.wechatPaySuccess().nodes +
                SnapshotNode("收款成功", null, null, null, 4),
        )
        assertNull(AccessibilitySnapshotParser.parse(page))
        assertFalse(AccessibilitySnapshotParser.shouldForward(page))
    }

    @Test
    fun 列表营销账单和只有导航词的金额页都不是候选() {
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatPayList()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatPayListWithStatus()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.marketingSentence()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.chromeAmountNotResult()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatBillDetail()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.alipayTradeSuccessBill()))
    }

    @Test
    fun 余额宝结果页金额2510支出且商户为空() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.alipayYuebao())
        assertEquals(2_510L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertNull(parsed?.merchantRaw)
        assertNotEquals(1_000L, parsed?.amountMinor)
        assertNotEquals(200L, parsed?.amountMinor)
        assertNotEquals(1_200L, parsed?.amountMinor)
    }

    @Test
    fun 货币符号和数字拆开时仍是2510() {
        val parsed = AccessibilitySnapshotParser.parse(AccessibilityFixtures.alipayYuebaoSplit())
        assertEquals(2_510L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertNull(parsed?.merchantRaw)
    }

    @Test
    fun 订单金额和交易方式不能当商户() {
        val page = AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.ALIPAY_PACKAGE,
            windowId = 21,
            capturedAt = AccessibilityFixtures.YUEBAO_CAPTURED_AT,
            nodes = listOf(
                SnapshotNode("支付成功", null, null, null, 1),
                SnapshotNode("订单金额", null, null, null, 2),
                SnapshotNode("交易方式", null, null, null, 3),
                SnapshotNode("¥25.10", null, null, null, 4),
                SnapshotNode("完成", null, null, null, 5),
            ),
            truncated = false,
            textChars = 20,
            rootPackageName = AccessibilityTargets.ALIPAY_PACKAGE,
        )
        val parsed = AccessibilitySnapshotParser.parse(page)
        assertEquals(2_510L, parsed?.amountMinor)
        assertNull(parsed?.merchantRaw)
        assertNotEquals("订单金额", parsed?.merchantRaw)
        assertNotEquals("交易方式", parsed?.merchantRaw)
    }

    @Test
    fun 事件包名是支付宝但树是账然时不解析() {
        val page = AccessibilityFixtures.wrongWindowHome()
        assertNull(AccessibilitySnapshotParser.parse(page))
        assertFalse(AccessibilitySnapshotParser.shouldForward(page))
    }

    @Test
    fun 更多功能是按钮不能当商户() {
        val parsed = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("更多功能"),
                    node("支付成功"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        assertEquals(390L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertNull(parsed?.merchantRaw)
    }

    @Test
    fun 更多功能旁边的标签商户和下一节点仍然是商户() {
        val labeled = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("更多功能"),
                    node("支付成功"),
                    node("商户：瑞幸咖啡"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        val neighbor = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("支付成功"),
                    node("商户"),
                    node("瑞幸咖啡"),
                    node("更多功能"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        val fallback = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("更多功能"),
                    node("支付成功"),
                    node("瑞幸咖啡"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        assertEquals("瑞幸咖啡", labeled?.merchantRaw)
        assertEquals("瑞幸咖啡", neighbor?.merchantRaw)
        assertEquals("瑞幸咖啡", fallback?.merchantRaw)
        assertEquals(390L, fallback?.amountMinor)
    }

    @Test
    fun 结果页能解析交易单号商户单号和订单号且账单详情仍不是结果页() {
        val trade = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("支付成功"),
                    node("¥22.50"),
                    node("完成"),
                    node("订单号"),
                    node("202611180001"),
                    node("交易单号"),
                    node("202611180002"),
                ),
            ),
        )
        val merchantOrder = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("支付成功"),
                    node("商户单号：202611180003"),
                    node("¥12.90"),
                    node("完成"),
                ),
            ),
        )
        assertEquals("202611180002", trade?.externalTransactionId)
        assertEquals(2_250L, trade?.amountMinor)
        assertNull(trade?.merchantRaw)
        assertEquals("202611180003", merchantOrder?.externalTransactionId)
        assertNull(merchantOrder?.merchantRaw)
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatBillDetail()))
    }

    @Test
    fun 支付时间按上海时区解析没有支付时间则为空() {
        val parsed = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("支付成功"),
                    node("瑞幸咖啡"),
                    node("¥3.90"),
                    node("支付时间"),
                    node("2026-11-19 08:10:00"),
                    node("完成"),
                ),
            ),
        )
        val missing = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    node("支付成功"),
                    node("瑞幸咖啡"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        assertEquals(1_795_047_000_000L, parsed?.paymentTimeMillis)
        assertEquals("瑞幸咖啡", parsed?.merchantRaw)
        assertNull(missing?.paymentTimeMillis)
        assertNull(missing?.externalTransactionId)
    }

    @Test
    fun 可点击控件的文字和说明不能当商户() {
        val button = SnapshotNode(
            text = "菜单",
            contentDescription = "更多功能",
            viewId = "com.tencent.mm:id/jga",
            className = "android.widget.ImageView",
            depth = 1,
            clickable = true,
        )
        val onlyButton = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    button,
                    node("支付成功"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        val withMerchant = AccessibilitySnapshotParser.parse(
            resultPage(
                nodes = listOf(
                    button,
                    node("支付成功"),
                    node("瑞幸咖啡"),
                    node("¥3.90"),
                    node("完成"),
                ),
            ),
        )
        assertEquals(390L, onlyButton?.amountMinor)
        assertNull(onlyButton?.merchantRaw)
        assertEquals("瑞幸咖啡", withMerchant?.merchantRaw)
        assertNotEquals("菜单", withMerchant?.merchantRaw)
        assertNotEquals("更多功能", withMerchant?.merchantRaw)
    }

    @Test
    fun 微信没有结果用语但有完成和一笔金额时记为支出() {
        val page = AccessibilityFixtures.wechatSuccess(includeKeyword = false)
        val parsed = AccessibilitySnapshotParser.parse(page)
        assertEquals(800L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertEquals(AccessibilityFixtures.WX_SUCCESS_MERCHANT, parsed?.merchantRaw)
        assertNotEquals("完成", parsed?.merchantRaw)
        assertNotEquals(AccessibilityFixtures.WX_SUCCESS_PROMO, parsed?.merchantRaw)
        assertNull(parsed?.externalTransactionId)
        assertNull(parsed?.paymentTimeMillis)
        assertTrue(AccessibilitySnapshotParser.shouldForward(page))
        val joined = page.nodes.mapNotNull { it.text }
        assertFalse(joined.any { it in listOf("支付成功", "付款成功", "支付完成", "已支付") })
    }

    @Test
    fun 微信有结果用语的成功页仍是支出且商户是化名() {
        val parsed = AccessibilitySnapshotParser.parse(
            AccessibilityFixtures.wechatSuccess(includeKeyword = true),
        )
        assertEquals(800L, parsed?.amountMinor)
        assertEquals(TransactionType.EXPENSE, parsed?.direction)
        assertEquals(AccessibilityFixtures.WX_SUCCESS_MERCHANT, parsed?.merchantRaw)
        assertNotEquals("完成", parsed?.merchantRaw)
        assertNotEquals(AccessibilityFixtures.WX_SUCCESS_PROMO, parsed?.merchantRaw)
    }

    @Test
    fun 微信确认支付页和支付宝缺结果用语都不进候选() {
        assertNull(AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatConfirmPay()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatConfirmPay()))
        val alipay = AccessibilityFixtures.wechatSuccess(includeKeyword = false).copy(
            packageName = AccessibilityTargets.ALIPAY_PACKAGE,
            rootPackageName = AccessibilityTargets.ALIPAY_PACKAGE,
        )
        assertNull(AccessibilitySnapshotParser.parse(alipay))
        val twoAmounts = AccessibilityFixtures.wechatSuccess(includeKeyword = false).let { page ->
            page.copy(nodes = page.nodes + SnapshotNode("¥1.00", null, null, null, 7))
        }
        assertNull(AccessibilitySnapshotParser.parse(twoAmounts))
    }

    @Test
    fun 微信会话列表不是付款页且已有成功页仍能解析() {
        assertNull(AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatConversationList()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatConversationList()))
        assertEquals("星巴克咖啡", AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatPaySuccess())?.merchantRaw)
        assertEquals(3_850L, AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatPaySuccess())?.amountMinor)
        assertEquals("杭州深度求索", AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatDepthSeek())?.merchantRaw)
        assertEquals(1_000L, AccessibilitySnapshotParser.parse(AccessibilityFixtures.wechatDepthSeek())?.amountMinor)
        assertEquals("富富", AccessibilitySnapshotParser.parse(AccessibilityFixtures.alipayFufu())?.merchantRaw)
    }

}

private fun resultPage(nodes: List<SnapshotNode>) = AccessibilityPageSnapshot(
    packageName = AccessibilityTargets.WECHAT_PACKAGE,
    windowId = 70,
    capturedAt = AccessibilityFixtures.CAPTURED_AT,
    nodes = nodes,
    truncated = false,
    textChars = nodes.sumOf { it.text?.length ?: 0 },
    rootPackageName = AccessibilityTargets.WECHAT_PACKAGE,
)

private fun node(text: String) = SnapshotNode(
    text = text,
    contentDescription = null,
    viewId = null,
    className = null,
    depth = 1,
)
