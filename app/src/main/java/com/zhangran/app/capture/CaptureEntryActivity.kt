/**
 * 上游接收入口（无界面 Activity）。
 *
 * 依据 `docs/07` §3.5、`docs/04` §9.1（本文件是"单 Activity + Compose Navigation"之外唯一例外）：
 *
 * ```text
 * Intent 到达
 *   → 读取 data + extras（不做解析）
 *   → 交给 CaptureSubmitter（应用级作用域）
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
 * **提交必须走应用级作用域**（`T2b` §4.5.1）：`finish()` 会触发 `lifecycleScope` 取消，
 * 若用 Activity 作用域提交，Room 落库的挂起点会被取消，而 `runCatching` 捕不到
 * `CancellationException` → 事件静默丢失。因此这里只调用非挂起的
 * [CaptureSubmitter.submit]，由应用级作用域负责跑完。
 *
 * 本类只有 3 个动作：取 `Intent` 原始字符串 → 转交提交器 → 立即 `finish()`。
 */
package com.zhangran.app.capture

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CaptureEntryActivity : ComponentActivity() {

    @Inject
    lateinit var eventSource: CaptureEventSource

    @Inject
    lateinit var submitter: CaptureSubmitter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 故障隔离：任何异常都不得让接收入口崩溃（docs/07 §18）
        runCatching {
            val event = eventSource.newEvent(
                rawUri = intent?.dataString,
                sourcePackage = intent?.`package`,
            )
            // 交给应用级作用域提交：finish() 后仍会完成（T2b §4.5.1）
            submitter.submit(event)
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
