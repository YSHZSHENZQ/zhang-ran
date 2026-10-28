/**
 * 底部导航的纯逻辑（`M5-1` §4.1/§4.2；规则 SSOT：`docs/06` §6）。
 *
 * 抽离成纯函数，便于 JVM 单测覆盖"底栏显隐"与"返回落点"，避免只能靠设备测试。
 */
package com.zhangran.app.ui.navigation

/** 底栏是否显示：仅顶级页面显示（`06` §6.1）；未知路由不显示（安全默认）。 */
fun showsBottomBar(destination: Destination?): Boolean = destination?.isTopLevel == true

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
