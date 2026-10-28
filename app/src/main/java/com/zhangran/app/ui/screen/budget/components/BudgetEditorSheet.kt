/**
 * 预算编辑弹层（`M4a` §4.5/§4.7）。
 *
 * - 金额输入复用 M2a 的 `NumberPad`（`ui/screen/add/components/`，未复制实现）；
 * - 新增分类预算时先选分类（已设预算的分类不出现在选项里，A9 的唯一性前置）；
 * - 删除入口**只在**本弹层内（§4.4.1 / A21），二次确认文案为「删除后本月不再显示该预算」。
 */
package com.zhangran.app.ui.screen.budget.components

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.screen.add.components.NumberPad
import com.zhangran.app.ui.screen.budget.BudgetCategoryOption
import com.zhangran.app.ui.screen.budget.BudgetEditorKind
import com.zhangran.app.ui.screen.budget.BudgetEditorUi
import com.zhangran.app.ui.screen.budget.BudgetLayout
import com.zhangran.app.ui.screen.budget.budgetMoneyText
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.expenseColor
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
 * @param categoryOptions 可添加的分类
 * @param onDismiss 取消
 * @param onSelectCategory 新增时选择分类
 * @param onDigit 数字键
 * @param onDot 小数点
 * @param onBackspace 退格
 * @param onClear 清空
 * @param onSave 保存
 * @param onRequestDelete 删除（弹层内唯一入口）
 * @param onCancelDelete 取消删除确认
 * @param onConfirmDelete 确认删除
 */
@Composable
fun BudgetEditorSheet(
    editor: BudgetEditorUi,
    categoryOptions: List<BudgetCategoryOption>,
    onDismiss: () -> Unit,
    onSelectCategory: (BudgetCategoryOption) -> Unit,
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    onRequestDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .imePadding()
                    .clip(ZhangRanTheme.shapes.dialog)
                    .background(color = colors.backgroundColor)
                    .background(color = colors.modalFillColor)
                    .padding(BudgetLayout.sheetPaddingDp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(BudgetLayout.sheetHandleHeightDp)
                        .fillMaxWidth(BudgetLayout.sheetHandleWidthFraction)
                        .clip(RoundedCornerShape(BudgetLayout.sheetHandleHeightDp))
                        .background(color = colors.textDisabledColor),
                )
                Spacer(modifier = Modifier.height(BudgetLayout.sheetHandleGapDp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = editorTitle(editor),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimaryColor,
                    )
                    TextButton(onClick = onDismiss, enabled = !editor.saving) {
                        Text(text = "取消", color = colors.textDisabledColor)
                    }
                }

                Text(
                    text = editorAmountText(editor),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(vertical = BudgetLayout.amountPaddingVerticalDp),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = BudgetLayout.amountFontSizeSp.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = colors.textPrimaryColor,
                )

                if (editor.kind == BudgetEditorKind.CATEGORY_ADD) {
                    Text(
                        text = "选择分类",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textDisabledColor,
                    )
                    Spacer(modifier = Modifier.height(BudgetLayout.sheetItemGapDp))
                    CategoryPicker(
                        options = categoryOptions,
                        selectedId = editor.categoryId,
                        onSelect = onSelectCategory,
                    )
                    Spacer(modifier = Modifier.height(BudgetLayout.sheetItemGapDp))
                }

                editor.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.errorColor,
                    )
                    Spacer(modifier = Modifier.height(BudgetLayout.sheetItemGapDp))
                }

                if (editor.canDelete) {
                    Text(
                        text = "删除预算",
                        modifier = Modifier
                            .clip(ZhangRanTheme.shapes.button)
                            .clickable(enabled = !editor.saving, onClick = onRequestDelete)
                            .padding(vertical = BudgetLayout.deletePaddingVerticalDp)
                            .semantics { contentDescription = "删除预算" },
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.expenseColor,
                    )
                }

                NumberPad(
                    onDigit = onDigit,
                    onDot = onDot,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    onSave = onSave,
                    saving = editor.saving,
                )
            }
        }

        if (editor.confirmingDelete) {
            AlertDialog(
                onDismissRequest = onCancelDelete,
                title = { Text(text = "删除这笔预算？") },
                text = { Text(text = "删除后本月不再显示该预算") },
                confirmButton = {
                    TextButton(onClick = onConfirmDelete) {
                        Text(text = "删除", color = colors.expenseColor)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onCancelDelete) { Text(text = "取消") }
                },
            )
        }
    }
}

private fun editorTitle(editor: BudgetEditorUi): String = when (editor.kind) {
    BudgetEditorKind.TOTAL -> "月度总预算"
    BudgetEditorKind.CATEGORY_ADD -> "添加分类预算"
    BudgetEditorKind.CATEGORY_EDIT -> "分类预算 · ${editor.categoryName.orEmpty()}"
}

/** 金额展示：`¥` + 输入文本（空输入显示 0.00）。 */
private fun editorAmountText(editor: BudgetEditorUi): String {
    val text = if (editor.amount.text.isEmpty()) "0.00" else editor.amount.text
    return "¥$text"
}

@Composable
private fun CategoryPicker(
    options: List<BudgetCategoryOption>,
    selectedId: String?,
    onSelect: (BudgetCategoryOption) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    if (options.isEmpty()) {
        Text(
            text = "所有分类都已设置预算",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textDisabledColor,
        )
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = BudgetLayout.categoryPickerMaxHeightDp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(BudgetLayout.sheetItemGapDp),
    ) {
        options.chunked(BudgetLayout.categoryPickerColumns).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(BudgetLayout.sheetItemGapDp)) {
                rowItems.forEach { option ->
                    val selected = option.id == selectedId
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = BudgetLayout.touchTargetDp)
                            .clip(ZhangRanTheme.shapes.button)
                            .background(
                                color = if (selected) colors.surfaceVariantColor else colors.inputFillColor,
                            )
                            .border(
                                width = BudgetLayout.controlBorderWidthDp,
                                color = colors.dividerColor,
                                shape = ZhangRanTheme.shapes.button,
                            )
                            .clickable { onSelect(option) }
                            .padding(horizontal = BudgetLayout.categoryChipPaddingHorizontalDp)
                            .semantics {
                                contentDescription =
                                    "${option.name}${if (selected) "，已选中" else ""}"
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(BudgetLayout.categoryChipGapDp),
                    ) {
                        Text(
                            text = CategoryIconMapper.toEmoji(option.icon),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = BudgetLayout.categoryChipEmojiSizeSp.sp,
                            ),
                        )
                        Text(
                            text = option.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (selected) colors.primaryColor else colors.textSecondaryColor,
                        )
                    }
                }
                repeat(BudgetLayout.categoryPickerColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 预览用：把金额文案统一走 `Money` 格式化（弹层显示用原始输入文本，此处仅供外部复用）。 */
@Suppress("unused")
internal fun previewAmountText(amountMinor: Long): String = budgetMoneyText(amountMinor)
