/**
 * 捕获日志表实体（`capture_logs`）。
 *
 * 字段逐字对齐 `docs/03` §19 CaptureLog（15 个字段）。
 *
 * 关键点（`03` §19 规则）：
 * - 每条捕获事件**无论结果如何都必须留痕**，不得因失败而丢弃（`07` §18）；
 * - `reason` 承载 `02` §4 的 Pending reason 集合；
 * - 清空日志只清理日志与证据，**不影响** Transaction 与字段保护标记（`03` §10）；
 * - `rawCaptureRef` 必须按 `02` §20 脱敏后方可落库/展示；
 * - 置信度**不冗余存放在本表**：需要展示时经 `evidenceId` 读 `CaptureEvidence`。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource

@Entity(
    tableName = "capture_logs",
    indices = [
        // 日志页按时间倒序 + 保留策略（02 §20：1000 条或 90 天）需要时间索引
        Index(value = ["capturedAt"]),
        // 日志页筛选（02 §20：状态筛选、来源筛选）
        Index(value = ["status"]),
        Index(value = ["captureSource"]),
        // 与证据一一对应
        Index(value = ["captureId"], unique = true),
    ],
)
data class CaptureLogEntity(
    @PrimaryKey
    val id: String,
    /** 与 `CaptureEvidence.captureId` 对应。 */
    val captureId: String,
    /** 捕获时刻（UTC epoch millis）。 */
    val capturedAt: Long,
    /** 复用 `07` §5 的枚举，不得新增取值。 */
    val captureSource: CaptureSource,
    /** 来源应用包名。 */
    val packageName: String? = null,
    /** 上游 channel 原文。 */
    val channel: String? = null,
    /** 命中的规则。 */
    val parserRuleId: String? = null,
    /** 上游引擎 / 规则版本。 */
    val engineVersion: String? = null,
    /** 宿主接入契约版本 v（`07` §3.5）。 */
    val payloadVersion: Int,
    /** 见 `03` §19 的 `CaptureLogStatus`（11 个取值，不得扩展）。 */
    val status: CaptureLogStatus,
    /** 失败或需要确认的原因（可含 Pending reason 集合）。 */
    val reason: String? = null,
    /** 历史回灌隔离（`03` §2、`07` §8.1）。 */
    val isHistoricalReplay: Boolean = false,
    /** **脱敏后**的原始捕获引用或文本（`02` §20）。 */
    val rawCaptureRef: String? = null,
    /** 关联 `CaptureEvidence`。 */
    val evidenceId: String? = null,
    /** 已生成交易时回填，可空。 */
    val transactionId: String? = null,
)
