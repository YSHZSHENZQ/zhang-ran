/**
 * 恢复报告与执行中/错误弹层（`M4b-2` §4.8/§4.9）。
 *
 * 报告至少展示：新增 / 更新 / 跳过 / 冲突四类数量；冲突与不变量修复可展开。
 */
package com.zhangran.app.ui.screen.backup.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.data.backup.RestoreMode
import com.zhangran.app.data.backup.RestoreReport
import com.zhangran.app.data.backup.RestoreTables
import com.zhangran.app.ui.screen.backup.BackupLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.modalFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.scrimColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor

/** 模式展示名。 */
fun restoreModeLabel(mode: RestoreMode): String = when (mode) {
    RestoreMode.MERGE -> "合并恢复"
    RestoreMode.REPLACE -> "替换恢复"
    RestoreMode.SETTINGS_ONLY -> "仅恢复设置"
}

/** 恢复完成报告。 */
@Composable
fun RestoreReportSheet(
    report: RestoreReport,
    onDismiss: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    var conflictsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭恢复报告" },
        )
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(MotionDuration.medium, easing = MotionEasings.ease)) +
                slideInVertically(
                    animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
                    initialOffsetY = { fullHeight -> fullHeight },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZhangRanTheme.shapes.dialog)
                    .background(color = colors.backgroundColor)
                    .background(color = colors.modalFillColor)
                    .padding(BackupLayout.sheetPaddingDp)
                    .heightIn(max = BackupLayout.reportSheetMaxHeightDp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(BackupLayout.sheetHandleHeightDp)
                        .fillMaxWidth(BackupLayout.sheetHandleWidthFraction)
                        .clip(ZhangRanTheme.shapes.handle)
                        .background(color = colors.textDisabledColor),
                )
                Spacer(modifier = Modifier.height(BackupLayout.sheetHandleGapDp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "恢复完成",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.primaryColor,
                    )
                    Text(
                        text = restoreModeLabel(report.mode),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = BackupLayout.reportModeFontSizeSp.sp,
                        ),
                        color = colors.textSecondaryColor,
                    )
                }
                Text(
                    text = "耗时 ${report.durationMs} ms",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = BackupLayout.sheetSummaryFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                )
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                Row(horizontalArrangement = Arrangement.spacedBy(BackupLayout.itemGapDp)) {
                    ReportCount(label = "新增", value = report.totalInserted, modifier = Modifier.weight(1f))
                    ReportCount(label = "更新", value = report.totalUpdated, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                Row(horizontalArrangement = Arrangement.spacedBy(BackupLayout.itemGapDp)) {
                    ReportCount(label = "跳过", value = report.totalSkipped, modifier = Modifier.weight(1f))
                    ReportCount(label = "冲突", value = report.conflicts.size, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                ) {
                    report.touchedTables.forEach { table ->
                        val inserted = report.inserted[table] ?: 0
                        val updated = report.updated[table] ?: 0
                        val skipped = report.skipped[table] ?: 0
                        if (inserted + updated + skipped > 0) {
                            Text(
                                text = "${RestoreTables.displayName(table)}：新增 $inserted · 更新 $updated · 跳过 $skipped",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = BackupLayout.reportDetailFontSizeSp.sp,
                                ),
                                color = colors.textSecondaryColor,
                            )
                        }
                    }

                    report.preRestoreBackupFileName?.let { name ->
                        Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                        Text(
                            text = "恢复前备份已保存到应用私有目录：$name",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = BackupLayout.reportDetailFontSizeSp.sp,
                            ),
                            color = colors.textDisabledColor,
                        )
                    }

                    if (report.conflicts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                        Text(
                            text = if (conflictsExpanded) "收起冲突明细" else "查看冲突明细（${report.conflicts.size}）",
                            modifier = Modifier
                                .clip(ZhangRanTheme.shapes.option)
                                .clickable { conflictsExpanded = !conflictsExpanded }
                                .semantics { contentDescription = "切换冲突明细" },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.primaryColor,
                        )
                        if (conflictsExpanded) {
                            report.conflicts.forEach { conflict ->
                                Text(
                                    text = "${RestoreTables.displayName(conflict.table)} · ${conflict.id} · ${conflict.reason}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = BackupLayout.reportDetailFontSizeSp.sp,
                                    ),
                                    color = colors.textSecondaryColor,
                                )
                            }
                        }
                    }

                    if (report.invariantFixes.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                        Text(
                            text = "完整性修复",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textPrimaryColor,
                        )
                        report.invariantFixes.forEach { fix ->
                            Text(
                                text = "· $fix",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = BackupLayout.reportDetailFontSizeSp.sp,
                                ),
                                color = colors.textSecondaryColor,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = BackupLayout.touchTargetDp)
                        .clip(ZhangRanTheme.shapes.button)
                        .background(color = colors.primaryColor)
                        .clickable(onClick = onDismiss)
                        .semantics { contentDescription = "关闭恢复报告" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "完成",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.backgroundColor,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportCount(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = modifier
            .clip(ZhangRanTheme.shapes.option)
            .background(color = colors.backgroundColor)
            .padding(BackupLayout.reportCountPaddingDp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = BackupLayout.sheetSummaryFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = BackupLayout.reportCountFontSizeSp.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = colors.textPrimaryColor,
        )
    }
}

/** 执行中：不可取消的模态进度（A11/R12）。 */
@Composable
fun RestoreProgressOverlay(mode: RestoreMode?) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrimColor)
            .semantics { contentDescription = "正在恢复，请勿关闭应用" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(BackupLayout.dialogWidthFraction)
                .clip(ZhangRanTheme.shapes.dialog)
                .background(color = colors.backgroundColor)
                .background(color = colors.modalFillColor)
                .padding(BackupLayout.dialogPaddingDp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(BackupLayout.progressSizeDp),
                color = colors.primaryColor,
                trackColor = colors.dividerColor,
                strokeWidth = BackupLayout.progressStrokeDp,
            )
            Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
            Text(
                text = if (mode == null) "正在校验备份…" else "${restoreModeLabel(mode)}中…",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimaryColor,
            )
            Text(
                text = "请勿关闭应用；失败会自动回滚",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BackupLayout.sheetSummaryFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
    }
}

/** 恢复失败（校验失败 / 事务失败）。 */
@Composable
fun RestoreErrorDialog(message: String, onDismiss: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.scrimColor)
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(BackupLayout.dialogWidthFraction)
                .clip(ZhangRanTheme.shapes.dialog)
                .background(color = colors.backgroundColor)
                .background(color = colors.modalFillColor)
                .padding(BackupLayout.dialogPaddingDp)
                .clickable(enabled = false) {},
        ) {
            Text(
                text = "恢复失败",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.errorColor,
            )
            Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BackupLayout.dialogBodyFontSizeSp.sp,
                ),
                color = colors.textSecondaryColor,
            )
            Spacer(modifier = Modifier.height(BackupLayout.dialogActionGapDp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = BackupLayout.touchTargetDp)
                    .clip(ZhangRanTheme.shapes.button)
                    .background(color = colors.primaryColor)
                    .clickable(onClick = onDismiss)
                    .semantics { contentDescription = "关闭错误提示" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "知道了",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.backgroundColor,
                )
            }
        }
    }
}
