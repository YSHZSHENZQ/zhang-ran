/**
 * 分类图标映射的完整性断言（任务书 M2a 的 A10，纯 JVM）。
 *
 * 断言：
 * - P-009 的 17 个语义标识（12 支出 + 5 收入，两个「其他」共用 `other`）全部有映射；
 * - 未知 / 缺失标识返回默认 emoji（不崩溃、不返回空）；
 * - 映射结果都是 emoji（非 ASCII），且映射值不含空串。
 */
package com.zhangran.app.ui.common

import com.zhangran.app.data.local.DEFAULT_EXPENSE_CATEGORIES
import com.zhangran.app.data.local.DEFAULT_INCOME_CATEGORIES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryIconMapperTest {

    @Test
    fun A10_初始清单里的每个语义标识都有映射() {
        val allIcons = (DEFAULT_EXPENSE_CATEGORIES + DEFAULT_INCOME_CATEGORIES)
            .map { it.icon }
            .toSet()

        allIcons.forEach { icon ->
            assertTrue(
                "语义标识 `$icon` 缺少 emoji 映射（CategoryIconMapper）",
                CategoryIconMapper.supportedIcons.contains(icon),
            )
        }
    }

    @Test
    fun A10_映射覆盖17个语义标识() {
        // 12 支出 + 6 收入，其中「其他」两处共用 `other` → 17 个不同标识
        assertEquals(17, CategoryIconMapper.supportedIcons.size)
    }

    @Test
    fun A10_文档列出的映射逐项一致() {
        val expected = mapOf(
            "food" to "🍜",
            "transport" to "🚇",
            "shopping" to "🛍",
            "housing" to "🏠",
            "entertainment" to "🎮",
            "medical" to "💊",
            "education" to "📚",
            "communication" to "📱",
            "gift" to "🎁",
            "travel" to "✈️",
            "pet" to "🐾",
            "other" to "📦",
            "salary" to "💰",
            "bonus" to "🎉",
            "investment" to "📈",
            "parttime" to "💼",
            "redpacket" to "🧧",
        )
        expected.forEach { (icon, emoji) ->
            assertEquals("`$icon` 的 emoji 不符", emoji, CategoryIconMapper.toEmoji(icon))
        }
    }

    @Test
    fun A10_未知标识返回默认emoji且不崩溃() {
        assertEquals(CategoryIconMapper.DEFAULT_EMOJI, CategoryIconMapper.toEmoji("unknown_icon"))
        assertEquals(CategoryIconMapper.DEFAULT_EMOJI, CategoryIconMapper.toEmoji(""))
        assertEquals(CategoryIconMapper.DEFAULT_EMOJI, CategoryIconMapper.toEmoji(null))
        assertFalse("默认 emoji 不得为空", CategoryIconMapper.DEFAULT_EMOJI.isBlank())
    }

    @Test
    fun A10_映射值均为非空emoji() {
        CategoryIconMapper.mappings.forEach { (icon, emoji) ->
            assertTrue("`$icon` 的映射值为空", emoji.isNotBlank())
            assertTrue(
                "`$icon` 的映射值看起来不是 emoji：$emoji",
                emoji.codePoints().anyMatch { it > 0x2000 },
            )
        }
    }

    @Test
    fun A10_语义标识本身不是emoji() {
        // 存储层只允许语义标识（小写字母/下划线），emoji 不得进入数据库
        CategoryIconMapper.supportedIcons.forEach { icon ->
            assertTrue(
                "语义标识 `$icon` 必须是 ASCII 小写标识",
                icon.all { it in 'a'..'z' || it == '_' },
            )
        }
    }
}
