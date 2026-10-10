/**
 * 解析规则表 DAO（`parsing_rules`）。
 *
 * 查询契约（`docs/02` §16）：
 * - **按 `priority` 升序取 enabled 规则**（数值越小优先级越高，多规则命中时取第一条）；
 * - 启用规则之间 `priority` 必须唯一 → 提供冲突检测供上层在保存前校验。
 *
 * 边界：本 DAO **只描述匹配条件的存取**，不含任何匹配/决策逻辑（决策在 Domain，`03` §16）。
 * `include` / `exclude` 的 JSON 编解码见 `common/RuleListCodec`，不在 DAO 内做格式解析。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zhangran.app.data.local.entity.ParsingRuleEntity

@Dao
interface ParsingRuleDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(rule: ParsingRuleEntity)

    @Update
    suspend fun update(rule: ParsingRuleEntity)

    @Query("SELECT * FROM parsing_rules WHERE ruleId = :ruleId")
    suspend fun findById(ruleId: String): ParsingRuleEntity?

    /** 规则页列表（含禁用规则）。 */
    @Query("SELECT * FROM parsing_rules ORDER BY priority ASC")
    suspend fun listAll(): List<ParsingRuleEntity>

    /**
     * **查询契约**：按 `priority` 升序返回启用的规则。
     *
     * 这是 `02` §16「多规则命中时按 priority 升序取第一条」的数据来源；
     * 具体命中判定由 Domain 完成。
     */
    @Query("SELECT * FROM parsing_rules WHERE enabled = 1 ORDER BY priority ASC")
    suspend fun listEnabledByPriority(): List<ParsingRuleEntity>

    /** 统计其它启用规则中是否已占用该 priority（用于"priority 必须唯一"校验，排除自身）。 */
    @Query(
        "SELECT COUNT(*) FROM parsing_rules " +
            "WHERE enabled = 1 AND priority = :priority AND ruleId != :excludeRuleId",
    )
    suspend fun countEnabledWithPriority(priority: Int, excludeRuleId: String): Int

    @Query("SELECT COUNT(*) FROM parsing_rules")
    suspend fun count(): Int
}
