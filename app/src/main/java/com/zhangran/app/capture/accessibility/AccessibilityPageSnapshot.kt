/**
 * 无障碍采集的页面快照（`M6` 阶段 1）。
 *
 * 只承载**内存中的只读快照**：不持久化原始节点树、不保存截图。
 * 阶段 1 仅用于验证节点可读性与诊断；不参与解析、不入账。
 */
package com.zhangran.app.capture.accessibility

/** 一个节点的最小文本投影（已丢弃原始节点对象）。 */
data class SnapshotNode(
    val text: String?,
    val contentDescription: String?,
    val viewId: String?,
    val className: String?,
    val depth: Int,
)

/**
 * @property packageName 来源包名
 * @property windowId 窗口 id
 * @property capturedAt 采样时刻（UTC epoch millis）
 * @property nodes 节点文本快照（仅内存）
 * @property truncated 是否触达深度/数量/文本上限
 * @property textChars 收集到的文本字符数
 */
data class AccessibilityPageSnapshot(
    val packageName: String,
    val windowId: Int,
    val capturedAt: Long,
    val nodes: List<SnapshotNode>,
    val truncated: Boolean,
    val textChars: Int,
)

/** 节点读取结论（失败原因必须可区分，`M6` 阶段 1 验收）。 */
sealed interface NodeReadResult {
    data class Success(val snapshot: AccessibilityPageSnapshot) : NodeReadResult

    /** 当前窗口不可读（root 为空/服务无窗口）。 */
    data object WindowUnavailable : NodeReadResult

    /** 树可读但没有任何文本/内容描述。 */
    data object Empty : NodeReadResult
}
