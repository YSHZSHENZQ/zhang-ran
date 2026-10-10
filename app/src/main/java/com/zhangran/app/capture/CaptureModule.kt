/**
 * 采集层 Hilt 绑定。
 *
 * 依据 `docs/04` §3（`capture/`、`log/` 的职责）与 §6（存储归属）。
 *
 * 本任务（T4）的取舍（任务书 §4.5）：
 * - `CaptureLogSink` 用 [InMemoryCaptureLogSink]（写 logcat，**不落库**）；
 * - Room 实现将在 T2b 提供并替换本绑定。
 *
 * 注意：本绑定**不**把任何 Repository / DAO 暴露给上游（`docs/04` §8 禁止项）。
 */
package com.zhangran.app.capture

import com.zhangran.app.log.CaptureLogSink
import com.zhangran.app.log.InMemoryCaptureLogSink
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureModule {

    /** 事件源实现：采集编排器。 */
    @Binds
    @Singleton
    abstract fun bindCaptureEventSource(impl: CaptureProcessor): CaptureEventSource

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
         * 日志写入口：本任务用 logcat 实现（TODO(T2b) 换 Room 实现）。
         *
         * 用 `@Provides` 而不是 `@Binds`：该实现有构造参数（内存保留上限），
         * 显式构造比加 `@Inject constructor` 更清楚（后者会让上限无法配置）。
         */
        @Provides
        @Singleton
        fun provideCaptureLogSink(): CaptureLogSink = InMemoryCaptureLogSink()
    }
}
