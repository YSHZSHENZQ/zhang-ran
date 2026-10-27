/**
 * 详情页信息卡（`M2b` §4.1；原型 `.card` / `.card-head` / `.detail-info-row`）。
 *
 * 行清单严格按 `docs/06` §4.6（**不是**原型），并按 A2 隐藏空字段：
 * 类型 / 分类 / **支付方式**（账户的展示位，不得再出现"账户"行） / 来源 / 交易状态，
 * 以及"有值才显示"的置信度、引擎版本、命中规则、交易单号。
 *
 * 文案纪律：`engineVersion` 的中文标签用「引擎版本」，**不用**原型里的 "Parser Version"（`07` §13）。
 *
 * 编辑交互（`AGENTS.md` §7）：**点行本身**即编辑，不提供独立"编辑"按钮；
 * 行不可编辑（`TRANSFER` / `REFUND`）时只读展示。
 */
package com.zhangran.app.ui.screen.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.ui.common.CaptureSourceText
import com.zhangran.app.ui.screen.detail.DetailLayout
import com.zhangran.app.ui.screen.detail.TransactionDetailUi
import com.zhangran.app.ui.screen.detail.confidenceTextOf
import com.zhangran.app.ui.screen.detail.stateTextOf
import com.zhangran.app.ui.screen.detail.typeTextOf
import com.zhangran.app.ui.theme.ZhangRanFontFamily
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 交易信息卡。
 *
 * @param detail 详情数据
 * @param onEdit 点击可编辑行（字段 → 打开对应编辑器）
 */
@Composable
fun DetailInfoCard(
    detail: TransactionDetailUi,
    onEdit: (EditableField) -> Unit,
    modifier: Modifier = Modifier,
) {
    InfoCard(title = "交易信息", modifier = modifier) {
        EditInfoRow("类型", typeTextOf(detail.type), detail, EditableField.TYPE, onEdit, showDivider = false)
        EditInfoRow(
            label = "分类",
            value = detail.categoryName ?: "未选择",
            detail = detail,
            field = EditableField.CATEGORY,
            onEdit = onEdit,
            showDivider = true,
        )
        EditInfoRow(
            label = "支付方式",
            value = detail.accountName ?: "未选择",
            detail = detail,
            field = EditableField.ACCOUNT,
            onEdit = onEdit,
            showDivider = true,
        )
        ReadOnlyInfoRow("来源", CaptureSourceText.of(detail.source), accent = true, showDivider = true)
        ReadOnlyInfoRow("交易状态", stateTextOf(detail.state), showDivider = true)
        detail.confidence?.let { confidence ->
            ReadOnlyInfoRow("置信度", confidenceTextOf(confidence), showDivider = true)
        }
        detail.engineVersion?.takeIf { it.isNotBlank() }?.let {
            ReadOnlyInfoRow("引擎版本", it, mono = true, showDivider = true)
        }
        detail.matchedRuleId?.takeIf { it.isNotBlank() }?.let {
            ReadOnlyInfoRow("命中规则", it, accent = true, mono = true, showDivider = true)
        }
        detail.externalTransactionId?.takeIf { it.isNotBlank() }?.let {
            ReadOnlyInfoRow("交易单号", it, mono = true, showDivider = true)
        }
    }
}

/**
 * 备注卡（整张卡可点 → 编辑备注，原型 `data-edit="detailNote"`）。
 */
@Composable
fun DetailNoteCard(
    detail: TransactionDetailUi,
    onEditNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val editable = detail.fieldsEditable
    val note = detail.note?.takeIf { it.isNotBlank() }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DetailLayout.cardMarginHorizontalDp,
                end = DetailLayout.cardMarginHorizontalDp,
                bottom = DetailLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .clickable(enabled = editable, onClick = onEditNote)
            .semantics {
                contentDescription = if (editable) {
                    "备注，${note ?: "未填写"}，双击修改"
                } else {
                    "备注，${note ?: "未填写"}"
                }
            }
            .padding(DetailLayout.cardPaddingDp),
    ) {
        Text(
            text = "备注",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = DetailLayout.cardHeadFontSizeSp.sp,
                letterSpacing = DetailLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Box(modifier = Modifier.height(DetailLayout.cardHeadGapDp))
        Text(
            text = note ?: "未填写备注",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = DetailLayout.noteFontSizeSp.sp,
                lineHeight = DetailLayout.noteLineHeightSp.sp,
            ),
            color = if (note == null) colors.textDisabledColor else colors.textSecondaryColor,
        )
    }
}

/**
 * 自动解析证据卡（`M2b` §4.1 第 14 项）。
 *
 * A2：证据摘要为空时**整块不显示**（由调用方判断，不渲染占位）。
 */
@Composable
fun DetailEvidenceCard(
    detail: TransactionDetailUi,
    modifier: Modifier = Modifier,
) {
    val summary = detail.evidenceSummary?.takeIf { it.isNotBlank() } ?: return
    val colors = ZhangRanTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DetailLayout.cardMarginHorizontalDp,
                end = DetailLayout.cardMarginHorizontalDp,
                bottom = DetailLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(DetailLayout.cardPaddingDp),
    ) {
        Text(
            text = "自动解析证据",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = DetailLayout.cardHeadFontSizeSp.sp,
                letterSpacing = DetailLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Box(modifier = Modifier.height(DetailLayout.cardHeadGapDp))
        Text(
            text = summary,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DetailLayout.evidenceCornerDp))
                .background(color = colors.surfaceVariantColor)
                .padding(DetailLayout.evidencePaddingDp),
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = DetailLayout.evidenceFontSizeSp.sp,
                lineHeight = DetailLayout.evidenceLineHeightSp.sp,
            ),
            color = colors.textSecondaryColor,
        )
    }
}

@Composable
private fun InfoCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DetailLayout.cardMarginHorizontalDp,
                end = DetailLayout.cardMarginHorizontalDp,
                bottom = DetailLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(DetailLayout.cardPaddingDp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = DetailLayout.cardHeadFontSizeSp.sp,
                letterSpacing = DetailLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Box(modifier = Modifier.height(DetailLayout.cardHeadGapDp))
        content()
    }
}

@Composable
private fun EditInfoRow(
    label: String,
    value: String,
    detail: TransactionDetailUi,
    field: EditableField,
    onEdit: (EditableField) -> Unit,
    showDivider: Boolean,
) {
    InfoRow(
        label = label,
        value = value,
        accent = false,
        mono = false,
        showDivider = showDivider,
        clickable = detail.fieldsEditable,
        contentDescription = if (detail.fieldsEditable) {
            "$label，$value，双击修改"
        } else {
            "$label，$value"
        },
        onClick = { onEdit(field) },
    )
}

@Composable
private fun ReadOnlyInfoRow(
    label: String,
    value: String,
    accent: Boolean = false,
    mono: Boolean = false,
    showDivider: Boolean = true,
) {
    InfoRow(
        label = label,
        value = value,
        accent = accent,
        mono = mono,
        showDivider = showDivider,
        clickable = false,
        contentDescription = "$label，$value",
        onClick = {},
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    accent: Boolean,
    mono: Boolean,
    showDivider: Boolean,
    clickable: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column {
        // 行间分隔线（原型 `.detail-info-row + .detail-info-row { border-top }`：首行不画）
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(DetailLayout.hairlineWidthDp)
                    .background(color = colors.dividerColor),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = clickable, onClick = onClick)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription }
                .padding(vertical = DetailLayout.infoRowPaddingVerticalDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = DetailLayout.infoFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
            )
            Text(
                text = value,
                modifier = Modifier.weight(1.4f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = DetailLayout.infoFontSizeSp.sp,
                    fontFamily = if (mono) FontFamily.Monospace else ZhangRanFontFamily,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (accent) colors.primaryColor else colors.textPrimaryColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End,
            )
        }
    }
}
