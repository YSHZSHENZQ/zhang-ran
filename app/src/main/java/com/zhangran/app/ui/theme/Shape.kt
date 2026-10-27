/**
 * 形状 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §6 Shape（Card / Dialog / Button / Input / Chip radius）。
 * 具体圆角数值集中在 `Dimens.kt` 的 `Radii`，本文件只做 Compose 侧组装。
 *
 * 取值来自 `prototype/账然_UI原型.html`：
 * `.card` 20 / `.chip` 20 / 主按钮 `.onboard-btn`、`.detail-btn` 16 / `.modal-input` 13 /
 * 弹层容器 22 / `.screen` 36 / `.badge-*` 5 / `.modal-option` 12 / `.modal-handle` 2。
 */
package com.zhangran.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 组件形状 token。
 *
 * - `card` / `dialog` / `button` / `input` / `chip` 供组件直接引用（`docs/05` §6 的五项）；
 * - `screen` / `badge` / `option` / `handle` / `tile` 为原型中另有用途的扩展形状；
 * - `materialShapes` 供 `MaterialTheme(shapes = …)` 使用（small/medium/large 映射到 input/card/dialog）。
 */
data class ZhangRanShapes(
    val card: RoundedCornerShape,
    val dialog: RoundedCornerShape,
    val button: RoundedCornerShape,
    val input: RoundedCornerShape,
    val chip: RoundedCornerShape,
    val screen: RoundedCornerShape,
    val badge: RoundedCornerShape,
    val option: RoundedCornerShape,
    val handle: RoundedCornerShape,
    val tile: RoundedCornerShape,
) {
    /** 供 MaterialTheme 使用的形状集合。 */
    val materialShapes: Shapes
        get() = Shapes(small = input, medium = card, large = dialog)
}

/**
 * 构建形状 token。
 *
 * @param radii 圆角数值来源（`Dimens.kt` 的 `Radii`）
 */
fun zhangRanShapes(radii: ZhangRanRadii = Radii): ZhangRanShapes = ZhangRanShapes(
    card = RoundedCornerShape(radii.card.dp),
    dialog = RoundedCornerShape(radii.dialog.dp),
    button = RoundedCornerShape(radii.button.dp),
    input = RoundedCornerShape(radii.input.dp),
    chip = RoundedCornerShape(radii.chip.dp),
    screen = RoundedCornerShape(radii.screen.dp),
    badge = RoundedCornerShape(radii.badge.dp),
    option = RoundedCornerShape(radii.option.dp),
    handle = RoundedCornerShape(radii.handle.dp),
    tile = RoundedCornerShape(radii.tile.dp),
)
