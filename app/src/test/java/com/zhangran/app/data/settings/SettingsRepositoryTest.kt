/**
 * 设置仓库测试（`M4c` A8/A14；`docs/04` §6）。
 *
 * 用内存 `DataStore<Preferences>` 替身验证仓库自身逻辑：
 * 默认值、主题持久化、阈值读写、越界拒绝（不写入）、非法枚举/越界值的读取回退。
 */
package com.zhangran.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 内存 DataStore<Preferences>（仅测试用）。 */
private class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {

    private val state = MutableStateFlow(initial)
    val updateCount = AtomicInteger(0)

    override val data: Flow<Preferences> = state

    override suspend fun updateData(
        transform: suspend (t: Preferences) -> Preferences,
    ): Preferences {
        val updated = transform(state.value)
        state.value = updated
        updateCount.incrementAndGet()
        return updated
    }
}

class SettingsRepositoryTest {

    private fun repository(store: InMemoryPreferencesDataStore = InMemoryPreferencesDataStore()) =
        DataStoreSettingsRepository(store) to store

    // ---------------------------------------------------------------- 默认值

    @Test
    fun 空存储返回默认值() = runBlocking {
        val (repository, _) = repository()
        val settings = repository.settings.first()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(0.8, settings.overallConfidenceThreshold, 0.0)
        assertEquals(0.5, settings.fieldConfidenceThreshold, 0.0)
    }

    // ---------------------------------------------------------------- 主题

    @Test
    fun 主题写入后可读回() = runBlocking {
        val (repository, _) = repository()

        repository.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, repository.settings.first().themeMode)

        repository.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, repository.settings.first().themeMode)
    }

    @Test
    fun 未知主题名读取回退跟随系统() = runBlocking {
        val key = stringPreferencesKey(SettingsKeys.THEME_MODE)
        val initial = emptyPreferences().toMutablePreferences().apply { this[key] = "NEON" }
        val store = InMemoryPreferencesDataStore(initial)
        val (repository, _) = repository(store)

        assertEquals(ThemeMode.SYSTEM, repository.settings.first().themeMode)
    }

    // ---------------------------------------------------------------- 阈值

    @Test
    fun 阈值写入成功且两项一起更新() = runBlocking {
        val (repository, _) = repository()

        val result = repository.setThresholds(0.9, 0.6)

        assertEquals(SettingsWriteResult.Success, result)
        val settings = repository.settings.first()
        assertEquals(0.9, settings.overallConfidenceThreshold, 0.0)
        assertEquals(0.6, settings.fieldConfidenceThreshold, 0.0)
    }

    @Test
    fun 阈值上界与下界都允许() = runBlocking {
        val (repository, _) = repository()

        assertTrue(repository.setThresholds(1.0, 0.0) is SettingsWriteResult.Success)
        val settings = repository.settings.first()
        assertEquals(1.0, settings.overallConfidenceThreshold, 0.0)
        assertEquals(0.0, settings.fieldConfidenceThreshold, 0.0)
    }

    @Test
    fun 整体阈值越界拒绝且不写入任何字段() = runBlocking {
        val (repository, store) = repository()

        val result = repository.setThresholds(
            overallConfidenceThreshold = 1.2,
            fieldConfidenceThreshold = 0.6,
        )

        assertTrue(result is SettingsWriteResult.InvalidThreshold)
        val invalid = result as SettingsWriteResult.InvalidThreshold
        assertEquals(ThresholdField.OVERALL, invalid.field)
        assertEquals(1.2, invalid.value, 0.0)
        assertEquals("越界时不得写入", 0, store.updateCount.get())
        assertEquals(0.8, repository.settings.first().overallConfidenceThreshold, 0.0)
        assertEquals(0.5, repository.settings.first().fieldConfidenceThreshold, 0.0)
    }

    @Test
    fun 字段阈值越界拒绝() = runBlocking {
        val (repository, _) = repository()

        val result = repository.setThresholds(
            overallConfidenceThreshold = 0.9,
            fieldConfidenceThreshold = -0.1,
        )

        assertTrue(result is SettingsWriteResult.InvalidThreshold)
        assertEquals(ThresholdField.FIELD, (result as SettingsWriteResult.InvalidThreshold).field)
        assertEquals(0.8, repository.settings.first().overallConfidenceThreshold, 0.0)
    }

    @Test
    fun NaN与无穷被拒绝() = runBlocking {
        val (repository, _) = repository()

        assertTrue(repository.setThresholds(Double.NaN, 0.5) is SettingsWriteResult.InvalidThreshold)
        assertTrue(
            repository.setThresholds(0.8, Double.POSITIVE_INFINITY) is
                SettingsWriteResult.InvalidThreshold,
        )
    }

    @Test
    fun 已存储的越界阈值读取回退默认值() = runBlocking {
        val overall = androidx.datastore.preferences.core.doublePreferencesKey(
            SettingsKeys.OVERALL_CONFIDENCE_THRESHOLD,
        )
        val initial = emptyPreferences().toMutablePreferences().apply { this[overall] = 1.5 }
        val store = InMemoryPreferencesDataStore(initial)
        val (repository, _) = repository(store)

        assertEquals(0.8, repository.settings.first().overallConfidenceThreshold, 0.0)
    }

    @Test
    fun 设置流在写入后发出新值() = runBlocking {
        val (repository, _) = repository()
        val values = mutableListOf<ThemeMode>()

        repository.setThemeMode(ThemeMode.DARK)
        repository.settings.map { it.themeMode }.first().let(values::add)

        assertFalse(values.isEmpty())
        assertEquals(ThemeMode.DARK, values.last())
    }

    @Test
    fun 自动记账总开关默认关闭且可持久化() = runBlocking {
        val (repository, _) = repository()
        assertFalse(repository.settings.first().autoLedgerEnabled)
        assertFalse(repository.settings.first().onboardCompleted)

        repository.setAutoLedgerEnabled(true)
        repository.setOnboardCompleted(true)

        assertTrue(repository.settings.first().autoLedgerEnabled)
        assertTrue(repository.settings.first().onboardCompleted)
    }
}
