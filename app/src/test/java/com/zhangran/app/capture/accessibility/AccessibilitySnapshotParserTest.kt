/**
 * M6-3：真机结果页的商户、金额、方向，以及不是结果页的页面。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

}
