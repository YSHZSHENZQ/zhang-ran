/**
 * 恢复模式选择弹层与危险确认（`M4b-2` §4.9/§4.10）。
 *
 * 三种模式并列展示（含风险说明），Replace 选中后再弹二次确认；
 * 读屏文案按规范："合并恢复，保留较新记录，双击选择" / "替换恢复，会清空现有数据"。
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.zhangran.app.ui.screen.backup.BackupLayout
import com.zhangran.app.ui.screen.backup.BackupSummary
import com.zhangran.app.ui.screen.backup.backupTimeText
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

/**
 * 模式选择底部弹层。
 *
 * @param summary 校验后的备份摘要
 * @param onSelect 选中模式（Replace 由调用方再弹确认）
 */
@Composable
fun RestoreModeSheet(
    summary: BackupSummary,
    onSelect: (RestoreMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭恢复方式选择" },
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
                    .padding(BackupLayout.sheetPaddingDp),
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
                        text = "选择恢复方式",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimaryColor,
                    )
                    TextButton(onClick = onDismiss) {
                        Text(text = "取消", color = colors.textDisabledColor)
                    }
                }
                Text(
                    text = "${summary.fileName} · v${summary.backupVersion} · " +
                        "${backupTimeText(summary.createdAt)} · ${summary.recordCount} 条记录",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = BackupLayout.sheetSummaryFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                )
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
                ModeOption(
                    title = "合并恢复",
                    description = "保留较新记录：同 id 按更新时间取新；你手动改过的字段不会被覆盖。",
                    semanticsText = "合并恢复，保留较新记录，双击选择",
                    onClick = { onSelect(RestoreMode.MERGE) },
                )
                ModeOption(
                    title = "替换恢复（危险）",
                    description = "清空现有账务数据后导入备份；执行前会自动生成一份恢复前备份。",
                    semanticsText = "替换恢复，会清空现有数据，双击选择",
                    danger = true,
                    onClick = { onSelect(RestoreMode.REPLACE) },
                )
                ModeOption(
                    title = "仅恢复设置",
                    description = "只写入主题与置信度阈值，不触碰任何账务数据。",
                    semanticsText = "仅恢复设置，不触碰账务数据，双击选择",
                    onClick = { onSelect(RestoreMode.SETTINGS_ONLY) },
                )
                Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
            }
        }
    }
}

@Composable
private fun ModeOption(
    title: String,
    description: String,
    semanticsText: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BackupLayout.modeOptionMinHeightDp)
            .clip(ZhangRanTheme.shapes.option)
            .clickable(onClick = onClick)
            .padding(
                horizontal = BackupLayout.modeOptionPaddingHorizontalDp,
                vertical = BackupLayout.modeOptionPaddingVerticalDp,
            )
            .semantics { contentDescription = semanticsText },
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = BackupLayout.modeTitleFontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (danger) colors.errorColor else colors.primaryColor,
        )
        Spacer(modifier = Modifier.height(BackupLayout.rowSubGapDp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = BackupLayout.modeDescFontSizeSp.sp,
            ),
            color = colors.textSecondaryColor,
        )
    }
}

/** Replace 二次强确认（危险按钮用 expense 语义色）。 */
@Composable
fun RestoreConfirmDialog(
    fileName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
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
                text = "确认替换恢复？",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.errorColor,
            )
            Spacer(modifier = Modifier.height(BackupLayout.itemGapDp))
            Text(
                text = "将清空现有账务数据（交易、分类、账户、预算、规则、证据、日志、字段保护），" +
                    "再导入「$fileName」。执行前会自动生成一份恢复前备份；此操作不可撤销。",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BackupLayout.dialogBodyFontSizeSp.sp,
                ),
                color = colors.textSecondaryColor,
            )
            Spacer(modifier = Modifier.height(BackupLayout.dialogActionGapDp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(BackupLayout.itemGapDp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(text = "取消", color = colors.textDisabledColor)
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = BackupLayout.touchTargetDp)
                        .clip(ZhangRanTheme.shapes.button)
                        .background(color = colors.errorColor)
                        .clickable(onClick = onConfirm)
                        .semantics { contentDescription = "确认清空并替换恢复" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "清空并替换",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.backgroundColor,
                    )
                }
            }
        }
    }
}
