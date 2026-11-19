# FX-1 取证

功能提交 `6d5f42c612db0768773fbf029349b240c9e08dec`。`git-sha.txt` 与本目录里带 `gitSha` 的 JSON 都是这个提交。文档提交 `6868949cfe148a7247f0fce974e10ec4371e17e8` 只改 `docs/03`。

2026-11-19 本机 TCG 模拟器（AVD qj1，API 36）用该提交的 debug 包跑了 `Fx1ProofTest`，结果 OK (1 test)。16 个 JSON、`ac4-broadcast.txt`、`git-sha.txt` 和 4 张重拍 PNG 来自这次运行。仓库里没有缺新文件的旧 JSON，同名 JSON 都已换成这次的。

没有 `gitSha` 字段的 JSON：`ac1-yuebao.json`、`ac1-yuebao-split.json`、`ac1-wrong-window.json`、`ac2-health-before.json`、`ac2-health.json`。其余 JSON 的 `gitSha` 都是 `6d5f42c612db0768773fbf029349b240c9e08dec`。

`ac2-logs.png`、`ac3-yuebao-pending.png`、`ac4-stale-home.png`、`ac4-stale-auto-ledger.png` 是这次在 `6d5f42c6` 包上重拍的。`fx1-6d5f42c6-launch.png` 是同一次安装启动冒烟的截图。`ac3-after-wrong-pending.png` 仍是功能提交 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8` 的图，这次包里没有同名新文件，所以保留。`suspected-stale-root-cause.md` 也不是这次导出，仍是原先那份说明。

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
| `ac2-health-before.json` / `ac2-health.json` | 两份都是 `lastCaptureAt` null、`watchStartedAt` null、`rejectedSamplesSinceCapture` true。这次的 before 已经是 true |
| `ac2-ledger-before.json` / `ac2-ledger-after.json` | Pending 仍是 0 |
| `ac2-logs.png` | 日志页筛选 IGNORED_INVALID，六种原因都在。`6d5f42c6` 包上重拍 |

这六条拒绝都带 `windowId`、`rootClass`、`viewIds`、`nodeCount`、`hasAmountStyle`、`hasPaymentKeyword`，值都是 null。测试记录它们时没有页面快照。

## AC-3 回放

| 文件 | 内容 |
|---|---|
| `ac3-yuebao-ledger.json` | 两笔待确认，2510，EXPENSE，商户 null，ACCESSIBILITY，confirmedByUser false |
| `ac3-yuebao-pending.png` | 待确认页 ¥25.10，商户显示「未知」。`6d5f42c6` 包上重拍 |
| `ac3-before-wrong.json` / `ac3-after-wrong.json` | 看错窗口回放前后 Pending 条数不变 |
| `ac3-after-wrong-pending.png` | 看错窗口回放后的待确认页。仍是 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8` 的截图，见下方说明 |
| `ac3-wrong-window-logs.json` | 导出含 WRONG_WINDOW 与 `fx-1-f-wrong-window`，不含页面正文 |
| `ac3-regression.json` | m6-3-f-wx 1000/杭州深度求索，m6-3-f-ali 12999/富富，m6-2-wechat-pay-success 3850/星巴克咖啡。全部 PENDING，没有 CONFIRMED / DUPLICATE |

`ac3-after-wrong-pending.png` 的出处：2026-11-18 11:27–11:30（HKT）在模拟器 qj1（TCG）上重跑 `Fx1ProofTest`，被测应用是功能提交 `c77428730c79f5e8ff3ee985e3d3d0cf9c8381e8` 的 debug 构建。为了多截这一张，本地给测试加了 3 行，这 3 行没有提交。这次 `6d5f42c6` 的包没有重拍这张图。

`ac3-wrong-window-logs.json` 里，`fx-1-f-wrong-window` 那条 WRONG_WINDOW 有值：`windowId` 38598，`viewIds` 空数组，`nodeCount` 20，`hasAmountStyle` false，`hasPaymentKeyword` false，`rootClass` 仍是 null。同文件里没有页面快照的 `fx-1-WRONG_WINDOW`、`NOT_PAYMENT_PAGE`、`NO_MATCHING_WINDOW`、`WINDOW_NULL`、`NODE_EMPTY`、`SWITCH_OFF` 这六个字段都是 null。

## AC-4 疑似失效

| 文件 | 内容 |
|---|---|
| `suspected-stale-root-cause.md` | 代码路径和 11-13 时间线。不是这次导出，仍是原先的说明 |
| `ac4-broadcast.txt` | 把上次捕获和开始监听一起拨回 25 小时 |
| `ac4-stale-home.png` | 首页 `AUTO LEDGER · 疑似失效`。`6d5f42c6` 包上重拍 |
| `ac4-stale-auto-ledger.png` | 自动记账页「总状态 疑似失效」，一条翻转日志。`6d5f42c6` 包上重拍 |
| `ac4-ledger.json` | Pending 仍是 5，没有新交易，没有 CONFIRMED / DUPLICATE |
| `ac4-export.json` | 一条 `SUSPECTED_STALE`，含 lastCaptureAt、watchStartedAt、rejectedSamples=true |

## AC-5

`git-sha.txt` = `6d5f42c612db0768773fbf029349b240c9e08dec`。与这次模拟器上 `Fx1ProofTest` 写出的 `BuildConfig.GIT_SHA` 相同。

## AC-7

本切片没有自动确认路径。上表账本里无障碍行的 state 都是 PENDING，confirmedByUser 都是 false。

## AC-8 停留不再看节点文本

| 文件 | 内容 |
|---|---|
| `ac8-stay.md` | JVM 合成页，以及 `replay-summary-6d5f42c6.txt` 里 ¥22.50 / ¥12.90 的回放结论 |
| `replay-summary-6d5f42c6.txt` | 真机回放摘要。只含时间、windowId、节点数和结论 |

## AC-9 会话列表拒绝证据

| 文件 | 内容 |
|---|---|
| `ac9-chat-list.md` | 合成会话列表的单测断言，以及 windowId 44349 的 31 份回放 |

仪器导出里每条拒绝日志都有那六个结构字段。有值的只有 `fx-1-f-wrong-window`。带正文证据的 `NOT_PAYMENT_PAGE` 由 JVM 单测证明，摘录在 `ac9-chat-list.md`。

## AC-10 安装启动冒烟

| 文件 | 内容 |
|---|---|
| `smoke-6d5f42c6.txt` | 卸载、安装、启动、崩溃检查的原始输出，未手改 |
| `fx1-6d5f42c6-launch.png` | 启动后的截图 |

APK sha256 `c347e80f6a9341fd6f330d7f2780afe0d3512aa12bb18e84d3dabded87b5ad9a`。证书 `be2c20b0be0ad24b20e8144f361e22d6c7be2631af288748df13b5e604ab6371`。dex 里只有 `6d5f42c6` 这一个提交号。本机 JVM 622 条，0 失败。app 与 androidTest 用同一把 debug key。
