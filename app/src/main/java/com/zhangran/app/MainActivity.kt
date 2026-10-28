/**
 * 账然唯一的 Activity（单 Activity 架构，依据 docs/04 §15）。
 *
 * 职责：只负责把 Compose 内容挂上去（主题 + 导航骨架），不保存业务状态、不写业务规则。
 */
package com.zhangran.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.zhangran.app.ui.navigation.ZhangRanApp
import com.zhangran.app.ui.theme.ZhangRanAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 主题由设置驱动（M4c）：DataStore 首读后再渲染，避免先亮后暗
            ZhangRanAppTheme {
                ZhangRanApp()
            }
        }
    }
}
