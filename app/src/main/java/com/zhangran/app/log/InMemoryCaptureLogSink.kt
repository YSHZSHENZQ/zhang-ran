/**
 * 捕获日志的内存实现（本任务的临时实现，**不落库**）。
 *
 * 依据 T4 任务书 §4.5：落库推迟到 T2b，本任务用接口 + 内存/logcat 实现，
 * 以便 T4 与 T2a 解耦、两者可并行。
 *
 * 行为：
 * - 始终写 `android.util.Log`，便于 `adb logcat` 观察（验收 A1/A4/A5 依赖日志）；
 * - 同时把记录保留在内存列表中（有上限），供测试断言与本地排查。
 *
 * TODO(T2b): 用 Room 实现替换（`capture_logs` 表字段清单待 P-002 确认）。
 */
package com.zhangran.app.log

import android.util.Log

/**
 * @param maxRecords 内存中保留的最大条数（超出后丢弃最旧的，避免无界增长）
 */
class InMemoryCaptureLogSink(
    private val maxRecords: Int = DEFAULT_MAX_RECORDS,
) : CaptureLogSink {

    private val lock = Any()
    private val records = ArrayDeque<CaptureLogRecord>()

    override fun write(record: CaptureLogRecord) {
        synchronized(lock) {
            records.addLast(record)
            while (records.size > maxRecords) {
                records.removeFirst()
            }
        }
        // 只打印脱敏后的摘要：不得打印原始 URI / 未脱敏载荷。
        // 载荷字段本身已经在 CaptureProcessor 中脱敏过，这里原样输出便于设备侧验证 A5。
        runCatching {
            Log.i(
                TAG,
                "capture id=${record.captureId} outcome=${record.outcome} " +
                    "v=${record.version} replay=${record.isHistoricalReplay} " +
                    "pkg=${record.sourcePackage ?: "-"} reason=${record.reason ?: "-"} " +
                    "pending=${record.pendingReason ?: "-"} " +
                    "payload=${record.sanitizedPayloadRef ?: "-"}",
            )
        }
    }

    /** 当前保留的全部记录（按写入顺序）。 */
    fun snapshot(): List<CaptureLogRecord> = synchronized(lock) { records.toList() }

    /** 清空内存记录（对应"清空日志"语义，`docs/03` §10）。 */
    fun clear() {
        synchronized(lock) { records.clear() }
    }

    companion object {
        private const val TAG = "ZhangRanCapture"
        private const val DEFAULT_MAX_RECORDS = 200
    }
}
