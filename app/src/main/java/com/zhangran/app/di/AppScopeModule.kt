/**
 * 应用级协程作用域（Hilt 提供）。
 *
 * 为什么需要（`T2b` §4.5.1）：
 * T4 的 `CaptureEntryActivity` 原来用 `lifecycleScope.launch { submit(event) }` 提交事件，
 * 紧随其后调用 `finish()` → `onDestroy` → `lifecycleScope` 被取消。
 * 之前没出问题只因为 `submit` 全程没有挂起点（同步跑完）；一旦日志 sink 换成 Room
 * （必然有 `Dispatchers.IO` 挂起），在途提交会被取消，而 `runCatching` **捕不到**
 * `CancellationException`（协程取消是正常路径）→ 上游发了事件却**什么都没记、也没有报错**。
 *
 * 因此提交必须走**应用级**作用域：生命周期与进程一致，不随 Activity 结束而取消。
 *
 * 约束（`T2b` §4.5.1）：
 * - `SupervisorJob`：单个提交失败不影响其它提交；
 * - **不得**持有 Activity / Context 引用（本模块只创建空作用域，不注入 Context）；
 * - 不引入 WorkManager（无需新依赖）。
 */
package com.zhangran.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 应用级作用域限定符。
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    /**
     * 进程级协程作用域。
     *
     * 说明：不主动 cancel——其生命周期即进程生命周期（进程结束时随之销毁）。
     * 若将来需要优雅关闭，应在此注册关闭钩子，而不是在 UI 层取消。
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
