# UX-1 取证

功能提交 `e519fd065726f2821555b247c9db5c8949ff2914`。`git-sha.txt` 是这一行，没有换行。debug 包的 `BuildConfig.GIT_SHA` 也是这一行。文档提交 `a7a31e80443598a27c3c4c5d675e7e204fc4282a` 只改 `docs/06` §4.1。

模拟器：AVD `ux1`，系统映像 `android-36;google_apis;x86_64`，`-timezone Asia/Shanghai`。开机后 `persist.sys.timezone` 为 `Asia/Shanghai`，`date` 为 `Tue Nov 24 14:16:59 CST 2026`。截图全部来自 `adb exec-out screencap -p`，不是 Compose Preview。

数据只用既有脱敏 fixture：`fx-1-f-wx-success`（商户「示例早餐店」，¥8.00，已确认）和 `fx-1-f-ali-yuebao`（无商户，¥25.10，仍待确认）。没有真实商户，也没有真实账单。

| 文件 | AC | 内容 |
|---|---|---|
| `ac1-home.png` | AC-1 | 同一屏：待确认行有「待确认」标签，金额是警告色；已确认行没有该标签，金额是入账红字。本月支出 ¥8.00，收入 +0.00，结余 -¥8.00，角标 1 |
| `ac2-pending.png` | AC-2 | 点击待确认行（余额宝 fixture，普通 PENDING）后的该笔详情：确认 / 改完再记 / 忽略 |
| `ac2-confirmed.png` | AC-2 | 点击已确认行「示例早餐店」后的交易详情。状态「已确认」，金额 -8.00，时间 2026年11月24日 14:16。详情正文比标题晚一拍，这张是正文出现后再用 `adb exec-out screencap -p` 截的，仍是同一包、同一笔账 |
| `ac3-home.png` | AC-3 | 与 AC-1 同一账本状态下的首页，用来和 JSON 对数字 |
| `ac3-ledger.json` | AC-3 | 同一状态下的账本 JSON。`pendingCount` 1，`monthExpenseMinor` 800，`monthIncomeMinor` 0，`monthBalanceMinor` -800。待确认那笔 `amountMinor` 2510，没有进本月支出 |
| `git-sha.txt` | AC-4 | 功能提交 SHA，与 JSON 里的 `gitSha` 相同 |

没有另拍 `POSSIBLE_DUPLICATE` 详情。普通待确认和已确认两条点击路径都有截图。疑似重复仍走原来的「查看重复 / 忽略」，这条由单测和待确认页测试覆盖，没有改那套操作。
