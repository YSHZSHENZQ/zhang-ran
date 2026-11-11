# M6-4 证明

功能提交（debug APK 的 `BuildConfig.GIT_SHA`）是 `0910af02f4a924140e6709e4befa239b08191cee`。`git-sha.txt` 与本目录每份 JSON 的 `gitSha` 都是这一串。APK 从这次提交打出，没有用后来的证明提交重打。

模拟器：`m64_phone`，API 36，1080×2400，时区 `Asia/Shanghai`，时钟 2026-11-11 16:50–16:52 CST。截图全部是 `adb exec-out screencap -p`，不是 Compose Preview。键盘没有挡住要读的字。

回放只用已有 fixture，没有新的真机付款：

| id | 内容 |
|---|---|
| `m6-3-f-wx` | 微信，2026-11-11 13:05，¥10.00（`amountMinor=1000`），商户杭州深度求索，包名 `com.tencent.mm` |
| `m6-3-f-ali` | 支付宝，2026-11-11 10:09，¥129.99（`amountMinor=12999`），商户富富，包名 `com.eg.android.AlipayGphone` |

忽略路径是 M3a 的「忽略」：对待确认里的 `m6-3-f-ali` 点忽略，再从捕获日志恢复。没有第三份 fixture 文件。

回放之后两条都是 `PENDING` / `ACCESSIBILITY`，本月支出 0。后面出现的唯一一笔 `CONFIRMED` 是用户在「记一笔」里手动保存的 ¥1.00（`source=MANUAL`）。没有任何路径自动写成 `CONFIRMED` 或 `DUPLICATE`。

## AC-7 把上次捕获拨到 24 小时前

只有 debug 包注册 `DebugCaptureHealthReceiver`。它把健康度里的「上次捕获时间」写成 `现在 - ageMillis`，不改捕获日志，不新建 Pending。`ageMillis` 省略时是 24 小时。总开关和无障碍都已经打开时，拨够 24 小时，首页和自动记账页显示「疑似失效」；关掉总开关后回到「不可用」。

本次证明用的是 90000000 毫秒（25 小时）：

```
adb shell am broadcast -a com.zhangran.app.debug.BACKDATE_LAST_CAPTURE --el ageMillis 90000000 -n com.zhangran.app/.debug.DebugCaptureHealthReceiver
```

`ac7-broadcast.txt` 是仪器测试里同一次广播的输出，含 `result=0`。

## 文件

| 文件 | 验收 |
|---|---|
| `git-sha.txt` | 功能提交 SHA，等于 APK `BuildConfig.GIT_SHA` |
| `ac1-available-home.png` | AC-1。首页副标题 `AUTO LEDGER · 可用`。待确认 2。杭州深度求索 -10.00 13:05，富富 -129.99 10:09。本月支出 ¥0.00。没有「未接入」 |
| `ac1-available-mine.png` | AC-1。总开关只在「我的」，当前开启。权限状态「无障碍已授权」。自动记账状态「可用」 |
| `ac1-available-auto-ledger.png` | AC-1。总状态可用，微信监听可用，支付宝监听可用，无障碍已授权。有「查看日志」「待确认」。没有第二个总开关 |
| `ac1-unavailable.json` | AC-1。关掉总开关后 `pendingCount` 仍是 2，两笔仍是 `PENDING` |
| `ac1-unavailable-home.png` | AC-1。首页 `AUTO LEDGER · 不可用`。待确认仍是 2 |
| `ac1-unavailable-mine.png` | AC-1。总开关关闭。状态「不可用」 |
| `ac1-unavailable-auto-ledger.png` | AC-1。自动记账页总状态不可用。没有总开关 |
| `ac1-manual-home.png` | AC-1。开关关闭后手动记一笔 ¥1.00，首页本月支出 ¥1.00，餐饮 · 手动。两条回放仍在，待确认仍是 2 |
| `ac1-manual.json` | AC-1 / AC-5。清空前的账本。回放两笔仍 `PENDING`，另有一笔 `source=MANUAL`、`state=CONFIRMED`、`amountMinor=100`。`m6-3-f-wx` 的 `fieldEdits` 含 `note`，备注 `m6-4-field-edit` |
| `ac2-onboard-before.png` | AC-2。冷启动落在权限引导。只有无障碍（未授权）和电池优化（可跳过）。没有 LSPosed、OCR、短信、悬浮窗 |
| `ac2-app-settings.png` | AC-2。「前往授权」打开的是本应用「账然」的系统应用信息页 |
| `ac2-onboard-refreshed.png` | AC-2。从系统设置返回后，无障碍变为已授权 |
| `ac2-battery-skipped.png` | AC-2。电池优化点了「跳过」，引导仍可完成 |
| `ac2-cold-start-home.png` | AC-2。点「完成」之后 `am force-stop` 再 `am start`。第二次冷启动在首页，不在权限引导。状态「不可用」（当时总开关已关） |
| `ac3-logs.json` | AC-3。两条日志：`2026-11-11 13:05` / `m6-3-f-wx` / `com.tencent.mm`，`2026-11-11 10:09` / `m6-3-f-ali` / `com.eg.android.AlipayGphone`。来源 `ACCESSIBILITY`，状态 `PENDING`，原因 `ACCOUNT_UNCERTAIN,CATEGORY_UNCERTAIN` |
| `ac3-logs-list.png` | AC-3。页面读到「11月11日 13:05」「11月11日 10:09」，来源「无障碍 ACCESSIBILITY」，包名、`PENDING`、原因。筛选里没有 `RULE_TEST`（规则页不在本刀） |
| `ac3-filter-pending.png` | AC-3。筛选 `PENDING` 后两条都还在 |
| `ac3-filter-empty.png` | AC-3。筛选 `CONFIRMED` 后空态「没有该状态的日志」 |
| `zhangran-logs-20261111-165121.json` | AC-4。系统分享出去的文件本身。文件名 `zhangran-logs-<yyyyMMdd-HHmmss>.json`。内含功能提交 `gitSha` 和上面两条日志 |
| `ac4-share-sheet.png` | AC-4。系统分享面板，文件名 `zhangran-logs-20261111-165121.json` |
| `ac4-sensitive-scan.txt` | AC-4。仪器测试对导出文件的字符串字段扫描，结果 `no plaintext sensitive fields` |
| `ac4-host-scan.txt` | AC-4。同一文件在主机上再扫一遍字符串字段（手机号、18 位身份证、16–19 位卡号、12 位以上数字串），无命中。整文件里仅有的两段 12 位以上数字是 `capturedAt` 时间戳 `1794373522722` 与 `1794362941004`，不是账号或单号 |
| `ac5-before-clear.json` | AC-5。改备注之后、忽略和手动入账之前。`m6-3-f-wx` 的 `fieldEdits` 为 `["note"]`，`note` 为 `m6-4-field-edit`，状态仍是 `PENDING` |
| `ac5-confirm.png` | AC-5。二次确认文案「只清日志和证据，不影响已记的账」 |
| `ac5-empty.png` | AC-5。清空后空态「还没有捕获日志」 |
| `ac5-logs-after.json` | AC-5。清空后导出结构里 `entries` 为空 |
| `ac5-after-clear.json` | AC-5。与 `ac1-manual.json` 比：三笔交易的状态、金额、备注、`fieldEdits`、`confirmedByUser` 不变。`evidenceCount` 与 `captureLogCount` 变为 0。`fixtureId` 变空，因为它写在已删除的捕获日志上，不在交易上 |
| `ac6-ignored.json` | AC-6。`m6-3-f-ali` 变为 `IGNORED`，`pendingCount` 从 2 降到 1。本月支出仍是 0 |
| `ac6-log-ignored.png` | AC-6。日志筛选 `IGNORED`，读到 11月11日 10:09、支付宝包名、`IGNORED`，以及「恢复」 |
| `ac6-restored.json` | AC-6。恢复后 `m6-3-f-ali` 回到 `PENDING`，`pendingCount` 回到 2。本月支出、收入仍是 0 |
| `ac6-pending-restored.png` | AC-6。待确认列表重新出现 `m6-3-f-ali` 与「富富」 |
| `ac6-home-stats.png` | AC-6。恢复后首页本月支出仍是 ¥0.00，待确认 2 |
| `ac7-broadcast.txt` | AC-7。debug 广播 `result=0` |
| `ac7-stale.json` | AC-7。拨到 25 小时前之后 `pendingCount` 仍是 2，没有新的 Pending |
| `ac7-stale-home.png` | AC-7。首页 `AUTO LEDGER · 疑似失效`。待确认仍是 2，本月支出仍是 ¥0.00 |
| `ac7-stale-auto-ledger.png` | AC-7。总状态「疑似失效」，文案「已超过 24 小时没有捕获」。微信、支付宝监听仍可用 |
| `ac7-switch-off-home.png` | AC-7。关掉总开关后首页回到「不可用」，「疑似失效」消失 |
| `ac8-replay.json` | AC-8。回放刚结束、手动入账之前。两笔都是 `PENDING`，没有 `CONFIRMED`，没有 `DUPLICATE`。`pendingCount=2`，本月支出 0。`gitSha` 为 `0910af02f4a924140e6709e4befa239b08191cee` |
