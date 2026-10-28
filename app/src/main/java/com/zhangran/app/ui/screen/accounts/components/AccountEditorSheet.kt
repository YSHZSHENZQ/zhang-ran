/**
 * 账户编辑弹层（`M2d-2` §4.5）。
 *
 * 字段：名称（≤ 20，补充规则）、类型（**仅新增可选**，V7）、渠道（含"无"）、尾号（仅银行卡，
 * 4 位数字，V2）、默认账户、启用（关闭时由用例校验 D-1）、上移/下移。
 *
 * 「默认账户」交互选择**按钮**而非开关（理由见交付说明）：
 * - 编辑既有账户：「设为默认」按钮 → 立即调用 `SetDefaultAccountUseCase`（事务、幂等）；
 * - 新增账户：用开关表达"创建后设为默认"（此时还没有 id，无法调用切换用例）。
 */
package com.zhangran.app.ui.screen.accounts.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.usecase.MAX_ACCOUNT_NAME_LENGTH
import com.zhangran.app.ui.screen.accounts.AccountEditorUi
import com.zhangran.app.ui.screen.accounts.AccountsLayout
import com.zhangran.app.ui.screen.accounts.accountTypeLabelOf
import com.zhangran.app.ui.screen.accounts.paymentChannelLabelOf
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.modalFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.scrimColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor

/**
 * @param editor 弹层状态
 * @param onDismiss 取消
 * @param onNameChange 名称
 * @param onTypeChange 类型（仅新增生效）
 * @param onChannelChange 渠道（`null` = 无）
 * @param onLast4Change 尾号（仅数字，≤4）
 * @param onEnabledChange 启用开关
 * @param onDefaultChange 新增时"设为默认"草稿
 * @param onSetDefault 编辑时「设为默认」按钮
 * @param onMove 上移（`true`）/下移（`false`）
 * @param onSave 保存
 */
@Composable
fun AccountEditorSheet(
    editor: AccountEditorUi,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onChannelChange: (PaymentChannel?) -> Unit,
    onLast4Change: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onMove: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(enabled = !editor.saving, onClick = onDismiss)
                .semantics { contentDescription = "关闭编辑" },
        )
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
            ) + slideInVertically(
                animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
                initialOffsetY = { fullHeight -> fullHeight },
            ),
        ) {
            SheetPanel(
                editor = editor,
                onDismiss = onDismiss,
                onNameChange = onNameChange,
                onTypeChange = onTypeChange,
                onChannelChange = onChannelChange,
                onLast4Change = onLast4Change,
                onEnabledChange = onEnabledChange,
                onDefaultChange = onDefaultChange,
                onSetDefault = onSetDefault,
                onMove = onMove,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun SheetPanel(
    editor: AccountEditorUi,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (AccountType) -> Unit,
    onChannelChange: (PaymentChannel?) -> Unit,
    onLast4Change: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onDefaultChange: (Boolean) -> Unit,
    onSetDefault: () -> Unit,
    onMove: (Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .clip(ZhangRanTheme.shapes.dialog)
            // 不透明底色打底（modalFill 有 4% 透明；本项目不做 backdrop blur）
            .background(color = colors.backgroundColor)
            .background(color = colors.modalFillColor)
            .padding(AccountsLayout.sheetPaddingDp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(
                    width = AccountsLayout.sheetHandleWidthDp,
                    height = AccountsLayout.sheetHandleHeightDp,
                )
                .clip(RoundedCornerShape(AccountsLayout.sheetHandleHeightDp))
                .background(color = colors.textDisabledColor),
        )
        Spacer(modifier = Modifier.height(AccountsLayout.sheetHandleGapDp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (editor.editingId == null) "添加账户" else "编辑账户",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimaryColor,
            )
            TextButton(onClick = onDismiss, enabled = !editor.saving) {
                Text(text = "取消", color = colors.textDisabledColor)
            }
        }
        Spacer(modifier = Modifier.height(AccountsLayout.sheetTitleGapDp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = AccountsLayout.sheetFieldsMaxHeightDp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp),
        ) {
            TextField(
                value = editor.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !editor.saving,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "账户名称（最多 $MAX_ACCOUNT_NAME_LENGTH 字）",
                        color = colors.textDisabledColor,
                    )
                },
                shape = ZhangRanTheme.shapes.input,
                colors = fieldColors(),
            )

            // 类型（V7：仅新增可选）
            FieldLabel(text = if (editor.typeEditable) "类型" else "类型（不可修改）")
            if (editor.typeEditable) {
                Column(verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp)) {
                    AccountType.entries.chunked(2).forEach { rowTypes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp)) {
                            rowTypes.forEach { type ->
                                TypeChip(
                                    text = accountTypeLabelOf(type),
                                    selected = editor.type == type,
                                    enabled = !editor.saving,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onTypeChange(type) },
                                )
                            }
                            repeat(2 - rowTypes.size) { Spacer(modifier = Modifier.weight(1f)) }
                        }
                    }
                }
            } else {
                Text(
                    text = accountTypeLabelOf(editor.type),
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondaryColor,
                )
            }

            // 渠道（可空）
            FieldLabel(text = "渠道")
            val channels: List<PaymentChannel?> = listOf(null) + PaymentChannel.entries
            Column(verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp)) {
                channels.chunked(3).forEach { rowChannels ->
                    Row(horizontalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp)) {
                        rowChannels.forEach { channel ->
                            TypeChip(
                                text = channel?.let { paymentChannelLabelOf(it) } ?: "无",
                                selected = editor.channel == channel,
                                enabled = !editor.saving,
                                modifier = Modifier.weight(1f),
                                onClick = { onChannelChange(channel) },
                            )
                        }
                        repeat(3 - rowChannels.size) { Spacer(modifier = Modifier.weight(1f)) }
                    }
                }
            }

            // 尾号（V2：只有银行卡类显示）
            if (editor.last4Visible) {
                FieldLabel(text = "尾号（4 位数字）")
                TextField(
                    value = editor.last4,
                    onValueChange = onLast4Change,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !editor.saving,
                    singleLine = true,
                    placeholder = { Text(text = "如 8823", color = colors.textDisabledColor) },
                    shape = ZhangRanTheme.shapes.input,
                    colors = fieldColors(),
                )
            }

            // 默认账户
            if (editor.editingId == null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "设为默认账户",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimaryColor,
                    )
                    Switch(
                        checked = editor.isDefault,
                        onCheckedChange = onDefaultChange,
                        enabled = !editor.saving,
                        colors = switchColors(),
                    )
                }
            } else if (editor.isDefault) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "默认账户",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.primaryColor,
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (editor.enabled) "未设为默认" else "禁用账户不能设为默认",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDisabledColor,
                    )
                    TextButton(
                        onClick = onSetDefault,
                        enabled = editor.enabled && !editor.saving,
                    ) {
                        Text(text = "设为默认", color = colors.primaryColor)
                    }
                }
            }

            // 启用（D-1 由用例校验）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "启用",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimaryColor,
                    )
                    Text(
                        text = "禁用后不出现在记账账户列表，历史记录仍显示；至少保留一个启用账户",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDisabledColor,
                    )
                }
                Switch(
                    checked = editor.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = !editor.saving,
                    colors = switchColors(),
                )
            }

            // 排序（仅编辑既有账户）
            if (editor.editingId != null) {
                FieldLabel(text = "排序")
                Row(horizontalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp)) {
                    OutlineAction(
                        text = "上移",
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(true) },
                    )
                    OutlineAction(
                        text = "下移",
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                        onClick = { onMove(false) },
                    )
                }
            }
        }

        // 错误固定在字段区外（不随滚动消失），保证用户一定看得到
        editor.error?.let { message ->
            Spacer(modifier = Modifier.height(AccountsLayout.sheetItemGapDp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = colors.errorColor,
            )
        }

        Spacer(modifier = Modifier.height(AccountsLayout.sheetItemGapDp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = AccountsLayout.touchTargetDp)
                .clip(ZhangRanTheme.shapes.button)
                .background(color = colors.primaryColor)
                .clickable(enabled = !editor.saving, onClick = onSave)
                .padding(vertical = AccountsLayout.confirmPaddingDp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (editor.saving) "保存中…" else "保存",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.backgroundColor,
            )
        }
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = AccountsLayout.fieldLabelFontSizeSp.sp,
        ),
        color = ZhangRanTheme.colors.textDisabledColor,
    )
}

@Composable
private fun TypeChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = AccountsLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = if (selected) colors.surfaceVariantColor else colors.inputFillColor)
            .border(
                width = AccountsLayout.controlBorderWidthDp,
                color = colors.dividerColor,
                shape = ZhangRanTheme.shapes.button,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.primaryColor else colors.textSecondaryColor,
        )
    }
}

@Composable
private fun OutlineAction(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = AccountsLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = colors.inputFillColor)
            .border(
                width = AccountsLayout.controlBorderWidthDp,
                color = colors.dividerColor,
                shape = ZhangRanTheme.shapes.button,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) colors.textPrimaryColor else colors.textDisabledColor,
        )
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = ZhangRanTheme.colors.backgroundColor,
    checkedTrackColor = ZhangRanTheme.colors.primaryColor,
    uncheckedThumbColor = ZhangRanTheme.colors.textDisabledColor,
    uncheckedTrackColor = ZhangRanTheme.colors.surfaceVariantColor,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun fieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = ZhangRanTheme.colors.inputFillColor,
    unfocusedContainerColor = ZhangRanTheme.colors.inputFillColor,
    disabledContainerColor = ZhangRanTheme.colors.inputFillColor,
    focusedIndicatorColor = ZhangRanTheme.colors.primaryColor,
    unfocusedIndicatorColor = ZhangRanTheme.colors.dividerColor,
    disabledIndicatorColor = ZhangRanTheme.colors.dividerColor,
    focusedTextColor = ZhangRanTheme.colors.textPrimaryColor,
    unfocusedTextColor = ZhangRanTheme.colors.textPrimaryColor,
    disabledTextColor = ZhangRanTheme.colors.textDisabledColor,
)
