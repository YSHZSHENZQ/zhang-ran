/**
 * 动画 token（唯一落点）。
 *
 * 依据 `review/UI-ANIMATION-SPEC.md` §2；视觉真值来自 `prototype/账然_UI原型.html`
 * 的全部 `transition` / `@keyframes` / `animation`（18 种 transition + 2 个 keyframes）。
 *
 * 强制纪律（同文件 §6，任何含动效的任务书必须遵守）：
 * - 时长与缓动**只能**引用本文件的 token，**禁止**页面内写字面时长（`tween(300)`）与内联曲线；
 * - `ease` 必须用 [MotionEasings.ease]（`CubicBezierEasing(0.25, 0.1, 0.25, 1)`），
 *   **不得**用 `FastOutSlowInEasing` 顶替——两者数值不同，观感可辨（前者收尾更软）；
 * - 入场动画优先 `AnimatedVisibility(enter = …)`，避免首帧闪烁（规范 §4.3）；
 * - CSS `transition: all` 在 Compose 无等价物，必须**逐属性**声明动画（规范 §4.1）；
 * - **不得**给渐变背景加插值动画：原型主题切换时背景是**跳变**（CSS 无法插值 gradient），
 *   这是有意识的一致，不是遗漏（规范 §4.2）。
 */
package com.zhangran.app.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 时长 token（毫秒）。取值逐条对应原型里的 `transition` / `animation` 声明。
 *
 * @property instant 100：按压反馈（`transform .1s`）
 * @property fast 150：按压 / 淡入淡出（`.15s`）
 * @property normal 200：`transition: all .2s`、颜色与描边
 * @property medium 250：`transform/opacity .25s`、弹层位移
 * @property page 300：页面切换（`opacity/transform .3s`）
 * @property nav 320：底部导航滑入滑出（`.tab-bar .32s`）
 * @property enter 380：列表入场 `fadeInUp .38s`
 * @property slow 400：主题切换文字色（`.4s`）、进度条宽度（`width .4s`）
 * @property theme 500：`body` 的 `background .5s`（仅纯色属性生效，渐变见规范 §4.2）
 * @property pulse 1600：呼吸点 `pulse 1.6s`
 */
data class MotionDurations(
    val instant: Int,
    val fast: Int,
    val normal: Int,
    val medium: Int,
    val page: Int,
    val nav: Int,
    val enter: Int,
    val slow: Int,
    val theme: Int,
    val pulse: Int,
)

/** 时长 token 取值（逐条取自原型）。 */
val MotionDuration: MotionDurations = MotionDurations(
    instant = 100,
    fast = 150,
    normal = 200,
    medium = 250,
    page = 300,
    nav = 320,
    enter = 380,
    slow = 400,
    theme = 500,
    pulse = 1600,
)

/**
 * 三次贝塞尔控制点（纯数据，便于纯 JVM 断言）。
 *
 * `CubicBezierEasing` 不暴露内部参数，因此**控制点在此显式声明**，
 * [MotionEasings] 用同一组常量构造 Easing —— 二者不会漂移。
 *
 * @property x1 第 1 控制点 x
 * @property y1 第 1 控制点 y
 * @property x2 第 2 控制点 x
 * @property y2 第 2 控制点 y
 */
data class BezierControlPoints(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
)

/**
 * 缓动曲线 token。
 *
 * | token | CSS 等价 | 实现 |
 * |---|---|---|
 * | [ease] | `cubic-bezier(0.25, 0.1, 0.25, 1)` | `CubicBezierEasing`（**不得**用内建顶替） |
 * | [easeInOut] | `cubic-bezier(0.42, 0, 0.58, 1)` | `CubicBezierEasing` |
 * | [standard] | `cubic-bezier(0.4, 0, 0.2, 1)` | `FastOutSlowInEasing`（数值完全一致，用内建即可） |
 */
object MotionEasings {

    /** CSS `ease` 的控制点。 */
    val easeControlPoints: BezierControlPoints = BezierControlPoints(0.25f, 0.1f, 0.25f, 1f)

    /** CSS `ease-in-out` 的控制点。 */
    val easeInOutControlPoints: BezierControlPoints = BezierControlPoints(0.42f, 0f, 0.58f, 1f)

    /** CSS `cubic-bezier(.4, 0, .2, 1)` 的控制点（= Material 标准曲线）。 */
    val standardControlPoints: BezierControlPoints = BezierControlPoints(0.4f, 0f, 0.2f, 1f)

    /** CSS `ease`：按压、颜色、弹层、页面切换、入场，全部用它。 */
    val ease: Easing = CubicBezierEasing(
        easeControlPoints.x1,
        easeControlPoints.y1,
        easeControlPoints.x2,
        easeControlPoints.y2,
    )

    /** CSS `ease-in-out`：呼吸点循环。 */
    val easeInOut: Easing = CubicBezierEasing(
        easeInOutControlPoints.x1,
        easeInOutControlPoints.y1,
        easeInOutControlPoints.x2,
        easeInOutControlPoints.y2,
    )

    /** 底部导航滑入滑出：Material 标准曲线（与原型 `cubic-bezier(.4,0,.2,1)` 数值一致）。 */
    val standard: Easing = FastOutSlowInEasing
}

/**
 * 位移距离 token（dp），用于页面切换与入场动画。
 *
 * @property pageSlide 页面切换水平位移（原型 `translateX(26px)`）
 * @property enterSlide 入场垂直位移（原型 `fadeInUp` 的 `translateY(10px)`）
 */
data class MotionDistances(
    val pageSlide: Int,
    val enterSlide: Int,
)

/** 位移距离取值（逐条取自原型）。 */
val MotionDistance: MotionDistances = MotionDistances(
    pageSlide = 26,
    enterSlide = 10,
)

/**
 * 按压缩放 token（原型各 `:active` 选择器的 `scale()`）。
 *
 * 数值越小按压越"深"；元素越大，缩放幅度越小（原型的一致做法）。
 *
 * @property icon 0.92：图标按钮（`.head-icon` / `.back-btn`）
 * @property tile 0.94：可点区块（`.page-head-right` / `.key` / `.cat-item`）
 * @property button 0.96：次级按钮（`.detail-btn` / `.pending-btn`）
 * @property largeButton 0.97：主按钮（`.onboard-btn`）
 * @property card 0.98：卡片（`.source-card`）
 * @property row 0.975：列表行（`.tx-row`）
 * @property pulseMin 0.7：呼吸点的缩放下限（`@keyframes pulse` 的 `50%`）
 */
data class MotionScales(
    val icon: Float,
    val tile: Float,
    val button: Float,
    val largeButton: Float,
    val card: Float,
    val row: Float,
    val pulseMin: Float,
)

/** 按压缩放取值（逐条取自原型的 `:active` 与 `@keyframes pulse`）。 */
val MotionScale: MotionScales = MotionScales(
    icon = 0.92f,
    tile = 0.94f,
    button = 0.96f,
    largeButton = 0.97f,
    card = 0.98f,
    row = 0.975f,
    pulseMin = 0.7f,
)

// ---- Compose 侧投影（时长直接是 Int 毫秒，交给 tween(Int)）----

/** 页面切换水平位移（dp）。 */
val MotionDistances.pageSlideDp: Dp get() = pageSlide.dp

/** 入场垂直位移（dp）。 */
val MotionDistances.enterSlideDp: Dp get() = enterSlide.dp
