/**
 * 调试取证前清掉交易、字段保护和捕获日志。不删分类与账户。正式页面不调用。
 */
package com.zhangran.app.capture.accessibility

import androidx.room.withTransaction
import com.zhangran.app.data.local.ZhangRanDatabase
import javax.inject.Inject

class DebugLedgerReset @Inject constructor(
    private val database: ZhangRanDatabase,
) {

    suspend fun clearCapturedLedger() {
        database.withTransaction {
            database.fieldEditDao().deleteAll()
            database.transactionDao().deleteAll()
            database.captureLogDao().clearAll()
            database.captureEvidenceDao().clearAll()
        }
    }
}
