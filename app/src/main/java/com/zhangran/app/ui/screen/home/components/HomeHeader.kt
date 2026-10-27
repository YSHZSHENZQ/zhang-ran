/**
 * 首页顶栏（`M2e-1b` §4.2；原型 `.app-head` / `.app-logo` / `.app-sub` / `.head-icon`）。
 *
 * 结构：左侧「账然」渐变标题 + 「AUTO LEDGER · 状态」副标题；右侧两个玻璃圆钮
 * （搜索 / 待确认）。副标题与待确认圆钮都可点，分别进「自动记账」与「待确认」。
 *
 * 自动记账状态（`M2e-1b` §4.5）：采集核心**尚未接入**，因此只能显示"未接入"，
 * **不得**显示"正常运行"（`docs/07` §19：不得静默表现为正常）。文案集中在
 * [HomeAutoLedgerStatus]，接入后改这一处即可。
 *
 * 待确认红点（§4.2）：原型 `.dot` 只是一个 7px 无数字的小点；任务书要求"内含数量"，
 * 故此处做成**带数字的小徽标**（> 99 显示 `99+`），属任务书 §4.2 明确要求，
 * 交付说明中标注为"补充规则"。
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.screen.home.HomeLayout
import com.zhangran.app.ui.screen.home.homePressScale
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.warningColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 自动记账状态文案（`M2e-1b` §4.5：集中定义、不得伪造）。
 *
 * TODO(采集接入后改为真实状态)：采集核心接入后，把 [LABEL] 改为按真实可用信号计算的状态，
 * 并与 `docs/07` §19 的"不得静默表现为正常"保持一致。
 */
object HomeAutoLedgerStatus {
    /** 副标题前缀（原型 `.app-sub` 的 `AUTO LEDGER · `）。 */
    const val SUBTITLE_PREFIX: String = "AUTO LEDGER · "

    /** 采集未接入时的状态文案。 */
    const val LABEL: String = "未接入"
}

/**
 * 首页顶栏。
 *
 * @param pendingCount 待确认条数（> 0 时显示红点数字；> 99 显示 `99+`）
 * @param onNavigate 导航回调（参数为 `Destination` 的 route）
 */
@Composable
fun HomeHeader(
    pendingCount: Int,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    val spacing = ZhangRanTheme.spacing

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = spacing.pagePaddingDp,
                end = spacing.pagePaddingDp,
                top = HomeLayout.headerTopPaddingDp,
                bottom = HomeLayout.headerBottomPaddingDp,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            // 「账然」：原型 `.app-logo` 用 `--accent-grad` 做文字渐变
            Text(
                text = "账然",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = HomeLayout.logoFontSizeSp.sp,
                    letterSpacing = HomeLayout.logoLetterSpacingSp.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(HomeLayout.subtitleTopPaddingDp))
            // 副标题同时是「自动记账」入口（`docs/06` §4.1：点击自动记账状态 → AutoLedger）
            Text(
                text = HomeAutoLedgerStatus.SUBTITLE_PREFIX + HomeAutoLedgerStatus.LABEL,
                modifier = Modifier
                    .clickable { onNavigate(Destination.AutoLedger.route) }
                    .semantics {
                        contentDescription = "自动记账状态：" + HomeAutoLedgerStatus.LABEL
                    },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = HomeLayout.subtitleFontSizeSp.sp,
                    letterSpacing = HomeLayout.subtitleLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(HomeLayout.headerActionsGapDp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeIconButton(
                icon = "🔍",
                contentDescription = "搜索",
                onClick = { onNavigate(Destination.Search.route) },
            )
            HomeIconButton(
                icon = "⏳",
                contentDescription = pendingDescriptionOf(pendingCount),
                badgeCount = pendingCount,
                onClick = { onNavigate(Destination.Pending.route) },
            )
        }
    }
}

/** 待确认按钮的无障碍描述（`M2e-1b` §4.8）。 */
private fun pendingDescriptionOf(pendingCount: Int): String =
    if (pendingCount > 0) "待确认 $pendingCount 条" else "待确认"

/**
 * 顶栏玻璃圆钮（原型 `.head-icon`）：34dp 视觉尺寸 + 48dp 触控区 + 按压缩放。
 *
 * @param badgeCount 右上角徽标数字；`<= 0` 不显示
 */
@Composable
private fun HomeIconButton(
    icon: String,
    contentDescription: String,
    onClick: () -> Unit,
    badgeCount: Int = 0,
) {
    val colors = ZhangRanTheme.colors
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(HomeLayout.touchTargetDp)
            // 点击与无障碍落在 48dp 触控区（`M2e-1b` §4.8：图标按钮 ≥ 48dp）；
            // 视觉尺寸仍是原型的 34dp，按压缩放只作用于视觉块
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(HomeLayout.headIconSizeDp)
                .homePressScale(
                    interactionSource = interactionSource,
                    scale = MotionScale.icon,
                )
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clip(ZhangRanTheme.shapes.option),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.labelMedium,
                color = colors.textPrimaryColor,
            )
        }

        if (badgeCount > 0) {
            PendingBadge(
                count = badgeCount,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = HomeLayout.badgeOffsetDp, end = HomeLayout.badgeOffsetDp),
            )
        }
    }
}

/**
 * 待确认红点（补充规则：`> 99` 显示 `99+`；原型 `.dot` 无数字，本任务书要求带数量）。
 */
@Composable
private fun PendingBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = modifier
            .size(HomeLayout.badgeMinSizeDp)
            .background(color = colors.warningColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = HomeLayout.badgeFontSizeSp.sp,
                letterSpacing = HomeLayout.noLetterSpacingSp.sp,
            ),
            color = colors.backgroundColor,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}
