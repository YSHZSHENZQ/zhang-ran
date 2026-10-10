/**
 * 占位页面的统一实现（T1b 专用）。
 *
 * 依据：`docs/06` §7（原型中的示例数据不作为依据）、`AGENTS.md` §10（不得在 Composable 中
 * 写业务规则、硬编码颜色/尺寸、假数据）。
 *
 * 规则：
 * - 骨架期只显示页面名称，**不显示任何示例金额、统计数字或业务文案**；
 * - 尺寸只引用 `docs/05` §5 的 spacing token，颜色只引用 `docs/05` §3 的颜色 token；
 * - 本组件在后续任务被真实页面替换时可直接删除。
 */
package com.zhangran.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.zhangran.app.ui.theme.ZhangRanTheme
import com.zhangran.app.ui.theme.lgDp
import com.zhangran.app.ui.theme.textSecondaryColor

/**
 * 占位页面：居中显示页面名称。
 *
 * @param title 页面名称（来自 `Destination.title`，与 `docs/06` §4 的页面名对应）
 */
@Composable
fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(ZhangRanTheme.spacing.lgDp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            textAlign = TextAlign.Center,
            color = ZhangRanTheme.colors.textSecondaryColor,
        )
    }
}
