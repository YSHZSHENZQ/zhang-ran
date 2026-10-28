/**
 * 预算页 ViewModel（`M4a` §4.3–§4.6）。
 *
 * 刷新方式：**一次性 `suspend` + `refresh()`**（见 `ObserveBudgetOverviewUseCase` 文件头）。
 * 写入（保存/删除/示例预算）成功后立即 `load()`，因此页面数据始终与库一致。
 *
 * 金额输入复用 M2a 的 [AmountInput] 状态机（`Money` 解析，禁止 `Double`）。
 */
package com.zhangran.app.ui.screen.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.usecase.BudgetInput
import com.zhangran.app.domain.usecase.BudgetSaveError
import com.zhangran.app.domain.usecase.DeleteBudgetResult
import com.zhangran.app.domain.usecase.ObserveBudgetOverviewUseCase
import com.zhangran.app.domain.usecase.SaveBudgetResult
import com.zhangran.app.domain.usecase.SaveBudgetUseCase
import com.zhangran.app.ui.screen.add.AmountInput
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @param observeOverview 当前月预算概览（一次性）
 * @param saveBudget 预算唯一写入口
 * @param categories 分类读取（编辑弹层的可选分类）
 */
class BudgetViewModel @Inject constructor(
    private val observeOverview: ObserveBudgetOverviewUseCase,
    private val saveBudget: SaveBudgetUseCase,
    private val categories: CategoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<BudgetUiState>(BudgetUiState.Loading)
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<BudgetEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<BudgetEvent> = _events.asSharedFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            runCatching {
                val overview = observeOverview()
                val options = categoryOptions(overview)
                if (overview.isEmpty) {
                    BudgetUiState.Empty(
                        periodKey = overview.periodKey,
                        categoryOptions = options,
                    )
                } else {
                    BudgetUiState.Content(overview = overview, categoryOptions = options)
                }
            }.onSuccess { _uiState.value = it }
                .onFailure { _uiState.value = BudgetUiState.Error(it.message ?: "读取预算失败") }
        }
    }

    fun refresh() = load()

    // ---------------------------------------------------------------- 编辑弹层

    /** 点击总额卡片：编辑（或首次设置）月度总预算。 */
    fun openTotalEditor() {
        val current = _uiState.value
        val existingId = (current as? BudgetUiState.Content)?.overview?.totalBudgetId
        val existingLimit = (current as? BudgetUiState.Content)?.overview?.totalLimitMinor
        updateEditor(
            BudgetEditorUi(
                kind = BudgetEditorKind.TOTAL,
                budgetId = existingId,
                amount = existingLimit?.let { AmountInput(minorToAmountText(it)) } ?: AmountInput(),
            ),
        )
    }

    /** 点击分类预算行：编辑该分类预算。 */
    fun openCategoryEditor(categoryId: String) {
        val content = _uiState.value as? BudgetUiState.Content ?: return
        val category = content.overview.categories.firstOrNull { it.categoryId == categoryId } ?: return
        updateEditor(
            BudgetEditorUi(
                kind = BudgetEditorKind.CATEGORY_EDIT,
                budgetId = category.budgetId,
                categoryId = category.categoryId,
                categoryName = category.categoryName,
                amount = AmountInput(minorToAmountText(category.limitMinor)),
            ),
        )
    }

    /** 「添加分类预算」：先选分类，再输金额。 */
    fun openAddCategory() {
        updateEditor(BudgetEditorUi(kind = BudgetEditorKind.CATEGORY_ADD))
    }

    /** 新建分类预算时选择分类。 */
    fun selectCategory(option: BudgetCategoryOption) {
        val editor = currentEditor() ?: return
        if (editor.kind != BudgetEditorKind.CATEGORY_ADD) return
        updateEditor(editor.copy(categoryId = option.id, categoryName = option.name, error = null))
    }

    fun closeEditor() = updateEditor(null)

    fun pressDigit(digit: Char) = updateAmount { it.digit(digit) }

    fun pressDot() = updateAmount { it.dot() }

    fun backspace() = updateAmount { it.backspace() }

    fun clearAmount() = updateAmount { it.clear() }

    /** 保存（成功关闭弹层并刷新；失败留在弹层里就地提示）。 */
    fun saveEditor() {
        val editor = currentEditor() ?: return
        if (editor.saving) return
        val periodKey = currentPeriodKey() ?: return
        if (editor.needsCategory) {
            updateEditor(editor.copy(error = "请先选择分类"))
            return
        }
        val limitMinor = editor.amount.toMinorOrNull()
        if (limitMinor == null || limitMinor <= 0L) {
            updateEditor(editor.copy(error = "请输入有效金额"))
            return
        }
        updateEditor(editor.copy(saving = true, error = null))
        viewModelScope.launch {
            val result = runCatching {
                saveBudget(
                    BudgetInput(
                        id = editor.budgetId,
                        scope = if (editor.kind == BudgetEditorKind.TOTAL) {
                            BudgetScope.TOTAL
                        } else {
                            BudgetScope.CATEGORY
                        },
                        categoryId = editor.categoryId,
                        periodKey = periodKey,
                        limitMinor = limitMinor,
                    ),
                )
            }.getOrNull()
            when (result) {
                is SaveBudgetResult.Success -> {
                    updateEditor(null)
                    load()
                    _events.emit(BudgetEvent.Message("已保存"))
                }

                is SaveBudgetResult.Rejected -> updateEditor(
                    editor.copy(saving = false, error = budgetErrorTextOf(result.error)),
                )

                null -> updateEditor(editor.copy(saving = false, error = "保存失败，请重试"))
            }
        }
    }

    // ---------------------------------------------------------------- 删除

    fun requestDelete() {
        val editor = currentEditor() ?: return
        if (!editor.canDelete) return
        updateEditor(editor.copy(confirmingDelete = true))
    }

    fun cancelDelete() {
        val editor = currentEditor() ?: return
        updateEditor(editor.copy(confirmingDelete = false))
    }

    /** 二次确认后物理删除（§4.4.1；不影响交易与统计）。 */
    fun confirmDelete() {
        val editor = currentEditor() ?: return
        val id = editor.budgetId ?: return
        if (editor.saving) return
        updateEditor(editor.copy(saving = true, confirmingDelete = false))
        viewModelScope.launch {
            when (saveBudget.delete(id)) {
                DeleteBudgetResult.DELETED -> {
                    updateEditor(null)
                    load()
                    _events.emit(BudgetEvent.Message("已删除"))
                }

                DeleteBudgetResult.NOT_FOUND -> {
                    updateEditor(null)
                    load()
                    _events.emit(BudgetEvent.Message("预算已不存在"))
                }
            }
        }
    }

    // ---------------------------------------------------------------- 示例预算

    /** 用户点选「使用示例预算」后写入当前月（幂等，不覆盖已设项）。 */
    fun applyExampleBudgets() {
        val periodKey = currentPeriodKey() ?: return
        viewModelScope.launch {
            val created = runCatching { saveBudget.applyExampleBudgets(periodKey) }.getOrNull()
            load()
            _events.emit(
                BudgetEvent.Message(
                    when {
                        created == null -> "写入示例预算失败，请重试"
                        created == 0 -> "示例预算已存在，未重复创建"
                        else -> "已写入 $created 项示例预算"
                    },
                ),
            )
        }
    }

    // ---------------------------------------------------------------- 内部

    private suspend fun categoryOptions(
        overview: com.zhangran.app.domain.usecase.BudgetOverview,
    ): List<BudgetCategoryOption> {
        val budgeted = overview.categories.map { it.categoryId }.toSet()
        return runCatching {
            categories.enabledByType(CategoryType.EXPENSE)
                .filterNot { it.id in budgeted }
                .map { BudgetCategoryOption(id = it.id, name = it.name, icon = it.icon) }
        }.getOrDefault(emptyList())
    }

    private fun currentPeriodKey(): String? = when (val current = _uiState.value) {
        is BudgetUiState.Content -> current.overview.periodKey
        is BudgetUiState.Empty -> current.periodKey
        else -> null
    }

    private fun currentEditor(): BudgetEditorUi? = when (val current = _uiState.value) {
        is BudgetUiState.Content -> current.editor
        is BudgetUiState.Empty -> current.editor
        else -> null
    }

    private fun updateEditor(editor: BudgetEditorUi?) {
        _uiState.value = when (val current = _uiState.value) {
            is BudgetUiState.Content -> current.copy(editor = editor)
            is BudgetUiState.Empty -> current.copy(editor = editor)
            else -> current
        }
    }

    private fun updateAmount(transform: (AmountInput) -> AmountInput) {
        val editor = currentEditor() ?: return
        updateEditor(editor.copy(amount = transform(editor.amount), error = null))
    }

    /** 最小货币单位 → 金额输入框文本（不带货币符号与千分位，便于 `AmountInput` 继续编辑）。 */
    private fun minorToAmountText(amountMinor: Long): String {
        val sign = if (amountMinor < 0) "-" else ""
        val abs = if (amountMinor < 0) -amountMinor else amountMinor
        return "$sign${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
    }
}

/** 保存失败 → 中文提示。 */
internal fun budgetErrorTextOf(error: BudgetSaveError): String = when (error) {
    BudgetSaveError.LIMIT_INVALID -> "预算金额必须大于 0"
    BudgetSaveError.CATEGORY_REQUIRED -> "请选择分类"
    BudgetSaveError.CATEGORY_NOT_ALLOWED -> "总预算不能关联分类"
    BudgetSaveError.CATEGORY_MISSING -> "分类不存在，请刷新后重试"
    BudgetSaveError.PERIOD_KEY_INVALID -> "月份格式不正确"
    BudgetSaveError.NOT_FOUND -> "预算不存在，请刷新后重试"
    BudgetSaveError.SCOPE_CHANGE_NOT_ALLOWED -> "预算类型不可修改"
    BudgetSaveError.PERIOD_CHANGE_NOT_ALLOWED -> "预算月份不可修改"
}
