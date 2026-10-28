/**
 * 恢复报告（`M4b-2` §4.8；`docs/02` §465）。
 *
 * 只描述"发生了什么"，不含备份内容；UI 直接展示四类数量 + 冲突明细 + 不变量修复。
 */
package com.zhangran.app.data.backup

/** 恢复模式（`docs/02` §457–459）。 */
enum class RestoreMode {
    /** 合并：同 id 按 updatedAt 取新，受保护字段保留本地。 */
    MERGE,

    /** 替换：清空后导入，设置按备份覆盖。 */
    REPLACE,

    /** 只恢复设置项，不触碰账务数据。 */
    SETTINGS_ONLY,
}

/** 表名常量（报告键；与 `data/local` 的 tableName 一致）。 */
object RestoreTables {
    const val TRANSACTIONS = "transactions"
    const val CATEGORIES = "categories"
    const val ACCOUNTS = "accounts"
    const val BUDGETS = "budgets"
    const val RULES = "parsing_rules"
    const val EVIDENCE = "capture_evidence"
    const val DUPLICATES = "duplicate_decisions"
    const val CAPTURE_LOGS = "capture_logs"
    const val FIELD_EDITS = "field_edits"
    const val SETTINGS = "settings"

    /** 面向用户的表名（报告展示用）。 */
    fun displayName(table: String): String = when (table) {
        TRANSACTIONS -> "交易"
        CATEGORIES -> "分类"
        ACCOUNTS -> "账户"
        BUDGETS -> "预算"
        RULES -> "解析规则"
        EVIDENCE -> "捕获证据"
        DUPLICATES -> "重复判定"
        CAPTURE_LOGS -> "捕获日志"
        FIELD_EDITS -> "字段保护"
        SETTINGS -> "设置"
        else -> table
    }
}

/** 一条冲突（保留本地记录）。 */
data class ConflictEntry(
    val table: String,
    val id: String,
    val reason: String,
)

/**
 * @property inserted 表名 → 新增条数
 * @property updated 表名 → 更新条数
 * @property skipped 表名 → 跳过条数
 * @property conflicts 冲突明细（`updatedAt` 相同等情况）
 * @property invariantFixes I1–I6 的修复记录（人类可读）
 * @property durationMs 整个恢复耗时
 * @property preRestoreBackupFileName Replace 前自动备份的文件名（非 Replace 为 null）
 * @property settingsUpdated 是否写了设置
 */
data class RestoreReport(
    val mode: RestoreMode,
    val inserted: Map<String, Int>,
    val updated: Map<String, Int>,
    val skipped: Map<String, Int>,
    val conflicts: List<ConflictEntry>,
    val invariantFixes: List<String>,
    val durationMs: Long,
    val preRestoreBackupFileName: String? = null,
    val settingsUpdated: Boolean = false,
) {
    val totalInserted: Int get() = inserted.values.sum()
    val totalUpdated: Int get() = updated.values.sum()
    val totalSkipped: Int get() = skipped.values.sum()

    /** 报告里出现过的表（按插入/更新/跳过顺序）。 */
    val touchedTables: List<String>
        get() = (inserted.keys + updated.keys + skipped.keys).distinct()
}

/** 恢复用的临时汇总器（仅内部可变）。 */
internal class RestoreTally {
    val inserted = linkedMapOf<String, Int>()
    val updated = linkedMapOf<String, Int>()
    val skipped = linkedMapOf<String, Int>()
    val conflicts = mutableListOf<ConflictEntry>()

    fun insert(table: String, count: Int = 1) {
        inserted[table] = (inserted[table] ?: 0) + count
    }

    fun update(table: String, count: Int = 1) {
        updated[table] = (updated[table] ?: 0) + count
    }

    fun skip(table: String, count: Int = 1) {
        skipped[table] = (skipped[table] ?: 0) + count
    }

    fun conflict(table: String, id: String, reason: String) {
        conflicts += ConflictEntry(table = table, id = id, reason = reason)
    }
}
