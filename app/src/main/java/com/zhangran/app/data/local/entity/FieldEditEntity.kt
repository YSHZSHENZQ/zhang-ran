/**
 * 字段级用户修改保护表实体（`field_edits`）。
 *
 * 字段逐字对齐 `docs/03` §11。保护粒度是**字段级**而非记录级：用户实际改过哪个字段，
 * 就只保护那个字段；被保护的字段**永不被自动流程覆盖**（`03` §11）。
 *
 * 主键说明：`03` §11 的清单未定义 id，字段只有 `transactionId` / `fieldName` /
 * `editedAt` / `editedValueHash`，语义上同一交易的同一字段只应有一条当前保护记录，
 * 故采用**复合主键** `(transactionId, fieldName)`（不自行新增 `03` 未定义的 id 字段）。
 *
 * `fieldName` 取值来自 `03` §11：`merchant | amountMinor | type | categoryId | accountId |
 * sourceAccountId | targetAccountId | transactionTime | note`。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "field_edits",
    primaryKeys = ["transactionId", "fieldName"],
    indices = [
        Index(value = ["transactionId"]),
    ],
)
data class FieldEditEntity(
    /** 被保护的交易 id。 */
    val transactionId: String,
    /** 被用户修改过的字段名（取值见 `03` §11）。 */
    val fieldName: String,
    /** 修改时间（UTC epoch millis）。 */
    val editedAt: Long,
    /** 可选，用于审计。 */
    val editedValueHash: String? = null,
)
