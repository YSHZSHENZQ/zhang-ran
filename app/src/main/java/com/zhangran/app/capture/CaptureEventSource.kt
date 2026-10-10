/**
 * 上游采集事件入口接口。
 *
 * 依据 `docs/07` §3.2（`capture/` 只负责"接收 + 保真 + 标记来源"）与 `docs/04` §9.1
 * （接收入口只做"取参 → 交给 `CaptureEventSource` → 立即 `finish()`"）。
 *
 * 设计说明：**事件构造也在本接口内完成**，这样接收入口（Activity）只负责把
 * `Intent` 的原始字符串转交进来，不需要做任何解析、校验或类型判断
 * （满足 T4 §4.1「入口内无解析/映射/落库」与 A3）。
 *
 * 约束：
 * - 实现方**不得**做领域决策（Pending / Confirmed 只在 Domain，`docs/07` §2）；
 * - 调用方（Activity）**不得**依赖实现细节，只依赖本接口。
 */
package com.zhangran.app.capture

/**
 * 事件来源：把上游事件交给账然采集链路。
 */
interface CaptureEventSource {

    /**
     * 由原始入参构造事件（**参数提取在实现内部完成**，调用方不做解析）。
     *
     * @param rawUri `Intent.data` 的字符串形式（可为空）
     * @param sourcePackage 触发方包名（`Intent.package`，可为空）
     * @param listenerConnectedAt 监听/连接时刻（UTC epoch millis，可为空）
     * @param eventTime 事件发生时刻（上游 `postTime`，可为空）
     */
    fun newEvent(
        rawUri: String?,
        sourcePackage: String?,
        listenerConnectedAt: Long? = null,
        eventTime: Long? = null,
    ): ExternalCaptureEvent

    /**
     * 提交一个上游事件。
     *
     * 实现必须**不阻塞主线程**且**不向上抛未捕获异常**（`docs/07` §18 故障隔离）：
     * 上游给的任何畸形输入都只能导致"记录失败"，不能导致崩溃。
     *
     * @param event 已解析出参数的原始事件（可能版本不匹配、载荷非法）
     * @return 处理结论（供日志与测试观察）
     */
    suspend fun submit(event: ExternalCaptureEvent): CaptureResult
}
