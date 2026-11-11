/**
 * 采集层 Hilt 绑定。
 *
 * 依据 `docs/04` §3（`capture/`、`log/` 的职责）与 §6（存储归属）。
 *
 * U0 变更：
 * - 事件源绑定从 `CaptureEventSource`（URI 时代）切换为 **[CollectorEventSource]**（进程内接口）；
 * - `CaptureEntryActivity` 已随 D7 删除，不再有相关绑定。
 *
 * 保留（T2b）：
 * - `CaptureLogSink` → [RoomCaptureLogSink]（事件真正落 `capture_logs` + `capture_evidence`）；
 * - [CaptureSubmitter]：提交走**应用级作用域**（`T2b` §4.5.1），避免页面结束导致静默丢事件。
 *
 * 注意：本绑定**不**把任何 Repository 暴露给采集核心（`docs/04` §8 禁止项）。
 */
package com.zhangran.app.capture

import com.zhangran.app.capture.accessibility.AccessibilityCapturePipeline
import com.zhangran.app.capture.accessibility.AccessibilityConnectivityRecorder
import com.zhangran.app.capture.accessibility.AccessibilitySnapshotIngest
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.di.ApplicationScope
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.log.CaptureLogSink
import com.zhangran.app.log.RoomCaptureLogSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    /** 事件源实现：采集接入层编排器（进程内接口）。 */
    @Binds
    @Singleton
    abstract fun bindCollectorEventSource(impl: CaptureProcessor): CollectorEventSource

    /** 日志写入口：Room 实现（T2b 打开落库）。 */
    @Binds
    @Singleton
    abstract fun bindCaptureLogSink(impl: RoomCaptureLogSink): CaptureLogSink

    companion object {
        /**
         * 提供采集接入层编排器。
         *
         * 说明：`clock` / `idGenerator` 用默认值（系统时间 / UUID），测试通过构造器直接注入。
         */
        @Provides
        @Singleton
        fun provideCaptureProcessor(logSink: CaptureLogSink): CaptureProcessor =
            CaptureProcessor(logSink = logSink)

        /**
         * 提供应用级提交器（`T2b` §4.5.1）。
         *
         * 用 `@Provides` 而非 `@Binds`：需要注入 `@ApplicationScope` 限定的作用域参数。
         */
        @Provides
        @Singleton
        fun provideCaptureSubmitter(
            eventSource: CollectorEventSource,
            @ApplicationScope scope: CoroutineScope,
        ): CaptureSubmitter = CaptureSubmitter(eventSource = eventSource, scope = scope)

        /** M6-2：无障碍快照只进待确认。 */
        @Provides
        @Singleton
        fun provideAccessibilitySnapshotIngest(
            normalize: Normalize,
            transactions: TransactionRepository,
            records: CaptureLogRepository,
            health: CaptureHealthStore,
        ): AccessibilitySnapshotIngest = AccessibilitySnapshotIngest(
            normalize = normalize,
            transactions = transactions,
            records = records,
            onCaptured = { at -> health.recordCapture(at) },
        )

        @Provides
        @Singleton
        fun provideAccessibilityCapturePipeline(
            ingest: AccessibilitySnapshotIngest,
            @ApplicationScope scope: CoroutineScope,
        ): AccessibilityCapturePipeline = AccessibilityCapturePipeline(
            ingest = ingest,
            scope = scope,
        )

        @Provides
        @Singleton
        fun provideAccessibilityConnectivityRecorder(
            records: CaptureLogRepository,
            @ApplicationScope scope: CoroutineScope,
        ): AccessibilityConnectivityRecorder = AccessibilityConnectivityRecorder(
            records = records,
            scope = scope,
        )
    }
}
