/**
 * 底栏显隐与返回落点的纯逻辑测试（`M5-1` §5；A1、A6–A8）。
 *
 * 不依赖 Android：直接验证 [showsBottomBar] 与 [backActionFor]，覆盖 5 个顶级页 / 13 个二级页。
 */
package com.zhangran.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BottomNavBehaviorTest {

    @Test
    fun A1_仅5个顶级页显示底栏() {
        val topLevel = Destination.entries.filter { it.isTopLevel }
        assertEquals(5, topLevel.size)
        topLevel.forEach { destination ->
            assertTrue("${destination.title} 应显示底栏", showsBottomBar(destination))
        }
        assertEquals(
            listOf(
                Destination.Home,
                Destination.Stats,
                Destination.Add,
                Destination.Budget,
                Destination.Mine,
            ),
            topLevel,
        )
    }

    @Test
    fun A2_全部13个二级页隐藏底栏() {
        val secondary = Destination.entries.filterNot { it.isTopLevel }
        assertEquals(13, secondary.size)
        secondary.forEach { destination ->
            assertFalse("${destination.title} 不应显示底栏", showsBottomBar(destination))
        }
    }

    @Test
    fun 推入型记一笔记隐藏底栏而Tab进入显示() {
        assertTrue(showsBottomBar(Destination.Add))
        assertFalse(showsBottomBar(Destination.Add, addOpenedAsPush = true))
        // 其他顶级页不受该标志影响
        assertTrue(showsBottomBar(Destination.Home, addOpenedAsPush = true))
        assertTrue(showsBottomBar(Destination.Stats, addOpenedAsPush = true))
    }

    @Test
    fun 推入型记一笔不拦截返回() {
        assertTrue(shouldInterceptBack(Destination.Stats))
        assertTrue(shouldInterceptBack(Destination.Stats, addOpenedAsPush = true))
        assertFalse(shouldInterceptBack(Destination.Add, addOpenedAsPush = true))
        assertTrue(shouldInterceptBack(Destination.Add))
    }

    @Test
    fun 未知路由不显示底栏() {
        assertFalse(showsBottomBar(null))
    }

    @Test
    fun A6_非首页顶级页返回首页() {
        listOf(Destination.Stats, Destination.Add, Destination.Budget, Destination.Mine).forEach {
            assertEquals(
                "${it.title} 返回应回首页",
                BackAction.GoHome(Destination.Home),
                backActionFor(it),
            )
        }
    }

    @Test
    fun A8_首页返回交给系统退出() {
        assertEquals(BackAction.ExitApp, backActionFor(Destination.Home))
    }

    @Test
    fun A7_二级页返回交给Navigation() {
        Destination.entries.filterNot { it.isTopLevel }.forEach {
            assertEquals(
                "${it.title} 应交给默认返回栈",
                BackAction.PopBackStack,
                backActionFor(it),
            )
        }
        assertEquals(BackAction.PopBackStack, backActionFor(null))
    }
}
