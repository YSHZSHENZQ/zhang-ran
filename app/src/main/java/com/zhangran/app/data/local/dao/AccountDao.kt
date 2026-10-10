/**
 * 账户表 DAO（`accounts`）。
 *
 * 默认账户唯一（`docs/03` §8，P-005 落地后的文本）：
 * - 全库至多一个 `isDefault = true`；
 * - Room 不支持部分唯一索引，故由**应用层**保证：`setAsDefault` 用 `@Transaction`
 *   先清空所有账户的默认标记，再置目标账户为默认（同一事务内完成）；
 * - **不得**用数据库唯一索引实现该约束。
 *
 * 账户不得物理删除，只能禁用（`03` §8），因此不暴露 DELETE 接口。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zhangran.app.data.local.entity.AccountEntity

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(account: AccountEntity)

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun findById(id: String): AccountEntity?

    /** 默认列表：只含启用账户，按 sortOrder 升序。 */
    @Query("SELECT * FROM accounts WHERE enabled = 1 ORDER BY sortOrder ASC")
    suspend fun listEnabled(): List<AccountEntity>

    /** 含禁用账户的全量列表（供设置页管理用）。 */
    @Query("SELECT * FROM accounts ORDER BY sortOrder ASC")
    suspend fun listAll(): List<AccountEntity>

    /** 当前默认账户（至多一个；`03` §8）。 */
    @Query("SELECT * FROM accounts WHERE isDefault = 1 LIMIT 1")
    suspend fun findDefault(): AccountEntity?

    /** 默认账户数量（用于验收 A5 与自检）。 */
    @Query("SELECT COUNT(*) FROM accounts WHERE isDefault = 1")
    suspend fun countDefaults(): Int

    /** 清空所有默认标记。 */
    @Query("UPDATE accounts SET isDefault = 0, updatedAt = :updatedAt WHERE isDefault = 1")
    suspend fun clearDefault(updatedAt: Long)

    /**
     * 设为默认账户（应用层事务保证"唯一"）。
     *
     * 依据 `docs/03` §8：「任何"设为默认"的写入必须在**同一事务**内先把其它账户的
     * `isDefault` 置为 `false`，再置目标账户为 `true`；禁止依赖数据库唯一索引」。
     */
    @Transaction
    suspend fun setAsDefault(id: String, updatedAt: Long) {
        clearDefault(updatedAt)
        markDefault(id, updatedAt)
    }

    /** 置目标账户为默认（仅由 [setAsDefault] 在事务内调用）。 */
    @Query("UPDATE accounts SET isDefault = 1, updatedAt = :updatedAt WHERE id = :id")
    suspend fun markDefault(id: String, updatedAt: Long)

    /** 禁用账户；被禁用账户仍保留 `isDefault` 取值，但不得作为新交易的默认账户。 */
    @Query("UPDATE accounts SET enabled = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun disable(id: String, updatedAt: Long)
}
