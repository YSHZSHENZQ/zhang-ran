/**
 * 数据层与用例层的 Hilt 绑定（M1c'）。
 *
 * 绑定内容：
 * - 3 个 Repository：接口 → Room 实现（`@Binds`，便于 JVM 单测用 fake）；
 * - `AddManualTransactionUseCase`：用 `@Provides`（它带 `clock` / `idGenerator` 默认参数，
 *   Hilt 无法自动注入 lambda）；
 * - `Normalize`：本任务只用到它的**商户 / 备注 / 时间**规范化（任务书 §4.2 V6/V7），
 *   分类与账户的 Resolver 实现属阶段二（采集链路），此处传 no-op。
 *
 * 注意：`ObserveTransactionsUseCase` 用 `@Inject` 构造，无需在此声明。
 */
package com.zhangran.app.di

import com.zhangran.app.data.repository.AccountRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.RoomAccountRepository
import com.zhangran.app.data.repository.RoomCategoryRepository
import com.zhangran.app.data.repository.RoomTransactionRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.TransactionTime
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import com.zhangran.app.domain.usecase.AddManualTransactionUseCase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(impl: RoomTransactionRepository): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindCategoryRepository(impl: RoomCategoryRepository): CategoryRepository

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: RoomAccountRepository): AccountRepository

    companion object {

        /**
         * 手工记账所需的规范化器。
         *
         * 本任务不涉及采集链路，因此分类/账户 Resolver 用 **no-op**（恒返回 `null`）：
         * 手工记账的分类与账户由用户在表单中直接选定 id，不需要名称→id 的解析。
         *
         * TODO(U2)：采集链路装配时，`Normalize` 需要真实的 `CategoryResolver` / `AccountResolver`，
         * 届时应改为全局单例并注入真实实现（避免与本绑定重复）。
         */
        @Provides
        @Singleton
        fun provideNormalize(): Normalize = Normalize(
            categoryResolver = CategoryResolver { null },
            accountResolver = AccountResolver { null },
            defaultZoneId = TransactionTime.deviceZoneId(),
        )

        /**
         * 手工记账用例（唯一写入口）。
         *
         * `clock` / `idGenerator` 用默认值（系统时间 / UUID）；测试通过构造器直接注入。
         */
        @Provides
        @Singleton
        fun provideAddManualTransactionUseCase(
            normalize: Normalize,
            transactionRepository: TransactionRepository,
        ): AddManualTransactionUseCase = AddManualTransactionUseCase(
            normalize = normalize,
            transactionRepository = transactionRepository,
        )
    }
}
