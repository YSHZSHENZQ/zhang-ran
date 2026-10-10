/**
 * 采集编排：版本校验 → 解码 → 脱敏 → 写日志/证据（经接口）→ 返回可解释结果。
 *
 * 依据：
 * - `docs/07` §3.5（版本不匹配：写 `CaptureLog` + 一律 Pending，禁止按旧契约解析入账）
 * - `docs/04` §9.1（入口内禁止解析/落库/业务判断 → 这些都在本编排器里）
 * - `docs/07` §18（故障隔离：任何畸形输入不崩溃、留日志）
 * - `docs/03` §16 规则 1（`ClearlyInvalid` 分支存在 → 本任务只记录建议，不做决策）
 *
 * 本任务**不**：生成候选、做字段映射、决定 Pending/Confirmed、写 Room。
 */
package com.zhangran.app.capture

import com.zhangran.app.log.CaptureLogRecord
import com.zhangran.app.log.CaptureLogSink
import java.util.Base64
import java.util.UUID

/**
 * @param logSink 日志/证据写入口（T2b 会替换为 Room 实现）
 * @param clock 时间源（便于测试注入固定值；默认使用系统时间）
 * @param idGenerator 捕获 id 生成器（便于测试注入固定值）
 */
class CaptureProcessor(
    private val logSink: CaptureLogSink,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : CaptureEventSource {
    /**
     * 处理一个上游事件。
     *
     * 处理顺序（不可调换）：
     * 1. 版本校验：不受支持 → 记录 `VERSION_MISMATCH` 并返回（**不解析载荷**）
     * 2. Base64URL 解码：失败 → `INVALID_PAYLOAD`
     * 3. 脱敏：仅对"将要落日志/证据"的文本做，不影响解密后的业务字段
     * 4. 写日志：任何分支都必须留痕
     */
    override suspend fun submit(event: ExternalCaptureEvent): CaptureResult {
        // 1) 版本校验优先：不受支持时绝不按旧契约解析（docs/04 §9.1）
        if (!event.versionSupported) {
            val reason = when (event.version) {
                VERSION_MISSING -> "载荷版本缺失（v 参数未提供）"
                VERSION_INVALID -> "载荷版本非法（v 参数不是整数）"
                else -> "载荷版本不受支持：v=${event.version}，当前支持 v=$SUPPORTED_PAYLOAD_VERSION"
            }
            return finish(
                event = event,
                outcome = CaptureOutcome.VERSION_MISMATCH,
                reason = reason,
                pendingReason = PENDING_REASON_VERSION_MISMATCH,
                sanitizedPayload = null,
            )
        }

        // 2) 载荷解码（Base64URL / 无换行）
        val decoded = decodePayload(event.payloadBase64)
        if (decoded == null) {
            return finish(
                event = event,
                outcome = CaptureOutcome.INVALID_PAYLOAD,
                reason = event.payloadBase64?.let { "载荷不是合法的 Base64URL 文本" } ?: "载荷缺失",
                pendingReason = PENDING_REASON_UNSUPPORTED,
                sanitizedPayload = null,
            )
        }

        // 3) 脱敏后落日志/证据；原始文本只留在内存事件里（供后续 Adapter 使用）
        val sanitized = sanitize(decoded)
        return finish(
            event = event,
            outcome = CaptureOutcome.ACCEPTED,
            reason = if (event.isHistoricalReplay) "历史回灌事件：不得直接入账" else null,
            pendingReason = if (event.isHistoricalReplay) PENDING_REASON_LOW_CONFIDENCE else null,
            sanitizedPayload = sanitized,
        )
    }

    /**
     * Base64URL 解码（纯 JVM 实现，见 `docs/09` 关于 Android 桩类的教训）。
     *
     * 使用 `java.util.Base64.getUrlDecoder()`，与 `docs/07` §3.5 的载荷约定一致；
     * 任何异常都归一为"解码失败"（不崩溃，`docs/07` §18）。
     */
    private fun decodePayload(base64: String?): String? {
        if (base64.isNullOrBlank()) return null
        return runCatching {
            val bytes = Base64.getUrlDecoder().decode(base64)
            val text = String(bytes, Charsets.UTF_8)
            text.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /** 统一收口：写日志 + 组装结果（保证每条路径都留痕）。 */
    private fun finish(
        event: ExternalCaptureEvent,
        outcome: CaptureOutcome,
        reason: String?,
        pendingReason: String?,
        sanitizedPayload: String?,
    ): CaptureResult {
        val result = CaptureResult(
            captureId = event.captureId,
            outcome = outcome,
            reason = reason,
            pendingReason = pendingReason,
            sanitizedPayload = sanitizedPayload,
        )

        // 写日志：不得因为 sink 失败而影响主流程（docs/07 §18）。
        // 这里不使用 android.util.Log，以保证编排逻辑可在纯 JVM 单测中运行
        // （Android 桩类在 JVM 单测里会抛异常，见 docs/09）；日志由 sink 负责落痕。
        runCatching {
            logSink.write(
                CaptureLogRecord(
                    captureId = event.captureId,
                    receivedAt = event.receivedAt,
                    outcome = outcome,
                    reason = reason,
                    pendingReason = pendingReason,
                    sourcePackage = event.sourcePackage,
                    version = event.version,
                    isHistoricalReplay = event.isHistoricalReplay,
                    sanitizedPayloadRef = sanitizedPayload,
                ),
            )
        }

        return result
    }

    /**
     * 由原始入参构造事件（供 Activity 调用）。
     *
     * 说明：参数提取（`CaptureContract.parse`）与事件组装都在这里完成，
     * 因此接收入口不需要做任何解析（T4 §4.1「入口无业务」、验收 A3）。
     */
    override fun newEvent(
        rawUri: String?,
        sourcePackage: String?,
        listenerConnectedAt: Long?,
        eventTime: Long?,
    ): ExternalCaptureEvent {
        val params = CaptureContract.parse(rawUri)
        return ExternalCaptureEvent(
            captureId = idGenerator(),
            rawUri = rawUri.orEmpty(),
            version = params.version,
            payloadBase64 = params.payload,
            receivedAt = clock(),
            sourcePackage = sourcePackage,
            listenerConnectedAt = listenerConnectedAt,
            isHistoricalReplay = ExternalCaptureEvent.markHistoricalIfNeeded(
                eventTime = eventTime,
                listenerConnectedAt = listenerConnectedAt,
            ),
        )
    }

    companion object {
        /** 版本不匹配对应的 Pending 原因（`docs/03` §16 规则 10 的 `UNSUPPORTED_NOTIFICATION`）。 */
        const val PENDING_REASON_VERSION_MISMATCH = "UNSUPPORTED_NOTIFICATION"

        /** 非法载荷 / 无法解析对应的 Pending 原因。 */
        const val PENDING_REASON_UNSUPPORTED = "UNSUPPORTED_NOTIFICATION"

        /** 历史回灌对应的建议原因（`docs/07` §8.1：只能进 Pending 或日志）。 */
        const val PENDING_REASON_LOW_CONFIDENCE = "LOW_CONFIDENCE"

        /** 脱敏占位符（`docs/02` §20）。 */
        private const val MASK = "***"

        /** 手机号（中国大陆，1 开头 11 位）。 */
        private val PHONE = Regex("""(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)""")

        /** 银行卡号（16–19 位，允许空格或 - 分隔）。 */
        private val BANK_CARD =
            Regex("""(?<!\d)(\d{4})[\s-]?((?:\d{4}[\s-]?){2,3})(\d{4})(?!\d)""")

        /** 身份证号（18 位，末位可为 X）。 */
        private val ID_CARD = Regex("""(?<!\d)(\d{6})(\d{8})(\d{3})([\dXx])(?!\d)""")

        /** 支付/交易单号（≥12 位长数字串），保留首尾各 4 位。 */
        private val ORDER_NO = Regex("""(?<!\d)(\d{4})\d{4,}(\d{4})(?!\d)""")

        /** 通知里的个人姓名：`收款方：张三` / `付款方 李四` → 保留姓氏 + `**`。 */
        private val NAMED_PERSON =
            Regex("""(收款方|付款方|对方|收款人|付款人)([:：\s]*)([\u4e00-\u9fa5])[\u4e00-\u9fa5]{1,3}""")

        /**
         * 文本脱敏（`docs/02` §20 清单：完整账号/手机号/银行卡号/身份证号/支付单号中间段/个人姓名）。
         *
         * 顺序：身份证 → 银行卡 → 手机号 → 单号 → 姓名，避免长数字被短规则先切断。
         * 脱敏是单向的，且不得改变金额、时间等业务字段。
         *
         * 说明：本实现内联在采集编排器内（T4 §3 白名单只允许新增所列文件）。
         * 若后续 `log/` 需要独立复用，应在 T2b 提取为独立工具并走提案。
         */
        fun sanitize(text: String?): String? {
            if (text == null) return null
            var result = text
            result = ID_CARD.replace(result) { m ->
                "${m.groupValues[1]}$MASK${m.groupValues[3]}${m.groupValues[4]}"
            }
            result = BANK_CARD.replace(result) { m ->
                // 掩码后不带原分隔符：6222 0212 3456 7890 123 → 6222***0123
                "${m.groupValues[1]}$MASK${m.groupValues[3]}"
            }
            result = PHONE.replace(result) { m ->
                "${m.groupValues[1]}$MASK${m.groupValues[2]}"
            }
            result = ORDER_NO.replace(result) { m ->
                "${m.groupValues[1]}$MASK${m.groupValues[2]}"
            }
            result = NAMED_PERSON.replace(result) { m ->
                "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}**"
            }
            return result
        }
    }
}
