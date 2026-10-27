/**
 * 字段编辑器集合（`M2b` §4.2/§4.3；原型编辑弹层）。
 *
 * 交互纪律：
 * - **点击详情行**打开对应编辑器（底部弹层），**不存在**"一次改多个字段"的聚合弹窗（`docs/06` §4.6）；
 * - 金额编辑器**复用 M2a 的 `AmountDisplay` + `NumberPad`**（不复制实现，A13）；
 * - 分类编辑器**复用 M2a 的 `CategoryGrid`**；「其他」按 `docs/02` §9 走自定义分类名称流程
 *   （创建新分类的写入路径仍未打通，与 M2a 行为一致：不存在时提示到分类管理中创建）；
 * - 类型编辑器只放开 `EXPENSE ⇄ INCOME`；`TRANSFER` / `REFUND` 置灰并提示"暂不支持（转账/退款编辑）"（A6）；
 * - 弹层动效按 `UI-ANIMATION-SPEC` §3：淡入 + 上移，`MotionDuration.medium`，`MotionEasings.ease`。
 */
package com.zhangran.app.ui.screen.detail.components

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.domain.usecase.FieldEditValue
import com.zhangran.app.ui.screen.add.AmountInput
import com.zhangran.app.ui.screen.add.components.AmountDisplay
import com.zhangran.app.ui.screen.add.components.CategoryGrid
import com.zhangran.app.ui.screen.add.components.NumberPad
import com.zhangran.app.ui.screen.detail.DetailLayout
import com.zhangran.app.ui.screen.detail.FieldEditorKind
import com.zhangran.app.ui.screen.detail.TransactionDetailUi
import com.zhangran.app.ui.screen.detail.amountInputTextOf
import com.zhangran.app.ui.screen.detail.formatDetailDateTime
import com.zhangran.app.ui.screen.detail.typeTextOf
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
import com.zhangran.app.ui.theme.warningColor
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 字段编辑弹层。
 *
 * @param kind 打开哪个编辑器
 * @param detail 当前详情（编辑器初值）
 * @param submitting 提交中（禁用交互，A14）
 * @param onDismiss 关闭
 * @param onSubmit 确认提交（字段 + 新值）
 */
@Composable
fun FieldEditorOverlay(
    kind: FieldEditorKind,
    detail: TransactionDetailUi,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩（点遮罩关闭；提交中不可关闭）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(enabled = !submitting, onClick = onDismiss)
                .semantics { contentDescription = "关闭编辑" },
        )
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(
                animationSpec = tween(
                    durationMillis = MotionDuration.medium,
                    easing = MotionEasings.ease,
                ),
            ) + slideInVertically(
                animationSpec = tween(
                    durationMillis = MotionDuration.medium,
                    easing = MotionEasings.ease,
                ),
                initialOffsetY = { fullHeight -> fullHeight },
            ),
        ) {
            EditorPanel(
                kind = kind,
                detail = detail,
                submitting = submitting,
                onDismiss = onDismiss,
                onSubmit = onSubmit,
            )
        }
    }
}

@Composable
private fun EditorPanel(
    kind: FieldEditorKind,
    detail: TransactionDetailUi,
    submitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.dialog
    // 只有金额编辑器（数字键盘较高）占满固定高度；其余编辑器按内容高度贴底
    val heightModifier = if (kind == FieldEditorKind.Amount) {
        Modifier.fillMaxHeight(DetailLayout.sheetHeightFraction)
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(heightModifier)
            .clip(shape)
            .background(color = colors.modalFillColor)
            .padding(DetailLayout.sheetPaddingDp),
    ) {
        // 拖拽把手（原型 `.modal-handle`）
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = DetailLayout.sheetHandleWidthDp, height = DetailLayout.sheetHandleHeightDp)
                .clip(RoundedCornerShape(DetailLayout.sheetHandleHeightDp))
                .background(color = colors.textDisabledColor),
        )
        Spacer(modifier = Modifier.height(DetailLayout.sheetHandleGapDp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleOf(kind),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimaryColor,
            )
            TextButton(onClick = onDismiss, enabled = !submitting) {
                Text(text = "取消", color = colors.textDisabledColor)
            }
        }
        Spacer(modifier = Modifier.height(DetailLayout.sheetTitleGapDp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DetailLayout.sheetItemGapDp),
        ) {
            when (kind) {
                FieldEditorKind.Merchant -> MerchantEditor(detail, submitting, onSubmit)
                FieldEditorKind.Amount -> AmountEditor(detail, submitting, onSubmit)
                FieldEditorKind.Type -> TypeEditor(detail, submitting, onSubmit)
                FieldEditorKind.Category -> CategoryEditor(detail, submitting, onSubmit)
                FieldEditorKind.Account -> AccountEditor(detail, submitting, onSubmit)
                FieldEditorKind.Time -> TimeEditor(detail, submitting, onSubmit)
                FieldEditorKind.Note -> NoteEditor(detail, submitting, onSubmit)
            }
        }
    }
}

private fun titleOf(kind: FieldEditorKind): String = when (kind) {
    FieldEditorKind.Merchant -> "修改商户"
    FieldEditorKind.Amount -> "修改金额"
    FieldEditorKind.Type -> "修改类型"
    FieldEditorKind.Category -> "修改分类"
    FieldEditorKind.Account -> "修改支付方式"
    FieldEditorKind.Time -> "修改时间"
    FieldEditorKind.Note -> "修改备注"
}

// ---------------------------------------------------------------- 商户

@Composable
private fun MerchantEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    var text by remember { mutableStateOf(detail.merchant.orEmpty()) }
    DetailTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = "输入商户名称",
    )
    EditorConfirmButton(
        enabled = !submitting,
        onClick = { onSubmit(EditableField.MERCHANT, FieldEditValue.Merchant(text)) },
    )
}

// ---------------------------------------------------------------- 金额（复用 M2a 组件）

@Composable
private fun AmountEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    var amount by remember { mutableStateOf(AmountInput(amountInputTextOf(detail.amountMinor))) }
    var invalid by remember { mutableStateOf(false) }

    AmountDisplay(amount = amount)
    Spacer(modifier = Modifier.height(DetailLayout.sheetItemGapDp))
    if (invalid) {
        Text(
            text = "金额必须大于 0",
            style = MaterialTheme.typography.bodySmall,
            color = ZhangRanTheme.colors.errorColor,
        )
        Spacer(modifier = Modifier.height(DetailLayout.sheetItemGapDp))
    }
    NumberPad(
        onDigit = { amount = amount.digit(it) },
        onDot = { amount = amount.dot() },
        onBackspace = { amount = amount.backspace() },
        onClear = { amount = amount.clear() },
        onSave = {
            val minor = amount.toMinorOrNull()
            if (minor == null || minor <= 0L) {
                invalid = true
            } else {
                invalid = false
                onSubmit(EditableField.AMOUNT, FieldEditValue.Amount(minor))
            }
        },
        saving = submitting,
    )
}

// ---------------------------------------------------------------- 类型

@Composable
private fun TypeEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var selected by remember { mutableStateOf(detail.type) }

    Row(horizontalArrangement = Arrangement.spacedBy(DetailLayout.sheetItemGapDp)) {
        listOf(TransactionType.EXPENSE, TransactionType.INCOME).forEach { type ->
            SelectableChip(
                text = typeTextOf(type),
                selected = selected == type,
                enabled = !submitting,
                modifier = Modifier.weight(1f),
                onClick = { selected = type },
            )
        }
    }

    // `TRANSFER` / `REFUND`：置灰 + 明确提示（A6；`M2b` §1.1）
    listOf(TransactionType.TRANSFER, TransactionType.REFUND).forEach { type ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZhangRanTheme.shapes.option)
                .background(color = colors.surfaceVariantColor)
                .padding(DetailLayout.sheetOptionPaddingDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = typeTextOf(type),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textDisabledColor,
            )
            Text(
                text = "暂不支持（转账/退款编辑）",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabledColor,
            )
        }
    }

    EditorConfirmButton(
        enabled = !submitting,
        onClick = { onSubmit(EditableField.TYPE, FieldEditValue.Type(selected)) },
    )
}

// ---------------------------------------------------------------- 分类（复用 M2a CategoryGrid）

@Composable
private fun CategoryEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    var selectedId by remember { mutableStateOf(detail.categoryId) }
    var customDialogVisible by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    if (detail.categories.isEmpty()) {
        Text(
            text = "当前类型下没有可用分类",
            style = MaterialTheme.typography.bodyMedium,
            color = ZhangRanTheme.colors.textDisabledColor,
        )
    } else {
        CategoryGrid(
            categories = detail.categories,
            selectedId = selectedId,
            onSelect = { option ->
                if (option.isFallback) {
                    customDialogVisible = true
                } else {
                    selectedId = option.id
                    hint = null
                }
            },
        )
    }

    hint?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = ZhangRanTheme.colors.warningColor,
        )
    }

    EditorConfirmButton(
        enabled = !submitting && selectedId != null,
        onClick = {
            selectedId?.let { onSubmit(EditableField.CATEGORY, FieldEditValue.Category(it)) }
        },
    )

    if (customDialogVisible) {
        CustomCategoryDialog(
            existingNames = detail.categories.map { it.name },
            onDismiss = { customDialogVisible = false },
            onConfirm = { name ->
                customDialogVisible = false
                val matched = detail.categories.firstOrNull { it.name == name }
                if (matched != null) {
                    selectedId = matched.id
                    hint = null
                } else {
                    // 与 M2a 一致：创建新分类的写入路径尚未打通（CategoryRepository 只有读方法）
                    hint = "分类「$name」尚不存在，请先在分类管理中创建"
                }
            },
        )
    }
}

/** 「其他」自定义分类弹窗（`docs/02` §9；写入路径缺口与 M2a 相同）。 */
@Composable
private fun CustomCategoryDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "自定义分类") },
        text = {
            Column {
                Text(
                    text = "输入分类名称（当前可用：${existingNames.joinToString("、")}）",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZhangRanTheme.colors.textSecondaryColor,
                )
                Spacer(modifier = Modifier.height(DetailLayout.sheetItemGapDp))
                DetailTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "分类名称",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = text.isNotBlank(),
                onClick = { onConfirm(text.trim()) },
            ) { Text(text = "确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}

// ---------------------------------------------------------------- 支付方式

@Composable
private fun AccountEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var selectedId by remember { mutableStateOf(detail.accountId) }

    if (detail.accounts.isEmpty()) {
        Text(
            text = "没有可用支付方式",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textDisabledColor,
        )
    } else {
        detail.accounts.forEach { account ->
            SelectableRow(
                text = account.name,
                selected = account.id == selectedId,
                enabled = !submitting,
                onClick = { selectedId = account.id },
            )
        }
    }

    EditorConfirmButton(
        enabled = !submitting && selectedId != null,
        onClick = {
            selectedId?.let { onSubmit(EditableField.ACCOUNT, FieldEditValue.Account(it)) }
        },
    )
}

// ---------------------------------------------------------------- 时间

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val initial = Instant.ofEpochMilli(detail.transactionTime).atZone(zone)
    var date by remember { mutableStateOf(initial.toLocalDate()) }
    var time by remember { mutableStateOf(initial.toLocalTime().withSecond(0).withNano(0)) }
    var datePickerVisible by remember { mutableStateOf(false) }
    var timePickerVisible by remember { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(DetailLayout.sheetItemGapDp)) {
        SelectableChip(
            text = "${date.year}年${date.monthValue}月${date.dayOfMonth}日",
            selected = true,
            enabled = !submitting,
            modifier = Modifier.weight(1f),
            onClick = { datePickerVisible = true },
        )
        SelectableChip(
            text = "%02d:%02d".format(time.hour, time.minute),
            selected = true,
            enabled = !submitting,
            modifier = Modifier.weight(1f),
            onClick = { timePickerVisible = true },
        )
    }

    Text(
        text = "当前：${formatDetailDateTime(detail.transactionTime)}",
        style = MaterialTheme.typography.bodySmall,
        color = ZhangRanTheme.colors.textDisabledColor,
    )

    EditorConfirmButton(
        enabled = !submitting,
        onClick = {
            val millis = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            onSubmit(EditableField.TIME, FieldEditValue.Time(millis, zone.id))
        },
    )

    if (datePickerVisible) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { millis ->
                            date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        }
                        datePickerVisible = false
                    },
                ) { Text(text = "确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) { Text(text = "取消") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (timePickerVisible) {
        val timeState = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { timePickerVisible = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        time = LocalTime.of(timeState.hour, timeState.minute)
                        timePickerVisible = false
                    },
                ) { Text(text = "确定") }
            },
            dismissButton = {
                TextButton(onClick = { timePickerVisible = false }) { Text(text = "取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 备注

@Composable
private fun NoteEditor(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onSubmit: (EditableField, FieldEditValue) -> Unit,
) {
    var text by remember { mutableStateOf(detail.note.orEmpty()) }
    DetailTextField(
        value = text,
        onValueChange = { if (it.length <= NOTE_LIMIT) text = it },
        placeholder = "输入备注（最多 $NOTE_LIMIT 字）",
        singleLine = false,
    )
    Text(
        text = "${text.length}/$NOTE_LIMIT",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = ZhangRanTheme.colors.textDisabledColor,
        textAlign = TextAlign.End,
    )
    EditorConfirmButton(
        enabled = !submitting && text.length <= NOTE_LIMIT,
        onClick = { onSubmit(EditableField.NOTE, FieldEditValue.Note(text)) },
    )
}

// ---------------------------------------------------------------- 公共控件

@Composable
private fun DetailTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    val colors = ZhangRanTheme.colors
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(text = placeholder, color = colors.textDisabledColor) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else DetailLayout.noteEditorMinLines,
        shape = ZhangRanTheme.shapes.input,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = colors.inputFillColor,
            unfocusedContainerColor = colors.inputFillColor,
            focusedIndicatorColor = colors.primaryColor,
            unfocusedIndicatorColor = colors.dividerColor,
            focusedTextColor = colors.textPrimaryColor,
            unfocusedTextColor = colors.textPrimaryColor,
        ),
    )
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = DetailLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(
                color = if (selected) colors.surfaceVariantColor else colors.inputFillColor,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = DetailLayout.sheetOptionPaddingDp, horizontal = DetailLayout.sheetPaddingDp),
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
private fun SelectableRow(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DetailLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.option)
            .background(color = if (selected) colors.surfaceVariantColor else colors.modalFillColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(DetailLayout.sheetOptionPaddingDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimaryColor,
        )
        Text(
            text = if (selected) "✓" else "",
            style = MaterialTheme.typography.titleMedium,
            color = colors.primaryColor,
        )
    }
}

@Composable
private fun EditorConfirmButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = DetailLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = ZhangRanTheme.colors.primaryColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = DetailLayout.confirmPaddingDp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "保存修改",
            style = MaterialTheme.typography.titleMedium,
            color = ZhangRanTheme.colors.backgroundColor,
        )
    }
}

/** 备注长度上限（`docs/03` §5）。 */
private const val NOTE_LIMIT: Int = 500
