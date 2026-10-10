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
import com.zhangran.app.ui.theme.ZhangRanTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 骨架期使用系统明暗设置；后续若提供“暗色主题”设置项，从此处接入
            ZhangRanTheme {
                ZhangRanApp()
            }
        }
    }
}
