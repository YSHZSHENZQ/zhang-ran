/**
 * 记账表单的 ViewModel（任务书 M2a §4.2、§4.6）。
 *
 * 职责：
 * - 加载当前类型下的分类与账户（`CategoryRepository` / `AccountRepository`，M1c' 已提供）；
 * - 维护金额输入缓冲（[AmountInput] 纯状态机）与表单字段；
 * - 调用 [AddManualTransactionUseCase]（**不重写校验**），把结构化错误映射为就地提示；
 * - 保存成功后清空表单并发出 [AddTransactionEvent.Saved]。
 *
 * 不负责：布局、弹窗、导航（属 Composable / 调用方）。
 */
package com.zhangran.app.ui.screen.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.AddManualTransactionResult
import com.zhangran.app.domain.usecase.AddManualTransactionUseCase
import com.zhangran.app.domain.usecase.ManualTransactionInput
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @param addTransaction 手工记账用例（M1c'，唯一写入口）
 * @param categoryRepository 分类读取
 * @param accountRepository 账户读取
 * @param clock 时间源（测试可注入固定值）
 */
class AddTransactionViewModel(
    private val addTransaction: AddManualTransactionUseCase,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    externalScope: CoroutineScope? = null,
) : ViewModel() {

    /** 工作作用域（测试可注入 Unconfined，生产用 iewModelScope）。 */
    private val scope: CoroutineScope = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow<AddTransactionUiState>(AddTransactionUiState.Loading)

    /** 当前 UI 状态。 */
    val uiState: StateFlow<AddTransactionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AddTransactionEvent>(extraBufferCapacity = 8)

    /** 一次性事件（保存成功 / 提示）。 */
    val events: SharedFlow<AddTransactionEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /** 首次加载（或失败后重试）。 */
    fun load() {
        scope.launch {
            runCatching { loadReady(currentType()) }
                .onSuccess { _uiState.value = it }
                .onFailure {
                    _uiState.value = AddTransactionUiState.LoadFailed(
                        it.message ?: "读取分类与账户失败",
                    )
                }
        }
    }

    /** 切换类型（支出 ⇄ 收入），并按新类型重新加载分类（A4）。 */
    fun toggleType() {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        val next = if (current.type == TransactionType.EXPENSE) {
            TransactionType.INCOME
        } else {
            TransactionType.EXPENSE
        }
        scope.launch {
            val categories = runCatching { categoriesOf(next) }.getOrElse { emptyList() }
            val latest = _uiState.value as? AddTransactionUiState.Ready ?: return@launch
            _uiState.value = latest.copy(
                type = next,
                categories = categories,
                // 支出/收入分类互不通用（`03` §7），切换后必须清空选中项
                selectedCategoryId = null,
                error = null,
            )
        }
    }

    // ---------------------------------------------------------------- 金额键盘

    /** 数字键。 */
    fun pressDigit(digit: Char) = updateAmount { it.digit(digit) }

    /** 小数点。 */
    fun pressDot() = updateAmount { it.dot() }

    /** 退格。 */
    fun backspace() = updateAmount { it.backspace() }

    /** 清空金额。 */
    fun clearAmount() = updateAmount { it.clear() }

    // ---------------------------------------------------------------- 字段

    /** 选择分类（「其他」的自定义流程由页面另行处理）。 */
    fun selectCategory(categoryId: String) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        _uiState.value = current.copy(selectedCategoryId = categoryId, error = null)
    }

    /** 选择账户。 */
    fun selectAccount(accountId: String) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        _uiState.value = current.copy(selectedAccountId = accountId, error = null)
    }

    /** 设置交易时间（`null` = 现在）。 */
    fun setTransactionTime(millis: Long?) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        _uiState.value = current.copy(transactionTime = millis)
    }

    /** 设置备注（超过 500 字符截断，`03` §5）。 */
    fun setNote(note: String) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        _uiState.value = current.copy(note = note.take(MAX_NOTE_LENGTH))
    }

    /**
     * 处理"自定义分类"弹窗的确认。
     *
     * 当前实现：在**当前类型**下按名称复用已存在的分类；若不存在则给出提示。
     *
     * 注意：**创建新分类的写入路径尚未打通**——`CategoryRepository`（M1c'）只提供读取，
     * 而本任务禁止修改 `data/`。该缺口已在任务报告中说明（A8 部分实现）。
     */
    fun confirmCustomCategory(name: String) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val existing = runCatching {
                categoryRepository.findByName(current.type.toCategoryType(), trimmed)
            }.getOrNull()
            val latest = _uiState.value as? AddTransactionUiState.Ready ?: return@launch
            if (existing != null) {
                _uiState.value = latest.copy(selectedCategoryId = existing.id, error = null)
            } else {
                _events.emit(
                    AddTransactionEvent.Message(
                        "分类「$trimmed」尚不存在，请先在分类管理中创建",
                    ),
                )
            }
        }
    }

    // ---------------------------------------------------------------- 保存

    /** 保存（键盘上的「保存」键）。 */
    fun save() {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        if (current.saving) return

        val amountMinor = current.amount.toMinorOrNull()
        if (amountMinor == null || amountMinor <= 0L) {
            _uiState.value = current.copy(error = FormError.AMOUNT_INVALID)
            return
        }
        val categoryId = current.selectedCategoryId
        if (categoryId == null) {
            _uiState.value = current.copy(error = FormError.CATEGORY_REQUIRED)
            return
        }
        val accountId = current.selectedAccountId
        if (accountId == null) {
            _uiState.value = current.copy(error = FormError.ACCOUNT_REQUIRED)
            return
        }

        _uiState.value = current.copy(saving = true, error = null)
        scope.launch {
            val result = runCatching {
                addTransaction.invoke(
                    ManualTransactionInput(
                        type = current.type,
                        amountMinor = amountMinor,
                        categoryId = categoryId,
                        accountId = accountId,
                        transactionTime = current.transactionTime ?: clock(),
                        transactionTimeZone = TimeZone.getDefault().id,
                        note = current.note.ifBlank { null },
                    ),
                )
            }.getOrNull()

            when (result) {
                is AddManualTransactionResult.Success -> {
                    val latest = _uiState.value as? AddTransactionUiState.Ready ?: return@launch
                    // 保存成功：重置金额/备注/分类/时间，保留类型与账户（便于连续记账）
                    _uiState.value = latest.copy(
                        amount = AmountInput(),
                        note = "",
                        selectedCategoryId = null,
                        transactionTime = null,
                        saving = false,
                        error = null,
                    )
                    _events.emit(AddTransactionEvent.Saved(result.transactionId))
                }

                // 校验失败 / 用例异常：**不落库**，就地提示（任务书 A7）
                is AddManualTransactionResult.ValidationFailed, null -> {
                    val latest = _uiState.value as? AddTransactionUiState.Ready ?: return@launch
                    _uiState.value = latest.copy(saving = false, error = FormError.SAVE_FAILED)
                }
            }
        }
    }

    // ---------------------------------------------------------------- 内部

    private fun updateAmount(transform: (AmountInput) -> AmountInput) {
        val current = _uiState.value as? AddTransactionUiState.Ready ?: return
        _uiState.value = current.copy(amount = transform(current.amount), error = null)
    }

    /** 交易类型 → 分类类型（ 3 §7：支出/收入分类互不通用）。 */
    private fun TransactionType.toCategoryType(): CategoryType = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
        TransactionType.TRANSFER -> CategoryType.EXPENSE
        TransactionType.REFUND -> CategoryType.EXPENSE
    }

    private fun currentType(): TransactionType =
        (_uiState.value as? AddTransactionUiState.Ready)?.type ?: TransactionType.EXPENSE

    private suspend fun categoriesOf(type: TransactionType): List<CategoryOption> =
        categoryRepository.enabledByType(type.toCategoryType()).map { entity ->
            CategoryOption(
                id = entity.id,
                name = entity.name,
                icon = entity.icon,
                // 「其他」由 `02` §9 定义为兜底分类（点击后走自定义分类流程）
                isFallback = entity.name == FALLBACK_CATEGORY_NAME,
            )
        }

    private suspend fun loadReady(type: TransactionType): AddTransactionUiState.Ready {
        val categories = categoriesOf(type)
        val accounts = accountRepository.all().map { AccountOption(id = it.id, name = it.name) }
        val defaultAccountId = accountRepository.defaultAccount()?.id ?: accounts.firstOrNull()?.id
        return AddTransactionUiState.Ready(
            type = type,
            categories = categories,
            accounts = accounts,
            selectedAccountId = defaultAccountId,
            note = "",
            transactionTime = null,
        )
    }

    private companion object {
        /** 备注长度上限（`03` §5）。 */
        const val MAX_NOTE_LENGTH: Int = 500

        /** 兜底分类名（`02` §9）。 */
        const val FALLBACK_CATEGORY_NAME: String = "其他"
    }
}
