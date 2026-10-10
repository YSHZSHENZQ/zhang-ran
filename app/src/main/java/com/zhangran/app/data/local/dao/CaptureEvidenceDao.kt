/**
 * 证据表 DAO（`capture_evidence`）。
 *
 * 规则（`docs/03` §10）：
 * - 证据**不随交易删除而删除** → 因此**不提供**按交易删除/级联清理的接口；
 * - 只有"清空日志与证据"（用户显式动作，见 `CaptureLogDao.clearAll`）才清理证据；
 * - 写入前 `rawCaptureRef` 必须已脱敏（`02` §20），脱敏在采集层完成。
 */
package com.zhangran.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity

@Dao
interface CaptureEvidenceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(evidence: CaptureEvidenceEntity)

    @Query("SELECT * FROM capture_evidence WHERE id = :id")
    suspend fun findById(id: String): CaptureEvidenceEntity?

    /** 同一次捕获的全部证据。 */
    @Query("SELECT * FROM capture_evidence WHERE captureId = :captureId ORDER BY capturedAt ASC")
    suspend fun listByCaptureId(captureId: String): List<CaptureEvidenceEntity>

    /** 按时间倒序分页（日志/证据页）。 */
    @Query("SELECT * FROM capture_evidence ORDER BY capturedAt DESC LIMIT :limit OFFSET :offset")
    suspend fun listPage(limit: Int, offset: Int): List<CaptureEvidenceEntity>

    @Query("SELECT COUNT(*) FROM capture_evidence")
    suspend fun count(): Int

    /**
     * 清空证据（仅供"清空捕获日志"使用，`03` §10）。
     *
     * 注意：本方法**不得**被任何"删除交易"的路径调用——证据不随交易删除而删除。
     */
    @Query("DELETE FROM capture_evidence")
    suspend fun clearAll()
}
