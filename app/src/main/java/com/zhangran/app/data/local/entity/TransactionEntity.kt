/**
 * 交易表实体（`transactions`）。
 *
 * 字段逐字对齐 `docs/03` §5 的 Transaction 清单（25 个字段，不增不减）。
 * 金额为最小货币单位 `Long amountMinor`，方向由 `type` 表达（`docs/03` §4）；
 * **禁止 `Double`/`Float`**（`AGENTS.md` §5）。
 *
 * 关键约束：
 * - `id` 为 UUID 字符串主键；上游 `externalId`/渠道单号**不得作主键**（`03` §5）。
 * - 软删除：`deletedAt != null` 视为已删除，只能软删除（`03` §6）。
 * - 本文件不建外键约束：分类/账户被禁用后历史交易仍必须可读（`08` §9），
 *   建 FK 反而会引入阻断读取的风险（`T2a` §4.5）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        // 去重依赖（docs/03 §5：externalTransactionId 非唯一索引）
        Index(value = ["externalTransactionId"]),
        // Pending/确认列表查询
        Index(value = ["state"]),
        // 月度统计与列表排序
        Index(value = ["transactionTime"]),
        // 关联查询
        Index(value = ["categoryId"]),
        Index(value = ["accountId"]),
    ],
)
data class TransactionEntity(
    @PrimaryKey
    val id: String,
    val type: TransactionType,
    /** 最小货币单位（分）；恒非负，方向由 [type] 表达。 */
    val amountMinor: Long,
    val currency: Currency,
    val merchant: String? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    /** 仅 TRANSFER。 */
    val sourceAccountId: String? = null,
    /** 仅 TRANSFER。 */
    val targetAccountId: String? = null,
    /** 仅 REFUND：原交易 id。 */
    val relatedTransactionId: String? = null,
    /** UTC epoch millis。 */
    val transactionTime: Long,
    /** 交易发生时的时区 id（如 `Asia/Shanghai`）。 */
    val transactionTimeZone: String,
    val note: String? = null,
    val state: TransactionState,
    val source: CaptureSource,
    /** 渠道单号（上游 transactionId），可空。 */
    val externalTransactionId: String? = null,
    /** 上游引擎/规则版本。 */
    val engineVersion: String? = null,
    val matchedRuleId: String? = null,
    val evidenceId: String? = null,
    /** 用户是否确认过（未修改也置 true）。 */
    val confirmedByUser: Boolean = false,
    /** 仅 REFUND：原交易已删除。 */
    val orphan: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** 软删除时间；非空即视为已删除。 */
    val deletedAt: Long? = null,
)
