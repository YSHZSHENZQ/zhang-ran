/**
 * 采集接入层编排：接口版本校验 → 脱敏 → 写日志/证据（经接口）→ 返回可解释结果。
 *
 * 依据：
 * - `docs/07` §3.5（进程内接口；接口版本不匹配 → 写 `CaptureLog` + 建议 Pending）
 * - `docs/07` §18（故障隔离：任何畸形产出不崩溃、留日志）
 * - `docs/04` §9.1（接入层不做业务判断）
 * - `docs/03` §16 规则 1（`ClearlyInvalid` 分支存在 → 本层只记录建议，不做决策）
 *
 * 本层**不**：生成候选、做字段映射、决定 Pending/Confirmed、写 Transaction。
 *
 * U0 变更（相对 T4/T2b）：
 * - 实现 [CollectorEventSource]（进程内接口），删除 URI 解析与 Base64URL 解码；
 * - `interfaceVersion` 沿用既有 `payloadVersion` 列的写入语义（列名按裁决未改）。
 */
package com.zhangran.app.capture

import com.zhangran.app.log.CaptureLogRecord
import com.zhangran.app.log.CaptureLogSink
import java.util.UUID

/**
 * @param logSink 日志/证据写入口（Room 实现见 `log/RoomCaptureLogSink`）
 * @param clock 时间源（保留以便测试注入；当前产出已自带 `capturedAt`，不再用它覆盖）
 * @param idGenerator 捕获 id 生成器（便于测试注入固定值）
 */
class CaptureProcessor(
    private val logSink: CaptureLogSink,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGenerator: () -> String = { UUID.randomUUID().toString() },
) : CollectorEventSource {

    /**
     * 由采集核心产出构造事件（账然侧生成 `captureId`）。
     *
     * 只在账然侧补 id，**不修改**产出内容（上游对象可能被就地修改，必须只读投影）。
     */
    override fun newOutput(output: CollectorOutput): CollectorEvent =
        CollectorEvent(captureId = idGenerator(), output = output)

    /**
     * 提交一条产出。
     *
     * 处理顺序（不可调换）：
     * 1. 接口版本校验：不一致 → `VERSION_MISMATCH` 并返回（**不解析产出**）
     * 2. 产出为空 → `INVALID_PAYLOAD`
     * 3. 脱敏：只处理"将要落日志/证据"的文本
     * 4. 写日志：任何分支都必须留痕
     */
    override suspend fun submit(event: CollectorEvent): CaptureResult {
        val output = event.output

        // 1) 接口版本优先：不一致时绝不当成可用产出（docs/07 §3.5）
        if (!event.interfaceVersionSupported) {
            val reason = when (output.interfaceVersion) {
                VERSION_MISSING -> "接口版本缺失（产出未携带 interfaceVersion）"
                VERSION_INVALID -> "接口版本非法"
                else -> "接口版本不受支持：${output.interfaceVersion}，当前支持 $COLLECTOR_INTERFACE_VERSION"
            }
            return finish(
                event = event,
                outcome = CaptureOutcome.VERSION_MISMATCH,
                reason = reason,
                pendingReason = PENDING_REASON_VERSION_MISMATCH,
                sanitizedPayload = null,
            )
        }

        // 2) 空产出视为非法（不崩溃，留日志）
        if (output.rawPayloadJson.isBlank()) {
            return finish(
                event = event,
                outcome = CaptureOutcome.INVALID_PAYLOAD,
                reason = "采集产出为空",
                pendingReason = PENDING_REASON_UNSUPPORTED,
                sanitizedPayload = null,
            )
        }

        // 3) 脱敏后落日志/证据；原始文本只留在内存事件里（供后续 Adapter 使用）
        val sanitized = sanitize(output.rawPayloadJson)
        return finish(
            event = event,
            outcome = CaptureOutcome.ACCEPTED,
            reason = if (output.isHistoricalReplay) "历史回灌事件：不得直接入账" else null,
            pendingReason = if (output.isHistoricalReplay) PENDING_REASON_LOW_CONFIDENCE else null,
            sanitizedPayload = sanitized,
        )
    }

    /** 统一收口：写日志 + 组装结果（保证每条路径都留痕）。 */
    private fun finish(
        event: CollectorEvent,
        outcome: CaptureOutcome,
        reason: String?,
        pendingReason: String?,
        sanitizedPayload: String?,
    ): CaptureResult {
        val output = event.output
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
                    receivedAt = output.capturedAt,
                    outcome = outcome,
                    reason = reason,
                    pendingReason = pendingReason,
                    sourcePackage = output.sourcePackage,
                    // 接口版本写入既有 payloadVersion 列（U0 §4.3：列名未改、无 migration）
                    version = output.interfaceVersion,
                    isHistoricalReplay = output.isHistoricalReplay,
                    sanitizedPayloadRef = sanitizedPayload,
                ),
            )
        }

        return result
    }

    companion object {
        /** 版本不匹配对应的建议原因（`docs/03` §16 规则 10 的 `UNSUPPORTED_NOTIFICATION`）。 */
        const val PENDING_REASON_VERSION_MISMATCH = "UNSUPPORTED_NOTIFICATION"

        /** 非法产出 / 无法解析对应的建议原因。 */
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
