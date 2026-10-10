/**
 * 路由完整性测试（T1b）。
 *
 * 断言依据任务书 §4.6：
 * - 18 个 route 常量互不重复、非空；
 * - 与 `docs/06` §4 的页面清单一致；
 * - 底部导航恰为 `docs/05` §12 的 5 项且顺序一致；
 * - 路由解析（`fromRoute`）可往返。
 */
package com.zhangran.app.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class DestinationsTest {

    @Test
    fun 页面总数为18() {
        assertEquals(18, Destination.entries.size)
    }

    @Test
    fun 路由非空且互不重复() {
        val routes = Destination.entries.map { it.route }
        assertTrue("路由不得为空", routes.none { it.isBlank() })
        assertEquals("路由必须互不重复", routes.size, routes.toSet().size)
    }

    @Test
    fun 页面标题非空() {
        assertTrue("页面标题不得为空", Destination.entries.none { it.title.isBlank() })
        assertEquals(
            "页面标题不得重复",
            Destination.entries.size,
            Destination.entries.map { it.title }.toSet().size,
        )
    }

    @Test
    fun 页面清单与文档一致() {
        // 依据 docs/06 §1 的 18 个子包名（route 与其同名）
        val expected = listOf(
            "home", "stats", "add", "budget", "mine",
            "detail", "pending", "pendingEdit", "duplicateReview",
            "autoLedger", "onboard", "categories", "accounts",
            "rules", "backup", "logs", "search", "about",
        )
        assertEquals(expected.toSet(), Destination.entries.map { it.route }.toSet())
    }

    @Test
    fun 底部导航为5项且顺序符合文档() {
        // 依据 docs/05 §12：首页 / 统计 / 记一笔 / 预算 / 我的
        assertEquals(
            listOf("home", "stats", "add", "budget", "mine"),
            Destination.topLevel.map { it.route },
        )
        assertEquals(5, Destination.topLevel.size)
    }

    @Test
    fun 起始目的地为首页() {
        assertSame(Destination.Home, Destination.start)
    }

    @Test
    fun 路由解析可往返() {
        Destination.entries.forEach { destination ->
            assertSame(destination, Destination.fromRoute(destination.route))
        }
        assertNull("未知名应返回 null", Destination.fromRoute("not-a-route"))
        assertNull("null 应返回 null", Destination.fromRoute(null))
    }
}
