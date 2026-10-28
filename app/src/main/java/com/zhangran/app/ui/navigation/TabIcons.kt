/**
 * 底部导航图标（`M5-1` §4.3；视觉真值：`prototype/账然_UI原型.html` 的 `.tab-bar` SVG）。
 *
 * **不使用图标库**：5 个图标用 [ImageVector] 手写；路径字符串逐字取自原型：
 * - 首页：房子（path + polyline）
 * - 统计：3 根竖线
 * - 记一笔：加号
 * - 预算：**时钟**（不是钱包）
 * - 我的：**齿轮**（不是人像）
 *
 * 描边参数统一：`stroke-width=2`、`stroke-linecap/linejoin=round`、`fill=none`、24×24 viewport。
 * 颜色由调用方传入（`Icon(tint = …)` 会覆盖矢量内部占位色），因此不写死主题色。
 */
package com.zhangran.app.ui.navigation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

object TabIcons {

    /** 首页：`M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z` + polyline `9,22 9,12 15,12 15,22`。 */
    private const val HOME_PATH =
        "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12H15V22"

    /** 统计：line (18,20)-(18,10)、(12,20)-(12,4)、(6,20)-(6,14)。 */
    private const val STATS_PATH = "M18 20V10 M12 20V4 M6 20V14"

    /** 记一笔：line (12,5)-(12,19)、(5,12)-(19,12)。 */
    private const val ADD_PATH = "M12 5V19 M5 12H19"

    /** 预算：时钟 `circle r=10` + 指针 `M12 6v6l4 2`。 */
    private const val BUDGET_PATH =
        "M12 2a10 10 0 1 0 0 20 10 10 0 1 0 0-20z M12 6v6l4 2"

    /** 我的：齿轮 `circle r=3` + 原型的齿轮 path（逐字）。 */
    private const val SETTINGS_PATH =
        "M12 9a3 3 0 1 0 0 6 3 3 0 1 0 0-6z " +
            "M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06" +
            "a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09" +
            "A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83" +
            "l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09" +
            "A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83" +
            "l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09" +
            "a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83" +
            "l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4" +
            "h-.09a1.65 1.65 0 0 0-1.51 1z"

    /** 按色调缓存（每帧只构建一次；同一 tint 复用同一矢量）。 */
    private val cache = mutableMapOf<Pair<Destination, Color>, ImageVector>()

    /**
     * 取某个 tab 的图标。
     *
     * @param tint 描边颜色（调用方传主题 token，`Icon(tint)` 再统一着色）
     */
    fun forDestination(destination: Destination, tint: Color): ImageVector =
        cache.getOrPut(destination to tint) {
            when (destination) {
                Destination.Home -> build("TabHome", HOME_PATH, tint)
                Destination.Stats -> build("TabStats", STATS_PATH, tint)
                Destination.Add -> build("TabAdd", ADD_PATH, tint)
                Destination.Budget -> build("TabBudget", BUDGET_PATH, tint)
                Destination.Mine -> build("TabSettings", SETTINGS_PATH, tint)
                // 非顶级页面不在底栏使用图标；给一个安全的默认（首页）
                else -> build("TabHome", HOME_PATH, tint)
            }
        }

    private fun build(name: String, pathData: String, tint: Color): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = ICON_SIZE_DP.dp,
            defaultHeight = ICON_SIZE_DP.dp,
            viewportWidth = VIEWPORT_SIZE,
            viewportHeight = VIEWPORT_SIZE,
        ).addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(tint),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ).build()

    private const val ICON_SIZE_DP = 24
    private const val VIEWPORT_SIZE = 24f
    private const val STROKE_WIDTH = 2f
}
