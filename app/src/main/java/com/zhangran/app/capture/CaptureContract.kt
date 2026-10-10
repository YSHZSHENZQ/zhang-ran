/**
 * 宿主接入协议契约（纯 Kotlin/JVM，无 Android 依赖）。
 *
 * 依据 `docs/07` §3.5（宿主接入协议，唯一出站通道）：
 *
 * ```text
 * Intent(ACTION_VIEW, "zhangran://capture?v=1&payload=<Base64URL(Gson JSON)>")
 * flags = FLAG_ACTIVITY_NEW_TASK
 * ```
 *
 * 设计说明：
 * - 本文件只做**参数提取**（版本号与载荷字符串），**不做** Base64 解码、JSON 反序列化、
 *   业务判断——那些属于 [CaptureProcessor]（`docs/04` §9.1：入口内禁止解析/落库/业务判断）。
 * - 解析写成对"URI 字符串 / 查询串"的纯函数，因此可在 JVM 单测里覆盖，不需要设备。
 */
package com.zhangran.app.capture

/** scheme（`docs/07` §3.5：`zhangran://capture`）。 */
const val CAPTURE_SCHEME: String = "zhangran"

/** host（`docs/07` §3.5）。 */
const val CAPTURE_HOST: String = "capture"

/** 完整 URI 前缀，便于测试与日志使用。 */
const val CAPTURE_URI_PREFIX: String = "$CAPTURE_SCHEME://$CAPTURE_HOST"

/**
 * 当前支持的载荷版本（`docs/07` §3.5：`v` 版本参数）。
 *
 * 约束：`v` 缺失、非数字、或不是本值 → **一律不按旧契约解析入账**（`04` §9.1）。
 */
const val SUPPORTED_PAYLOAD_VERSION: Int = 1

/** 版本参数缺失时的语义值（显式区分"没给"与"给了别的版本"）。 */
const val VERSION_MISSING: Int = -1

/** 版本参数存在但非法（非整数）时的语义值。 */
const val VERSION_INVALID: Int = -2

/**
 * 从 URI / 查询串中提取到的原始参数（尚未校验、尚未解码）。
 *
 * @property version `v` 参数；[VERSION_MISSING] 表示缺失，[VERSION_INVALID] 表示非法
 * @property payload `payload` 参数原文（Base64URL，未解码）；缺失为 `null`
 * @property rawQuery 原始查询串（用于日志与排查，脱敏前不得落库）
 */
data class CaptureParams(
    val version: Int,
    val payload: String?,
    val rawQuery: String,
) {
    /** 版本是否受支持（缺失/非法/其它版本一律 false）。 */
    val versionSupported: Boolean get() = version == SUPPORTED_PAYLOAD_VERSION
}

/**
 * 契约解析器（纯函数）。
 */
object CaptureContract {

    /**
     * 从完整 URI 或裸查询串解析参数。
     *
     * 可接受输入：
     * - `zhangran://capture?v=1&payload=xxx`
     * - `v=1&payload=xxx`
     * - `?v=1&payload=xxx`
     *
     * 不可接受（返回 [CaptureParams] 但 `payload = null` / 版本语义值）：
     * - 空串、无查询串、只有 scheme 的 URI
     *
     * 注意：**不做** scheme/host 校验之外的业务判断；scheme 是否匹配由 [isCaptureUri] 判定。
     *
     * @param uri 上游传入的 `Intent.data` 字符串（`null` 表示无 data）
     */
    fun parse(uri: String?): CaptureParams {
        val text = uri?.trim().orEmpty()
        val queryIndex = text.indexOf('?')
        val rawQuery = if (queryIndex < 0) {
            // 允许直接传查询串（便于测试与手工 adb 触发）
            if (text.contains('=')) text else ""
        } else {
            text.substring(queryIndex + 1)
        }

        val params = splitQuery(rawQuery)
        return CaptureParams(
            version = parseVersion(params["v"]),
            payload = params["payload"]?.takeIf { it.isNotBlank() },
            rawQuery = rawQuery,
        )
    }

    /**
     * 是否为账然的接收入口 URI（scheme + host 匹配）。
     *
     * 说明：上游用 `startActivityIfResolvable` 拉起，若我们的 `intent-filter` 配置正确，
     * 系统只会把匹配的 Intent 交给我们；此函数用于**日志与防御性判断**。
     */
    fun isCaptureUri(uri: String?): Boolean {
        val text = uri?.trim()?.lowercase().orEmpty()
        return text.startsWith("$CAPTURE_SCHEME://$CAPTURE_HOST")
    }

    /** 解析版本参数：缺失 → [VERSION_MISSING]，非法 → [VERSION_INVALID]。 */
    private fun parseVersion(raw: String?): Int {
        if (raw == null || raw.isBlank()) return VERSION_MISSING
        return raw.trim().toIntOrNull() ?: VERSION_INVALID
    }

    /**
     * 拆分查询串为键值表（简单实现，够用且可测）。
     *
     * 说明：
     * - 只按 `&` 分隔，取第一个 `=` 之前为键；
     * - **不做**百分号解码（上游传的是 Base64URL，本身不含需要解码的字符；
     *   若上游做了 URL 编码，解码责任在 Adapter 任务，本任务只保存原文）；
     * - 重复键取最后一个（`Uri.getQueryParameter` 同语义）。
     */
    private fun splitQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        rawQuery.split('&').forEach { pair ->
            if (pair.isEmpty()) return@forEach
            val eq = pair.indexOf('=')
            if (eq < 0) {
                map[pair] = ""
            } else {
                map[pair.substring(0, eq)] = pair.substring(eq + 1)
            }
        }
        return map
    }
}
