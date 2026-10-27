/**
 * 自定义数字键盘（任务书 M2a §4.1）。
 *
 * 布局（严格按原型 `.keypad`）：
 * ```text
 * 1 2 3 ⌫
 * 4 5 6 C
 * 7 8 9 保存
 * 0(占两格) .
 * ```
 *
 * 无障碍（§4.8）：每个键都有 `contentDescription`（"1" "删除" "保存" 等），
 * 触控高度不小于 48dp。
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.zhangran.app.ui.screen.add.AddLayout
import com.zhangran.app.ui.theme.MotionDuration
import com.zhangran.app.ui.theme.MotionEasings
import com.zhangran.app.ui.theme.MotionScale
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.accentGradientEndColor
import com.zhangran.app.ui.theme.accentGradientStartColor
import com.zhangran.app.ui.theme.glassBorderColor
import com.zhangran.app.ui.theme.surfaceVariantColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor

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
        KeyRow {
            NumberKey("7", Modifier.weight(1f)) { onDigit('7') }
            NumberKey("8", Modifier.weight(1f)) { onDigit('8') }
            NumberKey("9", Modifier.weight(1f)) { onDigit('9') }
            SaveKey(
                enabled = !saving,
                modifier = Modifier.weight(1f),
                onClick = onSave,
            )
        }
        KeyRow {
            // 0 占两格（原型 grid-column: span 2）
            NumberKey("0", Modifier.weight(2f)) { onDigit('0') }
            NumberKey(".", Modifier.weight(1f)) { onDot() }
            SpacerKey(Modifier.weight(1f))
        }
    }
}

@Composable
private fun KeyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AddLayout.keyGap),
        content = content,
    )
}

/** 行尾占位（保持 4 列对齐；不响应点击）。 */
@Composable
private fun SpacerKey(modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(AddLayout.keyHeight))
}

/** 数字键。 */
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

/** 功能键（⌫ / C）。 */
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

/** 保存键（原型 `.key.accent`：渐变背景 + 白字）。 */
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
    )
}

/**
 * 键的统一外观：玻璃底 + 圆角 + 按压缩放（`MotionScale.tile`，`MotionDuration.instant`）。
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
        Modifier.background(color = colors.surfaceVariantColor, shape = shapes.tile)
    }

    Box(
        modifier = modifier
            .height(AddLayout.keyHeight)
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
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.titleMedium
            },
            color = when {
                accent -> colors.glassBorderColor // 近白（原型 #fff），不写字面色值
                emphasis -> colors.textSecondaryColor
                else -> colors.textPrimaryColor
            },
        )
    }
}
