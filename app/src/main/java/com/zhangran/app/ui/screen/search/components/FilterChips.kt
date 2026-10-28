/**
 * 快捷筛选 chips（`M4e` §4.4/§4.7；`02` §13：全部 / 分类 / 类型 / 账户）。
 *
 * chips 是**维度快捷入口**，不是新增独立维度；再次点击已选中的 chip → 清除该维度。
 * 选中态颜色过渡 `MotionDuration.fast`；读屏："筛选：分类，已选中：餐饮，双击选择"。
 */
package com.zhangran.app.ui.screen.search.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.screen.search.SearchCategoryOption
import com.zhangran.app.ui.screen.search.SearchLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor

/**
 * @param onAll 点「全部」：清空所有筛选
 * @param onCategory 点「分类」：打开分类选择
 * @param onType 点「类型」：打开类型选择
 * @param onAccount 点「账户」：打开账户选择
 */
@Composable
fun FilterChips(
    criteria: SearchCriteria,
    categoryName: String?,
    accountName: String?,
    onAll: () -> Unit,
    onCategory: () -> Unit,
    onType: () -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(
                start = SearchLayout.pagePaddingHorizontalDp,
                end = SearchLayout.pagePaddingHorizontalDp,
                bottom = SearchLayout.chipsMarginBottomDp,
            ),
        horizontalArrangement = Arrangement.spacedBy(SearchLayout.chipGapDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val noFilter = !criteria.hasAnyFilter()
        Chip(
            label = "全部",
            selected = noFilter,
            semantics = "筛选：全部，${if (noFilter) "已选中" else "未选中"}，双击选择",
            onClick = onAll,
        )
        Chip(
            label = if (criteria.categoryId != null && categoryName != null) {
                "分类：$categoryName"
            } else {
                "分类"
            },
            selected = criteria.categoryId != null,
            semantics = "筛选：分类，${if (criteria.categoryId != null) "已选中" else "未选中"}，双击选择",
            onClick = onCategory,
        )
        Chip(
            label = if (criteria.type != null) "类型：${typeLabel(criteria.type)}" else "类型",
            selected = criteria.type != null,
            semantics = "筛选：类型，${if (criteria.type != null) "已选中" else "未选中"}，双击选择",
            onClick = onType,
        )
        Chip(
            label = if (criteria.accountId != null && accountName != null) {
                "账户：$accountName"
            } else {
                "账户"
            },
            selected = criteria.accountId != null,
            semantics = "筛选：账户，${if (criteria.accountId != null) "已选中" else "未选中"}，双击选择",
            onClick = onAccount,
        )
    }
}

@Composable
private fun Chip(
    label: String,
    selected: Boolean,
    semantics: String,
    onClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val background by animateColorAsState(
        targetValue = if (selected) colors.primaryColor else colors.inputFillColor,
        animationSpec = tween(MotionDuration.fast, easing = MotionEasings.ease),
        label = "chipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.backgroundColor else colors.textDisabledColor,
        animationSpec = tween(MotionDuration.fast, easing = MotionEasings.ease),
        label = "chipContent",
    )
    Row(
        modifier = Modifier
            .heightIn(min = SearchLayout.chipMinHeightDp)
            .clip(ZhangRanTheme.shapes.badge)
            .background(color = background)
            .border(
                width = SearchLayout.chipBorderWidthDp,
                color = if (selected) colors.primaryColor else colors.dividerColor,
                shape = ZhangRanTheme.shapes.badge,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = SearchLayout.chipPaddingHorizontalDp)
            .semantics { contentDescription = semantics },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = SearchLayout.chipFontSizeSp.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = contentColor,
        )
    }
}

/** 类型中文名（与首页一致）。 */
internal fun typeLabel(type: TransactionType): String = when (type) {
    TransactionType.EXPENSE -> "支出"
    TransactionType.INCOME -> "收入"
    TransactionType.TRANSFER -> "转账"
    TransactionType.REFUND -> "退款"
}

/** 来源中文名（与详情/首页一致，7 个枚举）。 */
internal fun sourceLabel(source: CaptureSource): String = when (source) {
    CaptureSource.MANUAL -> "手动"
    CaptureSource.NOTIFICATION -> "通知"
    CaptureSource.HOOK -> "应用内"
    CaptureSource.SMS -> "短信"
    CaptureSource.ACCESSIBILITY -> "无障碍"
    CaptureSource.AI -> "AI"
    CaptureSource.OCR -> "OCR"
}

/** 状态中文名。 */
internal fun stateLabel(state: com.zhangran.app.domain.model.TransactionState): String = when (state) {
    com.zhangran.app.domain.model.TransactionState.PENDING -> "待确认"
    com.zhangran.app.domain.model.TransactionState.CONFIRMED -> "已确认"
    com.zhangran.app.domain.model.TransactionState.IGNORED -> "已忽略"
    com.zhangran.app.domain.model.TransactionState.DELETED -> "已删除"
    com.zhangran.app.domain.model.TransactionState.DUPLICATE -> "重复"
}

/** 分类选项（供 chips 显示名称）。 */
internal fun SearchCategoryOption.shortName(): String = name
