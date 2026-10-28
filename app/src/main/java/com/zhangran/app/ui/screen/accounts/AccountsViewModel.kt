/**
 * 账户管理 ViewModel（`M2d-2` §4.1–§4.6）。
 *
 * - 订阅 `AccountRepository.observeAll()` → 按类型分组（现金 / 银行卡 / 第三方支付），写后自动刷新；
 * - 编辑弹层草稿、就地错误、保存中状态都在 [AccountsUiState.Content.editor]；
 * - 写操作只经 [SaveAccountUseCase]；切换默认只经 [SetDefaultAccountUseCase]（事务先清后置）；
 * - 排序用上移/下移（不做拖拽）：交换相邻两项 `sortOrder`（两次写；第二步失败会尝试回滚第一步）。
 */
package com.zhangran.app.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.usecase.AccountInput
import com.zhangran.app.domain.usecase.MAX_ACCOUNT_NAME_LENGTH
import com.zhangran.app.domain.usecase.SaveAccountResult
import com.zhangran.app.domain.usecase.SaveAccountUseCase
import com.zhangran.app.domain.usecase.SetDefaultAccountResult
import com.zhangran.app.domain.usecase.SetDefaultAccountUseCase
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param accounts 账户读取（响应式）
 * @param saveAccount 账户写入口
 * @param setDefaultAccount 默认账户切换（事务）
 */
class AccountsViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val saveAccount: SaveAccountUseCase,
    private val setDefaultAccount: SetDefaultAccountUseCase,
) : ViewModel() {

    private sealed interface SourceState {
        data object Loading : SourceState
        data class Ready(val rows: List<AccountEntity>) : SourceState
        data class Failure(val message: String) : SourceState
    }

    private val refreshTrigger = MutableStateFlow(0)
    private val editor = MutableStateFlow<AccountEditorUi?>(null)

    private val _events = MutableSharedFlow<AccountsEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AccountsEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source: Flow<SourceState> = refreshTrigger.flatMapLatest {
        accounts.observeAll()
            .map<List<AccountEntity>, SourceState> { SourceState.Ready(it) }
            .catch { throwable -> emit(SourceState.Failure(throwable.message ?: "读取账户失败")) }
            .onStart { emit(SourceState.Loading) }
    }

    val uiState: StateFlow<AccountsUiState> = combine(source, editor) { data, currentEditor ->
        when (data) {
            SourceState.Loading -> AccountsUiState.Loading
            is SourceState.Failure -> AccountsUiState.Error(data.message)
            is SourceState.Ready -> if (data.rows.isEmpty()) {
                AccountsUiState.Empty
            } else {
                AccountsUiState.Content(
                    groups = groupsOf(data.rows),
                    editor = currentEditor,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = AccountsUiState.Loading,
    )

    fun retry() {
        refreshTrigger.value += 1
    }

    // ---------------------------------------------------------------- 编辑弹层

    /** 打开"添加"：类型可选，默认现金。 */
    fun openAdd() {
        editor.value = AccountEditorUi(
            editingId = null,
            type = AccountType.CASH,
            name = "",
            channel = null,
            last4 = "",
            enabled = true,
            isDefault = false,
        )
    }

    /** 打开编辑（类型只读，V7）。 */
    fun openEdit(item: AccountUiItem) {
        editor.value = AccountEditorUi(
            editingId = item.id,
            type = item.type,
            name = item.name,
            channel = item.channel,
            last4 = item.last4.orEmpty(),
            enabled = item.enabled,
            isDefault = item.isDefault,
        )
    }

    fun closeEditor() {
        editor.value = null
    }

    fun onNameChange(value: String) {
        editor.value = editor.value?.copy(
            name = value.take(MAX_ACCOUNT_NAME_LENGTH),
            error = null,
        )
    }

    /** 类型只在"添加"时可改；切到现金/第三方支付时清空尾号（V2）。 */
    fun onTypeChange(type: AccountType) {
        val current = editor.value ?: return
        if (!current.typeEditable) return
        val keepLast4 = type == AccountType.DEBIT_CARD || type == AccountType.CREDIT_CARD
        editor.value = current.copy(
            type = type,
            last4 = if (keepLast4) current.last4 else "",
            error = null,
        )
    }

    fun onChannelChange(channel: PaymentChannel?) {
        editor.value = editor.value?.copy(channel = channel, error = null)
    }

    /** 尾号：只允许数字、最多 4 位（V2；是否必填由保存时校验）。 */
    fun onLast4Change(value: String) {
        editor.value = editor.value?.copy(
            last4 = value.filter { it.isDigit() }.take(4),
            error = null,
        )
    }

    fun onEnabledChange(enabled: Boolean) {
        editor.value = editor.value?.copy(enabled = enabled, error = null)
    }

    /** 新增时的"设为默认"草稿（编辑既有账户用 [setDefault]）。 */
    fun onDefaultChange(isDefault: Boolean) {
        val current = editor.value ?: return
        if (current.editingId != null) return
        editor.value = current.copy(isDefault = isDefault, error = null)
    }

    /** 编辑既有账户时「设为默认」：立即走事务用例（不关闭弹层）。 */
    fun setDefault() {
        val current = editor.value ?: return
        val id = current.editingId ?: return
        if (current.saving) return
        editor.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            when (val result = setDefaultAccount(id)) {
                is SetDefaultAccountResult.Success -> {
                    editor.value = current.copy(saving = false, isDefault = true)
                    _events.emit(AccountsEvent.Message("已设为默认账户"))
                }

                SetDefaultAccountResult.AccountDisabled -> editor.value = current.copy(
                    saving = false,
                    error = "禁用账户不能设为默认账户",
                )

                SetDefaultAccountResult.NotFound -> editor.value = current.copy(
                    saving = false,
                    error = "账户不存在，请刷新后重试",
                )
            }
        }
    }

    /** 保存（成功关闭弹层；失败留在弹层里就地提示）。 */
    fun save() {
        val current = editor.value ?: return
        if (current.saving) return
        editor.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val outcome = runCatching {
                saveAccount(
                    AccountInput(
                        id = current.editingId,
                        name = current.name,
                        type = current.type,
                        channel = current.channel,
                        last4 = if (current.last4Visible) current.last4 else null,
                        enabled = current.enabled,
                        // 编辑时 isDefault 由「设为默认」按钮处理，避免保存时误改默认
                        isDefault = current.editingId == null && current.isDefault,
                    ),
                )
            }.getOrNull()
            when (outcome) {
                is SaveAccountResult.Success -> {
                    editor.value = null
                    _events.emit(AccountsEvent.Saved)
                }

                is SaveAccountResult.Rejected -> editor.value = current.copy(
                    saving = false,
                    error = accountErrorTextOf(outcome.error),
                )

                null -> editor.value = current.copy(saving = false, error = "保存失败，请重试")
            }
        }
    }

    /** 上移/下移（交换相邻两项 `sortOrder`）。 */
    fun move(up: Boolean) {
        val current = editor.value ?: return
        val editingId = current.editingId ?: return
        val content = uiState.value as? AccountsUiState.Content ?: return
        // 只允许在同一分组内移动（跨组移动会造成"分组跳变"）
        val group = content.groups.firstOrNull { group -> group.items.any { it.id == editingId } } ?: return
        val groupIndex = group.items.indexOfFirst { it.id == editingId }
        val targetIndex = if (up) groupIndex - 1 else groupIndex + 1
        if (targetIndex !in group.items.indices) return
        val item = group.items[groupIndex]
        val neighbour = group.items[targetIndex]

        viewModelScope.launch {
            val moved = runCatching {
                saveAccount(inputOf(item, sortOrder = neighbour.sortOrder))
            }.getOrNull()
            if (moved !is SaveAccountResult.Success) {
                _events.emit(AccountsEvent.Message("调整顺序失败，请重试"))
                return@launch
            }
            val other = runCatching {
                saveAccount(inputOf(neighbour, sortOrder = item.sortOrder))
            }.getOrNull()
            if (other !is SaveAccountResult.Success) {
                runCatching { saveAccount(inputOf(item, sortOrder = item.sortOrder)) }
                _events.emit(AccountsEvent.Message("调整顺序失败，请重试"))
            }
        }
    }

    // ---------------------------------------------------------------- 内部映射

    private fun inputOf(item: AccountUiItem, sortOrder: Int): AccountInput = AccountInput(
        id = item.id,
        name = item.name,
        type = item.type,
        channel = item.channel,
        last4 = item.last4,
        enabled = item.enabled,
        // 排序不改变默认标记（SaveAccountUseCase 在 isDefault=false 时保留原值）
        isDefault = false,
        sortOrder = sortOrder,
    )

    /** 固定分组顺序；空组不显示（A1）。 */
    private fun groupsOf(rows: List<AccountEntity>): List<AccountGroupUi> {
        val ordered = rows.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
        return listOf(
            AccountType.CASH to listOf(AccountType.CASH),
            AccountType.DEBIT_CARD to listOf(AccountType.DEBIT_CARD, AccountType.CREDIT_CARD),
            AccountType.THIRD_PARTY to listOf(AccountType.THIRD_PARTY),
        ).mapNotNull { (anchor, types) ->
            val items = ordered.filter { it.type in types }
            if (items.isEmpty()) {
                null
            } else {
                AccountGroupUi(
                    title = accountGroupTitleOf(anchor),
                    items = items.map { it.toUiItem() },
                )
            }
        }
    }

    private fun AccountEntity.toUiItem(): AccountUiItem = AccountUiItem(
        id = id,
        name = name,
        type = type,
        channel = channel,
        last4 = last4,
        enabled = enabled,
        isDefault = isDefault,
        sortOrder = sortOrder,
    )

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L
    }
}
