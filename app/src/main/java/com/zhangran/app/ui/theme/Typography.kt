/**
 * 排版 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §4 Typography（Display / Headline / Title / Body /
 * Label / Caption / **Amount**）。金额必须有专用 Typography（`05` §4、§9）。
 *
 * 真值以纯数据（字号 sp、字重枚举、行高 sp）存储，Compose 侧在 `themeTypography()` 中组装为
 * `TextStyle`；纯 JVM 单测可直接断言数值。
 * TODO(视觉): 取值与字重待按 05 §4 与原型 CSS 变量确定。
 */
package com.zhangran.app.ui.theme

/**
 * 字重（纯枚举，避免在纯数据层依赖 Compose 的 `FontWeight`）。
 */
enum class ZhangRanFontWeight { NORMAL, MEDIUM, BOLD }

/**
 * 单个排版 token 的纯数据表示。
 *
 * @property fontSizeSp 字号（sp）
 * @property fontWeight 字重
 * @property lineHeightSp 行高（sp）
 */
data class ZhangRanTextToken(
    val fontSizeSp: Int,
    val fontWeight: ZhangRanFontWeight,
    val lineHeightSp: Int,
)

/**
 * 一套排版 token 的纯数据表示。
 *
 * @property display 展示级大字（首页汇总金额等场景的标题）
 * @property headline 页面主标题
 * @property title 区块标题
 * @property body 正文
 * @property label 标签 / 按钮文字
 * @property caption 辅助说明 / 元信息
 * @property amount 金额专用（`05` §4 要求金额使用专用 Typography）
 */
data class ZhangRanTypographyTokens(
    val display: ZhangRanTextToken,
    val headline: ZhangRanTextToken,
    val title: ZhangRanTextToken,
    val body: ZhangRanTextToken,
    val label: ZhangRanTextToken,
    val caption: ZhangRanTextToken,
    val amount: ZhangRanTextToken,
)

/** 排版 token 取值（占位值）。 */
val TypographyTokens: ZhangRanTypographyTokens = ZhangRanTypographyTokens(
    display = ZhangRanTextToken(fontSizeSp = 32, fontWeight = ZhangRanFontWeight.BOLD, lineHeightSp = 40),
    headline = ZhangRanTextToken(fontSizeSp = 24, fontWeight = ZhangRanFontWeight.BOLD, lineHeightSp = 32),
    title = ZhangRanTextToken(fontSizeSp = 18, fontWeight = ZhangRanFontWeight.MEDIUM, lineHeightSp = 24),
    body = ZhangRanTextToken(fontSizeSp = 14, fontWeight = ZhangRanFontWeight.NORMAL, lineHeightSp = 22),
    label = ZhangRanTextToken(fontSizeSp = 13, fontWeight = ZhangRanFontWeight.MEDIUM, lineHeightSp = 18),
    caption = ZhangRanTextToken(fontSizeSp = 11, fontWeight = ZhangRanFontWeight.NORMAL, lineHeightSp = 16),
    // 金额使用等宽思想：行高略大、字重加粗，便于对齐与阅读（格式化逻辑属 M2，本任务只给 token）
    amount = ZhangRanTextToken(fontSizeSp = 28, fontWeight = ZhangRanFontWeight.BOLD, lineHeightSp = 36),
)

/** 排版 token 名 → 取值（用于完整性自查与单元测试）。 */
fun ZhangRanTypographyTokens.asTokenMap(): Map<String, ZhangRanTextToken> = linkedMapOf(
    "Display" to display,
    "Headline" to headline,
    "Title" to title,
    "Body" to body,
    "Label" to label,
    "Caption" to caption,
    "Amount" to amount,
)
