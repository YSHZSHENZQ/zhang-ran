/**
 * 备份用 DTO（`M4b-1` §4.3/§4.4；字段名与 `docs/03` 实体一致）。
 *
 * 设计：
 * - 每个 DTO 与实体一一对应，**金额一律 `Long`（分）**、时间一律 UTC epoch millis；
 * - 枚举用名称字符串（kotlinx 默认行为），恢复侧可读；
 * - **排除清单**：`CaptureEvidenceEntity.rawCaptureRef`、`CaptureLogEntity.rawCaptureRef`
 *   —— 它们是"原始捕获引用/文本"（可能指向已删除的临时截图或通知原文），
 *   备份只保留结构化证据，避免把截图类内容带出去（`02` §17）；
 * - **脱敏**：捕获链路产生的自由文本（证据关键词、日志原因、重复判定说明/信号）
 *   在导出前再做一次"手机号 / 银行卡号 / 身份证号"掩码，避免上游漏脱敏时把敏感值带出。
 *   用户自己写的字段（`merchant` / `note`）**原样导出**：备份必须可原样恢复，
 *   对用户数据做不可逆掩码等于数据丢失。
 */
package com.zhangran.app.data.backup

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.local.entity.DuplicateDecisionEntity
import com.zhangran.app.data.local.entity.FieldEditEntity
import com.zhangran.app.data.local.entity.ParsingRuleEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.settings.AppSettings
import com.zhangran.app.data.settings.SettingsDefaults
import com.zhangran.app.data.settings.ThemeMode
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
import kotlinx.serialization.Serializable

@Serializable
data class TransactionDto(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val currency: Currency,
    val merchant: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val sourceAccountId: String? = null,
    val targetAccountId: String? = null,
    val relatedTransactionId: String? = null,
    val transactionTime: Long,
    val transactionTimeZone: String,
    val note: String? = null,
    val state: TransactionState,
    val source: CaptureSource,
    val externalTransactionId: String? = null,
    val engineVersion: String? = null,
    val matchedRuleId: String? = null,
    val evidenceId: String? = null,
    val confirmedByUser: Boolean = false,
    val orphan: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val pendingReasons: String? = null,
)

@Serializable
data class CategoryDto(
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val sortOrder: Int,
    val enabled: Boolean = true,
    val systemDefined: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class AccountDto(
    val id: String,
    val name: String,
    val type: AccountType,
    val channel: PaymentChannel? = null,
    /** 仅后 4 位（实体本身只存 last4，`02` §20）。 */
    val last4: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BudgetDto(
    val id: String,
    val scope: BudgetScope,
    val categoryId: String? = null,
    val period: BudgetPeriod = BudgetPeriod.MONTH,
    val periodKey: String,
    val limitMinor: Long,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ParsingRuleDto(
    val ruleId: String,
    val priority: Int,
    val enabled: Boolean = true,
    val direction: TransactionType? = null,
    /** JSON 数组字符串（canonical，`02` §16）。 */
    val include: String = "[]",
    val exclude: String = "[]",
    val matchMode: MatchMode = MatchMode.DEFAULT,
    val engineVersion: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CaptureEvidenceDto(
    val id: String,
    val captureId: String,
    val captureSource: CaptureSource,
    val engineVersion: String? = null,
    val ruleId: String? = null,
    val confidence: Double? = null,
    val matchedKeywords: String? = null,
    val rejectedKeywords: String? = null,
    val capturedAt: Long,
)

@Serializable
data class DuplicateDecisionDto(
    val id: String,
    val candidateId: String,
    val candidateTransactionId: String? = null,
    val existingTransactionId: String,
    val verdict: DuplicateVerdict,
    val decidedBy: DecisionActor,
    val decidedAt: Long,
    val reason: String? = null,
    val matchSignals: String? = null,
    val evidenceId: String? = null,
    val revokedAt: Long? = null,
)

@Serializable
data class CaptureLogDto(
    val id: String,
    val captureId: String,
    val capturedAt: Long,
    val captureSource: CaptureSource,
    val packageName: String? = null,
    val channel: String? = null,
    val parserRuleId: String? = null,
    val engineVersion: String? = null,
    val payloadVersion: Int,
    val status: CaptureLogStatus,
    val reason: String? = null,
    val isHistoricalReplay: Boolean = false,
    val evidenceId: String? = null,
    val transactionId: String? = null,
)

@Serializable
data class FieldEditDto(
    val transactionId: String,
    val fieldName: String,
    val editedAt: Long,
    /** 只存哈希，不含原值（实体本身如此）。 */
    val editedValueHash: String? = null,
)

@Serializable
data class SettingsDto(
    /** 枚举名称字符串（`SYSTEM` / `LIGHT` / `DARK`）。 */
    val themeMode: String,
    val overallConfidenceThreshold: Double,
    val fieldConfidenceThreshold: Double,
)

// ------------------------------------------------------------------ 实体 → DTO

fun TransactionEntity.toDto(): TransactionDto = TransactionDto(
    id = id,
    type = type,
    amountMinor = amountMinor,
    currency = currency,
    merchant = merchant,
    categoryId = categoryId,
    accountId = accountId,
    sourceAccountId = sourceAccountId,
    targetAccountId = targetAccountId,
    relatedTransactionId = relatedTransactionId,
    transactionTime = transactionTime,
    transactionTimeZone = transactionTimeZone,
    note = note,
    state = state,
    source = source,
    externalTransactionId = externalTransactionId,
    engineVersion = engineVersion,
    matchedRuleId = matchedRuleId,
    evidenceId = evidenceId,
    confirmedByUser = confirmedByUser,
    orphan = orphan,
    createdAt = createdAt,
    updatedAt = updatedAt,
    deletedAt = deletedAt,
    pendingReasons = pendingReasons,
)

fun CategoryEntity.toDto(): CategoryDto = CategoryDto(
    id = id,
    name = name,
    type = type,
    icon = icon,
    sortOrder = sortOrder,
    enabled = enabled,
    systemDefined = systemDefined,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun AccountEntity.toDto(): AccountDto = AccountDto(
    id = id,
    name = name,
    type = type,
    channel = channel,
    last4 = last4,
    enabled = enabled,
    sortOrder = sortOrder,
    isDefault = isDefault,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun BudgetEntity.toDto(): BudgetDto = BudgetDto(
    id = id,
    scope = scope,
    categoryId = categoryId,
    period = period,
    periodKey = periodKey,
    limitMinor = limitMinor,
    enabled = enabled,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ParsingRuleEntity.toDto(): ParsingRuleDto = ParsingRuleDto(
    ruleId = ruleId,
    priority = priority,
    enabled = enabled,
    direction = direction,
    include = include,
    exclude = exclude,
    matchMode = matchMode,
    engineVersion = engineVersion,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** 排除 `rawCaptureRef`（原始截图的引用/文本，见文件头）。 */
fun CaptureEvidenceEntity.toDto(): CaptureEvidenceDto = CaptureEvidenceDto(
    id = id,
    captureId = captureId,
    captureSource = captureSource,
    engineVersion = engineVersion,
    ruleId = ruleId,
    confidence = confidence,
    matchedKeywords = scrubSensitiveText(matchedKeywords),
    rejectedKeywords = scrubSensitiveText(rejectedKeywords),
    capturedAt = capturedAt,
)

fun DuplicateDecisionEntity.toDto(): DuplicateDecisionDto = DuplicateDecisionDto(
    id = id,
    candidateId = candidateId,
    candidateTransactionId = candidateTransactionId,
    existingTransactionId = existingTransactionId,
    verdict = verdict,
    decidedBy = decidedBy,
    decidedAt = decidedAt,
    reason = scrubSensitiveText(reason),
    matchSignals = scrubSensitiveText(matchSignals),
    evidenceId = evidenceId,
    revokedAt = revokedAt,
)

/** 排除 `rawCaptureRef`；`reason` 做一次敏感数字掩码。 */
fun CaptureLogEntity.toDto(): CaptureLogDto = CaptureLogDto(
    id = id,
    captureId = captureId,
    capturedAt = capturedAt,
    captureSource = captureSource,
    packageName = packageName,
    channel = channel,
    parserRuleId = parserRuleId,
    engineVersion = engineVersion,
    payloadVersion = payloadVersion,
    status = status,
    reason = scrubSensitiveText(reason),
    isHistoricalReplay = isHistoricalReplay,
    evidenceId = evidenceId,
    transactionId = transactionId,
)

fun FieldEditEntity.toDto(): FieldEditDto = FieldEditDto(
    transactionId = transactionId,
    fieldName = fieldName,
    editedAt = editedAt,
    editedValueHash = editedValueHash,
)

fun AppSettings.toDto(): SettingsDto = SettingsDto(
    themeMode = themeMode.name,
    overallConfidenceThreshold = overallConfidenceThreshold,
    fieldConfidenceThreshold = fieldConfidenceThreshold,
)

/** 供 M4b-2 复用：字符串 → 枚举，未知值回退默认。 */
fun SettingsDto.toAppSettings(): AppSettings = AppSettings(
    themeMode = ThemeMode.entries.firstOrNull { it.name == themeMode } ?: ThemeMode.SYSTEM,
    overallConfidenceThreshold = overallConfidenceThreshold.takeIf { it in 0.0..1.0 }
        ?: SettingsDefaults.OVERALL_CONFIDENCE_THRESHOLD,
    fieldConfidenceThreshold = fieldConfidenceThreshold.takeIf { it in 0.0..1.0 }
        ?: SettingsDefaults.FIELD_CONFIDENCE_THRESHOLD,
)

// ------------------------------------------------------------------ 敏感文本掩码

private val ID_CARD_REGEX = Regex("""(?<!\d)(\d{6})\d{8}(\d{3}[\dXx])(?!\d)""")
private val BANK_CARD_REGEX = Regex("""(?<!\d)(\d{4})[\s-]?\d{4}[\s-]?\d{4}[\s-]?(\d{4,7})(?!\d)""")
private val PHONE_REGEX = Regex("""(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)""")

/**
 * 对捕获链路文本做"手机号 / 银行卡号 / 身份证号"掩码（保留首尾便于人工核对）。
 *
 * 只用于**捕获证据/日志/重复判定**字段；用户手写字段不经过这里（见文件头）。
 */
internal fun scrubSensitiveText(text: String?): String? {
    if (text.isNullOrEmpty()) return text
    return text
        .replace(ID_CARD_REGEX, "$1********$2")
        .replace(BANK_CARD_REGEX, "$1********$2")
        .replace(PHONE_REGEX, "$1****$2")
}
