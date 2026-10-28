/**
 * 备份页的入口行组件（`M4b-1` §4.7）。
 *
 * 与「我的」页的设置行同构，但只保留本页需要的形态：
 * 图标块 + 标题 + 副文案 + 尾部（箭头/自定义）。
 * 禁用行不显示箭头，避免"看起来可点"（恢复入口必须禁用）。
 */
package com.zhangran.app.ui.screen.backup.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.screen.backup.BackupLayout
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.iconGradientColors
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor

/**
 * @param enabled 禁用时仍展示（恢复入口），但不可点、降权
 * @param onClick 非空时整行可点（右侧显示箭头）
 */
@Composable
fun ExportRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BackupLayout.rowMinHeightDp)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .padding(vertical = BackupLayout.rowPaddingVerticalDp)
            .semantics {
                contentDescription = buildString {
                    append(title)
                    if (!enabled) append("，不可用")
                    if (description.isNotBlank()) {
                        append("，")
                        append(description)
                    }
                    if (onClick != null && enabled) append("，双击进入")
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BackupLayout.rowGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(BackupLayout.rowIconSizeDp)
                .clip(RoundedCornerShape(BackupLayout.rowIconRadiusDp))
                .background(
                    brush = Brush.linearGradient(
                        colors = colors.iconGradientColors(iconGroup),
                    ),
                )
                .graphicsLayer { alpha = if (enabled) 1f else BackupLayout.disabledAlpha },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = BackupLayout.rowIconFontSizeSp.sp,
                ),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = BackupLayout.rowTitleFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) colors.textPrimaryColor else colors.textDisabledColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(BackupLayout.rowSubGapDp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = BackupLayout.rowDescFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
                maxLines = 2,
            )
        }
        trailing()
    }
}

/** 跳转/动作行右侧箭头（禁用时不显示）。 */
@Composable
fun RowArrow(enabled: Boolean = true) {
    if (enabled) {
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = BackupLayout.rowArrowFontSizeSp.sp,
            ),
            color = ZhangRanTheme.colors.textDisabledColor,
        )
    }
}
