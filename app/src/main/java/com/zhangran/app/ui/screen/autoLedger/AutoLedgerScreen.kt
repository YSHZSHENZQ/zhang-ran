/**
 * 自动记账页。总开关在「我的」，这里只展示状态和入口。
 */
package com.zhangran.app.ui.screen.autoLedger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.autoledger.AutoLedgerSnapshot
import com.zhangran.app.data.autoledger.AutoLedgerStatusSource
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.service.AutoLedgerAvailability
import com.zhangran.app.ui.common.RefreshOnResume
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.warningColor
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun AutoLedgerScreen(
    onOpenLogs: () -> Unit = {},
    onOpenPending: () -> Unit = {},
) {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AutoLedgerEntryPoint::class.java,
        )
    }
    val viewModel: AutoLedgerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AutoLedgerViewModel(
                    status = entryPoint.autoLedgerStatusSource(),
                    logs = entryPoint.captureLogRepository(),
                    transactions = entryPoint.transactionRepository(),
                )
            }
        },
    )
    RefreshOnResume(viewModel::refresh)
    val state by viewModel.uiState.collectAsState()
    AutoLedgerContent(
        state = state,
        onOpenLogs = onOpenLogs,
        onOpenPending = onOpenPending,
        onRetry = viewModel::refresh,
    )
}

@Composable
fun AutoLedgerContent(
    state: AutoLedgerUiState,
    onOpenLogs: () -> Unit = {},
    onOpenPending: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(ZhangRanTheme.colors)
            .verticalScroll(rememberScrollState())
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
    ) {
        Text(
            text = "自动记账",
            style = MaterialTheme.typography.headlineMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        when (state) {
            AutoLedgerUiState.Loading -> Text(
                text = "正在读取",
                color = ZhangRanTheme.colors.textSecondaryColor,
            )

            is AutoLedgerUiState.Error -> Column {
                Text(text = state.message, color = ZhangRanTheme.colors.textSecondaryColor)
                TextButton(onClick = onRetry) { Text(text = "重试") }
            }

            is AutoLedgerUiState.Content -> AutoLedgerBody(
                snapshot = state.snapshot,
                recent = state.recent,
                onOpenLogs = onOpenLogs,
                onOpenPending = onOpenPending,
            )
        }
    }
}

@Composable
private fun AutoLedgerBody(
    snapshot: AutoLedgerSnapshot,
    recent: List<RecentCaptureUi>,
    onOpenLogs: () -> Unit,
    onOpenPending: () -> Unit,
) {
    Text(
        text = "总状态 ${snapshot.label}",
        color = if (snapshot.availability == AutoLedgerAvailability.SUSPECTED_STALE) {
            ZhangRanTheme.colors.warningColor
        } else {
            ZhangRanTheme.colors.textPrimaryColor
        },
        modifier = Modifier.semantics { contentDescription = "自动记账总状态：${snapshot.label}" },
    )
    if (snapshot.availability == AutoLedgerAvailability.SUSPECTED_STALE) {
        Text(
            text = "已超过 24 小时没有捕获。这只是状态提示，不会新增待确认。",
            color = ZhangRanTheme.colors.warningColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Text(
        text = "微信监听 ${if (snapshot.wechatListening) "可用" else "不可用"}",
        color = ZhangRanTheme.colors.textPrimaryColor,
    )
    Text(
        text = "支付宝监听 ${if (snapshot.alipayListening) "可用" else "不可用"}",
        color = ZhangRanTheme.colors.textPrimaryColor,
    )
    Text(
        text = "无障碍 ${if (snapshot.accessibilityGranted) "已授权" else "未授权"}",
        color = ZhangRanTheme.colors.textSecondaryColor,
    )
    Text(
        text = "电池优化 ${if (snapshot.batteryOptimizationIgnored) "已忽略" else "未忽略"}",
        color = ZhangRanTheme.colors.textSecondaryColor,
    )
    Text(
        text = "最近识别记录",
        style = MaterialTheme.typography.titleMedium,
        color = ZhangRanTheme.colors.textPrimaryColor,
    )
    if (recent.isEmpty()) {
        Text(text = "还没有识别记录", color = ZhangRanTheme.colors.textSecondaryColor)
    } else {
        recent.forEach { row ->
            Text(
                text = "${row.timeText}  ${row.sourceText}  ${row.packageName}  ${row.statusText}  ${row.summary}",
                color = ZhangRanTheme.colors.textSecondaryColor,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
    TextButton(
        onClick = onOpenLogs,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = "查看日志") }
    TextButton(
        onClick = onOpenPending,
        modifier = Modifier.fillMaxWidth(),
    ) { Text(text = "待确认") }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AutoLedgerEntryPoint {
    fun autoLedgerStatusSource(): AutoLedgerStatusSource

    fun captureLogRepository(): CaptureLogRepository

    fun transactionRepository(): TransactionRepository
}
