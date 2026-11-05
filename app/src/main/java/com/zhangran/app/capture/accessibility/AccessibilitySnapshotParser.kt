/**
 * 支付结果页的最小解析（M6-2）。
 *
 * 只在同时看到「明确的支付结果用语」和「可解析金额」时交出字段。
 * 缺一不可：聊天页里的普通句子或孤立金额都不是业务候选。
 * 不猜分类、不猜账户、不把方向缺省成支出。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Money
import com.zhangran.app.domain.service.MoneyResult

data class AccessibilityParsedFields(
    val amountMinor: Long,
    val direction: TransactionType,
    val merchantRaw: String?,
    val textClue: String,
    val directionSemanticsTrusted: Boolean,
)

object AccessibilitySnapshotParser {

    private val expensePhrases = listOf("支付成功", "付款成功", "支付完成", "已支付")
    private val incomePhrases = listOf("收款成功", "已收款", "收款到账")

    private val statusExact = setOf(
        "支付成功",
        "付款成功",
        "支付完成",
        "已支付",
        "收款成功",
        "已收款",
        "收款到账",
        "完成",
        "确定",
        "返回",
        "关闭",
        "查看账单",
        "账单详情",
    )

    private val labelOnly = setOf("商户", "商家", "店铺", "收款方", "付款方", "商户名称")

    private val labeledMerchant = Regex("""(?:商户名称|商户|商家|店铺|收款方|付款方)[:：\s]+(\S{1,32})""")

    private val symbolAmount = Regex("""[¥￥]\s*[0-9][0-9,，]*(?:\.[0-9]{1,2})?""")

    private val yuanAmount = Regex("""[0-9][0-9,，]*\.[0-9]{1,2}\s*元""")

    /** 服务侧转发门槛：不像支付结果的白名单页面不进入落库。 */
    fun shouldForward(snapshot: AccessibilityPageSnapshot): Boolean = parse(snapshot) != null

    fun parse(snapshot: AccessibilityPageSnapshot): AccessibilityParsedFields? {
        val texts = visibleTexts(snapshot)
        if (texts.isEmpty()) return null

        val blob = texts.joinToString(separator = "\n")
        val expense = expensePhrases.any { blob.contains(it) }
        val income = incomePhrases.any { blob.contains(it) }
        val direction = when {
            expense && income -> return null
            expense -> TransactionType.EXPENSE
            income -> TransactionType.INCOME
            else -> return null
        }
        val amountMinor = texts.firstNotNullOfOrNull { amountOf(it) } ?: return null
        if (amountMinor <= 0L) return null

        val merchant = merchantOf(texts)
        val clue = texts.joinToString(separator = " ").take(MAX_CLUE)
        return AccessibilityParsedFields(
            amountMinor = amountMinor,
            direction = direction,
            merchantRaw = merchant,
            textClue = clue,
            directionSemanticsTrusted = true,
        )
    }

    private fun visibleTexts(snapshot: AccessibilityPageSnapshot): List<String> =
        snapshot.nodes.flatMap { node ->
            listOfNotNull(node.text, node.contentDescription)
        }.map { it.trim() }.filter { it.isNotEmpty() }

    private fun amountOf(text: String): Long? {
        val token = symbolAmount.find(text)?.value ?: yuanAmount.find(text)?.value ?: return null
        return when (val parsed = Money.fromText(token)) {
            is MoneyResult.Success -> parsed.amountMinor.takeIf { it > 0L }
            else -> null
        }
    }

    private fun merchantOf(texts: List<String>): String? {
        for (text in texts) {
            val labeled = labeledMerchant.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!labeled.isNullOrEmpty() && !isAmount(labeled)) return labeled
        }
        for (index in texts.indices) {
            if (texts[index] !in labelOnly) continue
            val next = texts.getOrNull(index + 1) ?: continue
            if (!isAmount(next) && !isStatus(next)) return next
        }
        return texts.firstOrNull { text ->
            text.length >= 2 && !isStatus(text) && !isAmount(text) && !text.all { it.isDigit() }
        }
    }

    private fun isAmount(text: String): Boolean =
        symbolAmount.containsMatchIn(text) || yuanAmount.containsMatchIn(text)

    private fun isStatus(text: String): Boolean {
        if (text in statusExact || text in labelOnly) return true
        return text.contains("成功") && text.length <= 12
    }

    private const val MAX_CLUE = 200
}
