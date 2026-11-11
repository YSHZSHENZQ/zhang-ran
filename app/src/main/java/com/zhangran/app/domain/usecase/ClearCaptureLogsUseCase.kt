/**
 * 清空捕获日志和证据。不碰交易、待确认和字段保护。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.repository.CaptureLogRepository
import javax.inject.Inject

class ClearCaptureLogsUseCase @Inject constructor(
    private val logs: CaptureLogRepository,
) {

    suspend operator fun invoke() {
        logs.clearLogsAndEvidence()
    }
}
