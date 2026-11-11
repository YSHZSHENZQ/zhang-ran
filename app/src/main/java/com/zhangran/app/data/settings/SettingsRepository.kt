/**
 * 设置读写（`M4c` §4.1）。
 *
 * 存储：`androidx.datastore:datastore-preferences`（**唯一新增依赖**）。
 * - 暴露 `Flow<AppSettings>`，主题与设置页订阅同一数据源（切换即时生效）；
 * - 写入为 `suspend`；阈值越界**拒绝写入**并返回结构化错误（不静默截断）。
 */
package com.zhangran.app.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** 阈值字段（用于拒绝写入时定位是哪一个越界）。 */
enum class ThresholdField {
    OVERALL,
    FIELD,
}

/** 设置写入结果。 */
sealed interface SettingsWriteResult {

    data object Success : SettingsWriteResult

    /** 阈值越界（**拒绝写入**，不截断）。 */
    data class InvalidThreshold(
        val field: ThresholdField,
        val value: Double,
    ) : SettingsWriteResult
}

interface SettingsRepository {

    /** 当前设置（DataStore 的 `Flow`；首次发射即为"默认值 + 已持久化值"）。 */
    val settings: Flow<AppSettings>

    /** 保存主题模式。 */
    suspend fun setThemeMode(mode: ThemeMode)

    /**
     * 保存两个置信度阈值。
     *
     * @return [SettingsWriteResult.InvalidThreshold] 时**不会写入任何字段**
     */
    suspend fun setThresholds(
        overallConfidenceThreshold: Double,
        fieldConfidenceThreshold: Double,
    ): SettingsWriteResult

    /** 自动记账总开关。只存值，打开时的计时由调用方另外记下。 */
    suspend fun setAutoLedgerEnabled(enabled: Boolean)

    /** 权限引导完成一次之后不再在冷启动强制进入。 */
    suspend fun setOnboardCompleted(completed: Boolean)
}

// 键定义唯一落点（类与读取映射共用）。
private val THEME_MODE_KEY = stringPreferencesKey(SettingsKeys.THEME_MODE)
private val OVERALL_THRESHOLD_KEY = doublePreferencesKey(SettingsKeys.OVERALL_CONFIDENCE_THRESHOLD)
private val FIELD_THRESHOLD_KEY = doublePreferencesKey(SettingsKeys.FIELD_CONFIDENCE_THRESHOLD)
private val AUTO_LEDGER_ENABLED_KEY = booleanPreferencesKey(SettingsKeys.AUTO_LEDGER_ENABLED)
private val ONBOARD_COMPLETED_KEY = booleanPreferencesKey(SettingsKeys.ONBOARD_COMPLETED)

/**
 * DataStore 实现。
 *
 * 读取容错：IO 异常 → 回退默认值（避免设置页整页 Error）；非法枚举/越界阈值 → 使用默认值。
 * 写入校验：任一阈值越界 → 整体拒绝（保持两项阈值一致更新）。
 */
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { throwable ->
            if (throwable is IOException) {
                emit(emptyPreferences())
            } else {
                throw throwable
            }
        }
        .map { preferences -> preferences.toAppSettings() }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = mode.name
        }
    }

    override suspend fun setThresholds(
        overallConfidenceThreshold: Double,
        fieldConfidenceThreshold: Double,
    ): SettingsWriteResult {
        if (!overallConfidenceThreshold.isValidThreshold()) {
            return SettingsWriteResult.InvalidThreshold(
                field = ThresholdField.OVERALL,
                value = overallConfidenceThreshold,
            )
        }
        if (!fieldConfidenceThreshold.isValidThreshold()) {
            return SettingsWriteResult.InvalidThreshold(
                field = ThresholdField.FIELD,
                value = fieldConfidenceThreshold,
            )
        }
        dataStore.edit { preferences ->
            preferences[OVERALL_THRESHOLD_KEY] = overallConfidenceThreshold
            preferences[FIELD_THRESHOLD_KEY] = fieldConfidenceThreshold
        }
        return SettingsWriteResult.Success
    }

    override suspend fun setAutoLedgerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_LEDGER_ENABLED_KEY] = enabled
        }
    }

    override suspend fun setOnboardCompleted(completed: Boolean) {
        dataStore.edit { preferences ->
            preferences[ONBOARD_COMPLETED_KEY] = completed
        }
    }
}

/** 默认值 + 已持久化值；未知/越界的持久化值回退默认值。 */
internal fun Preferences.toAppSettings(): AppSettings {
    val themeMode = this[THEME_MODE_KEY]
        ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
        ?: ThemeMode.SYSTEM
    val overall = this[OVERALL_THRESHOLD_KEY]
        ?.takeIf { it.isValidThreshold() }
        ?: SettingsDefaults.OVERALL_CONFIDENCE_THRESHOLD
    val field = this[FIELD_THRESHOLD_KEY]
        ?.takeIf { it.isValidThreshold() }
        ?: SettingsDefaults.FIELD_CONFIDENCE_THRESHOLD
    return AppSettings(
        themeMode = themeMode,
        overallConfidenceThreshold = overall,
        fieldConfidenceThreshold = field,
        autoLedgerEnabled = this[AUTO_LEDGER_ENABLED_KEY] ?: false,
        onboardCompleted = this[ONBOARD_COMPLETED_KEY] ?: false,
    )
}

private fun Double.isValidThreshold(): Boolean =
    !isNaN() && this in SettingsDefaults.THRESHOLD_RANGE
