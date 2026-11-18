# FX-1 取证

功能提交 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8`。`git-sha.txt` 与该提交的 `BuildConfig.GIT_SHA` 相同。截图来自模拟器 `screencap`，时区 Asia/Shanghai。

同一拒绝原因的去重窗口是 **60 秒**（`RejectionRateLimiter.WINDOW_MILLIS`）。窗口按原因码计算，不按包名。`now - previous < 60_000` 时丢掉后到的一条；刚好满 60 秒可以再记一条。

## AC-1 余额宝金额、商户、看错窗口

| 文件 | 内容 |
|---|---|
| `ac1-yuebao.json` | `fx-1-f-ali-yuebao`：amountMinor 2510，EXPENSE，merchantRaw null |
| `ac1-yuebao-split.json` | `fx-1-f-ali-yuebao-split`：¥ 与 25.10 拆开，仍是 2510，商户 null |
| `ac1-wrong-window.json` | 事件包名支付宝、根包名 `com.zhangran.app`，不转发、不入账 |

## AC-2 拒绝原因可导出，且不改上次捕获

| 文件 | 内容 |
|---|---|
| `ac2-export-before.json` | 六种原因各一条：WRONG_WINDOW、NO_MATCHING_WINDOW、NOT_PAYMENT_PAGE、WINDOW_NULL、NODE_EMPTY、SWITCH_OFF |
| `ac2-export-after.json` | 与 before 相同。1 秒后的同原因没有再写 |
| `ac2-health-before.json` / `ac2-health.json` | `lastCaptureAt` 仍为 null；`rejectedSamplesSinceCapture` 变为 true |
| `ac2-ledger-before.json` / `ac2-ledger-after.json` | Pending 仍是 0 |
| `ac2-logs.png` | 日志页筛选 IGNORED_INVALID，六种原因都在 |

## AC-3 回放

| 文件 | 内容 |
|---|---|
| `ac3-yuebao-ledger.json` | 两笔待确认，2510，EXPENSE，商户 null，ACCESSIBILITY，confirmedByUser false |
| `ac3-yuebao-pending.png` | 待确认页 ¥25.10，商户显示「未知」 |
| `ac3-before-wrong.json` / `ac3-after-wrong.json` | 看错窗口回放前后 Pending 条数不变 |
| `ac3-wrong-window-logs.json` | 导出含 WRONG_WINDOW 与 `fx-1-f-wrong-window`，不含页面正文 |
| `ac3-regression.json` | m6-3-f-wx 1000/杭州深度求索，m6-3-f-ali 12999/富富，m6-2-wechat-pay-success 3850/星巴克咖啡。全部 PENDING，没有 CONFIRMED / DUPLICATE |

## AC-4 疑似失效

| 文件 | 内容 |
|---|---|
| `suspected-stale-root-cause.md` | 代码路径和 11-13 时间线 |
| `ac4-broadcast.txt` | 把上次捕获和开始监听一起拨回 25 小时 |
| `ac4-stale-home.png` | 首页 `AUTO LEDGER · 疑似失效` |
| `ac4-stale-auto-ledger.png` | 自动记账页「总状态 疑似失效」，一条翻转日志 |
| `ac4-ledger.json` | Pending 仍是 5，没有新交易，没有 CONFIRMED / DUPLICATE |
| `ac4-export.json` | 一条 `SUSPECTED_STALE`，含 lastCaptureAt、watchStartedAt、rejectedSamples=true |

## AC-5

`git-sha.txt` = `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8`。该提交上 `assembleDebug` 已打出 debug APK。

## AC-7

本切片没有自动确认路径。上表账本里无障碍行的 state 都是 PENDING，confirmedByUser 都是 false。
