/**
 * 捕获日志表 DAO（`capture_logs`）。
 *
 * 规则（`docs/03` §19/§10、`02` §20）：
 * - 每条捕获事件必须留痕；
 * - 日志页需要状态筛选、来源筛选、按时间分页；
 * - 保留策略：最多 1000 条或最近 90 天，以先到者为准，超出后按时间从旧到新清理；
 * - **清空日志只清理日志与证据**，不影响 Transaction 与 `field_edits`。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource

@Dao
interface CaptureLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CaptureLogEntity)

    @Update
    suspend fun update(log: CaptureLogEntity)

    @Query("SELECT * FROM capture_logs WHERE id = :id")
    suspend fun findById(id: String): CaptureLogEntity?

    @Query("SELECT * FROM capture_logs WHERE captureId = :captureId")
    suspend fun findByCaptureId(captureId: String): CaptureLogEntity?

    /** 待确认行回查原因（一笔交易至多取最新一条日志）。 */
    @Query(
        "SELECT * FROM capture_logs WHERE transactionId = :transactionId " +
            "ORDER BY capturedAt DESC LIMIT 1",
    )
    suspend fun findByTransactionId(transactionId: String): CaptureLogEntity?

    @Query("SELECT * FROM capture_logs ORDER BY capturedAt DESC")
    suspend fun listAll(): List<CaptureLogEntity>

    /** 日志页：按时间倒序分页（`02` §20）。 */
    @Query("SELECT * FROM capture_logs ORDER BY capturedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun listPage(limit: Int, offset: Int): List<CaptureLogEntity>

    /** 状态筛选（`02` §20）。 */
    @Query(
        "SELECT * FROM capture_logs WHERE status = :status " +
            "ORDER BY capturedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun listPageByStatus(
        status: CaptureLogStatus,
        limit: Int,
        offset: Int,
    ): List<CaptureLogEntity>

    /** 来源筛选（`02` §20）。 */
    @Query(
        "SELECT * FROM capture_logs WHERE captureSource = :source " +
            "ORDER BY capturedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun listPageBySource(
        source: CaptureSource,
        limit: Int,
        offset: Int,
    ): List<CaptureLogEntity>

    /** 关键词搜索（`02` §20：对脱敏后的原始捕获文本搜索）。 */
    @Query(
        "SELECT * FROM capture_logs WHERE rawCaptureRef LIKE '%' || :keyword || '%' " +
            "ORDER BY capturedAt DESC LIMIT :limit OFFSET :offset",
    )
    suspend fun search(keyword: String, limit: Int, offset: Int): List<CaptureLogEntity>

    @Query("SELECT COUNT(*) FROM capture_logs")
    suspend fun count(): Int

    /** 按状态计数（供"清空后不影响 Transaction"类断言与自检使用）。 */
    @Query("SELECT COUNT(*) FROM capture_logs WHERE status = :status")
    suspend fun countByStatus(status: CaptureLogStatus): Int

    /**
     * 保留策略：删除 `before` 之前（更旧）的日志。
     *
     * 依据 `02` §20「超出后按时间从旧到新清理」；1000 条上限由调用方结合 `count()` 处理。
     */
    @Query("DELETE FROM capture_logs WHERE capturedAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    /**
     * 清空日志（`03` §19/§10）。
     *
     * **只**清 `capture_logs`；证据由 `CaptureEvidenceDao.clearAll()` 单独清理（两者同属"清空日志"语义）。
     * 本方法**不得**触碰 `transactions` 或 `field_edits`。
     */
    @Query("DELETE FROM capture_logs")
    suspend fun clearAll()
}
