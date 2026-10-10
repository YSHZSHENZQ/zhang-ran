/**
 * 解析规则表实体（`parsing_rules`）。
 *
 * 字段来源：`docs/02` §16（SSOT）+ `docs/03` §3.1（落库字段名与约束）。
 *
 * 关键点：
 * - `matchedKeywords` / `rejectedKeywords` **不属于本表**：它们是每次匹配的结果，落 `CaptureEvidence`（`03` §3.1）；
 * - `include` / `exclude` 为 **JSON 数组字符串（canonical）**，编解码见 `common/RuleListCodec`（`02` §16）；
 *   空集合存 `[]`，**不是** `null`（`null` 与"无效规则"无法区分）→ 两列 `NOT NULL` + 默认 `[]`；
 * - `priority`：升序；**启用规则之间必须唯一**（`02` §16）；
 * - `matchMode`：规则级属性，默认 `CONTAINS`（`02` §16）；
 * - 本表只描述**匹配条件**，不含任何决策逻辑（决策在 Domain，`03` §16）。
 *
 * 表名与主键列名：产品域称 `ruleId`（`02` §16），落库列名同为 `ruleId`（`03` §3.1）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.MatchMode
import com.zhangran.app.domain.model.TransactionType

@Entity(
    tableName = "parsing_rules",
    indices = [
        // 查询契约：按 priority 升序取 enabled 规则（02 §16）
        Index(value = ["enabled", "priority"]),
    ],
)
data class ParsingRuleEntity(
    @PrimaryKey
    @ColumnInfo(name = "ruleId")
    val ruleId: String,
    /** 升序；启用规则之间必须唯一。 */
    val priority: Int,
    val enabled: Boolean = true,
    /** 为空表示"未指定"（`02` §16）。 */
    val direction: TransactionType? = null,
    /** JSON 数组字符串（canonical），空集合为 `[]`。 */
    @ColumnInfo(defaultValue = "[]")
    val include: String = "[]",
    /** JSON 数组字符串（canonical），空集合为 `[]`。 */
    @ColumnInfo(defaultValue = "[]")
    val exclude: String = "[]",
    /** EXACT | CONTAINS | REGEX，默认 CONTAINS。 */
    @ColumnInfo(defaultValue = "CONTAINS")
    val matchMode: MatchMode = MatchMode.DEFAULT,
    /** 上游引擎/规则版本（替代上游口径字段名 `parserVersion`，见 `07` §13）。 */
    val engineVersion: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
