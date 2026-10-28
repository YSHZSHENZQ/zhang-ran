/**
 * 备份 manifest（`M4b-1` §4.2；格式 SSOT：`docs/02` §17）。
 *
 * `manifest.json` 是 ZIP 的第一个条目，含逐文件原始字节的 SHA-256，供恢复侧校验。
 * 本期 `backupVersion` 固定为 1；M4b-2 按此判断兼容性。
 */
package com.zhangran.app.data.backup

import kotlinx.serialization.Serializable

/** 备份格式版本（本期固定 1）。 */
const val BACKUP_VERSION: Int = 1

/** manifest 文件名（ZIP 第一个条目）。 */
const val BACKUP_MANIFEST_FILE_NAME: String = "manifest.json"

/**
 * 11 个数据文件（顺序即 ZIP 写入顺序；空表也必须写入空数组文件，`02` §17）。
 */
val BACKUP_DATA_FILE_NAMES: List<String> = listOf(
    "transactions.json",
    "categories.json",
    "accounts.json",
    "budgets.json",
    "rules.json",
    "pending.json",
    "evidence.json",
    "duplicate_decisions.json",
    "capture_logs.json",
    "field_edits.json",
    "settings.json",
)

/** 应用版本（从 `BuildConfig` 取，不在 domain 里硬编码）。 */
data class AppVersion(
    val versionName: String,
    val versionCode: Long,
)

/**
 * 一个待写入 ZIP 的数据文件。
 *
 * @property bytes 已序列化的 UTF-8 JSON 字节（**摘要按这些字节计算**，不是按对象）
 * @property recordCount 条目数（写入 manifest 便于人工核对）
 */
data class BackupFileContent(
    val name: String,
    val bytes: ByteArray,
    val recordCount: Int,
) {
    // ByteArray 的 equals/hashCode 需显式实现，避免测试比较踩坑
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupFileContent) return false
        return name == other.name && recordCount == other.recordCount && bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int = 31 * (31 * name.hashCode() + recordCount) + bytes.contentHashCode()
}

@Serializable
data class BackupFileEntry(
    val name: String,
    val byteSize: Long,
    val sha256: String,
    val recordCount: Int,
)

@Serializable
data class BackupManifest(
    val backupVersion: Int,
    val appVersionName: String,
    val appVersionCode: Long,
    val createdAt: Long,
    val createdAtZone: String,
    val files: List<BackupFileEntry>,
)

/** 生成好的 ZIP（内存中；数据量为个人记账规模）。 */
data class BackupZip(
    val fileName: String,
    val bytes: ByteArray,
    val manifest: BackupManifest,
) {
    val entryCount: Int get() = manifest.files.size + 1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BackupZip) return false
        return fileName == other.fileName && bytes.contentEquals(other.bytes) && manifest == other.manifest
    }

    override fun hashCode(): Int =
        31 * (31 * fileName.hashCode() + bytes.contentHashCode()) + manifest.hashCode()
}
