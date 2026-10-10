/**
 * 账然唯一的 Activity（单 Activity 架构，依据 docs/04 §15）。
 *
 * 本任务（T1a）只验证构建链打通：显示应用名文本。
 * 禁止：在此保存业务状态、写业务规则、硬编码颜色/尺寸（主题与页面属 T1b）。
 */
package com.zhangran.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ZhangRanRoot()
        }
    }
}

/**
 * 根 Composable：本任务只显示应用名，用于确认 Compose + Material3 + Hilt 链路可用。
 * T1b 将替换为 ZhangRanTheme + 导航骨架 + 18 个占位页面。
 */
@Composable
private fun ZhangRanRoot() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "账然")
            }
        }
    }
}
