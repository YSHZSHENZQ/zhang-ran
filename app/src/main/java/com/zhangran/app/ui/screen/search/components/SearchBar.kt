/**
 * 搜索输入框（`M4e` §4.4；原型 `.search-bar`）。
 *
 * 玻璃底 + 🔍 + 输入 + 清除（`✕`）；关键字由外部持有，本组件无状态。
 */
package com.zhangran.app.ui.screen.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.zhangran.app.ui.screen.search.SearchLayout
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.inputFillColor
import com.zhangran.app.ui.theme.primaryColor
import com.zhangran.app.ui.theme.textDisabledColor
import com.zhangran.app.ui.theme.textPrimaryColor

/**
 * @param value 当前关键字
 * @param onValueChange 每次输入
 * @param onClear 点 `✕`
 */
@Composable
fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ZhangRanTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SearchLayout.pagePaddingHorizontalDp,
                end = SearchLayout.pagePaddingHorizontalDp,
                bottom = SearchLayout.searchBarMarginBottomDp,
            )
            .heightIn(min = SearchLayout.searchBarMinHeightDp)
            .clip(ZhangRanTheme.shapes.card)
            .background(color = colors.inputFillColor)
            .padding(
                horizontal = SearchLayout.searchBarPaddingHorizontalDp,
                vertical = SearchLayout.searchBarPaddingVerticalDp,
            )
            .semantics { contentDescription = "搜索商户、备注" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SearchLayout.searchBarGapDp),
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = SearchLayout.searchIconSizeSp.sp,
            ),
        )
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = "搜索商户、备注、金额…",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = SearchLayout.searchFontSizeSp.sp,
                        fontFamily = FontFamily.Default,
                    ),
                    color = colors.textDisabledColor,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colors.textPrimaryColor,
                    fontSize = SearchLayout.searchFontSizeSp.sp,
                    fontFamily = FontFamily.Default,
                ),
                cursorBrush = SolidColor(colors.primaryColor),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(SearchLayout.clearButtonSizeDp)
                    .clip(ZhangRanTheme.shapes.badge)
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "清除搜索关键字" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = SearchLayout.clearGlyphSizeSp.sp,
                    ),
                    color = colors.textDisabledColor,
                )
            }
        }
    }
}
