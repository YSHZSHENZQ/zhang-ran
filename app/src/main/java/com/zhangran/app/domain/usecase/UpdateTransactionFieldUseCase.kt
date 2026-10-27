/**
 * 字段级编辑的唯一入口（`M2b` §4.3；`docs/03` §11、`docs/08` §5）。
 *
 * 一次成功编辑 = **一条 `FieldEdit`** + 一次交易更新；校验失败时**不落库、不写 `FieldEdit`**。
 *
 * 纪律：
 * - **不改 `state`**（已 `CONFIRMED` 仍为 `CONFIRMED`），但成功后必须置 `confirmedByUser = true`；
 * - 金额只接受 `Long amountMinor`（禁止 `Double` / `Float`），且 `0 < amount <= Money.MAX_AMOUNT_MINOR`；
 * - 类型只允许 `EXPENSE ⇄ INCOME`（`TRANSFER` / `REFUND` 属 M2c，一律拒绝）；
 * - 类型切换时**清空分类**（`docs/03` §7：收支分类不可混用），由用户重选；
 * - 时间经 `TransactionTime` 校验（时区显式、时间必须为正）；
 * - 备注 ≤ 500 字符（`docs/03` §5）；
 * - `fieldName` 取值复用 [com.zhangran.app.domain.model.ProtectedField.ALLOWED_FIELD_NAMES]（SSOT）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.FieldEditRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.ProtectedField
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Money
import com.zhangran.app.domain.service.MAX_AMOUNT_MINOR
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.TransactionTimeResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject

/**
 * 详情页可编辑的 7 个字段（`M2b` §4.2）。
 *
 * @property fieldName 落库用的字段名，与 `docs/03` §11 的名单**逐字一致**
 */
enum class EditableField(val fieldName: String) {
    MERCHANT("merchant"),
    AMOUNT("amountMinor"),
    TYPE("type"),
    CATEGORY("categoryId"),
    ACCOUNT("accountId"),
    TIME("transactionTime"),
    NOTE("note"),
    ;

    init {
        // 编译期常量表校验：字段名一旦与 SSOT 漂移，类加载即失败（A5）
        require(fieldName in ProtectedField.ALLOWED_FIELD_NAMES) {
            "字段名 $fieldName 不在 docs/03 §11 的保护字段名单内"
        }
    }
}

/**
 * 一次编辑提交的新值（与 [EditableField] 一一对应）。
 *
 * 用密封类而不是 `Any`：校验器可以按类型精确匹配，错配的 field/value 会被显式拒绝。
 */
sealed interface FieldEditValue {

    data class Merchant(val value: String?) : FieldEditValue

    data class Amount(val amountMinor: Long) : FieldEditValue

    data class Type(val type: TransactionType) : FieldEditValue

    data class Category(val categoryId: String?) : FieldEditValue

    data class Account(val accountId: String?) : FieldEditValue

    /** @property zoneId 用户选择时间时所在的时区（IANA id） */
    data class Time(val epochMillis: Long, val zoneId: String) : FieldEditValue

    data class Note(val value: String?) : FieldEditValue
}

/** 编辑被拒绝的原因（`M2b` §4.3；UI 据此给出可读提示，A17）。 */
enum class FieldEditRejection {
    /** field 与 value 类型不匹配（编程错误，不应由 UI 触发）。 */
    VALUE_MISMATCH,

    /** 交易不存在（或已软删除）。 */
    NOT_FOUND,

    /** 金额非法：≤ 0 或超出上限。 */
    AMOUNT_INVALID,

    /** 类型改动不被允许（只允许 EXPENSE ⇄ INCOME）。 */
    TYPE_NOT_SUPPORTED,

    /** 分类不存在。 */
    CATEGORY_MISSING,

    /** 分类不属于当前类型（`docs/03` §7 禁止混用）。 */
    CATEGORY_TYPE_MISMATCH,

    /** 账户不存在。 */
    ACCOUNT_MISSING,

    /** 时间非法（≤ 0、超范围或时区非法）。 */
    TIME_INVALID,

    /** 备注超过 500 字符。 */
    NOTE_TOO_LONG,
}

/**
 * 编辑结果。
 *
 * @property changed `false` 表示新值与当前值相同（不写库、不写 `FieldEdit`）
 */
sealed interface UpdateTransactionFieldResult {

    data class Success(val changed: Boolean) : UpdateTransactionFieldResult

    data class Rejected(val reason: FieldEditRejection) : UpdateTransactionFieldResult
}

/**
 * 单字段更新用例。
 *
 * @param transactions 交易读写（`update` 是本次新增的方法）
 * @param categories 分类校验（分类必须存在且属于当前 type）
 * @param accounts 账户校验
 * @param fieldEdits 写入字段级保护记录
 * @param clock 时间源（`editedAt` / `updatedAt`；测试注入固定时钟）
 */
class UpdateTransactionFieldUseCase @Inject constructor(
    private val transactions: TransactionRepository,
    private val categories: CategoryRepository,
    private val accounts: AccountRepository,
    private val fieldEdits: FieldEditRepository,
    private val clock: Clock,
) {

    /**
     * 更新一个字段。
     *
     * @throws IllegalArgumentException 仅当调用方传入非法 `fieldName`（枚举已保证不会发生）
     */
    suspend fun invoke(
        transactionId: String,
        field: EditableField,
        value: FieldEditValue,
    ): UpdateTransactionFieldResult {
        require(field.fieldName in ProtectedField.ALLOWED_FIELD_NAMES) {
            "未知的受保护字段：${field.fieldName}"
        }
        val current = transactions.getById(transactionId) ?: return UpdateTransactionFieldResult.Rejected(FieldEditRejection.NOT_FOUND)

        return when (val evaluation = evaluate(current, field, value)) {
            is Evaluation.Rejected -> UpdateTransactionFieldResult.Rejected(evaluation.reason)
            Evaluation.Unchanged -> UpdateTransactionFieldResult.Success(changed = false)
            is Evaluation.Changed -> {
                val now = clock.millis()
                transactions.update(
                    evaluation.updated.copy(
                        updatedAt = now,
                        // 编辑即确认（`docs/03` §11；P-010）：失败路径不会走到这里
                        confirmedByUser = true,
                    ),
                )
                fieldEdits.record(
                    FieldEditEntity(
                        transactionId = transactionId,
                        fieldName = field.fieldName,
                        editedAt = now,
                        editedValueHash = sha256(evaluation.valueText),
                    ),
                )
                UpdateTransactionFieldResult.Success(changed = true)
            }
        }
    }

    /** 校验并计算"更新后的交易"，不做任何写入。 */
    private suspend fun evaluate(
        current: com.zhangran.app.data.local.entity.TransactionEntity,
        field: EditableField,
        value: FieldEditValue,
    ): Evaluation = when (field) {
        EditableField.MERCHANT -> {
            val merchant = value as? FieldEditValue.Merchant
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val normalized = merchant.value?.trim()?.takeIf { it.isNotEmpty() }
            if (normalized == current.merchant) {
                Evaluation.Unchanged
            } else {
                Evaluation.Changed(
                    updated = current.copy(merchant = normalized),
                    valueText = normalized.orEmpty(),
                )
            }
        }

        EditableField.AMOUNT -> {
            val amount = value as? FieldEditValue.Amount
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            if (amount.amountMinor <= 0L || amount.amountMinor > MAX_AMOUNT_MINOR) {
                return Evaluation.Rejected(FieldEditRejection.AMOUNT_INVALID)
            }
            if (amount.amountMinor == current.amountMinor) {
                Evaluation.Unchanged
            } else {
                Evaluation.Changed(
                    updated = current.copy(amountMinor = amount.amountMinor),
                    valueText = amount.amountMinor.toString(),
                )
            }
        }

        EditableField.TYPE -> {
            val type = value as? FieldEditValue.Type
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val editable = type.type == TransactionType.EXPENSE || type.type == TransactionType.INCOME
            val currentEditable = current.type == TransactionType.EXPENSE || current.type == TransactionType.INCOME
            when {
                !editable || !currentEditable -> Evaluation.Rejected(FieldEditRejection.TYPE_NOT_SUPPORTED)
                type.type == current.type -> Evaluation.Unchanged
                // 类型切换：清空分类，提示用户重选（`docs/03` §7）
                else -> Evaluation.Changed(
                    updated = current.copy(type = type.type, categoryId = null),
                    valueText = type.type.name,
                )
            }
        }

        EditableField.CATEGORY -> {
            val category = value as? FieldEditValue.Category
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val targetType = current.type.toCategoryTypeOrNull()
                ?: return Evaluation.Rejected(FieldEditRejection.CATEGORY_TYPE_MISMATCH)
            val categoryId = category.categoryId
                ?: return Evaluation.Rejected(FieldEditRejection.CATEGORY_MISSING)
            val entity = categories.all().firstOrNull { it.id == categoryId }
                ?: return Evaluation.Rejected(FieldEditRejection.CATEGORY_MISSING)
            if (entity.type != targetType) {
                return Evaluation.Rejected(FieldEditRejection.CATEGORY_TYPE_MISMATCH)
            }
            if (categoryId == current.categoryId) {
                Evaluation.Unchanged
            } else {
                Evaluation.Changed(
                    updated = current.copy(categoryId = categoryId),
                    valueText = categoryId,
                )
            }
        }

        EditableField.ACCOUNT -> {
            val account = value as? FieldEditValue.Account
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val accountId = account.accountId
                ?: return Evaluation.Rejected(FieldEditRejection.ACCOUNT_MISSING)
            if (accounts.all().none { it.id == accountId }) {
                return Evaluation.Rejected(FieldEditRejection.ACCOUNT_MISSING)
            }
            if (accountId == current.accountId) {
                Evaluation.Unchanged
            } else {
                Evaluation.Changed(
                    updated = current.copy(accountId = accountId),
                    valueText = accountId,
                )
            }
        }

        EditableField.TIME -> {
            val time = value as? FieldEditValue.Time
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val validated = TransactionTime.fromEpochMillis(time.epochMillis, time.zoneId)
            if (validated !is TransactionTimeResult.Success) {
                return Evaluation.Rejected(FieldEditRejection.TIME_INVALID)
            }
            if (time.epochMillis == current.transactionTime) {
                Evaluation.Unchanged
            } else {
                // 只改时刻，不改交易发生时区（`transactionTimeZone` 记录的是"发生时"的时区）
                Evaluation.Changed(
                    updated = current.copy(transactionTime = time.epochMillis),
                    valueText = time.epochMillis.toString(),
                )
            }
        }

        EditableField.NOTE -> {
            val note = value as? FieldEditValue.Note
                ?: return Evaluation.Rejected(FieldEditRejection.VALUE_MISMATCH)
            val text = note.value?.takeIf { it.isNotBlank() }
            if ((text?.length ?: 0) > MAX_NOTE_LENGTH) {
                return Evaluation.Rejected(FieldEditRejection.NOTE_TOO_LONG)
            }
            if (text == current.note) {
                Evaluation.Unchanged
            } else {
                Evaluation.Changed(
                    updated = current.copy(note = text),
                    valueText = text.orEmpty(),
                )
            }
        }
    }

    private sealed interface Evaluation {
        data object Unchanged : Evaluation

        data class Changed(
            val updated: com.zhangran.app.data.local.entity.TransactionEntity,
            val valueText: String,
        ) : Evaluation

        data class Rejected(val reason: FieldEditRejection) : Evaluation
    }

    private fun TransactionType.toCategoryTypeOrNull(): CategoryType? = when (this) {
        TransactionType.EXPENSE -> CategoryType.EXPENSE
        TransactionType.INCOME -> CategoryType.INCOME
        TransactionType.TRANSFER, TransactionType.REFUND -> null
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xFF) }

    private companion object {
        /** 备注长度上限（`docs/03` §5）。 */
        const val MAX_NOTE_LENGTH: Int = 500
    }
}
