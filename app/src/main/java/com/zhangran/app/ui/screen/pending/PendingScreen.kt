/**
 * 待确认列表（M6-2）。
 *
 * 展示金额、商户（缺失时为「未知」）和至少一个原因。
 * 不提供编辑后入账，也不提供疑似重复专页。
 */
package com.zhangran.app.ui.screen.pending

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.domain.usecase.LoadPendingRecordsUseCase
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.surfaceColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun PendingScreen() {
    val context = LocalContext.current
    val pendingViewModel: PendingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PendingEntryPoint::class.java,
                )
                PendingViewModel(loadPending = entryPoint.loadPendingRecordsUseCase())
            }
        },
    )
    val state by pendingViewModel.uiState.collectAsState()
    PendingContent(state = state, onRetry = pendingViewModel::refresh)
}

@Composable
fun PendingContent(
    state: PendingUiState,
    onRetry: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors)
            .verticalScroll(rememberScrollState())
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
    ) {
        Text(
            text = Destination.Pending.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimaryColor,
        )
        when (state) {
            PendingUiState.Loading -> Text(
                text = "正在读取",
                color = colors.textSecondaryColor,
            )
            PendingUiState.Empty -> Text(
                text = "所有记录已处理完毕",
                color = colors.textSecondaryColor,
            )
            is PendingUiState.Error -> {
                Text(text = state.message, color = colors.textSecondaryColor)
                TextButton(onClick = onRetry) { Text("重试") }
            }
            is PendingUiState.Content -> {
                state.rows.forEach { row ->
                    PendingRowCard(row)
                }
            }
        }
    }
}

@Composable
private fun PendingRowCard(row: PendingRow) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceColor, ZhangRanTheme.shapes.card)
            .padding(ZhangRanTheme.spacing.mdDp)
            .semantics {
                contentDescription = "${row.merchantText}，${row.amountText}，${row.reasonText}"
            },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = row.merchantText,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimaryColor,
            )
            Text(
                text = row.amountText,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimaryColor,
            )
        }
        Spacer(modifier = Modifier.height(ZhangRanTheme.spacing.smDp))
        Text(
            text = row.reasonText,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondaryColor,
        )
        Spacer(modifier = Modifier.height(ZhangRanTheme.spacing.smDp))
        Text(
            text = "${row.sourceText} · ${row.timeText}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondaryColor,
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PendingEntryPoint {
    fun loadPendingRecordsUseCase(): LoadPendingRecordsUseCase
}
