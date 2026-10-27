/**
 * 手工记账用例（`docs/02` §1 手工入口范围、`docs/03` §4/§5/§14、`docs/04` §5）。
 *
 * 定位：**手工记账的唯一写入口**。手工记账由用户确认，直接落 `CONFIRMED`
 * （`docs/03` §14「手工记账保存」），**不经过** `Dedup` / `Decision`——那是自动记账链路。
 *
 * 依赖约束（`docs/02` §22、A11）：**不依赖任何采集能力**；本文件不引用 `capture/` 下的任何类型，
 * 即使采集核心完全不可用，本用例也必须能工作。
 *
 * 复用约束（不得重写，A1/R3/R4）：
 * - 金额校验：`Money` 的口径（`03` §4）——本用例只做 `> 0` 的业务校验；
 * - 时间/时区：`TransactionTime`；
 * - 商户规范化与备注长度：`Normalize`（M1b，`03` §5）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.PendingReason
import com.zhangran.app.domain.model.RawCandidate
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.TransactionTime
import java.util.UUID

/**
 * 手工记账输入（`docs/02` §1、`docs/03` §5 字段约束）。
 *
 * @property type 交易类型（必填）
 * @property amountMinor 金额（**最小单位，`> 0`**；方向由 [type] 表达，金额恒非负）
 * @property categoryId 分类 id（EXPENSE/INCOME 必填；TRANSFER 必须为空）
 * @property accountId 账户 id（EXPENSE/INCOME/REFUND 必填）
 * @property sourceAccountId 转出账户（仅 TRANSFER）
 * @property targetAccountId 转入账户（仅 TRANSFER）
 * @property relatedTransactionId 原交易 id（仅 REFUND；为空时落 `PENDING`）
 * @property transactionTime 交易时间（UTC epoch millis，**`> 0`**）
 * @property transactionTimeZone 交易发生时区（IANA id；为空时用设备时区）
 * @property merchant 商户原文（**由 `Normalize` 规范化**，本用例不自行去前后缀）
 * @property note 备注（超长由 `Normalize` 截断到 500）
 */
data class ManualTransactionInput(
    val type: TransactionType,
    val amountMinor: Long,
    val categoryId: String? = null,
    val accountId: String? = null,
    val sourceAccountId: String? = null,
    val targetAccountId: String? = null,
    val relatedTransactionId: String? = null,
    val transactionTime: Long,
    val transactionTimeZone: String? = null,
    val merchant: String? = null,
    val note: String? = null,
)

/** 校验错误（结构化返回，**不抛异常**，`docs/03` §4、A6）。 */
enum class ManualValidationError {
    /** V1：`amountMinor` 必须 `> 0`（`03` §4）。 */
    AMOUNT_NOT_POSITIVE,

    /** V2：`transactionTime` 必须 `> 0`，不得用 0 占位（`03` §5、P-007）。 */
    TIME_INVALID,

    /** V2 延伸：时区 id 非法（`03` §5 要求可追溯的 `transactionTimeZone`）。 */
    TIMEZONE_INVALID,

    /** V3：EXPENSE/INCOME 必须有分类（`03` §5、`02` §1）。 */
    CATEGORY_REQUIRED,

    /** V3：EXPENSE/INCOME/REFUND 必须有账户（`03` §5、`02` §1）。 */
    ACCOUNT_REQUIRED,

    /** V4：TRANSFER 必须有转出与转入账户（`03` §5）。 */
    TRANSFER_ACCOUNTS_REQUIRED,

    /** V4：TRANSFER 两侧账户不得相同（`03` §5）。 */
    TRANSFER_ACCOUNTS_SAME,

    /** V4：TRANSFER 的 `categoryId` 必须为空（`03` §5）。 */
    TRANSFER_CATEGORY_NOT_ALLOWED,
}

/** 手工记账结果。 */
sealed interface AddManualTransactionResult {

    /**
     * 落库成功。
     *
     * @property transactionId 新交易 id（UUID）
     * @property state `CONFIRMED`（正常）或 `PENDING`（V5：退款无法关联）
     * @property pendingReason 当 [state] 为 `PENDING` 时给出的原因（当前仅 `REFUND_UNCERTAIN`）
     */
    data class Success(
        val transactionId: String,
        val state: TransactionState,
        val pendingReason: PendingReason? = null,
    ) : AddManualTransactionResult

    /** 校验失败：**未落库**，返回全部错误（多错误同时返回，便于表单一次标红）。 */
    data class ValidationFailed(
        val errors: List<ManualValidationError>,
    ) : AddManualTransactionResult
}

/**
 * 手工记账用例。
 *
 * @param normalize 复用 M1b 的规范化（商户、备注长度、时间/时区）
 * @param transactionRepository 唯一落库通道
 * @param clock 落库时间源（`createdAt` / `updatedAt`；**不是**交易时间）
 * @param idGenerator 交易 id 生成器（测试注入固定值）
 */
class AddManualTransactionUseCase(
    private val normalize: Normalize,
    private val transactionRepository: TransactionRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) {

    /**
     * 校验并落库。
     *
     * 校验顺序：V1 金额 → V2 时间/时区 → V3 分类与账户 → V4 转账约束。
     * 全部通过后复用 `Normalize` 完成 V6（备注截断）与 V7（商户规范化），再决定状态：
     * - V5：`REFUND` 且无 `relatedTransactionId` → `PENDING` + `REFUND_UNCERTAIN`；
     * - 其余 → `CONFIRMED` + `confirmedByUser = true`。
     */
    suspend fun invoke(input: ManualTransactionInput): AddManualTransactionResult {
        val errors = validate(input)
        if (errors.isNotEmpty()) return AddManualTransactionResult.ValidationFailed(errors)

        // 复用 M1b 的规范化：只取它与本用例相关的产出（商户、备注、时间、时区）
        val normalized = normalize.normalize(
            RawCandidate(
                packageName = null,
                sourceChannel = null,
                captureSource = CaptureSource.MANUAL,
                amountMinor = input.amountMinor,
                currency = null,
                direction = input.type,
                merchantRaw = input.merchant,
                noteRaw = input.note,
                categoryNameRaw = null,
                accountNameRaw = null,
                transactionTimeRaw = input.transactionTime,
                transactionTimeZoneRaw = input.transactionTimeZone,
                externalTransactionId = null,
                channelOrderId = null,
                capturedAt = input.transactionTime,
                textClue = null,
            ),
        )

        val transactionTime = normalized.transactionTime
        if (transactionTime == null) {
            // Normalize 复用了 TransactionTime 的判定：时间或时区不可用
            val error = if (normalized.issues.contains(
                    com.zhangran.app.domain.service.NormalizeIssue.TIME_INVALID_ZONE,
                )
            ) {
                ManualValidationError.TIMEZONE_INVALID
            } else {
                ManualValidationError.TIME_INVALID
            }
            return AddManualTransactionResult.ValidationFailed(listOf(error))
        }

        val isRefundUnlinked = input.type == TransactionType.REFUND &&
            input.relatedTransactionId.isNullOrBlank()
        val state = if (isRefundUnlinked) TransactionState.PENDING else TransactionState.CONFIRMED
        val now = clock()
        val id = idGenerator()

        transactionRepository.insert(
            TransactionEntity(
                id = id,
                type = input.type,
                amountMinor = input.amountMinor,
                currency = com.zhangran.app.domain.model.Currency.CNY,
                merchant = normalized.merchant,
                // TRANSFER 的 categoryId 必须为空（V4 已校验输入；此处再按类型兜底）
                categoryId = if (input.type == TransactionType.TRANSFER) null else input.categoryId,
                accountId = input.accountId,
                sourceAccountId = if (input.type == TransactionType.TRANSFER) input.sourceAccountId else null,
                targetAccountId = if (input.type == TransactionType.TRANSFER) input.targetAccountId else null,
                relatedTransactionId = input.relatedTransactionId,
                transactionTime = transactionTime,
                transactionTimeZone = normalized.transactionTimeZone
                    ?: TransactionTime.deviceZoneId(),
                note = normalized.note,
                state = state,
                source = CaptureSource.MANUAL,
                externalTransactionId = null,
                engineVersion = null,
                matchedRuleId = null,
                evidenceId = null,
                confirmedByUser = true,
                orphan = false,
                createdAt = now,
                updatedAt = now,
                deletedAt = null,
            ),
        )

        return AddManualTransactionResult.Success(
            transactionId = id,
            state = state,
            pendingReason = if (isRefundUnlinked) PendingReason.REFUND_UNCERTAIN else null,
        )
    }

    /** V1–V4 的输入校验（规则见 `docs/03` §4、§5 与任务书 §4.2 表格）。 */
    private fun validate(input: ManualTransactionInput): List<ManualValidationError> {
        val errors = mutableListOf<ManualValidationError>()

        // V1：金额必须 > 0（03 §4：方向由 type 表达，金额恒非负，且 0 不是有效金额）
        if (input.amountMinor <= 0L) errors += ManualValidationError.AMOUNT_NOT_POSITIVE

        // V2：时间必须 > 0（03 §5、P-007：不得用 0 占位）
        if (input.transactionTime <= 0L) errors += ManualValidationError.TIME_INVALID

        when (input.type) {
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                if (input.categoryId.isNullOrBlank()) errors += ManualValidationError.CATEGORY_REQUIRED
                if (input.accountId.isNullOrBlank()) errors += ManualValidationError.ACCOUNT_REQUIRED
            }

            TransactionType.TRANSFER -> {
                if (input.sourceAccountId.isNullOrBlank() || input.targetAccountId.isNullOrBlank()) {
                    errors += ManualValidationError.TRANSFER_ACCOUNTS_REQUIRED
                } else if (input.sourceAccountId == input.targetAccountId) {
                    errors += ManualValidationError.TRANSFER_ACCOUNTS_SAME
                }
                if (!input.categoryId.isNullOrBlank()) {
                    errors += ManualValidationError.TRANSFER_CATEGORY_NOT_ALLOWED
                }
            }

            TransactionType.REFUND -> {
                // relatedTransactionId 允许为空：按 V5 落 PENDING（不视为校验失败）
                if (input.accountId.isNullOrBlank()) errors += ManualValidationError.ACCOUNT_REQUIRED
            }
        }

        return errors
    }
}
