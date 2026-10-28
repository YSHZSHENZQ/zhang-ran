/**
 * 应用设置（`M4c` §4.1；阈值默认值来自 `docs/03` §16）。
 *
 * 只放**设置项**：不存账务数据，也不存初始化标记（后者仍由 `DatabaseInitializer` 的
 * SharedPreferences 管理）。M4b（备份）会把这里的字段写入 `settings.json`。
 */
package com.zhangran.app.data.settings

/** 主题模式（`M4c` §4.1）。 */
enum class ThemeMode {
    /** 跟随系统。 */
    SYSTEM,

    /** 强制浅色。 */
    LIGHT,

    /** 强制深色。 */
    DARK,
}

/**
 * @property themeMode 主题模式；默认跟随系统
 * @property overallConfidenceThreshold 整体置信度阈值；默认 0.8（`03` §16 规则 3）
 * @property fieldConfidenceThreshold 字段置信度阈值；默认 0.5（`03` §16 规则 2）
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val overallConfidenceThreshold: Double = SettingsDefaults.OVERALL_CONFIDENCE_THRESHOLD,
    val fieldConfidenceThreshold: Double = SettingsDefaults.FIELD_CONFIDENCE_THRESHOLD,
)

/** 设置项的默认值（唯一来源）。 */
object SettingsDefaults {
    const val OVERALL_CONFIDENCE_THRESHOLD: Double = 0.8
    const val FIELD_CONFIDENCE_THRESHOLD: Double = 0.5

    /** 阈值的合法范围（闭区间）。 */
    val THRESHOLD_RANGE: ClosedFloatingPointRange<Double> = 0.0..1.0
}

/** DataStore 键名（集中定义，`M4c` §4.1）。 */
object SettingsKeys {
    const val THEME_MODE: String = "theme_mode"
    const val OVERALL_CONFIDENCE_THRESHOLD: String = "overall_confidence_threshold"
    const val FIELD_CONFIDENCE_THRESHOLD: String = "field_confidence_threshold"
}
