/**
 * 账户写操作（`M2d-2` §4.2；规则 SSOT：`docs/03` §8、`docs/06` §4.13）。
 *
 * **唯一写入口**：新增/更新账户都只能经过本用例。校验失败返回结构化错误（不落库、不抛异常）。
 *
 * 规则映射（任务书 V1–V8）：
 * - V1 名称 trim 后非空、全库唯一（更新排除自身）；
 * - V2 `last4` 若填必须 4 位数字；`CASH` / `THIRD_PARTY` 禁止填写；
 * - V3 `channel` 为枚举（类型系统已保证合法，空表示"无"）；
 * - V4 **不实现删除**；
 * - V5 禁用前检查 `enabledCount > 1`（D-1：至少保留一个启用账户）；
 * - V6 新建即默认 / 更新设为默认 → 委托 [AccountRepository.setDefault]（同一事务先清后置）；
 * - V7 更新时 `type` 不得变更；
 * - V8 名称长度 ≤ 20（`03` §8 无上限，本任务取值，属**补充**规则）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** 账户名称长度上限（任务书 V8；属**补充**规则）。 */
const val MAX_ACCOUNT_NAME_LENGTH: Int = 20

/** 卡号后四位长度。 */
const val ACCOUNT_LAST4_LENGTH: Int = 4

/** 一次账户写操作入参。 */
data class AccountInput(
    val id: String? = null,
    val name: String,
    val type: AccountType,
    val channel: PaymentChannel? = null,
    val last4: String? = null,
    val enabled: Boolean = true,
    /** 新增时是否设为默认；更新时 `true` 表示"设为默认"（`false` 不会取消既有默认）。 */
    val isDefault: Boolean = false,
    val sortOrder: Int? = null,
)

/** 账户写操作的拒绝原因（V1–V8）。 */
enum class AccountSaveError {
    /** V1：名称为空。 */
    NAME_EMPTY,

    /** V8：名称超长（补充规则）。 */
    NAME_TOO_LONG,

    /** V1：名称已被其它账户使用。 */
    DUPLICATE_NAME,

    /** V7：更新时不允许改 type。 */
    TYPE_CHANGE_NOT_ALLOWED,

    /** V2：尾号必须是 4 位数字。 */
    INVALID_LAST4,

    /** V2：现金/第三方支付账户不得填尾号。 */
    LAST4_NOT_ALLOWED,

    /** V5（D-1）：不能禁用最后一个启用账户。 */
    LAST_ENABLED_ACCOUNT,

    /** 更新目标不存在。 */
    NOT_FOUND,
}

/** 账户写操作结果。 */
sealed interface SaveAccountResult {

    /**
     * @property account 写入后的完整实体（`isDefault` 已反映本次结果）
     * @property created `true` 为新增
     */
    data class Success(val account: AccountEntity, val created: Boolean) : SaveAccountResult

    data class Rejected(val error: AccountSaveError) : SaveAccountResult
}

/**
 * @param accounts 账户读写
 * @param clock 时间源（`createdAt` / `updatedAt`）
 */
class SaveAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(input: AccountInput): SaveAccountResult {
        val name = input.name.trim()
        if (name.isEmpty()) return SaveAccountResult.Rejected(AccountSaveError.NAME_EMPTY)
        if (name.length > MAX_ACCOUNT_NAME_LENGTH) {
            return SaveAccountResult.Rejected(AccountSaveError.NAME_TOO_LONG)
        }

        val all = accounts.all()
        val existing = input.id?.let { id -> all.firstOrNull { it.id == id } }
        if (input.id != null && existing == null) {
            return SaveAccountResult.Rejected(AccountSaveError.NOT_FOUND)
        }
        // V1：全库名称唯一（更新排除自身）
        if (all.any { it.id != existing?.id && it.name == name }) {
            return SaveAccountResult.Rejected(AccountSaveError.DUPLICATE_NAME)
        }
        // V7：类型不得变更
        if (existing != null && existing.type != input.type) {
            return SaveAccountResult.Rejected(AccountSaveError.TYPE_CHANGE_NOT_ALLOWED)
        }

        val type = existing?.type ?: input.type
        val last4 = normalizeLast4(input.last4)
        when {
            // V2：现金 / 第三方支付禁止尾号
            type == AccountType.CASH || type == AccountType.THIRD_PARTY -> {
                if (last4 != null) return SaveAccountResult.Rejected(AccountSaveError.LAST4_NOT_ALLOWED)
            }
            last4 != null && (last4.length != ACCOUNT_LAST4_LENGTH || !last4.all { it.isDigit() }) -> {
                return SaveAccountResult.Rejected(AccountSaveError.INVALID_LAST4)
            }
        }

        val now = clock.millis()

        // V5（D-1）：禁用前必须至少还有另一个启用账户
        if (existing != null && existing.enabled && !input.enabled && accounts.enabledCount() <= 1) {
            return SaveAccountResult.Rejected(AccountSaveError.LAST_ENABLED_ACCOUNT)
        }

        // V6：新建账户 → 追加排序；是否默认走事务
        if (existing == null) {
            val sortOrder = input.sortOrder ?: ((all.maxOfOrNull { it.sortOrder } ?: -1) + 1)
            val entity = AccountEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                type = type,
                channel = input.channel,
                last4 = last4,
                enabled = input.enabled,
                sortOrder = sortOrder,
                // 先以 false 插入，再走 setDefault 事务，避免出现两个默认账户的中间态
                isDefault = false,
                createdAt = now,
                updatedAt = now,
            )
            accounts.insert(entity)
            if (input.isDefault) {
                accounts.setDefault(entity.id, now)
                return SaveAccountResult.Success(entity.copy(isDefault = true), created = true)
            }
            return SaveAccountResult.Success(entity, created = true)
        }

        // 更新：`isDefault` 默认保留原值（"取消默认"无意义，默认必须存在或为无）
        val updated = existing.copy(
            name = name,
            channel = input.channel,
            last4 = last4,
            enabled = input.enabled,
            sortOrder = input.sortOrder ?: existing.sortOrder,
            updatedAt = now,
        )
        accounts.update(updated)
        if (input.isDefault && !existing.isDefault) {
            accounts.setDefault(updated.id, now)
            return SaveAccountResult.Success(updated.copy(isDefault = true), created = false)
        }
        return SaveAccountResult.Success(updated, created = false)
    }

    /** 空白视为未填；其余保留原样（是否合法由 V2 判断）。 */
    private fun normalizeLast4(raw: String?): String? =
        raw?.trim()?.takeIf { it.isNotEmpty() }
}
