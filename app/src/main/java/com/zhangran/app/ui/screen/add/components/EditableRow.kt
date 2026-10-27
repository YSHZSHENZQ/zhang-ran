/**
 * 可点击的字段行（任务书 M2a §4.1 的"属性卡片"、`AGENTS.md` §7）。
 *
 * **整行可点**：点击行本身就是修改入口，不额外提供"编辑"按钮（`AGENTS.md` §7）。
 *
 * 视觉按原型 `.set-row`：图标块（`34dp` 圆角）+ 标题 + 右侧值 + `›`。
 */
package com.zhangran.app.ui.screen.add.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.zhangran.app.ui.theme.dividerColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import com.zhangran.app.ui.screen.add.AddLayout
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.xsDp

/**
 * @param icon emoji 图标（账户 💳 / 时间 🕐 / 备注 📝）
 * @param title 字段名（账户 / 时间 / 备注）
 * @param value 右侧展示值（"现金" / "现在" / "添加"）
 * @param onClick 点击整行的回调
 * @param modifier 外部修饰符
 * @param showTopDivider 是否绘制上分隔线（原型 .set-row + .set-row { border-top: 1px solid var(--divider) }）
 */
@Composable
internal fun EditableRow(
    icon: String,
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showTopDivider: Boolean = false,
) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val spacing = ZhangRanTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shapes.option)
            .drawBehind {
                if (!showTopDivider) return@drawBehind
                val stroke = AddLayout.hairlineWidth.toPx()
                drawLine(
                    color = colors.dividerColor,
                    start = Offset(0f, stroke / 2f),
                    end = Offset(size.width, stroke / 2f),
                    strokeWidth = stroke,
                )
            }
            .clickable(onClick = onClick)
            .padding(vertical = spacing.xsDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.mdDp),
    ) {
        Box(
            modifier = Modifier
                .size(AddLayout.rowIconSize)
                .clip(shapes.tile)
                .background(color = colors.surfaceVariantColor, shape = shapes.tile),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                // 原型 add 页内联 .set-icon { font-size: 13px }
                style = MaterialTheme.typography.titleMedium.copy(fontSize = AddLayout.rowIconFontSize),
            )
        }
        Text(
            text = title,
            // 原型 add 页内联 .set-name { font-size: 12.5px }
            style = MaterialTheme.typography.titleMedium.copy(fontSize = AddLayout.rowTitleFontSize),
            color = colors.textPrimaryColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$value ›",
            // 原型内联 12px
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AddLayout.rowValueFontSize),
            color = colors.textDisabledColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
