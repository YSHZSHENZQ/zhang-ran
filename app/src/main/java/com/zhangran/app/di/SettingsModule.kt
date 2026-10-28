/**
 * 设置存储的 Hilt 装配（`M4c` §4.1）。
 *
 * - `DataStore<Preferences>` 是**进程内单例**：`preferencesDataStore` 委托保证同一文件只创建一个实例；
 * - `SettingsEntryPoint` 供 `Theme.kt` 在组合根读取设置（主题必须包在 `ZhangRanApp` 之外）。
 */
package com.zhangran.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.zhangran.app.data.settings.DataStoreSettingsRepository
import com.zhangran.app.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** DataStore 文件名（仅设置项；与 Room 数据库无关）。 */
private const val SETTINGS_DATASTORE_NAME = "app_settings"

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = SETTINGS_DATASTORE_NAME,
)

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.settingsDataStore

    @Provides
    @Singleton
    fun provideSettingsRepository(dataStore: DataStore<Preferences>): SettingsRepository =
        DataStoreSettingsRepository(dataStore)
}

/** 组合根读取设置用（`Theme.kt` 的 `ZhangRanAppTheme`）。 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SettingsEntryPoint {
    fun settingsRepository(): SettingsRepository
}
