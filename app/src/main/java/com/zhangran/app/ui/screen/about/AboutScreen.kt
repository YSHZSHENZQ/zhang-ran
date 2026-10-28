/**
 * 关于账然（`M4d` 完整重写；行为 SSOT：`docs/06` §4.18，隐私口径：`docs/02` §18/§18.1）。
 *
 * 结构（二级页，无底栏；返回由页面顶部按钮/系统返回）：
 * ```text
 * ‹ 返回   关于账然
 * [头部卡] 📒 / 账然 / 版本 0.1.0 (1) / 不主动，不打扰 / 账目自然清晰
 * [隐私承诺] 4 条（D1 口径，逐字）
 * [信息] 开源协议（可展开） / 采集核心（可展开）
 * ```
 *
 * **只实现 Content 态**：本页是纯静态内容，没有异步数据，因此不存在
 * Loading / Empty / Error（`06` §5 对"若有对应状态则必须实现"的如实声明）。
 *
 * **不实现**：GitHub / 反馈 / 检查更新三个网络入口（`06` §562），也不打开任何外链。
 */
package com.zhangran.app.ui.screen.about

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zhangran.app.BuildConfig
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.iconGradientColors
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass

/** 关于页（无参数，`ScreensSmokeTest` 直接调用）。 */
@Composable
fun AboutScreen() {
    val backDispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(ZhangRanTheme.colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AboutPageHead(
                onBack = {
                    if (backDispatcher != null) {
                        backDispatcher.onBackPressed()
                    } else {
                        // 测试/预览等无 dispatcher 的宿主：安全兜底，不崩溃
                        (context as? android.app.Activity)?.finish()
                    }
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                HeroCard()
                PrivacyCard()
                InfoCard()
                Spacer(modifier = Modifier.height(AboutLayout.pageBottomPaddingDp))
            }
        }
    }
}

/** 顶栏：`‹ 返回 / 关于账然`（原型 `.page-head`）。 */
@Composable
private fun AboutPageHead(onBack: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = AboutLayout.pageHeadPaddingTopDp,
                bottom = AboutLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AboutLayout.pageHeadGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(AboutLayout.backButtonSizeDp)
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clip(ZhangRanTheme.shapes.option)
                .clickable(onClick = onBack)
                .semantics { contentDescription = "返回" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = AboutLayout.backGlyphSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Text(
            text = "关于账然",
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimaryColor,
        )
    }
}

/** 头部卡：logo + 名称 + 真实版本 + 标语。 */
@Composable
private fun HeroCard() {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AboutLayout.cardMarginHorizontalDp,
                vertical = AboutLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(
                horizontal = AboutLayout.heroPaddingHorizontalDp,
                vertical = AboutLayout.heroPaddingVerticalDp,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(AboutLayout.logoBoxSizeDp)
                .clip(RoundedCornerShape(AboutLayout.logoBoxRadiusDp))
                .background(color = colors.inputFillColor)
                .semantics { contentDescription = "账然应用图标" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "📒",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = AboutLayout.logoGlyphSizeSp.sp,
                ),
            )
        }
        Spacer(modifier = Modifier.height(AboutLayout.logoBottomGapDp))
        Text(
            text = AboutInfo.APP_NAME,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontSize = AboutLayout.appNameFontSizeSp.sp,
                letterSpacing = AboutLayout.appNameLetterSpacingSp.sp,
                fontWeight = FontWeight.Bold,
                brush = Brush.linearGradient(
                    listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                ),
            ),
        )
        Spacer(modifier = Modifier.height(AboutLayout.appNameBottomGapDp))
        Text(
            text = AboutInfo.VERSION_PREFIX + BuildConfig.VERSION_NAME +
                " (" + BuildConfig.VERSION_CODE + ")",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = AboutLayout.versionFontSizeSp.sp,
                letterSpacing = AboutLayout.versionLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
            modifier = Modifier.semantics {
                contentDescription =
                    "版本 ${BuildConfig.VERSION_NAME}，构建号 ${BuildConfig.VERSION_CODE}"
            },
        )
        Spacer(modifier = Modifier.height(AboutLayout.sloganTopGapDp))
        Text(
            text = AboutInfo.SLOGAN_LINE_1 + "\n" + AboutInfo.SLOGAN_LINE_2,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = AboutLayout.sloganFontSizeSp.sp,
                lineHeight = AboutLayout.sloganLineHeightSp.sp,
            ),
            color = colors.textSecondaryColor,
        )
    }
}

/** 隐私承诺卡（D1 口径，逐字）。 */
@Composable
private fun PrivacyCard() {
    AboutCard(title = "隐私承诺") {
        AboutInfo.PRIVACY_LINES.forEach { line ->
            Text(
                text = "· $line",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = AboutLayout.privacyFontSizeSp.sp,
                    lineHeight = AboutLayout.privacyLineHeightSp.sp,
                ),
                color = ZhangRanTheme.colors.textSecondaryColor,
            )
        }
    }
}

/** 信息卡：开源协议 / 采集核心（页内展开摘要，无外链）。 */
@Composable
private fun InfoCard() {
    var expanded by remember { mutableStateOf<AboutSection?>(null) }
    AboutCard(title = "信息") {
        AboutInfoRow(
            icon = "📄",
            iconGroup = IconGradientGroup.BLUE,
            title = "开源协议",
            description = AboutInfo.LICENSE_ROW_DESCRIPTION,
            expanded = expanded == AboutSection.LICENSE,
            onClick = { expanded = expanded.toggle(AboutSection.LICENSE) },
        ) {
            Text(
                text = AboutInfo.LICENSE_SUMMARY,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = AboutLayout.expandedFontSizeSp.sp,
                    lineHeight = AboutLayout.expandedLineHeightSp.sp,
                ),
                color = ZhangRanTheme.colors.textSecondaryColor,
            )
        }
        AboutInfoRow(
            icon = "🧩",
            iconGroup = IconGradientGroup.GREEN,
            title = "采集核心",
            description = AboutInfo.CAPTURE_CORE_ROW_DESCRIPTION,
            expanded = expanded == AboutSection.CAPTURE_CORE,
            onClick = { expanded = expanded.toggle(AboutSection.CAPTURE_CORE) },
        ) {
            Text(
                text = AboutInfo.CAPTURE_CORE_SUMMARY,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = AboutLayout.expandedFontSizeSp.sp,
                    lineHeight = AboutLayout.expandedLineHeightSp.sp,
                ),
                color = ZhangRanTheme.colors.textSecondaryColor,
            )
        }
    }
}

private fun AboutSection?.toggle(section: AboutSection): AboutSection? =
    if (this == section) null else section

private enum class AboutSection { LICENSE, CAPTURE_CORE }

/** 可展开的信息行。 */
@Composable
private fun AboutInfoRow(
    icon: String,
    iconGroup: IconGradientGroup,
    title: String,
    description: String,
    expanded: Boolean,
    onClick: () -> Unit,
    detail: @Composable () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZhangRanTheme.shapes.option)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title，双击查看" },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AboutLayout.rowPaddingHorizontalDp,
                    vertical = AboutLayout.rowPaddingVerticalDp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AboutLayout.rowGapDp),
        ) {
            Box(
                modifier = Modifier
                    .size(AboutLayout.rowIconSizeDp)
                    .clip(RoundedCornerShape(AboutLayout.rowIconRadiusDp))
                    .background(
                        brush = Brush.linearGradient(colors.iconGradientColors(iconGroup)),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = AboutLayout.rowIconFontSizeSp.sp,
                    ),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = AboutLayout.rowTitleFontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textPrimaryColor,
                )
                Spacer(modifier = Modifier.height(AboutLayout.rowSubGapDp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = AboutLayout.rowDescFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                )
            }
            Text(
                text = if (expanded) "⌄" else "›",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = AboutLayout.rowArrowFontSizeSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(MotionDuration.medium, easing = MotionEasings.ease)) +
                expandVertically(tween(MotionDuration.medium, easing = MotionEasings.ease)),
            exit = fadeOut(tween(MotionDuration.fast, easing = MotionEasings.ease)) +
                shrinkVertically(tween(MotionDuration.fast, easing = MotionEasings.ease)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = AboutLayout.expandedPaddingHorizontalDp,
                        end = AboutLayout.expandedPaddingHorizontalDp,
                        bottom = AboutLayout.expandedPaddingBottomDp,
                    )
                    .clip(ZhangRanTheme.shapes.option)
                    .background(color = colors.inputFillColor)
                    .padding(AboutLayout.expandedContentPaddingDp),
            ) {
                detail()
            }
        }
    }
}

/** 统一卡片（玻璃底 + 标题 + 内容）。 */
@Composable
private fun AboutCard(title: String, content: @Composable () -> Unit) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = AboutLayout.cardMarginHorizontalDp,
                vertical = AboutLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(AboutLayout.cardPaddingDp),
        verticalArrangement = Arrangement.spacedBy(AboutLayout.cardContentGapDp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = AboutLayout.cardHeadFontSizeSp.sp,
                letterSpacing = AboutLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        content()
    }
}

/** 关于页页面级尺寸（原型 `data-page="about"`；token 未覆盖的集中在此）。 */
internal object AboutLayout {
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 16.dp
    val pageHeadGapDp: Dp = 14.dp
    val backButtonSizeDp: Dp = 34.dp
    const val backGlyphSizeSp: Float = 18f

    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardContentGapDp: Dp = 10.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    val heroPaddingHorizontalDp: Dp = 18.dp
    val heroPaddingVerticalDp: Dp = 26.dp
    val logoBoxSizeDp: Dp = 72.dp
    val logoBoxRadiusDp: Dp = 22.dp
    const val logoGlyphSizeSp: Float = 32f
    val logoBottomGapDp: Dp = 14.dp
    const val appNameFontSizeSp: Float = 20f
    const val appNameLetterSpacingSp: Float = 3f
    val appNameBottomGapDp: Dp = 6.dp
    const val versionFontSizeSp: Float = 11f
    const val versionLetterSpacingSp: Float = 1f
    val sloganTopGapDp: Dp = 16.dp
    const val sloganFontSizeSp: Float = 12f
    const val sloganLineHeightSp: Float = 20f

    const val privacyFontSizeSp: Float = 12.5f
    const val privacyLineHeightSp: Float = 24f

    val rowIconSizeDp: Dp = 30.dp
    val rowIconRadiusDp: Dp = 10.dp
    const val rowIconFontSizeSp: Float = 15f
    val rowGapDp: Dp = 12.dp
    val rowPaddingHorizontalDp: Dp = 2.dp
    val rowPaddingVerticalDp: Dp = 10.dp
    val rowSubGapDp: Dp = 2.dp
    const val rowTitleFontSizeSp: Float = 12.5f
    const val rowDescFontSizeSp: Float = 10.5f
    const val rowArrowFontSizeSp: Float = 14f

    val expandedPaddingHorizontalDp: Dp = 42.dp
    val expandedPaddingBottomDp: Dp = 8.dp
    val expandedContentPaddingDp: Dp = 12.dp
    const val expandedFontSizeSp: Float = 10.5f
    const val expandedLineHeightSp: Float = 18f

    val pageBottomPaddingDp: Dp = 24.dp
}
