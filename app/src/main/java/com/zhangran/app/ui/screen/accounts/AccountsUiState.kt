/**
 * 账户管理页的 UI 状态与展示映射（`M2d-2` §4.1/§4.6；行为 SSOT：`docs/06` §4.13）。
 *
 * 设计要点与分类管理一致：
 * - 页面不持有实体：`AccountEntity` 在 ViewModel 内映射为 [AccountUiItem]；
 * - 编辑弹层的草稿与错误属于 UiState（保存失败留在弹层里提示，A4）；
 * - 分组按 `AccountType`：现金 / 银行卡（储蓄卡+信用卡）/ 第三方支付；**空分组不显示**；
 * - `Empty` = 初始化异常（正常至少有 1 个"现金"账户），提示检查初始数据而不是"暂无账户"。
 */
package com.zhangran.app.ui.screen.accounts

import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.usecase.AccountSaveError

/**
 * 一个账户的展示模型。
 *
 * @property descText 副行文案（"默认账户 · 尾号 8823 · 已禁用" 的组合；空串表示不显示副行）
 */
data class AccountUiItem(
    val id: String,
    val name: String,
    val type: AccountType,
    val channel: PaymentChannel?,
    val last4: String?,
    val enabled: Boolean,
    val isDefault: Boolean,
    val sortOrder: Int,
) {
    val descText: String
        get() = listOfNotNull(
            "默认账户".takeIf { isDefault },
            last4?.let { "尾号 $it" },
            "已禁用".takeIf { !enabled },
        ).joinToString(" · ")
}

/** 一组账户（按展示分组聚合，空组不生成）。 */
data class AccountGroupUi(
    val title: String,
    val items: List<AccountUiItem>,
)

/**
 * 账户编辑弹层状态。
 *
 * @property editingId 非空 = 编辑；`null` = 新增
 * @property type 新增时可选；编辑时只读（V7）
 * @property channel "无渠道" 用 `null`
 * @property last4 文本框内容（仅借记卡/信用卡可见，V2）
 * @property isDefault 新增时表示"设为默认"；编辑时不使用（改用「设为默认」按钮）
 * @property error 就地错误；`null` 表示无错误
 * @property saving 保存中
 */
data class AccountEditorUi(
    val editingId: String? = null,
    val type: AccountType,
    val name: String = "",
    val channel: PaymentChannel? = null,
    val last4: String = "",
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
) {
    /** V7：类型只在新增时可选。 */
    val typeEditable: Boolean get() = editingId == null

    /** V2：只有银行卡类（储蓄卡/信用卡）允许填尾号。 */
    val last4Visible: Boolean
        get() = type == AccountType.DEBIT_CARD || type == AccountType.CREDIT_CARD
}

/** 账户管理页状态。 */
sealed interface AccountsUiState {

    data object Loading : AccountsUiState

    /**
     * @property groups 非空分组（按 `CASH` → 银行卡 → `THIRD_PARTY` 顺序）
     * @property editor 打开的编辑弹层
     */
    data class Content(
        val groups: List<AccountGroupUi>,
        val editor: AccountEditorUi? = null,
    ) : AccountsUiState {

        /** 全部展示项（排序/查邻位用）。 */
        val allItems: List<AccountUiItem> get() = groups.flatMap { it.items }
    }

    /** 账户表为空：初始化异常（正常至少有 1 个"现金"账户）。 */
    data object Empty : AccountsUiState

    data class Error(val message: String) : AccountsUiState
}

/** 账户管理页的一次性事件。 */
sealed interface AccountsEvent {

    data class Message(val text: String) : AccountsEvent

    /** 保存成功（弹层关闭由状态驱动）。 */
    data object Saved : AccountsEvent
}

/** 校验失败 → 中文提示。 */
fun accountErrorTextOf(error: AccountSaveError): String = when (error) {
    AccountSaveError.NAME_EMPTY -> "账户名称不能为空"
    AccountSaveError.NAME_TOO_LONG -> "账户名称最多 20 字"
    AccountSaveError.DUPLICATE_NAME -> "已存在同名账户"
    AccountSaveError.TYPE_CHANGE_NOT_ALLOWED -> "账户类型不可修改"
    AccountSaveError.INVALID_LAST4 -> "尾号必须是 4 位数字"
    AccountSaveError.LAST4_NOT_ALLOWED -> "现金/第三方支付账户不能填写尾号"
    AccountSaveError.LAST_ENABLED_ACCOUNT -> "至少保留一个启用账户"
    AccountSaveError.NOT_FOUND -> "账户不存在，请刷新后重试"
}

/** 分组标题（`06` §4.13：现金 / 银行卡 / 第三方支付）。 */
fun accountGroupTitleOf(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金类"
    AccountType.DEBIT_CARD, AccountType.CREDIT_CARD -> "银行卡"
    AccountType.THIRD_PARTY -> "第三方支付"
}

/** 类型展示名（编辑弹层选项 + 无障碍）。 */
fun accountTypeLabelOf(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金"
    AccountType.DEBIT_CARD -> "储蓄卡"
    AccountType.CREDIT_CARD -> "信用卡"
    AccountType.THIRD_PARTY -> "第三方支付"
}

/** 无障碍用的类型描述（"第三方支付账户" 等）。 */
fun accountTypeDescriptionOf(type: AccountType): String = when (type) {
    AccountType.CASH -> "现金账户"
    AccountType.DEBIT_CARD -> "储蓄卡账户"
    AccountType.CREDIT_CARD -> "信用卡账户"
    AccountType.THIRD_PARTY -> "第三方支付账户"
}

/** 渠道展示名（"无" 用 `null` 表示）。 */
fun paymentChannelLabelOf(channel: PaymentChannel): String = when (channel) {
    PaymentChannel.ALIPAY -> "支付宝"
    PaymentChannel.WECHAT -> "微信"
    PaymentChannel.BANK -> "银行"
    PaymentChannel.CASH -> "现金"
    PaymentChannel.OTHER -> "其他"
}
