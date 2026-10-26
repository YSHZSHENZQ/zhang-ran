/**
 * 采集核心 → 领域层的**进程内接口**（`docs/07` §3.5 的唯一通道）。
 *
 * 单 APK 方案下不再有 URI scheme / Intent / `payload` 载荷：采集核心与账然同进程，
 * 接口就是普通的 Kotlin 调用（任务书 U0 §4.1）。
 *
 * 下一跳：`capture/` 落 `capture_logs` / `evidence` → `autoaccounting/` Adapter
 * （纯转换，**不是本任务**）→ 领域 Pipeline（Normalize → Confidence → Dedup → Decision）。
 *
 * 一次交易允许多条产出：支付结果页"处理中 → 成功"的二次渲染会产生多条 [CollectorOutput]，
 * 采集核心**不得**合并或丢弃；逐条落 `capture_logs`，由 Dedup 收敛（`docs/03` §12）。
 */
package com.zhangran.app.capture

/**
 * 当前支持的**接口版本**（取代双 APK 时期的契约版本 `v`）。
 *
 * 语义：`CollectorOutput.interfaceVersion` 与本值不一致时，`CaptureProcessor` 必须
 * 写 `CaptureLog` + 建议 Pending，**不得**解析/入账（`docs/07` §3.5、§19.1）。
 */
const val COLLECTOR_INTERFACE_VERSION: Int = 1

/**
 * 历史常量名（双 APK 时期为"载荷版本 v"）。
 *
 * 保留原因：`log/RoomCaptureLogSink`（不在 U0 可改白名单内）引用该名称；
 * 语义已统一为**接口版本**，新代码应使用 [COLLECTOR_INTERFACE_VERSION]。
 */
@Deprecated(
    message = "改用 COLLECTOR_INTERFACE_VERSION：语义已是采集核心与领域层的接口版本",
    replaceWith = ReplaceWith("COLLECTOR_INTERFACE_VERSION"),
)
const val SUPPORTED_PAYLOAD_VERSION: Int = COLLECTOR_INTERFACE_VERSION

/** 接口版本缺失（产出未携带）。 */
const val VERSION_MISSING: Int = -1

/** 接口版本非法（无法解释的取值）。 */
const val VERSION_INVALID: Int = -2

/**
 * 采集核心产出入口（进程内）。
 *
 * 实现方（[CaptureProcessor]）负责：接口版本校验 → 脱敏 → 写日志/证据（经 `CaptureLogSink`）。
 * **不做**候选生成、字段映射与状态决策（那些属 Adapter / Domain）。
 */
interface CollectorEventSource {

    /**
     * 由采集核心的一次产出构造事件（账然侧生成 `captureId`，用于串联日志与证据）。
     */
    fun newOutput(output: CollectorOutput): CollectorEvent

    /**
     * 提交一条产出。
     *
     * 实现必须**不抛未捕获异常**（`docs/07` §18 故障隔离）。
     */
    suspend fun submit(event: CollectorEvent): CaptureResult
}

/**
 * 兼容别名：T2b 的 `CaptureSubmitter`（应用级作用域，不在 U0 白名单）以该名字注入。
 *
 * 两者是**同一类型**（不是第二套定义）；`CaptureSubmitter` 下次改动时可去掉该别名。
 */
typealias CaptureEventSource = CollectorEventSource
