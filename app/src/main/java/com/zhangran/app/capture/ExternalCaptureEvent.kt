/**
 * 上游采集事件（宿主契约入站的统一事件模型）。
 *
 * 依据：
 * - `docs/07` §3.5 宿主接入协议（版本、载荷、只读投影、不做回执）
 * - `docs/07` §8.1 历史回灌隔离
 * - `docs/03` §2 `NotificationEnvelope` 的 `listenerConnectedAt` / `isHistoricalReplay` 语义
 * - `docs/02` §20 脱敏清单
 *
 * 说明：本任务**只承载并传递**事件语义，不做候选生成与字段映射
 * （那属后续 Adapter 任务，见 `docs/10` §5 的上游字段事实）。
 */
package com.zhangran.app.capture

/**
 * 从上游收到的原始采集事件。
 *
 * @property captureId 账然侧生成的捕获 id（UUID），用于串联日志与证据
 * @property rawUri 上游传入的完整 URI（**仅内存中使用**，落库前必须经 [Sanitizer]）
 * @property version 载荷版本（缺失/非法时为 [VERSION_MISSING] / [VERSION_INVALID]）
 * @property payloadBase64 载荷原文（Base64URL，未解码）；缺失为 `null`
 * @property payloadJson 解码后的 JSON 文本；未解码或解码失败为 `null`
 * @property decodeError 解码失败原因；成功为 `null`
 * @property receivedAt 账然接收时刻（UTC epoch millis）
 * @property sourcePackage 触发方包名（上游模块的包名；`Intent` 提供，可为空）
 * @property listenerConnectedAt 监听/连接时刻（UTC epoch millis，`docs/03` §2）；未知为空
 * @property isHistoricalReplay 是否为历史回灌事件（见 [markHistoricalIfNeeded]）
 */
data class ExternalCaptureEvent(
    val captureId: String,
    val rawUri: String,
    val version: Int,
    val payloadBase64: String?,
    val payloadJson: String? = null,
    val decodeError: String? = null,
    val receivedAt: Long,
    val sourcePackage: String? = null,
    val listenerConnectedAt: Long? = null,
    val isHistoricalReplay: Boolean = false,
) {
    /**
     * 版本是否受支持（缺失/非法/其它版本一律 false → 不得按旧契约解析，`docs/04` §9.1）。
     */
    val versionSupported: Boolean get() = version == SUPPORTED_PAYLOAD_VERSION

    /** 载荷是否可用（解码成功且有内容）。 */
    val hasUsablePayload: Boolean get() = !payloadJson.isNullOrBlank() && decodeError == null

    companion object {
        /**
         * 依据连接时刻判定是否为历史回灌（`docs/07` §8.1：早于 `listenerConnectedAt` 的 `postTime`）。
         *
         * 本任务只做**判定与承载**；"不得直接入账"由后续领域任务实现。
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
 * 事件处理结果（供 `CaptureLogSink` 与测试观察）。
 *
 * @property captureId 对应事件 id
 * @property outcome 处理结论
 * @property reason 可解释原因（写入日志；版本不匹配与非法载荷都必须有）
 * @property pendingReason 若需进入 Pending，这里给出原因标识（本任务只记录，不决策状态）
 * @property sanitizedPayload 脱敏后的载荷文本（可落库/落日志）；未成功解码时为 `null`
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
 * 决策（Pending / Confirmed）只在 Domain（`docs/07` §2），本任务不决定状态。
 */
enum class CaptureOutcome {
    /** 事件已接收且载荷可用（交给后续 Adapter/Pipeline 继续处理）。 */
    ACCEPTED,

    /** 版本缺失、非法或不受支持（`docs/07` §3.5）。 */
    VERSION_MISMATCH,

    /** 载荷无法解码 / 反序列化（`docs/07` §18：不崩溃、留日志）。 */
    INVALID_PAYLOAD,
}
