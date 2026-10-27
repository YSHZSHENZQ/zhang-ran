/**
 * 分类编辑弹层（`M2d-1` §4.4）。
 *
 * 字段：名称（≤ 20 字，补充规则）、类型（**只有添加时可选**，V8）、图标（从已知语义标识里选）、
 * 启用开关（「其他」禁用并说明原因，V3）、上移/下移（不拖拽，避免与滚动冲突）。
 *
 * 保存失败**留在弹层里**就地提示（重名/空名等，A4）；成功由状态驱动关闭。
 */
package com.zhangran.app.ui.screen.categories.components

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
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.usecase.KNOWN_CATEGORY_ICONS
import com.zhangran.app.domain.usecase.MAX_CATEGORY_NAME_LENGTH
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.screen.categories.CategoriesLayout
import com.zhangran.app.ui.screen.categories.CategoryEditorUi
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
 * 分类编辑弹层。
 *
 * @param editor 弹层状态（草稿 + 错误 + saving）
 * @param onDismiss 取消
 * @param onNameChange 名称输入
 * @param onTypeChange 类型切换（只在添加时生效）
 * @param onIconChange 选择图标语义标识
 * @param onEnabledChange 启用开关
 * @param onMove 上移（`true`）/ 下移（`false`）；仅编辑既有分类时可用
 * @param onSave 保存
 */
@Composable
fun CategoryEditorSheet(
    editor: CategoryEditorUi,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onIconChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
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
                onIconChange = onIconChange,
                onEnabledChange = onEnabledChange,
                onMove = onMove,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun SheetPanel(
    editor: CategoryEditorUi,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onTypeChange: (CategoryType) -> Unit,
    onIconChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
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
            // 先用不透明页面底色打底：modalFill 本身带 4% 透明（原型靠 backdrop blur 掩盖，
            // 本项目有意不做模糊），否则弹层上会透出下层分类文字
            .background(color = colors.backgroundColor)
            .background(color = colors.modalFillColor)
            .padding(CategoriesLayout.sheetPaddingDp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(
                    width = CategoriesLayout.sheetHandleWidthDp,
                    height = CategoriesLayout.sheetHandleHeightDp,
                )
                .clip(RoundedCornerShape(CategoriesLayout.sheetHandleHeightDp))
                .background(color = colors.textDisabledColor),
        )
        Spacer(modifier = Modifier.height(CategoriesLayout.sheetHandleGapDp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (editor.editingId == null) "添加分类" else "编辑分类",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimaryColor,
            )
            TextButton(onClick = onDismiss, enabled = !editor.saving) {
                Text(text = "取消", color = colors.textDisabledColor)
            }
        }
        Spacer(modifier = Modifier.height(CategoriesLayout.sheetTitleGapDp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = CategoriesLayout.sheetFieldsMaxHeightDp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp),
        ) {
            // 名称
            TextField(
                value = editor.name,
                onValueChange = onNameChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = editor.nameEditable && !editor.saving,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "分类名称（最多 $MAX_CATEGORY_NAME_LENGTH 字）",
                        color = colors.textDisabledColor,
                    )
                },
                shape = ZhangRanTheme.shapes.input,
                colors = fieldColors(),
            )
            if (!editor.nameEditable) {
                Text(
                    text = "「其他」是兜底分类，不可改名",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textDisabledColor,
                )
            }

            // 类型：只在添加时可选（V8）
            if (editor.typeEditable) {
                FieldLabel(text = "类型")
                Row(horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp)) {
                    TypeChip(
                        text = "支出",
                        selected = editor.type == CategoryType.EXPENSE,
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                        onClick = { onTypeChange(CategoryType.EXPENSE) },
                    )
                    TypeChip(
                        text = "收入",
                        selected = editor.type == CategoryType.INCOME,
                        enabled = !editor.saving,
                        modifier = Modifier.weight(1f),
                        onClick = { onTypeChange(CategoryType.INCOME) },
                    )
                }
            } else {
                FieldLabel(text = "类型（不可修改）")
                Text(
                    text = if (editor.type == CategoryType.EXPENSE) "支出分类" else "收入分类",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textSecondaryColor,
                )
            }

            // 图标：只能从已知语义标识里选（V7，数据库只存标识）
            FieldLabel(text = "图标")
            IconPicker(
                selectedIcon = editor.icon,
                enabled = !editor.saving,
                onSelect = onIconChange,
            )

            // 启用（「其他」锁定为启用，V3）
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
                        text = if (editor.enabledEditable) {
                            "禁用后不出现在记账选择列表，历史记录仍显示"
                        } else {
                            "兜底分类不可禁用"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textDisabledColor,
                    )
                }
                Switch(
                    checked = if (editor.enabledEditable) editor.enabled else true,
                    onCheckedChange = onEnabledChange,
                    enabled = editor.enabledEditable && !editor.saving,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.backgroundColor,
                        checkedTrackColor = colors.primaryColor,
                        uncheckedThumbColor = colors.textDisabledColor,
                        uncheckedTrackColor = colors.surfaceVariantColor,
                    ),
                )
            }

            // 排序：上移/下移（仅编辑既有分类）
            if (editor.editingId != null) {
                FieldLabel(text = "排序")
                Row(horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.sheetItemGapDp)) {
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

            editor.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.errorColor,
                )
            }
        }

        // 保存固定在弹层底部，不随字段区滚动（字段多时也能点到）
        Spacer(modifier = Modifier.height(CategoriesLayout.sheetItemGapDp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CategoriesLayout.touchTargetDp)
                .clip(ZhangRanTheme.shapes.button)
                .background(color = colors.primaryColor)
                .clickable(enabled = !editor.saving, onClick = onSave)
                .padding(vertical = CategoriesLayout.confirmPaddingDp),
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
            fontSize = CategoriesLayout.fieldLabelFontSizeSp.sp,
        ),
        color = ZhangRanTheme.colors.textDisabledColor,
    )
}

@Composable
private fun IconPicker(
    selectedIcon: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val icons = remember { KNOWN_CATEGORY_ICONS.sorted() }
    Column(verticalArrangement = Arrangement.spacedBy(CategoriesLayout.iconPickerRowGapDp)) {
        icons.chunked(CategoriesLayout.iconPickerColumns).forEach { rowIcons ->
            Row(horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.iconPickerGapDp)) {
                rowIcons.forEach { icon ->
                    val selected = icon == selectedIcon
                    Box(
                        modifier = Modifier
                            .size(CategoriesLayout.iconOptionSizeDp)
                            .clip(ZhangRanTheme.shapes.option)
                            .background(
                                color = if (selected) colors.surfaceVariantColor else colors.inputFillColor,
                            )
                            .clickable(enabled = enabled) { onSelect(icon) }
                            .semantics {
                                contentDescription =
                                    "${CategoryIconMapper.toEmoji(icon)} 图标${if (selected) "，已选中" else ""}"
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = CategoryIconMapper.toEmoji(icon),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = CategoriesLayout.iconOptionFontSizeSp.sp,
                            ),
                            modifier = Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.5f },
                        )
                    }
                }
                repeat(CategoriesLayout.iconPickerColumns - rowIcons.size) {
                    Spacer(modifier = Modifier.size(CategoriesLayout.iconOptionSizeDp))
                }
            }
        }
    }
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
            .heightIn(min = CategoriesLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = if (selected) colors.surfaceVariantColor else colors.inputFillColor)
            .border(
                width = CategoriesLayout.controlBorderWidthDp,
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
            .heightIn(min = CategoriesLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = colors.inputFillColor)
            .border(
                width = CategoriesLayout.controlBorderWidthDp,
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
