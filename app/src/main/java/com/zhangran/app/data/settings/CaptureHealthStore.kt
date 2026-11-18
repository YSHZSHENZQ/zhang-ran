/**
 * 采集健康度用的两个时间：上次捕获、这次打开总开关的时刻。
 *
 * 跟主题开关存在同一个 DataStore 文件里，但不进备份的 settings.json：
 * 清空日志不会把它们一起清掉，调试回拨也只改上次捕获时间。
 */
package com.zhangran.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class CaptureHealth(
    val lastCaptureAtMillis: Long? = null,
    val watchStartedAtMillis: Long? = null,
    /** 上次成功捕获之后有没有被拒绝的采样。成功捕获会清掉。 */
    val rejectedSamplesSinceCapture: Boolean = false,
    /** 已经为哪一次静默写过「疑似失效」日志。离开该状态后清掉。 */
    val staleFlipMarker: String? = null,
)

interface CaptureHealthStore {

    val state: Flow<CaptureHealth>

    /** 新的捕获比已记录的更新时才覆盖，避免旧回放把时钟拨回去。 */
    suspend fun recordCapture(atMillis: Long)

    /** 调试专用：无条件写成给定时间，可以早于现有记录。 */
    suspend fun setLastCaptureAt(atMillis: Long)

    /**
     * 调试专用：一次写上次捕获和开始监听，避免中间状态触发两次「疑似失效」。
     * 不清除「期间有过被拒绝的采样」。
     */
    suspend fun setDebugTimeline(lastCaptureAtMillis: Long, watchStartedAtMillis: Long)

    suspend fun markWatchStarted(atMillis: Long)

    suspend fun clearWatchStarted()

    /** 记下出现过被拒绝的采样。不改上次捕获时间。 */
    suspend fun recordRejectedSample()

    suspend fun markStaleFlip(marker: String)

    suspend fun clearStaleFlipMarker()
}

private val LAST_CAPTURE_AT = longPreferencesKey("auto_ledger_last_capture_at")
private val WATCH_STARTED_AT = longPreferencesKey("auto_ledger_watch_started_at")
private val REJECTED_SAMPLES = booleanPreferencesKey("auto_ledger_rejected_samples")
private val STALE_FLIP_MARKER = stringPreferencesKey("auto_ledger_stale_flip_marker")

@Singleton
class DataStoreCaptureHealthStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : CaptureHealthStore {

    override val state: Flow<CaptureHealth> = dataStore.data.map { preferences ->
        CaptureHealth(
            lastCaptureAtMillis = preferences[LAST_CAPTURE_AT],
            watchStartedAtMillis = preferences[WATCH_STARTED_AT],
            rejectedSamplesSinceCapture = preferences[REJECTED_SAMPLES] == true,
            staleFlipMarker = preferences[STALE_FLIP_MARKER],
        )
    }

    override suspend fun recordCapture(atMillis: Long) {
        dataStore.edit { preferences ->
            val current = preferences[LAST_CAPTURE_AT]
            if (current == null || atMillis > current) {
                preferences[LAST_CAPTURE_AT] = atMillis
            }
            preferences[REJECTED_SAMPLES] = false
        }
    }

    override suspend fun recordRejectedSample() {
        dataStore.edit { preferences ->
            preferences[REJECTED_SAMPLES] = true
        }
    }

    override suspend fun markStaleFlip(marker: String) {
        dataStore.edit { preferences ->
            preferences[STALE_FLIP_MARKER] = marker
        }
    }

    override suspend fun clearStaleFlipMarker() {
        dataStore.edit { preferences ->
            preferences.remove(STALE_FLIP_MARKER)
        }
    }

    override suspend fun setLastCaptureAt(atMillis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_CAPTURE_AT] = atMillis
        }
    }

    override suspend fun setDebugTimeline(lastCaptureAtMillis: Long, watchStartedAtMillis: Long) {
        dataStore.edit { preferences ->
            preferences[LAST_CAPTURE_AT] = lastCaptureAtMillis
            preferences[WATCH_STARTED_AT] = watchStartedAtMillis
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
