/**
 * 交易详情（`M2b` 完整重写；`docs/06` §4.6、原型 `data-page="detail"`）。
 *
 * 页面结构（与原型一致，B 类）：
 * ```text
 * ‹ 返回   交易详情                 点击字段可修改
 * [ hero 卡 ]  图标 / 商户 / 金额 / 时间        ← 商户、金额、时间点击即编辑
 * [ 交易信息 ] 类型 / 分类 / 支付方式 / 来源 / 状态 / 有值才显示的证据类字段
 * [ 备注 ]     点击整卡编辑
 * [ 自动解析证据 ]  有证据摘要时才显示
 * [ 删除记录 ]
 * ```
 *
 * 关键约束：
 * - 13 项信息按 `docs/06` §4.6（**不是**原型）：保留「支付方式」标签（即账户），
 *   不并存「账户」行；空字段隐藏（A1/A2）；`engineVersion` 标签用「引擎版本」（A3）。
 * - 字段级编辑：点击行打开底部弹层，**没有**聚合编辑弹窗、没有独立"编辑"按钮（A4）。
 * - 删除：二次确认（文案不承诺恢复路径、不写"无法恢复"，A11）→ 软删除 → 返回首页并弹撤销（A10）。
 * - 状态：Loading / Content / Submitting / Error（A14）；Submitting 时用遮罩禁用交互，防重复提交。
 */
package com.zhangran.app.ui.screen.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavBackStackEntry
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CaptureEvidenceRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.usecase.DeleteTransactionUseCase
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.domain.usecase.FieldEditValue
import com.zhangran.app.domain.usecase.UpdateTransactionFieldUseCase
import com.zhangran.app.ui.common.TransactionDeletionEvents
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.screen.detail.components.DetailEvidenceCard
import com.zhangran.app.ui.screen.detail.components.DetailHero
import com.zhangran.app.ui.screen.detail.components.DetailInfoCard
import com.zhangran.app.ui.screen.detail.components.DetailNoteCard
import com.zhangran.app.ui.screen.detail.components.FieldEditorOverlay
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.expenseColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.scrimColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.zhangRanBackground
import com.zhangran.app.ui.theme.zhangRanGlass
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 详情页入口（导航直接调用，参数从导航 back stack 读取）。
 */
@Composable
fun TransactionDetailScreen() {
    val context = LocalContext.current
    val owner: ViewModelStoreOwner? = LocalViewModelStoreOwner.current
    // 导航层不在本任务白名单内：从 NavBackStackEntry 读取 `transactionId`（路由参数由 M2e-1b 已定义）
    val transactionId = (owner as? NavBackStackEntry)
        ?.arguments
        ?.getString(Destination.ARG_TRANSACTION_ID)
        .orEmpty()
    val activity = context.findActivity()
    val onBack: () -> Unit = { activity?.onBackPressedDispatcher?.onBackPressed() }

    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        TransactionDetailEntryPoint::class.java,
    )
    val viewModel: TransactionDetailViewModel = viewModel(
        key = "detail:$transactionId",
        factory = viewModelFactory {
            initializer {
                TransactionDetailViewModel(
                    transactionId = transactionId,
                    transactions = entryPoint.transactionRepository(),
                    categories = entryPoint.categoryRepository(),
                    accounts = entryPoint.accountRepository(),
                    evidence = entryPoint.captureEvidenceRepository(),
                    updateField = entryPoint.updateTransactionFieldUseCase(),
                    deleteTransaction = entryPoint.deleteTransactionUseCase(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is TransactionDetailEvent.Saved -> onBack()
                is TransactionDetailEvent.Deleted -> {
                    // 首页负责「已删除，可撤销」SnackBar（`M2b` §1.2）
                    TransactionDeletionEvents.publishDeleted(event.transactionId)
                    onBack()
                }

                is TransactionDetailEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TransactionDetailContent(
            state = state,
            onBack = onBack,
            onRetry = viewModel::load,
            onSubmitEdit = viewModel::submit,
            onDelete = viewModel::delete,
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * 详情内容（无状态，便于 Compose 测试渲染三态）。
 *
 * @param state 四态
 * @param onBack 返回
 * @param onRetry 错误态重试
 * @param onSubmitEdit 字段编辑提交（字段 + 新值）
 * @param onDelete 删除（已二次确认）
 */
@Composable
fun TransactionDetailContent(
    state: TransactionDetailUiState,
    onBack: () -> Unit = {},
    onRetry: () -> Unit = {},
    onSubmitEdit: (EditableField, FieldEditValue) -> Unit = { _, _ -> },
    onDelete: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    var editor by remember { mutableStateOf<FieldEditorKind?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailPageHead(
                onBack = onBack,
                hint = "点击字段可修改",
            )

            when (state) {
                TransactionDetailUiState.Loading -> CenteredBox {
                    CircularProgressIndicator(color = colors.primaryColor)
                }

                is TransactionDetailUiState.Error -> CenteredBox {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(DetailLayout.sheetItemGapDp),
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        TextButton(onClick = onRetry) { Text(text = "重试") }
                    }
                }

                is TransactionDetailUiState.Content -> Box(modifier = Modifier.fillMaxSize()) {
                    DetailBody(
                        detail = state.detail,
                        submitting = false,
                        onEdit = { field -> editor = field.toEditorKind() },
                        onEditNote = { editor = FieldEditorKind.Note },
                        onDeleteRequest = { showDeleteConfirm = true },
                    )
                }

                is TransactionDetailUiState.Submitting -> Box(modifier = Modifier.fillMaxSize()) {
                    DetailBody(
                        detail = state.detail,
                        submitting = true,
                        onEdit = {},
                        onEditNote = {},
                        onDeleteRequest = {},
                    )
                    // 提交中：遮罩 + 进度，禁止重复提交（A14）
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(color = colors.scrimColor)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {},
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = colors.primaryColor)
                    }
                }
            }
        }

        val currentDetail = when (state) {
            is TransactionDetailUiState.Content -> state.detail
            is TransactionDetailUiState.Submitting -> state.detail
            else -> null
        }
        val kind = editor
        if (currentDetail != null && kind != null) {
            FieldEditorOverlay(
                kind = kind,
                detail = currentDetail,
                submitting = state is TransactionDetailUiState.Submitting,
                onDismiss = { editor = null },
                onSubmit = { field, value ->
                    editor = null
                    onSubmitEdit(field, value)
                },
            )
        }

        if (showDeleteConfirm) {
            DeleteConfirmDialog(
                onDismiss = { showDeleteConfirm = false },
                onConfirm = {
                    showDeleteConfirm = false
                    onDelete()
                },
            )
        }
    }
}

/** 详情正文（可滚动）。 */
@Composable
private fun DetailBody(
    detail: TransactionDetailUi,
    submitting: Boolean,
    onEdit: (EditableField) -> Unit,
    onEditNote: () -> Unit,
    onDeleteRequest: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        DetailHero(
            detail = detail,
            onEditMerchant = { onEdit(EditableField.MERCHANT) },
            onEditAmount = { onEdit(EditableField.AMOUNT) },
            onEditTime = { onEdit(EditableField.TIME) },
        )
        DetailInfoCard(detail = detail, onEdit = onEdit)
        DetailNoteCard(detail = detail, onEditNote = onEditNote)
        if (detail.evidenceSummary != null) {
            DetailEvidenceCard(detail = detail)
        }
        DeleteButton(
            enabled = !submitting,
            onClick = onDeleteRequest,
        )
        Spacer(modifier = Modifier.height(DetailLayout.pageBottomPaddingDp))
    }
}

/** 顶部返回栏（原型 `.page-head` / `.back-btn` / `.page-title`）。 */
@Composable
private fun DetailPageHead(
    onBack: () -> Unit,
    hint: String,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = ZhangRanTheme.spacing.pagePaddingDp,
                end = ZhangRanTheme.spacing.pagePaddingDp,
                top = DetailLayout.pageHeadPaddingTopDp,
                bottom = DetailLayout.pageHeadPaddingBottomDp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DetailLayout.pageHeadGapDp),
    ) {
        Box(
            modifier = Modifier
                .size(DetailLayout.backButtonSizeDp)
                .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.option)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = DetailLayout.backGlyphSizeSp.sp,
                    fontWeight = FontWeight.Normal,
                ),
                color = colors.textPrimaryColor,
            )
        }
        Text(
            text = "交易详情",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = colors.textPrimaryColor,
        )
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = DetailLayout.pageHintFontSizeSp.sp,
            ),
            color = colors.textDisabledColor,
        )
    }
}

/** 删除按钮（原型 `.detail-btn.danger`）。 */
@Composable
private fun DeleteButton(enabled: Boolean, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = DetailLayout.cardMarginHorizontalDp,
                end = DetailLayout.cardMarginHorizontalDp,
            )
            .heightIn(min = DetailLayout.touchTargetDp)
            .zhangRanGlass(colors = colors, shape = ZhangRanTheme.shapes.button)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = DetailLayout.deletePaddingDp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "删除记录",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = DetailLayout.deleteFontSizeSp.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = colors.expenseColor,
        )
    }
}

/**
 * 删除二次确认（`docs/02` §267）。
 *
 * 文案纪律（A11）：**不写"无法恢复"**，也**不承诺**具体恢复路径
 * （30 天入口已记入 `review/BACKLOG.md` B1，本期只有即时撤销）。
 */
@Composable
private fun DeleteConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "确认删除这笔交易？") },
        text = { Text(text = "记录将从列表中移除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "删除", color = ZhangRanTheme.colors.expenseColor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "取消") }
        },
    )
}

private fun EditableField.toEditorKind(): FieldEditorKind = when (this) {
    EditableField.MERCHANT -> FieldEditorKind.Merchant
    EditableField.AMOUNT -> FieldEditorKind.Amount
    EditableField.TYPE -> FieldEditorKind.Type
    EditableField.CATEGORY -> FieldEditorKind.Category
    EditableField.ACCOUNT -> FieldEditorKind.Account
    EditableField.TIME -> FieldEditorKind.Time
    EditableField.NOTE -> FieldEditorKind.Note
}

@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/** 从 Compose 的 `Context` 找到宿主 Activity（用于返回导航；导航层不在本任务白名单）。 */
private fun Context.findActivity(): androidx.activity.ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is androidx.activity.ComponentActivity) return current
        current = current.baseContext
    }
    return current as? androidx.activity.ComponentActivity
}

/**
 * 详情页页面级尺寸（原型 `data-page="detail"` 的 CSS 取值；主题 token 未覆盖的集中在此）。
 */
internal object DetailLayout {

    // 顶栏（.page-head / .back-btn / .page-title）
    val pageHeadPaddingTopDp: Dp = 4.dp
    val pageHeadPaddingBottomDp: Dp = 16.dp
    val pageHeadGapDp: Dp = 14.dp
    val backButtonSizeDp: Dp = 34.dp
    const val backGlyphSizeSp: Float = 18f
    const val pageHintFontSizeSp: Float = 11f

    // 卡片（.card / .card-head）
    val cardMarginHorizontalDp: Dp = 16.dp
    val cardMarginBottomDp: Dp = 12.dp
    val cardPaddingDp: Dp = 18.dp
    val cardHeadGapDp: Dp = 14.dp
    const val cardHeadFontSizeSp: Float = 10f
    const val cardHeadLetterSpacingSp: Float = 2f

    // hero（.detail-hero / .detail-name / .detail-amount / .detail-date）
    val heroPaddingTopDp: Dp = 28.dp
    val heroPaddingHorizontalDp: Dp = 18.dp
    val heroPaddingBottomDp: Dp = 24.dp
    val heroIconSizeDp: Dp = 56.dp
    val heroIconRadiusDp: Dp = 18.dp
    val heroAmountTopGapDp: Dp = 12.dp
    val heroDateTopGapDp: Dp = 8.dp
    const val heroIconFontSizeSp: Float = 24f
    const val heroNameFontSizeSp: Float = 15f
    const val heroAmountFontSizeSp: Float = 32f
    const val heroAmountLetterSpacingSp: Float = -1.2f
    const val heroDateFontSizeSp: Float = 11f

    // 信息行（.detail-info-row）
    val infoRowPaddingVerticalDp: Dp = 10.dp
    val hairlineWidthDp: Dp = 1.dp
    const val infoFontSizeSp: Float = 13f

    // 备注（.detail-note）
    const val noteFontSizeSp: Float = 13f
    const val noteLineHeightSp: Float = 22f

    // 证据块（.evidence-block）
    val evidencePaddingDp: Dp = 12.dp
    val evidenceCornerDp: Dp = 13.dp
    const val evidenceFontSizeSp: Float = 9.5f
    const val evidenceLineHeightSp: Float = 16f

    // 删除按钮（.detail-btn.danger）
    val deletePaddingDp: Dp = 14.dp
    const val deleteFontSizeSp: Float = 13f

    // 编辑弹层（原型 .modal-*）
    const val sheetHeightFraction: Float = 0.92f
    val sheetPaddingDp: Dp = 18.dp
    val sheetHandleWidthDp: Dp = 40.dp
    val sheetHandleHeightDp: Dp = 2.dp
    val sheetHandleGapDp: Dp = 12.dp
    val sheetTitleGapDp: Dp = 12.dp
    val sheetItemGapDp: Dp = 12.dp
    val sheetOptionPaddingDp: Dp = 12.dp
    val confirmPaddingDp: Dp = 14.dp
    val touchTargetDp: Dp = 48.dp
    const val noteEditorMinLines: Int = 4

    // 页面底部留白
    val pageBottomPaddingDp: Dp = 24.dp
}

/**
 * Hilt 入口（本任务不引入 `hilt-navigation-compose`，故用 EntryPoint 手工构造 ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface TransactionDetailEntryPoint {
    fun transactionRepository(): TransactionRepository

    fun categoryRepository(): CategoryRepository

    fun accountRepository(): AccountRepository

    fun captureEvidenceRepository(): CaptureEvidenceRepository

    fun updateTransactionFieldUseCase(): UpdateTransactionFieldUseCase

    fun deleteTransactionUseCase(): DeleteTransactionUseCase
}
