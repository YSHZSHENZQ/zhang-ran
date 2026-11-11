/**
 * 待确认编辑页「保存并入账」。
 *
 * 只为用户实际改过的字段写 FieldEdit。校验失败时保持 PENDING，不写 FieldEdit。
 * 成功时 PENDING → CONFIRMED，且 confirmedByUser = true。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Money
import com.zhangran.app.domain.service.MoneyResult
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.TransactionTimeResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject

data class PendingBookDraft(
    val merchant: String?,
    val amountText: String,
    val type: TransactionType,
    val categoryId: String?,
    val accountId: String?,
    /** 绝对时间。null 或非正数表示时间为空。 */
    val transactionTimeEpochMillis: Long?,
    val timeZoneId: String,
    val note: String?,
)

data class PendingFieldErrors(
    val amount: String? = null,
    val time: String? = null,
    val type: String? = null,
    val category: String? = null,
    val account: String? = null,
    val note: String? = null,
) {
    fun isEmpty(): Boolean =
        amount == null && time == null && type == null && category == null && account == null && note == null
}

sealed interface SavePendingResult {
    data class Saved(val fieldNames: List<String>) : SavePendingResult

    data class Invalid(val errors: PendingFieldErrors) : SavePendingResult

    data class Rejected(val reason: String) : SavePendingResult
}

class SavePendingAndBookUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val fieldEdits: FieldEditRepository,
    private val clock: Clock,
) {

    suspend fun invoke(transactionId: String, draft: PendingBookDraft): SavePendingResult {
        val current = transactions.getById(transactionId)
            ?: return SavePendingResult.Rejected("记录不存在")
        if (current.state != TransactionState.PENDING) {
            return SavePendingResult.Rejected("只能入账待确认记录")
        }
        val reasons = parsePendingReasons(current.pendingReasons)
        if (PendingReason.POSSIBLE_DUPLICATE in reasons) {
            return SavePendingResult.Rejected("疑似重复不能直接入账")
        }

        val errors = validate(draft)
        if (!errors.isEmpty()) return SavePendingResult.Invalid(errors)

        val amountMinor = (Money.fromText(draft.amountText.trim()) as MoneyResult.Success).amountMinor
        val time = TransactionTime.fromEpochMillis(draft.transactionTimeEpochMillis, draft.timeZoneId)
            as TransactionTimeResult.Success
        val merchant = draft.merchant?.trim()?.takeIf { it.isNotEmpty() }
        val note = draft.note?.trim()?.takeIf { it.isNotEmpty() }
        val categoryId = draft.categoryId?.takeIf { it.isNotBlank() }
        val accountId = draft.accountId?.takeIf { it.isNotBlank() }

        val changed = linkedMapOf<String, String>()
        if (merchant != current.merchant) changed["merchant"] = merchant.orEmpty()
        if (amountMinor != current.amountMinor) changed["amountMinor"] = amountMinor.toString()
        if (draft.type != current.type) changed["type"] = draft.type.name
        if (categoryId != current.categoryId) changed["categoryId"] = categoryId.orEmpty()
        if (accountId != current.accountId) changed["accountId"] = accountId.orEmpty()
        if (time.epochMillis != current.transactionTime) {
            changed["transactionTime"] = time.epochMillis.toString()
        }
        if (note != current.note) changed["note"] = note.orEmpty()

        val now = clock.millis()
        transactions.update(
            current.copy(
                merchant = merchant,
                amountMinor = amountMinor,
                type = draft.type,
                categoryId = categoryId,
                accountId = accountId,
                sourceAccountId = null,
                targetAccountId = null,
                relatedTransactionId = null,
                transactionTime = time.epochMillis,
                transactionTimeZone = if (time.epochMillis == current.transactionTime) {
                    current.transactionTimeZone
                } else {
                    time.zoneId
                },
                note = note,
                state = TransactionState.CONFIRMED,
                confirmedByUser = true,
                updatedAt = now,
            ),
        )
        changed.forEach { (fieldName, valueText) ->
            fieldEdits.record(
                FieldEditEntity(
                    transactionId = transactionId,
                    fieldName = fieldName,
                    editedAt = now,
                    editedValueHash = sha256(valueText),
                ),
            )
        }
        return SavePendingResult.Saved(changed.keys.toList())
    }

    private suspend fun validate(draft: PendingBookDraft): PendingFieldErrors {
        var amountError: String? = null
        var timeError: String? = null
        var typeError: String? = null
        var categoryError: String? = null
        var accountError: String? = null
        var noteError: String? = null

        val amountText = draft.amountText.trim()
        val parsedAmount = if (amountText.isEmpty()) {
            amountError = "金额不能为空"
            null
        } else {
            when (val parsed = Money.fromText(amountText)) {
                is MoneyResult.Success -> parsed
                else -> {
                    amountError = "金额不合法"
                    null
                }
            }
        }
        if (parsedAmount != null && parsedAmount.amountMinor == 0L) {
            amountError = "金额不能为 0"
        }

        val parsedTime = TransactionTime.fromEpochMillis(draft.transactionTimeEpochMillis, draft.timeZoneId)
        when (parsedTime) {
            is TransactionTimeResult.Success -> Unit
            is TransactionTimeResult.Failure -> {
                timeError = if (
                    draft.transactionTimeEpochMillis == null || draft.transactionTimeEpochMillis <= 0L
                ) {
                    "时间不能为空"
                } else {
                    "时间不合法"
                }
            }
        }

        val typeOk = draft.type == TransactionType.EXPENSE || draft.type == TransactionType.INCOME
        if (!typeOk) {
            typeError = "类型只支持支出或收入"
        }

        val categoryId = draft.categoryId?.takeIf { it.isNotBlank() }
        if (categoryId != null) {
            if (!typeOk) {
                categoryError = "分类与类型不符"
            } else {
                val category = categories.all().firstOrNull { it.id == categoryId }
                val expected = if (draft.type == TransactionType.EXPENSE) {
                    CategoryType.EXPENSE
                } else {
                    CategoryType.INCOME
                }
                when {
                    category == null -> categoryError = "分类不存在"
                    category.type != expected -> categoryError = "分类与类型不符"
                }
            }
        }

        val accountId = draft.accountId?.takeIf { it.isNotBlank() }
        if (accountId != null && accounts.all().none { it.id == accountId }) {
            accountError = "支付方式不存在"
        }

        val note = draft.note?.trim()?.takeIf { it.isNotEmpty() }
        if ((note?.length ?: 0) > MAX_NOTE_LENGTH) {
            noteError = "备注过长"
        }

        return PendingFieldErrors(
            amount = amountError,
            time = timeError,
            type = typeError,
            category = categoryError,
            account = accountError,
            note = noteError,
        )
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private companion object {
        const val MAX_NOTE_LENGTH: Int = 500
    }
}
