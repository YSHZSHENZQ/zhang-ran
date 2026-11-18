# 2026-11-13 14:45「疑似失效」根因

结论：健康度算式和当时的事实一致。自动记账总开关开着、无障碍已授权，但从未成功入账，`lastCaptureAt` 一直是 null。静默从「开始监听」起算，满 24 小时后变成疑似失效。24 小时阈值没有改。

被拒的采样当时只打到 logcat，导出里看不见，所以看起来像是静默失效。

## 时间线（2026-11-13）

| 时刻 | 现象 | 对应状态 |
|---|---|---|
| 2026-11-12 13:10、13:31 | 导出里只有 `SERVICE_CONNECTED` | 服务连上过。这不是成功捕获，也不等于 `watchStartedAt` |
| 2026-11-13 09:00 | 首页仍是「可用」 | `now - watchStartedAt < 24h`，且 `lastCaptureAt == null` |
| 2026-11-13 14:45 | 首页变成「疑似失效」 | 同一条静默跨过 24 小时 |
| 付款 ¥25.10 | 没有待确认 | 采到的是账然自己的窗口，不是支付宝结果页 |

09:00 仍可用、14:45 变疑似失效，和「开始监听约在 2026-11-12 14:45、此后没有成功捕获」相符。`SERVICE_CONNECTED` 比这个时刻更早，不能拿来当开始监听的时间。

## 代码路径

1. 采样晚了 300ms，读的是当时的活动窗口。  
   `AccessibilityCaptureService.scheduleSample` → `DelayedSampleScheduler`（300ms）→ `rootInActiveWindow`。  
   快照上的包名用的是事件包名（`NodeTreeReader`），没有核对窗口根包名。  
   服务配置当时只有 `flagReportViewIds`，拿不到非活动窗口。
2. 于是支付宝事件配上了账然首页或系统界面。解析器认不出支付结果，`shouldForward == parse() != null` 为 false，不入库。
3. `WINDOW_NULL` / `NODE_EMPTY` / `NOT_PAYMENT_PAGE` / `SWITCH_OFF` 只进 `AccessibilityDiagnostics.log`（`Log.d`）。失败不会走到 ingest，也不会进 `capture_logs` 和导出 JSON。
4. `lastCaptureAt` 只在成功 ingest 之后更新（`AccessibilitySnapshotIngest` 的 `onCaptured` → `CaptureHealthStore.recordCapture`）。看错窗口不会更新它。
5. `AutoLedgerHealth.resolve`：总开关和无障碍都开着时，`silenceSince = lastCaptureAt ?: watchStartedAt`。`now - silenceSince >= 24h` 就是疑似失效。`lastCaptureAt == null` 时，静默从开始监听算。

付款页没有进待确认，是第 1–2 步采错窗口，不是健康度算错。健康度变成疑似失效，是第 4–5 步在「从未成功捕获」上跨过了 24 小时。

## 这次补上的可导出记录

进入疑似失效时写一条 `capture_logs`：`lastCaptureAt`（或 null）、`watchStartedAt`、这期间有没有被拒绝的采样。不改 24 小时阈值，不更新 `lastCaptureAt`，不新建 Pending。同一次静默只写一条。
