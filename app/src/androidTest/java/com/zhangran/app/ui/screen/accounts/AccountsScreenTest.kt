/**
 * 账户管理页测试（`M2d-2` §5；覆盖 A1–A4、A6、A7、A9–A13、A15 的可测部分）。
 *
 * 页面是无状态 `AccountsContent`，编辑弹层由 `Content.editor` 驱动；
 * 末尾另有一条**真实 Room** 的默认账户唯一性测试（A5），验证 `setAsDefault` 的 `@Transaction`。
 */
package com.zhangran.app.ui.screen.accounts

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zhangran.app.data.local.ZhangRanDatabase
import com.zhangran.app.data.local.entity.AccountEntity
import com.zhangran.app.data.repository.RoomAccountRepository
import com.zhangran.app.domain.model.AccountType
import com.zhangran.app.domain.model.PaymentChannel
import com.zhangran.app.domain.usecase.SetDefaultAccountResult
import com.zhangran.app.domain.usecase.SetDefaultAccountUseCase
import com.zhangran.app.ui.theme.ZhangRanTheme
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountsScreenTest {

    @get:Rule
    val timeout: Timeout = Timeout.seconds(90)

    @get:Rule
    val composeRule = createComposeRule()

    private var database: ZhangRanDatabase? = null

    @After
    fun tearDown() {
        database?.close()
    }

    private fun item(
        id: String,
        name: String,
        type: AccountType,
        channel: PaymentChannel? = null,
        last4: String? = null,
        enabled: Boolean = true,
        isDefault: Boolean = false,
        sortOrder: Int = 0,
    ) = AccountUiItem(
        id = id,
        name = name,
        type = type,
        channel = channel,
        last4 = last4,
        enabled = enabled,
        isDefault = isDefault,
        sortOrder = sortOrder,
    )

    private fun groups() = listOf(
        AccountGroupUi(
            title = "现金类",
            items = listOf(
                item("acc-cash", "现金", AccountType.CASH, isDefault = true, sortOrder = 0),
            ),
        ),
        AccountGroupUi(
            title = "银行卡",
            items = listOf(
                item(
                    "acc-bank",
                    "招商银行储蓄卡",
                    AccountType.DEBIT_CARD,
                    channel = PaymentChannel.BANK,
                    last4 = "8823",
                    sortOrder = 1,
                ),
                item(
                    "acc-old",
                    "停用卡",
                    AccountType.DEBIT_CARD,
                    enabled = false,
                    sortOrder = 2,
                ),
            ),
        ),
        AccountGroupUi(
            title = "第三方支付",
            items = listOf(
                item(
                    "acc-alipay",
                    "支付宝",
                    AccountType.THIRD_PARTY,
                    channel = PaymentChannel.ALIPAY,
                    sortOrder = 3,
                ),
            ),
        ),
    )

    private fun content(editor: AccountEditorUi? = null) =
        AccountsUiState.Content(groups = groups(), editor = editor)

    private fun setContent(
        state: AccountsUiState,
        onAdd: () -> Unit = {},
        onEdit: (AccountUiItem) -> Unit = {},
        onMove: (Boolean) -> Unit = {},
        onSave: () -> Unit = {},
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            ZhangRanTheme(darkTheme = false) {
                AccountsContent(
                    state = state,
                    onAdd = onAdd,
                    onEdit = onEdit,
                    onMove = onMove,
                    onSave = onSave,
                    onRetry = onRetry,
                )
            }
        }
    }

    // ------------------------------------------------------------------ A1 分组

    @Test
    fun A1_按类型分组展示且空分组不显示() {
        setContent(
            AccountsUiState.Content(
                groups = listOf(
                    AccountGroupUi(
                        title = "现金类",
                        items = listOf(item("acc-cash", "现金", AccountType.CASH, isDefault = true)),
                    ),
                    AccountGroupUi(
                        title = "银行卡",
                        items = listOf(item("acc-bank", "招商银行储蓄卡", AccountType.DEBIT_CARD, last4 = "8823")),
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("现金类").assertIsDisplayed()
        composeRule.onNodeWithText("银行卡").assertIsDisplayed()
        composeRule.onNodeWithText("第三方支付").assertDoesNotExist()
    }

    @Test
    fun A1_行展示名称与副行组合() {
        setContent(content())

        composeRule.onNodeWithText("现金").assertIsDisplayed()
        composeRule.onNodeWithText("默认账户").assertIsDisplayed()
        composeRule.onNodeWithText("尾号 8823").assertIsDisplayed()
        composeRule.onNodeWithText("已禁用").assertIsDisplayed()
    }

    @Test
    fun A12_不展示本月支出() {
        setContent(content())
        composeRule.onAllNodesWithText("本月支出", substring = true).assertCountEquals(0)
    }

    @Test
    fun 点击行触发编辑回调() {
        var edited: AccountUiItem? = null
        setContent(content(), onEdit = { edited = it })

        composeRule.onNodeWithContentDescription("支付宝，第三方支付账户，双击编辑").performClick()

        assertEquals("acc-alipay", edited?.id)
    }

    @Test
    fun A7_禁用行的无障碍描述含已禁用() {
        setContent(content())
        composeRule
            .onNodeWithContentDescription("停用卡，储蓄卡账户，已禁用，双击编辑")
            .assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A2 添加

    @Test
    fun A2_添加弹层可填名称类型渠道并选择默认() {
        setContent(
            content(
                AccountEditorUi(
                    editingId = null,
                    type = AccountType.CASH,
                    name = "",
                    channel = null,
                ),
            ),
        )

        composeRule.onNodeWithText("添加账户").assertIsDisplayed()
        composeRule.onNodeWithText("类型").assertIsDisplayed()
        composeRule.onNodeWithText("渠道").assertIsDisplayed()
        composeRule.onNodeWithText("设为默认账户").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("保存").assertIsDisplayed()
        // 现金类型不显示尾号（V2）
        composeRule.onNodeWithText("尾号（4 位数字）").assertDoesNotExist()
    }

    @Test
    fun A2_点击添加触发回调() {
        var added = false
        setContent(content(), onAdd = { added = true })

        composeRule.onNodeWithContentDescription("添加账户").performClick()

        assertTrue(added)
    }

    // ------------------------------------------------------------------ A3 编辑

    @Test
    fun A3_编辑时类型只读且银行卡可见尾号() {
        setContent(
            content(
                AccountEditorUi(
                    editingId = "acc-bank",
                    type = AccountType.DEBIT_CARD,
                    name = "招商银行储蓄卡",
                    channel = PaymentChannel.BANK,
                    last4 = "8823",
                ),
            ),
        )

        composeRule.onNodeWithText("编辑账户").assertIsDisplayed()
        composeRule.onNodeWithText("类型（不可修改）").assertIsDisplayed()
        composeRule.onNodeWithText("尾号（4 位数字）").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("启用").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("排序").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("设为默认").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun A3_保存与上移回调可用() {
        var saved = false
        var movedUp: Boolean? = null
        setContent(
            content(
                AccountEditorUi(
                    editingId = "acc-alipay",
                    type = AccountType.THIRD_PARTY,
                    name = "支付宝",
                    channel = PaymentChannel.ALIPAY,
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

    // ------------------------------------------------------------------ A4 / A6 错误提示

    @Test
    fun A4_重名错误在弹层内提示() {
        setContent(
            content(
                AccountEditorUi(
                    editingId = null,
                    type = AccountType.CASH,
                    name = "现金",
                    error = "已存在同名账户",
                ),
            ),
        )
        composeRule.onNodeWithText("已存在同名账户").assertIsDisplayed()
    }

    @Test
    fun A6_禁用最后一个启用账户的提示就地展示() {
        setContent(
            content(
                AccountEditorUi(
                    editingId = "acc-cash",
                    type = AccountType.CASH,
                    name = "现金",
                    enabled = true,
                    error = "至少保留一个启用账户",
                ),
            ),
        )
        composeRule.onNodeWithText("至少保留一个启用账户").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A9 无删除

    @Test
    fun A9_页面与弹层都没有删除入口() {
        setContent(
            content(
                AccountEditorUi(
                    editingId = "acc-cash",
                    type = AccountType.CASH,
                    name = "现金",
                ),
            ),
        )
        composeRule.onAllNodesWithText("删除", substring = true).assertCountEquals(0)
    }

    // ------------------------------------------------------------------ A13 三态

    @Test
    fun A13_空数据提示初始化异常() {
        setContent(AccountsUiState.Empty)
        composeRule.onNodeWithText("账户数据异常").assertIsDisplayed()
        composeRule.onNodeWithText("暂无账户").assertDoesNotExist()
    }

    @Test
    fun A13_错误态可重试() {
        var retried = false
        setContent(AccountsUiState.Error("数据库打开失败"), onRetry = { retried = true })

        composeRule.onNodeWithText("数据库打开失败").assertIsDisplayed()
        composeRule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }

    @Test
    fun A13_加载态渲染标题() {
        setContent(AccountsUiState.Loading)
        composeRule.onNodeWithText("账户管理").assertIsDisplayed()
    }

    // ------------------------------------------------------------------ A5 真实 Room 的默认唯一

    @Test
    fun A5_真实Room上切换默认账户保持唯一() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            ZhangRanDatabase::class.java,
        ).allowMainThreadQueries().build()
        database = db
        val repository = RoomAccountRepository(db.accountDao())
        db.accountDao().insert(cashAccount())
        db.accountDao().insert(alipayAccount())

        val useCase = SetDefaultAccountUseCase(
            accounts = repository,
            clock = Clock.fixed(Instant.ofEpochMilli(NOW), ZoneId.of("Asia/Shanghai")),
        )

        assertTrue(useCase("acc-alipay") is SetDefaultAccountResult.Success)
        assertEquals(1, db.accountDao().countDefaults())
        assertEquals("acc-alipay", db.accountDao().findDefault()?.id)

        // 再切回现金：仍然只有一个默认（事务先清后置）
        assertTrue(useCase("acc-cash") is SetDefaultAccountResult.Success)
        assertEquals(1, db.accountDao().countDefaults())
        assertEquals("acc-cash", db.accountDao().findDefault()?.id)
        assertEquals("acc-cash", repository.usableDefault()?.id)
    }

    private companion object {
        const val NOW = 1_800_000_000_000L

        fun cashAccount() = AccountEntity(
            id = "acc-cash",
            name = "现金",
            type = AccountType.CASH,
            isDefault = true,
            sortOrder = 0,
            createdAt = NOW,
            updatedAt = NOW,
        )

        fun alipayAccount() = AccountEntity(
            id = "acc-alipay",
            name = "支付宝",
            type = AccountType.THIRD_PARTY,
            channel = PaymentChannel.ALIPAY,
            sortOrder = 1,
            createdAt = NOW,
            updatedAt = NOW,
        )
    }
}
