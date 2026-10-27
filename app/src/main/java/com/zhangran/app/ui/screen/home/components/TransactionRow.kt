/**
 * 交易行（`M2e-1b` §4.4；原型 `.tx-row` / `.tx-icon` / `.tx-info` / `.tx-name` / `.tx-meta` / `.tx-amt`）。
 *
 * 结构：
 * ```text
 * [渐变图标块] 商户名 / 备注摘要        金额
 *              分类 · HH:mm · 来源
 * ```
 *
 * 约定：
 * - 渐变组与 emoji 由 [HomeCategoryVisuals] 集中决定（未知分类 → 默认橙组 + 默认 emoji，A8）；
 * - 金额符号与颜色按交易类型：支出 `-`（expense 色）、收入 `+`（income 色）、
 *   转账 `-`（transfer 色）、退款 `+`（income 色，**补充规则**：原型无退款样式）；
 * - 点击进详情（带 transactionId 的路由由调用方拼好）。
 */
package com.zhangran.app.ui.screen.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.screen.home.HomeCategoryVisuals
import com.zhangran.app.ui.screen.home.HomeLayout
import com.zhangran.app.ui.screen.home.TransactionListItem
import com.zhangran.app.ui.screen.home.homePressScale
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.iconGradientBrush
import com.zhangran.app.ui.theme.incomeColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.transferColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 一条交易行。
 *
 * @param item 已格式化的展示模型（金额/时间/无障碍文本都由 ViewModel 算好）
 * @param onClick 点击回调（导航到详情）
 */
@Composable
fun TransactionRow(
    item: TransactionListItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.button
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HomeLayout.rowHorizontalMarginDp,
                end = HomeLayout.rowHorizontalMarginDp,
                bottom = HomeLayout.rowBottomMarginDp,
            )
            .homePressScale(interactionSource = interactionSource, scale = MotionScale.row)
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = item.accessibilityText
            }
            .padding(
                horizontal = HomeLayout.rowPaddingHorizontalDp,
                vertical = HomeLayout.rowPaddingVerticalDp,
            ),
        horizontalArrangement = Arrangement.spacedBy(HomeLayout.rowGapDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 渐变图标块（原型 .tx-icon：38dp / 圆角 12 / emoji 16px）
        Box(
            modifier = Modifier
                .size(HomeLayout.iconSizeDp)
                .clip(ZhangRanTheme.shapes.option)
                .background(
                    brush = colors.iconGradientBrush(
                        HomeCategoryVisuals.gradientFor(item.categoryIcon, item.type),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = HomeCategoryVisuals.emojiFor(item.categoryIcon),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = HomeLayout.iconFontSizeSp.sp,
                ),
                color = colors.textPrimaryColor,
                textAlign = TextAlign.Center,
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(HomeLayout.metaTopPaddingDp))
            Text(
                text = metaTextOf(item),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = HomeLayout.metaFontSizeSp.sp,
                    letterSpacing = HomeLayout.noLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(HomeLayout.rowGapDp))
        Text(
            text = item.amountText,
            style = MaterialTheme.typography.displayMedium,
            color = amountColorOf(item.type),
            maxLines = 1,
        )
    }
}

/** 元信息：`分类 · HH:mm · 来源`（空字段自动省略）。 */
private fun metaTextOf(item: TransactionListItem): String = listOfNotNull(
    item.categoryName?.takeIf { it.isNotBlank() },
    item.timeText,
    item.sourceText.takeIf { it.isNotBlank() },
).joinToString(separator = " · ")

/**
 * 金额语义色（`M2e-1b` §4.4）。
 *
 * 退款用收入色是本任务的**补充规则**：原型没有退款行样式；退款是"钱回来"，与收入同向。
 */
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
