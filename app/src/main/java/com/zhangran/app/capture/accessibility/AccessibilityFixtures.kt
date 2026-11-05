/**
 * M6-2 固定快照。结构与 [AccessibilityPageSnapshot] 相同，供流水线与待确认页断言。
 */
package com.zhangran.app.capture.accessibility

object AccessibilityFixtures {

    const val WECHAT_PAY_SUCCESS: String = "m6-2-wechat-pay-success"

    const val INVALID_BLANK: String = "m6-2-invalid-blank"

    /** 2025-11-11 附近的真实毫秒时间，落在 [com.zhangran.app.domain.service.TransactionTime] 允许范围内。 */
    const val CAPTURED_AT: Long = 1_762_888_000_000L

    fun wechatPaySuccess(capturedAt: Long = CAPTURED_AT): AccessibilityPageSnapshot =
        AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 1,
            capturedAt = capturedAt,
            nodes = listOf(
                node("支付成功", 1),
                node("星巴克咖啡", 2),
                node("¥38.50", 2),
                node("完成", 3),
            ),
            truncated = false,
            textChars = 16,
        )

    /** 有节点但没有金额、也没有支付结果用语。 */
    fun invalidBlank(capturedAt: Long = CAPTURED_AT): AccessibilityPageSnapshot =
        AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 2,
            capturedAt = capturedAt,
            nodes = listOf(node("完成", 0)),
            truncated = false,
            textChars = 2,
        )

    fun nonWhitelist(capturedAt: Long = CAPTURED_AT): AccessibilityPageSnapshot =
        wechatPaySuccess(capturedAt).copy(packageName = "com.android.settings")

    private fun node(text: String, depth: Int) = SnapshotNode(
        text = text,
        contentDescription = null,
        viewId = null,
        className = null,
        depth = depth,
    )
}
