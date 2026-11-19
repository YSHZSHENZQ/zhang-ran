# FX-1 取证

当前功能提交 `6d5f42c612db0768773fbf029349b240c9e08dec`。`git-sha.txt` 是这个提交。文档提交 `6868949cfe148a7247f0fce974e10ec4371e17e8` 只改 `docs/03`。

AC-1 到 AC-5、AC-7 的 JSON 和截图仍是功能提交 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8` 那次模拟器取证，这次没有替换。那些导出里的 `gitSha` 仍是 `c774287`。本环境模拟器停在 adb offline，没有用新包生成导出 JSON。AC-8、AC-9 是 JVM 单测摘录。截图时区 Asia/Shanghai。

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
| `ac3-after-wrong-pending.png` | 看错窗口回放后的待确认页：只有两笔 ¥25.10（余额宝），没有新增待确认。出处见下方说明 |
| `ac3-wrong-window-logs.json` | 导出含 WRONG_WINDOW 与 `fx-1-f-wrong-window`，不含页面正文 |
| `ac3-regression.json` | m6-3-f-wx 1000/杭州深度求索，m6-3-f-ali 12999/富富，m6-2-wechat-pay-success 3850/星巴克咖啡。全部 PENDING，没有 CONFIRMED / DUPLICATE |

`ac3-after-wrong-pending.png` 的出处：2026-11-18 11:27–11:30（HKT）在模拟器 qj1（TCG）上重跑 `Fx1ProofTest`，被测应用是功能提交 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8` 的 debug 构建（sha256 与交付的 APK 相同，9258e3bc…）。为了多截这一张，我在本地给测试加了 3 行（看错窗口回放后再 `openPending()` 并 `shot("ac3-after-wrong-pending")`），这 3 行没有提交，仓库里的测试文件没变。这次重跑里 `ac3-before-wrong` 和 `ac3-after-wrong` 的 `pendingCount` 都是 2，行数都是 2，测试通过（OK (1 test)）。其余的 JSON 和 PNG 仍是原来那次取证的，这次没有替换。截图本身看不出回放发生过，它只证明回放之后待确认还是这两笔；回放前后条数不变看 `ac3-before-wrong.json` / `ac3-after-wrong.json`。

## AC-4 疑似失效

| 文件 | 内容 |
|---|---|
| `suspected-stale-root-cause.md` | 代码路径和 11-13 时间线（翻转发生在 11-13 09:00 到 14:45 之间） |
| `ac4-broadcast.txt` | 把上次捕获和开始监听一起拨回 25 小时 |
| `ac4-stale-home.png` | 首页 `AUTO LEDGER · 疑似失效` |
| `ac4-stale-auto-ledger.png` | 自动记账页「总状态 疑似失效」，一条翻转日志 |
| `ac4-ledger.json` | Pending 仍是 5，没有新交易，没有 CONFIRMED / DUPLICATE |
| `ac4-export.json` | 一条 `SUSPECTED_STALE`，含 lastCaptureAt、watchStartedAt、rejectedSamples=true |

## AC-5

`git-sha.txt` = `6d5f42c612db0768773fbf029349b240c9e08dec`。该提交上 `assembleDebug` 打出的 debug 包，`BuildConfig.GIT_SHA` 与此相同。没有装到模拟器，也没有从设备拉出新的导出 JSON。

## AC-8 停留不再看节点文本

| 文件 | 内容 |
|---|---|
| `ac8-stay.md` | 节点从「支付金额」换成「回首页」、相隔 1.3 秒仍是 1 条；不同单号、16 秒、不同窗口仍是 2 条。真机 ¥22.50 / ¥12.90 回放结果由 Eng 本机提供 |

## AC-9 会话列表拒绝证据

| 文件 | 内容 |
|---|---|
| `ac9-chat-list.md` | 合成会话列表待确认 0 条，每次 `NOT_PAYMENT_PAGE`。单测导出含结构字段，不含正文、金额数字、人名、contentDescription。60 秒内同一原因只留一条 |

## AC-7

本切片没有自动确认路径。上表账本里无障碍行的 state 都是 PENDING，confirmedByUser 都是 false。
