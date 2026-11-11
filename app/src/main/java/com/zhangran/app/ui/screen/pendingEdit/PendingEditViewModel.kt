/**
 * 待确认编辑页（M3a）。只编辑支出/收入字段，保存并入账。
 */
package com.zhangran.app.ui.screen.pendingEdit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.PendingBookDraft
import com.zhangran.app.domain.usecase.PendingFieldErrors
import com.zhangran.app.domain.usecase.SavePendingAndBookUseCase
import com.zhangran.app.domain.usecase.SavePendingResult
import com.zhangran.app.domain.usecase.fixtureIdOf
import com.zhangran.app.ui.common.DeviceZone
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PendingChoice(
    val id: String,
    val name: String,
)

data class PendingEditForm(
    val fixtureId: String?,
    val merchant: String,
    val amountText: String,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String?,
    val timeText: String,
    val note: String,
    val categories: List<PendingChoice>,
    val accounts: List<PendingChoice>,
    val amountError: String? = null,
    val timeError: String? = null,
    val categoryError: String? = null,
    val noteError: String? = null,
    val formError: String? = null,
    val saving: Boolean = false,
    val originalTimeEpochMillis: Long,
    val timeZoneId: String,
    val timeDirty: Boolean = false,
)

sealed interface PendingEditUiState {
    data object Loading : PendingEditUiState

    data class Ready(val form: PendingEditForm) : PendingEditUiState

    data class Missing(val message: String) : PendingEditUiState
}

class PendingEditViewModel(
    private val transactionId: String,
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val logs: CaptureLogRepository,
    private val savePending: SavePendingAndBookUseCase,
) : ViewModel() {

    private val mutableState = MutableStateFlow<PendingEditUiState>(PendingEditUiState.Loading)
    val uiState: StateFlow<PendingEditUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    fun onMerchant(value: String) = update { it.copy(merchant = value, formError = null) }

    fun onAmount(value: String) = update { it.copy(amountText = value, amountError = null, formError = null) }

    fun onNote(value: String) = update { it.copy(note = value, noteError = null, formError = null) }

    fun onTime(value: String) = update {
        it.copy(timeText = value, timeDirty = true, timeError = null, formError = null)
    }

    fun onType(type: TransactionType) = update { form ->
        if (type != TransactionType.EXPENSE && type != TransactionType.INCOME) return@update form
        val choices = form.categories
        val stillValid = choices.any { it.id == form.categoryId }
        form.copy(
            type = type,
            categoryId = if (stillValid && sameSide(form.type, type)) form.categoryId else null,
            formError = null,
        ).let { next ->
            // 类型切换后分类列表要换边，这里先清空，loadChoices 在协程里补。
            next
        }.also { next ->
            viewModelScope.launch {
                val refreshed = categoryChoices(next.type)
                update { current ->
                    if (current.type != next.type) current else current.copy(categories = refreshed)
                }
            }
        }
    }

    fun onCategory(categoryId: String?) = update { it.copy(categoryId = categoryId, categoryError = null) }

    fun onAccount(accountId: String?) = update { it.copy(accountId = accountId) }

    fun save(onBooked: () -> Unit) {
        val ready = mutableState.value as? PendingEditUiState.Ready ?: return
        if (ready.form.saving) return
        val epoch = resolveTime(ready.form)
        if (epoch is TimeResolution.Invalid) {
            update { it.copy(timeError = epoch.message) }
            return
        }
        val timeEpoch = (epoch as TimeResolution.Ok).epochMillis
        update { it.copy(saving = true, formError = null) }
        viewModelScope.launch {
            val form = (mutableState.value as? PendingEditUiState.Ready)?.form ?: return@launch
            when (
                val result = savePending.invoke(
                    transactionId,
                    PendingBookDraft(
                        merchant = form.merchant,
                        amountText = form.amountText,
                        type = form.type,
                        categoryId = form.categoryId,
                        accountId = form.accountId,
                        transactionTimeEpochMillis = timeEpoch,
                        timeZoneId = form.timeZoneId,
                        note = form.note,
                    ),
                )
            ) {
                is SavePendingResult.Saved -> onBooked()
                is SavePendingResult.Invalid -> update { it.copy(saving = false).withErrors(result.errors) }
                is SavePendingResult.Rejected -> update { it.copy(saving = false, formError = result.reason) }
            }
        }
    }

    private suspend fun load() {
        mutableState.value = try {
            val entity = transactions.getById(transactionId)
            if (entity == null || entity.state != TransactionState.PENDING) {
                PendingEditUiState.Missing("未找到待确认记录")
            } else if (entity.type != TransactionType.EXPENSE && entity.type != TransactionType.INCOME) {
                PendingEditUiState.Missing("这条记录不是支出或收入")
            } else {
                val zone = DeviceZone.current().id
                PendingEditUiState.Ready(
                    PendingEditForm(
                        fixtureId = fixtureIdOf(logs.findByTransactionId(entity.id)?.rawCaptureRef),
                        merchant = entity.merchant.orEmpty(),
                        amountText = amountTextOf(entity.amountMinor),
                        type = entity.type,
                        categoryId = entity.categoryId,
                        accountId = entity.accountId,
                        timeText = formatTime(entity.transactionTime, zone),
                        note = entity.note.orEmpty(),
                        categories = categoryChoices(entity.type),
                        accounts = accounts.all().filter { it.enabled }.map { PendingChoice(it.id, it.name) },
                        originalTimeEpochMillis = entity.transactionTime,
                        timeZoneId = zone,
                    ),
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (thrown: Throwable) {
            PendingEditUiState.Missing(thrown.message ?: "加载失败")
        }
    }

    private suspend fun categoryChoices(type: TransactionType): List<PendingChoice> {
        val categoryType = if (type == TransactionType.INCOME) CategoryType.INCOME else CategoryType.EXPENSE
        return categories.enabledByType(categoryType).map { PendingChoice(it.id, it.name) }
    }

    private fun update(transform: (PendingEditForm) -> PendingEditForm) {
        val ready = mutableState.value as? PendingEditUiState.Ready ?: return
        mutableState.value = PendingEditUiState.Ready(transform(ready.form))
    }

    private fun resolveTime(form: PendingEditForm): TimeResolution {
        if (!form.timeDirty) return TimeResolution.Ok(form.originalTimeEpochMillis)
        if (form.timeText.isBlank()) return TimeResolution.Ok(null)
        val zone = runCatching { ZoneId.of(form.timeZoneId) }.getOrElse { DeviceZone.current() }
        return try {
            val local = java.time.LocalDateTime.parse(form.timeText.trim(), TIME_FORMAT)
            TimeResolution.Ok(local.atZone(zone).toInstant().toEpochMilli())
        } catch (_: DateTimeParseException) {
            TimeResolution.Invalid("时间不合法")
        }
    }

    private fun formatTime(epochMillis: Long, zoneId: String): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrElse { DeviceZone.current() }
        return Instant.ofEpochMilli(epochMillis).atZone(zone).format(TIME_FORMAT)
    }

    private fun sameSide(current: TransactionType, next: TransactionType): Boolean = current == next

    private fun PendingEditForm.withErrors(errors: PendingFieldErrors): PendingEditForm = copy(
        amountError = errors.amount,
        timeError = errors.time,
        categoryError = errors.category,
        noteError = errors.note,
        formError = errors.type ?: errors.account,
    )

    private sealed interface TimeResolution {
        data class Ok(val epochMillis: Long?) : TimeResolution

        data class Invalid(val message: String) : TimeResolution
    }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        fun amountTextOf(amountMinor: Long): String =
            BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString()
    }
}
