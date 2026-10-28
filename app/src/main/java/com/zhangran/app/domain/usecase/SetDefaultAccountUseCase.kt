/**
 * 切换默认账户（`M2d-2` §4.3；规则 SSOT：`docs/03` §8）。
 *
 * 事务性：委托 [AccountRepository.setDefault] → `AccountDao.setAsDefault`（Room `@Transaction`），
 * 同一事务内"先清旧默认，再置新默认"，因此不会出现两个默认账户（A5/R4）。
 *
 * 前置：目标必须存在且启用（禁用账户不得设为默认）；对已是默认的账户重复调用是幂等的。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.repository.AccountRepository
import java.time.Clock
import javax.inject.Inject

/** 设置默认账户的结果。 */
sealed interface SetDefaultAccountResult {

    /** 成功（`account.isDefault = true`）。 */
    data class Success(val account: AccountEntity) : SetDefaultAccountResult

    /** 目标账户不存在。 */
    data object NotFound : SetDefaultAccountResult

    /** 目标账户已禁用（`docs/03` §8：禁用账户不得作为新交易的默认账户）。 */
    data object AccountDisabled : SetDefaultAccountResult
}

/**
 * @param accounts 账户读写
 * @param clock 时间源
 */
class SetDefaultAccountUseCase @Inject constructor(
    private val accounts: AccountRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(accountId: String): SetDefaultAccountResult {
        val target = accounts.all().firstOrNull { it.id == accountId }
            ?: return SetDefaultAccountResult.NotFound
        if (!target.enabled) return SetDefaultAccountResult.AccountDisabled
        if (target.isDefault) return SetDefaultAccountResult.Success(target)

        val now = clock.millis()
        accounts.setDefault(accountId, now)
        return SetDefaultAccountResult.Success(target.copy(isDefault = true, updatedAt = now))
    }
}
