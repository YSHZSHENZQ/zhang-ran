/**
 * 关于页文案断言（`M4d` §3/A11；防止后人写回禁用措辞或示例版本）。
 *
 * 纯 JVM：只校验 [AboutInfo] 的静态文案。
 */
package com.zhangran.app.ui.screen.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutInfoTest {

    @Test
    fun A3_隐私四条逐字且顺序固定() {
        assertEquals(
            listOf(
                "所有数据仅存储在本地",
                "INTERNET 仅用于与本机识别服务的回环通信，账务数据不出本机",
                "不收集、不上传任何信息",
                "无障碍服务只读取可见文本",
            ),
            AboutInfo.PRIVACY_LINES,
        )
    }

    @Test
    fun A3_不含任何禁用措辞() {
        val allText = AboutInfo.allText()
        listOf("不申请 INTERNET", "不申请网络权限", "整个方案不联网", "完全离线", "零网络")
            .forEach { forbidden ->
                assertFalse("不得出现「$forbidden」", allText.contains(forbidden))
            }
    }

    @Test
    fun A4_开源信息含来源commit与GPL() {
        assertTrue(AboutInfo.LICENSE_SUMMARY.contains("GPL-3.0"))
        assertTrue(AboutInfo.LICENSE_SUMMARY.contains("仅个人自用"))
        assertTrue(AboutInfo.LICENSE_SUMMARY.contains("未声明开源许可"))

        assertEquals("AutoAccounting", AboutInfo.CAPTURE_CORE_NAME)
        assertTrue(AboutInfo.CAPTURE_CORE_REPOSITORY.contains("AutoAccounting"))
        assertEquals(7, AboutInfo.CAPTURE_CORE_COMMIT.length)
        assertTrue(AboutInfo.CAPTURE_CORE_COMMIT.all { it in "0123456789abcdef" })
        assertEquals("GPL-3.0", AboutInfo.CAPTURE_CORE_LICENSE)
    }

    @Test
    fun A5_不含三个网络入口名称与原型示例版本() {
        val allText = AboutInfo.allText()
        listOf("GitHub 仓库", "反馈与建议", "检查更新", "版本 1.0.0").forEach { forbidden ->
            assertFalse("不得出现「$forbidden」", allText.contains(forbidden))
        }
    }

    @Test
    fun 标语与版本前缀正确() {
        assertEquals("账然", AboutInfo.APP_NAME)
        assertEquals("不主动，不打扰", AboutInfo.SLOGAN_LINE_1)
        assertEquals("账目自然清晰", AboutInfo.SLOGAN_LINE_2)
        assertEquals("版本 ", AboutInfo.VERSION_PREFIX)
    }

    private fun AboutInfo.allText(): String = buildString {
        append(APP_NAME)
        append(SLOGAN_LINE_1)
        append(SLOGAN_LINE_2)
        append(PRIVACY_LINES.joinToString())
        append(LICENSE_SUMMARY)
        append(LICENSE_ROW_DESCRIPTION)
        append(CAPTURE_CORE_NAME)
        append(CAPTURE_CORE_REPOSITORY)
        append(CAPTURE_CORE_COMMIT)
        append(CAPTURE_CORE_LICENSE)
        append(CAPTURE_CORE_ROW_DESCRIPTION)
        append(CAPTURE_CORE_SUMMARY)
    }
}
