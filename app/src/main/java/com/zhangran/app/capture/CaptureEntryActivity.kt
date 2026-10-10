/**
 * 上游接收入口（无界面 Activity）。
 *
 * 依据 `docs/07` §3.5、`docs/04` §9.1（本文件是"单 Activity + Compose Navigation"之外唯一例外）：
 *
 * ```text
 * Intent 到达
 *   → 读取 data + extras（不做解析）
 *   → 交给 CaptureEventSource
 *   → finish()
 * ```
 *
 * 严格禁止在本类中出现（T4 §4.1 / `04` §9.1，验收 A3 会审查）：
 * - Base64 解码 / JSON 反序列化
 * - 参数提取与校验（由 `CaptureEventSource.newEvent` 负责）
 * - 版本判断或任何业务判断
 * - 写库 / 访问 DAO / Repository
 * - 导航（本 Activity 不进入导航图，也不是 Compose 页面）
 *
 * 本类只有 3 个动作：取 `Intent` 原始字符串 → 转交事件源 → 立即 `finish()`。
 */
package com.zhangran.app.capture

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CaptureEntryActivity : ComponentActivity() {

    @Inject
    lateinit var eventSource: CaptureEventSource

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 故障隔离：任何异常都不得让接收入口崩溃（docs/07 §18）
        runCatching {
            val event = eventSource.newEvent(
                rawUri = intent?.dataString,
                sourcePackage = intent?.`package`,
            )
            // 后台提交，不等待结果（v0.1 不做回执，docs/07 §3.5）
            lifecycleScope.launch {
                runCatching { eventSource.submit(event) }
                    .onFailure { e -> Log.w(TAG, "提交采集事件失败：${e.javaClass.simpleName}") }
            }
        }.onFailure { t ->
            Log.w(TAG, "接收入口处理异常：${t.javaClass.simpleName}")
        }

        // 必须立即结束，避免 ANR 与白屏（docs/04 §9.1）
        finish()
    }

    companion object {
        private const val TAG = "ZhangRanCapture"
    }
}
