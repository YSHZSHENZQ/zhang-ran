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

import com.zhangran.app.domain.model.CaptureSource

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

/**
 * 采集来源的中文展示文案（`M2b` §4.1；枚举 SSOT 见 `docs/07` §5）。
 *
 * 集中定义，**首页与详情页共用**（`AGENTS.md` §7.2：不得散落各页）。
 * `MANUAL` 用「手动」（`M2b` §4.1 的展示口径；`docs/07` §5 原文是"用户手工记账"，此处取展示用语）。
 *
 * 说明：`docs/07` §5 给了示例「NOTIFICATION → 自动捕获 · 通知」；
 * 本任务按更简洁的行内文案展示（"通知"），细节（渠道名）属日志/证据页。
 */
object CaptureSourceText {

    /**
     * 来源 → 中文文案。
     *
     * @param source 采集来源枚举
     */
    fun of(source: CaptureSource): String = when (source) {
        CaptureSource.MANUAL -> "手动"
        CaptureSource.NOTIFICATION -> "通知"
        CaptureSource.HOOK -> "应用数据"
        CaptureSource.SMS -> "短信"
        CaptureSource.ACCESSIBILITY -> "无障碍"
        CaptureSource.AI -> "AI"
        CaptureSource.OCR -> "OCR"
    }
}
