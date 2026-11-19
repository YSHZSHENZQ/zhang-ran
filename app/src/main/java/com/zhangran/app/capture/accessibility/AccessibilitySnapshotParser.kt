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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

data class AccessibilityParsedFields(
    val amountMinor: Long,
    val direction: TransactionType,
    val merchantRaw: String?,
    val textClue: String,
    val directionSemanticsTrusted: Boolean,
    /** 订单号、交易单号或商户单号。没有合格单号时为 null。 */
    val externalTransactionId: String? = null,
    /**
     * 页面上的支付时间（Asia/Shanghai 解析成 UTC epoch millis）。
     * 没有可解析的支付时间时为 null，调用方继续用捕获时刻。
     */
    val paymentTimeMillis: Long? = null,
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
        "更多功能",
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

    /**
     * 整页出现这些节点时是账单详情，不是支付结果页。
     * 「交易单号」本身可以出现在结果页上，用来识别同一笔，不能单独把结果页判成账单。
     */
    private val billDetailMarkers = setOf(
        "全部账单",
        "商户全称",
        "收款方全称",
        "当前状态",
        "账单服务",
    )

    /** 结果页上的交易身份。按这个顺序取第一个合格值。 */
    private val orderLabels = listOf("交易单号", "商户单号", "订单号")

    private val labeledMerchant = Regex("""(?:商户名称|商户|商家|店铺|收款方|付款方)[:：\s]+(\S{1,32})""")

    private val symbolAmount = Regex("""[¥￥]\s*[0-9][0-9,，]*(?:\.[0-9]{1,2})?""")

    private val yuanAmount = Regex("""[0-9][0-9,，]*(?:\.[0-9]{1,2})?\s*元""")

    private val bareNumber = Regex("""^[0-9][0-9,，]*(?:\.[0-9]{1,2})?$""")

    private val paymentInstrument = Regex("""储蓄卡|信用卡|银行卡|借记卡|花呗|余额宝|零钱""")

    /** 服务侧转发门槛：不像支付结果的白名单页面不进入落库。 */
    fun shouldForward(snapshot: AccessibilityPageSnapshot): Boolean = parse(snapshot) != null

    fun parse(snapshot: AccessibilityPageSnapshot): AccessibilityParsedFields? {
        if (PaymentWindowResolver.snapshotMismatch(snapshot)) return null
        if (isWeChatConversationList(snapshot)) return null
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

        val merchant = merchantOf(snapshot)
        val clue = texts.joinToString(separator = " ").take(MAX_CLUE)
        return AccessibilityParsedFields(
            amountMinor = amountMinor,
            direction = direction,
            merchantRaw = merchant,
            textClue = clue,
            directionSemanticsTrusted = true,
            externalTransactionId = orderIdOf(texts),
            paymentTimeMillis = paymentTimeOf(texts),
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

    /**
     * 微信会话列表：顶栏是 `com.tencent.mm:id/jga` 且说明为「更多功能」，
     * 金额写在同一条会话预览里（「已支付¥…」）。不能只因为出现「已支付」就拒绝。
     * 已经有单独的支付结果节点时仍交给结果页解析。
     */
    private fun isWeChatConversationList(snapshot: AccessibilityPageSnapshot): Boolean {
        if (snapshot.packageName != AccessibilityTargets.WECHAT_PACKAGE) return false
        val topBar = snapshot.nodes.any { node ->
            node.viewId == WECHAT_CONVERSATION_MORE_ID && node.contentDescription == "更多功能"
        }
        if (!topBar) return false
        val texts = visibleTexts(snapshot)
        if (texts.none { isConversationPreview(it) }) return false
        val standaloneResult = texts.any { text ->
            text in expenseExact || text in incomeExact || text in refundExact
        }
        return !standaloneResult
    }

    /** 金额贴在「已支付 / 已收款」后面，是会话预览，不是单独的结果金额节点。 */
    private fun isConversationPreview(text: String): Boolean {
        for (lead in listOf("已支付", "已收款")) {
            val index = text.indexOf(lead)
            if (index < 0) continue
            val rest = text.substring(index + lead.length).trimStart()
            val amount = symbolAmount.find(rest) ?: yuanAmount.find(rest) ?: continue
            if (amount.range.first == 0) return true
        }
        return false
    }

    private fun merchantOf(snapshot: AccessibilityPageSnapshot): String? {
        val texts = visibleTexts(snapshot)
        for (text in texts) {
            val labeled = labeledMerchant.find(text)?.groupValues?.getOrNull(1)?.trim()
            if (!labeled.isNullOrEmpty() && !isRejectedMerchant(labeled)) return labeled
        }
        for (index in texts.indices) {
            if (texts[index] !in fieldLabels) continue
            val next = texts.getOrNull(index + 1) ?: continue
            if (!isRejectedMerchant(next)) return next
        }
        return fallbackMerchant(snapshot.nodes)
    }

    /**
     * 兜底只看非按钮节点。按钮和可点击控件的文字、contentDescription 都不能当商户。
     */
    private fun fallbackMerchant(nodes: List<SnapshotNode>): String? {
        val candidates = ArrayList<String>()
        for (node in nodes) {
            if (isButtonOrClickable(node)) continue
            node.text?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates += it }
            node.contentDescription?.trim()?.takeIf { it.isNotEmpty() }?.let { candidates += it }
        }
        return candidates.firstOrNull { text -> !isRejectedMerchant(text) }
    }

    private fun isButtonOrClickable(node: SnapshotNode): Boolean {
        if (node.clickable) return true
        val simple = node.className?.substringAfterLast('.') ?: return false
        return simple == "Button" || simple.endsWith("Button")
    }

    private fun isRejectedMerchant(text: String): Boolean {
        if (text.length < 2) return true
        if (text.all { it.isDigit() }) return true
        if (statusOf(text) != null) return true
        if (text in chromeExact || text in fieldLabels) return true
        if (fieldLabels.any { label -> text.startsWith(label) && continuesLabel(text, label) }) return true
        if (orderLabels.any { label -> text.startsWith(label) }) return true
        if (isCouponText(text)) return true
        if (bareNumber.matches(text)) return true
        if (isAmount(text)) return true
        if (paymentInstrument.containsMatchIn(text)) return true
        if (text.startsWith("使用") && text.contains("付")) return true
        if (parsePaymentTime(text) != null) return true
        return false
    }

    private fun continuesLabel(text: String, label: String): Boolean {
        if (text.length == label.length) return true
        return text[label.length] in ":： "
    }

    /**
     * 交易单号、商户单号、订单号。标签后一格或同一节点里的「标签 + 值」。
     * 值必须像单号：至少 8 位，且含数字。纯商户名、按钮、金额都不收。
     */
    private fun orderIdOf(texts: List<String>): String? {
        for (label in orderLabels) {
            val inline = Regex("""${Regex.escape(label)}[:：\s]+(\S{8,64})""")
            for (text in texts) {
                val found = inline.find(text)?.groupValues?.getOrNull(1)?.trim()
                if (isOrderId(found)) return found
            }
            for (index in texts.indices) {
                if (texts[index] != label) continue
                val next = texts.getOrNull(index + 1)?.trim()
                if (isOrderId(next)) return next
            }
        }
        return null
    }

    private fun isOrderId(value: String?): Boolean {
        val text = value?.trim().orEmpty()
        if (!ORDER_ID.matches(text)) return false
        if (text.none { it.isDigit() }) return false
        if (text in chromeExact || text in fieldLabels) return false
        if (statusOf(text) != null) return false
        if (isAmount(text)) return false
        return true
    }

    /** 只接受带年份的支付时间。解析不了就当没有，不拿捕获时刻冒充。 */
    private fun paymentTimeOf(texts: List<String>): Long? {
        val candidates = mutableListOf<String>()
        val inline = Regex("""支付时间[:：\s]+(\d{4}\S*(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)""")
        for (text in texts) {
            inline.find(text)?.groupValues?.getOrNull(1)?.trim()?.let { candidates += it }
        }
        for (index in texts.indices) {
            if (texts[index] != "支付时间") continue
            texts.getOrNull(index + 1)?.trim()?.let { candidates += it }
        }
        return candidates.firstNotNullOfOrNull { parsePaymentTime(it) }
    }

    private fun parsePaymentTime(text: String): Long? {
        val value = text.trim()
        if (value.isEmpty()) return null
        for (formatter in paymentTimeFormatters) {
            try {
                val local = LocalDateTime.parse(value, formatter)
                val millis = local.atZone(PAYMENT_ZONE).toInstant().toEpochMilli()
                if (millis > 0L) return millis
            } catch (_: DateTimeParseException) {
                continue
            }
        }
        return null
    }

    private fun isAmount(text: String): Boolean =
        symbolAmount.containsMatchIn(text) || yuanAmount.containsMatchIn(text)

    private const val MAX_CLUE = 200

    private val ORDER_ID = Regex("""[A-Za-z0-9_.:-]{8,64}""")

    private const val WECHAT_CONVERSATION_MORE_ID = "com.tencent.mm:id/jga"

    private val PAYMENT_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    private val paymentTimeFormatters: List<DateTimeFormatter> = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy/MM/dd HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy年M月d日 HH:mm:ss",
        "yyyy年M月d日 HH:mm",
        "yyyy年M月d日HH:mm:ss",
        "yyyy年M月d日HH:mm",
    ).map { DateTimeFormatter.ofPattern(it) }
}
