/**
 * 采集核心产出的事件化包装与处理结果。
 *
 * 依据：
 * - `docs/07` §3.5 进程内接口（采集核心 → capture/ → Adapter）
 * - `docs/07` §8.1 历史回灌隔离
 * - `docs/03` §2 `NotificationEnvelope` 的 `listenerConnectedAt` / `isHistoricalReplay` 语义
 * - `docs/02` §20 脱敏清单
 *
 * 本文件**只承载并传递**产出语义，不做候选生成与字段映射
 * （那属 M1b/U2 的 Adapter；上游字段硬事实见 `docs/10` §5）。
 */
package com.zhangran.app.capture

/**
 * 一次采集产出对应的事件（账然侧视角）。
 *
 * @property captureId 账然侧生成的捕获 id（UUID），用于串联 `capture_logs` 与 `capture_evidence`
 * @property output 采集核心的原始产出快照
 */
data class CollectorEvent(
    val captureId: String,
    val output: CollectorOutput,
) {
    /** 接口版本（写入 `capture_logs.payloadVersion` 列，列名按裁决未改）。 */
    val interfaceVersion: Int get() = output.interfaceVersion

    /** 接口版本是否受支持；不一致时不得解析/入账（`docs/07` §3.5）。 */
    val interfaceVersionSupported: Boolean get() = interfaceVersion == COLLECTOR_INTERFACE_VERSION

    /** 采集时刻（UTC epoch millis）。 */
    val receivedAt: Long get() = output.capturedAt

    /** 来源包名。 */
    val sourcePackage: String? get() = output.sourcePackage

    /** 来源类型（必填）。 */
    val sourceType: CollectorSourceType get() = output.sourceType

    /** 映射到 `docs/07` §5 的采集来源枚举。 */
    val source get() = output.sourceType.toCaptureSource()

    /** 是否历史回灌事件（`docs/07` §8.1）。 */
    val isHistoricalReplay: Boolean get() = output.isHistoricalReplay

    companion object {
        /**
         * 依据连接时刻判定是否为历史回灌（`docs/07` §8.1：早于 `listenerConnectedAt` 的 `postTime`）。
         *
         * 本层只做**判定**；"不得直接入账"由 Domain 的 Decision 实现（`03` §16）。
         *
         * @param eventTime 事件发生时刻（上游 `postTime`，可为空）
         * @param listenerConnectedAt 连接时刻（可为空 → 无法判定，视为非回灌）
         */
        fun markHistoricalIfNeeded(
            eventTime: Long?,
            listenerConnectedAt: Long?,
        ): Boolean {
            if (eventTime == null || listenerConnectedAt == null) return false
            return eventTime < listenerConnectedAt
        }
    }
}

/**
 * 兼容别名：T2b 的 `CaptureSubmitter`（应用级作用域，不在 U0 白名单）以该名字声明参数。
 *
 * 与 [CollectorEvent] 是**同一类型**；`CaptureSubmitter` 下次改动时可去掉该别名。
 */
typealias ExternalCaptureEvent = CollectorEvent

/**
 * 事件处理结果（供 `CaptureLogSink` 与测试观察）。
 *
 * @property captureId 对应事件 id
 * @property outcome 处理结论
 * @property reason 可解释原因（版本不匹配与非法产出都必须有）
 * @property pendingReason 建议的 Pending 原因标识（本层不决策状态，`03` §16）
 * @property sanitizedPayload 脱敏后的产出文本（可落库/落日志）；版本不匹配时为 `null`
 */
data class CaptureResult(
    val captureId: String,
    val outcome: CaptureOutcome,
    val reason: String? = null,
    val pendingReason: String? = null,
    val sanitizedPayload: String? = null,
)

/**
 * 处理结论。
 *
 * 注意：这些是**采集侧的可能性**，不是领域状态机（`docs/03` §6 的 `TransactionState`）。
 * 决策（Pending / Confirmed）只在 Domain（`docs/07` §2），本层不决定状态。
 */
enum class CaptureOutcome {
    /** 产出已接收且可用（交给后续 Adapter/Pipeline 继续处理）。 */
    ACCEPTED,

    /** 接口版本缺失、非法或不受支持（`docs/07` §3.5）。 */
    VERSION_MISMATCH,

    /** 产出无法解析（`docs/07` §18：不崩溃、留日志）。 */
    INVALID_PAYLOAD,
}
