/**
 * 交易详情页 UI 测试（`M2b` §5；对应 A1/A2/A3/A4/A6/A11/A14/A15）。
 *
 * 覆盖：
 * - 13 项展示：**「支付方式」存在且没有并存的「账户」行**（A1）；空字段隐藏（A2）；
 *   「引擎版本」而非 "Parser Version"（A3）；
 * - 7 个字段点击即编辑（A4）：分类/类型/金额/时间/商户/备注/支付方式；
 * - 类型编辑器：`TRANSFER` / `REFUND` 置灰并提示（A6）；
 * - 删除二次确认文案不含"无法恢复"（A11）；
 * - Submitting 期间不能打开删除确认（A14）；
 * - 无障碍：可编辑行读作"…，双击修改"（A15）。
 *
 * 纪律（`docs/09` §5）：每个测试方法只 `setContent` 一次，标准断言 + `Timeout` 兜底。
 */
package com.zhangran.app.ui.screen.detail

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.screen.add.AccountOption
import com.zhangran.app.ui.screen.add.CategoryOption
import com.zhangran.app.ui.theme.ZhangRanTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionDetailScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(60)

    @get:Rule
    val composeRule = createComposeRule()

    private fun detail(
        type: TransactionType = TransactionType.EXPENSE,
        merchant: String? = "星巴克",
        categoryName: String? = "餐饮",
        accountName: String? = "现金",
        confidence: Double? = null,
        engineVersion: String? = null,
        matchedRuleId: String? = null,
        externalTransactionId: String? = null,
        evidenceId: String? = null,
        evidenceSummary: String? = null,
        note: String? = "下午茶",
    ) = TransactionDetailUi(
        id = "tx-1",
        type = type,
        amountMinor = 3_850L,
        merchant = merchant,
        categoryId = "cat-food",
        categoryName = categoryName,
        categoryIcon = "food",
        accountId = "acc-cash",
        accountName = accountName,
        transactionTime = 1_791_610_182_000L,
        transactionTimeZone = "Asia/Shanghai",
        note = note,
        source = CaptureSource.MANUAL,
        state = TransactionState.CONFIRMED,
        confidence = confidence,
        engineVersion = engineVersion,
        matchedRuleId = matchedRuleId,
        externalTransactionId = externalTransactionId,
        evidenceId = evidenceId,
        evidenceSummary = evidenceSummary,
        categories = listOf(
            CategoryOption(id = "cat-food", name = "餐饮", icon = "food"),
            CategoryOption(id = "cat-other", name = "其他", icon = "other", isFallback = true),
        ),
        accounts = listOf(
            AccountOption(id = "acc-cash", name = "现金"),
            AccountOption(id = "acc-bank", name = "银行卡"),
        ),
    )

    private fun setContent(state: TransactionDetailUiState, onDelete: () -> Unit = {}) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                TransactionDetailContent(state = state, onDelete = onDelete)
            }
        }
    }

    // ------------------------------------------------------------------ A1 展示

    @Test
    fun A1_展示支付方式且不存在并存的账户行() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithText("类型").assertIsDisplayed()
        composeRule.onNodeWithText("分类").assertIsDisplayed()
        composeRule.onNodeWithText("支付方式").assertIsDisplayed()
        composeRule.onNodeWithText("来源").assertIsDisplayed()
        composeRule.onNodeWithText("交易状态").assertIsDisplayed()
        composeRule.onNodeWithText("账户").assertDoesNotExist()
    }

    @Test
    fun A1_支付方式行显示账户名() {
        setContent(TransactionDetailUiState.Content(detail(accountName = "现金")))
        composeRule.onNodeWithText("现金").assertIsDisplayed()
    }

    @Test
    fun A2_空字段隐藏且证据卡片不显示() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithText("置信度").assertDoesNotExist()
        composeRule.onNodeWithText("引擎版本").assertDoesNotExist()
        composeRule.onNodeWithText("命中规则").assertDoesNotExist()
        composeRule.onNodeWithText("交易单号").assertDoesNotExist()
        composeRule.onNodeWithText("自动解析证据").assertDoesNotExist()
    }

    @Test
    fun A2_有证据时显示置信度与证据卡片() {
        setContent(
            TransactionDetailUiState.Content(
                detail(
                    confidence = 0.96,
                    engineVersion = "1.3.0",
                    matchedRuleId = "alipay-payment-success-v3",
                    externalTransactionId = "20240628...8823",
                    evidenceId = "ev-1",
                    evidenceSummary = "sourcePackage: com.eg.android.AlipayGphone",
                ),
            ),
        )
        composeRule.onNodeWithText("置信度").assertIsDisplayed()
        composeRule.onNodeWithText("96%").assertIsDisplayed()
        composeRule.onNodeWithText("自动解析证据").assertIsDisplayed()
        composeRule.onNodeWithText("sourcePackage: com.eg.android.AlipayGphone").assertIsDisplayed()
    }

    @Test
    fun A3_使用引擎版本标签且不出现ParserVersion() {
        setContent(TransactionDetailUiState.Content(detail(engineVersion = "1.3.0")))
        composeRule.onNodeWithText("引擎版本").assertIsDisplayed()
        composeRule.onNodeWithText("1.3.0").assertIsDisplayed()
        composeRule.onNodeWithText("Parser Version").assertDoesNotExist()
    }

    // ------------------------------------------------------------------ A4 字段级编辑

    @Test
    fun A4_点击分类行打开分类编辑器并复用分类网格() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithContentDescription("分类，餐饮，双击修改").performClick()
        composeRule.onNodeWithText("修改分类").assertIsDisplayed()
        // CategoryGrid 的选项（复用 M2a 组件）
        composeRule.onNodeWithContentDescription("餐饮 分类 已选中").assertIsDisplayed()
        composeRule.onNodeWithText("保存修改").assertIsDisplayed()
    }

    @Test
    fun A4_点击商户行打开商户编辑器() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithContentDescription("商户，星巴克，双击修改").performClick()
        composeRule.onNodeWithText("修改商户").assertIsDisplayed()
    }

    @Test
    fun A4_点击金额打开金额编辑器并复用数字键盘() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithContentDescription("金额，-38.50 元").performClick()
        composeRule.onNodeWithText("修改金额").assertIsDisplayed()
        // NumberPad 的保存键（复用 M2a 组件）
        composeRule.onNodeWithText("保存").assertIsDisplayed()
    }

    @Test
    fun A4_点击备注卡打开备注编辑器() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithContentDescription("备注，下午茶，双击修改").performClick()
        composeRule.onNodeWithText("修改备注").assertIsDisplayed()
    }

    @Test
    fun A4_点击时间打开日期时间编辑器() {
        setContent(TransactionDetailUiState.Content(detail()))
        val timeText = formatDetailDateTime(1_791_610_182_000L)
        composeRule.onNodeWithContentDescription("时间，$timeText，双击修改").performClick()
        composeRule.onNodeWithText("修改时间").assertIsDisplayed()
    }

    @Test
    fun A6_类型编辑器只放开支出收入并提示转账退款不支持() {
        setContent(TransactionDetailUiState.Content(detail()))
        composeRule.onNodeWithContentDescription("类型，支出，双击修改").performClick()
        composeRule.onNodeWithText("修改类型").assertIsDisplayed()
        // 转账与退款各一行提示（A6）
        composeRule.onAllNodesWithText("暂不支持（转账/退款编辑）", substring = true)
            .assertCountEquals(2)
    }

    // ------------------------------------------------------------------ A11 删除

    @Test
    fun A11_删除确认文案不承诺恢复路径也不写无法恢复() {
        var deleted = false
        setContent(TransactionDetailUiState.Content(detail()), onDelete = { deleted = true })

        composeRule.onNodeWithText("删除记录").performClick()
        composeRule.onNodeWithText("确认删除这笔交易？").assertIsDisplayed()
        composeRule.onNodeWithText("记录将从列表中移除。").assertIsDisplayed()
        composeRule.onNodeWithText("无法恢复", substring = true).assertDoesNotExist()

        composeRule.onNodeWithText("删除").performClick()
        assertTrue("确认后必须回调删除", deleted)
    }

    // ------------------------------------------------------------------ A14 状态

    @Test
    fun A14_提交中不能再次打开删除确认() {
        val submitting = TransactionDetailUiState.Submitting(detail())
        setContent(submitting)

        composeRule.onNodeWithText("交易详情").assertIsDisplayed()
        composeRule.onNodeWithText("删除记录").performClick()
        composeRule.onNodeWithText("确认删除这笔交易？").assertDoesNotExist()
    }

    @Test
    fun A14_错误态可重试() {
        var retried = false
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                TransactionDetailContent(
                    state = TransactionDetailUiState.Error("数据库打开失败"),
                    onRetry = { retried = true },
                )
            }
        }
        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue("重试必须回调", retried)
    }

    @Test
    fun 加载态渲染页面标题() {
        setContent(TransactionDetailUiState.Loading)
        composeRule.onNodeWithText("交易详情").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ 只读类型

    @Test
    fun 转账交易只读展示且行不可编辑() {
        setContent(
            TransactionDetailUiState.Content(
                detail(type = TransactionType.TRANSFER, categoryName = null),
            ),
        )
        // 不可编辑：点击"分类"行不会打开编辑器
        composeRule.onNodeWithText("分类").performClick()
        composeRule.onNodeWithText("修改分类").assertDoesNotExist()
        composeRule.onNodeWithText("暂不支持（转账/退款编辑）", substring = true).assertDoesNotExist()
    }
}
