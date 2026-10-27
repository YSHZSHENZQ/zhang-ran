/**
 * 余额卡片（`M2e-1b` §4.3；原型 `.balance-card` / `.card-label` / `.card-amount` / `.card-stats`）。
 *
 * 结构（严格按原型）：
 * ```text
 * 本月支出
 * ¥3,847.20            ← 大字（整数部分 36sp + 小数部分 19sp，等宽）
 * INCOME  +12,000.00   BALANCE  8,152.80
 * ```
 *
 * 两条硬约束：
 * 1. **不实现 `RECORDS`**——`docs/06` §131 明确该指标无口径、不实现；
 * 2. 结余为负时显示负号并改用 expense 色（`-¥120.00`），这是**补充规则**
 *    （原型未定义负结余样式，任务书 §4.3 指定）。
 *
 * 金额格式化只调用 `HomeViewModel.formatMoney`（唯一实现，A7）。
 * 顶部彩色强调线来自 `cardTopAccentColors`：**暗色为 `null` → 不绘制**（原型 `--card-top: none`）。
 */
package com.zhangran.app.ui.screen.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.HomeSummary
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeLayout
import com.zhangran.app.ui.screen.home.HomeViewModel
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.cardTopAccentColors
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.incomeColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 余额卡片。
 *
 * @param summary 本月汇总（消费/收入/结余/待确认；本卡片只展示前三项）
 */
@Composable
fun BalanceCard(summary: HomeSummary, modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.dialog

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = HomeLayout.balanceCardHorizontalMarginDp,
                end = HomeLayout.balanceCardHorizontalMarginDp,
                bottom = HomeLayout.balanceCardBottomMarginDp,
            )
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            // 无障碍：整张卡片读作一句完整的话（`M2e-1b` §4.8）
            .semantics(mergeDescendants = true) {
                contentDescription = balanceDescriptionOf(summary)
            },
    ) {
        // 顶部彩色强调线：暗色 token 为 null，不绘制（原型 --card-top: none）
        val topAccent = colors.cardTopAccentColors
        if (topAccent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(HomeLayout.cardTopAccentHeightDp)
                    .background(Brush.horizontalGradient(topAccent)),
            )
        }

        Column(
            modifier = Modifier.padding(
                start = HomeLayout.balanceCardPaddingHorizontalDp,
                end = HomeLayout.balanceCardPaddingHorizontalDp,
                top = HomeLayout.balanceCardPaddingTopDp,
                bottom = HomeLayout.balanceCardPaddingBottomDp,
            ),
        ) {
            Text(
                text = "本月支出",
                style = MaterialTheme.typography.labelMedium,
                color = colors.textDisabledColor,
            )
            Spacer(modifier = Modifier.height(HomeLayout.cardAmountTopPaddingDp))
            Text(
                text = expenseAmountOf(summary.monthExpenseMinor),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = HomeLayout.cardAmountFontSizeSp.sp,
                    letterSpacing = HomeLayout.cardAmountLetterSpacingSp.sp,
                ),
                color = colors.textPrimaryColor,
            )
            Spacer(modifier = Modifier.height(HomeLayout.cardAmountBottomPaddingDp))

            Row(horizontalArrangement = Arrangement.spacedBy(HomeLayout.cardStatsGapDp)) {
                StatColumn(
                    label = "INCOME",
                    value = HomeViewModel.formatMoney(
                        amountMinor = summary.monthIncomeMinor,
                        sign = AmountSign.PLUS,
                        withCurrencySymbol = false,
                    ),
                    valueColor = colors.incomeColor,
                )
                StatColumn(
                    label = "BALANCE",
                    value = balanceAmountOf(summary.monthBalanceMinor),
                    valueColor = if (summary.monthBalanceMinor < 0) colors.expenseColor else null,
                )
            }
        }
    }
}

/**
 * 一列统计（标签 + 值）。
 *
 * @param valueColor 固定颜色；`null` 表示用主题强调渐变（原型 `.stat-v.grad`）；
 *   结余为负时由调用方传入 expense 色
 */
@Composable
private fun StatColumn(
    label: String,
    value: String,
    valueColor: Color?,
) {
    val colors = ZhangRanTheme.colors
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                letterSpacing = HomeLayout.statLabelLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(HomeLayout.statValueTopPaddingDp))
        val style = if (valueColor == null) {
            MaterialTheme.typography.titleMedium.copy(
                brush = Brush.linearGradient(
                    listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                ),
            )
        } else {
            MaterialTheme.typography.titleMedium
        }
        Text(
            text = value,
            style = style,
            color = valueColor ?: Color.Unspecified,
        )
    }
}

/**
 * 「本月支出」大字：整数部分 36sp、小数部分 19sp（原型 `.card-amount small`）。
 *
 * 负数（退款多于消费，`M2e-1a` R9）由 [HomeViewModel.formatMoney] 的 AUTO 符号带出 `-`。
 */
private fun expenseAmountOf(amountMinor: Long): AnnotatedString {
    val text = HomeViewModel.formatMoney(
        amountMinor = amountMinor,
        sign = AmountSign.AUTO,
        withCurrencySymbol = true,
    )
    return splitDecimals(text)
}

/** 「本月结余」：非负用渐变文字，负值用 expense 色并带 `¥`（补充规则）。 */
private fun balanceAmountOf(amountMinor: Long): String = if (amountMinor < 0) {
    HomeViewModel.formatMoney(amountMinor, AmountSign.AUTO, withCurrencySymbol = true)
} else {
    HomeViewModel.formatMoney(amountMinor, AmountSign.NONE, withCurrencySymbol = false)
}

/** 把 `¥1,234.56` 拆成「大字部分 + 小字小数部分」。 */
private fun splitDecimals(text: String): AnnotatedString {
    val dotIndex = text.lastIndexOf('.')
    if (dotIndex < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, dotIndex))
        withStyle(SpanStyle(fontSize = HomeLayout.cardAmountDecimalFontSizeSp.sp)) {
            append(text.substring(dotIndex))
        }
    }
}

/** 无障碍朗读文本（`M2e-1b` §4.8）："本月支出 3847.20 元，本月收入 12000.00 元，本月结余 8152.80 元"。 */
internal fun balanceDescriptionOf(summary: HomeSummary): String {
    val expense = HomeViewModel.formatMoney(summary.monthExpenseMinor, AmountSign.NONE, false)
    val income = HomeViewModel.formatMoney(summary.monthIncomeMinor, AmountSign.NONE, false)
    val balance = HomeViewModel.formatMoney(summary.monthBalanceMinor, AmountSign.NONE, false)
    return "本月支出 $expense 元，本月收入 $income 元，本月结余 $balance 元"
}
