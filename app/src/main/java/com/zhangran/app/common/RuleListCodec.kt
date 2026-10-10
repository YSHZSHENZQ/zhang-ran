/**
 * 解析规则关键词集合（`include` / `exclude`）的存储编解码。
 *
 * SSOT：`docs/02` §16「`include` / `exclude` 的存储形态为 **JSON 数组字符串（canonical）**」。
 *
 * 契约（逐条对齐 `02` §16）：
 * - 输出必须是**唯一 canonical 形态**，解析后再序列化**逐字节相同**（否则每次保存都会产生"假更新"）；
 * - UTF-8、无多余空白、保持插入顺序、**不转义非 ASCII**（中文直接写出）；
 * - 解析时**去重并保留首次出现顺序**；忽略纯空字符串项；`[""]` 归一为 `[]`；
 * - 每项**保留原样、不 trim**（大小写与全半角等价由匹配层负责）；
 * - **解析失败必须显式失败**（不得静默当作空集合——那会让规则变成"匹配一切"），
 *   由调用方把该规则视为不可用并留日志。
 *
 * 实现说明：手写最小 JSON 数组编解码器（不引入 Gson/JSONObject 等依赖，`T2b` §9 禁止新增依赖），
 * 且为纯 Kotlin/JVM，可在 JVM 单测中完整覆盖。
 */
package com.zhangran.app.common

/**
 * 关键词集合解析结果：成功（已归一化）或失败（含原因）。
 */
sealed interface RuleListResult {
    /** 解析成功，[items] 为归一化后的关键词（去重保序、已丢弃纯空串）。 */
    data class Success(val items: List<String>) : RuleListResult

    /** 解析失败；携带原因供日志使用。 */
    data class Failure(val reason: String) : RuleListResult
}

/**
 * 关键词集合编解码器。
 */
object RuleListCodec {

    /**
     * 序列化为 canonical JSON 数组字符串。
     *
     * @param items 关键词集合（顺序即存储顺序）
     */
    fun encode(items: List<String>): String = buildString {
        append('[')
        items.forEachIndexed { index, item ->
            if (index > 0) append(',')
            appendQuoted(item)
        }
        append(']')
    }

    /**
     * 解析 JSON 数组字符串并按 `02` §16 归一化。
     *
     * 归一化：去重（保留首次出现顺序）、丢弃纯空字符串项。
     * 失败（不是数组、含未终止字符串、非法转义、尾随垃圾等）返回 [RuleListResult.Failure]。
     */
    fun decode(text: String?): RuleListResult {
        if (text == null) return RuleListResult.Failure("关键词集合为空（null）")
        val items = try {
            Parser(text).parseArray()
        } catch (e: RuleListFormatException) {
            return RuleListResult.Failure(e.message ?: "关键词集合不是合法的 JSON 数组字符串")
        }

        // 归一化：丢弃纯空串 → 去重保序
        val seen = LinkedHashSet<String>()
        items.forEach { item ->
            if (item.isNotEmpty()) seen.add(item)
        }
        return RuleListResult.Success(seen.toList())
    }

    /** 追加一个 JSON 字符串字面量（不转义非 ASCII）。 */
    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (ch < ' ') {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
        append('"')
    }

    /** 格式错误（解析失败）。 */
    private class RuleListFormatException(message: String) : Exception(message)

    /**
     * 最小 JSON 数组解析器（只接受字符串数组）。
     */
    private class Parser(private val text: String) {
        private var pos = 0

        fun parseArray(): List<String> {
            skipWhitespace()
            expect('[')
            skipWhitespace()
            val items = mutableListOf<String>()
            if (peek() == ']') {
                pos++
                finish()
                return items
            }
            while (true) {
                skipWhitespace()
                items.add(parseString())
                skipWhitespace()
                when (val ch = peek()) {
                    ',' -> {
                        pos++
                        continue
                    }
                    ']' -> {
                        pos++
                        break
                    }
                    null -> throw RuleListFormatException("JSON 数组未闭合")
                    else -> throw RuleListFormatException("JSON 数组中出现意外字符 '$ch'")
                }
            }
            finish()
            return items
        }

        private fun parseString(): String {
            if (peek() != '"') throw RuleListFormatException("数组元素必须是字符串")
            pos++
            val sb = StringBuilder()
            while (true) {
                val ch = peek() ?: throw RuleListFormatException("字符串未闭合")
                pos++
                when {
                    ch == '"' -> return sb.toString()
                    ch == '\\' -> sb.append(parseEscape())
                    ch < ' ' -> throw RuleListFormatException("字符串中出现未转义控制字符")
                    else -> sb.append(ch)
                }
            }
        }

        private fun parseEscape(): Char {
            val ch = peek() ?: throw RuleListFormatException("转义序列不完整")
            pos++
            return when (ch) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                'f' -> '\u000C'
                'u' -> {
                    if (pos + 4 > text.length) throw RuleListFormatException("\\u 转义不完整")
                    val hex = text.substring(pos, pos + 4)
                    pos += 4
                    val code = hex.toIntOrNull(16)
                        ?: throw RuleListFormatException("\\u 转义不是合法十六进制")
                    code.toChar()
                }
                else -> throw RuleListFormatException("非法转义 \\$ch")
            }
        }

        private fun skipWhitespace() {
            while (pos < text.length && text[pos].isWhitespace()) pos++
        }

        private fun expect(ch: Char) {
            if (peek() != ch) throw RuleListFormatException("期望 '$ch'")
            pos++
        }

        private fun peek(): Char? = if (pos < text.length) text[pos] else null

        /** 数组结束后不允许有非空白内容。 */
        private fun finish() {
            skipWhitespace()
            if (pos != text.length) throw RuleListFormatException("JSON 数组后存在多余内容")
        }
    }
}
