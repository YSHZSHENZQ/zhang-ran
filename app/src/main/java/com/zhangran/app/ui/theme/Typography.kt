/**
 * 排版 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §4（Display / Headline / Title / Body / Label / Caption / Amount）；
 * **取值来自 `prototype/账然_UI原型.html`**（逐条对应选择器见 `review/UI-0-token对照表.md`）。
 *
 * 真值以纯数据（字号 sp、字重枚举、行高 sp）存储，Compose 侧在 `zhangRanTypography()` 中组装为
 * `TextStyle`；纯 JVM 单测可直接断言数值。
 *
 * 关于小数：原型使用 9.5 / 10.5 / 13.5 px，因此字号与行高以 **Float** 存储（0.5 分辨率），
 * 不四舍五入到整数——`docs/05` §4 要求"取值以原型为准，禁止取相近值"。
 *
 * 行高：原型仅对少数规则显式声明 `line-height`（如 `.detail-note` 1.7）。
 * 其余规则未声明，按浏览器默认 `normal` 以 **字号的 1.2 倍** 折算，并在对照表中逐条标注。
 */
package com.zhangran.app.ui.theme

/**
 * 字重（纯枚举，避免在纯数据层依赖 Compose 的 `FontWeight`）。
 *
 * 取值与原型用到的 CSS `font-weight` 一一对应：400 / 500 / 600 / 700。
 */
enum class ZhangRanFontWeight {
    /** 400：正文、说明。 */
    NORMAL,

    /** 500：中等强调（原型 `.action-tile b` 等）。 */
    MEDIUM,

    /** 600：半粗（原型 `.tx-name`、`.set-name` 等行标题）。 */
    SEMI_BOLD,

    /** 700：粗体（原型页面标题、金额）。 */
    BOLD,
}

/**
 * 单个排版 token 的纯数据表示。
 *
 * @property fontSizeSp 字号（sp，支持 0.5 分辨率）
 * @property fontWeight 字重
 * @property lineHeightSp 行高（sp）
 * @property letterSpacingSp 字间距（sp）；原型对大号金额使用负值（如 `.amount-display` -2px）
 */
data class ZhangRanTextToken(
    val fontSizeSp: Float,
    val fontWeight: ZhangRanFontWeight,
    val lineHeightSp: Float,
    val letterSpacingSp: Float = 0f,
)

/**
 * 一套排版 token 的纯数据表示。
 *
 * @property display 展示级大字（原型 `.amount-display` 42px：首页总览金额）
 * @property headline 页面主标题（原型 `.page-title` / `.modal-title` 17px）
 * @property title 区块/行标题（原型 `.tx-name` / `.set-name` 13.5px）
 * @property body 正文（原型 `.detail-note` 13px，行高 1.7）
 * @property label 标签 / 按钮文字（原型 `.card-label` 10.5px）
 * @property caption 辅助说明 / 元信息（原型 `.stat-k` / `.source-meta` 9.5px）
 * @property amount 金额专用（原型列表金额 `.tx-amt` 14px）
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

/**
 * 排版 token 取值（逐条取自原型）。
 *
 * | token | 原型选择器 | 字号 | 字重 | 行高 |
 * |---|---|---|---|---|
 * | Display | `.amount-display` | 42 | 700 | 50（未声明，×1.2） |
 * | Headline | `.page-title` / `.modal-title` | 17 | 700 | 20（×1.2） |
 * | Title | `.tx-name` | 13.5 | 600 | 16（×1.2） |
 * | Body | `.detail-note` | 13 | 400 | 22（声明 1.7） |
 * | Label | `.card-label` | 10.5 | 400 | 13（×1.2） |
 * | Caption | `.stat-k` / `.source-meta` | 9.5 | 400 | 11（×1.2） |
 * | Amount | `.tx-amt` | 14 | 700 | 17（×1.2） |
 */
val TypographyTokens: ZhangRanTypographyTokens = ZhangRanTypographyTokens(
    display = ZhangRanTextToken(
        fontSizeSp = 42f,
        fontWeight = ZhangRanFontWeight.BOLD,
        lineHeightSp = 50f,
        letterSpacingSp = -2f,
    ),
    headline = ZhangRanTextToken(
        fontSizeSp = 17f,
        fontWeight = ZhangRanFontWeight.BOLD,
        lineHeightSp = 20f,
        letterSpacingSp = 0.5f,
    ),
    title = ZhangRanTextToken(
        fontSizeSp = 13.5f,
        fontWeight = ZhangRanFontWeight.SEMI_BOLD,
        lineHeightSp = 16f,
    ),
    body = ZhangRanTextToken(
        fontSizeSp = 13f,
        fontWeight = ZhangRanFontWeight.NORMAL,
        lineHeightSp = 22f,
    ),
    label = ZhangRanTextToken(
        fontSizeSp = 10.5f,
        fontWeight = ZhangRanFontWeight.NORMAL,
        lineHeightSp = 13f,
        letterSpacingSp = 2f,
    ),
    caption = ZhangRanTextToken(
        fontSizeSp = 9.5f,
        fontWeight = ZhangRanFontWeight.NORMAL,
        lineHeightSp = 11f,
    ),
    // 金额使用等宽思想与负字间距（原型 `.tx-amt` letter-spacing -0.3px）
    amount = ZhangRanTextToken(
        fontSizeSp = 14f,
        fontWeight = ZhangRanFontWeight.BOLD,
        lineHeightSp = 17f,
        letterSpacingSp = -0.3f,
    ),
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
