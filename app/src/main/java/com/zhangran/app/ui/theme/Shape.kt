/**
 * 形状 token（唯一落点）。
 *
 * 依据：`docs/05_UI设计系统.md` §6 Shape（Card / Dialog / Button / Input / Chip radius）。
 * 具体圆角数值集中在 `Dimens.kt` 的 `Radii`，本文件只做 Compose 侧组装。
 */
package com.zhangran.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 组件形状 token。
 *
 * - `card` / `dialog` / `button` / `input` / `chip` 供组件直接引用；
 * - `materialShapes` 供 `MaterialTheme(shapes = …)` 使用（small/medium/large 映射到 input/card/dialog）。
 */
data class ZhangRanShapes(
    val card: RoundedCornerShape,
    val dialog: RoundedCornerShape,
    val button: RoundedCornerShape,
    val input: RoundedCornerShape,
    val chip: RoundedCornerShape,
) {
    /** 供 MaterialTheme 使用的形状集合。 */
    val materialShapes: Shapes
        get() = Shapes(small = input, medium = card, large = dialog)
}

/**
 * 构建形状 token。
 *
 * @param radii 圆角数值来源（`Dimens.kt` 的 `Radii`），本任务使用占位值。
 */
fun zhangRanShapes(radii: ZhangRanRadii = Radii): ZhangRanShapes = ZhangRanShapes(
    card = RoundedCornerShape(radii.card.dp),
    dialog = RoundedCornerShape(radii.dialog.dp),
    button = RoundedCornerShape(radii.button.dp),
    input = RoundedCornerShape(radii.input.dp),
    // chip 为胶囊形（占位值 999 表示足够大的圆角）
    chip = RoundedCornerShape(radii.chip.dp),
)
