/**
 * 固定快照。结构与 [AccessibilityPageSnapshot] 相同，供流水线与待确认页断言。
 *
 * M6-3 的 F-WX / F-ALI 来自 2026-11-11 真机证据里能读到的节点，订单号和卡号不入库。
 */
package com.zhangran.app.capture.accessibility

object AccessibilityFixtures {

    const val WECHAT_PAY_SUCCESS: String = "m6-2-wechat-pay-success"

    const val INVALID_BLANK: String = "m6-2-invalid-blank"

    /** 微信支出 ¥10.00，商户「杭州深度求索」。 */
    const val F_WX: String = "m6-3-f-wx"

    /** 支付宝支出 ¥129.99，商户「富富」。 */
    const val F_ALI: String = "m6-3-f-ali"

    /** 与 [F_WX] 同页，另带通知 identity，用来验证同一事件只留一条。 */
    const val AC1_WX: String = "m3b-ac1-wx"

    /** 同一商户同一金额，交易时间比 [WX_CAPTURED_AT] 晚 181 秒。 */
    const val AC3A_WX: String = "m3b-ac3a-wx"

    /** 与 [F_WX] 同金额、180 秒内，商户换成「富富」。 */
    const val AC3B_FUFU: String = "m3b-ac3b-fufu-1000"

    /** 与 [F_WX] 同商户同金额的退款。 */
    const val AC3C_REFUND: String = "m3b-ac3c-refund"

    /** [F_ALI] 的第二份，供「两笔」后确认。 */
    const val AC6_ALI: String = "m3b-ac6-ali"

    const val AC1_NOTIFICATION_ID: String = "m3b-wx-1000"

    const val AC1_POST_TIME: Long = 1_794_373_522_722L

    /** 181 秒，刚好比语义窗口多 1 秒。 */
    const val AC3A_DELTA_MILLIS: Long = 181_000L

    /** 2025-11-11 附近的真实毫秒时间，落在 [com.zhangran.app.domain.service.TransactionTime] 允许范围内。 */
    const val CAPTURED_AT: Long = 1_762_888_000_000L

    fun wechatPaySuccess(
        capturedAt: Long = CAPTURED_AT,
        merchant: String = "星巴克咖啡",
    ): AccessibilityPageSnapshot {
        val nodes = listOf(
            node("支付成功", 1),
            node(merchant, 2),
            node("¥38.50", 2),
            node("完成", 3),
        )
        return AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 1,
            capturedAt = capturedAt,
            nodes = nodes,
            truncated = false,
            textChars = nodes.sumOf { it.text?.length ?: 0 },
        )
    }

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

    /**
     * 微信「支付完成」页。节点顺序与真机证据一致。
     * 「更多信息」是右上角菜单，不能当商户。订单尾号已打码，不写入数字。
     */
    fun wechatDepthSeek(capturedAt: Long = WX_CAPTURED_AT): AccessibilityPageSnapshot {
        val nodes = listOf(
            described(text = null, description = "更多信息", viewId = "com.tencent.mm:id/fq", depth = 13),
            described(text = "支付完成", description = null, viewId = "android:id/text1", depth = 12),
            node("¥10.00", 19),
            node("支付总额", 19),
            node("订单尾号", 19),
            node("支付成功", 19),
            node("订单状态", 19),
            node("杭州深度求索", 19),
        )
        return page(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 36_148,
            capturedAt = capturedAt,
            nodes = nodes,
        )
    }

    /**
     * 支付宝支付结果页。短名是「富富」。
     * 「回首页」「完成」是导航和按钮。卡号只保留已打码的占位，不写真实数字。
     */
    fun alipayFufu(capturedAt: Long = ALI_CAPTURED_AT): AccessibilityPageSnapshot {
        val nodes = listOf(
            described(
                text = "回首页",
                description = null,
                viewId = "com.alipay.android.app:id/nav_right_textview",
                depth = 1,
            ),
            node("完成", 4),
            node("中国银行储蓄卡***", 6),
            node("付款方式", 5),
            node("¥129.99", 5),
            node("富富", 5),
            described(text = null, description = "支付成功 ¥129.99", viewId = null, depth = 5),
        )
        return page(
            packageName = AccessibilityTargets.ALIPAY_PACKAGE,
            windowId = 35_697,
            capturedAt = capturedAt,
            nodes = nodes,
        )
    }

    /** 微信支付列表：两笔金额，没有单独的结果节点。 */
    fun wechatPayList(): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.WECHAT_PACKAGE,
        windowId = 11,
        capturedAt = CAPTURED_AT,
        nodes = listOf(
            node("微信支付", 1),
            node("账单详情", 2),
            node("汉堡王", 3),
            node("¥30.90", 4),
            node("使用杭州银行储蓄卡", 4),
            node("杭州深度求索", 5),
            node("使用零钱支付", 5),
            node("¥10.00", 6),
        ),
    )

    /** 列表里某一行写着「支付成功」，同时还有另一笔金额。 */
    fun wechatPayListWithStatus(): AccessibilityPageSnapshot {
        val base = wechatPayList()
        return base.copy(nodes = listOf(node("支付成功", 3)) + base.nodes)
    }

    /** 营销长句里夹着结果用语和金额，不是结果页。 */
    fun marketingSentence(): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 12,
        capturedAt = CAPTURED_AT,
        nodes = listOf(node("恭喜支付成功，再付¥11.58可得红包", 1)),
    )

    /**
     * 只有「支付成功」、一笔金额和导航/按钮词，没有结果页锚点。
     * 对应首页误记的「付款 -11.58」和标题「我的」。
     */
    fun chromeAmountNotResult(): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 13,
        capturedAt = CAPTURED_AT,
        nodes = listOf(
            node("我的", 1),
            node("付款", 2),
            node("支付成功", 3),
            node("¥11.58", 4),
            node("回首页", 5),
        ),
    )

    /** 账单详情含「支付成功」，但是详情页，不是刚付完的结果页。 */
    fun wechatBillDetail(): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.WECHAT_PACKAGE,
        windowId = 14,
        capturedAt = CAPTURED_AT,
        nodes = listOf(
            node("全部账单", 1),
            node("当前状态", 2),
            node("支付成功", 2),
            node("¥10.00", 3),
            node("商户全称", 4),
            node("杭州深度求索人工智能基础技术研究有限公司", 4),
            node("交易单号", 5),
        ),
    )

    /** 支付宝账单写「交易成功」，不是本刀的支付结果用语。 */
    fun alipayTradeSuccessBill(): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 15,
        capturedAt = CAPTURED_AT,
        nodes = listOf(
            node("交易成功", 1),
            node("-14.90", 2),
            node("luckincoffee", 3),
            node("我的", 4),
        ),
    )

    /**
     * 支付宝结果页，金额改成 ¥10.00，商户仍是「富富」。
     * 用来和微信 ¥10.00 比：同金额、不同商户。
     */
    fun alipayFufuTenYuan(capturedAt: Long = WX_CAPTURED_AT): AccessibilityPageSnapshot {
        val nodes = listOf(
            described(
                text = "回首页",
                description = null,
                viewId = "com.alipay.android.app:id/nav_right_textview",
                depth = 1,
            ),
            node("完成", 4),
            node("中国银行储蓄卡***", 6),
            node("付款方式", 5),
            node("¥10.00", 5),
            node("富富", 5),
            described(text = null, description = "支付成功 ¥10.00", viewId = null, depth = 5),
        )
        return page(
            packageName = AccessibilityTargets.ALIPAY_PACKAGE,
            windowId = 35_698,
            capturedAt = capturedAt,
            nodes = nodes,
        )
    }

    /** 微信退款结果。只有「退款成功」，不和支出用语混在同一页。 */
    fun wechatRefund(capturedAt: Long = WX_CAPTURED_AT): AccessibilityPageSnapshot {
        val nodes = listOf(
            node("退款成功", 12),
            node("¥10.00", 19),
            node("杭州深度求索", 19),
            node("完成", 19),
        )
        return page(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 36_149,
            capturedAt = capturedAt,
            nodes = nodes,
        )
    }

    /** 余额宝转出付款的结果页。没有商户名。 */
    const val F_YUEBAO: String = "fx-1-f-ali-yuebao"

    /** 同一页，但「¥」和「25.10」拆成两个节点。 */
    const val F_YUEBAO_SPLIT: String = "fx-1-f-ali-yuebao-split"

    /** 事件包名是支付宝，树却是账然首页。 */
    const val F_WRONG_WINDOW: String = "fx-1-f-wrong-window"

    const val YUEBAO_CAPTURED_AT: Long = 1_794_552_000_000L

    const val WRONG_WINDOW_CAPTURED_AT: Long = 1_794_552_319_644L

    /**
     * 支付宝余额宝结果页的脱敏结构：单独的「支付成功」、主金额 ¥25.10、
     * 「订单金额」和金额分列、「交易方式 / 余额宝(转出资金付款)」、
     * 优惠券（10元、2元、12元、¥10、10.00元）、「回首页」「完成」。没有商户名。
     */
    fun alipayYuebao(capturedAt: Long = YUEBAO_CAPTURED_AT): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 41_001,
        capturedAt = capturedAt,
        rootPackageName = AccessibilityTargets.ALIPAY_PACKAGE,
        nodes = yuebaoNodes(splitAmount = false),
    )

    /** 与 [alipayYuebao] 相同，主金额的货币符号和数字分成两个节点。 */
    fun alipayYuebaoSplit(capturedAt: Long = YUEBAO_CAPTURED_AT): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 41_002,
        capturedAt = capturedAt,
        rootPackageName = AccessibilityTargets.ALIPAY_PACKAGE,
        nodes = yuebaoNodes(splitAmount = true),
    )

    /**
     * 事件写着支付宝，节点却是账然首页。结构来自一次脱敏后的窗口快照，
     * 根包名补成 `com.zhangran.app`。不含最近任务里的个人应用名。
     */
    fun wrongWindowHome(capturedAt: Long = WRONG_WINDOW_CAPTURED_AT): AccessibilityPageSnapshot = page(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = 38_598,
        capturedAt = capturedAt,
        rootPackageName = NonPaymentWindows.OWN_PACKAGE,
        nodes = listOf(
            node("我的", 8),
            described(text = null, description = "我的，标签页，未选中", viewId = null, depth = 7),
            node("预算", 8),
            described(text = null, description = "预算，标签页，未选中", viewId = null, depth = 7),
            node("记一笔", 8),
            described(text = null, description = "记一笔，标签页，未选中", viewId = null, depth = 7),
            node("统计", 8),
            described(text = null, description = "统计，标签页，未选中", viewId = null, depth = 7),
            node("首页", 8),
            described(text = null, description = "首页，标签页，已选中", viewId = null, depth = 7),
            node("检查自动记账", 6),
            node("记一笔", 6),
            node("记一笔，或检查自动记账设置", 5),
            node("还没有记录", 5),
            node("⏳", 7),
            described(text = null, description = "待确认", viewId = null, depth = 6),
            node("🔍", 7),
            described(text = null, description = "搜索", viewId = null, depth = 6),
            described(
                text = "AUTO LEDGER · 疑似失效",
                description = "自动记账状态：疑似失效",
                viewId = null,
                depth = 5,
            ),
            node("账然", 5),
        ),
    )

    /** 真机证据上的采样时间（UTC epoch millis）。 */
    const val WX_CAPTURED_AT: Long = 1_794_373_522_722L

    const val ALI_CAPTURED_AT: Long = 1_794_362_941_004L

    private fun yuebaoNodes(splitAmount: Boolean): List<SnapshotNode> {
        val main = if (splitAmount) {
            listOf(node("¥", 5), node("25.10", 5))
        } else {
            listOf(node("¥25.10", 5))
        }
        val orderAmount = if (splitAmount) {
            listOf(node("订单金额", 6), node("¥", 7), node("25.10", 7))
        } else {
            listOf(node("订单金额", 6), node("¥25.10", 7))
        }
        return listOf(node("支付成功", 4)) +
            main +
            orderAmount +
            listOf(
                node("交易方式", 6),
                node("余额宝(转出资金付款)", 7),
                node("支付红包", 8),
                node("10元", 9),
                node("优惠券", 8),
                node("2元", 9),
                node("立减券", 8),
                node("12元", 9),
                node("抵扣券", 8),
                node("¥10", 9),
                node("10.00元", 9),
                node("回首页", 3),
                node("完成", 4),
            )
    }

    private fun page(
        packageName: String,
        windowId: Int,
        capturedAt: Long,
        nodes: List<SnapshotNode>,
        rootPackageName: String? = null,
    ) = AccessibilityPageSnapshot(
        packageName = packageName,
        windowId = windowId,
        capturedAt = capturedAt,
        nodes = nodes,
        truncated = false,
        textChars = nodes.sumOf { (it.text?.length ?: 0) + (it.contentDescription?.length ?: 0) },
        rootPackageName = rootPackageName,
    )

    private fun node(text: String, depth: Int) = described(
        text = text,
        description = null,
        viewId = null,
        depth = depth,
    )

    private fun described(
        text: String?,
        description: String?,
        viewId: String?,
        depth: Int,
    ) = SnapshotNode(
        text = text,
        contentDescription = description,
        viewId = viewId,
        className = null,
        depth = depth,
    )
}
