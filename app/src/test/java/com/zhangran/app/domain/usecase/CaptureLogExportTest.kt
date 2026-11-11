package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureLogExportTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun 文件名按设备时区() {
        assertEquals(
            "zhangran-logs-20261111-130522.json",
            CaptureLogExport.fileName(1_794_373_522_722L, zone),
        )
    }

    @Test
    fun 导出含版本号并脱敏账号手机卡号证件订单号和通知姓名() {
        val log = CaptureLogEntity(
            id = "log-1",
            captureId = "cap-1",
            capturedAt = 1_794_373_522_722L,
            captureSource = CaptureSource.ACCESSIBILITY,
            packageName = "com.tencent.mm",
            channel = "wechat",
            parserRuleId = null,
            engineVersion = "m6-3",
            payloadVersion = 1,
            status = CaptureLogStatus.PENDING,
            reason = "收款方张三，账号6222021234567890，手机13812348000，身份证110101199001011234，订单4200001234567890",
            rawCaptureRef = """{"fixtureId":"m6-3-f-wx","payer":"张三"}""",
            evidenceId = "evi-1",
            transactionId = "tx-1",
        )

        val json = CaptureLogExport.json(
            gitSha = "abc123",
            entries = listOf(CaptureLogExport.entry(log, zone)),
        )

        assertTrue(json.contains("\"gitSha\":\"abc123\""))
        assertTrue(json.contains("2026-11-11 13:05"))
        assertTrue(json.contains("ACCESSIBILITY"))
        assertTrue(json.contains("com.tencent.mm"))
        assertTrue(json.contains("m6-3-f-wx"))
        assertTrue(json.contains("张**"))
        assertFalse(json.contains("张三"))
        assertFalse(json.contains("6222021234567890"))
        assertFalse(json.contains("13812348000"))
        assertFalse(json.contains("110101199001011234"))
        assertFalse(json.contains("4200001234567890"))
        assertTrue(json.contains("6222***7890"))
        assertTrue(json.contains("138***8000"))
        assertTrue(json.contains("4200***7890"))
        assertTrue(CaptureLogExport.sensitiveFindings(json).isEmpty())
    }
}
