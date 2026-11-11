/**
 * 捕获日志展示与导出的脱敏（`docs/02` §20）。
 *
 * 完整账号、手机号、银行卡号、身份证号不得留下；支付单号只留首尾、中间段换成掩码；
 * 通知里的姓名只留姓氏和 `**`。金额和时间不经过这里。
 */
package com.zhangran.app.domain.service

object SensitiveText {

    private const val MASK = "***"

    private val ID_CARD = Regex("""(?<!\d)(\d{6})(\d{8})(\d{3})([\dXx])(?!\d)""")
    private val BANK_CARD =
        Regex("""(?<!\d)(\d{4})[\s-]?((?:\d{4}[\s-]?){2,3})(\d{4})(?!\d)""")
    private val PHONE = Regex("""(?<!\d)(1[3-9]\d)\d{4}(\d{4})(?!\d)""")
    private val ORDER_NO = Regex("""(?<!\d)(\d{4})\d{4,}(\d{4})(?!\d)""")
    private val ACCOUNT_DIGITS = Regex("""(?<!\d)\d{8,11}(?!\d)""")
    private val LABELED_ACCOUNT =
        Regex("""(账号|帐户|账户号)([:：\s]*)([A-Za-z0-9]{6,})""")
    private val NAMED_PERSON =
        Regex("""(收款方|付款方|对方|收款人|付款人)([:：\s]*)([\u4e00-\u9fa5])[\u4e00-\u9fa5]{1,3}""")

    fun redact(text: String?): String? {
        if (text == null) return null
        var result = text
        result = ID_CARD.replace(result) { match ->
            "${match.groupValues[1]}$MASK${match.groupValues[3]}${match.groupValues[4]}"
        }
        result = BANK_CARD.replace(result) { match ->
            "${match.groupValues[1]}$MASK${match.groupValues[3]}"
        }
        result = PHONE.replace(result) { match ->
            "${match.groupValues[1]}$MASK${match.groupValues[2]}"
        }
        result = ORDER_NO.replace(result) { match ->
            "${match.groupValues[1]}$MASK${match.groupValues[2]}"
        }
        result = LABELED_ACCOUNT.replace(result) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}$MASK"
        }
        result = ACCOUNT_DIGITS.replace(result, MASK)
        result = NAMED_PERSON.replace(result) { match ->
            "${match.groupValues[1]}${match.groupValues[2]}${match.groupValues[3]}**"
        }
        return result
    }
}

/**
 * 在一段文本里找还没脱敏的敏感字段。
 *
 * 只看调用方交给它的字符串（导出文件里的字符串字段），不把 JSON 数字时间戳当成单号。
 */
object SensitiveFieldScan {

    private val PHONE = Regex("""(?<!\d)1[3-9]\d{9}(?!\d)""")
    private val ID_CARD = Regex("""(?<!\d)\d{17}[\dXx](?!\d)""")
    private val BANK_CARD = Regex("""(?<!\d)\d{16,19}(?!\d)""")
    private val LONG_DIGITS = Regex("""\d{12,}""")

    fun findings(text: String): List<String> {
        val hits = mutableListOf<String>()
        if (PHONE.containsMatchIn(text)) hits += "phone"
        if (ID_CARD.containsMatchIn(text)) hits += "id-card"
        if (BANK_CARD.containsMatchIn(text)) hits += "bank-card"
        if (LONG_DIGITS.containsMatchIn(text)) hits += "long-digits"
        return hits
    }
}
