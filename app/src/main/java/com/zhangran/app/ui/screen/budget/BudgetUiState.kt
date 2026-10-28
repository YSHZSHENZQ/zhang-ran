/**
 * 预算页的 UI 状态与展示映射（`M4a` §4.1/§4.6；行为 SSOT：`docs/06` §4.4）。
 *
 * 设计要点：
 * - 页面不计算统计口径：`BudgetOverview` 的已用额来自 `BudgetUsage`，这里只做**展示换算**
 *   （比例、百分比、剩余/超支文案）；
 * - 金额格式化复用首页的 `HomeViewModel.formatMoney`（`BigDecimal`，禁止 `Double`）；
 * - 编辑弹层的草稿/错误/删除确认属于 UiState（保存失败就地提示）。
 */
package com.zhangran.app.ui.screen.budget

import com.zhangran.app.domain.usecase.BudgetOverview
import com.zhangran.app.domain.usecase.CategoryBudgetOverview
import com.zhangran.app.ui.screen.add.AmountInput
import com.zhangran.app.ui.screen.home.AmountSign
import com.zhangran.app.ui.screen.home.HomeViewModel
import kotlin.math.roundToInt

/** 分类预算编辑时可选的分类。 */
data class BudgetCategoryOption(
    val id: String,
    val name: String,
    val icon: String,
)

/** 编辑目标。 */
enum class BudgetEditorKind {
    /** 月度总预算（新增或编辑）。 */
    TOTAL,

    /** 编辑既有分类预算。 */
    CATEGORY_EDIT,

    /** 新增分类预算（需先选分类）。 */
    CATEGORY_ADD,
}

/**
 * 预算编辑弹层状态。
 *
 * @property budgetId 既有预算 id；`null` = 新增
 * @property categoryId 分类预算的分类；`CATEGORY_ADD` 未选时为 `null`
 * @property amount 金额输入状态机（复用 M2a 的 [AmountInput]，走 `Money` 解析）
 * @property confirmingDelete 是否显示二次确认（仅既有预算）
 */
data class BudgetEditorUi(
    val kind: BudgetEditorKind,
    val budgetId: String? = null,
    val categoryId: String? = null,
    val categoryName: String? = null,
    val amount: AmountInput = AmountInput(),
    val error: String? = null,
    val saving: Boolean = false,
    val confirmingDelete: Boolean = false,
) {
    val canDelete: Boolean get() = budgetId != null
    val needsCategory: Boolean get() = kind == BudgetEditorKind.CATEGORY_ADD && categoryId == null
}

/** 预算页状态。 */
sealed interface BudgetUiState {

    data object Loading : BudgetUiState

    /**
     * 有预算。
     *
     * @property editor 打开的编辑弹层
     * @property categoryOptions 可添加的分类（已排除已设预算的分类）
     */
    data class Content(
        val overview: BudgetOverview,
        val categoryOptions: List<BudgetCategoryOption>,
        val editor: BudgetEditorUi? = null,
    ) : BudgetUiState

    /**
     * 未设置任何预算（A16）。
     *
     * @property periodKey 当前月，供"设置总预算/示例预算"使用
     */
    data class Empty(
        val periodKey: String,
        val categoryOptions: List<BudgetCategoryOption>,
        val editor: BudgetEditorUi? = null,
    ) : BudgetUiState

    data class Error(val message: String) : BudgetUiState
}

/** 预算页的一次性事件。 */
sealed interface BudgetEvent {

    data class Message(val text: String) : BudgetEvent
}

// ------------------------------------------------------------------ 展示换算

/** 金额文案（默认带 `¥` 与千分位；负值带 `-`）。 */
fun budgetMoneyText(amountMinor: Long): String =
    HomeViewModel.formatMoney(amountMinor, sign = AmountSign.AUTO, withCurrencySymbol = true)

/**
 * 进度条填充比例：`used / limit`，**限制在 [0,1]**（超支时条满格）。
 *
 * 与"百分比文案"不同：文案可以超过 100%，条宽不能溢出。
 */
fun budgetBarFraction(usedMinor: Long, limitMinor: Long): Float {
    if (limitMinor <= 0L) return 0f
    return (usedMinor.toFloat() / limitMinor.toFloat()).coerceIn(0f, 1f)
}

/** 使用百分比（四舍五入，可为 0..>100）。 */
fun budgetPercent(usedMinor: Long, limitMinor: Long): Int {
    if (limitMinor <= 0L) return 0
    return ((usedMinor.toDouble() / limitMinor.toDouble()) * 100).roundToInt()
}

/** 剩余文案（超支时由调用方改用 [budgetOverText]）。 */
fun budgetRemainingText(usedMinor: Long, limitMinor: Long): String =
    "剩余 ${budgetMoneyText(limitMinor - usedMinor)}"

/** 超支文案（`02` §12：仅视觉提示）。 */
fun budgetOverText(usedMinor: Long, limitMinor: Long): String =
    "已超支 ${budgetMoneyText(usedMinor - limitMinor)}"

/** 总预算进度区文案："剩余 ¥X · 已用 Y%"。 */
fun budgetTotalFooterText(usedMinor: Long, limitMinor: Long): String =
    "${budgetRemainingText(usedMinor, limitMinor)} · 已用 ${budgetPercent(usedMinor, limitMinor)}%"

/** 分类行右侧文案："¥1,620 / ¥2,000"。 */
fun budgetCategoryAmountText(category: CategoryBudgetOverview): String =
    "${budgetMoneyText(category.usedMinor)} / ${budgetMoneyText(category.limitMinor)}"
