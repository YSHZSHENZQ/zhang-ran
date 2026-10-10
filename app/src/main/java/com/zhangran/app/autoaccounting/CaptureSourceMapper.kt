/**
 * 上游来源 → `CaptureSource` 映射（Adapter 层）。
 *
 * 依据：
 * - `docs/07` §5 —— `CaptureSource` 的 SSOT：7 个枚举值 + 「上游来源 → 枚举」映射表 +
 *   「同一笔账命中多个来源时取可信度最高者，其余写入证据」
 * - `docs/10` §5 / §8 第 3 项 —— 上游 `app` / `channel` 的**实际取值集合尚未确认**
 *
 * 纪律：
 * - 枚举只有 7 个值，**不得新增**（`docs/07` §5）。
 * - 映射写成**显式数据表**，便于按真实载荷扩展与测试。
 * - 映射不到时**不猜默认值**：返回 `Unmapped` 并保留原始字符串，由上层写提案补映射表
 *   （`docs/07` §5、任务书 T3 §7 R1）。
 * - 本文件不访问 DAO / Room / UI（`docs/03` §15、`docs/04` §20）。
 */
package com.zhangran.app.autoaccounting

/**
 * 采集来源（SSOT：`docs/07` §5）。**枚举值与顺序不得新增或改动。**
 *
 * `priority` 为多来源命中时的可信度排序（**数值越小越可信**）：
 * 用户手工 > 系统通知 > 上游 Hook 的应用内账单数据 > 短信 > 无障碍页面文本 > AI > OCR 截图识别。
 */
enum class CaptureSource(val priority: Int) {
    /** 用户手工记账。 */
    MANUAL(0),

    /** 通知（含上游 Hook 系统通知、通知渠道）。 */
    NOTIFICATION(1),

    /** 上游 Hook 拿到的应用内账单数据。 */
    HOOK(2),

    /** 短信广播解析。 */
    SMS(3),

    /** 无障碍读取页面可见文本。 */
    ACCESSIBILITY(4),

    /** AI 辅助识别（在上游产生）。 */
    AI(5),

    /** Shizuku / 截图 + OCR 识别。 */
    OCR(6),
    ;

    companion object {
        /** 按可信度排序（可信在前）。 */
        val byPriority: List<CaptureSource> = entries.sortedBy { it.priority }
    }
}

/**
 * 单个来源的匹配结果。
 *
 * @property raw 上游给出的原始值（`app` 或 `channel`，已 trim）
 * @property source 命中的枚举；`null` 表示未映射
 */
data class SourceMatch(
    val raw: String,
    val source: CaptureSource?,
) {
    /** 是否成功映射。 */
    val mapped: Boolean get() = source != null
}

/**
 * 来源映射结果。
 *
 * @property source 选定的来源（可信度最高的已命中来源）；全部未命中时为 `null`
 * @property matched 全部命中的来源（按可信度排序，可信在前）
 * @property discarded 被丢弃的已命中来源（`docs/07` §5 要求写入证据）
 * @property unmapped 未映射的原始取值（供补映射表用；**不得**静默回退成某个枚举）
 */
data class CaptureSourceResult(
    val source: CaptureSource?,
    val matched: List<SourceMatch>,
    val discarded: List<SourceMatch>,
    val unmapped: List<String>,
) {
    /** 是否至少命中一个已知来源。 */
    val resolved: Boolean get() = source != null
}

/**
 * `app` / `channel` → [CaptureSource] 映射器。
 *
 * 别名表说明：K 为**归一化后的小写值**（去掉空白与 `-` / `_`），值域覆盖 `docs/07` §5
 * 映射表中的语义类别。表内只收「语义明确无歧义」的缩写；上游真实取值仍需按
 * `docs/10` §8 第 3 项用真实载荷确认后增补（新增别名不改枚举）。
 */
object CaptureSourceMapper {

    /**
     * 别名表：归一化取值 → 枚举。
     *
     * 归一化规则见 [normalizeKey]：小写、去空白、去 `-` / `_`。
     */
    private val ALIASES: Map<String, CaptureSource> = buildMap {
        // —— MANUAL：用户手工记账
        put("manual", CaptureSource.MANUAL)
        put("user", CaptureSource.MANUAL)
        put("手動", CaptureSource.MANUAL)

        // —— NOTIFICATION：通知（含上游 Hook 系统通知）
        put("notification", CaptureSource.NOTIFICATION)
        put("notify", CaptureSource.NOTIFICATION)
        put("通知", CaptureSource.NOTIFICATION)
        put("notificationlistener", CaptureSource.NOTIFICATION)

        // —— HOOK：上游 Hook 应用内账单数据
        put("hook", CaptureSource.HOOK)
        put("xposed", CaptureSource.HOOK)
        put("lsposed", CaptureSource.HOOK)
        put("app", CaptureSource.HOOK)
        put("application", CaptureSource.HOOK)
        put("db", CaptureSource.HOOK)
        put("database", CaptureSource.HOOK)

        // —— SMS：短信广播
        put("sms", CaptureSource.SMS)
        put("message", CaptureSource.SMS)
        put("短信", CaptureSource.SMS)

        // —— ACCESSIBILITY：无障碍页面文本
        put("accessibility", CaptureSource.ACCESSIBILITY)
        put("a11y", CaptureSource.ACCESSIBILITY)
        put("无障碍", CaptureSource.ACCESSIBILITY)

        // —— AI：上游 AI 识别
        put("ai", CaptureSource.AI)
        put("vlm", CaptureSource.AI)
        put("llm", CaptureSource.AI)

        // —— OCR：截图 / OCR 识别
        put("ocr", CaptureSource.OCR)
        put("screenshot", CaptureSource.OCR)
        put("screen", CaptureSource.OCR)
        put("shizuku", CaptureSource.OCR)
    }

    /**
     * 解析上游 `app` / `channel`，选出可信度最高的来源。
     *
     * @param appKeys 上游 `app` 字段可能拆分出的取值（可空）
     * @param channelKeys 上游 `channel` 字段的取值（可空）
     * @param extraKeys 其它可选来源线索（例如上游标记“由 AI 生成”）
     */
    fun resolve(
        appKeys: List<String> = emptyList(),
        channelKeys: List<String> = emptyList(),
        extraKeys: List<String> = emptyList(),
    ): CaptureSourceResult {
        val all = (appKeys + channelKeys + extraKeys)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val matches = all.map { raw ->
            SourceMatch(raw = raw, source = ALIASES[normalizeKey(raw)])
        }

        val mapped = matches.filter { it.mapped }.distinctBy { it.raw.lowercase() }
        val unmapped = matches.filterNot { it.mapped }.map { it.raw }.distinct()

        // 按可信度排序（数值小者在前）；同可信度时保持输入顺序
        val ranked = mapped.sortedBy { it.source!!.priority }

        return CaptureSourceResult(
            source = ranked.firstOrNull()?.source,
            matched = ranked,
            discarded = ranked.drop(1),
            unmapped = unmapped,
        )
    }

    /** 便捷入口：单个取值。 */
    fun resolveSingle(value: String?): CaptureSourceResult =
        resolve(appKeys = listOfNotNull(value))

    /**
     * 归一化映射键：小写、去首尾与中间空白、去 `-` / `_`。
     *
     * 目的：容忍上游写法差异（`Notification` / `notification_listener` / `NOTIFICATION`）。
     */
    private fun normalizeKey(raw: String): String =
        raw.lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")

    /** 供测试与文档使用：别名表大小与全部键（不暴露可变集合）。 */
    val aliasKeys: Set<String> get() = ALIASES.keys
}
