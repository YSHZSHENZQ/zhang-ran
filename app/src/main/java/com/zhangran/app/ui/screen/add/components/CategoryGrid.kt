/**
 * 分类网格（任务书 M2a §4.1/§4.3/§4.4）。
 *
 * - **3 列**网格（任务书 §4.1/§4.4 明确，原型的 5 列在 12 项下过窄；差异已记录）；
 * - 每项 = emoji（由语义标识映射，见 `CategoryIconMapper`）+ 名称；
 * - 选中态：背景 + 名称变强调色；按下：缩放到 `MotionScale.tile`（动效见 `UI-ANIMATION-SPEC`）；
 * - 分类清单来自数据库（P-009 的 12 + 6），**不硬编码**；
 * - 无障碍：读作"餐饮 分类 已选中/未选中"。
 */
package com.zhangran.app.ui.screen.add.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.screen.add.AddLayout
import com.zhangran.app.ui.screen.add.CategoryOption
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.glassBorderColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.smDp

/**
 * 分类网格。
 *
 * @param categories 当前类型下的可用分类（为空时由调用方展示"无分类数据"）
 * @param selectedId 已选分类 id
 * @param onSelect 选中回调
 * @param modifier 外部修饰符
 */
@Composable
internal fun CategoryGrid(
    categories: List<CategoryOption>,
    selectedId: String?,
    onSelect: (CategoryOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ZhangRanTheme.spacing

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AddLayout.catGap),
    ) {
        categories.chunked(AddLayout.gridColumns).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AddLayout.catGap),
            ) {
                rowItems.forEach { option ->
                    CategoryCell(
                        option = option,
                        selected = option.id == selectedId,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // 末行不足 3 项时补空位，保证列宽一致
                repeat(AddLayout.gridColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(modifier = Modifier.size(spacing.smDp))
    }
}

/**
 * 单个分类格。
 */
@Composable
private fun CategoryCell(
    option: CategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // 选中：背景淡入（原型的 .cat-item.selected 有 background .2s）
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.surfaceVariantColor else Color.Transparent,
        animationSpec = tween(durationMillis = MotionDuration.normal, easing = MotionEasings.ease),
        label = "categoryCellBackground",
    )
    // 按下：缩放到原型 `.cat-item:active { transform: scale(.94) }`（.15s）
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionScale.tile else 1f,
        animationSpec = tween(durationMillis = MotionDuration.fast, easing = MotionEasings.ease),
        label = "categoryCellScale",
    )

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shapes.option)
            .then(
                if (selected) {
                    Modifier.border(width = AddLayout.hairlineWidth, color = colors.glassBorderColor, shape = shapes.option)
                } else {
                    Modifier
                },
            )
            .background(color = backgroundColor, shape = shapes.option)
            .semantics {
                contentDescription = "${option.name} 分类 ${if (selected) "已选中" else "未选中"}"
                this.selected = selected
            }
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = AddLayout.catItemPaddingV, horizontal = AddLayout.catItemPaddingH),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AddLayout.catItemGap),
    ) {
        Text(
            text = CategoryIconMapper.toEmoji(option.icon),
            // 原型 .cat-emoji { font-size: 20px }
            style = MaterialTheme.typography.titleMedium.copy(fontSize = AddLayout.catEmojiFontSize),
        )
        Text(
            text = option.name,
            // 原型 .cat-name { font-size: 10px }
            style = MaterialTheme.typography.labelMedium.copy(fontSize = AddLayout.catNameFontSize),
            color = if (selected) colors.primaryColor else colors.textSecondaryColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
