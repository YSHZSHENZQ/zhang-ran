/**
 * 分类管理页的单组网格（`M2d-1` §4.1；原型 `.card` / `.card-head` / `.cat-grid` / `.cat-item`）。
 *
 * 一个卡片 = 一个分组（支出/收入）：标题 + 提示 + 3 列网格（emoji + 名称）。
 *
 * 与原型的两点差异（已在交付说明记录）：
 * - **列数**：任务书 §4.1 明确要求 3 列（原型 `.cat-grid` 为 5 列）；
 * - **禁用态**：原型未定义禁用样式，本任务用"降低不透明度 + 名称弱化"表示（补充设计）。
 */
package com.zhangran.app.ui.screen.categories.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.screen.categories.CategoriesLayout
import com.zhangran.app.ui.screen.categories.CategoryGroupUi
import com.zhangran.app.ui.screen.categories.CategoryUiItem
import com.zhangran.app.ui.screen.categories.typeLabelOf
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 一组分类卡片。
 *
 * @param group 分组数据（已按 `sortOrder` 升序）
 * @param showEditHint 是否展示"点击分类可编辑"（原型只在支出分组显示）
 * @param onEdit 点击某个分类（打开编辑弹层）
 */
@Composable
fun CategoryGridSection(
    group: CategoryGroupUi,
    showEditHint: Boolean,
    onEdit: (CategoryUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = CategoriesLayout.cardMarginHorizontalDp,
                end = CategoriesLayout.cardMarginHorizontalDp,
                bottom = CategoriesLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(CategoriesLayout.cardPaddingDp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group.title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = CategoriesLayout.cardHeadFontSizeSp.sp,
                    letterSpacing = CategoriesLayout.cardHeadLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
            if (showEditHint) {
                Text(
                    text = "点击分类可编辑",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = CategoriesLayout.cardActionFontSizeSp.sp,
                    ),
                    color = colors.primaryColor,
                )
            }
        }
        Spacer(modifier = Modifier.height(CategoriesLayout.cardHeadGapDp))

        if (group.items.isEmpty()) {
            Text(
                text = "该分组暂无分类",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabledColor,
            )
            return@Column
        }

        Column(verticalArrangement = Arrangement.spacedBy(CategoriesLayout.gridRowGapDp)) {
            group.items.chunked(CategoriesLayout.gridColumns).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(CategoriesLayout.gridColumnGapDp),
                ) {
                    rowItems.forEach { item ->
                        CategoryCell(
                            item = item,
                            typeLabel = typeLabelOf(group.type),
                            onClick = { onEdit(item) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(CategoriesLayout.gridColumns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(
    item: CategoryUiItem,
    typeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) MotionScale.tile else 1f,
        animationSpec = tween(durationMillis = MotionDuration.fast, easing = MotionEasings.ease),
        label = "categoryCellPress",
    )
    val enabledLabel = if (item.enabled) "已启用" else "已禁用"

    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ZhangRanTheme.shapes.option)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .semantics {
                contentDescription = "${item.name}，$typeLabel，$enabledLabel，双击编辑"
            }
            .padding(
                horizontal = CategoriesLayout.cellPaddingHorizontalDp,
                vertical = CategoriesLayout.cellPaddingVerticalDp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CategoriesLayout.cellGapDp),
    ) {
        // 禁用态降权（补充设计：原型未定义）——只降透明度，不加底色块（保持原型"裸 emoji"外观）
        Box(
            modifier = Modifier.size(CategoriesLayout.cellEmojiBoxDp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = CategoryIconMapper.toEmoji(item.icon),
                modifier = Modifier.graphicsLayer { alpha = if (item.enabled) 1f else DISABLED_ALPHA },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = CategoriesLayout.cellEmojiFontSizeSp.sp,
                ),
            )
        }
        Text(
            text = item.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = CategoriesLayout.cellNameFontSizeSp.sp,
                letterSpacing = CategoriesLayout.cellNameLetterSpacingSp.sp,
            ),
            color = if (item.enabled) colors.textSecondaryColor else colors.textDisabledColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** 禁用分类的不透明度（补充设计：原型未定义禁用态）。 */
private const val DISABLED_ALPHA: Float = 0.45f
