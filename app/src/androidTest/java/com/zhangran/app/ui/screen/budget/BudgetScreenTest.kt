/**
 * 预算页 UI 测试（`M4a` §5；覆盖 A1、A11、A12、A16、A20–A22 的可测部分）。
 *
 * 页面是无状态 `BudgetContent`；金额口径由 `BudgetUsageTest` 保证，这里只验证展示与交互。
 */
package com.zhangran.app.ui.screen.budget

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.domain.usecase.BudgetOverview
import com.zhangran.app.domain.usecase.CategoryBudgetOverview
import com.zhangran.app.ui.screen.add.AmountInput
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BudgetScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun category(
        id: String,
        name: String,
        icon: String,
        limit: Long,
        used: Long,
    ) = CategoryBudgetOverview(
        budgetId = "budget-$id",
        categoryId = id,
        categoryName = name,
        categoryIcon = icon,
        limitMinor = limit,
        usedMinor = used,
    )

    private fun overview(
        totalLimit: Long? = 500_000L,
        totalUsed: Long = 384_720L,
        categories: List<CategoryBudgetOverview> = listOf(
            category("cat-food", "餐饮", "food", 150_000L, 98_000L),
            // 超支：¥520 / ¥500
            category("cat-fun", "娱乐", "entertainment", 50_000L, 52_000L),
        ),
    ) = BudgetOverview(
        periodKey = "2026-10",
        totalBudgetId = if (totalLimit == null) null else "budget-total",
        totalLimitMinor = totalLimit,
        totalUsedMinor = totalUsed,
        categories = categories,
    )

    private val options = listOf(
        BudgetCategoryOption("cat-traffic", "交通", "transport"),
        BudgetCategoryOption("cat-shop", "购物", "shopping"),
    )

    private fun setContent(
        state: BudgetUiState,
        onEditTotal: () -> Unit = {},
        onEditCategory: (String) -> Unit = {},
        onAddCategory: () -> Unit = {},
        onRetry: () -> Unit = {},
        onUseExample: () -> Unit = {},
        onRequestDelete: () -> Unit = {},
        onConfirmDelete: () -> Unit = {},
        onSelectCategory: (BudgetCategoryOption) -> Unit = {},
        onSave: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                BudgetContent(
                    state = state,
                    onEditTotal = onEditTotal,
                    onEditCategory = onEditCategory,
                    onAddCategory = onAddCategory,
                    onRetry = onRetry,
                    onUseExample = onUseExample,
                    onRequestDelete = onRequestDelete,
                    onConfirmDelete = onConfirmDelete,
                    onSelectCategory = onSelectCategory,
                    onSave = onSave,
                )
            }
        }
    }

    // ------------------------------------------------------------------ A1 结构

    @Test
    fun A1_总额卡片展示金额已用剩余与百分比() {
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options))

        composeRule.onNodeWithText("月度总预算").assertIsDisplayed()
        composeRule.onNodeWithText("¥5,000.00").assertIsDisplayed()
        composeRule.onNodeWithText("已用 ¥3,847.20").assertIsDisplayed()
        composeRule.onNodeWithText("剩余 ¥1,152.80 · 已用 77%").assertIsDisplayed()
    }

    @Test
    fun A1_分类预算行展示已用与额度() {
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options))

        composeRule.onNodeWithText("分类预算").assertIsDisplayed()
        composeRule.onNodeWithText("餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("¥980.00 / ¥1,500.00").assertIsDisplayed()
        composeRule.onNodeWithText("¥520.00 / ¥500.00").assertIsDisplayed()
    }

    @Test
    fun A11_超支仅视觉提示显示已超支金额() {
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options))

        composeRule.onNodeWithText("已超支 ¥20.00").assertIsDisplayed()
        // 仅提示，不弹出强制对话框
        composeRule.onAllNodesWithText("确定").assertCountEquals(0)
    }

    @Test
    fun A11_总额超支时footer显示已超支与超过100的百分比() {
        setContent(
            BudgetUiState.Content(
                overview = overview(totalLimit = 100_000L, totalUsed = 120_000L, categories = emptyList()),
                categoryOptions = options,
            ),
        )
        composeRule.onNodeWithText("已超支 ¥200.00 · 已用 120%").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A12 点整卡/整行

    @Test
    fun A12_点击总额卡片触发编辑且没有顶部编辑按钮() {
        var edited = false
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options), onEditTotal = { edited = true })

        composeRule.onNodeWithContentDescription("月度总预算", substring = true).performClick()

        assertTrue(edited)
        composeRule.onAllNodesWithText("编辑").assertCountEquals(0)
    }

    @Test
    fun A12_点击分类行触发该分类编辑() {
        var categoryId: String? = null
        setContent(
            BudgetUiState.Content(overview = overview(), categoryOptions = options),
            onEditCategory = { categoryId = it },
        )

        composeRule.onNodeWithContentDescription("餐饮 分类预算", substring = true).performClick()

        assertEquals("cat-food", categoryId)
    }

    @Test
    fun 添加分类预算入口可点击() {
        var added = false
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options), onAddCategory = { added = true })

        composeRule.onNodeWithContentDescription("添加分类预算").performClick()

        assertTrue(added)
    }

    // ------------------------------------------------------------------ A16 三态

    @Test
    fun A16_Empty展示设置引导与示例预算且标注示例() {
        var exampleClicked = false
        setContent(
            BudgetUiState.Empty(periodKey = "2026-10", categoryOptions = options),
            onUseExample = { exampleClicked = true },
        )

        composeRule.onNodeWithText("还没有设置预算").assertIsDisplayed()
        composeRule.onNodeWithText("设置月度总预算").assertIsDisplayed()
        composeRule.onNodeWithText("使用示例预算").assertIsDisplayed()
        // 必须标注"示例预算"（A22）：说明文案里明确写了示例与可修改/删除
        composeRule.onNodeWithText("写入后可随时修改或删除", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("使用示例预算").performClick()
        assertTrue(exampleClicked)
    }

    @Test
    fun A16_错误态可重试() {
        var retried = false
        setContent(BudgetUiState.Error("读取预算失败"), onRetry = { retried = true })

        composeRule.onNodeWithText("读取预算失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun A16_加载态渲染标题() {
        setContent(BudgetUiState.Loading)
        composeRule.onNodeWithText("预算").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A20/A21 删除

    @Test
    fun A21_列表态没有任何删除入口() {
        setContent(BudgetUiState.Content(overview = overview(), categoryOptions = options))
        composeRule.onAllNodesWithText("删除", substring = true).assertCountEquals(0)
    }

    @Test
    fun A20_A21_删除按钮只在编辑弹层内() {
        var requested = false
        setContent(
            BudgetUiState.Content(
                overview = overview(),
                categoryOptions = options,
                editor = BudgetEditorUi(
                    kind = BudgetEditorKind.TOTAL,
                    budgetId = "budget-total",
                    amount = AmountInput("5000.00"),
                ),
            ),
            onRequestDelete = { requested = true },
        )

        composeRule.onNodeWithText("删除预算").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除预算").performClick()
        assertTrue(requested)
    }

    @Test
    fun A21_删除需要二次确认且文案固定() {
        var confirmed = false
        setContent(
            BudgetUiState.Content(
                overview = overview(),
                categoryOptions = options,
                editor = BudgetEditorUi(
                    kind = BudgetEditorKind.TOTAL,
                    budgetId = "budget-total",
                    amount = AmountInput("5000.00"),
                    confirmingDelete = true,
                ),
            ),
            onConfirmDelete = { confirmed = true },
        )

        composeRule.onNodeWithText("删除这笔预算？").assertIsDisplayed()
        composeRule.onNodeWithText("删除后本月不再显示该预算").assertIsDisplayed()
        composeRule.onNodeWithText("删除").performClick()
        assertTrue(confirmed)
    }

    // ------------------------------------------------------------------ 新增分类预算弹层

    @Test
    fun 新增分类预算时可选择分类() {
        var selected: BudgetCategoryOption? = null
        setContent(
            BudgetUiState.Content(
                overview = overview(),
                categoryOptions = options,
                editor = BudgetEditorUi(kind = BudgetEditorKind.CATEGORY_ADD),
            ),
            onSelectCategory = { selected = it },
        )

        composeRule.onNodeWithText("添加分类预算").assertIsDisplayed()
        composeRule.onNodeWithText("选择分类").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("交通").performClick()

        assertEquals("cat-traffic", selected?.id)
    }

    @Test
    fun 编辑分类预算弹层显示分类名与金额() {
        setContent(
            BudgetUiState.Content(
                overview = overview(),
                categoryOptions = options,
                editor = BudgetEditorUi(
                    kind = BudgetEditorKind.CATEGORY_EDIT,
                    budgetId = "budget-cat-food",
                    categoryId = "cat-food",
                    categoryName = "餐饮",
                    amount = AmountInput("1500.00"),
                ),
            ),
        )
        composeRule.onNodeWithText("分类预算 · 餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("¥1500.00").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
    }

    @Test
    fun 保存失败错误就地提示() {
        setContent(
            BudgetUiState.Content(
                overview = overview(),
                categoryOptions = options,
                editor = BudgetEditorUi(
                    kind = BudgetEditorKind.TOTAL,
                    amount = AmountInput("abc"),
                    error = "请输入有效金额",
                ),
            ),
        )
        composeRule.onNodeWithText("请输入有效金额").assertIsDisplayed()
    }
}
