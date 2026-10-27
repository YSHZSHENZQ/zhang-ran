/**
 * 交易详情页的 UI 状态与展示格式化（`M2b` §4.1/§4.5）。
 *
 * 设计要点：
 * - 详情**只读一次**（`M2b` §4.5：不要求响应式），编辑/删除成功后由 ViewModel 主动刷新；
 * - 状态只有四态：`Loading` / `Content` / `Submitting` / `Error`；
 * - 字段编辑器是**页面内临时状态**（不放进 UiState），提交时才调用 `UpdateTransactionFieldUseCase`；
 * - 展示格式化（时间/状态/置信度）集中在此文件的纯函数里，Composable 只负责画。
 */
package com.zhangran.app.ui.screen.detail

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.usecase.EditableField
import com.zhangran.app.domain.usecase.FieldEditRejection
import com.zhangran.app.domain.usecase.FieldEditValue
import com.zhangran.app.ui.screen.add.AccountOption
import com.zhangran.app.ui.screen.add.CategoryOption
import com.zhangran.app.ui.screen.home.HomeViewModel
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * 详情页展示所需的全部数据（已解析名称，不含实体）。
 *
 * @property categoryId 分类 id（编辑器初始选中用）
 * @property accountId 账户 id（同上）
 * @property confidence 置信度（0–1，来自证据；缺失为 `null` → 隐藏该行）
 * @property evidenceSummary 原始捕获证据摘要（已脱敏的 `rawCaptureRef`；缺失为 `null` → 隐藏整块）
 * @property categories 当前类型下的可选分类（编辑分类用）
 * @property accounts 可选账户（编辑支付方式用）
 */
data class TransactionDetailUi(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val merchant: String?,
    val categoryId: String?,
    val categoryName: String?,
    val categoryIcon: String?,
    val accountId: String?,
    val accountName: String?,
    val transactionTime: Long,
    val transactionTimeZone: String,
    val note: String?,
    val source: CaptureSource,
    val state: TransactionState,
    val confidence: Double?,
    val engineVersion: String?,
    val matchedRuleId: String?,
    val externalTransactionId: String?,
    val evidenceId: String?,
    val evidenceSummary: String?,
    val categories: List<CategoryOption> = emptyList(),
    val accounts: List<AccountOption> = emptyList(),
) {
    /** `TRANSFER` / `REFUND` 的字段编辑属 M2c → 详情只读（`M2b` §1.1）。 */
    val fieldsEditable: Boolean
        get() = type == TransactionType.EXPENSE || type == TransactionType.INCOME

    /** 是否展示「自动解析证据」卡片（全部为空则整块不显示，A2）。 */
    val hasEvidenceBlock: Boolean
        get() = !evidenceSummary.isNullOrBlank() ||
            !evidenceId.isNullOrBlank() ||
            !engineVersion.isNullOrBlank() ||
            !matchedRuleId.isNullOrBlank() ||
            !externalTransactionId.isNullOrBlank() ||
            confidence != null
}

/** 详情页状态（`M2b` §4.5）。 */
sealed interface TransactionDetailUiState {

    /** 首次加载。 */
    data object Loading : TransactionDetailUiState

    /** 加载/操作失败（可重试）。 */
    data class Error(val message: String) : TransactionDetailUiState

    /** 展示详情。 */
    data class Content(val detail: TransactionDetailUi) : TransactionDetailUiState

    /** 编辑/删除进行中：交互禁用，防止重复提交（A14）。 */
    data class Submitting(val detail: TransactionDetailUi) : TransactionDetailUiState
}

/** 详情页一次性事件。 */
sealed interface TransactionDetailEvent {

    /** 编辑成功 → 返回上一页（首页会自动反映变更，A8）。 */
    data class Saved(val transactionId: String) : TransactionDetailEvent

    /** 删除成功 → 返回首页，并由首页弹出「已删除，可撤销」SnackBar（A10）。 */
    data class Deleted(val transactionId: String) : TransactionDetailEvent

    /** 需要提示用户的消息（校验失败/分类创建受限等）。 */
    data class Message(val text: String) : TransactionDetailEvent
}

/** 当前打开的字段编辑器（页面内临时状态，不进 UiState）。 */
sealed interface FieldEditorKind {
    data object Merchant : FieldEditorKind
    data object Amount : FieldEditorKind
    data object Type : FieldEditorKind
    data object Category : FieldEditorKind
    data object Account : FieldEditorKind
    data object Time : FieldEditorKind
    data object Note : FieldEditorKind
}

// ---------------------------------------------------------------- 展示格式化（纯函数）

/** 时间：`2024年6月28日 14:23`（设备时区，`docs/03` §5：展示时按设备当前时区转换）。 */
fun formatDetailDateTime(
    epochMillis: Long,
    zoneId: String = ZoneId.systemDefault().id,
): String = DETAIL_TIME_FORMATTER.format(
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.of(zoneId)),
)

/** 状态的中文文案（`docs/03` §6 的 5 种状态）。 */
fun stateTextOf(state: TransactionState): String = when (state) {
    TransactionState.PENDING -> "待确认"
    TransactionState.CONFIRMED -> "已确认"
    TransactionState.IGNORED -> "已忽略"
    TransactionState.DELETED -> "已删除"
    TransactionState.DUPLICATE -> "重复"
}

/** 置信度：`96%`（证据缺失时调用方不展示该行）。 */
fun confidenceTextOf(confidence: Double): String = "${(confidence * 100).roundToInt()}%"

/** 金额展示（复用 M2e-1b 的唯一金额格式化实现，不复制）。 */
fun amountTextOf(type: TransactionType, amountMinor: Long): String =
    HomeViewModel.rowAmountOf(type, amountMinor)

/** 类型文案（复用 `HomeViewModel.typeTextOf`）。 */
fun typeTextOf(type: TransactionType): String = HomeViewModel.typeTextOf(type)

/** 编辑金额时的初始输入文本（分 → `3847.20`，供 `AmountInput` 复用）。 */
fun amountInputTextOf(amountMinor: Long): String =
    BigDecimal.valueOf(amountMinor, 2).setScale(2).toPlainString()

/** 详情页所有可编辑字段（供测试与 UI 遍历）。 */
val DETAIL_EDITABLE_FIELDS: List<EditableField> = listOf(
    EditableField.MERCHANT,
    EditableField.AMOUNT,
    EditableField.TYPE,
    EditableField.CATEGORY,
    EditableField.ACCOUNT,
    EditableField.TIME,
    EditableField.NOTE,
)

/**
 * 校验失败时的中文提示（`M2b` §4.3：结构化错误 → 可读文案）。
 *
 * 放在 UI 层而不是用例里：用例返回枚举，文案属展示。
 */
fun rejectionTextOf(reason: FieldEditRejection): String = when (reason) {
    FieldEditRejection.VALUE_MISMATCH -> "数据格式不正确"
    FieldEditRejection.NOT_FOUND -> "交易不存在或已删除"
    FieldEditRejection.AMOUNT_INVALID -> "金额必须大于 0 且不超过上限"
    FieldEditRejection.TYPE_NOT_SUPPORTED -> "暂不支持该类型（转账/退款编辑）"
    FieldEditRejection.CATEGORY_MISSING -> "分类不存在"
    FieldEditRejection.CATEGORY_TYPE_MISMATCH -> "分类与收支类型不匹配，请重选"
    FieldEditRejection.ACCOUNT_MISSING -> "支付方式不存在"
    FieldEditRejection.TIME_INVALID -> "时间不合法"
    FieldEditRejection.NOTE_TOO_LONG -> "备注最多 500 字"
}

private val DETAIL_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
