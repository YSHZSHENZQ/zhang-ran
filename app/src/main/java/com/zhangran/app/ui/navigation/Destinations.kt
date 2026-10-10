/**
 * 路由常量（唯一落点）。
 *
 * 依据：`docs/04` §15（单 Activity + Compose Navigation、Screen 不互相持有）、
 * `docs/05` §12（底部导航 5 项）、`docs/06` §1（18 个页面目录）。
 *
 * 规则：页面内**禁止**写字符串路由，只能引用本文件常量。
 */
package com.zhangran.app.ui.navigation

/**
 * 账然全部页面目的地。
 *
 * @property route 路由字符串（Navigation Compose 使用）
 * @property title 页面标题（与 `docs/06` §4 的页面名对应，中文展示名）
 * @property isTopLevel 是否出现在底部导航（`docs/05` §12 的 5 项）
 */
enum class Destination(
    val route: String,
    val title: String,
    val isTopLevel: Boolean,
) {
    // —— 底部导航 5 项（顺序即底栏顺序，依据 05 §12：首页 / 统计 / 记一笔 / 预算 / 我的）
    Home(route = "home", title = "首页", isTopLevel = true),
    Stats(route = "stats", title = "统计", isTopLevel = true),
    Add(route = "add", title = "记一笔", isTopLevel = true),
    Budget(route = "budget", title = "预算", isTopLevel = true),
    Mine(route = "mine", title = "我的", isTopLevel = true),

    // —— 其余 13 个页面（通过路由可达，不出现在底栏）
    Detail(route = "detail", title = "交易详情", isTopLevel = false),
    Pending(route = "pending", title = "待确认记录", isTopLevel = false),
    PendingEdit(route = "pendingEdit", title = "编辑后入账", isTopLevel = false),
    DuplicateReview(route = "duplicateReview", title = "疑似重复", isTopLevel = false),
    AutoLedger(route = "autoLedger", title = "自动记账", isTopLevel = false),
    Onboard(route = "onboard", title = "权限引导", isTopLevel = false),
    Categories(route = "categories", title = "分类管理", isTopLevel = false),
    Accounts(route = "accounts", title = "账户管理", isTopLevel = false),
    Rules(route = "rules", title = "解析规则", isTopLevel = false),
    Backup(route = "backup", title = "数据备份与恢复", isTopLevel = false),
    Logs(route = "logs", title = "捕获日志", isTopLevel = false),
    Search(route = "search", title = "搜索", isTopLevel = false),
    About(route = "about", title = "关于账然", isTopLevel = false),
    ;

    companion object {
        /** 底部导航项（固定 5 项，`docs/05` §12）。 */
        val topLevel: List<Destination> = entries.filter { it.isTopLevel }

        /** 起始目的地。 */
        val start: Destination = Home

        /** 按路由查找目的地；未知名返回 null。 */
        fun fromRoute(route: String?): Destination? = entries.firstOrNull { it.route == route }
    }
}
