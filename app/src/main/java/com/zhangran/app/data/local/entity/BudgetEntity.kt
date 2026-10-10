/**
 * 预算表实体（`budgets`）。
 *
 * 字段逐字对齐 `docs/03` §9；唯一约束为「`periodKey + categoryId` 唯一」。
 * 总预算（`scope = TOTAL`）时 `categoryId` 必须为空；分类预算（`scope = CATEGORY`）时必填。
 *
 * 关于 TOTAL 的唯一性：`docs/03` §9 要求「总预算每个 `periodKey` 唯一」。
 * 由于 SQLite 唯一索引把 NULL 视为互不相等，`(periodKey, categoryId)` 唯一索引
 * **无法**表达"同一 periodKey 下至多一条 TOTAL"。这里按 `03` §8 默认账户的同一思路处理：
 * 库级建 `(periodKey, categoryId)` 唯一索引（覆盖 CATEGORY），
 * TOTAL 的唯一性由应用层在同一事务内保证（见 `BudgetDao.insertTotalBudgetIfAbsent`）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.BudgetPeriod
import com.zhangran.app.domain.model.BudgetScope

@Entity(
    tableName = "budgets",
    indices = [
        // docs/03 §9：分类预算每个 periodKey + categoryId 唯一。
        // 注意：分类预算的 categoryId 必填，因此该索引对 CATEGORY 有效；
        // TOTAL 行的 categoryId 为 NULL，SQLite 视 NULL 互不相等，故 TOTAL 唯一性
        // 由应用层事务保证（见类注释与 BudgetDao）。
        Index(value = ["periodKey", "categoryId"], unique = true),
        Index(value = ["periodKey"]),
    ],
)
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    /** TOTAL | CATEGORY。 */
    val scope: BudgetScope,
    /** scope = CATEGORY 时必填；scope = TOTAL 时必须为空。 */
    val categoryId: String? = null,
    /** v0.1 仅 MONTH。 */
    val period: BudgetPeriod = BudgetPeriod.MONTH,
    /** 形如 `"2026-10"`，标识具体月份。 */
    val periodKey: String,
    /** 预算额度（最小货币单位，分）。 */
    val limitMinor: Long,
    val enabled: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
)
