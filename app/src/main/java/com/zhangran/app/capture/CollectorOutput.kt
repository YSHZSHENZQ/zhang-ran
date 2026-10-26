/**
 * 采集核心产出模型（账然内部类型）。
 *
 * 依据 `docs/07` §3.5（采集层 → 领域层 进程内接口）与任务书 U0 §4.1。
 *
 * 纪律：
 * - **不得暴露上游类型**：本文件不得出现 `net.ankio.auto.*` / `BillInfoModel` 等上游符号
 *   （`docs/03` §15、`docs/04` §20）；上游对象的字段事实见 `docs/10` §5，映射在 Adapter（U2/M1b）。
 * - **必须带来源类型**：`sourceType` 不得省略；映射到 `docs/07` §5 的 `CaptureSource` 见
 *   [toCaptureSource]。
 * - **允许同一交易多条产出**：支付结果页存在"处理中 → 成功"的二次渲染，采集核心**不得**
 *   自行合并或丢弃后续采样；逐条落 `capture_logs`，由 Dedup 收敛（`docs/03` §12）。
 * - 本类型是**不可变快照**：上游对象可能被就地修改（手续费改写 `money`），落库前必须已拷贝。
 */
package com.zhangran.app.capture

import com.zhangran.app.domain.model.CaptureSource

/**
 * 采集来源类型（与上游 `DataType` 对齐；`NODE` 为节点快照通道，见 U2）。
 */
enum class CollectorSourceType {
    /** 通知监听（含 Hook 通知）。 */
    NOTICE,

    /** 应用内数据 / 短信。 */
    DATA,

    /** 截图 OCR。 */
    OCR,

    /** 无障碍节点快照（U2 视 P0 结果决定是否启用；本任务只建枚举，不实现采集）。 */
    NODE,
}

/**
 * 采集核心的一次产出（账然内部快照）。
 *
 * @property collectorId 采集核心侧的稳定标识（可为上游 id）；**不用于**判重（判重见 `03` §12）
 * @property interfaceVersion 采集核心与领域层的接口版本；写入 `capture_logs.payloadVersion` 列
 * @property sourceType 来源类型（必填）
 * @property engineVersion 上游引擎/规则版本
 * @property sourcePackage 来源包名
 * @property sourceChannel 上游 `channel` 原文
 * @property rawPayloadJson 原始结果（落库前经脱敏）
 * @property capturedAt 采集时刻（UTC epoch millis）
 * @property isHistoricalReplay 是否历史回灌（`03` §2、`07` §8.1）
 */
data class CollectorOutput(
    val collectorId: String,
    val interfaceVersion: Int,
    val sourceType: CollectorSourceType,
    val engineVersion: String? = null,
    val sourcePackage: String? = null,
    val sourceChannel: String? = null,
    val rawPayloadJson: String,
    val capturedAt: Long,
    val isHistoricalReplay: Boolean = false,
)

/**
 * `sourceType` → `docs/07` §5 的 `CaptureSource`（映射表为 SSOT，不得自行新增取值）。
 *
 * 映射依据（`07` §5）：
 * - `NOTICE`（通知监听）→ `NOTIFICATION`
 * - `DATA`（应用内数据）→ `HOOK`；短信通道在 `07` §5 映射为 `SMS`，
 *   但 `CollectorOutput` 只有一个 `DATA` 值，**短信与 Hook 的区分需结合 `sourceChannel`**，
 *   该细分留给 Adapter（U2）在拿到真实 `channel` 取值后收口（`docs/10` §8 第 3 项）。
 * - `OCR`（截图识别）→ `OCR`
 * - `NODE`（无障碍节点快照）→ `ACCESSIBILITY`
 */
fun CollectorSourceType.toCaptureSource(): CaptureSource = when (this) {
    CollectorSourceType.NOTICE -> CaptureSource.NOTIFICATION
    CollectorSourceType.DATA -> CaptureSource.HOOK
    CollectorSourceType.OCR -> CaptureSource.OCR
    CollectorSourceType.NODE -> CaptureSource.ACCESSIBILITY
}
