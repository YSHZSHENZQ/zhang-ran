/**
 * 无障碍快照的保真文本（M6-2）。
 *
 * 只保存解析所需的文本投影，并在落库前走 [com.zhangran.app.capture.CaptureProcessor.sanitize]。
 * 不保存截图，不保存节点对象。
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
)

object AccessibilitySnapshotFidelity {

    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
    }

    /** 编码并脱敏。脱敏失败时退回已编码文本，不抛给采集入口。 */
    fun encode(snapshot: AccessibilityPageSnapshot, fixtureId: String?): String {
        val document = AccessibilityFidelityDocument(
            fixtureId = fixtureId,
            packageName = snapshot.packageName,
            windowId = snapshot.windowId,
            capturedAt = snapshot.capturedAt,
            truncated = snapshot.truncated,
            nodes = snapshot.nodes.map { node ->
                AccessibilityFidelityNode(
                    text = node.text,
                    contentDescription = node.contentDescription,
                    viewId = node.viewId,
                    depth = node.depth,
                )
            },
        )
        val encoded = json.encodeToString(document)
        return CaptureProcessor.sanitize(encoded) ?: encoded
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
