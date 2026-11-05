/**
 * 待确认编辑页（M3a，账然内自建）。
 *
 * 字段：商户、金额、类型（支出/收入）、分类、支付方式、绝对时间、备注。
 * 底部「保存并入账」。无交易 id 时只显示页面名，供页面冒烟测试使用。
 */
package com.zhangran.app.ui.screen.pendingEdit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.SavePendingAndBookUseCase
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.surfaceColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.xsDp
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun PendingEditScreen(
    transactionId: String = "",
    onBooked: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    if (transactionId.isBlank()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zhangRanBackground(colors)
                .padding(ZhangRanTheme.spacing.pagePaddingDp),
        ) {
            Text(
                text = Destination.PendingEdit.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimaryColor,
            )
        }
        return
    }
    val context = LocalContext.current
    val editViewModel: PendingEditViewModel = viewModel(
        key = transactionId,
        factory = viewModelFactory {
            initializer {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PendingEditEntryPoint::class.java,
                )
                PendingEditViewModel(
                    transactionId = transactionId,
                    transactions = entryPoint.transactionRepository(),
                    categories = entryPoint.categoryRepository(),
                    accounts = entryPoint.accountRepository(),
                    logs = entryPoint.captureLogRepository(),
                    savePending = entryPoint.savePendingAndBookUseCase(),
                )
            }
        },
    )
    val state by editViewModel.uiState.collectAsState()
    PendingEditContent(
        state = state,
        onMerchant = editViewModel::onMerchant,
        onAmount = editViewModel::onAmount,
        onType = editViewModel::onType,
        onCategory = editViewModel::onCategory,
        onAccount = editViewModel::onAccount,
        onTime = editViewModel::onTime,
        onNote = editViewModel::onNote,
        onSave = { editViewModel.save(onBooked) },
    )
}

@Composable
fun PendingEditContent(
    state: PendingEditUiState,
    onMerchant: (String) -> Unit = {},
    onAmount: (String) -> Unit = {},
    onType: (TransactionType) -> Unit = {},
    onCategory: (String?) -> Unit = {},
    onAccount: (String?) -> Unit = {},
    onTime: (String) -> Unit = {},
    onNote: (String) -> Unit = {},
    onSave: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors)
            .verticalScroll(rememberScrollState())
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
    ) {
        Text(
            text = Destination.PendingEdit.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimaryColor,
        )
        when (state) {
            PendingEditUiState.Loading -> Text("正在读取", color = colors.textSecondaryColor)
            is PendingEditUiState.Missing -> Text(state.message, color = colors.textSecondaryColor)
            is PendingEditUiState.Ready -> PendingEditFormBody(
                form = state.form,
                onMerchant = onMerchant,
                onAmount = onAmount,
                onType = onType,
                onCategory = onCategory,
                onAccount = onAccount,
                onTime = onTime,
                onNote = onNote,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun PendingEditFormBody(
    form: PendingEditForm,
    onMerchant: (String) -> Unit,
    onAmount: (String) -> Unit,
    onType: (TransactionType) -> Unit,
    onCategory: (String?) -> Unit,
    onAccount: (String?) -> Unit,
    onTime: (String) -> Unit,
    onNote: (String) -> Unit,
    onSave: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    form.fixtureId?.let { fixtureId ->
        Text(text = fixtureId, style = MaterialTheme.typography.bodySmall, color = colors.textSecondaryColor)
    }
    OutlinedTextField(
        value = form.merchant,
        onValueChange = onMerchant,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "商户输入" },
        label = { Text("商户") },
        singleLine = true,
    )
    OutlinedTextField(
        value = form.amountText,
        onValueChange = onAmount,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "金额输入" },
        label = { Text("金额") },
        supportingText = { form.amountError?.let { Text(it) } },
        isError = form.amountError != null,
        singleLine = true,
    )
    Text("类型", color = colors.textSecondaryColor)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp)) {
        TypeChip("支出", form.type == TransactionType.EXPENSE) { onType(TransactionType.EXPENSE) }
        TypeChip("收入", form.type == TransactionType.INCOME) { onType(TransactionType.INCOME) }
    }
    Text("分类", color = colors.textSecondaryColor)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp)) {
        form.categories.forEach { choice ->
            ChoiceChip(
                label = choice.name,
                selected = choice.id == form.categoryId,
                onClick = { onCategory(choice.id) },
            )
        }
    }
    form.categoryError?.let { Text(it, color = colors.errorColor) }
    Text("支付方式", color = colors.textSecondaryColor)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp)) {
        ChoiceChip(label = "未选择", selected = form.accountId == null, onClick = { onAccount(null) })
        form.accounts.forEach { choice ->
            ChoiceChip(
                label = choice.name,
                selected = choice.id == form.accountId,
                onClick = { onAccount(choice.id) },
            )
        }
    }
    OutlinedTextField(
        value = form.timeText,
        onValueChange = onTime,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "时间输入" },
        label = { Text("时间") },
        supportingText = { form.timeError?.let { Text(it) } },
        isError = form.timeError != null,
        singleLine = true,
    )
    OutlinedTextField(
        value = form.note,
        onValueChange = onNote,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "备注输入" },
        label = { Text("备注") },
        supportingText = { form.noteError?.let { Text(it) } },
        isError = form.noteError != null,
    )
    form.formError?.let { Text(it, color = colors.errorColor) }
    Button(
        onClick = onSave,
        enabled = !form.saving,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "保存并入账" },
    ) {
        Text(if (form.saving) "正在保存" else "保存并入账")
    }
}

@Composable
private fun TypeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    TextButton(onClick = onClick) {
        Text(text = label, color = if (selected) colors.primaryColor else colors.textPrimaryColor)
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = ZhangRanTheme.colors
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .background(
                if (selected) colors.surfaceColor else colors.surfaceColor,
                ZhangRanTheme.shapes.chip,
            )
            .padding(horizontal = ZhangRanTheme.spacing.xsDp),
    ) {
        Text(text = label, color = if (selected) colors.primaryColor else colors.textPrimaryColor)
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface PendingEditEntryPoint {
    fun transactionRepository(): TransactionRepository

    fun categoryRepository(): CategoryRepository

    fun accountRepository(): AccountRepository

    fun captureLogRepository(): CaptureLogRepository

    fun savePendingAndBookUseCase(): SavePendingAndBookUseCase
}
