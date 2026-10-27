/**
 * 首页分组、图标映射与金额格式化测试（`M2e-1b` §4.4 / §4.1 / §4.3，对应验收 A4/A5/A7/A8）。
 *
 * 为什么是纯 JVM：分组与格式化都在 `HomeViewModel` 的纯函数里完成（不在 Composable 里），
 * 因此可以固定"现在"与时区逐条断言，包括**跨年**与**时区归属**（任务书 §7 R11）。
 */
package com.zhangran.app.ui.screen.home

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.TransactionListEntry
import com.zhangran.app.ui.common.CategoryIconMapper
import com.zhangran.app.ui.theme.IconGradientGroup
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionGroupingTest {

    private companion object {
        const val ZONE = "Asia/Shanghai"

        /** 固定"现在"：2024-06-15 12:00（Asia/Shanghai）。 */
        val NOW: Long = millisOf(2024, 6, 15, 12, 0)

        fun millisOf(
            year: Int,
            month: Int,
            day: Int,
            hour: Int,
            minute: Int,
            second: Int = 0,
        ): Long = LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(ZoneId.of(ZONE))
            .toInstant()
            .toEpochMilli()
    }

    private fun entry(
        id: String,
        time: Long,
        type: TransactionType = TransactionType.EXPENSE,
        amountMinor: Long = 3_850L,
        categoryName: String? = "餐饮",
        categoryIcon: String? = "food",
        source: CaptureSource = CaptureSource.MANUAL,
    ) = TransactionListEntry(
        id = id,
        type = type,
        amountMinor = amountMinor,
        merchant = "星巴克咖啡",
        note = null,
        transactionTime = time,
        categoryName = categoryName,
        categoryIcon = categoryIcon,
        accountName = "现金",
        source = source,
        state = TransactionState.CONFIRMED,
    )

    private fun sectionsOf(entries: List<TransactionListEntry>) =
        HomeViewModel.groupIntoSections(entries, ZONE, NOW)

    // ---------------------------------------------------------------- A4 分组标题

    @Test
    fun A4_今天与昨天与更早日期分别成组() {
        val sections = sectionsOf(
            listOf(
                entry("today", millisOf(2024, 6, 15, 9, 30)),
                entry("yesterday", millisOf(2024, 6, 14, 20, 0)),
                entry("earlier", millisOf(2024, 6, 2, 8, 0)),
            ),
        )
        assertEquals(listOf("今天", "昨天", "6月2日"), sections.map { it.label })
    }

    @Test
    fun A4_跨年时分组标题带年份() {
        // 现在 = 2024-01-05：昨天是 1 月 4 日；2023 年的日期必须带年份
        val now = millisOf(2024, 1, 5, 10, 0)
        val sections = HomeViewModel.groupIntoSections(
            entries = listOf(
                entry("today", millisOf(2024, 1, 5, 9, 0)),
                entry("yesterday", millisOf(2024, 1, 4, 20, 0)),
                entry("last-year", millisOf(2023, 12, 31, 23, 30)),
                entry("old", millisOf(2023, 3, 2, 12, 0)),
            ),
            zoneId = ZONE,
            nowEpochMillis = now,
        )
        assertEquals(
            listOf("今天", "昨天", "2023年12月31日", "2023年3月2日"),
            sections.map { it.label },
        )
    }

    @Test
    fun A4_昨天恰逢跨年时仍显示昨天() {
        // 现在 = 2024-01-01 → 2023-12-31 是"昨天"（优先于"带年份的日期"）
        val now = millisOf(2024, 1, 1, 10, 0)
        val sections = HomeViewModel.groupIntoSections(
            entries = listOf(entry("last-year", millisOf(2023, 12, 31, 23, 30))),
            zoneId = ZONE,
            nowEpochMillis = now,
        )
        assertEquals(listOf("昨天"), sections.map { it.label })
    }

    // ---------------------------------------------------------------- A5 排序

    @Test
    fun A5_组间与组内均按交易时间倒序() {
        val sections = sectionsOf(
            listOf(
                entry("t1", millisOf(2024, 6, 15, 8, 0)),
                entry("t2", millisOf(2024, 6, 15, 18, 0)),
                entry("t3", millisOf(2024, 6, 15, 12, 0)),
                entry("y1", millisOf(2024, 6, 14, 9, 0)),
                entry("old", millisOf(2024, 5, 1, 9, 0)),
            ),
        )
        assertEquals(listOf("今天", "昨天", "5月1日"), sections.map { it.label })
        assertEquals(listOf("t2", "t3", "t1"), sections.first().items.map { it.id })
        assertEquals(listOf("y1"), sections[1].items.map { it.id })
        assertEquals(listOf("old"), sections[2].items.map { it.id })
    }

    @Test
    fun A4_空分组不显示() {
        // 只有两个不同日期 → 只应有两组（不会为"中间空日期"造组）
        val sections = sectionsOf(
            listOf(
                entry("today", millisOf(2024, 6, 15, 9, 0)),
                entry("ancient", millisOf(2024, 1, 1, 9, 0)),
            ),
        )
        assertEquals(2, sections.size)
        assertTrue(sections.all { it.items.isNotEmpty() })
    }

    // ---------------------------------------------------------------- R11 时区

    @Test
    fun R11_分组按设备本地时区而非UTC划分日期() {
        // 2024-06-15 00:30 +08:00 = 2024-06-14 16:30 UTC：
        // 若按 UTC 划分会被算到"昨天"，按设备时区必须算"今天"
        val sections = sectionsOf(listOf(entry("early", millisOf(2024, 6, 15, 0, 30))))
        assertEquals(listOf("今天"), sections.map { it.label })
    }

    @Test
    fun R11_昨天最后一秒仍属于昨天明天第一毫秒属于今天() {
        val todayFirstMillisecond = millisOf(2024, 6, 15, 0, 0)
        val yesterdayLastMillisecond = todayFirstMillisecond - 1
        val sections = sectionsOf(
            listOf(
                entry("last-ms-yesterday", yesterdayLastMillisecond),
                entry("first-ms-today", todayFirstMillisecond),
            ),
        )
        assertEquals(listOf("今天", "昨天"), sections.map { it.label })
    }

    // ---------------------------------------------------------------- A8 图标与渐变

    @Test
    fun A8_所有预置分类标识都能归入渐变组() {
        CategoryIconMapper.supportedIcons.forEach { icon ->
            assertTrue(
                "分类标识 $icon 缺少渐变组映射",
                HomeCategoryVisuals.mappedIcons.contains(icon),
            )
        }
    }

    @Test
    fun A8_未知分类与缺失分类使用默认组与默认emoji() {
        assertEquals(
            HomeCategoryVisuals.DEFAULT_GRADIENT,
            HomeCategoryVisuals.gradientFor("unknown-icon", TransactionType.EXPENSE),
        )
        assertEquals(
            HomeCategoryVisuals.DEFAULT_GRADIENT,
            HomeCategoryVisuals.gradientFor(null, TransactionType.EXPENSE),
        )
        assertEquals(
            CategoryIconMapper.DEFAULT_EMOJI,
            HomeCategoryVisuals.emojiFor(null),
        )
        assertEquals(
            CategoryIconMapper.DEFAULT_EMOJI,
            HomeCategoryVisuals.emojiFor("unknown-icon"),
        )
    }

    @Test
    fun A8_收入类别归紫组且无分类的收入也归紫组() {
        assertEquals(IconGradientGroup.PURPLE, HomeCategoryVisuals.gradientFor("salary", TransactionType.INCOME))
        assertEquals(IconGradientGroup.PURPLE, HomeCategoryVisuals.gradientFor(null, TransactionType.INCOME))
        assertEquals(IconGradientGroup.GREEN, HomeCategoryVisuals.gradientFor("transport", TransactionType.EXPENSE))
        assertEquals(IconGradientGroup.RED, HomeCategoryVisuals.gradientFor("medical", TransactionType.EXPENSE))
    }
}

/**
 * 金额格式化测试（`M2e-1b` §4.3 / A7：千分位 + 固定 2 位小数、**唯一实现**）。
 */
class HomeMoneyFormatTest {

    @Test
    fun A7_千分位与两位小数() {
        assertEquals("0.00", HomeViewModel.formatMoney(0L))
        assertEquals("0.05", HomeViewModel.formatMoney(5L))
        assertEquals("12.34", HomeViewModel.formatMoney(1_234L))
        assertEquals("1,234.56", HomeViewModel.formatMoney(123_456L))
        assertEquals("12,345.67", HomeViewModel.formatMoney(1_234_567L))
    }

    @Test
    fun A7_符号策略与货币符号() {
        assertEquals("120.00", HomeViewModel.formatMoney(12_000L, AmountSign.AUTO, withCurrencySymbol = true).removePrefix("¥"))
        assertEquals("¥120.00", HomeViewModel.formatMoney(12_000L, AmountSign.AUTO, withCurrencySymbol = true))
        assertEquals("+12,000.00", HomeViewModel.formatMoney(1_200_000L, AmountSign.PLUS))
        assertEquals("-1,500.00", HomeViewModel.formatMoney(-150_000L, AmountSign.AUTO))
        assertEquals("-¥1,500.00", HomeViewModel.formatMoney(-150_000L, AmountSign.AUTO, withCurrencySymbol = true))
        assertEquals("120.00", HomeViewModel.formatMoney(12_000L, AmountSign.NONE))
    }

    @Test
    fun A6_交易行金额按类型给符号() {
        assertEquals("-38.50", HomeViewModel.rowAmountOf(TransactionType.EXPENSE, 3_850L))
        assertEquals("+1,200.00", HomeViewModel.rowAmountOf(TransactionType.INCOME, 120_000L))
        assertEquals("-1,200.00", HomeViewModel.rowAmountOf(TransactionType.TRANSFER, 120_000L))
        assertEquals("+1,200.00", HomeViewModel.rowAmountOf(TransactionType.REFUND, 120_000L))
    }

    @Test
    fun 主标题回退顺序为商户备注分类类型() {
        assertEquals("星巴克", HomeViewModel.titleOf("星巴克", "备注", "餐饮", TransactionType.EXPENSE))
        assertEquals("备注", HomeViewModel.titleOf(null, "备注", "餐饮", TransactionType.EXPENSE))
        assertEquals("餐饮", HomeViewModel.titleOf(null, null, "餐饮", TransactionType.EXPENSE))
        assertEquals("支出", HomeViewModel.titleOf(null, null, null, TransactionType.EXPENSE))
    }

    @Test
    fun 行内元信息与无障碍文本由ViewModel算好() {
        val entry = TransactionListEntry(
            id = "tx-1",
            type = TransactionType.EXPENSE,
            amountMinor = 3_850L,
            merchant = "星巴克咖啡",
            note = null,
            transactionTime = LocalDateTime.of(2024, 6, 15, 14, 23)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli(),
            categoryName = "餐饮",
            categoryIcon = "food",
            accountName = "现金",
            source = CaptureSource.MANUAL,
            state = TransactionState.CONFIRMED,
        )
        val sections = HomeViewModel.groupIntoSections(
            entries = listOf(entry),
            zoneId = "Asia/Shanghai",
            nowEpochMillis = LocalDateTime.of(2024, 6, 15, 20, 0)
                .atZone(ZoneId.of("Asia/Shanghai"))
                .toInstant()
                .toEpochMilli(),
        )
        val item = sections.single().items.single()
        assertEquals("14:23", item.timeText)
        assertEquals("手工", item.sourceText)
        assertEquals("餐饮", item.categoryName)
        assertEquals("food", item.categoryIcon)
        assertEquals("星巴克咖啡，餐饮，14:23，支出 38.50 元", item.accessibilityText)
    }
}
