/**
 * 主题 token 完整性测试（T1b，纯 JVM）。
 *
 * 断言依据任务书 §4.6 与 `docs/05` §3–§6：
 * - `docs/05` §3 的 14 个颜色 token 均有定义；
 * - `docs/05` §4 的 7 个排版 token 均有定义且字号 / 行高为正；
 * - `docs/05` §5 的 6 个间距 token 均有定义且为正；
 * - `docs/05` §6 的 5 个圆角 token 均有定义且非负；
 * - 明暗两套颜色 token 键集合一致。
 *
 * 说明：token 真值为纯数据（int / 数据类），因此可在纯 JVM 单测中直接断言。
 */
package com.zhangran.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTokensTest {

    @Test
    fun 颜色token覆盖文档要求的14项() {
        val required = listOf(
            "Background", "Surface", "SurfaceVariant", "Primary", "Secondary",
            "TextPrimary", "TextSecondary", "TextDisabled", "Divider",
            "Expense", "Income", "Transfer", "Warning", "Error",
        )
        val actual = LightColors.asTokenMap()
        assertEquals("颜色 token 数量不符", required.size, actual.size)
        required.forEach { name ->
            assertTrue("缺少颜色 token: $name", actual.containsKey(name))
        }
    }

    @Test
    fun 明暗颜色token键集合一致() {
        assertEquals(LightColors.asTokenMap().keys, DarkColors.asTokenMap().keys)
    }

    @Test
    fun 颜色token取值非零() {
        listOf("Light" to LightColors, "Dark" to DarkColors).forEach { (label, colors) ->
            colors.asTokenMap().forEach { (name, value) ->
                assertTrue("$label.$name 取值不应为 0（未定义）", value != 0)
            }
        }
    }

    @Test
    fun 排版token覆盖文档要求的7项且数值有效() {
        val actual = TypographyTokens.asTokenMap()
        val expected = listOf("Display", "Headline", "Title", "Body", "Label", "Caption", "Amount")
        assertEquals(expected.toSet(), actual.keys)
        actual.forEach { (name, token) ->
            assertTrue("$name 字号必须为正", token.fontSizeSp > 0)
            assertTrue("$name 行高必须不小于字号", token.lineHeightSp >= token.fontSizeSp)
        }
    }

    @Test
    fun 金额使用专用排版token() {
        // docs/05 §4：金额必须有专用 Typography
        val amount = TypographyTokens.asTokenMap()["Amount"]
        assertTrue("Amount token 必须存在", amount != null)
        assertTrue("Amount 必须是强调字重", amount!!.fontWeight == ZhangRanFontWeight.BOLD)
    }

    @Test
    fun 间距token覆盖文档要求的6项且为正() {
        val actual = Spacing.asTokenMap()
        assertEquals(listOf("xs", "sm", "md", "lg", "xl", "xxl"), actual.keys.toList())
        actual.forEach { (name, value) -> assertTrue("间距 $name 必须为正", value > 0) }
        assertTrue("间距应递增", actual.values.zipWithNext().all { (a, b) -> a < b })
    }

    @Test
    fun 圆角token覆盖文档要求的5项且非负() {
        val actual = Radii.asTokenMap()
        assertEquals(listOf("card", "dialog", "button", "input", "chip"), actual.keys.toList())
        actual.forEach { (name, value) -> assertTrue("圆角 $name 不得为负", value >= 0) }
    }
}
