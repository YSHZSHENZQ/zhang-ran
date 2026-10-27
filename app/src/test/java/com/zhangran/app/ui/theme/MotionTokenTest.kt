/**
 * 动画 token 取值断言（`review/UI-ANIMATION-SPEC.md` §5.1，纯 JVM）。
 *
 * 断言三组 token 与规范逐项一致：
 * - 10 个时长（§2.1）
 * - 3 条缓动曲线的控制点（§2.2），并验证"`ease` 不得用 `FastOutSlowInEasing` 顶替"
 * - 距离常量（§2.3）：`pageSlide = 26`、`enterSlide = 10`
 *
 * 额外断言按压缩放（§2.3 的 `pressScale`，取值来自原型各 `:active`）。
 */
package com.zhangran.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTokenTest {

    // ------------------------------------------------------------------ §2.1 时长

    @Test
    fun 十个时长token与规范一致() {
        assertEquals("instant = .1s", 100, MotionDuration.instant)
        assertEquals("fast = .15s", 150, MotionDuration.fast)
        assertEquals("normal = .2s", 200, MotionDuration.normal)
        assertEquals("medium = .25s", 250, MotionDuration.medium)
        assertEquals("page = .3s", 300, MotionDuration.page)
        assertEquals("nav = .32s", 320, MotionDuration.nav)
        assertEquals("enter = .38s", 380, MotionDuration.enter)
        assertEquals("slow = .4s", 400, MotionDuration.slow)
        assertEquals("theme = .5s", 500, MotionDuration.theme)
        assertEquals("pulse = 1.6s", 1600, MotionDuration.pulse)
    }

    @Test
    fun 时长token均为正数且按语义递增() {
        val all = listOf(
            MotionDuration.instant,
            MotionDuration.fast,
            MotionDuration.normal,
            MotionDuration.medium,
            MotionDuration.page,
            MotionDuration.nav,
            MotionDuration.enter,
            MotionDuration.slow,
            MotionDuration.theme,
            MotionDuration.pulse,
        )
        assertTrue("时长必须为正", all.all { it > 0 })
        assertTrue("时长刻度应严格递增", all.zipWithNext().all { (a, b) -> a < b })
    }

    // ------------------------------------------------------------------ §2.2 缓动

    @Test
    fun ease控制点与CSS一致() {
        val p = MotionEasings.easeControlPoints
        assertEquals("x1", 0.25f, p.x1, 0.0001f)
        assertEquals("y1", 0.1f, p.y1, 0.0001f)
        assertEquals("x2", 0.25f, p.x2, 0.0001f)
        assertEquals("y2", 1f, p.y2, 0.0001f)
    }

    @Test
    fun easeInOut控制点与CSS一致() {
        val p = MotionEasings.easeInOutControlPoints
        assertEquals("x1", 0.42f, p.x1, 0.0001f)
        assertEquals("y1", 0f, p.y1, 0.0001f)
        assertEquals("x2", 0.58f, p.x2, 0.0001f)
        assertEquals("y2", 1f, p.y2, 0.0001f)
    }

    @Test
    fun standard控制点与CSS一致() {
        val p = MotionEasings.standardControlPoints
        assertEquals("x1", 0.4f, p.x1, 0.0001f)
        assertEquals("y1", 0f, p.y1, 0.0001f)
        assertEquals("x2", 0.2f, p.x2, 0.0001f)
        assertEquals("y2", 1f, p.y2, 0.0001f)
    }

    @Test
    fun ease不得用FastOutSlowInEasing顶替() {
        // 规范 §2.2 的强制条款：两者数值不同，观感可辨（ease 收尾更软）
        assertNotSame(
            "ease 必须是 CubicBezierEasing(0.25, 0.1, 0.25, 1)，不得用内建曲线顶替",
            FastOutSlowInEasing,
            MotionEasings.ease,
        )
    }

    @Test
    fun standard直接用内建的FastOutSlowInEasing() {
        // 规范 §2.2：standard 与 Material 标准曲线数值完全一致，用内建即可
        assertSame(FastOutSlowInEasing, MotionEasings.standard)
    }

    @Test
    fun 三条缓动曲线互不相同() {
        assertNotSame(MotionEasings.ease, MotionEasings.easeInOut)
        assertNotSame(MotionEasings.ease, MotionEasings.standard)
        assertNotSame(MotionEasings.easeInOut, MotionEasings.standard)
    }

    // ------------------------------------------------------------------ §2.3 距离

    @Test
    fun 位移距离与原型一致() {
        assertEquals("页面切换水平位移 = translateX(26px)", 26, MotionDistance.pageSlide)
        assertEquals("入场垂直位移 = fadeInUp 的 translateY(10px)", 10, MotionDistance.enterSlide)
    }

    @Test
    fun 位移距离的Dp投影与Int一致() {
        assertEquals(26f, MotionDistance.pageSlideDp.value, 0.0001f)
        assertEquals(10f, MotionDistance.enterSlideDp.value, 0.0001f)
    }

    // ------------------------------------------------------------------ §2.3 按压缩放

    @Test
    fun 按压缩放取值来自原型() {
        assertEquals(".head-icon / .back-btn scale(.92)", 0.92f, MotionScale.icon, 0.0001f)
        assertEquals(".page-head-right / .key / .cat-item scale(.94)", 0.94f, MotionScale.tile, 0.0001f)
        assertEquals(".detail-btn / .pending-btn scale(.96)", 0.96f, MotionScale.button, 0.0001f)
        assertEquals(".onboard-btn scale(.97)", 0.97f, MotionScale.largeButton, 0.0001f)
        assertEquals(".source-card scale(.98)", 0.98f, MotionScale.card, 0.0001f)
        assertEquals(".tx-row scale(.975)", 0.975f, MotionScale.row, 0.0001f)
        assertEquals("@keyframes pulse 50% scale(.7)", 0.7f, MotionScale.pulseMin, 0.0001f)
    }

    @Test
    fun 按压缩放均在0到1之间且按元素尺寸递减() {
        val all = listOf(
            MotionScale.icon,
            MotionScale.tile,
            MotionScale.button,
            MotionScale.largeButton,
            MotionScale.card,
            MotionScale.row,
            MotionScale.pulseMin,
        )
        assertTrue("缩放值必须落在 (0, 1]", all.all { it > 0f && it <= 1f })
        // 元素越小按压缩放越深（数值越小）
        assertTrue("图标应比卡片缩得更深", MotionScale.icon < MotionScale.card)
        assertTrue("按钮应比卡片缩得更深", MotionScale.button < MotionScale.card)
    }
}
