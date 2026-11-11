/**
 * 权限引导：只含无障碍和电池优化。电池优化可以跳过。
 */
package com.zhangran.app.ui.screen.onboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.autoledger.AutoLedgerSnapshot
import com.zhangran.app.data.autoledger.AutoLedgerStatusSource
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.di.SettingsEntryPoint
import com.zhangran.app.ui.common.RefreshOnResume
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class OnboardViewModel(
    private val status: AutoLedgerStatusSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    val snapshot: StateFlow<AutoLedgerSnapshot?> = status.snapshots.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = null,
    )

    fun refresh() {
        status.refreshPermissions()
    }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            settings.setOnboardCompleted(true)
            onDone()
        }
    }
}

@Composable
fun OnboardScreen(onCompleted: () -> Unit = {}) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            OnboardEntryPoint::class.java,
        )
    }
    val settings = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            SettingsEntryPoint::class.java,
        ).settingsRepository()
    }
    val viewModel: OnboardViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OnboardViewModel(
                    status = entryPoint.autoLedgerStatusSource(),
                    settings = settings,
                )
            }
        },
    )
    RefreshOnResume(viewModel::refresh)
    val snapshot by viewModel.snapshot.collectAsState()
    OnboardContent(
        accessibilityGranted = snapshot?.accessibilityGranted == true,
        batteryIgnored = snapshot?.batteryOptimizationIgnored == true,
        onOpenAccessibility = { openAppDetails(context) },
        onOpenBattery = { openBatterySettings(context) },
        onComplete = { viewModel.complete(onCompleted) },
    )
}

@Composable
fun OnboardContent(
    accessibilityGranted: Boolean,
    batteryIgnored: Boolean,
    onOpenAccessibility: () -> Unit = {},
    onOpenBattery: () -> Unit = {},
    onComplete: () -> Unit = {},
) {
    var batterySkipped by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(ZhangRanTheme.colors)
            .verticalScroll(rememberScrollState())
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
    ) {
        Text(
            text = "权限引导",
            style = MaterialTheme.typography.headlineMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Text(
            text = "打开无障碍后，账然才能读取微信和支付宝的支付结果。电池优化是建议项，可以跳过。",
            color = ZhangRanTheme.colors.textSecondaryColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "无障碍服务",
            style = MaterialTheme.typography.titleMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Text(
            text = if (accessibilityGranted) "无障碍已授权" else "无障碍未授权",
            color = ZhangRanTheme.colors.textSecondaryColor,
        )
        TextButton(onClick = onOpenAccessibility) { Text(text = "前往授权") }
        Text(
            text = "电池优化",
            style = MaterialTheme.typography.titleMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Text(
            text = when {
                batteryIgnored -> "电池优化已忽略"
                batterySkipped -> "已跳过电池优化"
                else -> "建议忽略电池优化，可以跳过"
            },
            color = ZhangRanTheme.colors.textSecondaryColor,
        )
        TextButton(onClick = onOpenBattery) { Text(text = "去设置") }
        TextButton(onClick = { batterySkipped = true }) { Text(text = "跳过") }
        TextButton(onClick = onComplete) { Text(text = "完成") }
    }
}

private fun openAppDetails(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
    }
    context.startActivity(intent)
}

private fun openBatterySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface OnboardEntryPoint {
    fun autoLedgerStatusSource(): AutoLedgerStatusSource
}
