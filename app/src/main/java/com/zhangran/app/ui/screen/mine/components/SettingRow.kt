/**
 * 「我的」页的设置行组件（`M4c` §4.2；原型 `.set-row` / `.set-icon` / `.switch`）。
 *
 * 统一处理：
 * - 图标块（34dp、按渐变组着色，复用 `ui/theme` 的 icon 渐变 token）；
 * - 标题 + 副文案；
 * - 尾部：箭头（可跳转）/ 开关（可切换）/ 空；
 * - 禁用态：整体降权 + 读屏明确读出"不可用"（**不隐藏**，A2）。
 */
package com.zhangran.app.ui.screen.mine.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.ui.screen.mine.MineLayout
import com.zhangran.app.ui.screen.mine.themeModeLabelOf
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.iconGradientColors
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor

/**
 * 基础设置行。
 *
 * @param icon emoji（语义图标）
 * @param iconGroup 图标块渐变组（token）
 * @param enabled 禁用时仍展示（A2），但不可点、降权
 * @param onClick 非空时整行可点（右侧显示箭头）
 */
@Composable
fun SettingRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(enabled = enabled, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MineLayout.rowMinHeightDp)
            .then(clickableModifier)
            .padding(vertical = MineLayout.rowPaddingVerticalDp)
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
        horizontalArrangement = Arrangement.spacedBy(MineLayout.rowGapDp),
    ) {
        SettingIcon(icon = icon, group = iconGroup, enabled = enabled)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MineLayout.rowTitleFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (enabled) colors.textPrimaryColor else colors.textDisabledColor,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(MineLayout.rowSubGapDp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MineLayout.rowDescFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
                maxLines = 2,
            )
        }
        trailing()
    }
}

/** 图标块（`.set-icon`：34dp / 圆角 11dp / 渐变底）。 */
@Composable
private fun SettingIcon(icon: String, group: IconGradientGroup, enabled: Boolean) {
    val gradient = ZhangRanTheme.colors.iconGradientColors(group)
    Box(
        modifier = Modifier
            .size(MineLayout.rowIconSizeDp)
            .clip(RoundedCornerShape(MineLayout.rowIconRadiusDp))
            .background(
                brush = androidx.compose.ui.graphics.Brush.linearGradient(colors = gradient),
            )
            .graphicsLayer { alpha = if (enabled) 1f else MineLayout.disabledAlpha },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = MineLayout.rowIconFontSizeSp.sp,
            ),
        )
    }
}

/** 开关行（自动记账总开关 / 记账提醒——后者本期不做）。 */
@Composable
fun SettingSwitchRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    SettingRow(
        icon = icon,
        iconGroup = iconGroup,
        title = title,
        description = description,
        enabled = enabled,
    ) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription =
                    "$title，当前${if (checked) "已开启" else "已关闭"}${if (enabled) "" else "，不可用"}"
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.backgroundColor,
                checkedTrackColor = colors.primaryColor,
                uncheckedThumbColor = colors.textDisabledColor,
                uncheckedTrackColor = colors.surfaceVariantColor,
            ),
        )
    }
}

/** 跳转行（右侧箭头）。 */
@Composable
fun SettingNavRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    SettingRow(
        icon = icon,
        iconGroup = iconGroup,
        title = title,
        description = description,
        enabled = enabled,
        onClick = onClick,
    ) {
        // 禁用行不显示箭头，避免"看起来可进入"的误导（A2）
        if (enabled) {
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = MineLayout.rowArrowFontSizeSp.sp,
                ),
                color = ZhangRanTheme.colors.textDisabledColor,
            )
        }
    }
}

/**
 * 主题三选一（A4）：
 * 跟随系统 / 浅色 / 深色，选中项高亮；禁用时不可点（本页不禁用，保留参数便于测试）。
 */
@Composable
fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    enabled: Boolean = true,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = MineLayout.themeSelectorBottomPaddingDp),
        horizontalArrangement = Arrangement.spacedBy(MineLayout.themeOptionGapDp),
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MineLayout.touchTargetDp)
                    .clip(ZhangRanTheme.shapes.button)
                    .background(
                        color = if (isSelected) colors.surfaceVariantColor else colors.inputFillColor,
                    )
                    .clickable(enabled = enabled) { onSelect(mode) }
                    .semantics {
                        contentDescription =
                            "${themeModeLabelOf(mode)}${if (isSelected) "，已选中" else "，未选中"}"
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = themeModeLabelOf(mode),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MineLayout.themeOptionFontSizeSp.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (isSelected) colors.primaryColor else colors.textSecondaryColor,
                )
            }
        }
    }
}

/** 只读信息行（版本号 / 关于）。 */
@Composable
fun SettingInfoRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    value: String,
) {
    SettingRow(
        icon = icon,
        iconGroup = iconGroup,
        title = title,
        description = value,
        enabled = true,
    ) {
        Spacer(modifier = Modifier.width(MineLayout.rowGapDp))
    }
}
