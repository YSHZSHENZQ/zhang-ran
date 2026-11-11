/**
 * 「我的」页（`M4c` 完整重写；内容项 SSOT：`docs/06` §4.5，视觉：原型 `data-page="settings"`）。
 *
 * 结构（底部导航顶级页，无返回键）：
 * ```text
 * 我的
 * [自动记账] 权限状态 / 总开关 / 状态入口 / 待确认记录（N）
 * [外观]     暗色主题：跟随系统 | 浅色 | 深色
 * [数据管理] 分类 / 账户 / 规则* / 备份* / 日志*      (*标注"即将支持"；预算只保留底栏入口)
 * [关于]     关于账然 / 版本
 * ```
 *
 * 原则（§0.1）：**不可用项显式展示并禁用 + 说明，不隐藏、不伪装可用**。
 */
package com.zhangran.app.ui.screen.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.BuildConfig
import com.zhangran.app.data.autoledger.AutoLedgerStatusSource
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.data.settings.SettingsRepository
import com.zhangran.app.data.settings.ThemeMode
import com.zhangran.app.domain.usecase.SetAutoLedgerEnabledUseCase
import com.zhangran.app.ui.common.RefreshOnResume
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.screen.mine.components.SettingInfoRow
import com.zhangran.app.ui.screen.mine.components.SettingNavRow
import com.zhangran.app.ui.screen.mine.components.SettingSwitchRow
import com.zhangran.app.ui.screen.mine.components.ThemeModeSelector
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 「我的」页入口。
 *
 * @param onNavigate 导航回调（参数为 `Destination` 的 route；由 `ZhangRanNavHost` 注入，经批准的最小接线）
 */
@Composable
fun MineScreen(onNavigate: (String) -> Unit = {}) {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        MineEntryPoint::class.java,
    )
    val viewModel: MineViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                MineViewModel(
                    settingsRepository = entryPoint.settingsRepository(),
                    transactions = entryPoint.transactionRepository(),
                    status = entryPoint.autoLedgerStatusSource(),
                    setEnabled = entryPoint.setAutoLedgerEnabledUseCase(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    RefreshOnResume(viewModel::refreshPermissions)
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is MineEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MineContent(
            state = state,
            onThemeChange = viewModel::setThemeMode,
            onNavigate = onNavigate,
            onRetry = viewModel::refresh,
            onAutoLedgerEnabled = viewModel::setAutoLedgerEnabled,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 无状态内容（测试渲染三态/主题切换/入口跳转）。 */
@Composable
fun MineContent(
    state: MineUiState,
    onThemeChange: (ThemeMode) -> Unit = {},
    onNavigate: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onAutoLedgerEnabled: (Boolean) -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            MineHead(statusLabel = (state as? MineUiState.Content)?.statusLabel)

            when (state) {
                MineUiState.Loading -> MineSkeleton()

                is MineUiState.Error -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(ZhangRanTheme.spacing.pagePaddingDp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(MineLayout.itemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                is MineUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    AutoLedgerSection(
                        pendingCount = state.pendingCount,
                        statusLabel = state.statusLabel,
                        masterEnabled = state.settings.autoLedgerEnabled,
                        accessibilityGranted = state.accessibilityGranted,
                        onNavigate = onNavigate,
                        onAutoLedgerEnabled = onAutoLedgerEnabled,
                    )
                    AppearanceSection(
                        selected = state.settings.themeMode,
                        onThemeChange = onThemeChange,
                    )
                    DataManagementSection(onNavigate = onNavigate)
                    AboutSection(onNavigate = onNavigate)
                    Spacer(modifier = Modifier.height(MineLayout.pageBottomPaddingDp))
                }
            }
        }
    }
}

/** 页头：我的 / SETTINGS + 自动记账状态胶囊。 */
@Composable
private fun MineHead(statusLabel: String?) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = MineLayout.pageHeadPaddingTopDp,
                bottom = MineLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "我的",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = MineLayout.logoFontSizeSp.sp,
                    letterSpacing = MineLayout.logoLetterSpacingSp.sp,
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
                    ),
                ),
            )
            Spacer(modifier = Modifier.height(MineLayout.headSubGapDp))
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = MineLayout.headSubFontSizeSp.sp,
                    letterSpacing = MineLayout.headSubLetterSpacingSp.sp,
                ),
                color = colors.textDisabledColor,
            )
        }
        if (statusLabel != null) {
            Row(
                modifier = Modifier
                    .heightIn(min = MineLayout.pillMinHeightDp)
                    .clip(ZhangRanTheme.shapes.badge)
                    .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.badge)
                    .padding(
                        horizontal = MineLayout.pillPaddingHorizontalDp,
                        vertical = MineLayout.pillPaddingVerticalDp,
                    )
                    .semantics { contentDescription = "自动记账状态：$statusLabel" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MineLayout.pillDotGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .size(MineLayout.pillDotSizeDp)
                        .clip(ZhangRanTheme.shapes.badge)
                        .background(color = colors.textSecondaryColor),
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = MineLayout.pillFontSizeSp.sp,
                    ),
                    color = colors.textSecondaryColor,
                )
            }
        }
    }
}

@Composable
private fun AutoLedgerSection(
    pendingCount: Int,
    statusLabel: String,
    masterEnabled: Boolean,
    accessibilityGranted: Boolean,
    onNavigate: (String) -> Unit,
    onAutoLedgerEnabled: (Boolean) -> Unit,
) {
    MineCard(title = "自动记账") {
        SettingNavRow(
            icon = "🔐",
            iconGroup = IconGradientGroup.BLUE,
            title = "权限状态",
            description = permissionStatusText(accessibilityGranted),
            onClick = { onNavigate(Destination.Onboard.route) },
        )
        SettingSwitchRow(
            icon = "⚡",
            iconGroup = IconGradientGroup.BLUE,
            title = "自动记账",
            description = "总开关只在本页。关闭后不再自动记，已有待确认保留",
            checked = masterEnabled,
            enabled = true,
            onCheckedChange = onAutoLedgerEnabled,
        )
        SettingSwitchRow(
            icon = "🔔",
            iconGroup = IconGradientGroup.GREEN,
            title = "记账提醒",
            description = "暂不支持：提醒机制尚未定义（已记入 BACKLOG B10）",
            checked = false,
            enabled = false,
            onCheckedChange = {},
        )
        SettingNavRow(
            icon = "📡",
            iconGroup = IconGradientGroup.GREEN,
            title = "自动记账状态",
            description = statusLabel,
            onClick = { onNavigate(Destination.AutoLedger.route) },
        )
        SettingNavRow(
            icon = "⏳",
            iconGroup = IconGradientGroup.ORANGE,
            title = "待确认记录",
            description = pendingSummaryText(pendingCount),
            onClick = { onNavigate(Destination.Pending.route) },
        )
    }
}

/** 外观：主题三选一（A4 立即生效 + 持久化）。 */
@Composable
private fun AppearanceSection(selected: ThemeMode, onThemeChange: (ThemeMode) -> Unit) {
    MineCard(title = "外观") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    top = MineLayout.rowPaddingVerticalDp,
                    bottom = MineLayout.themeRowBottomPaddingDp,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MineLayout.rowGapDp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "暗色主题",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = MineLayout.rowTitleFontSizeSp.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = ZhangRanTheme.colors.textPrimaryColor,
                )
                Spacer(modifier = Modifier.height(MineLayout.rowSubGapDp))
                Text(
                    text = themeModeDescriptionOf(selected),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MineLayout.rowDescFontSizeSp.sp,
                    ),
                    color = ZhangRanTheme.colors.textDisabledColor,
                )
            }
            Text(
                text = themeModeLabelOf(selected),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = MineLayout.rowDescFontSizeSp.sp,
                ),
                color = ZhangRanTheme.colors.primaryColor,
            )
        }
        ThemeModeSelector(selected = selected, onSelect = onThemeChange)
    }
}

/** 数据管理：分类/账户可达；规则/备份/日志标注"即将支持"；预算只保留底栏入口。 */
@Composable
private fun DataManagementSection(onNavigate: (String) -> Unit) {
    MineCard(title = "数据管理") {
        SettingNavRow(
            icon = "🏷",
            iconGroup = IconGradientGroup.BLUE,
            title = "分类管理",
            description = "自定义分类、图标与排序",
            onClick = { onNavigate(Destination.Categories.route) },
        )
        SettingNavRow(
            icon = "💳",
            iconGroup = IconGradientGroup.GREEN,
            title = "账户管理",
            description = "现金、银行卡与第三方支付",
            onClick = { onNavigate(Destination.Accounts.route) },
        )

        SettingNavRow(
            icon = "⚙",
            iconGroup = IconGradientGroup.PURPLE,
            title = "解析规则",
            description = "即将支持 · 采集核心接入后可用",
            onClick = { onNavigate(Destination.Rules.route) },
        )
        SettingNavRow(
            icon = "☁",
            iconGroup = IconGradientGroup.BLUE,
            title = "数据备份与恢复",
            description = "即将支持 · 本地备份与 settings.json",
            onClick = { onNavigate(Destination.Backup.route) },
        )
        SettingNavRow(
            icon = "📋",
            iconGroup = IconGradientGroup.GREEN,
            title = "捕获日志",
            description = "时间、来源、包名、状态和原因",
            onClick = { onNavigate(Destination.Logs.route) },
        )
    }
}

/** 关于 + 版本（版本号来自 BuildConfig，禁止硬编码）。 */
@Composable
private fun AboutSection(onNavigate: (String) -> Unit) {
    MineCard(title = "关于") {
        SettingNavRow(
            icon = "ℹ",
            iconGroup = IconGradientGroup.BLUE,
            title = "关于账然",
            description = "开源协议与致谢",
            onClick = { onNavigate(Destination.About.route) },
        )
        SettingInfoRow(
            icon = "🏷",
            iconGroup = IconGradientGroup.PURPLE,
            title = "版本",
            value = "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }
}

/** 分组卡片（标题 + 行内容，行间分隔线）。 */
@Composable
private fun MineCard(title: String, content: @Composable () -> Unit) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = MineLayout.cardMarginHorizontalDp,
                end = MineLayout.cardMarginHorizontalDp,
                bottom = MineLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(MineLayout.cardPaddingDp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = MineLayout.cardHeadFontSizeSp.sp,
                letterSpacing = MineLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(MineLayout.cardHeadGapDp))
        Column(verticalArrangement = Arrangement.spacedBy(MineLayout.rowSpacingDp)) {
            content()
        }
    }
}

/** 加载骨架。 */
@Composable
private fun MineSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(MineLayout.cardMarginBottomDp)) {
        repeat(3) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = MineLayout.cardMarginHorizontalDp)
                    .zhangRanGlass(
                        colors = ZhangRanTheme.colors,
                        shape = ZhangRanTheme.shapes.card,
                    )
                    .clip(ZhangRanTheme.shapes.card)
                    .padding(MineLayout.cardPaddingDp),
                verticalArrangement = Arrangement.spacedBy(MineLayout.itemGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(MineLayout.skeletonHeadFraction)
                        .height(MineLayout.skeletonLineHeight)
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = MineLayout.skeletonAlpha },
                )
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MineLayout.skeletonRowHeight)
                            .clip(ZhangRanTheme.shapes.option)
                            .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                            .graphicsLayer { alpha = MineLayout.skeletonAlpha },
                    )
                }
            }
        }
    }
}

/** 「我的」页页面级尺寸（原型 `data-page="settings"`；token 未覆盖的集中在此）。 */
internal object MineLayout {
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 12.dp
    val headSubGapDp: Dp = 4.dp
    const val logoFontSizeSp: Float = 22f
    const val logoLetterSpacingSp: Float = 3f
    const val headSubFontSizeSp: Float = 9f
    const val headSubLetterSpacingSp: Float = 3.2f
    val pillMinHeightDp: Dp = 26.dp
    val pillPaddingHorizontalDp: Dp = 10.dp
    val pillPaddingVerticalDp: Dp = 4.dp
    val pillDotSizeDp: Dp = 5.dp
    val pillDotGapDp: Dp = 6.dp
    const val pillFontSizeSp: Float = 10f

    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 10.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    val rowMinHeightDp: Dp = 52.dp
    val rowPaddingVerticalDp: Dp = 8.dp
    val rowGapDp: Dp = 12.dp
    val rowSpacingDp: Dp = 6.dp
    val rowIconSizeDp: Dp = 34.dp
    val rowIconRadiusDp: Dp = 11.dp
    const val rowIconFontSizeSp: Float = 18f
    const val rowTitleFontSizeSp: Float = 13.5f
    const val rowDescFontSizeSp: Float = 10.5f
    const val rowArrowFontSizeSp: Float = 14f
    val rowSubGapDp: Dp = 3.dp
    const val disabledAlpha: Float = 0.5f

    val themeRowBottomPaddingDp: Dp = 8.dp
    val themeSelectorBottomPaddingDp: Dp = 4.dp
    val themeOptionGapDp: Dp = 8.dp
    const val themeOptionFontSizeSp: Float = 12f

    val itemGapDp: Dp = 12.dp
    val touchTargetDp: Dp = 48.dp
    val pageBottomPaddingDp: Dp = 24.dp

    const val skeletonHeadFraction: Float = 0.3f
    val skeletonLineHeight: Dp = 10.dp
    val skeletonRowHeight: Dp = 44.dp
    const val skeletonAlpha: Float = 0.5f
}

/** Hilt 入口（不引入 `hilt-navigation-compose`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface MineEntryPoint {
    fun settingsRepository(): SettingsRepository

    fun transactionRepository(): TransactionRepository

    fun autoLedgerStatusSource(): AutoLedgerStatusSource

    fun setAutoLedgerEnabledUseCase(): SetAutoLedgerEnabledUseCase
}
