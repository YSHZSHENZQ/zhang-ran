/**
 * 间距 / 尺寸 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §5 Spacing（xs / sm / md / lg / xl / xxl）。
 * 规则：页面与组件内**禁止**出现裸数字尺寸，只能引用本文件的 token
 * （`05` §5「禁止页面中随意出现大量魔法数字」）。
 *
 * 真值以 dp 的 Int 存储，Compose 侧通过扩展属性投影为 `Dp`；纯 JVM 单测可直接断言数值。
 * TODO(视觉): 取值待按 05 §5 与原型 CSS 变量确定。
 */
package com.zhangran.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一套间距 token 的纯数据表示（单位：dp）。
 *
 * @property xs 最小间距（图标与文字之间）
 * @property sm 小间距（卡片内元素之间）
 * @property md 中间距（卡片内边距）
 * @property lg 大间距（区块之间）
 * @property xl 更大间距（页面段落之间）
 * @property xxl 最大间距（页面顶部/底部留白）
 */
data class ZhangRanSpacing(
    val xs: Int,
    val sm: Int,
    val md: Int,
    val lg: Int,
    val xl: Int,
    val xxl: Int,
)

/**
 * 圆角 / 组件尺寸 token（单位：dp）。
 *
 * 依据 `docs/05` §6 Shape（Card / Dialog / Button / Input / Chip radius）。
 * 具体 `Shape` 对象见 `Shape.kt`。
 */
data class ZhangRanRadii(
    val card: Int,
    val dialog: Int,
    val button: Int,
    val input: Int,
    val chip: Int,
)

/** 间距 token 取值（占位值）。 */
val Spacing: ZhangRanSpacing = ZhangRanSpacing(xs = 4, sm = 8, md = 12, lg = 16, xl = 24, xxl = 32)

/** 圆角 token 取值（占位值）。 */
val Radii: ZhangRanRadii = ZhangRanRadii(card = 16, dialog = 20, button = 12, input = 12, chip = 999)

/** 间距 token 名 → 取值（用于完整性自查与单元测试）。 */
fun ZhangRanSpacing.asTokenMap(): Map<String, Int> = linkedMapOf(
    "xs" to xs,
    "sm" to sm,
    "md" to md,
    "lg" to lg,
    "xl" to xl,
    "xxl" to xxl,
)

/** 圆角 token 名 → 取值（用于完整性自查与单元测试）。 */
fun ZhangRanRadii.asTokenMap(): Map<String, Int> = linkedMapOf(
    "card" to card,
    "dialog" to dialog,
    "button" to button,
    "input" to input,
    "chip" to chip,
)

// ---- Compose 侧投影 ----

val ZhangRanSpacing.xsDp: Dp get() = xs.dp
val ZhangRanSpacing.smDp: Dp get() = sm.dp
val ZhangRanSpacing.mdDp: Dp get() = md.dp
val ZhangRanSpacing.lgDp: Dp get() = lg.dp
val ZhangRanSpacing.xlDp: Dp get() = xl.dp
val ZhangRanSpacing.xxlDp: Dp get() = xxl.dp
