/**
 * 账户管理页（`M2d-2` 完整重写；行为 SSOT：`docs/06` §4.13，视觉：原型 `data-page="accounts"`）。
 *
 * 结构（分组按 `06` §4.13；原型是"支付账户 / 信用卡"，本实现按 `AccountType` 分组）：
 * ```text
 * ‹ 返回   账户管理                 添加
 * [现金类]     💵 现金      默认账户            ›
 * [银行卡]     🏦 招商银行储蓄卡  尾号 8823     ›
 * [第三方支付] 💳 支付宝                        ›
 * ```
 *
 * 约束：
 * - **无删除入口**（`06` §4.13）；不显示"本月支出"（§1.2 裁定，BACKLOG B5）；
 * - 「默认账户」用按钮（编辑）/ 开关（新增），理由见 `AccountEditorSheet`；
 * - 所有写操作经 `SaveAccountUseCase` / `SetDefaultAccountUseCase`；
 * - `Empty` = 初始化异常（正常至少有 1 个"现金"账户）。
 */
package com.zhangran.app.ui.screen.accounts

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
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
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.usecase.SaveAccountUseCase
import com.zhangran.app.domain.usecase.SetDefaultAccountUseCase
import com.zhangran.app.ui.screen.accounts.components.AccountEditorSheet
import com.zhangran.app.ui.theme.IconGradientGroup
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.iconGradientColors
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

/** 账户管理页入口（无参；导航直接调用）。 */
@Composable
fun AccountsScreen() {
    val context = LocalContext.current
    val activity = context.findActivity()
    val onBack: () -> Unit = { activity?.onBackPressedDispatcher?.onBackPressed() }

    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        AccountsEntryPoint::class.java,
    )
    val viewModel: AccountsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AccountsViewModel(
                    accounts = entryPoint.accountRepository(),
                    saveAccount = entryPoint.saveAccountUseCase(),
                    setDefaultAccount = entryPoint.setDefaultAccountUseCase(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                AccountsEvent.Saved -> snackbarHostState.showSnackbar("已保存")
                is AccountsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AccountsContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::retry,
            onAdd = viewModel::openAdd,
            onEdit = viewModel::openEdit,
            onEditCancel = viewModel::closeEditor,
            onNameChange = viewModel::onNameChange,
            onTypeChange = viewModel::onTypeChange,
            onChannelChange = viewModel::onChannelChange,
            onLast4Change = viewModel::onLast4Change,
            onEnabledChange = viewModel::onEnabledChange,
            onDefaultChange = viewModel::onDefaultChange,
            onSetDefault = viewModel::setDefault,
            onMove = viewModel::move,
            onSave = viewModel::save,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 无状态内容（测试渲染三态与弹层）。 */
@Composable
fun AccountsContent(
    state: AccountsUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onAdd: () -> Unit = {},
    onEdit: (AccountUiItem) -> Unit = {},
    onEditCancel: () -> Unit = {},
    onNameChange: (String) -> Unit = {},
    onTypeChange: (AccountType) -> Unit = {},
    onChannelChange: (PaymentChannel?) -> Unit = {},
    onLast4Change: (String) -> Unit = {},
    onEnabledChange: (Boolean) -> Unit = {},
    onDefaultChange: (Boolean) -> Unit = {},
    onSetDefault: () -> Unit = {},
    onMove: (Boolean) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AccountsPageHead(
                onBack = onBack,
                onAdd = onAdd,
                addEnabled = state is AccountsUiState.Content,
            )

            when (state) {
                AccountsUiState.Loading -> AccountsSkeleton()

                is AccountsUiState.Error -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                AccountsUiState.Empty -> CenteredState {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp),
                    ) {
                        Text(
                            text = "账户数据异常",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimaryColor,
                        )
                        Text(
                            text = "初始账户未写入（正常应至少有 1 个「现金」账户）。请重启应用重试；若仍为空，请检查初始数据。",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textDisabledColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重新加载") }
                    }
                }

                is AccountsUiState.Content -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    state.groups.forEach { group ->
                        AccountGroupCard(group = group, onEdit = onEdit)
                    }
                    Spacer(modifier = Modifier.height(AccountsLayout.pageBottomPaddingDp))
                }
            }
        }

        val editor = (state as? AccountsUiState.Content)?.editor
        if (editor != null) {
            AccountEditorSheet(
                editor = editor,
                onDismiss = onEditCancel,
                onNameChange = onNameChange,
                onTypeChange = onTypeChange,
                onChannelChange = onChannelChange,
                onLast4Change = onLast4Change,
                onEnabledChange = onEnabledChange,
                onDefaultChange = onDefaultChange,
                onSetDefault = onSetDefault,
                onMove = onMove,
                onSave = onSave,
            )
        }
    }
}

/** 顶部返回栏（原型 `.page-head`）。 */
@Composable
private fun AccountsPageHead(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    addEnabled: Boolean,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = AccountsLayout.pageHeadPaddingTopDp,
                bottom = AccountsLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccountsLayout.pageHeadGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(AccountsLayout.backButtonSizeDp)
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = AccountsLayout.backGlyphSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Text(
            text = "账户管理",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimaryColor,
        )
        Text(
            text = "添加",
            modifier = Modifier
                .clip(RoundedCornerShape(AccountsLayout.optionRadiusDp))
                .clickable(enabled = addEnabled, onClick = onAdd)
                .padding(
                    horizontal = AccountsLayout.addPaddingHorizontalDp,
                    vertical = AccountsLayout.addPaddingVerticalDp,
                )
                .semantics { contentDescription = "添加账户" },
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = AccountsLayout.pageActionFontSizeSp.sp,
            ),
            color = if (addEnabled) colors.primaryColor else colors.textDisabledColor,
        )
    }
}

/** 一个分组卡片：组名 + 账户行（行间分隔线）。 */
@Composable
private fun AccountGroupCard(
    group: AccountGroupUi,
    onEdit: (AccountUiItem) -> Unit,
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AccountsLayout.cardMarginHorizontalDp,
                end = AccountsLayout.cardMarginHorizontalDp,
                bottom = AccountsLayout.cardMarginBottomDp,
            )
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.card)
            .clip(ZhangRanTheme.shapes.card)
            .padding(AccountsLayout.cardPaddingDp),
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = AccountsLayout.cardHeadFontSizeSp.sp,
                letterSpacing = AccountsLayout.cardHeadLetterSpacingSp.sp,
            ),
            color = colors.textDisabledColor,
        )
        Spacer(modifier = Modifier.height(AccountsLayout.cardHeadGapDp))
        group.items.forEachIndexed { index, item ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(AccountsLayout.hairlineWidthDp)
                        .background(color = colors.dividerColor),
                )
            }
            AccountRow(item = item, onClick = { onEdit(item) })
        }
    }
}

/** 单个账户行（原型 `.set-row`）。 */
@Composable
private fun AccountRow(item: AccountUiItem, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    val desc = item.descText
    val semanticsText = buildString {
        append(item.name)
        append("，")
        append(accountTypeDescriptionOf(item.type))
        if (desc.isNotEmpty()) {
            append("，")
            append(desc.replace(" · ", "，"))
        }
        append("，双击编辑")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = semanticsText }
            .padding(vertical = AccountsLayout.rowPaddingVerticalDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AccountsLayout.rowGapDp),
    ) {
        AccountIcon(item = item)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = AccountsLayout.rowNameFontSizeSp.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = if (item.enabled) colors.textPrimaryColor else colors.textDisabledColor,
                maxLines = 1,
            )
            if (desc.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AccountsLayout.rowDescTopGapDp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = AccountsLayout.rowDescFontSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                    maxLines = 1,
                )
            }
        }
        Text(
            text = "›",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = AccountsLayout.rowArrowFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

/** 账户图标：34dp 渐变块 + emoji（按 type/channel 选渐变组，补充设计）。 */
@Composable
private fun AccountIcon(item: AccountUiItem) {
    val group = AccountVisuals.gradientFor(item)
    val gradient = ZhangRanTheme.colors.iconGradientColors(group)
    Box(
        modifier = Modifier
            .size(AccountsLayout.rowIconSizeDp)
            .clip(RoundedCornerShape(AccountsLayout.rowIconRadiusDp))
            .background(
                brush = Brush.linearGradient(
                    colors = gradient,
                ),
            )
            .graphicsLayer { alpha = if (item.enabled) 1f else AccountsLayout.disabledAlpha },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = AccountVisuals.emojiFor(item),
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = AccountsLayout.rowIconFontSizeSp.sp,
            ),
        )
    }
}

/** 加载骨架。 */
@Composable
private fun AccountsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(AccountsLayout.pageBottomPaddingDp)) {
        repeat(2) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AccountsLayout.cardMarginHorizontalDp)
                    .zhangRanGlass(colors = ZhangRanTheme.colors, shape = ZhangRanTheme.shapes.card)
                    .clip(ZhangRanTheme.shapes.card)
                    .padding(AccountsLayout.cardPaddingDp),
                verticalArrangement = Arrangement.spacedBy(AccountsLayout.sheetItemGapDp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.3f)
                        .height(AccountsLayout.skeletonLineHeight)
                        .clip(ZhangRanTheme.shapes.option)
                        .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                        .graphicsLayer { alpha = AccountsLayout.skeletonAlpha },
                )
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AccountsLayout.skeletonRowHeight)
                            .clip(ZhangRanTheme.shapes.option)
                            .background(color = ZhangRanTheme.colors.surfaceVariantColor)
                            .graphicsLayer { alpha = AccountsLayout.skeletonAlpha },
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredState(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 从 Compose 的 `Context` 找到宿主 Activity（导航层冻结，返回走 Activity）。 */
private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return current as? ComponentActivity
}

/**
 * 账户图标视觉映射（补充设计：原型只给了 5 个示例图标，未定义完整规则）。
 *
 * 规则：**渠道优先，类型兜底**——渠道能表达"支付宝/微信/银行"的语义，
 * 类型保证没有渠道时仍有稳定图标；渐变复用 `ui/theme` 的 5 组 token。
 */
internal object AccountVisuals {

    fun gradientFor(item: AccountUiItem): IconGradientGroup = when {
        // 信用卡优先按类型（原型：红色卡面），避免 channel=BANK 时被"银行"覆盖
        item.type == AccountType.CREDIT_CARD -> IconGradientGroup.RED
        item.channel == PaymentChannel.ALIPAY -> IconGradientGroup.BLUE
        item.channel == PaymentChannel.WECHAT -> IconGradientGroup.GREEN
        item.channel == PaymentChannel.BANK -> IconGradientGroup.PURPLE
        item.channel == PaymentChannel.CASH -> IconGradientGroup.ORANGE
        item.channel == PaymentChannel.OTHER -> IconGradientGroup.ORANGE
        else -> when (item.type) {
            AccountType.CASH -> IconGradientGroup.ORANGE
            AccountType.DEBIT_CARD -> IconGradientGroup.PURPLE
            AccountType.CREDIT_CARD -> IconGradientGroup.RED
            AccountType.THIRD_PARTY -> IconGradientGroup.BLUE
        }
    }

    fun emojiFor(item: AccountUiItem): String = when {
        item.type == AccountType.CREDIT_CARD -> "💳"
        item.channel == PaymentChannel.ALIPAY -> "💳"
        item.channel == PaymentChannel.WECHAT -> "💚"
        item.channel == PaymentChannel.BANK -> "🏦"
        item.channel == PaymentChannel.CASH -> "💵"
        item.channel == PaymentChannel.OTHER -> "💰"
        else -> when (item.type) {
            AccountType.CASH -> "💵"
            AccountType.DEBIT_CARD -> "🏦"
            AccountType.CREDIT_CARD -> "💳"
            AccountType.THIRD_PARTY -> "📱"
        }
    }
}

/**
 * 页面级尺寸（原型 `data-page="accounts"` / `.set-row` CSS；主题 token 未覆盖的集中在此）。
 */
internal object AccountsLayout {

    // 顶栏（.page-head / .back-btn / .page-head-right）
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 16.dp
    val pageHeadGapDp: Dp = 14.dp
    val backButtonSizeDp: Dp = 34.dp
    val optionRadiusDp: Dp = 10.dp
    val addPaddingHorizontalDp: Dp = 10.dp
    val addPaddingVerticalDp: Dp = 6.dp
    const val backGlyphSizeSp: Float = 18f
    const val pageActionFontSizeSp: Float = 12f

    // 卡片（.card / .card-head）
    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 14.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    // 行（.set-row / .set-icon / .set-name / .set-desc / .set-arrow）
    val rowPaddingVerticalDp: Dp = 13.dp
    val rowGapDp: Dp = 12.dp
    val rowIconSizeDp: Dp = 34.dp
    val rowIconRadiusDp: Dp = 11.dp
    val rowDescTopGapDp: Dp = 3.dp
    val hairlineWidthDp: Dp = 1.dp
    const val rowIconFontSizeSp: Float = 18f
    const val rowNameFontSizeSp: Float = 13.5f
    const val rowDescFontSizeSp: Float = 10.5f
    const val rowArrowFontSizeSp: Float = 14f
    const val disabledAlpha: Float = 0.45f

    // 编辑弹层
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleWidthDp: Dp = 40.dp
    val sheetHandleHeightDp: Dp = 2.dp
    val sheetHandleGapDp: Dp = 12.dp
    val sheetTitleGapDp: Dp = 12.dp
    val sheetItemGapDp: Dp = 12.dp
    val sheetFieldsMaxHeightDp: Dp = 560.dp
    val confirmPaddingDp: Dp = 14.dp
    val controlBorderWidthDp: Dp = 1.dp
    val touchTargetDp: Dp = 48.dp
    const val fieldLabelFontSizeSp: Float = 10.5f

    // 骨架与底部空白
    val skeletonLineHeight: Dp = 10.dp
    val skeletonRowHeight: Dp = 40.dp
    const val skeletonAlpha: Float = 0.5f
    val pageBottomPaddingDp: Dp = 24.dp
}

/** Hilt 入口（不引入 `hilt-navigation-compose`，手工构造 ViewModel）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AccountsEntryPoint {
    fun accountRepository(): AccountRepository

    fun saveAccountUseCase(): SaveAccountUseCase

    fun setDefaultAccountUseCase(): SetDefaultAccountUseCase
}
