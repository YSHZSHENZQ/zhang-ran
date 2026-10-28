/**
 * 备份 ZIP 测试（`M4b-1` §5；覆盖 A1–A5、A8、A9、A10 中可离线验证的部分）。
 *
 * 直接解 ZIP 校验：条目数量与顺序、manifest 字段、逐文件 SHA-256/size、recordCount、
 * 空表仍写空数组、金额为整数、枚举为名称、null 保留。
 */
package com.zhangran.app.data.backup

import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.Currency
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupWriterTest {

    private val createdAt = 1_700_000_000_000L
    private val appVersion = AppVersion(versionName = "0.1.0", versionCode = 1L)

    private fun emptyFiles(): List<BackupFileContent> = BACKUP_DATA_FILE_NAMES.map { name ->
        BackupFileContent(name = name, bytes = "[]".toByteArray(Charsets.UTF_8), recordCount = 0)
    }

    private fun sampleTransaction(id: String = "tx-1"): TransactionDto = TransactionDto(
        id = id,
        type = TransactionType.EXPENSE,
        amountMinor = 1234L,
        currency = Currency.CNY,
        merchant = null,
        categoryId = null,
        accountId = null,
        transactionTime = createdAt,
        transactionTimeZone = "Asia/Shanghai",
        note = null,
        state = TransactionState.CONFIRMED,
        source = CaptureSource.MANUAL,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    private fun readZip(bytes: ByteArray): List<Pair<String, ByteArray>> =
        ZipInputStream(ByteArrayInputStream(bytes), Charsets.UTF_8).use { zip ->
            buildList {
                while (true) {
                    val entry = zip.nextEntry ?: break
                    add(entry.name to zip.readBytes())
                    zip.closeEntry()
                }
            }
        }

    @Test
    fun A1_ZIP含12个条目且顺序固定() {
        val zip = BackupWriter.buildZip(emptyFiles(), appVersion, createdAt, "Asia/Shanghai")
        val entries = readZip(zip.bytes)

        assertEquals(12, entries.size)
        assertEquals(BACKUP_MANIFEST_FILE_NAME, entries.first().first)
        assertEquals(BACKUP_DATA_FILE_NAMES, entries.drop(1).map { it.first })
    }

    @Test
    fun A2_空表也生成空数组文件() {
        val zip = BackupWriter.buildZip(emptyFiles(), appVersion, createdAt, "Asia/Shanghai")
        val entries = readZip(zip.bytes)

        assertEquals(12, zip.entryCount)
        entries.drop(1).forEach { (name, bytes) ->
            assertEquals("$name 必须是空数组", "[]", bytes.toString(Charsets.UTF_8))
        }
        assertEquals(11, zip.manifest.files.size)
    }

    @Test
    fun A3_manifest字段完整() {
        val files = emptyFiles().mapIndexed { index, file ->
            if (index == 0) {
                BackupFileContent(
                    name = file.name,
                    bytes = BackupWriter.encode(listOf(sampleTransaction())),
                    recordCount = 1,
                )
            } else {
                file
            }
        }
        val zip = BackupWriter.buildZip(files, appVersion, createdAt, "Asia/Shanghai")
        val manifestBytes = readZip(zip.bytes).first { it.first == BACKUP_MANIFEST_FILE_NAME }.second
        val manifest = Json.decodeFromString<BackupManifest>(manifestBytes.toString(Charsets.UTF_8))

        assertEquals(1, manifest.backupVersion)
        assertEquals("0.1.0", manifest.appVersionName)
        assertEquals(1L, manifest.appVersionCode)
        assertEquals(createdAt, manifest.createdAt)
        assertEquals("Asia/Shanghai", manifest.createdAtZone)
        assertEquals(11, manifest.files.size)
        assertEquals(BACKUP_DATA_FILE_NAMES, manifest.files.map { it.name })
    }

    @Test
    fun A4_逐文件sha256与byteSize可重算一致() {
        val files = emptyFiles().mapIndexed { index, file ->
            BackupFileContent(
                name = file.name,
                bytes = BackupWriter.encode(List(index) { sampleTransaction("tx-$index") }),
                recordCount = index,
            )
        }
        val zip = BackupWriter.buildZip(files, appVersion, createdAt, "Asia/Shanghai")
        val entries = readZip(zip.bytes).toMap()

        zip.manifest.files.forEach { entry ->
            val bytes = entries.getValue(entry.name)
            assertEquals(entry.byteSize, bytes.size.toLong())
            assertEquals(BackupWriter.sha256(bytes), entry.sha256)
            assertEquals(64, entry.sha256.length)
            assertTrue("sha256 必须是小写十六进制", entry.sha256.all { it in "0123456789abcdef" })
        }
        assertEquals(zip.manifest.files.sumOf { it.recordCount }, files.sumOf { it.recordCount })
    }

    @Test
    fun A5_recordCount与内容条数一致() {
        val files = emptyFiles().map { file ->
            if (file.name == "transactions.json") {
                BackupFileContent(
                    name = file.name,
                    bytes = BackupWriter.encode(List(3) { sampleTransaction("tx-$it") }),
                    recordCount = 3,
                )
            } else {
                file
            }
        }
        val zip = BackupWriter.buildZip(files, appVersion, createdAt, "Asia/Shanghai")

        assertEquals(3, zip.manifest.files.first { it.name == "transactions.json" }.recordCount)
        assertEquals(0, zip.manifest.files.first { it.name == "categories.json" }.recordCount)
    }

    @Test
    fun A8金额为整数且A9枚举为名称() {
        val zip = BackupWriter.buildZip(
            files = emptyFiles().map { file ->
                if (file.name == "transactions.json") {
                    BackupFileContent(
                        name = file.name,
                        bytes = BackupWriter.encode(listOf(sampleTransaction())),
                        recordCount = 1,
                    )
                } else {
                    file
                }
            },
            appVersion = appVersion,
            createdAt = createdAt,
            createdAtZone = "Asia/Shanghai",
        )
        val json = readZip(zip.bytes)
            .first { it.first == "transactions.json" }
            .second
            .toString(Charsets.UTF_8)

        assertTrue(json.contains("\"amountMinor\": 1234"))
        assertTrue(json.contains("\"type\": \"EXPENSE\""))
        assertTrue(json.contains("\"state\": \"CONFIRMED\""))
        assertTrue(json.contains("\"source\": \"MANUAL\""))
        assertTrue("金额不得出现小数点", !json.contains("1234.0"))
    }

    @Test
    fun A10_可空字段保留null() {
        val zip = BackupWriter.buildZip(
            files = emptyFiles().map { file ->
                if (file.name == "transactions.json") {
                    BackupFileContent(
                        name = file.name,
                        bytes = BackupWriter.encode(listOf(sampleTransaction())),
                        recordCount = 1,
                    )
                } else {
                    file
                }
            },
            appVersion = appVersion,
            createdAt = createdAt,
            createdAtZone = "Asia/Shanghai",
        )
        val json = readZip(zip.bytes)
            .first { it.first == "transactions.json" }
            .second
            .toString(Charsets.UTF_8)

        assertTrue(json.contains("\"merchant\": null"))
        assertTrue(json.contains("\"categoryId\": null"))
        assertTrue(json.contains("\"deletedAt\": null"))
    }

    @Test
    fun 文件名按yyyyMMdd与格式版本() {
        val name = BackupWriter.fileName(createdAt, "UTC")
        assertTrue(name.startsWith("zhangran-backup-v1-"))
        assertTrue(name.endsWith(".zip"))
        assertEquals("zhangran-backup-v1-".length + 8 + ".zip".length, name.length)
        assertTrue(name.drop(19).take(8).all { it.isDigit() })
    }

    @Test
    fun settings序列化为名称字符串且可回读() {
        val settings = com.zhangran.app.data.settings.AppSettings(
            themeMode = com.zhangran.app.data.settings.ThemeMode.DARK,
            overallConfidenceThreshold = 0.9,
            fieldConfidenceThreshold = 0.4,
        )
        val json = BackupWriter.encode(listOf(settings.toDto())).toString(Charsets.UTF_8)

        assertTrue(json.contains("\"themeMode\": \"DARK\""))
        assertTrue(json.contains("\"overallConfidenceThreshold\": 0.9"))
        val decoded = Json.decodeFromString<List<SettingsDto>>(json).single()
        assertEquals(com.zhangran.app.data.settings.ThemeMode.DARK, decoded.toAppSettings().themeMode)
    }

    @Test
    fun 敏感文本掩码() {
        assertEquals("138****5678", scrubSensitiveText("13812345678"))
        val card = scrubSensitiveText("6222021234567890123")!!
        assertTrue("银行卡号中段必须被掩码", card.contains("*"))
        assertTrue("银行卡号后 4 位保留", card.endsWith("0123"))
        assertTrue("身份证号必须被掩码", scrubSensitiveText("110101199001011234")!!.contains("*"))
        assertTrue(
            "带空格的卡号也必须被掩码",
            scrubSensitiveText("尾号 6222 0212 3456 7890")!!.contains("********"),
        )
        assertNull(scrubSensitiveText(null))
        assertEquals("没有数字", scrubSensitiveText("没有数字"))
    }

    @Test
    fun 缺文件或重复文件被拒绝() {
        val missing = emptyFiles().dropLast(1)
        val duplicate = emptyFiles() + emptyFiles().first()

        runCatching { BackupWriter.buildZip(missing, appVersion, createdAt, "UTC") }
            .onSuccess { error("缺少文件时必须失败") }
        runCatching { BackupWriter.buildZip(duplicate, appVersion, createdAt, "UTC") }
            .onSuccess { error("重复文件时必须失败") }
    }
}
