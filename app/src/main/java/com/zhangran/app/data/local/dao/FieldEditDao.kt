/**
 * 字段级用户修改保护表 DAO（`field_edits`）。
 *
 * 规则（`docs/03` §11）：
 * - 用户实际改过哪个字段就只保护那个字段；被保护字段永不被自动流程覆盖；
 * - 自动流程不得清空或改写被保护字段（只能新增 Pending 建议，属 Domain）；
 * - 因此本 DAO **不暴露**按单字段删除的接口；清理由用户显式操作或备份恢复流程触发。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhangran.app.data.local.entity.FieldEditEntity

@Dao
interface FieldEditDao {

    /** 新增保护记录（同交易同字段冲突则替换为最新修改）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(fieldEdit: FieldEditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(fieldEdits: List<FieldEditEntity>)

    /** 某交易的全部被保护字段。 */
    @Query("SELECT * FROM field_edits WHERE transactionId = :transactionId ORDER BY fieldName ASC")
    suspend fun listByTransaction(transactionId: String): List<FieldEditEntity>

    /** 查询某交易某字段是否被保护。 */
    @Query("SELECT * FROM field_edits WHERE transactionId = :transactionId AND fieldName = :fieldName")
    suspend fun find(transactionId: String, fieldName: String): FieldEditEntity?

    /** 全部保护记录（供备份 / 合并判断使用）。 */
    @Query("SELECT * FROM field_edits ORDER BY transactionId ASC, fieldName ASC")
    suspend fun listAll(): List<FieldEditEntity>

    /** 调试取证前清空保护记录。正式页面不调用。 */
    @Query("DELETE FROM field_edits")
    suspend fun deleteAll()
}
