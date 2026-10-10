/**
 * 捕获日志的 Room 实现（替换 T4 的临时 logcat 实现）。
 *
 * 依据 `docs/07` §3.2 第 2 项「事件接收与保真：接收上游事件，原样落 `capture_logs` / `evidence`」。
 *
 * 职责与红线：
 * - **只写日志与证据**，绝不写 `transactions`、不生成候选、不决定状态（`07` §2、`04` §20）；
 * - `rawCaptureRef` 使用 `CaptureProcessor` 已脱敏的文本（`02` §20）；
 * - 写库失败**不得**让接收入口崩溃（`07` §18）→ [write] 内部兜底，不向外抛。
 *
 * 落库映射（`03` §19 的字段 ← 本实现的数据来源）：
 *
 * | `capture_logs` 列 | 来源 |
 * |---|---|
 * | `id` | 由 `captureId` 派生（`<captureId>-log`），与证据一一对应 |
 * | `captureId` / `capturedAt` / `reason` / `isHistoricalReplay` / `rawCaptureRef` | `CaptureLogRecord` 同名语义字段 |
 * | `packageName` | `CaptureLogRecord.sourcePackage` |
 * | `payloadVersion` | `CaptureLogRecord.version`（缺失/非法时为契约默认版本） |
 * | `status` | 由 `CaptureOutcome` 映射（见 [mapStatus]） |
 * | `engineVersion` | 由 `payloadVersion` 记为 `"v<n>"`；上游引擎版本在 T4 契约中不携带 |
 * | `captureSource` / `channel` / `parserRuleId` / `evidenceId` / `transactionId` | 见下方说明 |
 *
 * 未落实处的说明（据实标注，未自行新增契约字段）：
 * - `captureSource`：T4 的 `CaptureLogRecord` 与宿主契约都不携带来源，而列 `NOT NULL`。
 *   当前走 `zhangran://capture`（通知驱动）路径，故记为 `NOTIFICATION`；
 *   **TODO(Adapter 任务)**：等上游 `app`/`channel` 映射（`docs/10` §8 第 3 项）确认后，
 *   由 Adapter 回填真实来源；届时需要给 `CaptureLogRecord` 增加可选来源字段（属 T4 契约扩展，
 *   需走提案）。
 * - `channel` / `parserRuleId` / `transactionId`：T4 契约不携带，保持 `null`，
 *   由后续 Adapter / Decision 阶段回填（本任务不生成候选，故 `transactionId` 必为空）。
 * - 证据 `confidence`：本阶段不产出置信度（属 Confidence 环节），保持 `null`。
 * - `evidenceId`：本 sink 同时写入 `capture_evidence`，并把二者关联起来。
 */
package com.zhangran.app.log

import android.util.Log
import com.zhangran.app.capture.CaptureOutcome
import com.zhangran.app.capture.SUPPORTED_PAYLOAD_VERSION
import com.zhangran.app.capture.VERSION_MISSING
import com.zhangran.app.data.local.dao.CaptureEvidenceDao
import com.zhangran.app.data.local.dao.CaptureLogDao
import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * @param captureLogDao `capture_logs` 写入
 * @param captureEvidenceDao `capture_evidence` 写入
 */
@Singleton
class RoomCaptureLogSink @Inject constructor(
    private val captureLogDao: CaptureLogDao,
    private val captureEvidenceDao: CaptureEvidenceDao,
) : CaptureLogSink {

    /**
     * 写入一条捕获日志 + 一条对应证据。
     *
     * 线程模型：接口契约是同步 `fun write`，但实现内部用 `runBlocking(Dispatchers.IO)` 完成 Room 写入，
     * 保证**不在主线程做数据库 IO**。调用链为
     * `CaptureEntryActivity → CaptureSubmitter(应用级作用域, Dispatchers.Default) → CaptureProcessor.submit`
     * `→ RoomCaptureLogSink.write`，`Dispatchers.IO` 进一步确保 IO 只在该线程池执行。
     *
     * 失败兜底：任何异常都在此捕获并打日志，不向外抛（`07` §18）。
     */
    override fun write(record: CaptureLogRecord) {
        try {
            runBlocking(Dispatchers.IO) {
                val evidenceId = EVIDENCE_ID_SUFFIX_PREFIX + record.captureId

                captureEvidenceDao.insert(
                    CaptureEvidenceEntity(
                        id = evidenceId,
                        captureId = record.captureId,
                        captureSource = CaptureSource.NOTIFICATION,
                        engineVersion = engineVersionOf(record),
                        ruleId = null,
                        confidence = null,
                        matchedKeywords = null,
                        rejectedKeywords = null,
                        rawCaptureRef = record.sanitizedPayloadRef,
                        capturedAt = record.receivedAt,
                    ),
                )

                captureLogDao.insert(
                    CaptureLogEntity(
                        id = logIdOf(record.captureId),
                        captureId = record.captureId,
                        capturedAt = record.receivedAt,
                        captureSource = CaptureSource.NOTIFICATION,
                        packageName = record.sourcePackage,
                        channel = null,
                        parserRuleId = null,
                        engineVersion = engineVersionOf(record),
                        payloadVersion = payloadVersionOf(record),
                        status = mapStatus(record.outcome),
                        reason = record.reason,
                        isHistoricalReplay = record.isHistoricalReplay,
                        rawCaptureRef = record.sanitizedPayloadRef,
                        evidenceId = evidenceId,
                        transactionId = null,
                    ),
                )
            }
        } catch (t: Throwable) {
            // 不得让接收入口崩溃（07 §18）
            Log.w(TAG, "写捕获日志/证据失败：captureId=${record.captureId} ${t.javaClass.simpleName}")
        }
    }

    /** `CaptureOutcome` → `CaptureLogStatus`（`03` §19 取值）。 */
    private fun mapStatus(outcome: CaptureOutcome): CaptureLogStatus = when (outcome) {
        // 已接收且载荷可用，但尚未校验业务可用性 → RECEIVED（03 §19：尚未校验）
        CaptureOutcome.ACCEPTED -> CaptureLogStatus.RECEIVED
        CaptureOutcome.VERSION_MISMATCH -> CaptureLogStatus.VERSION_MISMATCH
        CaptureOutcome.INVALID_PAYLOAD -> CaptureLogStatus.FAILED
    }

    /** 载荷版本：缺失/非法时用契约当前支持的版本（列 NOT NULL）。 */
    private fun payloadVersionOf(record: CaptureLogRecord): Int =
        if (record.version >= 0) record.version else SUPPORTED_PAYLOAD_VERSION

    /** 上游引擎版本列：T4 契约不携带，用载荷版本记为 `v<n>`；版本缺失时为 `null`。 */
    private fun engineVersionOf(record: CaptureLogRecord): String? =
        if (record.version == VERSION_MISSING) null else "v${payloadVersionOf(record)}"

    private fun logIdOf(captureId: String): String = "$LOG_ID_SUFFIX_PREFIX$captureId"

    companion object {
        private const val TAG = "ZhangRanCapture"
        private const val LOG_ID_SUFFIX_PREFIX = "log-"
        private const val EVIDENCE_ID_SUFFIX_PREFIX = "evi-"
    }
}
