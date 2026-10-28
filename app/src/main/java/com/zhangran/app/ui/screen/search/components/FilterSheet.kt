/**
 * 搜索筛选弹层（`M4e` §4.4）：分类/类型/账户选择 + 高级筛选（金额/日期/来源/状态）。
 *
 * 所有弹层共用同一视觉（scrim + 底部卡片 + `MotionDuration.medium`）。
 * 金额输入按元解析为**分（Long）**，全程不用 Double；日期按设备时区取自然日边界。
 */
package com.zhangran.app.ui.screen.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.ui.screen.search.SearchLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.modalFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.scrimColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class PickerOption(val id: String, val label: String)

/** 通用单选弹层（分类/类型/账户共用）。 */
@Composable
fun SearchPickerSheet(
    title: String,
    options: List<PickerOption>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    SheetScaffold(onDismiss = onDismiss) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Column(
            modifier = Modifier
                .heightIn(max = SearchLayout.sheetMaxHeightDp)
                .verticalScroll(rememberScrollState()),
        ) {
            PickerRow(
                label = "全部（清除该筛选）",
                selected = selectedId == null,
                onClick = { onSelect(null) },
            )
            options.forEach { option ->
                PickerRow(
                    label = option.label,
                    selected = option.id == selectedId,
                    onClick = { onSelect(option.id) },
                )
            }
        }
    }
}

@Composable
private fun PickerRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SearchLayout.pickerRowMinHeightDp)
            .clip(ZhangRanTheme.shapes.option)
            .clickable(onClick = onClick)
            .padding(horizontal = SearchLayout.pickerRowPaddingHorizontalDp)
            .semantics { contentDescription = "$label${if (selected) "，已选中" else ""}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = SearchLayout.pickerFontSizeSp.sp),
            color = if (selected) colors.primaryColor else colors.textPrimaryColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
        if (selected) {
            Text(text = "✓", style = MaterialTheme.typography.titleMedium, color = colors.primaryColor)
        }
    }
}

/**
 * 高级筛选：金额 / 日期 / 来源 / 状态。
 *
 * 金额：等值优先（填写等值即忽略区间）；日期：`[from, to)` 半开区间；
 * 来源/状态：单选，再次点击取消。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedFilterSheet(
    initial: SearchCriteria,
    onApply: (
        amountExactMinor: Long?,
        amountMinMinor: Long?,
        amountMaxMinor: Long?,
        dateFromInclusive: Long?,
        dateToExclusive: Long?,
        source: CaptureSource?,
        state: TransactionState?,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    var amountExact by remember { mutableStateOf(initial.amountExactMinor.toPlainYuan()) }
    var amountMin by remember { mutableStateOf(initial.amountMinMinor.toPlainYuan()) }
    var amountMax by remember { mutableStateOf(initial.amountMaxMinor.toPlainYuan()) }
    var dateFrom by remember { mutableStateOf(initial.dateFromInclusive) }
    var dateTo by remember { mutableStateOf(initial.dateToExclusive) }
    var source by remember { mutableStateOf(initial.source) }
    var state by remember { mutableStateOf(initial.state) }
    var datePickerTarget by remember { mutableStateOf<DateTarget?>(null) }

    SheetScaffold(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "高级筛选",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimaryColor,
            )
            TextButton(onClick = onDismiss) {
                Text(text = "取消", color = colors.textDisabledColor)
            }
        }
        Column(
            modifier = Modifier
                .heightIn(max = SearchLayout.advancedSheetMaxHeightDp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SearchLayout.sectionGapDp),
        ) {
            SectionLabel("金额（元）")
            Text(
                text = "填写「等值」时不使用区间",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = SearchLayout.hintFontSizeSp.sp),
                color = colors.textDisabledColor,
            )
            AmountField("等值", amountExact) { amountExact = it }
            AmountField("最小", amountMin) { amountMin = it }
            AmountField("最大", amountMax) { amountMax = it }

            SectionLabel("日期")
            Row(horizontalArrangement = Arrangement.spacedBy(SearchLayout.sectionGapDp)) {
                DateButton(
                    label = dateFrom?.let { "起始 " + formatLocalDate(it) } ?: "起始日期",
                    active = dateFrom != null,
                    modifier = Modifier.weight(1f),
                    onClick = { datePickerTarget = DateTarget.FROM },
                )
                DateButton(
                    label = dateTo?.let { "结束 " + formatLocalDate(it.minusOneDay()) } ?: "结束日期",
                    active = dateTo != null,
                    modifier = Modifier.weight(1f),
                    onClick = { datePickerTarget = DateTarget.TO },
                )
            }

            SectionLabel("来源")
            ChipRow(
                options = CaptureSource.entries.map { it.name to sourceLabel(it) },
                selected = source?.name,
                onSelect = { id -> source = CaptureSource.entries.firstOrNull { it.name == id } },
            )

            SectionLabel("状态")
            ChipRow(
                options = TransactionState.entries.map { it.name to stateLabel(it) },
                selected = state?.name,
                onSelect = { id -> state = TransactionState.entries.firstOrNull { it.name == id } },
            )

            Spacer(modifier = Modifier.height(SearchLayout.itemGapDp))
            Row(horizontalArrangement = Arrangement.spacedBy(SearchLayout.sectionGapDp)) {
                ActionButton(
                    text = "重置",
                    primary = false,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        amountExact = ""
                        amountMin = ""
                        amountMax = ""
                        dateFrom = null
                        dateTo = null
                        source = null
                        state = null
                    },
                )
                ActionButton(
                    text = "应用",
                    primary = true,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        onApply(
                            amountExact.toMinorOrNull(),
                            amountMin.toMinorOrNull(),
                            amountMax.toMinorOrNull(),
                            dateFrom,
                            dateTo,
                            source,
                            state,
                        )
                    },
                )
            }
        }
    }

    val target = datePickerTarget
    if (target != null) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = when (target) {
                DateTarget.FROM -> dateFrom
                DateTarget.TO -> dateTo?.minusOneDay()
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = pickerState.selectedDateMillis
                        if (selected != null) {
                            val start = utcDateToLocalStart(selected)
                            when (target) {
                                DateTarget.FROM -> dateFrom = start
                                DateTarget.TO -> dateTo = start.plusOneDay()
                            }
                        }
                        datePickerTarget = null
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerTarget = null }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

private enum class DateTarget { FROM, TO }

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(
            fontSize = SearchLayout.sectionLabelFontSizeSp.sp,
            letterSpacing = SearchLayout.sectionLabelLetterSpacingSp.sp,
        ),
        color = ZhangRanTheme.colors.textDisabledColor,
    )
}

@Composable
private fun AmountField(label: String, value: String, onValueChange: (String) -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = SearchLayout.fieldMinHeightDp)
            .clip(ZhangRanTheme.shapes.option)
            .background(color = colors.inputFillColor)
            .padding(horizontal = SearchLayout.fieldPaddingHorizontalDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = SearchLayout.fieldLabelFontSizeSp.sp),
            color = colors.textSecondaryColor,
            modifier = Modifier.padding(end = SearchLayout.fieldLabelGapDp),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "0.00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textDisabledColor,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = { input ->
                    // 只允许数字与小数点，最多两位小数
                    if (input.matches(Regex("""\d*\.?\d{0,2}"""))) onValueChange(input)
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimaryColor,
                    fontSize = SearchLayout.fieldInputFontSizeSp.sp,
                ),
                cursorBrush = SolidColor(colors.primaryColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "金额$label" },
            )
        }
        if (value.isNotEmpty()) {
            Text(
                text = "✕",
                modifier = Modifier
                    .clickable { onValueChange("") }
                    .padding(SearchLayout.fieldClearPaddingDp)
                    .semantics { contentDescription = "清除金额$label" },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabledColor,
            )
        }
    }
}

@Composable
private fun DateButton(
    label: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = SearchLayout.fieldMinHeightDp)
            .clip(ZhangRanTheme.shapes.option)
            .background(color = colors.inputFillColor)
            .clickable(onClick = onClick)
            .padding(horizontal = SearchLayout.dateButtonPaddingHorizontalDp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = SearchLayout.fieldLabelFontSizeSp.sp),
            color = if (active) colors.primaryColor else colors.textSecondaryColor,
        )
    }
}

@Composable
private fun ChipRow(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SearchLayout.chipGapDp),
    ) {
        options.forEach { (id, label) ->
            val isSelected = id == selected
            Box(
                modifier = Modifier
                    .heightIn(min = SearchLayout.chipMinHeightDp)
                    .clip(ZhangRanTheme.shapes.badge)
                    .background(
                        color = if (isSelected) {
                            ZhangRanTheme.colors.primaryColor
                        } else {
                            ZhangRanTheme.colors.inputFillColor
                        },
                    )
                    .clickable { onSelect(if (isSelected) null else id) }
                    .padding(horizontal = SearchLayout.chipPaddingHorizontalDp)
                    .semantics {
                        contentDescription = "$label，${if (isSelected) "已选中" else "未选中"}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = SearchLayout.chipFontSizeSp.sp,
                    ),
                    color = if (isSelected) {
                        ZhangRanTheme.colors.backgroundColor
                    } else {
                        ZhangRanTheme.colors.textDisabledColor
                    },
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = SearchLayout.touchTargetDp)
            .clip(ZhangRanTheme.shapes.button)
            .background(color = if (primary) colors.primaryColor else colors.inputFillColor)
            .clickable(onClick = onClick)
            .semantics { contentDescription = text },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (primary) colors.backgroundColor else colors.textSecondaryColor,
        )
    }
}

/** 弹层容器（scrim + 底部卡片 + 进入动画）。 */
@Composable
private fun SheetScaffold(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val colors = ZhangRanTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.scrimColor)
                .clickable(onClick = onDismiss)
                .semantics { contentDescription = "关闭筛选" },
        )
        AnimatedVisibility(
            visible = shown,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(tween(MotionDuration.medium, easing = MotionEasings.ease)) +
                slideInVertically(
                    animationSpec = tween(MotionDuration.medium, easing = MotionEasings.ease),
                    initialOffsetY = { it },
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZhangRanTheme.shapes.dialog)
                    .background(color = colors.backgroundColor)
                    .background(color = colors.modalFillColor)
                    .padding(SearchLayout.sheetPaddingDp),
                verticalArrangement = Arrangement.spacedBy(SearchLayout.itemGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .height(SearchLayout.sheetHandleHeightDp)
                        .fillMaxWidth(SearchLayout.sheetHandleWidthFraction)
                        .clip(ZhangRanTheme.shapes.handle)
                        .background(color = colors.textDisabledColor),
                )
                content()
            }
        }
    }
}

// ------------------------------------------------------------------ 金额/日期工具

/** `Long` 分 → 元字符串（无多余 0；空值返回空串）。 */
private fun Long?.toPlainYuan(): String {
    val minor = this ?: return ""
    return BigDecimal.valueOf(minor, 2).stripTrailingZeros().toPlainString()
}

/** 元字符串 → 分（Long）；非法/空返回 null；超过两位小数视为非法。 */
internal fun String.toMinorOrNull(): Long? {
    if (isBlank()) return null
    return runCatching {
        toBigDecimal().setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact()
    }.getOrNull()
}

private fun formatLocalDate(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

/** DatePicker 返回 UTC 当日 00:00 → 设备时区当日 00:00 的 epoch millis。 */
private fun utcDateToLocalStart(utcMillis: Long): Long {
    val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun Long.minusOneDay(): Long = minusDays(1)
private fun Long.plusOneDay(): Long = plusDays(1)

private fun Long.plusDays(days: Long): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
        .plusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.minusDays(days: Long): Long =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
        .minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
