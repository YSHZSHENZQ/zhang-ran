/**
 * 捕获证据读取（`capture_evidence`，`docs/03` §10）。
 *
 * 用途（`M2b` §4.1）：详情页展示「置信度」与「原始捕获证据摘要」。
 * 边界：
 * - **只读**：证据由采集层写入；本层不提供写/删接口（证据不随交易删除而删除）。
 * - 证据与交易通过 `Transaction.evidenceId` 关联；缺失时返回 `null`，由调用方隐藏对应行。
 */
package com.zhangran.app.data.repository

import com.zhangran.app.data.local.dao.CaptureEvidenceDao
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import javax.inject.Inject
import javax.inject.Singleton

interface CaptureEvidenceRepository {

    /** 按证据 id 读取；不存在返回 `null`。 */
    suspend fun findById(evidenceId: String): CaptureEvidenceEntity?
}

@Singleton
class RoomCaptureEvidenceRepository @Inject constructor(
    private val dao: CaptureEvidenceDao,
) : CaptureEvidenceRepository {

    override suspend fun findById(evidenceId: String): CaptureEvidenceEntity? =
        dao.findById(evidenceId)
}
