/**
 * 分类表 DAO（`categories`）。
 *
 * 规则（`docs/03` §7）：
 * - 同一 `type` 下 `name` 唯一（库级唯一索引保证）；
 * - 已被交易引用的分类只能禁用，**不得物理删除**——因此不暴露 DELETE 接口；
 * - 默认列表只返回启用的分类；历史交易展示不受影响。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(category: CategoryEntity)

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun findById(id: String): CategoryEntity?

    /** 默认列表：只含启用分类，按 sortOrder 升序。 */
    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY sortOrder ASC")
    suspend fun listEnabled(): List<CategoryEntity>

    /** 按类型查询启用分类（收入/支出互不可用，`03` §7）。 */
    @Query("SELECT * FROM categories WHERE type = :type AND enabled = 1 ORDER BY sortOrder ASC")
    suspend fun listEnabledByType(type: CategoryType): List<CategoryEntity>

    /** 含禁用分类的全量列表（供设置页管理用）。 */
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun listAll(): List<CategoryEntity>

    /** 禁用（`enabled = false`）：分类不允许物理删除。 */
    @Query("UPDATE categories SET enabled = 0, updatedAt = :updatedAt WHERE id = :id")
    suspend fun disable(id: String, updatedAt: Long)
}
