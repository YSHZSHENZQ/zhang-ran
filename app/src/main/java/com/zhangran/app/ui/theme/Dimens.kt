/**
 * 间距 / 圆角 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §5 Spacing、§6 Shape；
 * **取值来自 `prototype/账然_UI原型.html`**（逐条对应选择器见 `review/UI-0-token对照表.md`）。
 *
 * 规则：页面与组件内**禁止**出现裸数字尺寸，只能引用本文件的 token。
 *
 * 真值以 dp 的 Int 存储，Compose 侧通过扩展属性投影为 `Dp`；纯 JVM 单测可直接断言数值。
 *
 * 关于"主刻度 vs 扩展"：`docs/05` §5 规定 6 个间距名（xs/sm/md/lg/xl/xxl）、§6 规定 5 个圆角名；
 * 原型另有更细刻度（2 / 10 / 22 等）与更多圆角用途（36 / 5 / 12）。这些**扩展刻度不进**
 * [asTokenMap]（否则会破坏既有的"恰好 N 项"完整性判据），由 [asExtendedTokenMap] 覆盖。
 */
package com.zhangran.app.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 一套间距 token 的纯数据表示（单位：dp）。
 *
 * @property xs 最小间距（4）：图标与文字之间、元信息上边距
 * @property sm 小间距（8）：卡片内元素之间
 * @property md 中间距（12）：卡片内边距
 * @property lg 大间距（16）：区块之间
 * @property xl 大间距（22）：页面水平内边距（原型 `.section-label` 左右 22px）
 * @property xxl 最大间距（32）：页面顶部留白（原型 `.status-bar` 左 32px）
 * @property xxs 扩展：最细刻度（2，原型 `.tab-badge` top / `.modal-handle`）
 * @property gapTight 扩展：紧邻元素间距（10，原型 `.section-label` gap）
 * @property pagePadding 扩展：页面水平内边距的语义别名（22，与 [xl] 同值，供页面引用更清晰）
 */
data class ZhangRanSpacing(
    val xs: Int,
    val sm: Int,
    val md: Int,
    val lg: Int,
    val xl: Int,
    val xxl: Int,
    val xxs: Int = 2,
    val gapTight: Int = 10,
    val pagePadding: Int = 22,
)

/**
 * 圆角 / 组件尺寸 token（单位：dp）。
 *
 * 依据 `docs/05` §6 Shape（Card / Dialog / Button / Input / Chip radius）。
 *
 * @property card 卡片（20，原型 `.card`）
 * @property dialog 弹层（22，原型弹层容器）
 * @property button 主按钮（16，原型 `.onboard-btn` / `.detail-btn`）
 * @property input 输入框（13，原型 `.modal-input`）
 * @property chip 标签（20，原型 `.chip`）
 * @property badge 扩展：状态徽标（5，原型 `.badge-*`）
 * @property option 扩展：选项行（12，原型 `.modal-option`）
 * @property handle 扩展：拖拽把手（2，原型 `.modal-handle`）
 * @property tile 扩展：图标块（14，原型 `.head-icon` 等）
 */
data class ZhangRanRadii(
    val card: Int,
    val dialog: Int,
    val button: Int,
    val input: Int,
    val chip: Int,
    val badge: Int = 5,
    val option: Int = 12,
    val handle: Int = 2,
    val tile: Int = 14,
)

/** 间距 token 取值（6 个主刻度，严格递增；逐条取自原型）。 */
val Spacing: ZhangRanSpacing = ZhangRanSpacing(
    xs = 4,
    sm = 8,
    md = 12,
    lg = 16,
    xl = 22,
    xxl = 32,
    xxs = 2,
    gapTight = 10,
    pagePadding = 22,
)

/** 圆角 token 取值（5 个主项 + 4 个扩展项；逐条取自原型）。 */
val Radii: ZhangRanRadii = ZhangRanRadii(
    card = 20,
    dialog = 22,
    button = 16,
    input = 13,
    chip = 20,
    badge = 5,
    option = 12,
    handle = 2,
    tile = 14,
)

/** 间距 token 名 → 取值（`docs/05` §5 的 6 项；扩展刻度见 [asExtendedTokenMap]）。 */
fun ZhangRanSpacing.asTokenMap(): Map<String, Int> = linkedMapOf(
    "xs" to xs,
    "sm" to sm,
    "md" to md,
    "lg" to lg,
    "xl" to xl,
    "xxl" to xxl,
)

/** 间距 token 全部项（6 主 + 3 扩展）。 */
fun ZhangRanSpacing.asExtendedTokenMap(): Map<String, Int> = asTokenMap() + linkedMapOf(
    "xxs" to xxs,
    "gapTight" to gapTight,
    "pagePadding" to pagePadding,
)

/** 圆角 token 名 → 取值（`docs/05` §6 的 5 项；扩展圆角见 [asExtendedTokenMap]）。 */
fun ZhangRanRadii.asTokenMap(): Map<String, Int> = linkedMapOf(
    "card" to card,
    "dialog" to dialog,
    "button" to button,
    "input" to input,
    "chip" to chip,
)

/** 圆角 token 全部项（5 主 + 5 扩展）。 */
fun ZhangRanRadii.asExtendedTokenMap(): Map<String, Int> = asTokenMap() + linkedMapOf(
    "badge" to badge,
    "option" to option,
    "handle" to handle,
    "tile" to tile,
)

// ---- Compose 侧投影 ----

val ZhangRanSpacing.xsDp: Dp get() = xs.dp
val ZhangRanSpacing.smDp: Dp get() = sm.dp
val ZhangRanSpacing.mdDp: Dp get() = md.dp
val ZhangRanSpacing.lgDp: Dp get() = lg.dp
val ZhangRanSpacing.xlDp: Dp get() = xl.dp
val ZhangRanSpacing.xxlDp: Dp get() = xxl.dp
val ZhangRanSpacing.xxsDp: Dp get() = xxs.dp
val ZhangRanSpacing.gapTightDp: Dp get() = gapTight.dp
val ZhangRanSpacing.pagePaddingDp: Dp get() = pagePadding.dp
