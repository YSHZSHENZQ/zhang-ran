/**
 * 无障碍绑定后的连通诊断（M6-2 / AC-1）。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityConnectivityRecorderTest {

    @Test
    fun 绑定诊断写入来源时间戳且不产生交易() = runBlocking {
        val logs = ConnectivityLogs()
        val capturedAt = 1_700_000_000_123L
        val recorder = AccessibilityConnectivityRecorder(
            records = logs,
            scope = CoroutineScope(SupervisorJob()),
            clock = { capturedAt },
            newId = { "diag-1" },
        )
        recorder.write(capturedAt)

        val log = logs.logs.single()
        assertEquals(CaptureLogStatus.RECEIVED, log.status)
        assertEquals(CaptureSource.ACCESSIBILITY, log.captureSource)
        assertEquals(capturedAt, log.capturedAt)
        assertEquals(AccessibilityConnectivityRecorder.SERVICE_CONNECTED, log.reason)
        assertNull(log.transactionId)
        assertTrue(log.rawCaptureRef.orEmpty().contains("\"capturedAt\":$capturedAt"))
    }
}

private class ConnectivityLogs : CaptureLogRepository {
    val logs = mutableListOf<CaptureLogEntity>()

    override suspend fun insert(log: CaptureLogEntity, evidence: CaptureEvidenceEntity?) {
        logs += log
    }

    override suspend fun findByCaptureId(captureId: String): CaptureLogEntity? = null

    override suspend fun findByTransactionId(transactionId: String): CaptureLogEntity? = null

    override suspend fun listAll(): List<CaptureLogEntity> = logs

    override suspend fun listEvidence(captureId: String): List<CaptureEvidenceEntity> = emptyList()
}
