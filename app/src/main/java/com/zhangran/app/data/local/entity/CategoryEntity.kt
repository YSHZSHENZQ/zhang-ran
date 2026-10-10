/**
 * 分类表实体（`categories`）。
 *
 * 字段逐字对齐 `docs/03` §7；唯一约束为「同一 `type` 下 `name` 唯一」。
 * 只能禁用（`enabled = false`），不得物理删除（`03` §7）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.CategoryType

@Entity(
    tableName = "categories",
    indices = [
        // docs/03 §7：同一 type 下 name 唯一
        Index(value = ["type", "name"], unique = true),
    ],
)
data class CategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    /** 升序展示。 */
    val sortOrder: Int,
    val enabled: Boolean = true,
    /** 是否为系统预置分类。 */
    val systemDefined: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
