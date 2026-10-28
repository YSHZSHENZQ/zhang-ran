/**
 * 备份解析与校验（`M4b-2` §4.1，V1–V7）。
 *
 * **先全部校验完，再交给恢复用例**（任何写操作前）：ZIP 可打开 → manifest 可解析且版本/覆盖
 * 11 个文件 → 逐文件 SHA-256 与 byteSize → 每个 JSON 可解析。任一步失败抛
 * [BackupValidationException]，调用方不得修改任何数据。
 */
package com.zhangran.app.data.backup

import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString

class BackupValidationException(message: String) : Exception(message)

/**
 * 校验后的备份内容（已解码 DTO）。
 *
 * @property manifest 原始 manifest
 * @property settings `settings.json` 的唯一条目（缺失时为默认值）
 */
data class RestorePayload(
    val manifest: BackupManifest,
    val settings: SettingsDto,
    val transactions: List<TransactionDto>,
    val pending: List<TransactionDto>,
    val categories: List<CategoryDto>,
    val accounts: List<AccountDto>,
    val budgets: List<BudgetDto>,
    val rules: List<ParsingRuleDto>,
    val evidence: List<CaptureEvidenceDto>,
    val duplicateDecisions: List<DuplicateDecisionDto>,
    val captureLogs: List<CaptureLogDto>,
    val fieldEdits: List<FieldEditDto>,
)

class BackupReader @Inject constructor() {

    /**
     * 读取并校验 ZIP。
     *
     * @throws BackupValidationException 任一校验失败（消息面向用户）
     */
    fun read(input: InputStream): RestorePayload {
        val entries = readEntries(input)
        val manifestBytes = entries[BACKUP_MANIFEST_FILE_NAME]
            ?: throw BackupValidationException("备份文件损坏：缺少 manifest.json")

        val manifest = decodeManifest(manifestBytes)
        validateVersion(manifest)
        validateFileListing(manifest, entries.keys)
        validateHashes(manifest, entries)

        val settingsList = decodeFile<SettingsDto>(entries, "settings.json")
        return RestorePayload(
            manifest = manifest,
            settings = settingsList.firstOrNull() ?: SettingsDto(
                themeMode = "SYSTEM",
                overallConfidenceThreshold = 0.8,
                fieldConfidenceThreshold = 0.5,
            ),
            transactions = decodeFile(entries, "transactions.json"),
            pending = decodeFile(entries, "pending.json"),
            categories = decodeFile(entries, "categories.json"),
            accounts = decodeFile(entries, "accounts.json"),
            budgets = decodeFile(entries, "budgets.json"),
            rules = decodeFile(entries, "rules.json"),
            evidence = decodeFile(entries, "evidence.json"),
            duplicateDecisions = decodeFile(entries, "duplicate_decisions.json"),
            captureLogs = decodeFile(entries, "capture_logs.json"),
            fieldEdits = decodeFile(entries, "field_edits.json"),
        )
    }

    /** V1：ZIP 可打开；条目不得重复/未知。 */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val allowed = (BACKUP_DATA_FILE_NAMES + BACKUP_MANIFEST_FILE_NAME).toSet()
        val result = linkedMapOf<String, ByteArray>()
        try {
            ZipInputStream(input, Charsets.UTF_8).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (name !in allowed) {
                        throw BackupValidationException("备份文件包含未知条目：$name")
                    }
                    if (result.containsKey(name)) {
                        throw BackupValidationException("备份文件条目重复：$name")
                    }
                    result[name] = zip.readBytes()
                    zip.closeEntry()
                }
            }
        } catch (exception: BackupValidationException) {
            throw exception
        } catch (exception: IOException) {
            throw BackupValidationException("备份文件损坏：无法解压")
        } catch (exception: RuntimeException) {
            throw BackupValidationException("备份文件损坏：${exception.message ?: "无法解压"}")
        }
        if (result.isEmpty()) throw BackupValidationException("备份文件损坏：ZIP 为空")
        return result
    }

    /** V2：manifest 存在且可解析。 */
    private fun decodeManifest(bytes: ByteArray): BackupManifest =
        try {
            json.decodeFromString<BackupManifest>(bytes.toString(Charsets.UTF_8))
        } catch (exception: SerializationException) {
            throw BackupValidationException("备份文件损坏：manifest.json 无法解析")
        } catch (exception: IllegalArgumentException) {
            throw BackupValidationException("备份文件损坏：manifest.json 无法解析")
        }

    /** V3：backupVersion 必须正好为 1（本期无迁移路径）。 */
    private fun validateVersion(manifest: BackupManifest) {
        if (manifest.backupVersion > BACKUP_VERSION) {
            throw BackupValidationException("备份来自更新版本的账然（v${manifest.backupVersion}），请先升级应用")
        }
        if (manifest.backupVersion < BACKUP_VERSION) {
            throw BackupValidationException("备份版本过旧（v${manifest.backupVersion}），当前版本暂不支持迁移")
        }
    }

    /** V4：manifest 覆盖全部 11 个文件，且 ZIP 中实际存在。 */
    private fun validateFileListing(manifest: BackupManifest, entryNames: Set<String>) {
        val listed = manifest.files.map { it.name }
        if (listed.size != BACKUP_DATA_FILE_NAMES.size || listed.toSet() != BACKUP_DATA_FILE_NAMES.toSet()) {
            throw BackupValidationException("备份文件不完整：manifest 未覆盖全部数据文件")
        }
        val missing = BACKUP_DATA_FILE_NAMES.filter { it !in entryNames }
        if (missing.isNotEmpty()) {
            throw BackupValidationException("备份文件损坏：缺少 ${missing.joinToString()}")
        }
    }

    /** V5：实际字节的 SHA-256 与 byteSize 必须与 manifest 一致。 */
    private fun validateHashes(manifest: BackupManifest, entries: Map<String, ByteArray>) {
        manifest.files.forEach { file ->
            val bytes = entries[file.name]
                ?: throw BackupValidationException("备份文件损坏：缺少 ${file.name}")
            if (bytes.size.toLong() != file.byteSize) {
                throw BackupValidationException("校验失败：${file.name} 大小不符，文件可能损坏")
            }
            if (BackupWriter.sha256(bytes) != file.sha256) {
                throw BackupValidationException("校验失败：${file.name} 摘要不符，文件可能损坏")
            }
        }
    }

    /** V6：每个文件 JSON 可解析（`ignoreUnknownKeys` → 向后兼容）。 */
    private inline fun <reified T> decodeFile(entries: Map<String, ByteArray>, name: String): List<T> {
        val bytes = entries[name]
            ?: throw BackupValidationException("备份文件损坏：缺少 $name")
        return try {
            json.decodeFromString<List<T>>(bytes.toString(Charsets.UTF_8))
        } catch (exception: SerializationException) {
            throw BackupValidationException("校验失败：$name 内容无法解析")
        } catch (exception: IllegalArgumentException) {
            throw BackupValidationException("校验失败：$name 内容无法解析")
        }
    }

    private companion object {
        val json = BackupWriter.json
    }
}
