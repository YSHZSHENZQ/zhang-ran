/**
 * 分类读取（`docs/03` §7、§17；`docs/04` §6）。
 *
 * 职责边界：**只做数据访问**。分类体系、“其他”自定义、父子拆分等规则不在本层。
 *
 * 关键约束（`docs/03` §7、`docs/08` §9）：**已禁用分类仍可读出**——历史交易引用禁用分类时
 * 必须能正常展示名称。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.CategoryDao
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.domain.model.CategoryType
import javax.inject.Inject
import javax.inject.Singleton

interface CategoryRepository {

    /** 全部分类（**含已禁用**），按 `sortOrder` 升序。 */
    suspend fun all(): List<CategoryEntity>

    /** 某类型下**启用**的分类（选择列表用）。 */
    suspend fun enabledByType(type: CategoryType): List<CategoryEntity>

    /** 按类型与名字精确查找（含已禁用；`docs/03` §7「同一 type 下 name 唯一」保证无歧义）。 */
    suspend fun findByName(type: CategoryType, name: String): CategoryEntity?
}

@Singleton
class RoomCategoryRepository @Inject constructor(
    private val dao: CategoryDao,
) : CategoryRepository {

    override suspend fun all(): List<CategoryEntity> = dao.listAll()

    override suspend fun enabledByType(type: CategoryType): List<CategoryEntity> =
        dao.listEnabledByType(type)

    override suspend fun findByName(type: CategoryType, name: String): CategoryEntity? =
        dao.listAll().firstOrNull { it.type == type && it.name == name }
}
