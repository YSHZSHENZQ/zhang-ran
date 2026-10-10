/**
 * 捕获日志与证据的写入接口（`docs/03` §10、`docs/02` §20、`docs/04` §3 `log/`）。
 *
 * 本任务只定义接口 + logcat 实现（**不落库**）：
 * T4 与 T2a 解耦（T4 任务书 §4.5 的明确取舍），Room 落库在 T2b 打开。
 *
 * 约束：
 * - 写入方只写"已脱敏"的文本（`docs/02` §20）；
 * - 本接口不提供物理删除（`docs/03` §10：清空日志只清日志与证据，不影响 Transaction）；
 * - 实现必须**不抛异常**（`docs/07` §18：上游畸形输入不得让账然崩溃）。
 */
package com.zhangran.app.log

import com.zhangran.app.capture.CaptureOutcome

/**
 * 一条待写入的捕获日志/证据记录。
 *
 * 字段对应 `docs/02` §20（时间 / 来源 / 包名 / 上游规则版本 / 状态 / confidence / reason）
 * 与 `docs/03` §10（证据字段）。
 *
 * TODO(T2b): 本记录将映射为 Room 的 `capture_logs` 表（字段清单待 P-002 确认）。
 *
 * @property captureId 捕获 id
 * @property receivedAt 接收时刻（UTC epoch millis）
 * @property outcome 处理结论
 * @property reason 可解释原因（版本不匹配 / 非法载荷等）
 * @property pendingReason 建议的 Pending 原因标识（本任务不决策状态，只记录建议）
 * @property sourcePackage 触发方包名
 * @property version 载荷版本
 * @property isHistoricalReplay 是否历史回灌
 * @property sanitizedPayloadRef 脱敏后的载荷文本（可为空）
 */
data class CaptureLogRecord(
    val captureId: String,
    val receivedAt: Long,
    val outcome: CaptureOutcome,
    val reason: String? = null,
    val pendingReason: String? = null,
    val sourcePackage: String? = null,
    val version: Int = 0,
    val isHistoricalReplay: Boolean = false,
    val sanitizedPayloadRef: String? = null,
)

/**
 * 捕获日志/证据写入口。
 */
interface CaptureLogSink {

    /**
     * 写入一条记录。
     *
     * 实现必须吞掉自身异常（最多打日志），不得把异常抛回调用方。
     */
    fun write(record: CaptureLogRecord)
}
