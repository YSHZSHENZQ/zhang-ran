/**
 * 解析规则关键词集合编解码测试（`docs/02` §16 的 canonical JSON 数组契约）。
 *
 * 覆盖 T2b §6 的 A7：序列化稳定性（往返逐字节相同）、转义覆盖、归一化、
 * 去重保序、解析失败不得静默当空集合。
 */
package com.zhangran.app.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleListCodecTest {

    private fun success(result: RuleListResult): List<String> =
        (result as? RuleListResult.Success)?.items
            ?: throw AssertionError("期望解析成功，实际=$result")

    // ---------- canonical 形态 ----------

    @Test
    fun `canonical 编码不带多余空白且中文不转义`() {
        assertEquals("""["付款成功","支付成功"]""", RuleListCodec.encode(listOf("付款成功", "支付成功")))
    }

    @Test
    fun `空集合编码为方括号`() {
        assertEquals("[]", RuleListCodec.encode(emptyList()))
    }

    @Test
    fun `解析后再序列化逐字节相同`() {
        val cases = listOf(
            """["付款成功","支付成功"]""",
            """[]""",
            """["a"]""",
            """["含,逗号","含|竖线","含\\反斜杠","含\"引号"]""",
            """["含\n换行","含\t制表"]""",
        )
        cases.forEach { canonical ->
            val items = success(RuleListCodec.decode(canonical))
            assertEquals("往返必须逐字节相同：$canonical", canonical, RuleListCodec.encode(items))
        }
    }

    @Test
    fun `重复编码结果稳定`() {
        val items = listOf("微信", "支付宝")
        assertEquals(RuleListCodec.encode(items), RuleListCodec.encode(items))
    }

    // ---------- 转义覆盖 ----------

    @Test
    fun `转义字符可正确往返`() {
        val items = listOf(
            "含\"引号",
            "含\\反斜杠",
            "含\n换行",
            "含\r回车",
            "含\t制表",
            "含/u0000控制符",
        )
        val encoded = RuleListCodec.encode(items)
        assertEquals("转义后必须能原样解析回来", items, success(RuleListCodec.decode(encoded)))
    }

    @Test
    fun `正则模式内容可安全存储`() {
        // matchMode = REGEX 时，正则本身含 , | ; ( ) \ 等分隔符方案会撞车的字符
        val regex = """^付款(成功|完成),\s*\d+\.\d{2}$"""
        val encoded = RuleListCodec.encode(listOf(regex))
        assertEquals(listOf(regex), success(RuleListCodec.decode(encoded)))
    }

    // ---------- 归一化 ----------

    @Test
    fun `去重并保留首次出现顺序`() {
        val items = success(RuleListCodec.decode("""["b","a","b","c","a"]"""))
        assertEquals(listOf("b", "a", "c"), items)
    }

    @Test
    fun `忽略纯空串项且空串数组归一为方括号`() {
        assertEquals(listOf("a"), success(RuleListCodec.decode("""["","a",""]""")))
        assertEquals(emptyList<String>(), success(RuleListCodec.decode("""[""]""")))
        assertEquals("[]", RuleListCodec.encode(success(RuleListCodec.decode("""[""]"""))))
    }

    @Test
    fun `每项保留原样不做 trim`() {
        // 大小写与全半角等价由匹配层负责，存储层不得规范化
        assertEquals(listOf(" 带空格 ", "ABC", "ａｂｃ"), success(RuleListCodec.decode("""[" 带空格 ","ABC","ａｂｃ"]""")))
    }

    @Test
    fun `空白与换行不影响解析`() {
        assertEquals(listOf("a", "b"), success(RuleListCodec.decode("  [ \"a\" ,\n  \"b\" ]  ")))
    }

    // ---------- 解析失败必须显式失败 ----------

    @Test
    fun `非法输入解析失败而不是空集合`() {
        val invalids = listOf(
            null,
            "",
            "   ",
            "付款成功",
            "[付款成功]",
            """["未闭合""",
            """["a"]]""",
            """["a",]""",
            """["a"...""",
            """{"a":1}""",
            """["a"] extra""",
            """["\x"]""",
            """["\u12"]""",
        )
        invalids.forEach { input ->
            val result = RuleListCodec.decode(input)
            assertTrue("输入「$input」必须解析失败，实际=$result", result is RuleListResult.Failure)
        }
    }

    @Test
    fun `失败结果带可读原因`() {
        val failure = RuleListCodec.decode("付款成功") as RuleListResult.Failure
        assertTrue("失败原因不得为空", failure.reason.isNotBlank())
    }
}
