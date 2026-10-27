/**
 * 数据库 Hilt 绑定（`data/local`）。
 *
 * 依据 `docs/04` §6（存储归属）与 §7（Database 要求）：
 * - 只提供 `ZhangRanDatabase` 与 5 个 DAO；
 * - **禁止**把 DAO 直接暴露给 UI / 上游模块（`docs/04` §20：Composable → DAO、
 *   上游采集模块 → Room 均为禁止项）——访问必须经 Repository（后续任务实现）。
 * - 不使用 `fallbackToDestructiveMigration`（`docs/04` §7）。
 *
 * 说明：数据库实例为单例；`addMigrations(ALL_MIGRATIONS)` 注册迁移。
 * 数据库文件位于应用私有目录，`allowBackup=false`（Manifest），不参与系统备份。
 */
package com.zhangran.app.data.local

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.zhangran.app.data.local.dao.AccountDao
import com.zhangran.app.data.local.dao.BudgetDao
import com.zhangran.app.data.local.dao.CaptureEvidenceDao
import com.zhangran.app.data.local.dao.CaptureLogDao
import com.zhangran.app.data.local.dao.CategoryDao
import com.zhangran.app.data.local.dao.DuplicateDecisionDao
import com.zhangran.app.data.local.dao.FieldEditDao
import com.zhangran.app.data.local.dao.ParsingRuleDao
import com.zhangran.app.data.local.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.zhangran.app.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideZhangRanDatabase(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
    ): ZhangRanDatabase {
        val marker = InitializationMarker(context)
        val database = Room.databaseBuilder(
            context,
            ZhangRanDatabase::class.java,
            ZhangRanDatabase.DATABASE_NAME,
        )
            // 首次建库 → 标记置为"待初始化"（不在此写数据，避免 onCreate 事务内死锁）
            .addCallback(ZhangRanDatabase.initializationCallback(marker))
            .addMigrations(*ZhangRanDatabase.ALL_MIGRATIONS)
            .build()

        // 启动检查：数据库打开之后（不在建库事务内）写入初始数据。
        // 放在应用级作用域异步执行，避免阻塞启动；写入本身是幂等的（标记 + name/type 判重）。
        val initializer = DatabaseInitializer(marker)
        scope.launch {
            runCatching { initializer.ensureInitialized(database) }
                .onFailure { Log.w(TAG, "初始数据写入失败", it) }
        }

        return database
    }

    @Provides
    fun provideTransactionDao(database: ZhangRanDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideCategoryDao(database: ZhangRanDatabase): CategoryDao = database.categoryDao()

    @Provides
    fun provideAccountDao(database: ZhangRanDatabase): AccountDao = database.accountDao()

    @Provides
    fun provideBudgetDao(database: ZhangRanDatabase): BudgetDao = database.budgetDao()

    @Provides
    fun provideFieldEditDao(database: ZhangRanDatabase): FieldEditDao = database.fieldEditDao()

    // ---- T2b 新增（v2 四张表）----

    @Provides
    fun provideCaptureEvidenceDao(database: ZhangRanDatabase): CaptureEvidenceDao =
        database.captureEvidenceDao()

    @Provides
    fun provideParsingRuleDao(database: ZhangRanDatabase): ParsingRuleDao =
        database.parsingRuleDao()

    @Provides
    fun provideCaptureLogDao(database: ZhangRanDatabase): CaptureLogDao =
        database.captureLogDao()

    @Provides
    fun provideDuplicateDecisionDao(database: ZhangRanDatabase): DuplicateDecisionDao =
        database.duplicateDecisionDao()

    /** 日志标签（初始数据写入失败留痕：不崩溃、不静默，`docs/07` §18）。 */
    private const val TAG: String = "ZhangRanDatabase"
}
