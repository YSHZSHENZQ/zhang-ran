/**
 * 疑似重复对照（M3b）。
 *
 * 新记录和被比对的原记录并排，底部是「同一笔」「两笔」。
 * 没有交易 id 时只显示页面名，供页面冒烟测试使用。
 */
package com.zhangran.app.ui.screen.duplicateReview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.zhangran.app.domain.usecase.DuplicateReviewUseCase
import com.zhangran.app.ui.navigation.Destination
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.errorColor
import com.zhangran.app.ui.theme.mdDp
import com.zhangran.app.ui.theme.pagePaddingDp
import com.zhangran.app.ui.theme.smDp
import com.zhangran.app.ui.theme.surfaceColor
import com.zhangran.app.ui.theme.textPrimaryColor
import com.zhangran.app.ui.theme.textSecondaryColor
import com.zhangran.app.ui.theme.xsDp
import com.zhangran.app.ui.theme.zhangRanBackground
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@Composable
fun DuplicateReviewScreen(
    transactionId: String = "",
    onDone: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    if (transactionId.isBlank()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .zhangRanBackground(colors)
                .padding(ZhangRanTheme.spacing.pagePaddingDp),
        ) {
            Text(
                text = Destination.DuplicateReview.title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimaryColor,
            )
        }
        return
    }
    val context = LocalContext.current
    val reviewViewModel: DuplicateReviewViewModel = viewModel(
        key = transactionId,
        factory = viewModelFactory {
            initializer {
                val entry = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    DuplicateReviewEntryPoint::class.java,
                )
                DuplicateReviewViewModel(
                    transactionId = transactionId,
                    review = entry.duplicateReviewUseCase(),
                )
            }
        },
    )
    val state by reviewViewModel.uiState.collectAsState()
    DuplicateReviewContent(
        state = state,
        onSame = { reviewViewModel.markSame(onDone) },
        onDifferent = { reviewViewModel.markDifferent(onDone) },
    )
}

@Composable
fun DuplicateReviewContent(
    state: DuplicateReviewUiState,
    onSame: () -> Unit = {},
    onDifferent: () -> Unit = {},
) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .zhangRanBackground(colors)
            .verticalScroll(rememberScrollState())
            .padding(ZhangRanTheme.spacing.pagePaddingDp),
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
    ) {
        Text(
            text = Destination.DuplicateReview.title,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimaryColor,
        )
        when (state) {
            DuplicateReviewUiState.Loading -> Text("正在读取", color = colors.textSecondaryColor)
            is DuplicateReviewUiState.Missing -> Text(state.message, color = colors.textSecondaryColor)
            is DuplicateReviewUiState.Ready -> {
                state.content.message?.let { Text(it, color = colors.errorColor) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp),
                ) {
                    SideCard(state.content.fresh, Modifier.weight(1f))
                    SideCard(state.content.existing, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.smDp)) {
                    Button(
                        onClick = onSame,
                        enabled = !state.content.busy,
                        modifier = Modifier.semantics { contentDescription = "同一笔" },
                    ) { Text("同一笔") }
                    Button(
                        onClick = onDifferent,
                        enabled = !state.content.busy,
                        modifier = Modifier.semantics { contentDescription = "两笔" },
                    ) { Text("两笔") }
                }
            }
        }
    }
}

@Composable
private fun SideCard(side: DuplicateSideUi, modifier: Modifier = Modifier) {
    val colors = ZhangRanTheme.colors
    Column(
        modifier = modifier
            .background(colors.surfaceColor, ZhangRanTheme.shapes.card)
            .padding(ZhangRanTheme.spacing.mdDp)
            .semantics { contentDescription = "${side.heading}，${side.merchant}，${side.amount}" },
        verticalArrangement = Arrangement.spacedBy(ZhangRanTheme.spacing.xsDp),
    ) {
        Text(side.heading, style = MaterialTheme.typography.titleMedium, color = colors.textPrimaryColor)
        Fact("商户", side.merchant)
        Fact("金额", side.amount)
        Fact("方向", side.direction)
        Fact("时间", side.time)
        Fact("来源", side.source)
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val colors = ZhangRanTheme.colors
    Text(
        text = "$label $value",
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondaryColor,
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DuplicateReviewEntryPoint {
    fun duplicateReviewUseCase(): DuplicateReviewUseCase
}
