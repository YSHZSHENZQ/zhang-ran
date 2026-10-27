/**
 * 金额大字显示（任务书 M2a §4.1：`¥` + 数字，等宽）。
 *
 * 对齐按原型 `.amount-display { text-align: right }` → **整体靠右**。
 * 无障碍：整体读作"金额 0.00 元"，而不是逐字符朗读（§4.8）。
 */
package com.zhangran.app.ui.screen.add.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zhangran.app.ui.screen.add.AmountInput
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.xsDp

/**
 * @param amount 金额输入缓冲（展示 `amount.display()`）
 * @param modifier 外部修饰符
 */
@Composable
internal fun AmountDisplay(
    amount: AmountInput,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val spacing = ZhangRanTheme.spacing
    val shown = amount.display()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "金额 $shown 元"
            },
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textSecondaryColor,
            modifier = Modifier.padding(end = spacing.xsDp),
        )
        Text(
            text = shown,
            style = MaterialTheme.typography.displayLarge,
            color = colors.textPrimaryColor,
        )
    }
}
