/**
 * 捕获日志（本刀：时间、来源、包名、状态、原因；按状态筛选；导出；清空；忽略恢复）。
 */
package com.zhangran.app.ui.screen.logs

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.export.CaptureLogExportWriter
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.usecase.ClearCaptureLogsUseCase
import com.zhangran.app.domain.usecase.RestoreIgnoredCaptureUseCase
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.xsDp
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.io.File

@Composable
fun LogsScreen() {
    val context = LocalContext.current
    val entryPoint = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            LogsEntryPoint::class.java,
        )
    }
    val viewModel: LogsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                LogsViewModel(
                    logs = entryPoint.captureLogRepository(),
                    clearLogs = entryPoint.clearCaptureLogsUseCase(),
                    restoreIgnored = entryPoint.restoreIgnoredCaptureUseCase(),
                    exporter = entryPoint.captureLogExportWriter(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) {
        viewModel.eventsFlow.collect { event ->
            when (event) {
                is LogsEvent.Share -> shareLogFile(context, File(event.absolutePath))
                is LogsEvent.Message -> Unit
            }
        }
    }
    LogsContent(
        state = state,
        onFilter = viewModel::setFilter,
        onExport = { viewModel.export(BuildConfig.GIT_SHA) },
        onClear = viewModel::clear,
        onRestore = viewModel::restore,
        onRetry = viewModel::refresh,
    )
}

@Composable
fun LogsContent(
    state: LogsUiState,
    onFilter: (CaptureLogStatus?) -> Unit = {},
    onExport: () -> Unit = {},
    onClear: () -> Unit = {},
    onRestore: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    var confirmClear by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(ZhangRanTheme.colors)
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
    ) {
        Text(
            text = "捕获日志",
            style = MaterialTheme.typography.headlineMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp)) {
            TextButton(onClick = onExport) { Text(text = "导出") }
            TextButton(onClick = { confirmClear = true }) { Text(text = "清空日志") }
        }
        FilterRow(selected = (state as? LogsUiState.Content)?.filter, onFilter = onFilter)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (state) {
                LogsUiState.Loading -> Text(
                    text = "正在读取",
                    color = ZhangRanTheme.colors.textSecondaryColor,
                )

                is LogsUiState.Error -> Column {
                    Text(text = state.message, color = ZhangRanTheme.colors.textSecondaryColor)
                    TextButton(onClick = onRetry) { Text(text = "重试") }
                }

                is LogsUiState.Content -> if (state.rows.isEmpty()) {
                    Text(
                        text = if (state.emptyBecauseFilter) "没有该状态的日志" else "还没有捕获日志",
                        color = ZhangRanTheme.colors.textSecondaryColor,
                        modifier = Modifier.padding(top = ZhangRanTheme.spacing.smDp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
                    ) {
                        items(state.rows, key = { it.id }) { row ->
                            LogRow(row = row, onRestore = onRestore)
                        }
                    }
                }
            }
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(text = "清空捕获日志？") },
            text = { Text(text = "只清日志和证据，不影响已记的账") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        onClear()
                    },
                ) { Text(text = "清空") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(text = "取消") }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(selected: CaptureLogStatus?, onFilter: (CaptureLogStatus?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp),
    ) {
        FilterChip(label = "全部", selected = selected == null, onClick = { onFilter(null) })
        // 解析规则页与 RULE_TEST 不在本刀范围，筛选不提供该状态。
        CaptureLogStatus.entries.filter { it != CaptureLogStatus.RULE_TEST }.forEach { status ->
            FilterChip(
                label = status.name,
                selected = selected == status,
                onClick = { onFilter(status) },
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.semantics { contentDescription = "按状态筛选 $label" },
        ) {
        Text(
            text = label,
            color = if (selected) {
                ZhangRanTheme.colors.primaryColor
            } else {
                ZhangRanTheme.colors.textDisabledColor
            },
        )
    }
}

@Composable
private fun LogRow(row: CaptureLogRowUi, onRestore: (String) -> Unit) {
    Column(
        modifier = Modifier.semantics {
            contentDescription = "日志 ${row.timeText} ${row.sourceText} ${row.packageName} ${row.statusText}"
        },
    ) {
        Text(text = row.timeText, color = ZhangRanTheme.colors.textPrimaryColor)
        Text(
            text = "${row.sourceText}  ${row.packageName}  ${row.statusText}",
            color = ZhangRanTheme.colors.textSecondaryColor,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = row.reasonText,
            color = ZhangRanTheme.colors.textSecondaryColor,
            style = MaterialTheme.typography.bodySmall,
        )
        if (row.canRestore) {
            TextButton(onClick = { onRestore(row.id) }) { Text(text = "恢复") }
        }
    }
}

private fun shareLogFile(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/json"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, file.name)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(send, "分享捕获日志")
    if (context !is android.app.Activity) {
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(chooser)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface LogsEntryPoint {
    fun captureLogRepository(): CaptureLogRepository

    fun clearCaptureLogsUseCase(): ClearCaptureLogsUseCase

    fun restoreIgnoredCaptureUseCase(): RestoreIgnoredCaptureUseCase

    fun captureLogExportWriter(): CaptureLogExportWriter
}
