/**
 * 无障碍快照的保真文本（M6-2）。
 *
 * 只保存解析所需的文本投影。每个文本字段先走
 * [com.zhangran.app.capture.CaptureProcessor.sanitize]，再编码成 JSON。
 * 不保存截图，不保存节点对象。时间戳保持原值，否则无法回放。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.capture.CaptureProcessor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class AccessibilityFidelityNode(
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val depth: Int = 0,
)

@Serializable
data class AccessibilityFidelityDocument(
    val fixtureId: String? = null,
    val packageName: String,
    val windowId: Int,
    val capturedAt: Long,
    val truncated: Boolean = false,
    val nodes: List<AccessibilityFidelityNode> = emptyList(),
    /** 通知 id。旧保真文本没有这个字段时为 null。 */
    val notificationId: String? = null,
    /** 通知投递时间。旧保真文本没有这个字段时为 null。 */
    val postTime: Long? = null,
)

object AccessibilitySnapshotFidelity {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * 先按字段脱敏，再编码。
     *
     * 不对整段 JSON 跑脱敏：时间戳是 13 位数字，整段替换会把 `capturedAt` 当成单号打成非法 JSON，
     * 之后无法把这一页冻成 fixture。节点文本里的单号、手机号、姓名仍然会脱敏。
     */
    fun encode(
        snapshot: AccessibilityPageSnapshot,
        fixtureId: String?,
        notificationId: String? = null,
        postTime: Long? = null,
    ): String {
        val document = AccessibilityFidelityDocument(
            fixtureId = CaptureProcessor.sanitize(fixtureId),
            packageName = CaptureProcessor.sanitize(snapshot.packageName) ?: snapshot.packageName,
            windowId = snapshot.windowId,
            capturedAt = snapshot.capturedAt,
            truncated = snapshot.truncated,
            notificationId = CaptureProcessor.sanitize(notificationId),
            postTime = postTime,
            nodes = snapshot.nodes.map { node ->
                AccessibilityFidelityNode(
                    text = CaptureProcessor.sanitize(node.text),
                    contentDescription = CaptureProcessor.sanitize(node.contentDescription),
                    viewId = CaptureProcessor.sanitize(node.viewId),
                    depth = node.depth,
                )
            },
        )
        return json.encodeToString(document)
    }

    /** 从已落库的保真文本还原快照。解析失败返回 null。 */
    fun decode(raw: String?): AccessibilityFidelityDocument? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString<AccessibilityFidelityDocument>(raw) }.getOrNull()
    }

    fun toSnapshot(document: AccessibilityFidelityDocument): AccessibilityPageSnapshot =
        AccessibilityPageSnapshot(
            packageName = document.packageName,
            windowId = document.windowId,
            capturedAt = document.capturedAt,
            nodes = document.nodes.map { node ->
                SnapshotNode(
                    text = node.text,
                    contentDescription = node.contentDescription,
                    viewId = node.viewId,
                    className = null,
                    depth = node.depth,
                )
            },
            truncated = document.truncated,
            textChars = document.nodes.sumOf { (it.text?.length ?: 0) + (it.contentDescription?.length ?: 0) },
        )
}
