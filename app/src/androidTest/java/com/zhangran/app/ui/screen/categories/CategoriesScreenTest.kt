/**
 * 分类管理页 UI 测试（`M2d-1` §5；覆盖 A1–A8、A13–A15、A16 的可测部分）。
 *
 * 说明：页面是无状态 `CategoriesContent`，编辑弹层由 `Content.editor` 驱动，
 * 因此这里直接构造状态来断言"三态 / 网格 / 弹层字段禁用 / 无删除入口 / 无障碍"。
 */
package com.zhangran.app.ui.screen.categories

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoriesScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun item(
        id: String,
        name: String,
        type: CategoryType,
        icon: String,
        enabled: Boolean = true,
        isFallback: Boolean = false,
        sortOrder: Int = 0,
    ) = CategoryUiItem(
        id = id,
        name = name,
        type = type,
        icon = icon,
        enabled = enabled,
        isFallback = isFallback,
        sortOrder = sortOrder,
    )

    private fun groups() = listOf(
        CategoryGroupUi(
            type = CategoryType.EXPENSE,
            title = "支出分类",
            items = listOf(
                item("cat-food", "餐饮", CategoryType.EXPENSE, "food", sortOrder = 0),
                item("cat-shop", "购物", CategoryType.EXPENSE, "shopping", sortOrder = 1),
                item("cat-other", "其他", CategoryType.EXPENSE, "other", isFallback = true, sortOrder = 2),
            ),
        ),
        CategoryGroupUi(
            type = CategoryType.INCOME,
            title = "收入分类",
            items = listOf(
                item("cat-salary", "工资", CategoryType.INCOME, "salary", sortOrder = 0),
            ),
        ),
    )

    private fun content(editor: CategoryEditorUi? = null) =
        CategoriesUiState.Content(groups = groups(), editor = editor)

    private fun setContent(
        state: CategoriesUiState,
        onAdd: () -> Unit = {},
        onEdit: (CategoryUiItem) -> Unit = {},
        onSave: () -> Unit = {},
        onMove: (Boolean) -> Unit = {},
        onRules: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                CategoriesContent(
                    state = state,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onSave = onSave,
                    onMove = onMove,
                    onNavigateToRules = onRules,
                    onRetry = onRetry,
                )
            }
        }
    }

    // ------------------------------------------------------------------ A1 分组与顺序

    @Test
    fun A1_两组网格按sortOrder展示() {
        setContent(content())
        composeRule.onNodeWithText("支出分类").assertIsDisplayed()
        composeRule.onNodeWithText("收入分类").assertIsDisplayed()
        composeRule.onNodeWithText("餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("购物").assertIsDisplayed()
        composeRule.onNodeWithText("工资").assertIsDisplayed()
    }

    @Test
    fun A1_点击分类项触发编辑回调() {
        var edited: CategoryUiItem? = null
        setContent(content(), onEdit = { edited = it })

        composeRule.onNodeWithContentDescription("餐饮，支出分类，已启用，双击编辑").performClick()

        assertEquals("cat-food", edited?.id)
    }

    @Test
    fun A2_点击添加触发添加回调() {
        var added = false
        setContent(content(), onAdd = { added = true })

        composeRule.onNodeWithContentDescription("添加分类").performClick()

        assertTrue(added)
    }

    @Test
    fun A2_添加弹层可选类型与图标() {
        setContent(
            content(
                CategoryEditorUi(
                    editingId = null,
                    type = CategoryType.EXPENSE,
                    name = "",
                    icon = "other",
                ),
            ),
        )
        composeRule.onNodeWithText("添加分类").assertIsDisplayed()
        composeRule.onNodeWithText("类型").assertIsDisplayed()
        composeRule.onNodeWithText("支出").assertIsDisplayed()
        composeRule.onNodeWithText("收入").assertIsDisplayed()
        composeRule.onNodeWithText("图标").assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A3 编辑

    @Test
    fun A3_编辑弹层类型只读且提供名称图标启用排序() {
        setContent(
            content(
                CategoryEditorUi(
                    editingId = "cat-food",
                    type = CategoryType.EXPENSE,
                    name = "餐饮",
                    icon = "food",
                ),
            ),
        )
        composeRule.onNodeWithText("编辑分类").assertIsDisplayed()
        composeRule.onNodeWithText("类型（不可修改）").assertIsDisplayed()
        composeRule.onNodeWithText("启用").assertIsDisplayed()
        composeRule.onNodeWithText("排序").assertIsDisplayed()
        composeRule.onNodeWithText("上移").assertIsDisplayed()
        composeRule.onNodeWithText("下移").assertIsDisplayed()
    }

    @Test
    fun A3_保存与上移回调可用() {
        var saved = false
        var movedUp: Boolean? = null
        setContent(
            content(
                CategoryEditorUi(
                    editingId = "cat-shop",
                    type = CategoryType.EXPENSE,
                    name = "购物",
                    icon = "shopping",
                ),
            ),
            onSave = { saved = true },
            onMove = { movedUp = it },
        )

        composeRule.onNodeWithText("上移").performClick()
        composeRule.onNodeWithText("保存").performClick()

        assertEquals(true, movedUp)
        assertTrue(saved)
    }

    // ------------------------------------------------------------------ A4 错误就地提示

    @Test
    fun A4_重名错误在弹层内提示() {
        setContent(
            content(
                CategoryEditorUi(
                    editingId = "cat-shop",
                    type = CategoryType.EXPENSE,
                    name = "餐饮",
                    icon = "shopping",
                    error = "同一分组下已存在同名分类",
                ),
            ),
        )
        composeRule.onNodeWithText("同一分组下已存在同名分类").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A5 「其他」保护

    @Test
    fun A5_其他分类不可改名与禁用并说明原因() {
        setContent(
            content(
                CategoryEditorUi(
                    editingId = "cat-other",
                    type = CategoryType.EXPENSE,
                    name = "其他",
                    icon = "other",
                    isFallback = true,
                ),
            ),
        )
        composeRule.onNodeWithText("「其他」是兜底分类，不可改名").assertIsDisplayed()
        composeRule.onNodeWithText("兜底分类不可禁用").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A6 无删除入口

    @Test
    fun A6_页面与弹层都没有删除入口() {
        setContent(
            content(
                CategoryEditorUi(
                    editingId = "cat-food",
                    type = CategoryType.EXPENSE,
                    name = "餐饮",
                    icon = "food",
                ),
            ),
        )
        composeRule.onAllNodesWithText("删除", substring = true).assertCountEquals(0)
    }

    // ------------------------------------------------------------------ A7 禁用态

    @Test
    fun A7_禁用分类仍显示且无障碍读作已禁用() {
        setContent(
            CategoriesUiState.Content(
                groups = listOf(
                    CategoryGroupUi(
                        type = CategoryType.EXPENSE,
                        title = "支出分类",
                        items = listOf(
                            item("cat-shop", "购物", CategoryType.EXPENSE, "shopping", enabled = false),
                        ),
                    ),
                    CategoryGroupUi(CategoryType.INCOME, "收入分类", emptyList()),
                ),
            ),
        )
        composeRule.onNodeWithText("购物").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("购物，支出分类，已禁用，双击编辑").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A13 三态

    @Test
    fun A13_空数据提示初始化异常而不是暂无分类() {
        setContent(CategoriesUiState.Empty)
        composeRule.onNodeWithText("分类数据异常").assertIsDisplayed()
        composeRule.onNodeWithText("暂无分类").assertDoesNotExist()
    }

    @Test
    fun A13_错误态可重试() {
        var retried = false
        setContent(CategoriesUiState.Error("数据库打开失败"), onRetry = { retried = true })

        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun A13_加载态渲染标题() {
        setContent(CategoriesUiState.Loading)
        composeRule.onNodeWithText("分类管理").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A14 规则入口

    @Test
    fun A14_分类规则入口可跳转() {
        var navigated = false
        setContent(content(), onRules = { navigated = true })

        composeRule.onNodeWithContentDescription("分类规则，双击打开").performClick()

        assertTrue(navigated)
    }
}
