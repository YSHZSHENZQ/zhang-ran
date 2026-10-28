/**
 * 导航宿主与底部导航骨架。
 *
 * 依据：`docs/04` §15（单 Activity + Compose Navigation）、`docs/05` §12（底栏 5 项）。
 * 规则：本任务只做骨架 —— 每个目的地渲染对应占位页面（只显示页面名），不接业务。
 */
package com.zhangran.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhangran.app.ui.screen.about.AboutScreen
import com.zhangran.app.ui.screen.accounts.AccountsScreen
import com.zhangran.app.ui.screen.add.AddTransactionScreen
import com.zhangran.app.ui.screen.autoLedger.AutoLedgerScreen
import com.zhangran.app.ui.screen.backup.BackupScreen
import com.zhangran.app.ui.screen.budget.BudgetScreen
import com.zhangran.app.ui.screen.categories.CategoriesScreen
import com.zhangran.app.ui.screen.detail.TransactionDetailScreen
import com.zhangran.app.ui.screen.duplicateReview.DuplicateReviewScreen
import com.zhangran.app.ui.screen.home.HomeScreen
import com.zhangran.app.ui.screen.logs.LogsScreen
import com.zhangran.app.ui.screen.mine.MineScreen
import com.zhangran.app.ui.screen.onboard.OnboardScreen
import com.zhangran.app.ui.screen.pending.PendingScreen
import com.zhangran.app.ui.screen.pendingEdit.PendingEditScreen
import com.zhangran.app.ui.screen.rules.RulesScreen
import com.zhangran.app.ui.screen.search.SearchScreen
import com.zhangran.app.ui.screen.stats.StatsScreen
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme

/**
 * 账然应用骨架：Scaffold + 底部导航 + NavHost。
 *
 * @param navController 由调用方提供（测试可注入）；默认 `rememberNavController()`。
 */
@Composable
fun ZhangRanApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = Destination.fromRoute(backStackEntry?.destination?.route)

    // M5-1：非首页的顶级页按返回 → 回首页；二级页不拦截（交给 Navigation）
    BackHandler(enabled = backActionFor(current) is BackAction.GoHome) {
        navController.navigate(Destination.Home.route) {
            popUpTo(Destination.Home.route) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        bottomBar = {
            // M5-1：底栏仅顶级页显示（06 §6.1）
            if (showsBottomBar(current)) {
                BottomNavigationBar(current = current, navController = navController)
            }
        },
    ) { innerPadding ->
        ZhangRanNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/**
 * 底部导航栏（5 项；图标来自原型 SVG，选中项用 primary、未选中用 textDisabled）。
 */
@Composable
private fun BottomNavigationBar(current: Destination?, navController: NavHostController) {
    val colors = ZhangRanTheme.colors
    NavigationBar {
        Destination.topLevel.forEach { destination ->
            val selected = current == destination
            val tint by animateColorAsState(
                targetValue = if (selected) colors.primaryColor else colors.textDisabledColor,
                animationSpec = tween(
                    durationMillis = MotionDuration.fast,
                    easing = MotionEasings.ease,
                ),
                label = "tabTint",
            )
            NavigationBarItem(
                modifier = Modifier.semantics {
                    contentDescription =
                        "${destination.title}，标签页，${if (selected) "已选中" else "未选中"}"
                },
                selected = selected,
                onClick = { navController.navigateToTopLevel(destination) },
                icon = {
                    Icon(
                        imageVector = TabIcons.forDestination(destination, tint),
                        contentDescription = null,
                        modifier = Modifier.size(TAB_ICON_SIZE_DP.dp),
                        tint = tint,
                    )
                },
                label = { Text(destination.title) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = tint,
                    selectedTextColor = tint,
                    unselectedIconColor = tint,
                    unselectedTextColor = tint,
                    indicatorColor = Color.Transparent,
                ),
            )
        }
    }
}

/** 底栏图标尺寸（与原型 viewBox 一致；原型渲染 22px，任务书要求 24dp）。 */
private const val TAB_ICON_SIZE_DP = 24
/**
 * 导航图：把 [Destination] 的每个路由映射到对应占位页面。
 *
 * 注意：本任务所有页面都只是占位（显示页面名）；跳转关系由后续任务实现。
 */
@Composable
fun ZhangRanNavHost(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Destination.start.route,
        modifier = modifier,
    ) {
        composable(Destination.Home.route) {
            // 首页的 5 个入口都经这一个回调出站（`M2e-1b` §4.2/§4.4/§4.6）
            HomeScreen(onNavigate = { route -> navController.navigate(route) })
        }
        // M2e-2：统计页 Empty 态「去记一笔」（导航层唯一改动，经批准）
        composable(Destination.Stats.route) {
            StatsScreen(
                onNavigateToAdd = { navController.navigate(Destination.Add.route) },
            )
        }
        composable(Destination.Add.route) { AddTransactionScreen() }
        composable(Destination.Budget.route) { BudgetScreen() }
        // M4c：「我的」页入口跳转（导航层唯一改动，经批准）
        composable(Destination.Mine.route) {
            MineScreen(onNavigate = { route -> navController.navigate(route) })
        }
        composable(
            route = Destination.Detail.route,
            arguments = listOf(
                // 可选参数：首页点交易时带 id；其他入口（未来）可不带
                navArgument(Destination.ARG_TRANSACTION_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { TransactionDetailScreen() }
        composable(Destination.Pending.route) { PendingScreen() }
        composable(Destination.PendingEdit.route) { PendingEditScreen() }
        composable(Destination.DuplicateReview.route) { DuplicateReviewScreen() }
        composable(Destination.AutoLedger.route) { AutoLedgerScreen() }
        composable(Destination.Onboard.route) { OnboardScreen() }
        // M2d-1：分类管理 →「分类规则」跳转（导航层唯一改动，经批准）
        composable(Destination.Categories.route) {
            CategoriesScreen(
                onNavigateToRules = { navController.navigate(Destination.Rules.route) },
            )
        }
        composable(Destination.Accounts.route) { AccountsScreen() }
        composable(Destination.Rules.route) { RulesScreen() }
        composable(Destination.Backup.route) { BackupScreen() }
        composable(Destination.Logs.route) { LogsScreen() }
        composable(Destination.Search.route) { SearchScreen() }
        composable(Destination.About.route) { AboutScreen() }
    }
}

/**
 * 切换底部导航：同一目的地重复点击不重复入栈；切底栏时清掉中间栈（保持单层）。
 */
private fun NavHostController.navigateToTopLevel(destination: Destination) {
    navigate(destination.route) {
        popUpTo(Destination.start.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
