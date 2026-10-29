/**
 * 无障碍采集的白名单目标（`M6` 阶段 1）。
 *
 * 只读微信/支付宝；包名硬编码，不通过 PackageManager 查询已安装应用，
 * 避免申请 `QUERY_ALL_PACKAGES`。
 */
package com.zhangran.app.capture.accessibility

object AccessibilityTargets {

    const val WECHAT_PACKAGE: String = "com.tencent.mm"
    const val ALIPAY_PACKAGE: String = "com.eg.android.AlipayGphone"

    val packages: Set<String> = setOf(WECHAT_PACKAGE, ALIPAY_PACKAGE)
}
