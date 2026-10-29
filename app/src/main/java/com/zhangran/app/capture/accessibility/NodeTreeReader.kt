/**
 * 无障碍节点树读取（`M6` 阶段 1）。
 *
 * 约束：
 * - 限深、限量、限文本；绝不把整棵树或原始文本写入磁盘/日志；
 * - 跳过密码节点；不可见节点不采集；
 * - 不调用 `recycle()`（Android 14+ 已废弃，由系统管理生命周期）。
 *
 * 本类只负责“取文本”，页面分类/字段解析属阶段 2。
 */
package com.zhangran.app.capture.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * @param maxDepth 最大遍历深度
 * @param maxNodes 最大节点数
 * @param maxTextChars 最大收集字符数
 */
class NodeTreeReader(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxNodes: Int = DEFAULT_MAX_NODES,
    private val maxTextChars: Int = DEFAULT_MAX_TEXT_CHARS,
) {

    fun read(
        root: AccessibilityNodeInfo?,
        packageName: String,
        windowId: Int,
        capturedAt: Long,
    ): NodeReadResult {
        if (root == null) return NodeReadResult.WindowUnavailable

        val nodes = ArrayList<SnapshotNode>(minOf(maxNodes, 64))
        var chars = 0
        var truncated = false
        var visited = 0

        // 显式栈避免深层递归；每条记录带深度。
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)

        while (stack.isNotEmpty()) {
            if (visited >= maxNodes || chars >= maxTextChars) {
                truncated = true
                break
            }
            val (node, depth) = stack.removeLast()
            visited++

            if (node.isPassword) continue
            if (!node.isVisibleToUser) continue

            val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val description = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val viewId = node.viewIdResourceName
            val className = node.className?.toString()

            if (text != null || description != null) {
                val remaining = maxTextChars - chars
                val keptText = text?.take(remaining)?.takeIf { it.isNotEmpty() }
                val keptDesc = description?.take((remaining - (keptText?.length ?: 0)).coerceAtLeast(0))
                    ?.takeIf { it.isNotEmpty() }
                chars += (keptText?.length ?: 0) + (keptDesc?.length ?: 0)
                nodes += SnapshotNode(
                    text = keptText,
                    contentDescription = keptDesc,
                    viewId = viewId,
                    className = className,
                    depth = depth,
                )
                if (keptText?.length != text?.length || keptDesc?.length != description?.length) {
                    truncated = true
                }
            }

            if (depth >= maxDepth) {
                if (node.childCount > 0) truncated = true
                continue
            }
            for (index in 0 until node.childCount) {
                node.getChild(index)?.let { child ->
                    stack.addLast(child to depth + 1)
                }
            }
        }

        if (nodes.isEmpty()) return NodeReadResult.Empty
        return NodeReadResult.Success(
            AccessibilityPageSnapshot(
                packageName = packageName,
                windowId = windowId,
                capturedAt = capturedAt,
                nodes = nodes,
                truncated = truncated,
                textChars = chars,
            ),
        )
    }

    private companion object {
        const val DEFAULT_MAX_DEPTH = 30
        const val DEFAULT_MAX_NODES = 400
        const val DEFAULT_MAX_TEXT_CHARS = 20_000
    }
}
