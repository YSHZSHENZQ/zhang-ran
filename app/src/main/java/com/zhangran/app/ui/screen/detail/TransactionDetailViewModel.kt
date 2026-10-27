/**
 * 交易详情 ViewModel（`M2b` §4.5）。
 *
 * 职责：
 * - 按 `transactionId` **读取一次**详情（交易 + 分类/账户候选 + 证据摘要）；
 * - 提交单字段编辑（唯一入口 `UpdateTransactionFieldUseCase`）与删除/撤销（`DeleteTransactionUseCase`）；
 * - 编辑成功 → 发 [TransactionDetailEvent.Saved]（页面返回首页，首页响应式自动更新，A8）；
 * - 删除成功 → 发 [TransactionDetailEvent.Deleted]（页面返回首页，由首页弹「可撤销」SnackBar，A10）。
 *
 * 守卫：`Submitting` 期间拒绝重复提交（A14）；校验失败只发消息、不落库（A17）。
 */
package com.zhangran.app.ui.screen.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CaptureEvidenceRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.DeleteTransactionResult
import com.zhangran.app.domain.usecase.DeleteTransactionUseCase
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.domain.usecase.FieldEditValue
import com.zhangran.app.domain.usecase.UpdateTransactionFieldResult
import com.zhangran.app.domain.usecase.UpdateTransactionFieldUseCase
import com.zhangran.app.ui.screen.add.AccountOption
import com.zhangran.app.ui.screen.add.CategoryOption
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * @param transactionId 要展示的交易 id（来自导航参数；缺失时进入 Error）
 * @param transactions 交易读取（`getById`）
 * @param categories 分类候选（编辑分类用）
 * @param accounts 账户候选（编辑支付方式用）
 * @param evidence 证据读取（置信度/证据摘要）
 * @param updateField 单字段编辑用例（唯一写入口）
 * @param deleteTransaction 软删除/撤销用例
 */
class TransactionDetailViewModel(
    private val transactionId: String,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val evidence: CaptureEvidenceRepository,
    private val updateField: UpdateTransactionFieldUseCase,
    private val deleteTransaction: DeleteTransactionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow<TransactionDetailUiState>(TransactionDetailUiState.Loading)
    val uiState: StateFlow<TransactionDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TransactionDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<TransactionDetailEvent> = _events.asSharedFlow()

    init {
        load()
    }

    /** 重新加载详情（错误态重试、编辑成功后刷新）。 */
    fun load() {
        viewModelScope.launch {
            _uiState.value = TransactionDetailUiState.Loading
            val loaded = runCatching { loadDetail() }
            _uiState.value = loaded.fold(
                onSuccess = { detail ->
                    if (detail == null) {
                        TransactionDetailUiState.Error("交易不存在或已删除")
                    } else {
                        TransactionDetailUiState.Content(detail)
                    }
                },
                onFailure = { throwable ->
                    TransactionDetailUiState.Error(throwable.message ?: "加载失败")
                },
            )
        }
    }

    /**
     * 提交一个字段的编辑。
     *
     * 成功 → [TransactionDetailEvent.Saved]；被拒 → [TransactionDetailEvent.Message]（不落库、不写 FieldEdit）。
     */
    fun submit(field: EditableField, value: FieldEditValue) {
        val current = _uiState.value
        if (current !is TransactionDetailUiState.Content) return
        if (!current.detail.fieldsEditable) {
            emitMessage("暂不支持该类型的字段编辑（转账/退款属 M2c）")
            return
        }
        _uiState.value = TransactionDetailUiState.Submitting(current.detail)
        viewModelScope.launch {
            val result = runCatching { updateField.invoke(transactionId, field, value) }
            val failure = result.exceptionOrNull()
            val outcome = result.getOrNull()
            when {
                failure != null -> {
                    _uiState.value = TransactionDetailUiState.Content(current.detail)
                    emitMessage(failure.message ?: "编辑失败")
                }

                outcome is UpdateTransactionFieldResult.Rejected -> {
                    _uiState.value = TransactionDetailUiState.Content(current.detail)
                    emitMessage(rejectionTextOf(outcome.reason))
                }

                outcome is UpdateTransactionFieldResult.Success && !outcome.changed -> {
                    // 值与原来相同：不落库、不写 FieldEdit，也**不返回首页**（避免"没改也退出"）
                    _uiState.value = TransactionDetailUiState.Content(current.detail)
                }

                else -> {
                    // 成功：刷新详情后返回首页（M2b §4.5）
                    refreshOrError(current.detail)
                    _events.tryEmit(TransactionDetailEvent.Saved(transactionId))
                }
            }
        }
    }

    /** 删除（软删除）；成功后发 [TransactionDetailEvent.Deleted]，由首页负责撤销入口。 */
    fun delete() {
        val current = _uiState.value
        if (current !is TransactionDetailUiState.Content) return
        _uiState.value = TransactionDetailUiState.Submitting(current.detail)
        viewModelScope.launch {
            when (val result = runCatching { deleteTransaction.delete(transactionId) }.getOrNull()) {
                DeleteTransactionResult.Success ->
                    _events.tryEmit(TransactionDetailEvent.Deleted(transactionId))

                DeleteTransactionResult.NotFound -> {
                    _uiState.value = TransactionDetailUiState.Content(current.detail)
                    emitMessage("交易不存在或已删除")
                }

                null -> {
                    _uiState.value = TransactionDetailUiState.Content(current.detail)
                    emitMessage("删除失败")
                }
            }
        }
    }

    private suspend fun refreshOrError(fallback: TransactionDetailUi) {
        val reloaded = runCatching { loadDetail() }
        _uiState.value = reloaded.fold(
            onSuccess = { detail ->
                if (detail == null) {
                    TransactionDetailUiState.Error("交易不存在或已删除")
                } else {
                    TransactionDetailUiState.Content(detail)
                }
            },
            onFailure = { TransactionDetailUiState.Content(fallback) },
        )
    }

    private fun emitMessage(text: String) {
        _events.tryEmit(TransactionDetailEvent.Message(text))
    }

    /** @return 详情；交易不存在时返回 `null` */
    private suspend fun loadDetail(): TransactionDetailUi? {
        val entity = transactions.getById(transactionId) ?: return null
        val categoryById = categories.all().associateBy { it.id }
        val accountById = accounts.all().associateBy { it.id }
        val evidenceEntity = entity.evidenceId?.let { evidence.findById(it) }
        val categoryType = entity.type.toCategoryTypeOrNull()

        return TransactionDetailUi(
            id = entity.id,
            type = entity.type,
            amountMinor = entity.amountMinor,
            merchant = entity.merchant,
            categoryId = entity.categoryId,
            categoryName = entity.categoryId?.let { categoryById[it]?.name },
            categoryIcon = entity.categoryId?.let { categoryById[it]?.icon },
            accountId = entity.accountId,
            accountName = entity.accountId?.let { accountById[it]?.name },
            transactionTime = entity.transactionTime,
            transactionTimeZone = entity.transactionTimeZone,
            note = entity.note,
            source = entity.source,
            state = entity.state,
            confidence = evidenceEntity?.confidence,
            engineVersion = entity.engineVersion,
            matchedRuleId = entity.matchedRuleId,
            externalTransactionId = entity.externalTransactionId,
            evidenceId = entity.evidenceId,
            evidenceSummary = evidenceEntity?.rawCaptureRef,
            categories = if (categoryType == null) {
                emptyList()
            } else {
                categories.enabledByType(categoryType).map { it.toOption() }
            },
            accounts = accounts.all().filter { it.enabled }.map { AccountOption(id = it.id, name = it.name) },
        )
    }

    private fun com.zhangran.app.data.local.entity.CategoryEntity.toOption() = CategoryOption(
        id = id,
        name = name,
        icon = icon,
        // 「其他」是兜底分类：点击后走自定义分类流程（`docs/02` §9）
        isFallback = name == FALLBACK_CATEGORY_NAME,
    )

    private fun TransactionType.toCategoryTypeOrNull(): CategoryType? = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
        TransactionType.TRANSFER, TransactionType.REFUND -> null
    }

    private companion object {
        /** 兜底分类名（与 `DefaultData` 的预置名一致）。 */
        const val FALLBACK_CATEGORY_NAME: String = "其他"
    }
}
