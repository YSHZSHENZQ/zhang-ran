/**
 * 账户表实体（`accounts`）。
 *
 * 字段逐字对齐 `docs/03` §8；`channel` 是账户属性（可为空），不是独立对象。
 *
 * 默认账户唯一（`docs/03` §8，P-005 落地后的文本）：
 * - 全库**至多一个**账户 `isDefault = true`（唯一范围与 `type`/`channel` 无关）；
 * - Room 不支持部分唯一索引（无法表达 `WHERE isDefault = 1`），因此该约束由**应用层**保证：
 *   任何"设为默认"的写入必须在**同一事务**内先把其它账户置 `false` 再置目标为 `true`。
 *   对应 DAO 方法见 `AccountDao.setAsDefault`（Room 的 `@Transaction`）。
 * - 被禁用（`enabled = false`）的账户仍保留 `isDefault` 取值，但不得作为新交易的默认账户。
 *
 * 因此本表**不建** `isDefault` 的唯一索引（那是错的实现方式：会禁止"先清后置"的中间态）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel

@Entity(
    tableName = "accounts",
    indices = [
        // 默认账户查询（非唯一：唯一性由应用层保证，见类注释）
        Index(value = ["isDefault"]),
        // 名称唯一性由产品规则约束（不设库级唯一），此处只建查询索引
        Index(value = ["type"]),
    ],
)
data class AccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val type: AccountType,
    /** 支付渠道；可为空。 */
    val channel: PaymentChannel? = null,
    /** 卡号后四位；可为空。 */
    val last4: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** 是否默认账户；全库至多一个为 true（应用层事务保证）。 */
    val isDefault: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)
