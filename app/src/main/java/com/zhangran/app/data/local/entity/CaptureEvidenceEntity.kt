/**
 * 证据表实体（`capture_evidence`）。
 *
 * 字段逐字对齐 `docs/03` §10 CaptureEvidence（10 个字段）：
 * 证据用于解释"为什么形成这笔账"。
 *
 * 规则（`03` §10）：
 * - 证据与 Transaction 通过 `evidenceId` 关联；
 * - **证据不随交易删除而删除**（只在用户"清空日志"时清理）→ 因此**不建外键**，避免级联删除；
 * - `rawCaptureRef` 落库前必须按 `02` §20 的脱敏清单处理；
 * - `confidence` 是 0–1 的置信度标量，**不属于金额**（`03` §10 明确，`Double` 在此不违反金额红线）。
 */
package com.zhangran.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zhangran.app.domain.model.CaptureSource

@Entity(
    tableName = "capture_evidence",
    indices = [
        // 按 captureId 关联同一次捕获的所有证据
        Index(value = ["captureId"]),
        Index(value = ["capturedAt"]),
    ],
)
data class CaptureEvidenceEntity(
    @PrimaryKey
    val id: String,
    /** 与 `CaptureLog.captureId` 对应。 */
    val captureId: String,
    /** 复用 `07` §5 的枚举，不得新增取值。 */
    val captureSource: CaptureSource,
    /** 上游引擎/规则版本标识（上游口径字段名为 `parserVersion`，见 `07` §13）。 */
    val engineVersion: String? = null,
    /** 命中的规则 id。 */
    val ruleId: String? = null,
    /** 0–1 的置信度标量（非金额）。 */
    val confidence: Double? = null,
    /** 命中的关键词（每次匹配的结果）。 */
    val matchedKeywords: String? = null,
    /** 被拒绝的关键词（每次匹配的结果）。 */
    val rejectedKeywords: String? = null,
    /** 原始捕获的引用或**脱敏文本**（`02` §20）。 */
    val rawCaptureRef: String? = null,
    /** 捕获时刻（UTC epoch millis）。 */
    val capturedAt: Long,
)
