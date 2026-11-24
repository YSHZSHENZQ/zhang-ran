# AC-11 Proof — fx-1-f-wx-success-no-keyword

- Functional commit / APK GIT_SHA: `383a0c5f62a8e7378b9a2d08097fa21bbf7d0b49`
- Fixture: `fx-1-f-wx-success-no-keyword` (`AccessibilityFixtures.wechatSuccess(includeKeyword=false)`)
- Method: instrumented ingest on emulator package `com.zhangran.app` via throwaway `Fx1Cr2ProofTest` (not committed; removed after pull). Also covered by JVM unit tests.
- Result: **pendingCount = 1**
  - amountMinor = 800 (¥8.00)
  - type = EXPENSE
  - merchant = 示例早餐店 (not 「完成」, not 「摇一摇，有优惠」)
- Artifacts: `ac11-wx-success-no-keyword.json`, `ac11-ledger.json` (both `gitSha=383a0c5f62a8e7378b9a2d08097fa21bbf7d0b49`)
- Commands:
  - `adb shell am instrument -w -e class com.zhangran.app.capture.accessibility.Fx1Cr2ProofTest com.zhangran.app.test/androidx.test.runner.AndroidJUnitRunner`
  - JVM: `./gradlew testDebugUnitTest` → `微信没有结果用语的成功页进一条待确认` PASS
