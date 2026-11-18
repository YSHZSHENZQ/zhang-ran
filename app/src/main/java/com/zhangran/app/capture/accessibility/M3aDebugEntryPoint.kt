/**
 * 仪器测试取证入口（M3a）。不挂到正式导航。
 */
package com.zhangran.app.capture.accessibility

import com.zhangran.app.data.autoledger.AutoLedgerStaleFlipMonitor
import com.zhangran.app.data.autoledger.AutoLedgerStatusSource
import com.zhangran.app.data.repository.CaptureLogRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.data.repository.DuplicateDecisionRepository
import com.zhangran.app.data.repository.TransactionRepository
import com.zhangran.app.data.settings.CaptureHealthStore
import com.zhangran.app.domain.usecase.LedgerProofQuery
import com.zhangran.app.domain.usecase.SetAutoLedgerEnabledUseCase
import com.zhangran.app.domain.usecase.UpdateTransactionFieldUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface M3aDebugEntryPoint {
    fun rerunAutomaticRecognition(): RerunAutomaticRecognition

    fun ledgerProofQuery(): LedgerProofQuery

    fun debugLedgerReset(): DebugLedgerReset

    fun accessibilitySnapshotIngest(): AccessibilitySnapshotIngest

    fun categoryRepository(): CategoryRepository

    fun captureLogRepository(): CaptureLogRepository

    fun duplicateDecisionRepository(): DuplicateDecisionRepository

    fun transactionRepository(): TransactionRepository

    fun setAutoLedgerEnabledUseCase(): SetAutoLedgerEnabledUseCase

    fun updateTransactionFieldUseCase(): UpdateTransactionFieldUseCase

    fun captureHealthStore(): CaptureHealthStore

    fun accessibilityRejectionLog(): AccessibilityRejectionLog

    fun rejectionRateLimiter(): RejectionRateLimiter

    fun autoLedgerStaleFlipMonitor(): AutoLedgerStaleFlipMonitor

    fun autoLedgerStatusSource(): AutoLedgerStatusSource
}
