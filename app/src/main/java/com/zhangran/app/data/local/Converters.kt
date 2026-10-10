/**
 * Room 类型转换器（枚举 ↔ TEXT）。
 *
 * 为什么需要：`docs/04` §3 要求领域枚举只在 `domain/model/` 定义一次，实体只引用它；
 * 而 Room 默认把枚举当作未支持类型。这里统一以**枚举名**存为 TEXT：
 * - 可读性：数据库里直接是 `EXPENSE` / `CONFIRMED`，便于排查；
 * - 迁移稳定：不依赖序号（`ordinal`），将来插入新取值不会让旧数据错位；
 * - 与 `07` §5「存储值必须为枚举」一致。
 *
 * 容错：遇到未知字符串返回 `null`（由实体字段的可空性决定是否合法），
 * 不抛异常——避免一条坏数据让整个查询崩溃（`docs/07` §18 故障隔离的同类思路）。
 */
package com.zhangran.app.data.local

import androidx.room.TypeConverter
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.BudgetPeriod
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.DecisionActor
import com.zhangran.app.domain.model.DuplicateVerdict
import com.zhangran.app.domain.model.MatchMode
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType

/**
 * 枚举转换器集合。
 */
class Converters {

    // ---------- TransactionType ----------

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? = value?.name

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? = value?.let {
        runCatching { TransactionType.valueOf(it) }.getOrNull()
    }

    // ---------- TransactionState ----------

    @TypeConverter
    fun fromTransactionState(value: TransactionState?): String? = value?.name

    @TypeConverter
    fun toTransactionState(value: String?): TransactionState? = value?.let {
        runCatching { TransactionState.valueOf(it) }.getOrNull()
    }

    // ---------- CaptureSource ----------

    @TypeConverter
    fun fromCaptureSource(value: CaptureSource?): String? = value?.name

    @TypeConverter
    fun toCaptureSource(value: String?): CaptureSource? = value?.let {
        runCatching { CaptureSource.valueOf(it) }.getOrNull()
    }

    // ---------- Currency ----------

    @TypeConverter
    fun fromCurrency(value: Currency?): String? = value?.name

    @TypeConverter
    fun toCurrency(value: String?): Currency? = value?.let {
        runCatching { Currency.valueOf(it) }.getOrNull()
    }

    // ---------- CategoryType ----------

    @TypeConverter
    fun fromCategoryType(value: CategoryType?): String? = value?.name

    @TypeConverter
    fun toCategoryType(value: String?): CategoryType? = value?.let {
        runCatching { CategoryType.valueOf(it) }.getOrNull()
    }

    // ---------- AccountType ----------

    @TypeConverter
    fun fromAccountType(value: AccountType?): String? = value?.name

    @TypeConverter
    fun toAccountType(value: String?): AccountType? = value?.let {
        runCatching { AccountType.valueOf(it) }.getOrNull()
    }

    // ---------- PaymentChannel ----------

    @TypeConverter
    fun fromPaymentChannel(value: PaymentChannel?): String? = value?.name

    @TypeConverter
    fun toPaymentChannel(value: String?): PaymentChannel? = value?.let {
        runCatching { PaymentChannel.valueOf(it) }.getOrNull()
    }

    // ---------- BudgetScope ----------

    @TypeConverter
    fun fromBudgetScope(value: BudgetScope?): String? = value?.name

    @TypeConverter
    fun toBudgetScope(value: String?): BudgetScope? = value?.let {
        runCatching { BudgetScope.valueOf(it) }.getOrNull()
    }

    // ---------- BudgetPeriod ----------

    @TypeConverter
    fun fromBudgetPeriod(value: BudgetPeriod?): String? = value?.name

    @TypeConverter
    fun toBudgetPeriod(value: String?): BudgetPeriod? = value?.let {
        runCatching { BudgetPeriod.valueOf(it) }.getOrNull()
    }

    // ---------- MatchMode ----------

    @TypeConverter
    fun fromMatchMode(value: MatchMode?): String? = value?.name

    @TypeConverter
    fun toMatchMode(value: String?): MatchMode? = value?.let {
        runCatching { MatchMode.valueOf(it) }.getOrNull()
    }

    // ---------- CaptureLogStatus ----------

    @TypeConverter
    fun fromCaptureLogStatus(value: CaptureLogStatus?): String? = value?.name

    @TypeConverter
    fun toCaptureLogStatus(value: String?): CaptureLogStatus? = value?.let {
        runCatching { CaptureLogStatus.valueOf(it) }.getOrNull()
    }

    // ---------- DuplicateVerdict ----------

    @TypeConverter
    fun fromDuplicateVerdict(value: DuplicateVerdict?): String? = value?.name

    @TypeConverter
    fun toDuplicateVerdict(value: String?): DuplicateVerdict? = value?.let {
        runCatching { DuplicateVerdict.valueOf(it) }.getOrNull()
    }

    // ---------- DecisionActor ----------

    @TypeConverter
    fun fromDecisionActor(value: DecisionActor?): String? = value?.name

    @TypeConverter
    fun toDecisionActor(value: String?): DecisionActor? = value?.let {
        runCatching { DecisionActor.valueOf(it) }.getOrNull()
    }
}
