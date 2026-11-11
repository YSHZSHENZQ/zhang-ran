/**
 * 捕获日志与证据的读写（M6-2 起无障碍路径使用）。
 *
 * 采集适配层经本接口落库，不直接持有 DAO。
 */
package com.zhangran.app.data.repository

import androidx.room.withTransaction
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.dao.CaptureEvidenceDao
import com.zhangran.app.data.local.dao.CaptureLogDao
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import javax.inject.Inject
import javax.inject.Singleton

interface CaptureLogRepository {

    suspend fun insert(log: CaptureLogEntity, evidence: CaptureEvidenceEntity?)

    suspend fun findByCaptureId(captureId: String): CaptureLogEntity?

    suspend fun findByTransactionId(transactionId: String): CaptureLogEntity?

    suspend fun listAll(): List<CaptureLogEntity>

    suspend fun listEvidence(captureId: String): List<CaptureEvidenceEntity>

    suspend fun update(log: CaptureLogEntity)
}

@Singleton
class RoomCaptureLogRepository @Inject constructor(
    private val database: ZhangRanDatabase,
    private val logs: CaptureLogDao,
    private val evidence: CaptureEvidenceDao,
) : CaptureLogRepository {

    override suspend fun insert(log: CaptureLogEntity, evidence: CaptureEvidenceEntity?) {
        database.withTransaction {
            if (evidence != null) {
                this@RoomCaptureLogRepository.evidence.insert(evidence)
            }
            logs.insert(log)
        }
    }

    override suspend fun findByCaptureId(captureId: String): CaptureLogEntity? =
        logs.findByCaptureId(captureId)

    override suspend fun findByTransactionId(transactionId: String): CaptureLogEntity? =
        logs.findByTransactionId(transactionId)

    override suspend fun listAll(): List<CaptureLogEntity> = logs.listAll()

    override suspend fun listEvidence(captureId: String): List<CaptureEvidenceEntity> =
        evidence.listByCaptureId(captureId)

    override suspend fun update(log: CaptureLogEntity) {
        logs.update(log)
    }
}
