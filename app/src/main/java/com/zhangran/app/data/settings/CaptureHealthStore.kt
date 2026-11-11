/**
 * 采集健康度用的两个时间：上次捕获、这次打开总开关的时刻。
 *
 * 跟主题开关存在同一个 DataStore 文件里，但不进备份的 settings.json：
 * 清空日志不会把它们一起清掉，调试回拨也只改上次捕获时间。
 */
package com.zhangran.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CaptureHealth(
    val lastCaptureAtMillis: Long? = null,
    val watchStartedAtMillis: Long? = null,
)

interface CaptureHealthStore {

    val state: Flow<CaptureHealth>

    /** 新的捕获比已记录的更新时才覆盖，避免旧回放把时钟拨回去。 */
    suspend fun recordCapture(atMillis: Long)

    /** 调试专用：无条件写成给定时间，可以早于现有记录。 */
    suspend fun setLastCaptureAt(atMillis: Long)

    suspend fun markWatchStarted(atMillis: Long)

    suspend fun clearWatchStarted()
}

private val LAST_CAPTURE_AT = longPreferencesKey("auto_ledger_last_capture_at")
private val WATCH_STARTED_AT = longPreferencesKey("auto_ledger_watch_started_at")

@Singleton
class DataStoreCaptureHealthStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CaptureHealthStore {

    override val state: Flow<CaptureHealth> = dataStore.data.map { preferences ->
        CaptureHealth(
            lastCaptureAtMillis = preferences[LAST_CAPTURE_AT],
            watchStartedAtMillis = preferences[WATCH_STARTED_AT],
        )
    }

    override suspend fun recordCapture(atMillis: Long) {
        dataStore.edit { preferences ->
            val current = preferences[LAST_CAPTURE_AT]
            if (current == null || atMillis > current) {
                preferences[LAST_CAPTURE_AT] = atMillis
            }
        }
    }

    override suspend fun setLastCaptureAt(atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_CAPTURE_AT] = atMillis
        }
    }

    override suspend fun markWatchStarted(atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[WATCH_STARTED_AT] = atMillis
        }
    }

    override suspend fun clearWatchStarted() {
        dataStore.edit { preferences ->
            preferences.remove(WATCH_STARTED_AT)
        }
    }
}
