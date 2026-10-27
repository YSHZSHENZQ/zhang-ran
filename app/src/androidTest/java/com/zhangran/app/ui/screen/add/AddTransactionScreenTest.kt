/**
 * 记账表单的 instrumented 测试（任务书 M2a 的 A1、A5、A11）。
 *
 * 直接渲染**无状态**的 [AddTransactionContent]，不依赖 Hilt / 数据库；
 * 每个测试方法只 `setContent` 一次（`docs/09` 的 Compose 测试坑）。
 */
package com.zhangran.app.ui.screen.add

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddTransactionScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private companion object {
        const val ACCOUNT_ID = "acc-cash"
    }

    private fun categories(): List<CategoryOption> = listOf(
        CategoryOption(id = "cat-food", name = "餐饮", icon = "food"),
        CategoryOption(id = "cat-pet", name = "宠物", icon = "pet"),
        CategoryOption(id = "cat-other", name = "其他", icon = "other", isFallback = true),
    )

    private fun readyState(
        amount: AmountInput = AmountInput(),
        error: FormError? = null,
        saving: Boolean = false,
        selectedCategoryId: String? = null,
    ): AddTransactionUiState.Ready = AddTransactionUiState.Ready(
        type = TransactionType.EXPENSE,
        amount = amount,
        categories = categories(),
        selectedCategoryId = selectedCategoryId,
        accounts = listOf(AccountOption(id = ACCOUNT_ID, name = "现金")),
        selectedAccountId = ACCOUNT_ID,
        note = "",
        saving = saving,
        error = error,
    )

    // ------------------------------------------------------------ A1 四态

    @Test
    fun A1_加载中可渲染() {
        composeRule.setContent {
            ZhangRanTheme { AddTransactionContent(state = AddTransactionUiState.Loading) }
        }
        composeRule.onNodeWithText("记一笔").assertIsDisplayed()
    }

    @Test
    fun A1_加载失败可渲染并可重试() {
        composeRule.setContent {
            ZhangRanTheme {
                AddTransactionContent(state = AddTransactionUiState.LoadFailed("数据库打开失败"))
            }
        }
        composeRule.onNodeWithText("记一笔").assertIsDisplayed()
        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").assertIsDisplayed()
    }

    @Test
    fun A1_无分类数据可渲染并提示() {
        composeRule.setContent {
            ZhangRanTheme {
                AddTransactionContent(
                    state = readyState().copy(categories = emptyList()),
                )
            }
        }
        composeRule.onNodeWithText("暂无可用分类").assertIsDisplayed()
    }

    @Test
    fun A1_保存失败可渲染并就地提示() {
        composeRule.setContent {
            ZhangRanTheme {
                AddTransactionContent(state = readyState(error = FormError.CATEGORY_REQUIRED))
            }
        }
        composeRule.onNodeWithText("请选择分类").assertIsDisplayed()
        composeRule.onNodeWithText("记一笔").assertIsDisplayed()
    }

    // ------------------------------------------------------------ 正常内容

    @Test
    fun 正常态显示金额分类账户与键盘() {
        composeRule.setContent {
            ZhangRanTheme { AddTransactionContent(state = readyState()) }
        }
        composeRule.onNodeWithText("记一笔").assertIsDisplayed()
        composeRule.onNodeWithText("MANUAL ENTRY").assertIsDisplayed()
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithText("餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("宠物").assertIsDisplayed()
        // A5：默认账户名可见
        composeRule.onNodeWithText("现金 ›").assertIsDisplayed()
        // 键盘存在
        composeRule.onNodeWithContentDescription("保存").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("删除").assertIsDisplayed()
    }

    @Test
    fun A11_分类来自数据而非硬编码_其他为兜底项() {
        composeRule.setContent {
            ZhangRanTheme { AddTransactionContent(state = readyState()) }
        }
        // 数据里只有 3 个分类，页面必须只渲染这 3 个
        composeRule.onNodeWithText("餐饮").assertIsDisplayed()
        composeRule.onNodeWithText("其他").assertIsDisplayed()
        // 未出现在数据中的分类不得渲染
        composeRule.onNodeWithText("交通").assertDoesNotExist()
    }

    // ------------------------------------------------------------ 交互

    @Test
    fun 点击数字键金额随之更新() {
        composeRule.setContent {
            var state by remember { mutableStateOf<AddTransactionUiState>(readyState()) }
            ZhangRanTheme {
                AddTransactionContent(
                    state = state,
                    onDigit = { digit ->
                        val current = state as AddTransactionUiState.Ready
                        state = current.copy(amount = current.amount.digit(digit))
                    },
                )
            }
        }
        composeRule.onNodeWithText("0.00").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("1").performClick()
        // 金额区的无障碍描述是唯一的（键盘键也有 1，故不能用 onNodeWithText）
        composeRule.onNodeWithContentDescription("金额 1 元").assertIsDisplayed()
    }

    @Test
    fun 点击类型胶囊切换为收入() {
        composeRule.setContent {
            var state by remember { mutableStateOf<AddTransactionUiState>(readyState()) }
            ZhangRanTheme {
                AddTransactionContent(
                    state = state,
                    onToggleType = {
                        val current = state as AddTransactionUiState.Ready
                        state = current.copy(type = TransactionType.INCOME)
                    },
                )
            }
        }
        composeRule.onNodeWithText("支出 ⇄").assertIsDisplayed()
        composeRule.onNodeWithText("支出 ⇄").performClick()
        composeRule.onNodeWithText("收入 ⇄").assertIsDisplayed()
    }
}
