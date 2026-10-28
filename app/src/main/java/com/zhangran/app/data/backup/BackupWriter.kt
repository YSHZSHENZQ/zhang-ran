/**
 * ZIP 组装与校验（`M4b-1` §4.5）。
 *
 * 实现要点：
 * 1. 先把 11 个文件序列化为字节（调用方提供），**按字节**计算 SHA-256 与 `byteSize`；
 * 2. 再拼 manifest（`files` 顺序 = `BACKUP_DATA_FILE_NAMES`）；
 * 3. 写 ZIP：manifest **第一个**，然后按固定顺序写 11 个文件（UTF-8，DEFLATED）；
 * 4. 全部在内存完成（个人记账数据量级），返回字节 + manifest，由 UI 写入 SAF。
 */
package com.zhangran.app.data.backup

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BackupWriter {

    /**
     * 备份 JSON 配置。
     *
     * - `prettyPrint = true`：便于人工排查（§4.3 规则 6）；
     * - `encodeDefaults = true`：**保证可空字段以 `null` 出现**（§4.3 规则 5）。
     *   规则 7 的 `encodeDefaults=false` 与"保留 null"冲突，恢复歧义优先级更高，故取前者；
     * - `ignoreUnknownKeys = true`：供 M4b-2 向后兼容（编码无影响）。
     */
    val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

    /** 通用序列化（UTF-8）。 */
    inline fun <reified T> encode(value: T): ByteArray =
        json.encodeToString(value).toByteArray(Charsets.UTF_8)

    /** 对**原始文件字节**计算 SHA-256（小写十六进制）。 */
    fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    /**
     * 默认文件名：`zhangran-backup-v1-<yyyymmdd>.zip`（设备时区，`02` §17）。
     */
    fun fileName(createdAt: Long, zoneId: String): String {
        val zone = runCatching { ZoneId.of(zoneId) }.getOrDefault(ZoneId.systemDefault())
        val date = Instant.ofEpochMilli(createdAt).atZone(zone).toLocalDate()
        return "zhangran-backup-v1-${date.format(FILE_DATE_FORMAT)}.zip"
    }

    /**
     * 生成 ZIP。
     *
     * @param files 11 个数据文件的字节（必须齐全；空表用 `[]` 字节占位，不得省略）
     * @throws IllegalArgumentException 文件缺失/重复/未知
     */
    fun buildZip(
        files: List<BackupFileContent>,
        appVersion: AppVersion,
        createdAt: Long,
        createdAtZone: String,
    ): BackupZip {
        val byName = files.associateBy { it.name }
        require(byName.size == files.size) { "备份文件重复：${files.map { it.name }}" }
        require(byName.keys == BACKUP_DATA_FILE_NAMES.toSet()) {
            "备份文件不完整：缺少=${BACKUP_DATA_FILE_NAMES - byName.keys}，多余=${byName.keys - BACKUP_DATA_FILE_NAMES.toSet()}"
        }
        val orderedFiles = BACKUP_DATA_FILE_NAMES.map { name -> byName.getValue(name) }

        val manifest = BackupManifest(
            backupVersion = BACKUP_VERSION,
            appVersionName = appVersion.versionName,
            appVersionCode = appVersion.versionCode,
            createdAt = createdAt,
            createdAtZone = createdAtZone,
            files = orderedFiles.map { file ->
                BackupFileEntry(
                    name = file.name,
                    byteSize = file.bytes.size.toLong(),
                    sha256 = sha256(file.bytes),
                    recordCount = file.recordCount,
                )
            },
        )

        val zipBytes = ByteArrayOutputStream().use { buffer ->
            ZipOutputStream(buffer).use { zip ->
                // manifest 必须第一个写；其摘要不进 files 列表（避免自引用）
                writeEntry(zip, BACKUP_MANIFEST_FILE_NAME, encode(manifest), createdAt)
                orderedFiles.forEach { file ->
                    writeEntry(zip, file.name, file.bytes, createdAt)
                }
            }
            buffer.toByteArray()
        }

        return BackupZip(
            fileName = fileName(createdAt, createdAtZone),
            bytes = zipBytes,
            manifest = manifest,
        )
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, bytes: ByteArray, time: Long) {
        val entry = ZipEntry(name).apply { this.time = time }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private val FILE_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd")
}
