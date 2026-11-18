package com.zhangran.app.capture.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentWindowResolverTest {

    @Test
    fun 活动窗口已是账然但窗口列表里仍有支付宝时采支付宝() {
        val decision = PaymentWindowResolver.resolve(
            eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
            eventWindowId = 9,
            activePackage = NonPaymentWindows.OWN_PACKAGE,
            windows = listOf(
                ObservedWindow(NonPaymentWindows.OWN_PACKAGE, 1),
                ObservedWindow(AccessibilityTargets.ALIPAY_PACKAGE, 9),
            ),
        )
        assertEquals(PaymentWindowMatch.MATCHED, decision.match)
        assertEquals(AccessibilityTargets.ALIPAY_PACKAGE, decision.rootPackage)
        assertEquals(9, decision.windowId)
    }

    @Test
    fun 活动窗口就是事件包名时直接采用() {
        val decision = PaymentWindowResolver.resolve(
            eventPackage = AccessibilityTargets.WECHAT_PACKAGE,
            eventWindowId = 4,
            activePackage = AccessibilityTargets.WECHAT_PACKAGE,
            windows = emptyList(),
        )
        assertEquals(PaymentWindowMatch.MATCHED, decision.match)
        assertEquals(4, decision.windowId)
    }

    @Test
    fun 看到的窗口都不是事件包名时记为看错窗口() {
        val decision = PaymentWindowResolver.resolve(
            eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
            eventWindowId = 38_598,
            activePackage = NonPaymentWindows.OWN_PACKAGE,
            windows = listOf(ObservedWindow(NonPaymentWindows.OWN_PACKAGE, 38_598)),
        )
        assertEquals(PaymentWindowMatch.WRONG_WINDOW, decision.match)
        assertEquals(NonPaymentWindows.OWN_PACKAGE, decision.rootPackage)
        assertNull(decision.windowId)
    }

    @Test
    fun 什么窗口都没有时记为没有匹配窗口() {
        val decision = PaymentWindowResolver.resolve(
            eventPackage = AccessibilityTargets.ALIPAY_PACKAGE,
            eventWindowId = 1,
            activePackage = null,
            windows = emptyList(),
        )
        assertEquals(PaymentWindowMatch.NO_MATCHING_WINDOW, decision.match)
        assertNull(decision.rootPackage)
    }

    @Test
    fun 账然自己的包名即使和事件包名相同也不当支付页() {
        val decision = PaymentWindowResolver.resolve(
            eventPackage = NonPaymentWindows.OWN_PACKAGE,
            eventWindowId = 1,
            activePackage = NonPaymentWindows.OWN_PACKAGE,
            windows = listOf(ObservedWindow(NonPaymentWindows.OWN_PACKAGE, 1)),
        )
        assertEquals(PaymentWindowMatch.WRONG_WINDOW, decision.match)
    }

    @Test
    fun 快照根包名和事件包名不一致就是看错窗口() {
        val page = AccessibilityFixtures.wrongWindowHome()
        assertTrue(PaymentWindowResolver.snapshotMismatch(page))
        assertFalse(PaymentWindowResolver.snapshotMismatch(AccessibilityFixtures.alipayYuebao()))
        assertFalse(PaymentWindowResolver.snapshotMismatch(AccessibilityFixtures.wechatPaySuccess()))
    }
}
