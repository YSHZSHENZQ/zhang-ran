/**
 * 宿主接入契约解析测试（纯 JVM）。
 *
 * 覆盖 T4 §6 的 A4（版本与参数解析）：
 * - 正常 URI（`v=1` + `payload`）
 * - 版本缺失 / 非法 / 不受支持
 * - 载荷缺失 / 空串 / 顺序颠倒 / 多余参数
 * - scheme+host 判定
 */
package com.zhangran.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureContractTest {

    @Test
    fun `完整 URI 解析出版本与载荷`() {
        val params = CaptureContract.parse("zhangran://capture?v=1&payload=eyJhIjoxfQ")
        assertEquals(1, params.version)
        assertEquals("eyJhIjoxfQ", params.payload)
        assertTrue(params.versionSupported)
    }

    @Test
    fun `裸查询串与带问号查询串均可解析`() {
        assertEquals(1, CaptureContract.parse("v=1&payload=AAA").version)
        assertEquals(1, CaptureContract.parse("?v=1&payload=AAA").version)
        assertEquals("AAA", CaptureContract.parse("v=1&payload=AAA").payload)
    }

    @Test
    fun `参数顺序颠倒仍可解析`() {
        val params = CaptureContract.parse("zhangran://capture?payload=AAA&v=1")
        assertEquals(1, params.version)
        assertEquals("AAA", params.payload)
    }

    @Test
    fun `多余参数被忽略`() {
        val params = CaptureContract.parse("zhangran://capture?v=1&payload=AAA&foo=bar&ts=123")
        assertEquals(1, params.version)
        assertEquals("AAA", params.payload)
    }

    @Test
    fun `版本缺失为 VERSION_MISSING 且不受支持`() {
        val params = CaptureContract.parse("zhangran://capture?payload=AAA")
        assertEquals(VERSION_MISSING, params.version)
        assertFalse(params.versionSupported)
    }

    @Test
    fun `版本非整数为 VERSION_INVALID 且不受支持`() {
        listOf("abc", "1.0", "", " ").forEach { bad ->
            val params = CaptureContract.parse("zhangran://capture?v=$bad&payload=AAA")
            assertFalse("v=$bad 不应受支持", params.versionSupported)
        }
        assertEquals(VERSION_INVALID, CaptureContract.parse("zhangran://capture?v=abc").version)
        // 空值会被 parseVersion 视为缺失
        assertEquals(VERSION_MISSING, CaptureContract.parse("zhangran://capture?v=").version)
    }

    @Test
    fun `其它版本号不受支持`() {
        listOf(0, 2, 99, -1).forEach { v ->
            val params = CaptureContract.parse("zhangran://capture?v=$v&payload=AAA")
            assertFalse("v=$v 不应受支持", params.versionSupported)
        }
    }

    @Test
    fun `载荷缺失或空串视为无载荷`() {
        assertNull(CaptureContract.parse("zhangran://capture?v=1").payload)
        assertNull(CaptureContract.parse("zhangran://capture?v=1&payload=").payload)
        assertNull(CaptureContract.parse("zhangran://capture?v=1&payload=   ").payload)
    }

    @Test
    fun `空输入与非法输入不崩溃`() {
        listOf(null, "", "   ", "zhangran://capture", "not a uri", "///").forEach { input ->
            val params = CaptureContract.parse(input)
            assertFalse("输入「$input」应为不受支持", params.versionSupported)
        }
    }

    @Test
    fun `isCaptureUri 只认 scheme 加 host`() {
        assertTrue(CaptureContract.isCaptureUri("zhangran://capture?v=1"))
        assertTrue(CaptureContract.isCaptureUri("ZHANGRAN://CAPTURE?v=1"))
        assertFalse(CaptureContract.isCaptureUri("zhangran://other?v=1"))
        assertFalse(CaptureContract.isCaptureUri("https://capture?v=1"))
        assertFalse(CaptureContract.isCaptureUri(null))
        assertFalse(CaptureContract.isCaptureUri(""))
    }

    @Test
    fun `当前支持版本为 1`() {
        assertEquals(1, SUPPORTED_PAYLOAD_VERSION)
        assertEquals("zhangran", CAPTURE_SCHEME)
        assertEquals("capture", CAPTURE_HOST)
    }
}
