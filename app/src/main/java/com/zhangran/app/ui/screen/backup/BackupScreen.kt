/**
 * 备份页（`M4b-1` 完整重写；交互 SSOT：`docs/06` §4.15，格式：`docs/02` §17）。
 *
 * 本期只做**导出**：
 * ```text
 * 数据备份与恢复
 * [说明] 备份包含…（不含截图/二进制）
 * [操作] 导出备份 → SAF 保存；恢复备份（禁用，"即将支持"）
 * [格式] v1 · manifest + 11 个数据文件 · 最近一次导出
 * ```
 *
 * 恢复/导入属 M4b-2；本页**不提供**可用入口。返回走系统返回键（不改导航层）。
 */
package com.zhangran.app.ui.screen.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.backup.BACKUP_DATA_FILE_NAMES
import com.zhangran.app.data.backup.BACKUP_VERSION
import com.zhangran.app.domain.usecase.ExportBackupUseCase
import com.zhangran.app.ui.screen.backup.components.ExportRow
import com.zhangran.app.ui.screen.backup.components.RowArrow
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** 备份页入口（SAF 的默认文件名与结果显示在页面内）。 */
@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        BackupEntryPoint::class.java,
    )
    val viewModel: BackupViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                BackupViewModel(
                    exportBackup = entryPoint.exportBackupUseCase(),
                    clock = entryPoint.clock(),
                    context = context.applicationContext,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val createDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        // 用户取消 → uri == null：不报错、保持原状态（A10）
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is BackupEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackupContent(
            state = state,
            onExport = { createDocument.launch(viewModel.suggestedFileName()) },
            onDismissResult = viewModel::dismissResult,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 无状态内容（测试渲染四态）。 */
@Composable
fun BackupContent(
    state: BackupUiState,
    onExport: () -> Unit = {},
    onDismissResult: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    val exporting = state is BackupUiState.Exporting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            BackupHead()
            DescriptionCard()

            BackupCard(title = "备份操作") {
                ExportRow(
                    icon = "📤",
                    iconGroup = IconGradientGroup.BLUE,
                    title = "导出备份",
                    description = if (exporting) {
                        "正在生成备份…"
                    } else {
                        "生成 zip 并保存到所选位置"
                    },
                    enabled = !exporting,
                    onClick = onExport,
                ) { RowArrow(enabled = !exporting) }

                // M4b-2：恢复入口必须存在但禁用（A11）
                ExportRow(
                    icon = "📥",
                    iconGroup = IconGradientGroup.PURPLE,
                    title = "恢复备份",
                    description = "即将支持 · 将在后续版本提供",
                    enabled = false,
                )
            }

            when (state) {
                BackupUiState.Idle -> Unit
                BackupUiState.Exporting -> ExportingCard()
                is BackupUiState.Success -> SuccessCard(state = state, onDismiss = onDismissResult)
                is BackupUiState.Error -> ErrorCard(message = state.message, onRetry = onExport)
            }

            BackupCard(title = "格式") {
                InfoLine(label = "备份格式版本", value = "v$BACKUP_VERSION")
                InfoLine(label = "文件结构", value = "manifest + ${BACKUP_DATA_FILE_NAMES.size} 个数据文件")
                InfoLine(
                    label = "最近一次导出",
                    value = when (state) {
                        is BackupUiState.Success -> backupTimeText(state.createdAt)
                        else -> "本次运行尚未导出"
                    },
                )
            }

            Spacer(modifier = Modifier.height(BackupLayout.pageBottomPaddingDp))
        }
    }
}

/** 页头。 */
@Composable
private fun BackupHead() {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = BackupLayout.pageHeadPaddingTopDp,
                bottom = BackupLayout.pageHeadPaddingBottomDp,
            ),
    ) {
        Text(
            text = "数据备份与恢复",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = BackupLayout.titleFontSizeSp.sp,
                letterSpacing = BackupLayout.titleLetterSpacingSp.sp,
                fontWeight = FontWeight.Bold,
                brush = Brush.linearGradient(
                    listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                ),
            ),
        )
        Spacer(modifier = Modifier.height(BackupLayout.headSubGapDp))
        Text(
            text = "BACKUP & RESTORE",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = BackupLayout.headSubFontSizeSp.sp,
                letterSpacing = BackupLayout.headSubLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

/** 说明卡：备份范围与不包含项。 */
@Composable
private fun DescriptionCard() {
    BackupCard(title = "说明") {
        Text(
            text = "备份包含账务、分类、账户、预算、规则、待确认、证据、日志、字段保护标记与设置。",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BackupLayout.bodyFontSizeSp.sp,
            ),
            color = ZhangRanTheme.colors.textSecondaryColor,
        )
        Text(
            text = "不包含任何截图或二进制内容；捕获证据只保留脱敏后的结构化字段。",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BackupLayout.bodyFontSizeSp.sp,
            ),
            color = ZhangRanTheme.colors.textDisabledColor,
        )
    }
}

/** 导出中：进度卡（可读屏）。 */
@Composable
private fun ExportingCard() {
    BackupCard(title = "进度") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = BackupLayout.progressPaddingDp)
                .semantics { contentDescription = "正在生成备份" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BackupLayout.rowGapDp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(BackupLayout.progressSizeDp),
                color = ZhangRanTheme.colors.primaryColor,
                trackColor = ZhangRanTheme.colors.dividerColor,
                strokeWidth = BackupLayout.progressStrokeDp,
            )
            Text(
                text = "正在生成备份…",
                style = MaterialTheme.typography.bodyMedium,
                color = ZhangRanTheme.colors.textPrimaryColor,
            )
        }
    }
}

/** 成功：文件名 + 条目数 + 大小 + 时间。 */
@Composable
private fun SuccessCard(state: BackupUiState.Success, onDismiss: () -> Unit) {
    BackupCard(title = "已导出") {
        Text(
            text = state.fileName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = BackupLayout.fileNameFontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        InfoLine(label = "条目数", value = "${state.entryCount} 个")
        InfoLine(label = "大小", value = backupSizeText(state.byteSize))
        InfoLine(label = "时间", value = backupTimeText(state.createdAt))
        Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
        PrimaryButton(text = "完成", onClick = onDismiss)
    }
}

/** 失败：原因 + 重新选择位置。 */
@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    BackupCard(title = "导出失败") {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ZhangRanTheme.colors.errorColor,
        )
        Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
        PrimaryButton(text = "重新选择位置", onClick = onRetry)
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = BackupLayout.infoRowPaddingVerticalDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BackupLayout.bodyFontSizeSp.sp,
            ),
            color = ZhangRanTheme.colors.textDisabledColor,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BackupLayout.bodyFontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = ZhangRanTheme.colors.textSecondaryColor,
        )
    }
}

/** 主按钮（token 化，无 Material 默认色）。 */
@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BackupLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = ZhangRanTheme.colors.primaryColor)
            .clickable(onClick = onClick)
            .padding(vertical = BackupLayout.buttonPaddingVerticalDp)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = ZhangRanTheme.colors.backgroundColor,
        )
    }
}

/** 分组卡片。 */
@Composable
private fun BackupCard(title: String, content: @Composable () -> Unit) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = BackupLayout.cardMarginHorizontalDp,
                end = BackupLayout.cardMarginHorizontalDp,
                bottom = BackupLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(BackupLayout.cardPaddingDp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = BackupLayout.cardHeadFontSizeSp.sp,
                letterSpacing = BackupLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(BackupLayout.cardHeadGapDp))
        Column(verticalArrangement = Arrangement.spacedBy(BackupLayout.rowSpacingDp)) {
            content()
        }
    }
}

/** 备份页页面级尺寸（原型无独立备份页；沿用设置页比例）。 */
internal object BackupLayout {
    val pageHeadPaddingTopDp: Dp = 12.dp
    val pageHeadPaddingBottomDp: Dp = 12.dp
    val headSubGapDp: Dp = 4.dp
    const val titleFontSizeSp: Float = 22f
    const val titleLetterSpacingSp: Float = 1f
    const val headSubFontSizeSp: Float = 9f
    const val headSubLetterSpacingSp: Float = 3.2f

    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 10.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    val rowMinHeightDp: Dp = 52.dp
    val rowPaddingVerticalDp: Dp = 8.dp
    val rowGapDp: Dp = 12.dp
    val rowSpacingDp: Dp = 6.dp
    val rowIconSizeDp: Dp = 34.dp
    val rowIconRadiusDp: Dp = 11.dp
    const val rowIconFontSizeSp: Float = 18f
    const val rowTitleFontSizeSp: Float = 13.5f
    const val rowDescFontSizeSp: Float = 10.5f
    const val rowArrowFontSizeSp: Float = 14f
    val rowSubGapDp: Dp = 3.dp
    const val disabledAlpha: Float = 0.5f

    val progressPaddingDp: Dp = 8.dp
    val progressSizeDp: Dp = 22.dp
    val progressStrokeDp: Dp = 2.dp
    val infoRowPaddingVerticalDp: Dp = 4.dp
    const val bodyFontSizeSp: Float = 11.5f
    const val fileNameFontSizeSp: Float = 13.5f

    val itemGapDp: Dp = 10.dp
    val touchTargetDp: Dp = 48.dp
    val buttonPaddingVerticalDp: Dp = 14.dp
    val pageBottomPaddingDp: Dp = 24.dp
}

/** Hilt 入口（不引入 `hilt-navigation-compose`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackupEntryPoint {
    fun exportBackupUseCase(): ExportBackupUseCase

    fun clock(): Clock
}
