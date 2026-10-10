/**
 * 采集层 Hilt 绑定。
 *
 * 依据 `docs/04` §3（`capture/`、`log/` 的职责）与 §6（存储归属）。
 *
 * T2b 变更：
 * - `CaptureLogSink` 由临时 logcat 实现换为 **Room 实现**（[RoomCaptureLogSink]），
 *   事件真正落 `capture_logs` + `capture_evidence`（`07` §3.2）；
 * - 新增 [CaptureSubmitter]：把提交迁到**应用级作用域**（`T2b` §4.5.1）。
 *
 * 注意：本绑定**不**把任何 Repository 暴露给上游（`docs/04` §8 禁止项）。
 */
package com.zhangran.app.capture

import com.zhangran.app.di.ApplicationScope
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

    /** 事件源实现：采集编排器。 */
    @Binds
    @Singleton
    abstract fun bindCaptureEventSource(impl: CaptureProcessor): CaptureEventSource

    /** 日志写入口：Room 实现（T2b 打开落库）。 */
    @Binds
    @Singleton
    abstract fun bindCaptureLogSink(impl: RoomCaptureLogSink): CaptureLogSink

    companion object {
        /**
         * 提供采集编排器。
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
            eventSource: CaptureEventSource,
            @ApplicationScope scope: CoroutineScope,
        ): CaptureSubmitter = CaptureSubmitter(eventSource = eventSource, scope = scope)
    }
}
