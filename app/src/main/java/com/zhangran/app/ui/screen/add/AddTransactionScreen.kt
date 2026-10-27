/**
 * 记一笔（手工记账表单，任务书 M2a）。
 *
 * 结构按原型 `data-page="add"`：
 * ```text
 * [顶栏] 记一笔 / MANUAL ENTRY          [支出 ⇄ 收入]
 * [金额卡]（glass）  ¥ 0.00
 * [分类网格] 3 列（emoji + 名称）
 * [属性卡]（glass） 账户 / 时间 / 备注（整行可点）
 * [数字键盘]（固定底部）
 * ```
 *
 * 与原型的两处**有意识差异**（已在任务报告记录）：
 * 1. 分类网格为 **3 列**（原型 `repeat(5, 1fr)`）：任务书 §4.1/§4.4 明确要求，且 12 个分类在 5 列下过窄；
 * 2. 选择器用 **Dialog**（原型为底部弹层）：本任务不引入额外依赖，交互等价；
 *    账户/时间/备注三行仍是"点击整行即修改"，**没有**重复的编辑按钮（`AGENTS.md` §7）。
 *
 * 尺寸说明：`AddLayout` 集中承载原型中未被主题 token 覆盖的布局尺寸（键高 52dp、图标块 34dp、
 * 网格 3 列等）。`ui/theme/` 在本任务中属禁改范围，故这些值无法上收为 token；集中一处便于将来迁移。
 */
package com.zhangran.app.ui.screen.add

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.AddManualTransactionUseCase
import com.zhangran.app.ui.screen.add.components.AmountDisplay
import com.zhangran.app.ui.screen.add.components.CategoryGrid
import com.zhangran.app.ui.screen.add.components.EditableRow
import com.zhangran.app.ui.screen.add.components.NumberPad
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.backgroundColor
import com.zhangran.app.ui.theme.zhangRanGlass
import com.zhangran.app.ui.theme.lgDp
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.xsDp
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.surfaceColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 页面级布局尺寸（集中定义，避免散落裸数字）。
 *
 * 这些值来自原型 CSS，但现有主题 token 未覆盖；`ui/theme/` 属本任务禁改范围，
 * 因此先集中在此处，待后续任务上收为 token。
 */
internal object AddLayout {
    /** 键盘键高（原型 `.key { height: 52px }`）。 */
    val keyHeight = 52.dp

    /** 键盘键间距（原型 `.keypad { gap: 8px }`）。 */
    val keyGap = 8.dp

    /** 分类网格间距（原型 `.cat-grid { gap: 10px }`）。 */
    val catGap = 10.dp

    /** 分类格内边距（原型 `.cat-item { padding: 10px 4px }`）。 */
    val catItemPaddingV = 10.dp
    val catItemPaddingH = 4.dp

    /** 分类格内 emoji 与名称的间距（原型 `.cat-item { gap: 5px }`）。 */
    val catItemGap = 5.dp

    /** 网格列数（任务书 §4.1/§4.4：3 列；原型为 5 列，差异见文件头）。 */
    const val gridColumns: Int = 3

    /** 属性行图标块尺寸（原型 `.set-icon { width/height: 34px }`）。 */
    val rowIconSize = 34.dp

    /** 发丝线宽（选中描边）。 */
    val hairlineWidth = 1.dp

    /** 卡片内边距（原型 `.card { padding: 18px }`）。 */
    val cardPadding = 18.dp

    /** 页面水平内边距（原型 `.app-head { padding: 4px 22px 16px }`）。 */
    val pagePaddingH = 22.dp

    /** 页面顶部内边距（原型 `.app-head` 的上 4px）。 */
    val pagePaddingTop = 4.dp

    /** 顶栏与首个卡片之间（原型 `.app-head` 的下 16px）。 */
    val pagePaddingBottom = 16.dp
}

/**
 * 记一笔页面（有状态入口；`ScreensSmokeTest` 以无参方式调用）。
 */
@Composable
fun AddTransactionScreen() {
    val context = LocalContext.current
    val viewModel: AddTransactionViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    AddEntryPoint::class.java,
                )
                AddTransactionViewModel(
                    addTransaction = entryPoint.addManualTransactionUseCase(),
                    categoryRepository = entryPoint.categoryRepository(),
                    accountRepository = entryPoint.accountRepository(),
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsState()

    AddTransactionContent(
        state = state,
        onToggleType = viewModel::toggleType,
        onDigit = viewModel::pressDigit,
        onDot = viewModel::pressDot,
        onBackspace = viewModel::backspace,
        onClear = viewModel::clearAmount,
        onSave = viewModel::save,
        onSelectCategory = { viewModel.selectCategory(it.id) },
        onSelectAccount = viewModel::selectAccount,
        onSetTime = viewModel::setTransactionTime,
        onSetNote = viewModel::setNote,
        onConfirmCustomCategory = viewModel::confirmCustomCategory,
        onRetry = viewModel::load,
    )
}

/**
 * 记账表单内容（**无状态**，便于四态渲染测试）。
 *
 * 所有回调都有默认值，测试可只传 `state`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionContent(
    state: AddTransactionUiState,
    onToggleType: () -> Unit = {},
    onDigit: (Char) -> Unit = {},
    onDot: () -> Unit = {},
    onBackspace: () -> Unit = {},
    onClear: () -> Unit = {},
    onSave: () -> Unit = {},
    onSelectCategory: (CategoryOption) -> Unit = {},
    onSelectAccount: (String) -> Unit = {},
    onSetTime: (Long?) -> Unit = {},
    onSetNote: (String) -> Unit = {},
    onConfirmCustomCategory: (String) -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    val spacing = ZhangRanTheme.spacing

    var showCustomCategory by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var showNoteEditor by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.backgroundColor)
            .padding(top = AddLayout.pagePaddingTop),
    ) {
        AddTopBar(state = state, onToggleType = onToggleType)

        when (state) {
            is AddTransactionUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.primaryColor)
                }
            }

            is AddTransactionUiState.LoadFailed -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.errorColor,
                        )
                        Spacer(modifier = Modifier.height(spacing.smDp))
                        Button(onClick = onRetry) { Text("重试") }
                    }
                }
            }

            is AddTransactionUiState.Ready -> {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AddLayout.pagePaddingH),
                    verticalArrangement = Arrangement.spacedBy(spacing.mdDp),
                ) {
                    AmountCard(state = state)

                    if (state.hasNoCategories) {
                        NoCategoriesHint()
                    } else {
                        CategoryGrid(
                            categories = state.categories,
                            selectedId = state.selectedCategoryId,
                            onSelect = { option ->
                                if (option.isFallback) {
                                    showCustomCategory = true
                                } else {
                                    onSelectCategory(option)
                                }
                            },
                        )
                    }

                    AttributesCard(
                        state = state,
                        onAccountClick = { showAccountPicker = true },
                        onTimeClick = { showTimePicker = true },
                        onNoteClick = { showNoteEditor = true },
                    )

                    state.error?.let { error -> FormErrorText(error) }

                    Spacer(modifier = Modifier.height(spacing.smDp))
                }

                NumberPad(
                    onDigit = onDigit,
                    onDot = onDot,
                    onBackspace = onBackspace,
                    onClear = onClear,
                    onSave = onSave,
                    saving = state.saving,
                    modifier = Modifier.padding(
                        start = AddLayout.pagePaddingH,
                        end = AddLayout.pagePaddingH,
                        bottom = spacing.mdDp,
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 对话框

    if (showCustomCategory) {
        CustomCategoryDialog(
            onDismiss = { showCustomCategory = false },
            onConfirm = { name ->
                showCustomCategory = false
                onConfirmCustomCategory(name)
            },
        )
    }

    if (showAccountPicker && state is AddTransactionUiState.Ready) {
        AccountPickerDialog(
            accounts = state.accounts,
            selectedId = state.selectedAccountId,
            onDismiss = { showAccountPicker = false },
            onSelect = { id ->
                showAccountPicker = false
                onSelectAccount(id)
            },
        )
    }

    if (showNoteEditor && state is AddTransactionUiState.Ready) {
        NoteEditorDialog(
            initial = state.note,
            onDismiss = { showNoteEditor = false },
            onConfirm = { note ->
                showNoteEditor = false
                onSetNote(note)
            },
        )
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialMillis = (state as? AddTransactionUiState.Ready)?.transactionTime,
            onDismiss = { showTimePicker = false },
            onSelectNow = {
                showTimePicker = false
                onSetTime(null)
            },
            onSelectDate = { millis ->
                showTimePicker = false
                onSetTime(millis)
            },
        )
    }
}

// ---------------------------------------------------------------- 顶栏

@Composable
private fun AddTopBar(
    state: AddTransactionUiState,
    onToggleType: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val spacing = ZhangRanTheme.spacing

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = AddLayout.pagePaddingH,
                end = AddLayout.pagePaddingH,
                bottom = AddLayout.pagePaddingBottom,
            ),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column {
            Text(
                text = "记一笔",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.textPrimaryColor,
            )
            Text(
                text = "MANUAL ENTRY",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textDisabledColor,
            )
        }
        if (state is AddTransactionUiState.Ready) {
            Box(
                modifier = Modifier
                    .clip(shapes.chip)
                    .background(color = colors.surfaceColor)
                    .clickable(onClick = onToggleType)
                    .padding(horizontal = spacing.smDp, vertical = spacing.xsDp),
            ) {
                Text(
                    text = if (state.type == TransactionType.EXPENSE) "支出 ⇄" else "收入 ⇄",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primaryColor,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 金额卡

@Composable
private fun AmountCard(state: AddTransactionUiState.Ready) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val spacing = ZhangRanTheme.spacing

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zhangRanGlass(colors = colors, shape = shapes.card)
            .padding(horizontal = AddLayout.cardPadding, vertical = spacing.lgDp),
    ) {
        AmountDisplay(amount = state.amount)
    }
}

// ---------------------------------------------------------------- 属性卡

@Composable
private fun AttributesCard(
    state: AddTransactionUiState.Ready,
    onAccountClick: () -> Unit,
    onTimeClick: () -> Unit,
    onNoteClick: () -> Unit,
) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val spacing = ZhangRanTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .zhangRanGlass(colors = colors, shape = shapes.card)
            .padding(horizontal = AddLayout.cardPadding, vertical = spacing.mdDp),
    ) {
        EditableRow(
            icon = "💳",
            title = "账户",
            value = state.selectedAccountName ?: "选择",
            onClick = onAccountClick,
        )
        EditableRow(
            icon = "🕐",
            title = "时间",
            value = if (state.transactionTime == null) "现在" else "已选择",
            onClick = onTimeClick,
        )
        EditableRow(
            icon = "📝",
            title = "备注",
            value = state.note.ifBlank { "添加" },
            onClick = onNoteClick,
        )
    }
}

// ---------------------------------------------------------------- 其它

@Composable
private fun NoCategoriesHint() {
    val colors = ZhangRanTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZhangRanTheme.spacing.lgDp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "暂无可用分类",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondaryColor,
        )
    }
}

@Composable
private fun FormErrorText(error: FormError) {
    val colors = ZhangRanTheme.colors
    val message = when (error) {
        FormError.AMOUNT_INVALID -> "请输入大于 0 的金额"
        FormError.CATEGORY_REQUIRED -> "请选择分类"
        FormError.ACCOUNT_REQUIRED -> "请选择账户"
        FormError.SAVE_FAILED -> "保存失败，请检查后重试"
    }
    Text(
        text = message,
        style = MaterialTheme.typography.labelMedium,
        color = colors.errorColor,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ---------------------------------------------------------------- 对话框

/** 自定义分类弹窗（`02` §9：输入名称 → 当前 type 下创建或复用）。 */
@Composable
private fun CustomCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义分类") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("分类名称") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 账户选择。 */
@Composable
private fun AccountPickerDialog(
    accounts: List<AccountOption>,
    selectedId: String?,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择账户") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp)) {
                if (accounts.isEmpty()) {
                    Text("暂无账户")
                } else {
                    accounts.forEach { account ->
                        Text(
                            text = if (account.id == selectedId) "${account.name} ✓" else account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(account.id) }
                                .padding(vertical = ZhangRanTheme.spacing.smDp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 备注编辑（≤500 字符，`03` §5）。 */
@Composable
private fun NoteEditorDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("备注") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(NOTE_LIMIT) },
                label = { Text("最多 $NOTE_LIMIT 字") },
                minLines = 3,
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/** 时间选择：现在 / 选择日期。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialMillis: Long?,
    onDismiss: () -> Unit,
    onSelectNow: () -> Unit,
    onSelectDate: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择时间") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp)) {
                Text(
                    text = "现在",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectNow)
                        .padding(vertical = ZhangRanTheme.spacing.smDp),
                )
                Text(
                    text = "选择日期…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = ZhangRanTheme.spacing.smDp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let(onSelectDate)
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** 备注长度上限（`03` §5）。 */
private const val NOTE_LIMIT: Int = 500

/**
 * Hilt 入口（本任务不引入 `hilt-navigation-compose`，故用 EntryPoint 手工构造 ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface AddEntryPoint {
    fun addManualTransactionUseCase(): AddManualTransactionUseCase
    fun categoryRepository(): CategoryRepository
    fun accountRepository(): AccountRepository
}
