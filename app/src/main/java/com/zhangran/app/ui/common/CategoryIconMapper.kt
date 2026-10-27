/**
 * 分类图标映射：**语义标识 → emoji**（显示层）。
 *
 * 存储层（`DefaultData.kt`，P-009 规定）只存语义标识（如 `food`），**不得**把 emoji 写进数据库；
 * emoji 仅在 UI 显示时由本文件映射（任务书 §4.3）。
 *
 * 说明：emoji 在不同 Android 版本 / 字体下渲染不同，属 `UI_CONSISTENCY_SPEC` §1 的
 * B 类"字体渲染差异"，是与原型**允许**的差异（已在任务书 §4.3 记录）。
 *
 * 未知标识 → 返回 [DEFAULT_EMOJI]，**不崩溃、不显示空白**。
 */
package com.zhangran.app.ui.common

/**
 * 语义图标标识与 emoji 的映射表。
 *
 * 覆盖 P-009 的 12 支出 + 6 收入（两个「其他」共用 `other`）。
 */
object CategoryIconMapper {

    /** 未知标识的兜底 emoji。 */
    const val DEFAULT_EMOJI: String = "📦"

    private val MAPPINGS: Map<String, String> = linkedMapOf(
        // 支出
        "food" to "🍜",
        "transport" to "🚇",
        "shopping" to "🛍",
        "housing" to "🏠",
        "entertainment" to "🎮",
        "medical" to "💊",
        "education" to "📚",
        "communication" to "📱",
        "gift" to "🎁",
        "travel" to "✈️",
        "pet" to "🐾",
        "other" to "📦",
        // 收入
        "salary" to "💰",
        "bonus" to "🎉",
        "investment" to "📈",
        "parttime" to "💼",
        "redpacket" to "🧧",
    )

    /**
     * 语义标识 → emoji。
     *
     * @param icon 语义标识（可为 `null`，如历史数据缺失）
     * @return 对应 emoji；未知或缺失时返回 [DEFAULT_EMOJI]
     */
    fun toEmoji(icon: String?): String = MAPPINGS[icon] ?: DEFAULT_EMOJI

    /** 全部受支持的语义标识（供测试与完整性自查）。 */
    val supportedIcons: Set<String> get() = MAPPINGS.keys

    /** 完整映射（只读，供测试断言）。 */
    val mappings: Map<String, String> get() = MAPPINGS
}
