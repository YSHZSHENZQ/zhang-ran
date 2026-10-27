/**
 * 首页三态（`M2e-1b` §4.6；`docs/06` §5 页面统一状态）。
 *
 * - **Loading**：骨架屏（余额卡片 + 3 个交易行占位）。原型没有骨架屏定义，这里用
 *   低对比色块静态占位，属任务书允许的**补充设计**；不做 shimmer 动画（原型无此动效）。
 * - **Empty**：说明"还没有记录" + 两个引导（记一笔 / 检查自动记账）。
 * - **Error**：错误信息 + 重试按钮。
 *
 * 三态都不显示任何示例金额/笔数（`UI_CONSISTENCY_SPEC` §1 C 类）。
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.screen.home.HomeLayout
import com.zhangran.app.ui.screen.home.homePressScale
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanGlass

/** 骨架屏的测试标记（instrumented 断言用）。 */
const val HOME_SKELETON_TAG: String = "home-skeleton"

/** 骨架屏：余额卡片占位 + 3 个交易行占位（`M2e-1b` §4.6）。 */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    Column(modifier = modifier.testTag(HOME_SKELETON_TAG)) {
        SkeletonBalanceCard()
        repeat(HomeLayout.skeletonRowCount) {
            SkeletonRow()
        }
    }
}

@Composable
private fun SkeletonBalanceCard() {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.dialog
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HomeLayout.balanceCardHorizontalMarginDp,
                end = HomeLayout.balanceCardHorizontalMarginDp,
                bottom = HomeLayout.balanceCardBottomMarginDp,
            )
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            .padding(
                start = HomeLayout.balanceCardPaddingHorizontalDp,
                end = HomeLayout.balanceCardPaddingHorizontalDp,
                top = HomeLayout.balanceCardPaddingTopDp,
                bottom = HomeLayout.balanceCardPaddingBottomDp,
            ),
    ) {
        SkeletonBlock(
            widthFraction = HomeLayout.skeletonLabelWidthFraction,
            heightDp = HomeLayout.skeletonLabelHeightDp,
        )
        Spacer(modifier = Modifier.height(HomeLayout.cardAmountTopPaddingDp))
        SkeletonBlock(
            widthFraction = HomeLayout.skeletonAmountWidthFraction,
            heightDp = HomeLayout.skeletonAmountHeightDp,
        )
        Spacer(modifier = Modifier.height(HomeLayout.cardAmountBottomPaddingDp))
        Row(horizontalArrangement = Arrangement.spacedBy(HomeLayout.cardStatsGapDp)) {
            SkeletonBlock(
                widthFraction = HomeLayout.skeletonStatWidthFraction,
                heightDp = HomeLayout.skeletonStatHeightDp,
            )
            SkeletonBlock(
                widthFraction = HomeLayout.skeletonStatWidthFraction,
                heightDp = HomeLayout.skeletonStatHeightDp,
            )
        }
    }
}

@Composable
private fun SkeletonRow() {
    val colors = ZhangRanTheme.colors
    val shape = ZhangRanTheme.shapes.button
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = HomeLayout.rowHorizontalMarginDp,
                end = HomeLayout.rowHorizontalMarginDp,
                bottom = HomeLayout.rowBottomMarginDp,
            )
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            .padding(
                horizontal = HomeLayout.rowPaddingHorizontalDp,
                vertical = HomeLayout.rowPaddingVerticalDp,
            ),
        horizontalArrangement = Arrangement.spacedBy(HomeLayout.rowGapDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SkeletonBlock(
            widthFraction = HomeLayout.skeletonIconWidthFraction,
            heightDp = HomeLayout.iconSizeDp,
            cornerDp = HomeLayout.skeletonBlockCornerDp,
        )
        Column(modifier = Modifier.weight(1f)) {
            SkeletonBlock(
                widthFraction = HomeLayout.skeletonTitleWidthFraction,
                heightDp = HomeLayout.skeletonLineHeightDp,
            )
            Spacer(modifier = Modifier.height(HomeLayout.metaTopPaddingDp))
            SkeletonBlock(
                widthFraction = HomeLayout.skeletonMetaWidthFraction,
                heightDp = HomeLayout.skeletonLineHeightDp,
            )
        }
    }
}

/**
 * 低对比占位块（补充设计：原型无骨架屏）。
 *
 * 颜色取 `textDisabled` 的**低透明度**（不是新颜色 token），只做"有内容待加载"的暗示。
 */
@Composable
private fun SkeletonBlock(
    widthFraction: Float,
    heightDp: Dp,
    cornerDp: Dp = HomeLayout.skeletonBlockCornerDp,
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(heightDp)
            .clip(RoundedCornerShape(cornerDp))
            .background(color = colors.textDisabledColor.copy(alpha = HomeLayout.skeletonAlpha)),
    )
}

/** 空态：说明 + 两个引导（`M2e-1b` §4.6 / `docs/06` §4.1）。 */
@Composable
fun HomeEmptyState(
    onAddTransaction: () -> Unit,
    onCheckAutoLedger: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredState(modifier = modifier) {
        Text(
            text = "还没有记录",
            style = MaterialTheme.typography.titleMedium,
            color = ZhangRanTheme.colors.textPrimaryColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(HomeLayout.stateTextGapDp))
        Text(
            text = "记一笔，或检查自动记账设置",
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = HomeLayout.stateHintFontSizeSp.sp,
            ),
            color = ZhangRanTheme.colors.textSecondaryColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(HomeLayout.stateActionGapDp))
        HomePrimaryButton(text = "记一笔", onClick = onAddTransaction)
        Spacer(modifier = Modifier.height(HomeLayout.stateTextGapDp))
        HomeSecondaryButton(text = "检查自动记账", onClick = onCheckAutoLedger)
    }
}

/** 错误态：信息 + 重试（`M2e-1b` §4.6）。 */
@Composable
fun HomeErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CenteredState(modifier = modifier) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = ZhangRanTheme.colors.expenseColor,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(HomeLayout.stateActionGapDp))
        HomePrimaryButton(text = "重试", onClick = onRetry)
    }
}

@Composable
private fun CenteredState(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = HomeLayout.stateTopPaddingDp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

/** 主按钮（原型 `.onboard-btn` 风格：强调渐变底 + 16dp 圆角）。 */
@Composable
private fun HomePrimaryButton(text: String, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .homePressScale(interactionSource = interactionSource, scale = MotionScale.button)
            .clip(ZhangRanTheme.shapes.button)
            .background(
                Brush.linearGradient(
                    listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                ),
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            // 无障碍最小触控高度（`M2e-1b` §4.8：≥ 48dp）
            .heightIn(min = HomeLayout.touchTargetDp)
            .padding(
                horizontal = HomeLayout.actionButtonPaddingHorizontalDp,
                vertical = HomeLayout.actionButtonPaddingVerticalDp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.backgroundColor,
        )
    }
}

/** 次按钮：玻璃底（与原型次级按钮一致的信息层级）。 */
@Composable
private fun HomeSecondaryButton(text: String, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val shape = ZhangRanTheme.shapes.button
    Box(
        modifier = Modifier
            .homePressScale(interactionSource = interactionSource, scale = MotionScale.button)
            .zhangRanGlass(colors = colors, shape = shape)
            .clip(shape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            // 无障碍最小触控高度（`M2e-1b` §4.8：≥ 48dp）
            .heightIn(min = HomeLayout.touchTargetDp)
            .padding(
                horizontal = HomeLayout.actionButtonPaddingHorizontalDp,
                vertical = HomeLayout.actionButtonPaddingVerticalDp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimaryColor,
        )
    }
}
