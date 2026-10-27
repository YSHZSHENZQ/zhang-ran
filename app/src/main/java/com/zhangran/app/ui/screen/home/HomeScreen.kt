/**
 * 首页（`docs/06` §4.1 列表区、§5 页面统一状态）。
 *
 * 分层（`docs/04` §4、§20）：
 * - [HomeScreen] 只负责「取 ViewModel + 收集状态」，**不含业务判断**；
 * - [HomeContent] 是**无状态**可组合函数，接收 [HomeUiState] 与事件回调，
 *   便于 Compose 测试直接渲染四态（A9）；
 * - 页面**始终渲染「首页」标题**：既有 `ScreensSmokeTest` 以「页面名可见」作为渲染成功判据，
 *   四种状态下都必须成立。
 *
 * 本任务只做**只读列表**；详情跳转、搜索、编辑、删除属后续任务。
 */
package com.zhangran.app.ui.screen.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.di.ApplicationScope
import com.zhangran.app.domain.usecase.ObserveTransactionsUseCase
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.dividerColor
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.lgDp
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.xsDp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope

/** 页面标题（同时是 `ScreensSmokeTest` 的渲染判据）。 */
private const val PAGE_TITLE: String = "首页"

/**
 * 首页入口（无参，供导航直接调用）。
 */
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    HomeEntryPoint::class.java,
                )
                HomeViewModel(
                    observeTransactions = entryPoint.observeTransactionsUseCase(),
                    scope = entryPoint.applicationScope(),
                )
            }
        },
    )
    val state by homeViewModel.uiState.collectAsState()
    HomeContent(state = state, onRetry = homeViewModel::refresh)
}

/**
 * 首页内容（无状态）。
 *
 * @param state 四种 UI 状态
 * @param onRetry 错误态的重试回调
 */
@Composable
fun HomeContent(
    state: HomeUiState,
    onRetry: () -> Unit = {},
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZhangRanTheme.spacing.lgDp),
    ) {
        Text(
            text = PAGE_TITLE,
            style = MaterialTheme.typography.titleLarge,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
        Spacer(modifier = Modifier.height(ZhangRanTheme.spacing.mdDp))

        when (state) {
            is HomeUiState.Loading -> CenteredBox {
                CircularProgressIndicator()
            }

            is HomeUiState.Empty -> CenteredBox {
                Text(
                    text = "还没有记录",
                    color = ZhangRanTheme.colors.textSecondaryColor,
                )
            }

            is HomeUiState.Error -> CenteredBox {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
                ) {
                    Text(
                        text = state.message,
                        color = ZhangRanTheme.colors.errorColor,
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = "重试")
                    }
                }
            }

            is HomeUiState.Content -> TransactionList(items = state.items)
        }
    }
}

/**
 * 账务列表（`docs/06` §4.1：金额、类型、商户/备注摘要、时间、分类、账户）。
 */
@Composable
private fun TransactionList(items: List<TransactionListItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = ZhangRanTheme.spacing.lgDp),
    ) {
        items(items = items, key = { it.id }) { item ->
            TransactionRow(item = item)
            HorizontalDivider(color = ZhangRanTheme.colors.dividerColor)
        }
    }
}

/** 单行展示。 */
@Composable
private fun TransactionRow(item: TransactionListItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = ZhangRanTheme.spacing.mdDp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                color = ZhangRanTheme.colors.textPrimaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(ZhangRanTheme.spacing.xsDp))
            Text(
                text = subtitleOf(item),
                style = MaterialTheme.typography.bodySmall,
                color = ZhangRanTheme.colors.textSecondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.padding(horizontal = ZhangRanTheme.spacing.smDp))
        Text(
            text = item.amountText,
            style = MaterialTheme.typography.bodyLarge,
            color = ZhangRanTheme.colors.textPrimaryColor,
        )
    }
}

/** 副标题：类型 · 分类 · 账户 · 时间（空字段自动省略）。 */
private fun subtitleOf(item: TransactionListItem): String {
    val parts = buildList {
        add(item.typeText)
        item.categoryName?.takeIf { it.isNotBlank() }?.let { add(it) }
        item.accountName?.takeIf { it.isNotBlank() }?.let { add(it) }
        add(item.timeText)
    }
    return parts.joinToString(separator = " · ")
}

/** 居中容器（Loading / Empty / Error 共用）。 */
@Composable
private fun CenteredBox(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * Hilt 入口（本任务不引入 `hilt-navigation-compose`，故用 EntryPoint 手工构造 ViewModel）。
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeEntryPoint {
    fun observeTransactionsUseCase(): ObserveTransactionsUseCase

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
