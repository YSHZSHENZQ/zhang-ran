/**
 * 导入后不变量修复（`M4b-2` §4.6，I1–I6）。
 *
 * **必须在恢复事务内调用**（本类不自己开事务）；只做最小修复并把每项修复写入报告。
 * 不变量依据：`docs/02` §9、`docs/03` §7/§8/§9/§11。
 */
package com.zhangran.app.data.backup

import androidx.room.withTransaction
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType
import java.util.UUID
import javax.inject.Inject

/** 兜底分类名（`02` §9）。 */
const val FALLBACK_CATEGORY_NAME: String = "其他"

class RestoreInvariantFixer @Inject constructor(
    private val database: ZhangRanDatabase,
) {

    /**
     * 运行 I1–I6 修复。
     *
     * @param now 新补建记录的时间戳（UTC epoch millis）
     * @return 人类可读的修复记录（空表示无需修复）
     */
    suspend fun fix(now: Long): List<String> {
        val fixes = mutableListOf<String>()
        fixFallbackCategories(now, fixes)   // I1
        fixDuplicateCategoryNames(fixes)     // I2
        fixDefaultAccounts(fixes)            // I3（I4 天然满足：不回算 systemDefined）
        fixDuplicateBudgets(fixes)           // I5
        reportDanglingReferences(fixes)      // I6
        return fixes
    }

    /** I1：每个分类类型至少一个可用的「其他」。 */
    private suspend fun fixFallbackCategories(now: Long, fixes: MutableList<String>) {
        val dao = database.categoryDao()
        val all = dao.listAll()
        CategoryType.entries.forEach { type ->
            val existing = all.filter { it.type == type && it.name == FALLBACK_CATEGORY_NAME }
            val usable = existing.firstOrNull { it.enabled }
            if (usable != null) return@forEach

            val disabled = existing.firstOrNull()
            if (disabled != null) {
                dao.update(disabled.copy(enabled = true, updatedAt = now))
                fixes += "I1：重新启用「其他」分类（${type.displayName()}）"
            } else {
                val maxSortOrder = all.filter { it.type == type }.maxOfOrNull { it.sortOrder } ?: -1
                dao.insert(
                    CategoryEntity(
                        id = UUID.randomUUID().toString(),
                        name = FALLBACK_CATEGORY_NAME,
                        type = type,
                        icon = FALLBACK_CATEGORY_ICON,
                        sortOrder = maxSortOrder + 1,
                        enabled = true,
                        systemDefined = true,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                fixes += "I1：补建「其他」分类（${type.displayName()}）"
            }
        }
    }

    /** I2：同 type 下分类名称唯一；冲突者改名加后缀。 */
    private suspend fun fixDuplicateCategoryNames(fixes: MutableList<String>) {
        val dao = database.categoryDao()
        val categories = dao.listAll()
        val usedNames = categories.map { it.type to it.name }.toMutableSet()
        categories
            .groupBy { it.type to it.name }
            .filterValues { it.size > 1 }
            .forEach { (key, duplicates) ->
                val (type, name) = key
                // 保留 sortOrder 最小（同序时 id 较小）的一条，其余改名
                val ordered = duplicates.sortedWith(compareBy({ it.sortOrder }, { it.id }))
                ordered.drop(1).forEach { duplicate ->
                    var suffix = 2
                    var candidate = "$name ($suffix)"
                    while ((type to candidate) in usedNames) {
                        suffix++
                        candidate = "$name ($suffix)"
                    }
                    usedNames += type to candidate
                    dao.update(duplicate.copy(name = candidate))
                    fixes += "I2：分类重名「$name」（${type.displayName()}）→「$candidate」"
                }
            }
    }

    /** I3：`isDefault` 不多于 1 个；等于 0 且存在启用账户时只提示，不自动指定。 */
    private suspend fun fixDefaultAccounts(fixes: MutableList<String>) {
        val dao = database.accountDao()
        val accounts = dao.listAll()
        val defaults = accounts.filter { it.isDefault }
        if (defaults.size > 1) {
            val keeper = defaults.maxWithOrNull(compareBy<AccountEntity>({ it.updatedAt }, { it.id }))
            defaults.filter { it.id != keeper?.id }.forEach { account ->
                dao.update(account.copy(isDefault = false))
            }
            fixes += "I3：默认账户多于 1 个，保留 ${keeper?.name}，其余清除默认标记"
        } else if (defaults.isEmpty() && accounts.any { it.enabled }) {
            fixes += "I3：备份中没有默认账户，需由用户指定（未自动指定）"
        }
    }

    /** I5：`periodKey + categoryId` 唯一；重复时保留 `updatedAt` 最新者。 */
    private suspend fun fixDuplicateBudgets(fixes: MutableList<String>) {
        val budgets = database.budgetDao().listAll()
        budgets
            .groupBy { it.periodKey to it.categoryId }
            .filterValues { it.size > 1 }
            .forEach { (key, duplicates) ->
                val keeper = duplicates.maxWithOrNull(compareBy({ it.updatedAt }, { it.id }))
                duplicates.filter { it.id != keeper?.id }.forEach { duplicate ->
                    database.openHelper.writableDatabase.execSQL(
                        "DELETE FROM budgets WHERE id = ?",
                        arrayOf(duplicate.id),
                    )
                }
                fixes += "I5：预算重复（${key.first}），保留最新一条，删除 ${duplicates.size - 1} 条旧记录"
            }
    }

    /** I6：悬空外键只统计、不改写（历史交易容错）。 */
    private suspend fun reportDanglingReferences(fixes: MutableList<String>) {
        val categoryIds = database.categoryDao().listAll().map { it.id }.toSet()
        val accountIds = database.accountDao().listAll().map { it.id }.toSet()
        val transactions = database.transactionDao().listAllIncludingDeleted()

        val danglingCategory = transactions.count { it.categoryId != null && it.categoryId !in categoryIds }
        val danglingAccount = transactions.count { it.accountId != null && it.accountId !in accountIds }
        val danglingTransfer = transactions.count {
            (it.sourceAccountId != null && it.sourceAccountId !in accountIds) ||
                (it.targetAccountId != null && it.targetAccountId !in accountIds)
        }
        if (danglingCategory > 0) fixes += "I6：$danglingCategory 条交易的分类已不存在（保留原值）"
        if (danglingAccount > 0) fixes += "I6：$danglingAccount 条交易的支付账户已不存在（保留原值）"
        if (danglingTransfer > 0) fixes += "I6：$danglingTransfer 条转账的账户已不存在（保留原值）"
    }

    private fun CategoryType.displayName(): String = when (this) {
        CategoryType.EXPENSE -> "支出"
        CategoryType.INCOME -> "收入"
    }

    private companion object {
        const val FALLBACK_CATEGORY_ICON: String = "other"
    }
}

// 保留 withTransaction 的显式引用，提醒调用方本类必须在事务内使用（不会被单独调用）。
@Suppress("unused")
private suspend fun ZhangRanDatabase.runFixerInTransaction(block: suspend () -> Unit) {
    withTransaction { block() }
}
