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
import com.zhangran.app.domain.usecase.CaptureLogExport
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
import org.junit.Assert.assertNull
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
        assertEquals(first.transactionId, second.transactionId)
        assertEquals(first.captureId, second.captureId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertEquals(CaptureSource.ACCESSIBILITY, harness.transactions.rows.single().source)
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
        assertEquals(1, harness.logs.logs.size)
        assertEquals(1, harness.logs.evidence.size)
        assertFalse(second.evidenceWritten)
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
            AccessibilityFixtures.wechatDepthSeek(
                AccessibilityFixtures.WX_CAPTURED_AT - SAME_PAGE_STAY_MILLIS - 5_000L,
            ),
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
        val storedSecond = harness.transactions.rows.single { it.id == second.transactionId }
        val storedFirst = harness.transactions.rows.single { it.id == first.transactionId }
        assertTrue(storedSecond.pendingReasons.orEmpty().contains("POSSIBLE_DUPLICATE"))
        assertFalse(storedFirst.pendingReasons.orEmpty().contains("POSSIBLE_DUPLICATE"))
        assertEquals(
            harness.logs.logs.single { it.transactionId == storedSecond.id }.reason,
            storedSecond.pendingReasons,
        )
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

    @Test
    fun 余额宝和拆分金额都只进待确认且商户为空() = runBlocking {
        val harness = Harness()
        val first = harness.ingest.ingest(
            AccessibilityFixtures.alipayYuebao(capturedAt = 1_800_000_000_000L),
            AccessibilityFixtures.F_YUEBAO,
        )
        val second = harness.ingest.ingest(
            AccessibilityFixtures.alipayYuebaoSplit(capturedAt = 1_800_000_001_000L),
            AccessibilityFixtures.F_YUEBAO_SPLIT,
        )
        assertEquals(AccessibilityIngestOutcome.PENDING, first.outcome)
        assertEquals(AccessibilityIngestOutcome.PENDING, second.outcome)
        assertEquals(2, harness.transactions.rows.size)
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertTrue(harness.transactions.rows.all { it.amountMinor == 2_510L })
        assertTrue(harness.transactions.rows.all { it.type == TransactionType.EXPENSE })
        assertTrue(harness.transactions.rows.all { it.merchant == null })
        assertTrue(harness.transactions.rows.all { it.confirmedByUser == false })
        assertTrue(harness.transactions.rows.all { it.source == CaptureSource.ACCESSIBILITY })
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
    }

    @Test
    fun 看错窗口不入账且不更新上次捕获时间() = runBlocking {
        val harness = Harness()
        harness.health.setLastCaptureAt(1_700_000_000_000L)
        val calls = harness.health.captureCalls
        val result = harness.ingest.ingest(
            AccessibilityFixtures.wrongWindowHome(),
            AccessibilityFixtures.F_WRONG_WINDOW,
        )
        assertEquals(AccessibilityIngestOutcome.IGNORED_INVALID, result.outcome)
        assertEquals(CaptureLogStatus.IGNORED_INVALID, result.status)
        assertEquals(0, harness.transactions.rows.size)
        assertEquals(1_700_000_000_000L, harness.health.current.lastCaptureAtMillis)
        assertEquals(calls, harness.health.captureCalls)
        assertTrue(harness.health.current.rejectedSamplesSinceCapture)
        val log = harness.logs.logs.single()
        assertEquals("WRONG_WINDOW", log.reason)
        assertTrue(log.rawCaptureRef.orEmpty().contains(AccessibilityFixtures.F_WRONG_WINDOW))
        assertTrue(log.rawCaptureRef.orEmpty().contains("com.zhangran.app"))
        assertFalse(log.rawCaptureRef.orEmpty().contains("我的"))
        assertFalse(log.rawCaptureRef.orEmpty().contains("还没有记录"))
        assertFalse(log.rawCaptureRef.orEmpty().contains("疑似失效"))
    }

    @Test
    fun 同单号隔七十分钟不再插入忽略或确认后也不再插入() = runBlocking {
        val harness = Harness()
        val firstAt = 1_794_100_000_000L
        val page = payPage(capturedAt = firstAt, orderId = "202611190390")
        val first = harness.ingest.ingest(page, "fx1-order-a")
        val again = harness.ingest.ingest(
            page.copy(capturedAt = firstAt + SEVENTY_MINUTES),
            "fx1-order-a-late",
        )
        assertEquals(first.transactionId, again.transactionId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals("202611190390", harness.transactions.rows.single().externalTransactionId)
        assertEquals(firstAt, harness.logs.logs.single().capturedAt)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))

        harness.transactions.update(
            harness.transactions.rows.single().copy(
                state = TransactionState.CONFIRMED,
                confirmedByUser = true,
            ),
        )
        val afterConfirm = harness.ingest.ingest(
            page.copy(capturedAt = firstAt + SEVENTY_MINUTES),
            "fx1-order-confirmed",
        )
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(TransactionState.CONFIRMED, afterConfirm.transactionState)
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))

        harness.transactions.update(
            harness.transactions.rows.single().copy(state = TransactionState.IGNORED),
        )
        val afterIgnore = harness.ingest.ingest(
            page.copy(capturedAt = firstAt + SEVENTY_MINUTES + 1_000L),
            "fx1-order-ignored",
        )
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(TransactionState.IGNORED, afterIgnore.transactionState)
        assertEquals(first.transactionId, afterIgnore.transactionId)
    }

    @Test
    fun 软删除后的同单号可以再记一条() = runBlocking {
        val harness = Harness()
        val page = payPage(capturedAt = 1_794_100_000_000L, orderId = "202611190390")
        harness.ingest.ingest(page, "fx1-order-deleted")
        val stored = harness.transactions.rows.single()
        harness.transactions.update(stored.copy(deletedAt = 1L))
        harness.ingest.ingest(page.copy(capturedAt = page.capturedAt + SEVENTY_MINUTES), "fx1-order-new")
        assertEquals(2, harness.transactions.rows.size)
        assertEquals(1, harness.transactions.listVisible().size)
        assertEquals(TransactionState.PENDING, harness.transactions.listVisible().single().state)
    }

    @Test
    fun 不同单号同金额仍是两条() = runBlocking {
        val harness = Harness()
        val at = 1_794_100_000_000L
        harness.ingest.ingest(payPage(capturedAt = at, orderId = "202611190390"), "fx1-order-1")
        harness.ingest.ingest(payPage(capturedAt = at, orderId = "202611190391"), "fx1-order-2")
        assertEquals(2, harness.transactions.rows.size)
        assertEquals(
            setOf("202611190390", "202611190391"),
            harness.transactions.rows.map { it.externalTransactionId }.toSet(),
        )
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertTrue(harness.transactions.rows.all { it.amountMinor == 390L })
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
    }

    @Test
    fun 同支付时间隔七十分钟不再插入且捕获时刻另存() = runBlocking {
        val harness = Harness()
        val firstAt = 1_794_200_000_000L
        val page = payPage(capturedAt = firstAt, paymentTime = "2026-11-19 08:10:00")
        val parsed = AccessibilitySnapshotParser.parse(page)
        val first = harness.ingest.ingest(page, "fx1-pay-time")
        val again = harness.ingest.ingest(
            page.copy(capturedAt = firstAt + SEVENTY_MINUTES),
            "fx1-pay-time-late",
        )
        assertEquals(first.transactionId, again.transactionId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(parsed?.paymentTimeMillis, harness.transactions.rows.single().transactionTime)
        assertEquals(1_795_047_000_000L, harness.transactions.rows.single().transactionTime)
        assertEquals(firstAt, harness.logs.logs.single().capturedAt)
        assertNull(harness.transactions.rows.single().externalTransactionId)
        assertEquals("瑞幸咖啡", harness.transactions.rows.single().merchant)
        assertNotEquals("更多功能", harness.transactions.rows.single().merchant)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
    }

    @Test
    fun 支付时间相差十分钟仍是两条() = runBlocking {
        val harness = Harness()
        val at = 1_794_200_000_000L
        harness.ingest.ingest(
            payPage(capturedAt = at, paymentTime = "2026-11-19 08:10:00"),
            "fx1-pay-0810",
        )
        harness.ingest.ingest(
            payPage(capturedAt = at + 10 * 60_000L, paymentTime = "2026-11-19 08:20:00"),
            "fx1-pay-0820",
        )
        assertEquals(2, harness.transactions.rows.size)
        assertEquals(
            setOf(1_795_047_000_000L, 1_795_047_600_000L),
            harness.transactions.rows.map { it.transactionTime }.toSet(),
        )
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
    }

    @Test
    fun 同支付时间但两侧商户不同仍是两条一侧为空则不再插入() = runBlocking {
        val harness = Harness()
        val at = 1_794_200_000_000L
        val time = "2026-11-19 08:10:00"
        harness.ingest.ingest(
            payPage(capturedAt = at, paymentTime = time, merchant = "瑞幸咖啡"),
            "fx1-merchant-a",
        )
        harness.ingest.ingest(
            payPage(capturedAt = at + SEVENTY_MINUTES, paymentTime = time, merchant = "星巴克咖啡"),
            "fx1-merchant-b",
        )
        assertEquals(2, harness.transactions.rows.size)
        assertEquals(
            setOf("瑞幸咖啡", "星巴克咖啡"),
            harness.transactions.rows.map { it.merchant }.toSet(),
        )

        val other = Harness()
        val named = payPage(capturedAt = at, paymentTime = time, merchant = "瑞幸咖啡")
        val blank = payPage(capturedAt = at + SEVENTY_MINUTES, paymentTime = time, merchant = null)
        val first = other.ingest.ingest(named, "fx1-merchant-named")
        val second = other.ingest.ingest(blank, "fx1-merchant-blank")
        assertEquals(first.transactionId, second.transactionId)
        assertEquals(1, other.transactions.rows.size)
        assertEquals("瑞幸咖啡", other.transactions.rows.single().merchant)
    }

    @Test
    fun 不同包名的同支付时间不合并() = runBlocking {
        val harness = Harness()
        val at = 1_794_200_000_000L
        val time = "2026-11-19 08:10:00"
        harness.ingest.ingest(
            payPage(
                capturedAt = at,
                paymentTime = time,
                packageName = AccessibilityTargets.WECHAT_PACKAGE,
            ),
            "fx1-pkg-wx",
        )
        harness.ingest.ingest(
            payPage(
                capturedAt = at + SEVENTY_MINUTES,
                paymentTime = time,
                packageName = AccessibilityTargets.ALIPAY_PACKAGE,
                windowId = 78,
            ),
            "fx1-pkg-ali",
        )
        assertEquals(2, harness.transactions.rows.size)
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
    }

    @Test
    fun 没有单号也没有支付时间时两秒内同页合并七十分钟后仍新建() = runBlocking {
        val harness = Harness()
        val base = AccessibilityFixtures.wechatPaySuccess()
        val first = harness.ingest.ingest(base, "fx1-stay")
        val soon = harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(capturedAt = base.capturedAt + 2_000L),
            "fx1-stay-2s",
        )
        assertEquals(first.transactionId, soon.transactionId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(1, harness.logs.logs.size)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)

        val later = harness.ingest.ingest(
            AccessibilityFixtures.wechatPaySuccess(capturedAt = base.capturedAt + SEVENTY_MINUTES),
            "fx1-stay-70m",
        )
        assertNotEquals(first.transactionId, later.transactionId)
        assertEquals(2, harness.transactions.rows.size)
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
        assertTrue(harness.transactions.rows.all { it.amountMinor == 3_850L })
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
    }

    @Test
    fun 同金额但窗口不同即使两秒内也不合并() = runBlocking {
        val harness = Harness()
        val base = AccessibilityFixtures.wechatPaySuccess()
        harness.ingest.ingest(base, "fx1-window-a")
        harness.ingest.ingest(
            base.copy(windowId = base.windowId + 1, capturedAt = base.capturedAt + 2_000L),
            "fx1-window-b",
        )
        assertEquals(2, harness.transactions.rows.size)
        assertTrue(harness.transactions.rows.all { it.amountMinor == 3_850L })
        assertTrue(harness.transactions.rows.all { it.state == TransactionState.PENDING })
    }

    @Test
    fun 微信会话列表不入库并留下不含正文的拒绝日志() = runBlocking {
        val harness = Harness()
        val page = AccessibilityFixtures.wechatConversationList()
        val result = harness.ingest.ingest(page, "fx1-wx-chat")
        assertEquals(AccessibilityIngestOutcome.IGNORED_INVALID, result.outcome)
        assertEquals(CaptureLogStatus.IGNORED_INVALID, result.status)
        assertEquals(0, harness.transactions.rows.size)
        assertEquals(0, harness.transactions.countByState(TransactionState.PENDING))
        assertNull(result.transactionId)
        val log = harness.logs.logs.single()
        assertEquals("NOT_PAYMENT_PAGE", log.reason)
        assertNull(log.transactionId)
        val exported = CaptureLogExport.json(
            gitSha = "test",
            entries = listOf(CaptureLogExport.entry(log, java.time.ZoneId.of("Asia/Shanghai"))),
        )
        assertTrue(exported.contains("\"windowId\":44349"))
        assertTrue(exported.contains("\"rootClass\":\"android.widget.FrameLayout\""))
        assertTrue(exported.contains("com.tencent.mm:id/jga"))
        assertTrue(exported.contains("\"nodeCount\":4"))
        assertTrue(exported.contains("\"hasAmountStyle\":true"))
        assertTrue(exported.contains("\"hasPaymentKeyword\":true"))
        assertTrue(exported.contains("NOT_PAYMENT_PAGE"))
        listOf("王小明", "晚上吃饭", "更多功能", "头像", "3.90", "3.9", "390").forEach { secret ->
            assertFalse(exported.contains(secret))
            assertFalse(log.rawCaptureRef.orEmpty().contains(secret))
        }
        assertTrue(CaptureLogExport.sensitiveFindings(exported).isEmpty())
    }

    @Test
    fun 同一会话列表隔七十分钟仍不产生待确认() = runBlocking {
        val harness = Harness()
        val firstAt = AccessibilityFixtures.CAPTURED_AT
        harness.ingest.ingest(AccessibilityFixtures.wechatConversationList(firstAt), "fx1-wx-chat-1")
        harness.ingest.ingest(
            AccessibilityFixtures.wechatConversationList(firstAt + SEVENTY_MINUTES),
            "fx1-wx-chat-2",
        )
        assertEquals(0, harness.transactions.rows.size)
        assertEquals(0, harness.transactions.countByState(TransactionState.PENDING))
        assertEquals(2, harness.logs.logs.size)
        assertTrue(harness.logs.logs.all { it.reason == "NOT_PAYMENT_PAGE" && it.transactionId == null })
    }

    @Test
    fun 微信没有结果用语的成功页进一条待确认() = runBlocking {
        val harness = Harness()
        val page = AccessibilityFixtures.wechatSuccess(includeKeyword = false)
        val first = harness.ingest.ingest(page, AccessibilityFixtures.F_WX_SUCCESS_NO_KEYWORD)
        val again = harness.ingest.ingest(
            page.copy(capturedAt = page.capturedAt + 1_300L),
            AccessibilityFixtures.F_WX_SUCCESS_NO_KEYWORD,
        )
        assertEquals(first.transactionId, again.transactionId)
        val row = harness.transactions.rows.single()
        assertEquals(TransactionState.PENDING, row.state)
        assertEquals(TransactionType.EXPENSE, row.type)
        assertEquals(800L, row.amountMinor)
        assertEquals(AccessibilityFixtures.WX_SUCCESS_MERCHANT, row.merchant)
        assertNotEquals("完成", row.merchant)
        assertNotEquals(AccessibilityFixtures.WX_SUCCESS_PROMO, row.merchant)
        assertNull(row.externalTransactionId)
        assertEquals(0, harness.transactions.countByState(TransactionState.CONFIRMED))
        assertEquals(0, harness.transactions.countByState(TransactionState.DUPLICATE))
    }

    @Test
    fun 微信有结果用语的成功页同样是一条待确认() = runBlocking {
        val harness = Harness()
        harness.ingest.ingest(
            AccessibilityFixtures.wechatSuccess(includeKeyword = true),
            AccessibilityFixtures.F_WX_SUCCESS,
        )
        val row = harness.transactions.rows.single()
        assertEquals(TransactionState.PENDING, row.state)
        assertEquals(TransactionType.EXPENSE, row.type)
        assertEquals(800L, row.amountMinor)
        assertEquals(AccessibilityFixtures.WX_SUCCESS_MERCHANT, row.merchant)
        assertNotEquals("完成", row.merchant)
        assertNotEquals(AccessibilityFixtures.WX_SUCCESS_PROMO, row.merchant)
    }

    @Test
    fun 微信确认支付页不进待确认() = runBlocking {
        val harness = Harness()
        val page = AccessibilityFixtures.wechatConfirmPay()
        val result = harness.ingest.ingest(page, AccessibilityFixtures.F_WX_CONFIRM_PAY)
        assertEquals(AccessibilityIngestOutcome.IGNORED_INVALID, result.outcome)
        assertEquals(0, harness.transactions.rows.size)
        assertEquals(0, harness.transactions.countByState(TransactionState.PENDING))
        val log = harness.logs.logs.single()
        assertEquals("NOT_PAYMENT_PAGE", log.reason)
        val exported = CaptureLogExport.json(
            gitSha = "test",
            entries = listOf(CaptureLogExport.entry(log, java.time.ZoneId.of("Asia/Shanghai"))),
        )
        listOf(
            AccessibilityFixtures.WX_SUCCESS_MERCHANT,
            "确认支付",
            "立即支付",
            "8.00",
            "8.0",
        ).forEach { secret ->
            assertFalse(exported.contains(secret))
            assertFalse(log.rawCaptureRef.orEmpty().contains(secret))
        }
    }

    @Test
    fun 同窗口同金额节点文本变了仍合并() = runBlocking {
        val harness = Harness()
        val at = 1_794_210_000_000L
        val paying = amountPage(capturedAt = at, extra = "支付金额")
        val home = amountPage(capturedAt = at + 1_300L, extra = "回首页")
        assertNotEquals(paying.nodes.map { it.text }, home.nodes.map { it.text })
        val firstParsed = AccessibilitySnapshotParser.parse(paying)
        val secondParsed = AccessibilitySnapshotParser.parse(home)
        assertNotNull(firstParsed)
        assertNotNull(secondParsed)
        assertEquals(770L, firstParsed!!.amountMinor)
        assertEquals(770L, secondParsed!!.amountMinor)
        assertNull(firstParsed.externalTransactionId)
        assertNull(secondParsed.externalTransactionId)
        assertNull(firstParsed.paymentTimeMillis)
        assertNull(secondParsed.paymentTimeMillis)
        val first = harness.ingest.ingest(paying, "fx1-flip-amount")
        val second = harness.ingest.ingest(home, "fx1-flip-home")
        assertEquals(first.transactionId, second.transactionId)
        assertEquals(1, harness.transactions.rows.size)
        assertEquals(770L, harness.transactions.rows.single().amountMinor)
        assertEquals(TransactionState.PENDING, harness.transactions.rows.single().state)
        assertEquals(1, harness.logs.logs.size)
        assertFalse(second.evidenceWritten)
    }

    @Test
    fun `两笔不同的同金额付款是 2 条`() = runBlocking {
        val orders = Harness()
        val at = 1_794_220_000_000L
        orders.ingest.ingest(
            payPage(capturedAt = at, orderId = "202611191001", merchant = null),
            "fx1-two-order-a",
        )
        orders.ingest.ingest(
            payPage(capturedAt = at + 2_000L, orderId = "202611191002", merchant = null),
            "fx1-two-order-b",
        )
        assertEquals(2, orders.transactions.rows.size)
        assertEquals(
            setOf("202611191001", "202611191002"),
            orders.transactions.rows.map { it.externalTransactionId }.toSet(),
        )

        val later = Harness()
        val stay = amountPage(capturedAt = at, extra = "支付金额")
        later.ingest.ingest(stay, "fx1-two-16s-a")
        later.ingest.ingest(
            amountPage(capturedAt = at + 16_000L, extra = "回首页"),
            "fx1-two-16s-b",
        )
        assertEquals(2, later.transactions.rows.size)
        assertTrue(later.transactions.rows.all { it.amountMinor == 770L && it.state == TransactionState.PENDING })

        val windows = Harness()
        windows.ingest.ingest(amountPage(capturedAt = at, extra = "支付金额", windowId = 8801), "fx1-two-win-a")
        windows.ingest.ingest(
            amountPage(capturedAt = at + 2_000L, extra = "回首页", windowId = 8802),
            "fx1-two-win-b",
        )
        assertEquals(2, windows.transactions.rows.size)
        assertTrue(windows.transactions.rows.all { it.amountMinor == 770L && it.state == TransactionState.PENDING })
    }
}

private const val SEVENTY_MINUTES: Long = 70L * 60L * 1_000L

/** 合成结果页。金额固定 ¥7.70，没有单号和支付时间。节点正文只有调用方传入的那一个词。 */
private fun amountPage(
    capturedAt: Long,
    extra: String,
    windowId: Int = 8801,
): AccessibilityPageSnapshot {
    val nodes = listOf(
        SnapshotNode("支付成功", null, null, null, 1),
        SnapshotNode(extra, null, null, null, 2),
        SnapshotNode("¥7.70", null, null, null, 3),
        SnapshotNode("完成", null, null, null, 4),
    )
    return AccessibilityPageSnapshot(
        packageName = AccessibilityTargets.ALIPAY_PACKAGE,
        windowId = windowId,
        capturedAt = capturedAt,
        nodes = nodes,
        truncated = false,
        textChars = nodes.sumOf { it.text?.length ?: 0 },
        rootPackageName = AccessibilityTargets.ALIPAY_PACKAGE,
    )
}

private fun payPage(
    capturedAt: Long,
    orderId: String? = null,
    paymentTime: String? = null,
    merchant: String? = "瑞幸咖啡",
    packageName: String = AccessibilityTargets.WECHAT_PACKAGE,
    windowId: Int = 77,
): AccessibilityPageSnapshot {
    val nodes = mutableListOf<SnapshotNode>()
    nodes += SnapshotNode("更多功能", null, null, null, 1)
    nodes += SnapshotNode("支付成功", null, null, null, 2)
    if (merchant != null) nodes += SnapshotNode(merchant, null, null, null, 3)
    nodes += SnapshotNode("¥3.90", null, null, null, 4)
    if (paymentTime != null) {
        nodes += SnapshotNode("支付时间", null, null, null, 5)
        nodes += SnapshotNode(paymentTime, null, null, null, 5)
    }
    if (orderId != null) {
        nodes += SnapshotNode("订单号", null, null, null, 6)
        nodes += SnapshotNode(orderId, null, null, null, 6)
    }
    nodes += SnapshotNode("完成", null, null, null, 7)
    return AccessibilityPageSnapshot(
        packageName = packageName,
        windowId = windowId,
        capturedAt = capturedAt,
        nodes = nodes,
        truncated = false,
        textChars = nodes.sumOf { it.text?.length ?: 0 },
        rootPackageName = packageName,
    )
}

private class Harness {
    val transactions = MemoryTransactions()
    val logs = MemoryLogs()
    val health = MemoryHealth()
    private val ids = IdSeq()
    val ingest = AccessibilitySnapshotIngest(
        normalize = Normalize(
            categoryResolver = CategoryResolver { null },
            accountResolver = AccountResolver { null },
            defaultZoneId = "Asia/Shanghai",
        ),
        transactions = transactions,
        records = logs,
        clock = { 1_762_900_000_000L },
        newId = ids::next,
        onCaptured = { at -> health.recordCapture(at) },
        rejections = AccessibilityRejectionLog(
            records = logs,
            health = health,
            newId = { "rej-${ids.next()}" },
        ),
    )
}

internal class IdSeq {
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

internal class MemoryHealth : com.zhangran.app.data.settings.CaptureHealthStore {
    var current = com.zhangran.app.data.settings.CaptureHealth()
    var captureCalls = 0

    override val state = kotlinx.coroutines.flow.MutableStateFlow(current)

    private fun publish() {
        state.value = current
    }

    override suspend fun recordCapture(atMillis: Long) {
        captureCalls += 1
        val last = current.lastCaptureAtMillis
        current = current.copy(
            lastCaptureAtMillis = if (last == null || atMillis > last) atMillis else last,
            rejectedSamplesSinceCapture = false,
        )
        publish()
    }

    override suspend fun setLastCaptureAt(atMillis: Long) {
        current = current.copy(lastCaptureAtMillis = atMillis)
        publish()
    }

    override suspend fun setDebugTimeline(lastCaptureAtMillis: Long, watchStartedAtMillis: Long) {
        current = current.copy(
            lastCaptureAtMillis = lastCaptureAtMillis,
            watchStartedAtMillis = watchStartedAtMillis,
        )
        publish()
    }

    override suspend fun markWatchStarted(atMillis: Long) {
        current = current.copy(watchStartedAtMillis = atMillis)
        publish()
    }

    override suspend fun clearWatchStarted() {
        current = current.copy(watchStartedAtMillis = null)
        publish()
    }

    override suspend fun recordRejectedSample() {
        current = current.copy(rejectedSamplesSinceCapture = true)
        publish()
    }

    override suspend fun markStaleFlip(marker: String) {
        current = current.copy(staleFlipMarker = marker)
        publish()
    }

    override suspend fun clearStaleFlipMarker() {
        current = current.copy(staleFlipMarker = null)
        publish()
    }
}

internal class MemoryLogs : CaptureLogRepository {
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
