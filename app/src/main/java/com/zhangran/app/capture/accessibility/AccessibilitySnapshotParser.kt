/**
 * 支付结果页的最小解析（M6-2，M6-3 按真机页收窄）。
 *
 * 只在同时看到「单独的支付结果节点」和「可解析金额」时交出字段。
 * 结果用语必须是节点全文（或节点全文就是「结果用语 + 金额」），嵌在长句里不算。
 * 账单详情、多金额列表、只有导航/按钮文案的页面不是结果页。
 * 商户不得取导航、按钮、字段标签或支付方式。找不到商户就留空，不拿这些词充数。
 * 金额优先取「支付成功」或「订单金额」旁边的那一笔；优惠券上的金额不算。
 * 「¥」和数字拆在两个节点时仍拼成一笔。不猜分类、不猜账户、不把方向缺省成支出。
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

    private val expenseExact = listOf("支付成功", "付款成功", "支付完成", "已支付")
    private val incomeExact = listOf("收款成功", "已收款", "收款到账")
    private val refundExact = listOf("退款成功")

    /** 结果页上常见的收尾按钮或金额标签。没有这类锚点、状态节点本身也不带金额时，不当事务。 */
    private val resultAnchors = setOf("完成", "确定", "支付总额", "付款方式", "订单金额")

    /** 导航、按钮、底栏。这些词不能当商户。 */
    private val chromeExact = setOf(
        "更多信息",
        "回首页",
        "完成",
        "我的",
        "返回",
        "确定",
        "关闭",
        "取消",
        "知道了",
        "查看账单",
        "账单详情",
        "更多",
        "详情",
        "付款",
        "确认",
        "立即付款",
        "交易详情",
    )

    /** 结果页或账单上的字段名，不是商户。 */
    private val fieldLabels = setOf(
        "商户",
        "商家",
        "店铺",
        "收款方",
        "付款方",
        "商户名称",
        "支付总额",
        "订单状态",
        "订单尾号",
        "当前状态",
        "商品",
        "商品说明",
        "支付时间",
        "商户全称",
        "收款方全称",
        "付款方式",
        "支付方式",
        "交易方式",
        "订单金额",
        "实付金额",
        "付款金额",
        "优惠金额",
        "订单号",
        "商户单号",
        "交易单号",
        "收单机构",
        "账单分类",
    )

    /** 整页出现这些节点时是账单详情，不是支付结果页。 */
    private val billDetailMarkers = setOf(
        "全部账单",
        "商户全称",
        "收款方全称",
        "交易单号",
        "当前状态",
        "账单服务",
    )

    private val labeledMerchant = Regex("""(?:商户名称|商户|商家|店铺|收款方|付款方)[:：\s]+(\S{1,32})""")

    private val symbolAmount = Regex("""[¥￥]\s*[0-9][0-9,，]*(?:\.[0-9]{1,2})?""")

    private val yuanAmount = Regex("""[0-9][0-9,，]*(?:\.[0-9]{1,2})?\s*元""")

    private val bareNumber = Regex("""^[0-9][0-9,，]*(?:\.[0-9]{1,2})?$""")

    private val paymentInstrument = Regex("""储蓄卡|信用卡|银行卡|借记卡|花呗|余额宝|零钱""")

    /** 服务侧转发门槛：不像支付结果的白名单页面不进入落库。 */
    fun shouldForward(snapshot: AccessibilityPageSnapshot): Boolean = parse(snapshot) != null

    fun parse(snapshot: AccessibilityPageSnapshot): AccessibilityParsedFields? {
        if (PaymentWindowResolver.snapshotMismatch(snapshot)) return null
        val texts = visibleTexts(snapshot)
        if (texts.isEmpty()) return null
        if (texts.any { it in billDetailMarkers }) return null

        val statuses = texts.mapNotNull { text -> statusOf(text)?.let { text to it } }
        val expense = statuses.any { it.second == TransactionType.EXPENSE }
        val income = statuses.any { it.second == TransactionType.INCOME }
        val refund = statuses.any { it.second == TransactionType.REFUND }
        val direction = when {
            listOf(expense, income, refund).count { it } != 1 -> return null
            expense -> TransactionType.EXPENSE
            income -> TransactionType.INCOME
            else -> TransactionType.REFUND
        }

        val statusAmounts = statuses.mapNotNull { (text, _) -> amountOf(text) }.distinct()
        val amountMinor = selectAmount(texts) ?: return null
        if (amountMinor <= 0L) return null
        val anchored = statusAmounts.isNotEmpty() || texts.any { it in resultAnchors }
        if (!anchored) return null

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

    /**
     * 节点全文就是结果用语，或就是「结果用语 + 一笔金额」。
     * 长句里带有这些词不算，避免聊天、营销文案入账。
     */
    private fun statusOf(text: String): TransactionType? {
        if (text in expenseExact) return TransactionType.EXPENSE
        if (text in incomeExact) return TransactionType.INCOME
        if (text in refundExact) return TransactionType.REFUND
        expenseExact.firstOrNull { phrase -> isPhrasePlusAmount(text, phrase) }?.let {
            return TransactionType.EXPENSE
        }
        incomeExact.firstOrNull { phrase -> isPhrasePlusAmount(text, phrase) }?.let {
            return TransactionType.INCOME
        }
        refundExact.firstOrNull { phrase -> isPhrasePlusAmount(text, phrase) }?.let {
            return TransactionType.REFUND
        }
        return null
    }

    private fun isPhrasePlusAmount(text: String, phrase: String): Boolean {
        if (!text.startsWith(phrase) || text.length == phrase.length) return false
        return isBareAmount(text.removePrefix(phrase).trim())
    }

    private fun isBareAmount(text: String): Boolean {
        if (text.isEmpty() || amountOf(text) == null) return false
        val token = symbolAmount.find(text)?.value ?: yuanAmount.find(text)?.value ?: return false
        return text.replace(" ", "") == token.replace(" ", "")
    }

    private fun amountOf(text: String): Long? {
        val token = symbolAmount.find(text)?.value ?: yuanAmount.find(text)?.value ?: return null
        return minorOf(token.removeSuffix("元"))
    }

    /**
     * 优先「支付成功 / 订单金额」旁边的金额。旁边有多笔不同金额时不猜。
     * 没有这种锚点时，退回「整页只剩一笔、且不是优惠券」的旧规则。
     */
    private fun selectAmount(texts: List<String>): Long? {
        val located = locateAmounts(texts)
        if (located.isEmpty()) return null
        val preferred = mutableListOf<Long>()
        texts.forEachIndexed { index, text ->
            if (!isAmountAnchor(text)) return@forEachIndexed
            amountOf(text)?.let { preferred += it }
            located.filter { it.index != index && (it.index == index + 1 || it.index == index + 2) }
                .filter { !isCouponAmount(texts, it.index) }
                .forEach { preferred += it.minor }
        }
        val distinctPreferred = preferred.distinct()
        if (distinctPreferred.size == 1) return distinctPreferred.single()
        if (distinctPreferred.size > 1) return null
        val page = located.filter { !isCouponAmount(texts, it.index) }.map { it.minor }.distinct()
        return if (page.size == 1) page.single() else null
    }

    private fun locateAmounts(texts: List<String>): List<LocatedAmount> {
        val found = mutableListOf<LocatedAmount>()
        texts.forEachIndexed { index, text ->
            amountOf(text)?.let { found += LocatedAmount(index, it) }
        }
        texts.forEachIndexed { index, text ->
            if (found.any { it.index == index }) return@forEachIndexed
            val minor = bareMinor(text) ?: return@forEachIndexed
            val previous = texts.getOrNull(index - 1)
            val beforeThat = texts.getOrNull(index - 2)
            val currencyBefore = previous != null && isCurrencyOnly(previous)
            val anchorBefore = previous != null && isAmountAnchor(previous)
            val anchorThenCurrency = beforeThat != null && isAmountAnchor(beforeThat) && currencyBefore
            val decimal = text.contains('.')
            if (currencyBefore || anchorThenCurrency || (anchorBefore && decimal)) {
                found += LocatedAmount(index, minor)
            }
        }
        return found
    }

    private fun isAmountAnchor(text: String): Boolean {
        if (text == "订单金额" || text.startsWith("订单金额")) return true
        return statusOf(text) != null || text in expenseExact || text in incomeExact || text in refundExact
    }

    private fun isCouponAmount(texts: List<String>, index: Int): Boolean {
        if (isCouponText(texts[index])) return true
        val start = (index - 2).coerceAtLeast(0)
        val end = (index + 2).coerceAtMost(texts.lastIndex)
        for (cursor in start..end) {
            if (cursor != index && isCouponText(texts[cursor])) return true
        }
        return false
    }

    private fun isCouponText(text: String): Boolean =
        text.contains("券") || text.contains("红包") || text.contains("立减") || text.contains("抵扣")

    private fun isCurrencyOnly(text: String): Boolean = text == "¥" || text == "￥"

    private fun bareMinor(text: String): Long? {
        if (!bareNumber.matches(text)) return null
        return minorOf(text)
    }

    private fun minorOf(token: String): Long? = when (val parsed = Money.fromText(token.trim())) {
        is MoneyResult.Success -> parsed.amountMinor.takeIf { it > 0L }
        else -> null
    }

    private data class LocatedAmount(val index: Int, val minor: Long)

    private fun merchantOf(texts: List<String>): String? {
        for (text in texts) {
            val labeled = labeledMerchant.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!labeled.isNullOrEmpty() && !isRejectedMerchant(labeled)) return labeled
        }
        for (index in texts.indices) {
            if (texts[index] !in fieldLabels) continue
            val next = texts.getOrNull(index + 1) ?: continue
            if (!isRejectedMerchant(next)) return next
        }
        return texts.firstOrNull { text -> !isRejectedMerchant(text) }
    }

    private fun isRejectedMerchant(text: String): Boolean {
        if (text.length < 2) return true
        if (text.all { it.isDigit() }) return true
        if (statusOf(text) != null) return true
        if (text in chromeExact || text in fieldLabels) return true
        if (isCouponText(text)) return true
        if (bareNumber.matches(text)) return true
        if (isAmount(text)) return true
        if (paymentInstrument.containsMatchIn(text)) return true
        if (text.startsWith("使用") && text.contains("付")) return true
        return false
    }

    private fun isAmount(text: String): Boolean =
        symbolAmount.containsMatchIn(text) || yuanAmount.containsMatchIn(text)

    private const val MAX_CLUE = 200
}
