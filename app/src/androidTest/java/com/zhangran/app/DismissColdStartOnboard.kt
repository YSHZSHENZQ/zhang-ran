package com.zhangran.app

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick

/**
 * 冷启动会先进入权限引导。旧用例断言的是首页，这里只点一次「完成」，不改那些断言。
 */
fun dismissColdStartOnboard(rule: ComposeContentTestRule) {
    rule.waitUntil(timeoutMillis = 20_000) {
        rule.onAllNodesWithText("权限引导").fetchSemanticsNodes().isNotEmpty() ||
            rule.onAllNodesWithText("账然").fetchSemanticsNodes().isNotEmpty()
    }
    if (rule.onAllNodesWithText("权限引导").fetchSemanticsNodes().isNotEmpty()) {
        rule.onNodeWithText("完成").performClick()
        rule.waitUntil(timeoutMillis = 20_000) {
            rule.onAllNodesWithText("账然").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
