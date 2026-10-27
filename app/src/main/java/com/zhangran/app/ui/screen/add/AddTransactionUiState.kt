/**
 * 记账表单的 UI 状态与金额输入规则（任务书 M2a 的 §4.2、A1、A2）。
 *
 * 设计要点：
 * - 金额输入是**纯状态机**（[AmountInput]），不依赖 Compose 与数据库 → 可在纯 JVM 单测里逐条断言；
 * - 金额到 `Long amountMinor` 的转换**必须**走 [com.zhangran.app.domain.service.Money]（禁止 `Double`）；
 * - 四态：加载中 / 加载失败 / 就绪（含"无分类数据"）/ 保存中（`Ready.saving`）——
 *   保存失败以 `Ready.error` 就地呈现，不落库。
 */
package com.zhangran.app.ui.screen.add

import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Money
import com.zhangran.app.domain.service.MoneyResult

/**
 * 金额输入缓冲区（**纯逻辑**，无副作用）。
 *
 * 规则（任务书 §4.2）：
 * 1. 最多 **2 位小数**；
 * 2. 只能有 **一个小数点**；
 * 3. 前导 0 后跟数字要替换（`0` → `5`，即 `05` 显示为 `5`）；
 * 4. `⌫` 删末位、`C` 清空；
 * 5. 整数位上限 **9 位**（`999,999,999.99` → `99_999_999_999`，恰好等于 `Money.MAX_AMOUNT_MINOR`，
 *    防止 `Long` 溢出）。
 *
 * @property text 当前输入的原始文本（空串表示"尚未输入"）
 */
data class AmountInput(val text: String = "") {

    /** 是否已输入小数点。 */
    val hasDot: Boolean get() = text.contains('.')

    /** 整数部分（无小数点时就是全文）。 */
    val integerPart: String get() = text.substringBefore('.')

    /** 小数部分（无小数点时为空串）。 */
    val fractionPart: String get() = text.substringAfter('.', "")

    /**
     * 追加一个数字。
     *
     * @param digit 必须是 `'0'..'9'`
     */
    fun digit(digit: Char): AmountInput {
        require(digit in '0'..'9') { "不是数字：$digit" }
        if (hasDot) {
            // 小数位已满 2 位：忽略（不报错，符合键盘输入直觉）
            if (fractionPart.length >= MAX_FRACTION_DIGITS) return this
            return copy(text = text + digit)
        }
        // 前导 0：只有 "0" 时，输入非 0 数字则替换；输入 0 仍保持 "0"
        if (integerPart == "0") {
            return if (digit == '0') this else copy(text = digit.toString())
        }
        if (integerPart.length >= MAX_INTEGER_DIGITS) return this
        return copy(text = text + digit)
    }

    /** 追加小数点（已存在则忽略；空串时补成 `0.`）。 */
    fun dot(): AmountInput {
        if (hasDot) return this
        return copy(text = if (text.isEmpty()) "0." else "$text.")
    }

    /** 删除末位（空串则不变）。 */
    fun backspace(): AmountInput = if (text.isEmpty()) this else copy(text = text.dropLast(1))

    /** 清空。 */
    fun clear(): AmountInput = AmountInput()

    /**
     * 转换为最小货币单位（分）。
     *
     * **必须**经 [Money.fromText]，不得自行 `toDouble()`。
     *
     * @return 合法时返回 `amountMinor`；空串或非法（如 `"0."` 之外的异常形态）返回 `null`
     */
    fun toMinorOrNull(): Long? = when (val result = Money.fromText(text)) {
        is MoneyResult.Success -> result.amountMinor
        is MoneyResult.Invalid, is MoneyResult.CurrencyNotSupported -> null
    }

    /**
     * 展示文本（原型 `.amount-display` 初始显示 `0.00`）。
     *
     * 输入态保持用户输入的原始形态（如 `12.`），未输入时显示 `0.00`。
     */
    fun display(): String = if (text.isEmpty()) "0.00" else text

    companion object {
        /** 小数位上限。 */
        const val MAX_FRACTION_DIGITS: Int = 2

        /** 整数位上限（与 `Money.MAX_AMOUNT_MINOR` 的位数一致，防止溢出）。 */
        const val MAX_INTEGER_DIGITS: Int = 9
    }
}

/**
 * 分类下拉项。
 *
 * @property id 分类 id
 * @property name 分类名
 * @property icon 语义图标标识（显示时经 `CategoryIconMapper` 转 emoji）
 * @property isFallback 是否为兜底分类（「其他」：点击后走自定义分类流程）
 */
data class CategoryOption(
    val id: String,
    val name: String,
    val icon: String,
    val isFallback: Boolean = false,
)

/**
 * 账户下拉项。
 *
 * @property id 账户 id
 * @property name 账户名
 */
data class AccountOption(
    val id: String,
    val name: String,
)

/** 表单就地错误（`docs/06` §5、任务书 §4.6）。 */
enum class FormError {
    /** 金额为 0 或未输入。 */
    AMOUNT_INVALID,

    /** 未选择分类。 */
    CATEGORY_REQUIRED,

    /** 未选择账户（初始化后正常不会出现）。 */
    ACCOUNT_REQUIRED,

    /** 保存失败（用例返回校验失败或异常）。 */
    SAVE_FAILED,
}

/** 记账表单的一次性事件（保存成功等）。 */
sealed interface AddTransactionEvent {
    /** 保存成功（携带交易 id，供页面提示 / 返回）。 */
    data class Saved(val transactionId: String) : AddTransactionEvent

    /** 提示消息（如分类创建受限）。 */
    data class Message(val text: String) : AddTransactionEvent
}

/**
 * 记账表单的 UI 状态（四态 + 字段）。
 */
sealed interface AddTransactionUiState {

    /** 加载中（正在读取分类与账户）。 */
    data object Loading : AddTransactionUiState

    /** 加载失败（读库异常）。 */
    data class LoadFailed(val message: String) : AddTransactionUiState

    /**
     * 就绪。
     *
     * @property type 当前类型（支出 / 收入）
     * @property amount 金额输入缓冲
     * @property categories 当前类型下的可用分类（为空表示"无分类数据"）
     * @property selectedCategoryId 已选分类
     * @property accounts 可用账户
     * @property selectedAccountId 已选账户（默认 `isDefault` 账户）
     * @property transactionTime 交易时间（`null` 表示"现在"）
     * @property note 备注
     * @property saving 是否保存中
     * @property error 就地错误
     */
    data class Ready(
        val type: TransactionType = TransactionType.EXPENSE,
        val amount: AmountInput = AmountInput(),
        val categories: List<CategoryOption> = emptyList(),
        val selectedCategoryId: String? = null,
        val accounts: List<AccountOption> = emptyList(),
        val selectedAccountId: String? = null,
        val transactionTime: Long? = null,
        val note: String = "",
        val saving: Boolean = false,
        val error: FormError? = null,
    ) : AddTransactionUiState {
        /** 已选分类（用于展示与保存）。 */
        val selectedCategory: CategoryOption?
            get() = categories.firstOrNull { it.id == selectedCategoryId }

        /** 已选账户名（用于行展示）。 */
        val selectedAccountName: String?
            get() = accounts.firstOrNull { it.id == selectedAccountId }?.name

        /** 是否为"无分类数据"（A1 的第三态）。 */
        val hasNoCategories: Boolean
            get() = categories.isEmpty()
    }
}
