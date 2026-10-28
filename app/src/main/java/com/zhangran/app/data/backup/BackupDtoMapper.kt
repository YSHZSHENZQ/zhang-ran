/**
 * DTO → Entity 映射与字段校验/清洗（`M4b-2` §3）。
 *
 * 校验失败一律抛 [BackupValidationException]；调用方必须在开启事务前完成映射，
 * 以保证"校验失败不修改任何数据"（V7）。
 *
 * 清洗原则（不改语义、只修可安全修正的形态）：
 * - 金额不得为负（金额方向由 `type` 表达）；
 * - 账户 `last4` 只保留最多 4 位数字；
 * - `BudgetDto`：TOTAL 强制 `categoryId = null`，CATEGORY 必须有 `categoryId`；
 * - 证据 `confidence` 夹到 0..1；
 * - 分类图标为空时回退 `other`。
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
import com.zhangran.app.domain.model.BudgetScope

/** 已映射、可直接写入的数据（顺序与导入顺序一致：主表在前）。 */
data class RestoreEntities(
    val categories: List<CategoryEntity>,
    val accounts: List<AccountEntity>,
    val budgets: List<BudgetEntity>,
    val rules: List<ParsingRuleEntity>,
    val transactions: List<TransactionEntity>,
    val evidence: List<CaptureEvidenceEntity>,
    val duplicateDecisions: List<DuplicateDecisionEntity>,
    val captureLogs: List<CaptureLogEntity>,
    val fieldEdits: List<FieldEditEntity>,
)

/** 全部映射 + 校验；任何一条非法 → 整体失败（不写入）。 */
fun RestorePayload.toEntities(): RestoreEntities = RestoreEntities(
    categories = categories.map { it.toEntity() },
    accounts = accounts.map { it.toEntity() },
    budgets = budgets.map { it.toEntity() },
    rules = rules.map { it.toEntity() },
    transactions = transactions.map { it.toEntity() },
    evidence = evidence.map { it.toEntity() },
    duplicateDecisions = duplicateDecisions.map { it.toEntity() },
    captureLogs = captureLogs.map { it.toEntity() },
    fieldEdits = fieldEdits.map { it.toEntity() },
)

private fun requireText(value: String, what: String): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty()) throw BackupValidationException("备份内容无效：$what 为空")
    return value
}

private fun requireNonNegative(value: Long, what: String): Long {
    if (value < 0L) throw BackupValidationException("备份内容无效：$what 为负数")
    return value
}

fun TransactionDto.toEntity(): TransactionEntity {
    requireText(id, "交易 id")
    requireText(transactionTimeZone, "交易时区")
    requireNonNegative(amountMinor, "交易金额")
    return TransactionEntity(
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
    )
}

fun CategoryDto.toEntity(): CategoryEntity {
    requireText(id, "分类 id")
    requireText(name, "分类名称")
    return CategoryEntity(
        id = id,
        name = name,
        type = type,
        icon = icon.ifBlank { "other" },
        sortOrder = sortOrder,
        enabled = enabled,
        systemDefined = systemDefined,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun AccountDto.toEntity(): AccountEntity {
    requireText(id, "账户 id")
    requireText(name, "账户名称")
    val sanitizedLast4 = last4?.filter { it.isDigit() }?.takeLast(4)
    return AccountEntity(
        id = id,
        name = name,
        type = type,
        channel = channel,
        last4 = sanitizedLast4,
        enabled = enabled,
        sortOrder = sortOrder,
        isDefault = isDefault,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun BudgetDto.toEntity(): BudgetEntity {
    requireText(id, "预算 id")
    requireText(periodKey, "预算期间")
    requireNonNegative(limitMinor, "预算额度")
    val normalizedCategoryId = when (scope) {
        BudgetScope.TOTAL -> null
        BudgetScope.CATEGORY -> categoryId
            ?: throw BackupValidationException("备份内容无效：分类预算缺少 categoryId")
    }
    return BudgetEntity(
        id = id,
        scope = scope,
        categoryId = normalizedCategoryId,
        period = period,
        periodKey = periodKey,
        limitMinor = limitMinor,
        enabled = enabled,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ParsingRuleDto.toEntity(): ParsingRuleEntity {
    requireText(ruleId, "规则 id")
    if (priority < 0) throw BackupValidationException("备份内容无效：规则优先级为负数")
    return ParsingRuleEntity(
        ruleId = ruleId,
        priority = priority,
        enabled = enabled,
        direction = direction,
        include = include.ifBlank { "[]" },
        exclude = exclude.ifBlank { "[]" },
        matchMode = matchMode,
        engineVersion = engineVersion,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun CaptureEvidenceDto.toEntity(): CaptureEvidenceEntity {
    requireText(id, "证据 id")
    requireText(captureId, "证据 captureId")
    return CaptureEvidenceEntity(
        id = id,
        captureId = captureId,
        captureSource = captureSource,
        engineVersion = engineVersion,
        ruleId = ruleId,
        confidence = confidence?.coerceIn(0.0, 1.0),
        matchedKeywords = matchedKeywords,
        rejectedKeywords = rejectedKeywords,
        // rawCaptureRef 不在 DTO 中：恢复后为 null（备份本就不含原始捕获引用）
        rawCaptureRef = null,
        capturedAt = capturedAt,
    )
}

fun DuplicateDecisionDto.toEntity(): DuplicateDecisionEntity {
    requireText(id, "重复判定 id")
    requireText(candidateId, "重复判定 candidateId")
    requireText(existingTransactionId, "重复判定 existingTransactionId")
    return DuplicateDecisionEntity(
        id = id,
        candidateId = candidateId,
        candidateTransactionId = candidateTransactionId,
        existingTransactionId = existingTransactionId,
        verdict = verdict,
        decidedBy = decidedBy,
        decidedAt = decidedAt,
        reason = reason,
        matchSignals = matchSignals,
        evidenceId = evidenceId,
        revokedAt = revokedAt,
    )
}

fun CaptureLogDto.toEntity(): CaptureLogEntity {
    requireText(id, "日志 id")
    requireText(captureId, "日志 captureId")
    if (payloadVersion < 0) throw BackupValidationException("备份内容无效：日志 payloadVersion 为负数")
    return CaptureLogEntity(
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
        reason = reason,
        isHistoricalReplay = isHistoricalReplay,
        rawCaptureRef = null,
        evidenceId = evidenceId,
        transactionId = transactionId,
    )
}

fun FieldEditDto.toEntity(): FieldEditEntity {
    requireText(transactionId, "字段保护 transactionId")
    requireText(fieldName, "字段保护 fieldName")
    return FieldEditEntity(
        transactionId = transactionId,
        fieldName = fieldName,
        editedAt = editedAt,
        editedValueHash = editedValueHash,
    )
}
