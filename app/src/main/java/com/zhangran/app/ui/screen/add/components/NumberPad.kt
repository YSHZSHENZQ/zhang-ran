/**
 * 自定义数字键盘（任务书 M2a §4.1，视觉按原型 `.keypad`）。
 *
 * 布局（保存键**纵跨右下两行**，填满原型里空着的那一格）：
 * ```text
 * 1 2 3 ⌫
 * 4 5 6 C
 * 7 8 9 ┐
 * 0(占两格) .  保存
 * ```
 *
 * 字号按原型：数字键 `20px/500`、功能键 `15px/500`、保存键 `15px/700`。
 * 所有键**等宽**（保存键宽度由 `(总宽 - 3*gap) / 4` 精确算出，不用 weight 分配，
 * 否则会差 `2*gap/3`，实测偏宽 5dp）。
 *
 * 无障碍（§4.8）：每个键都有 `contentDescription`，触控高度 ≥ 48dp。
 */
package com.zhangran.app.ui.screen.add.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.zhangran.app.ui.screen.add.AddLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanGlassTokens
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.glassBorderColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.zhangRanGlass

/**
 * 数字键盘。
 *
 * @param onDigit 数字键
 * @param onDot 小数点
 * @param onBackspace 退格
 * @param onClear 清空
 * @param onSave 保存
 * @param saving 保存中（禁用保存键）
 * @param modifier 外部修饰符
 */
@Composable
internal fun NumberPad(
    onDigit: (Char) -> Unit,
    onDot: () -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AddLayout.keyGap),
    ) {
        KeyRow {
            NumberKey("1", Modifier.weight(1f)) { onDigit('1') }
            NumberKey("2", Modifier.weight(1f)) { onDigit('2') }
            NumberKey("3", Modifier.weight(1f)) { onDigit('3') }
            FunctionKey("⌫", "删除", Modifier.weight(1f)) { onBackspace() }
        }
        KeyRow {
            NumberKey("4", Modifier.weight(1f)) { onDigit('4') }
            NumberKey("5", Modifier.weight(1f)) { onDigit('5') }
            NumberKey("6", Modifier.weight(1f)) { onDigit('6') }
            FunctionKey("C", "清空", Modifier.weight(1f)) { onClear() }
        }
        // 下两行 + 保存键（纵跨两行）。宽度用 BoxWithConstraints 精确计算，保证与单键等宽。
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val singleKeyWidth = (maxWidth - AddLayout.keyGap * 3) / 4
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AddLayout.keyGap),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(AddLayout.keyGap),
                ) {
                    KeyRow {
                        NumberKey("7", Modifier.weight(1f)) { onDigit('7') }
                        NumberKey("8", Modifier.weight(1f)) { onDigit('8') }
                        NumberKey("9", Modifier.weight(1f)) { onDigit('9') }
                    }
                    KeyRow {
                        // 0 占两格（原型 `grid-column: span 2`）
                        NumberKey("0", Modifier.weight(2f)) { onDigit('0') }
                        NumberKey(".", Modifier.weight(1f)) { onDot() }
                    }
                }
                SaveKey(
                    enabled = !saving,
                    modifier = Modifier
                        .width(singleKeyWidth)
                        // 高度 = 左侧两行键 + 一个间隙；不能用 fillMaxHeight：
                        // Row 高度无约束时它会被解析成"填满剩余屏幕"，把整页撑爆（已复现）。
                        .height(AddLayout.keyHeight * 2 + AddLayout.keyGap),
                    onClick = onSave,
                )
            }
        }
    }
}

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AddLayout.keyGap),
        content = content,
    )
}

/** 数字键（原型 `.key { font-size: 20px; font-weight: 500 }`）。 */
@Composable
private fun NumberKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeySurface(
        text = text,
        description = text,
        modifier = modifier,
        onClick = onClick,
        accent = false,
        emphasis = false,
    )
}

/** 功能键（原型 `.key.fn { font-size: 15px }`）。 */
@Composable
private fun FunctionKey(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeySurface(
        text = text,
        description = description,
        modifier = modifier,
        onClick = onClick,
        accent = false,
        emphasis = true,
    )
}

/** 保存键（原型 `.key.accent`：渐变背景 + 白字 15px/700）。 */
@Composable
private fun SaveKey(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    KeySurface(
        text = "保存",
        description = "保存",
        modifier = modifier,
        onClick = onClick,
        accent = true,
        emphasis = true,
        enabled = enabled,
        // 跨两行时高度由外部 height 决定
        fixedHeight = false,
    )
}

/**
 * 键的统一外观：玻璃底 + 圆角 + 按压缩放（`MotionScale.tile`，`MotionDuration.instant`）。
 *
 * @param fixedHeight 是否使用固定键高（保存键跨行时由外部高度决定，传 `false`）
 */
@Composable
private fun KeySurface(
    text: String,
    description: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    accent: Boolean,
    emphasis: Boolean,
    enabled: Boolean = true,
    fixedHeight: Boolean = true,
) {
    val colors = ZhangRanTheme.colors
    val shapes = ZhangRanTheme.shapes
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    // 原型 `.key { transition: transform .1s ease }` → 按压反馈用 instant
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) MotionScale.tile else 1f,
        animationSpec = tween(durationMillis = MotionDuration.instant, easing = MotionEasings.ease),
        label = "keyScale",
    )

    val background: Modifier = if (accent) {
        Modifier.background(
            brush = Brush.linearGradient(
                listOf(colors.accentGradientStartColor, colors.accentGradientEndColor),
            ),
            shape = shapes.tile,
        )
    } else {
        // 原型 `.key` 是 `.glass`：半透明填充 + 1dp 描边 + 顶部内高光。
        // 键盘键密排，故把外阴影归零（否则 32dp 阴影会互相叠加、显脏）。
        Modifier.zhangRanGlass(
            colors = colors,
            shape = shapes.tile,
            tokens = ZhangRanGlassTokens.copy(shadowBlurDp = 0, shadowOffsetYDp = 0),
        )
    }

    Box(
        modifier = modifier
            .then(if (fixedHeight) Modifier.height(AddLayout.keyHeight) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shapes.tile)
            .then(background)
            .semantics { contentDescription = description }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (emphasis) {
                MaterialTheme.typography.labelMedium.copy(
                    // 原型 `.key.fn` 15px；`.key.accent` 15px/700
                    fontSize = AddLayout.keyFunctionFontSize,
                    fontWeight = if (accent) FontWeight.Bold else FontWeight.Medium,
                )
            } else {
                MaterialTheme.typography.titleMedium.copy(
                    // 原型 `.key` 20px/500
                    fontSize = AddLayout.keyFontSize,
                    fontWeight = FontWeight.Medium,
                )
            },
            color = when {
                accent -> colors.glassBorderColor // 近白（原型 #fff），不写字面色值
                emphasis -> colors.textSecondaryColor
                else -> colors.textPrimaryColor
            },
        )
    }
}
