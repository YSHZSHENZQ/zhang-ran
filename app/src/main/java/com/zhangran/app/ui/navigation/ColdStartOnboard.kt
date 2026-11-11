/**
 * 第一次冷启动还没完成权限引导时，进入引导页。完成一次后不再强制。
 *
 * 只挂在 [com.zhangran.app.MainActivity]。导航测试直接组合 [ZhangRanApp]，不会被这里带走。
 */
package com.zhangran.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.zhangran.app.di.SettingsEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Composable
fun ColdStartOnboard(navController: NavHostController) {
    val context = LocalContext.current
    val settings = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java,
        ).settingsRepository()
    }
    val completed by remember(settings) {
        settings.settings.map { it.onboardCompleted }
    }.collectAsState(initial = null)
    var prompted by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(completed) {
        if (completed == false && !prompted) {
            // NavHost 在后一个副作用里才挂上导航图；先跳会在首帧崩溃。
            navController.currentBackStackEntryFlow.first()
            prompted = true
            navController.navigate(Destination.Onboard.route) {
                launchSingleTop = true
            }
        }
    }
}
