/**
 * M6-2 竖切：白名单支付结果只进待确认，正式账不增加。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.data.local.entity.CaptureEvidenceEntity
import com.zhangran.app.data.local.entity.CaptureLogEntity
import com.zhangran.app.data.local.entity.TransactionEntity
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.CaptureLogStatus
import com.zhangran.app.domain.model.CaptureSource
import com.zhangran.app.domain.model.DecisionResult
import com.zhangran.app.domain.model.SearchCriteria
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.domain.service.Decision
import com.zhangran.app.domain.service.Normalize
import com.zhangran.app.domain.service.resolver.AccountResolver
import com.zhangran.app.domain.service.resolver.CategoryResolver
import com.zhangran.app.domain.usecase.ObserveHomeSummaryUseCase
import com.zhangran.app.domain.usecase.PendingRecord
import com.zhangran.app.domain.usecase.parsePendingReasons
import com.zhangran.app.ui.screen.pending.PendingViewModel
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilitySnapshotIngestTest {

    @Test
    fun 白名单支付结果写入待确认且证明含状态来源与时间() = runBlocking {
        val harness = Harness()
        val result = harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(),
            AccessibilityFixtures.WECHAT_PAY_SUCCESS,
        )
        val proof = result.toProofJson()

        assertEquals(AccessibilityIngestOutcome.PENDING, result.outcome)
        assertEquals(CaptureLogStatus.PENDING, result.status)
        assertEquals(CaptureSource.ACCESSIBILITY, result.captureSource)
        assertEquals(AccessibilityFixtures.CAPTURED_AT, result.capturedAt)
        assertEquals(TransactionState.PENDING, result.transactionState)
        assertTrue(proof.contains("\"status\":\"PENDING\""))
        assertTrue(proof.contains("\"source\":\"ACCESSIBILITY\""))
        assertTrue(proof.contains("\"capturedAt\":${AccessibilityFixtures.CAPTURED_AT}"))

        val log = harness.logs.logs.single()
        assertEquals(CaptureLogStatus.PENDING, log.status)
        assertEquals(CaptureSource.ACCESSIBILITY, log.captureSource)
        assertEquals(AccessibilityFixtures.CAPTURED_AT, log.capturedAt)
        assertTrue(log.rawCaptureRef.orEmpty().contains(AccessibilityFixtures.WECHAT_PAY_SUCCESS))
        assertEquals(1, harness.logs.listEvidence(log.captureId).size)
        assertEquals(CaptureSource.ACCESSIBILITY, harness.logs.evidence.single().captureSource)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertEquals(CaptureSource.ACCESSIBILITY, harness.transactions.rows.single().source)
    }

    @Test
    fun 支付宝白名单同样只进待确认() = runBlocking {
        val harness = Harness()
        val result = harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess().copy(
                packageName = AccessibilityTargets.ALIPAY_PACKAGE,
            ),
            "m6-2-alipay-pay-success",
        )
        assertEquals(TransactionState.PENDING, result.transactionState)
        assertEquals(CaptureSource.ACCESSIBILITY, result.captureSource)
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
    }

    @Test
    fun 非白名单不落日志也不产生候选交易() = runBlocking {
        val harness = Harness()
        val result = harness.ingest.ingest(
            AccessibilityFixtures.nonWhitelist(),
            "m6-2-not-whitelist",
        )
        assertEquals(AccessibilityIngestOutcome.NOT_WHITELIST, result.outcome)
        assertTrue(harness.logs.logs.isEmpty())
        assertTrue(harness.logs.evidence.isEmpty())
        assertTrue(harness.transactions.rows.isEmpty())
    }

    @Test
    fun 固定fixture对应待确认行且页面能读出金额商户和原因() = runBlocking {
        val harness = Harness()
        harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(),
            AccessibilityFixtures.WECHAT_PAY_SUCCESS,
        )
        val transaction = harness.transactions.rows.single()
        val log = harness.logs.logs.single { it.rawCaptureRef.orEmpty().contains(AccessibilityFixtures.WECHAT_PAY_SUCCESS) }
        assertEquals(transaction.id, log.transactionId)
        assertEquals(TransactionState.PENDING, transaction.state)
        assertEquals(CaptureSource.ACCESSIBILITY, transaction.source)

        val row = PendingViewModel.toRow(
            PendingRecord(
                transactionId = transaction.id,
                amountMinor = transaction.amountMinor,
                type = transaction.type,
                merchant = transaction.merchant,
                reasons = parsePendingReasons(log.reason),
                capturedAt = log.capturedAt,
                source = transaction.source,
            ),
            zoneId = "Asia/Shanghai",
        )
        assertEquals("¥38.50", row.amountText)
        assertEquals("星巴克咖啡", row.merchantText)
        assertTrue(row.reasonText.isNotBlank())
        assertTrue(row.reasonText.contains("分类不确定"))

        val unknown = PendingViewModel.toRow(
            PendingRecord(
                transactionId = "unknown",
                amountMinor = 3_850L,
                type = transaction.type,
                merchant = null,
                reasons = parsePendingReasons(log.reason),
                capturedAt = log.capturedAt,
                source = transaction.source,
            ),
            zoneId = "Asia/Shanghai",
        )
        assertEquals(PendingViewModel.UNKNOWN_MERCHANT, unknown.merchantText)
        assertTrue(unknown.reasonText.isNotBlank())
    }

    @Test
    fun 同一fixture跑两次都不自动入账且证据都还在() = runBlocking {
        val harness = Harness()
        val snapshot = AccessibilityFixtures.wechatPaySuccess()
        val first = harness.ingest.ingest(snapshot, AccessibilityFixtures.WECHAT_PAY_SUCCESS)
        val second = harness.ingest.ingest(snapshot, AccessibilityFixtures.WECHAT_PAY_SUCCESS)

        assertEquals(TransactionState.PENDING, first.transactionState)
        assertEquals(TransactionState.PENDING, second.transactionState)
        assertNotEquals(first.captureId, second.captureId)
        assertEquals(2, harness.transactions.rows.size)
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertTrue(harness.transactions.rows.all { it.source == CaptureSource.ACCESSIBILITY })
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertEquals(2, harness.logs.logs.size)
        assertEquals(2, harness.logs.evidence.size)
        assertTrue(
            harness.logs.logs.all {
                it.rawCaptureRef.orEmpty().contains(AccessibilityFixtures.WECHAT_PAY_SUCCESS)
            },
        )
        assertFalse(first.toProofJson().contains("CONFIRMED"))
        assertFalse(second.toProofJson().contains("CONFIRMED"))
    }

    @Test
    fun 无效快照不产生交易且日志为无效() = runBlocking {
        val harness = Harness()
        val before = harness.transactions.rows.size
        val result = harness.ingest.ingest(
            AccessibilityFixtures.invalidBlank(),
            AccessibilityFixtures.INVALID_BLANK,
        )
        assertEquals(AccessibilityIngestOutcome.IGNORED_INVALID, result.outcome)
        assertEquals(CaptureLogStatus.IGNORED_INVALID, result.status)
        assertEquals(before, harness.transactions.rows.size)
        assertEquals(CaptureLogStatus.IGNORED_INVALID, harness.logs.logs.single().status)
        assertEquals(CaptureSource.ACCESSIBILITY, harness.logs.logs.single().captureSource)
        assertTrue(result.toProofJson().contains("\"status\":\"IGNORED_INVALID\""))
    }

    @Test
    fun 待确认不计入首页正式收支且本路径没有自动确认() = runBlocking {
        val harness = Harness()
        val before = ObserveHomeSummaryUseCase(harness.transactions)(Clock.systemUTC()).first()
        harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(),
            AccessibilityFixtures.WECHAT_PAY_SUCCESS,
        )
        val after = ObserveHomeSummaryUseCase(harness.transactions)(Clock.systemUTC()).first()

        assertEquals(before.monthExpenseMinor, after.monthExpenseMinor)
        assertEquals(before.monthIncomeMinor, after.monthIncomeMinor)
        assertEquals(0L, after.monthExpenseMinor)
        assertEquals(0L, after.monthIncomeMinor)
        assertEquals(1, after.pendingCount)
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertTrue(harness.transactions.rows.none { it.state == TransactionState.CONFIRMED })
    }

    @Test
    fun Decision给出确认时这条路径仍只写待确认() = runBlocking {
        val harness = Harness()
        val candidate = Candidate(
            currency = "CNY",
            transactionTime = AccessibilityFixtures.CAPTURED_AT,
            overallConfidence = 1.0,
            amountMinor = 3_850L,
            amountConfidence = 1.0,
            direction = TransactionType.EXPENSE,
            directionConfidence = 1.0,
            merchant = "星巴克咖啡",
            merchantConfidence = 1.0,
            categoryId = "cat-food",
            categoryConfidence = 1.0,
            accountId = "acc-wechat",
            accountConfidence = 1.0,
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            capturedAt = AccessibilityFixtures.CAPTURED_AT,
            textClue = "支付成功",
        )
        val decided = Decision(supportedPackages = AccessibilityTargets.packages).decide(candidate)
        assertTrue(decided is DecisionResult.Confirmed)

        val result = harness.ingest.persist(
            snapshot = AccessibilityFixtures.wechatPaySuccess(),
            fixtureId = "m6-2-would-confirm",
            fidelity = "{}",
            admission = AccessibilityPendingGate.admit(decided),
            candidate = candidate,
            zoneId = "Asia/Shanghai",
        )
        assertTrue(result.refusedAutoConfirm)
        assertEquals(TransactionState.PENDING, result.transactionState)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertEquals(CaptureSource.ACCESSIBILITY, harness.transactions.rows.single().source)
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
    }

    @Test
    fun 支付结果才转发_聊天页与缺字段快照都不是候选() {
        assertTrue(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.wechatPaySuccess()))
        assertFalse(AccessibilitySnapshotParser.shouldForward(AccessibilityFixtures.invalidBlank()))
        val chat = AccessibilityPageSnapshot(
            packageName = AccessibilityTargets.WECHAT_PACKAGE,
            windowId = 9,
            capturedAt = AccessibilityFixtures.CAPTURED_AT,
            nodes = listOf(
                SnapshotNode("你好", null, null, null, 1),
                SnapshotNode("在吗", null, null, null, 1),
            ),
            truncated = false,
            textChars = 4,
        )
        assertFalse(AccessibilitySnapshotParser.shouldForward(chat))
    }

    @Test
    fun 真机两页与M6_2回放只进待确认且导航金额页不入账() = runBlocking {
        val harness = Harness()
        val wx = harness.ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
        val ali = harness.ingest.ingest(AccessibilityFixtures.alipayFufu(), AccessibilityFixtures.F_ALI)
        val old = harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(),
            AccessibilityFixtures.WECHAT_PAY_SUCCESS,
        )
        val ignored = harness.ingest.ingest(
            AccessibilityFixtures.chromeAmountNotResult(),
            "m6-3-not-11-58",
        )

        assertEquals(TransactionState.PENDING, wx.transactionState)
        assertEquals(CaptureSource.ACCESSIBILITY, wx.captureSource)
        assertEquals(TransactionState.PENDING, ali.transactionState)
        assertEquals(CaptureSource.ACCESSIBILITY, ali.captureSource)
        assertEquals(TransactionState.PENDING, old.transactionState)
        assertEquals(CaptureSource.ACCESSIBILITY, old.captureSource)
        assertEquals(AccessibilityIngestOutcome.IGNORED_INVALID, ignored.outcome)
        assertEquals(null, ignored.transactionId)

        val rows = harness.transactions.rows
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.state == TransactionState.PENDING && it.source == CaptureSource.ACCESSIBILITY })
        assertTrue(rows.all { !it.confirmedByUser && it.type == TransactionType.EXPENSE })
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        val byMerchant = rows.associate { it.merchant to it.amountMinor }
        assertEquals(1_000L, byMerchant["杭州深度求索"])
        assertEquals(12_999L, byMerchant["富富"])
        assertEquals(3_850L, byMerchant["星巴克咖啡"])
    }

    @Test
    fun 保真文本会脱敏手机号() {
        val snapshot = AccessibilityFixtures.wechatPaySuccess().copy(
            nodes = AccessibilityFixtures.wechatPaySuccess().nodes + SnapshotNode(
                text = "13812345678",
                contentDescription = null,
                viewId = null,
                className = null,
                depth = 4,
            ),
        )
        val encoded = AccessibilitySnapshotFidelity.encode(snapshot, AccessibilityFixtures.WECHAT_PAY_SUCCESS)
        assertFalse(encoded.contains("13812345678"))
        assertTrue(encoded.contains("***"))
        assertTrue(encoded.contains(AccessibilityFixtures.WECHAT_PAY_SUCCESS))
        assertTrue(encoded.contains("\"capturedAt\":${AccessibilityFixtures.CAPTURED_AT}"))
    }
}

    @Test
    fun 同一通知投递两次只留一条捕获和一条交易() = runBlocking {
        val harness = Harness()
        val snapshot = AccessibilityFixtures.wechatDepthSeek()
        val first = harness.ingest.ingest(
            snapshot,
            AccessibilityFixtures.AC1_WX,
            notificationId = AccessibilityFixtures.AC1_NOTIFICATION_ID,
            postTime = AccessibilityFixtures.AC1_POST_TIME,
        )
        val second = harness.ingest.ingest(
            snapshot,
            AccessibilityFixtures.AC1_WX,
            notificationId = AccessibilityFixtures.AC1_NOTIFICATION_ID,
            postTime = AccessibilityFixtures.AC1_POST_TIME,
        )
        assertEquals(first.transactionId, second.transactionId)
        assertEquals(first.captureId, second.captureId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(1, harness.logs.logs.size)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertFalse(harness.logs.logs.single().reason.orEmpty().contains("DUPLICATE"))
    }

    @Test
    fun 同商户同金额一百八十秒内第二条是疑似重复且仍待确认() = runBlocking {
        val harness = Harness()
        val first = harness.ingest.ingest(
            AccessibilityFixtures.wechatDepthSeek(),
            AccessibilityFixtures.F_WX,
        )
        val second = harness.ingest.ingest(
            AccessibilityFixtures.wechatDepthSeek(),
            AccessibilityFixtures.F_WX,
        )
        assertEquals(2, harness.transactions.rows.size)
        assertEquals(2, harness.logs.logs.size)
        assertFalse(first.reasons.contains(com.zhangran.app.domain.model.PendingReason.POSSIBLE_DUPLICATE))
        assertTrue(second.reasons.contains(com.zhangran.app.domain.model.PendingReason.POSSIBLE_DUPLICATE))
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
    }

    @Test
    fun 一百八十一秒或不同商户或退款都不标疑似重复() = runBlocking {
        val harness = Harness()
        harness.ingest.ingest(AccessibilityFixtures.wechatDepthSeek(), AccessibilityFixtures.F_WX)
        harness.ingest.ingest(
            AccessibilityFixtures.wechatDepthSeek(
                AccessibilityFixtures.WX_CAPTURED_AT + AccessibilityFixtures.AC3A_DELTA_MILLIS,
            ),
            AccessibilityFixtures.AC3A_WX,
        )
        harness.ingest.ingest(
            AccessibilityFixtures.alipayFufuTenYuan(),
            AccessibilityFixtures.AC3B_FUFU,
        )
        harness.ingest.ingest(
            AccessibilityFixtures.wechatRefund(),
            AccessibilityFixtures.AC3C_REFUND,
        )
        assertEquals(4, harness.transactions.rows.size)
        assertTrue(harness.logs.logs.none { it.reason.orEmpty().contains("POSSIBLE_DUPLICATE") })
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertEquals(1, harness.transactions.rows.count { it.type == com.zhangran.app.domain.model.TransactionType.REFUND })
    }

private class Harness {
    val transactions = MemoryTransactions()
    val logs = MemoryLogs()
    val ingest = AccessibilitySnapshotIngest(
        normalize = Normalize(
            categoryResolver = CategoryResolver { null },
            accountResolver = AccountResolver { null },
            defaultZoneId = "Asia/Shanghai",
        ),
        transactions = transactions,
        records = logs,
        clock = { 1_762_900_000_000L },
        newId = IdSeq()::next,
    )
}

private class IdSeq {
    private var n = 0
    fun next(): String = "m6-${++n}"
}

private class MemoryTransactions : TransactionRepository {
    val rows = mutableListOf<TransactionEntity>()
    private val visible = MutableStateFlow<List<TransactionEntity>>(emptyList())

    private fun publish() {
        visible.value = rows.filter { it.deletedAt == null }
    }

    override suspend fun insert(transaction: TransactionEntity) {
        rows += transaction
        publish()
    }

    override suspend fun listByState(state: TransactionState): List<TransactionEntity> =
        rows.filter { it.state == state && it.deletedAt == null }
            .sortedByDescending { it.transactionTime }

    override suspend fun listVisible(): List<TransactionEntity> =
        rows.filter { it.deletedAt == null }.sortedByDescending { it.transactionTime }

    override fun observeVisible(): Flow<List<TransactionEntity>> = visible

    override suspend fun listInRange(startInclusive: Long, endExclusive: Long): List<TransactionEntity> =
        rows.filter {
            it.state == TransactionState.CONFIRMED &&
                it.deletedAt == null &&
                it.transactionTime >= startInclusive &&
                it.transactionTime < endExclusive
        }

    override suspend fun countByState(state: TransactionState): Int =
        rows.count { it.state == state && it.deletedAt == null }

    override suspend fun getById(id: String, includeDeleted: Boolean): TransactionEntity? =
        rows.firstOrNull { it.id == id && (includeDeleted || it.deletedAt == null) }

    override suspend fun update(transaction: TransactionEntity) {
        val index = rows.indexOfFirst { it.id == transaction.id }
        if (index >= 0) rows[index] = transaction
        publish()
    }

    override suspend fun softDelete(id: String, deletedAt: Long) = Unit

    override suspend fun restore(id: String, updatedAt: Long) = Unit

    override suspend fun search(criteria: SearchCriteria): List<TransactionEntity> = emptyList()

    override suspend fun countSearch(criteria: SearchCriteria): Int = 0
}

private class MemoryLogs : CaptureLogRepository {
    val logs = mutableListOf<CaptureLogEntity>()
    val evidence = mutableListOf<CaptureEvidenceEntity>()

    override suspend fun insert(log: CaptureLogEntity, evidence: CaptureEvidenceEntity?) {
        logs.removeAll { it.id == log.id }
        logs += log
        if (evidence != null) {
            this.evidence.removeAll { it.id == evidence.id }
            this.evidence += evidence
        }
    }

    override suspend fun findByCaptureId(captureId: String): CaptureLogEntity? =
        logs.firstOrNull { it.captureId == captureId }

    override suspend fun findByTransactionId(transactionId: String): CaptureLogEntity? =
        logs.filter { it.transactionId == transactionId }.maxByOrNull { it.capturedAt }

    override suspend fun listAll(): List<CaptureLogEntity> = logs.toList()

    override suspend fun listEvidence(captureId: String): List<CaptureEvidenceEntity> =
        evidence.filter { it.captureId == captureId }

    override suspend fun update(log: CaptureLogEntity) {
        val index = logs.indexOfFirst { it.id == log.id }
        if (index >= 0) logs[index] = log
    }

    override suspend fun findById(id: String): CaptureLogEntity? = logs.firstOrNull { it.id == id }

    override suspend fun clearLogsAndEvidence() {
        logs.clear()
        evidence.clear()
    }

    override suspend fun countEvidence(): Int = evidence.size
}
