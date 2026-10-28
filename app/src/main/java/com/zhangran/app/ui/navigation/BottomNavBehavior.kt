/**
 * 底部导航的纯逻辑（`M5-1` §4.1/§4.2；规则 SSOT：`docs/06` §6）。
 *
 * 抽离成纯函数，便于 JVM 单测覆盖"底栏显隐"与"返回落点"，避免只能靠设备测试。
 */
package com.zhangran.app.ui.navigation

/**
 * 底栏是否显示：仅顶级页面显示（`06` §6.1）；未知路由不显示（安全默认）。
 *
 * @param addOpenedAsPush 「记一笔」是从其他页面**推入**的（而非点底栏进入）。
 * 推入时它是流程页，隐藏底栏（用户反馈：从首页进入记一笔不应显示底栏）。
 */
fun showsBottomBar(
    destination: Destination?,
    addOpenedAsPush: Boolean = false,
): Boolean = destination?.isTopLevel == true &&
    !(destination == Destination.Add && addOpenedAsPush)

/** 系统返回键的落点。 */
sealed interface BackAction {
    /** 首页：不拦截，交给系统退出。 */
    data object ExitApp : BackAction

    /** 二级页：交给 Navigation 默认 `popBackStack`。 */
    data object PopBackStack : BackAction

    /** 非首页的顶级页：回首页（不退出应用）。 */
    data class GoHome(val destination: Destination) : BackAction
}

/**
 * 返回键规则（`06` §6.3）：
 * - 二级页面 → 返回上一页（不拦截）；
 * - 非首页的顶级页面 → 回首页；
 * - 首页 → 退出应用。
 */
fun backActionFor(current: Destination?): BackAction = when {
    current == null -> BackAction.PopBackStack
    !current.isTopLevel -> BackAction.PopBackStack
    current == Destination.Home -> BackAction.ExitApp
    else -> BackAction.GoHome(Destination.Home)
}

/**
 * 系统返回键是否需要被本层拦截。
 *
 * 推入的「记一笔」当普通流程页处理：**不拦截**，由 Navigation 返回进入它的页面
 * （从统计空态进入时应回统计，而不是回首页）。
 */
fun shouldInterceptBack(current: Destination?, addOpenedAsPush: Boolean = false): Boolean =
    backActionFor(current) is BackAction.GoHome &&
        !(current == Destination.Add && addOpenedAsPush)