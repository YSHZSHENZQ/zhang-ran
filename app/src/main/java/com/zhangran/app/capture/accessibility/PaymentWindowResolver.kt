/**
 * 采样时选择「事件包名自己的窗口」。
 *
 * 延迟之后 `rootInActiveWindow` 常常已经变成账然、桌面或最近任务。
 * 只采根包名与事件包名一致的窗口；对不上就区分「看错窗口」和「没有匹配窗口」，不入账。
 */
package com.zhangran.app.capture.accessibility

/** `getWindows()` 里一个窗口的包名和 id。 */
data class ObservedWindow(
    val packageName: String?,
    val windowId: Int,
)

enum class PaymentWindowMatch {
    MATCHED,
    WRONG_WINDOW,
    NO_MATCHING_WINDOW,
}

data class PaymentWindowDecision(
    val match: PaymentWindowMatch,
    val eventPackage: String,
    val rootPackage: String?,
    val windowId: Int?,
)

/**
 * 绝不能当成支付页的窗口：账然自己、桌面、最近任务和系统界面。
 * 不收录设备最近任务里出现过的个人应用名。
 */
object NonPaymentWindows {

    const val OWN_PACKAGE: String = "com.zhangran.app"

    private val shells = setOf(
        OWN_PACKAGE,
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.miui.home",
        "com.mi.android.globallauncher",
    )

    fun isShell(packageName: String?): Boolean = packageName != null && packageName in shells
}

object PaymentWindowResolver {

    /**
     * @param eventPackage 无障碍事件上的包名
     * @param eventWindowId 事件当时的窗口 id
     * @param activePackage 延迟之后当前活动窗口的根包名
     * @param windows `getWindows()` 看到的窗口。需要 `flagRetrieveInteractiveWindows` 才能看到非活动窗口
     */
    fun resolve(
        eventPackage: String,
        eventWindowId: Int,
        activePackage: String?,
        windows: List<ObservedWindow>,
    ): PaymentWindowDecision {
        val matched = windows.firstOrNull { window ->
            window.packageName == eventPackage && !NonPaymentWindows.isShell(window.packageName)
        }
        if (matched != null) {
            return matched(eventPackage, matched.packageName, matched.windowId)
        }
        if (activePackage == eventPackage && !NonPaymentWindows.isShell(activePackage)) {
            return matched(eventPackage, activePackage, eventWindowId)
        }
        val wrong = wrongPackage(eventPackage, eventWindowId, activePackage, windows)
        if (wrong != null) {
            return PaymentWindowDecision(
                match = PaymentWindowMatch.WRONG_WINDOW,
                eventPackage = eventPackage,
                rootPackage = wrong,
                windowId = null,
            )
        }
        return PaymentWindowDecision(
            match = PaymentWindowMatch.NO_MATCHING_WINDOW,
            eventPackage = eventPackage,
            rootPackage = null,
            windowId = null,
        )
    }

    /** 根包名有记录且与事件包名不一致，或根包名是账然/桌面/系统界面。 */
    fun snapshotMismatch(snapshot: AccessibilityPageSnapshot): Boolean {
        val root = snapshot.rootPackageName ?: return false
        if (root != snapshot.packageName) return true
        return NonPaymentWindows.isShell(root)
    }

    private fun matched(eventPackage: String, rootPackage: String?, windowId: Int) = PaymentWindowDecision(
        match = PaymentWindowMatch.MATCHED,
        eventPackage = eventPackage,
        rootPackage = rootPackage,
        windowId = windowId,
    )

    private fun wrongPackage(
        eventPackage: String,
        eventWindowId: Int,
        activePackage: String?,
        windows: List<ObservedWindow>,
    ): String? {
        if (activePackage != null && activePackage != eventPackage) return activePackage
        val byId = windows.firstOrNull { it.windowId == eventWindowId }?.packageName
        if (!byId.isNullOrBlank() && byId != eventPackage) return byId
        val other = windows.firstOrNull { window ->
            val name = window.packageName
            !name.isNullOrBlank() && name != eventPackage
        }?.packageName
        if (other != null) return other
        if (NonPaymentWindows.isShell(activePackage)) return activePackage
        return null
    }
}
