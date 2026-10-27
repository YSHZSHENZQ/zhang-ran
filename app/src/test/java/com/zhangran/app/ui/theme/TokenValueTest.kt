/**
 * 主题 token 取值断言（UI-0 的 A1–A6，纯 JVM）。
 *
 * 每个期望值都直接来自 `prototype/账然_UI原型.html` 的 CSS 变量：
 * 亮色 token 块 L42–L63、暗色 L65–L88；字号/圆角/间距来自对应选择器（见 `review/UI-0-token对照表.md`）。
 *
 * 与既有的 `ThemeTokensTest` 分工：
 * - `ThemeTokensTest` 只校验"`docs/05` 规定的 token 数量与键集合"（14/7/6/5 项）；
 * - 本测试校验**具体取值**（含扩展 token），因此用 `asExtendedTokenMap()`。
 */
package com.zhangran.app.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TokenValueTest {

    // ------------------------------------------------------------------ A1 亮色取值

    @Test
    fun A1_亮色基础token取值与原型一致() {
        val c = LightColors
        assertEquals("--bg-1", 0xFFEEF3FF.toInt(), c.background)
        assertEquals("--glass-bg", 0x8CFFFFFF.toInt(), c.surface)
        assertEquals("--row-bg", 0xB3FFFFFF.toInt(), c.surfaceVariant)
        assertEquals("--accent-1", 0xFF4F46E5.toInt(), c.primary)
        assertEquals("--accent-2", 0xFF06B6D4.toInt(), c.secondary)
        assertEquals("--text", 0xFF1A2233.toInt(), c.textPrimary)
        assertEquals("--text-2", 0xFF475569.toInt(), c.textSecondary)
        assertEquals("--text-3", 0xFF94A3B8.toInt(), c.textDisabled)
        assertEquals("--divider", 0x0D000000, c.divider)
        assertEquals("--red", 0xFFEF4444.toInt(), c.expense)
        assertEquals("--green", 0xFF10B981.toInt(), c.income)
        assertEquals("--accent-2（transfer 取次强调色）", 0xFF06B6D4.toInt(), c.transfer)
        assertEquals("--amber", 0xFFF59E0B.toInt(), c.warning)
        assertEquals("--red（error）", 0xFFEF4444.toInt(), c.error)
    }

    @Test
    fun A1_亮色扩展token取值与原型一致() {
        val c = LightColors
        assertEquals("--bg-2", 0xFFF7F0FF.toInt(), c.backgroundGradientMid1)
        assertEquals("--bg-3", 0xFFE8F6FF.toInt(), c.backgroundGradientMid2)
        assertEquals("--bg-4", 0xFFECFDF7.toInt(), c.backgroundGradientEnd)
        assertEquals("--glass-border", 0xEBFFFFFF.toInt(), c.glassBorder)
        assertEquals("--row-bg", 0xB3FFFFFF.toInt(), c.rowFill)
        assertEquals("--input-bg", 0xB3FFFFFF.toInt(), c.inputFill)
        assertEquals("--modal-bg", 0xF5FFFFFF.toInt(), c.modalFill)
        assertEquals("--overlay", 0x660A0E14, c.scrim)
        assertEquals("--glow-1", 0x524F46E5, c.glow1)
        assertEquals("--glow-2", 0x4706B6D4, c.glow2)
        assertEquals("--glow-3", 0x38A855F7, c.glow3)
        assertEquals("--accent-grad 起点", 0xFF4F46E5.toInt(), c.accentGradientStart)
        assertEquals("--accent-grad 终点", 0xFF06B6D4.toInt(), c.accentGradientEnd)
        assertEquals("--glass-shadow 外阴影色", 0x1A4F46E5, c.glassShadowColor)
        assertEquals("--glass-shadow 内高光", 0xBFFFFFFF.toInt(), c.glassInnerHighlight)
    }

    // ------------------------------------------------------------------ A2 暗色取值

    @Test
    fun A2_暗色基础token取值与原型一致() {
        val c = DarkColors
        assertEquals("--bg-1", 0xFF070A12.toInt(), c.background)
        assertEquals("--glass-bg", 0x12FFFFFF, c.surface)
        assertEquals("--row-bg", 0x12FFFFFF, c.surfaceVariant)
        assertEquals("--accent-1", 0xFF00F0FF.toInt(), c.primary)
        assertEquals("--accent-2", 0xFF7C5CFF.toInt(), c.secondary)
        assertEquals("--text", 0xFFE6EDF7.toInt(), c.textPrimary)
        assertEquals("--text-2", 0xFF7F8B9B.toInt(), c.textSecondary)
        assertEquals("--text-3", 0xFF4D6480.toInt(), c.textDisabled)
        assertEquals("--divider", 0x0FFFFFFF, c.divider)
        assertEquals("--red", 0xFFFF6B81.toInt(), c.expense)
        assertEquals("--green", 0xFF00FFB3.toInt(), c.income)
        assertEquals("--accent-2（transfer）", 0xFF7C5CFF.toInt(), c.transfer)
        assertEquals("--amber", 0xFFFFB84D.toInt(), c.warning)
        assertEquals("--red（error）", 0xFFFF6B81.toInt(), c.error)
    }

    @Test
    fun A2_暗色扩展token取值与原型一致() {
        val c = DarkColors
        assertEquals("--bg-2", 0xFF0C1322.toInt(), c.backgroundGradientMid1)
        assertEquals("--bg-3", 0xFF08141A.toInt(), c.backgroundGradientMid2)
        assertEquals("--bg-4", 0xFF08141A.toInt(), c.backgroundGradientEnd)
        assertEquals("--glass-border", 0x1CFFFFFF, c.glassBorder)
        assertEquals("--row-bg", 0x12FFFFFF, c.rowFill)
        assertEquals("--input-bg", 0x14FFFFFF, c.inputFill)
        assertEquals("--modal-bg", 0xF5141A24.toInt(), c.modalFill)
        assertEquals("--overlay", 0x99000000.toInt(), c.scrim)
        assertEquals("--glow-1", 0x6B00F0FF, c.glow1)
        assertEquals("--glow-2", 0x667C5CFF, c.glow2)
        assertEquals("--glow-3", 0x3800FFAA, c.glow3)
        assertEquals("--accent-grad 起点", 0xFF00F0FF.toInt(), c.accentGradientStart)
        assertEquals("--accent-grad 终点", 0xFF7C5CFF.toInt(), c.accentGradientEnd)
        assertEquals("--glass-shadow 外阴影色", 0x59000000, c.glassShadowColor)
        assertEquals("--glass-shadow 内高光", 0x14FFFFFF, c.glassInnerHighlight)
    }

    // ------------------------------------------------------------------ A3 alpha

    @Test
    fun A3_带透明度的token保留alpha() {
        // 原型 --glass-bg 亮色 rgba(255,255,255,0.55) → round(0.55 * 255) = 140 = 0x8C
        assertEquals(140, (LightColors.surface ushr 24) and 0xFF)
        // --glass-bg 暗色 0.07 → round(17.85) = 18 = 0x12
        assertEquals(18, (DarkColors.surface ushr 24) and 0xFF)
        // --glass-border 亮 0.92 → 235 = 0xEB；暗 0.11 → 28 = 0x1C
        assertEquals(235, (LightColors.glassBorder ushr 24) and 0xFF)
        assertEquals(28, (DarkColors.glassBorder ushr 24) and 0xFF)
        // --overlay 亮 0.4 → 102 = 0x66；暗 0.6 → 153 = 0x99
        assertEquals(102, (LightColors.scrim ushr 24) and 0xFF)
        assertEquals(153, (DarkColors.scrim ushr 24) and 0xFF)
        // 玻璃填充不得被预乘成不透明
        assertNotNull(LightColors.surface)
        assertEquals("亮色玻璃填充必须半透明", true, (LightColors.surface ushr 24) and 0xFF < 255)
        assertEquals("暗色玻璃填充必须半透明", true, (DarkColors.surface ushr 24) and 0xFF < 255)
    }

    // ------------------------------------------------------------------ A4 暗色无顶部强调线

    @Test
    fun A4_暗色不绘制卡片顶部强调线() {
        assertNull("--card-top: none → start", DarkColors.cardTopAccentStart)
        assertNull("--card-top: none → mid", DarkColors.cardTopAccentMid)
        assertNull("--card-top: none → end", DarkColors.cardTopAccentEnd)
        assertNull("cardTopAccentColors 必须为 null", DarkColors.cardTopAccentColors)
    }

    @Test
    fun A4_亮色卡片顶部强调线为三色渐变() {
        assertEquals(0xFF4F46E5.toInt(), LightColors.cardTopAccentStart)
        assertEquals(0xFF06B6D4.toInt(), LightColors.cardTopAccentMid)
        assertEquals(0xFF10B981.toInt(), LightColors.cardTopAccentEnd)
        assertEquals(3, LightColors.cardTopAccentColors?.size)
    }

    // ------------------------------------------------------------------ A5 字号 / 行高

    @Test
    fun A5_排版字号与行高与原型一致() {
        val t = TypographyTokens
        assertEquals("Display = .amount-display 42px", 42f, t.display.fontSizeSp, 0.001f)
        assertEquals("Display 行高（未声明，×1.2）", 50f, t.display.lineHeightSp, 0.001f)
        assertEquals("Headline = .page-title 17px", 17f, t.headline.fontSizeSp, 0.001f)
        assertEquals("Headline 行高", 20f, t.headline.lineHeightSp, 0.001f)
        assertEquals("Title = .tx-name 13.5px", 13.5f, t.title.fontSizeSp, 0.001f)
        assertEquals("Title 行高", 16f, t.title.lineHeightSp, 0.001f)
        assertEquals("Body = .detail-note 13px", 13f, t.body.fontSizeSp, 0.001f)
        assertEquals("Body 行高（声明 1.7）", 22f, t.body.lineHeightSp, 0.001f)
        assertEquals("Label = .card-label 10.5px", 10.5f, t.label.fontSizeSp, 0.001f)
        assertEquals("Label 行高", 13f, t.label.lineHeightSp, 0.001f)
        assertEquals("Caption = .stat-k 9.5px", 9.5f, t.caption.fontSizeSp, 0.001f)
        assertEquals("Caption 行高", 11f, t.caption.lineHeightSp, 0.001f)
        assertEquals("Amount = .tx-amt 14px", 14f, t.amount.fontSizeSp, 0.001f)
        assertEquals("Amount 行高", 17f, t.amount.lineHeightSp, 0.001f)
    }

    @Test
    fun A5_字重与原型一致() {
        val t = TypographyTokens
        assertEquals("Display 700", ZhangRanFontWeight.BOLD, t.display.fontWeight)
        assertEquals("Headline 700", ZhangRanFontWeight.BOLD, t.headline.fontWeight)
        assertEquals("Title 600", ZhangRanFontWeight.SEMI_BOLD, t.title.fontWeight)
        assertEquals("Body 400", ZhangRanFontWeight.NORMAL, t.body.fontWeight)
        assertEquals("Label 400", ZhangRanFontWeight.NORMAL, t.label.fontWeight)
        assertEquals("Caption 400", ZhangRanFontWeight.NORMAL, t.caption.fontWeight)
        assertEquals("Amount 700", ZhangRanFontWeight.BOLD, t.amount.fontWeight)
    }

    @Test
    fun A5_字间距与原型一致() {
        assertEquals(".amount-display letter-spacing -2px", -2f, TypographyTokens.display.letterSpacingSp, 0.001f)
        assertEquals(".page-title letter-spacing .5px", 0.5f, TypographyTokens.headline.letterSpacingSp, 0.001f)
        assertEquals(".card-label letter-spacing 2px", 2f, TypographyTokens.label.letterSpacingSp, 0.001f)
        assertEquals(".tx-amt letter-spacing -.3px", -0.3f, TypographyTokens.amount.letterSpacingSp, 0.001f)
    }

    // ------------------------------------------------------------------ A6 圆角 / 间距

    @Test
    fun A6_圆角token与原型一致() {
        val r = Radii
        assertEquals("card = .card 20px", 20, r.card)
        assertEquals("dialog = 弹层 22px", 22, r.dialog)
        assertEquals("button = .onboard-btn 16px", 16, r.button)
        assertEquals("input = .modal-input 13px", 13, r.input)
        assertEquals("chip = .chip 20px", 20, r.chip)
        assertEquals("screen = .screen 36px", 36, r.screen)
        assertEquals("badge = .badge-* 5px", 5, r.badge)
        assertEquals("option = .modal-option 12px", 12, r.option)
        assertEquals("handle = .modal-handle 2px", 2, r.handle)
        assertEquals("tile = .head-icon 14px", 14, r.tile)
    }

    @Test
    fun A6_间距token与原型一致() {
        val s = Spacing
        assertEquals("xs = 4px", 4, s.xs)
        assertEquals("sm = 8px", 8, s.sm)
        assertEquals("md = 12px", 12, s.md)
        assertEquals("lg = 16px", 16, s.lg)
        assertEquals("xl = 页面水平 22px", 22, s.xl)
        assertEquals("xxl = 32px", 32, s.xxl)
        assertEquals("xxs = 2px", 2, s.xxs)
        assertEquals("gapTight = 10px", 10, s.gapTight)
        assertEquals("pagePadding = 22px", 22, s.pagePadding)
    }

    @Test
    fun A6_六个主间距刻度严格递增() {
        val values = Spacing.asTokenMap().values.toList()
        assertEquals("间距应递增", true, values.zipWithNext().all { (a, b) -> a < b })
    }

    // ------------------------------------------------------------------ 结构性断言

    @Test
    fun 明暗两套token的扩展键集合一致() {
        assertEquals(
            LightColors.asExtendedTokenMap().keys,
            DarkColors.asExtendedTokenMap().keys,
        )
    }

    @Test
    fun 扩展token不破坏基础token数量() {
        // 既有 ThemeTokensTest 以"恰好 14 项"作为完整性判据，扩展 token 必须走独立映射
        assertEquals(14, LightColors.asTokenMap().size)
        assertEquals(29, LightColors.asExtendedTokenMap().size)
        assertEquals(6, Spacing.asTokenMap().size)
        assertEquals(9, Spacing.asExtendedTokenMap().size)
        assertEquals(5, Radii.asTokenMap().size)
        assertEquals(10, Radii.asExtendedTokenMap().size)
    }

    @Test
    fun 玻璃效果token与原型一致() {
        val g = ZhangRanGlassTokens
        assertEquals("backdrop blur 26px", 26, g.blurRadiusDp)
        assertEquals("saturate 1.4", 1.4f, g.saturation, 0.001f)
        assertEquals("border 1px", 1, g.borderWidthDp)
        assertEquals("box-shadow Y 偏移 8px", 8, g.shadowOffsetYDp)
        assertEquals("box-shadow blur 32px", 32, g.shadowBlurDp)
        assertEquals("inset 高光 1px", 1, g.innerHighlightOffsetYDp)
    }
}
