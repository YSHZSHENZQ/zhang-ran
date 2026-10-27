/**
 * 详情页顶部 hero（`M2b` §4.1；原型 `.detail-hero` / `.detail-name` / `.detail-amount` / `.detail-date`）。
 *
 * 结构：居中图标块（56dp，分类渐变色 + emoji）→ 商户名 → 金额（按类型带符号与语义色）→ 时间。
 *
 * 三个**可点击字段**（`AGENTS.md` §7：点字段本身，不加"编辑"按钮）：
 * 商户名 → 商户编辑；金额 → 金额编辑器；时间 → 日期时间选择。
 * `TRANSFER` / `REFUND` 交易只读（`M2b` §1.1）。
 */
package com.zhangran.app.ui.screen.detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.screen.detail.DetailLayout
import com.zhangran.app.ui.screen.detail.TransactionDetailUi
import com.zhangran.app.ui.screen.detail.amountTextOf
import com.zhangran.app.ui.screen.detail.formatDetailDateTime
import com.zhangran.app.ui.screen.home.HomeCategoryVisuals
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.iconGradientBrush
import com.zhangran.app.ui.theme.incomeColor
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.transferColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 详情页 hero。
 *
 * @param detail 详情数据
 * @param onEditMerchant 点击商户名
 * @param onEditAmount 点击金额
 * @param onEditTime 点击时间
 */
@Composable
fun DetailHero(
    detail: TransactionDetailUi,
    onEditMerchant: () -> Unit,
    onEditAmount: () -> Unit,
    onEditTime: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.card
    val editable = detail.fieldsEditable

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = DetailLayout.cardMarginHorizontalDp,
                end = DetailLayout.cardMarginHorizontalDp,
                bottom = DetailLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            .padding(
                start = DetailLayout.heroPaddingHorizontalDp,
                end = DetailLayout.heroPaddingHorizontalDp,
                top = DetailLayout.heroPaddingTopDp,
                bottom = DetailLayout.heroPaddingBottomDp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 分类图标块（原型 56dp / 圆角 18 / emoji 24px）
        Box(
            modifier = Modifier
                .size(DetailLayout.heroIconSizeDp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(DetailLayout.heroIconRadiusDp))
                .background(
                    brush = colors.iconGradientBrush(
                        HomeCategoryVisuals.gradientFor(detail.categoryIcon, detail.type),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = HomeCategoryVisuals.emojiFor(detail.categoryIcon),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = DetailLayout.heroIconFontSizeSp.sp,
                ),
                color = colors.textPrimaryColor,
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(ZhangRanTheme.spacing.mdDp))

        // 商户名（可点编辑；空时给可点的占位文案）
        val merchantText = detail.merchant?.takeIf { it.isNotBlank() } ?: "未填写商户"
        Text(
            text = merchantText,
            modifier = Modifier
                .clickable(enabled = editable, onClick = onEditMerchant)
                .semantics {
                    contentDescription = if (editable) {
                        "商户，$merchantText，双击修改"
                    } else {
                        "商户，$merchantText"
                    }
                },
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = DetailLayout.heroNameFontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = if (detail.merchant.isNullOrBlank()) colors.textDisabledColor else colors.textPrimaryColor,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(DetailLayout.heroAmountTopGapDp))

        // 金额（可点编辑）：符号与颜色按类型
        Text(
            text = amountTextOf(detail.type, detail.amountMinor),
            modifier = Modifier
                .clickable(enabled = editable, onClick = onEditAmount)
                .semantics {
                    contentDescription = "金额，${amountTextOf(detail.type, detail.amountMinor)} 元"
                },
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = DetailLayout.heroAmountFontSizeSp.sp,
                letterSpacing = DetailLayout.heroAmountLetterSpacingSp.sp,
            ),
            color = amountColorOf(detail.type),
        )

        Spacer(modifier = Modifier.height(DetailLayout.heroDateTopGapDp))

        // 时间（可点编辑）：原型 `2024年6月28日 14:23`
        val timeText = formatDetailDateTime(detail.transactionTime)
        Text(
            text = timeText,
            modifier = Modifier
                .clickable(enabled = editable, onClick = onEditTime)
                .semantics {
                    contentDescription = if (editable) "时间，$timeText，双击修改" else "时间，$timeText"
                },
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = DetailLayout.heroDateFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

/** 金额语义色（与首页交易行一致；退款按"钱回来"用收入色）。 */
@Composable
private fun amountColorOf(type: TransactionType): Color {
    val colors = ZhangRanTheme.colors
    return when (type) {
        TransactionType.EXPENSE -> colors.expenseColor
        TransactionType.INCOME -> colors.incomeColor
        TransactionType.TRANSFER -> colors.transferColor
        TransactionType.REFUND -> colors.incomeColor
    }
}
