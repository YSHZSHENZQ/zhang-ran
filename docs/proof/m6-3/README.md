# M6-3 AC-1 证明

本目录只覆盖 AC-1。AC-2 到 AC-7 还没有真机付款样本，不在这里，也不把任何页面冻成 F-WX / F-ALI。

模拟器：Android 14（API 34，`m63_api34`，1080×2400）。截图来自 `adb exec-out screencap` 和同一台模拟器的 `adb logcat` / `sqlite3`，不是 Compose Preview。包 `com.zhangran.app` debug。

所验 APK 的 `BuildConfig.GIT_SHA` 见 `git-sha.txt`，与连通日志里的 `detail` 相同：`0bcfc84b3c1747051ccba1e7da3ce84562118b11`。这是功能提交（debug 快照导出）的 SHA。本目录是之后单独提交的证明，没有为它重打 APK。用证明提交重新 `assembleDebug` 会改掉 `GIT_SHA`，和 `git-sha.txt`、连通截图对不上。QA 以 APK 里的 `BuildConfig.GIT_SHA`、`git-sha.txt`、连通截图里的 `detail` 三者一致为准。

| 文件 | 验收 |
|---|---|
| `ac1-accessibility-settings.png` | AC-1。系统设置里「账然」无障碍页，「Use 账然」开关已打开。说明全文可读，没有键盘 |
| `ac1-connectivity-log.png` | AC-1。同一次绑定的 logcat 与 `capture_logs`。`capturedAt=1794324648399` 与 `1794324651226` 两边一致；`transactionId=null`；`transactions` 行数为 0。`detail` 为上面的 GIT_SHA |
| `git-sha.txt` | AC-1。所验 APK 的 `BuildConfig.GIT_SHA` |

连通行的 `reason=SERVICE_CONNECTED`，`captureSource=ACCESSIBILITY`。这次绑定没有产生交易。

## 怎么把真机支付结果页冻成 fixture

debug 包会把白名单窗口的节点文本写到应用私有目录。release 不写。没有新的捕获日志产品页。解析认不出来的页面也会写：当前规则对不上的真机结果页，仍然要能被收下来。

只采样这两个包：`com.tencent.mm`、`com.eg.android.AlipayGphone`。其它包不读、不导出。

1. 安装与 `git-sha.txt` 一致的 debug APK，在系统设置里打开「账然」无障碍。logcat 出现 `status=SERVICE_CONNECTED` 和 `status=BUILD`，`detail` 应等于 `git-sha.txt`。
2. 打开微信或支付宝的支付结果页，停大约一秒（采样延迟 300ms）。不要用转账、红包、退款、收款页做本刀样本。
3. 拉最近一页。目录在 `files/m6-3-snapshots/`（`context.filesDir`，不是产品页）：

```text
adb logcat -d -s ZhangRanAccessibility:D
adb shell "run-as com.zhangran.app ls files/m6-3-snapshots"
adb exec-out run-as com.zhangran.app cat files/m6-3-snapshots/latest.txt
adb exec-out run-as com.zhangran.app cat files/m6-3-snapshots/latest.json > latest.json
```

`latest.txt` 只有文件名、`capturedAt`、包名、`windowId`、节点数、是否截断，没有页面正文。`status=SNAPSHOT_EXPORTED` 的 logcat 行同样只有文件名和时间戳。

`latest.json` 是脱敏后的节点投影，字段为 `packageName`、`windowId`、`capturedAt`、`truncated`、`nodes[]` 的 `text` / `contentDescription` / `viewId` / `depth`。没有 `className`，没有截图。文本按字段脱敏（手机号、银行卡、身份证、不少于 12 位的单号、收款方/付款方一类姓名），`capturedAt` 保持原数字，否则 JSON 会坏，无法回放。

同一目录最多保留 20 个按时间命名的快照，外加 `latest.json` 和 `latest.txt`。文件名是 `{capturedAt}-{包名}-w{windowId}.json`。

4. 对照 `latest.json` 的 `nodes` 写成 `AccessibilityPageSnapshot`（或带 expected 的 fixture JSON）。金额仍用 `Long amountMinor`，经 `Money`，不要用 Double/Float。入库前再看一遍：自动脱敏没有盖住的聊天内容不要带进仓库。这一步留到有真机样本之后再做，本目录不收录 F-WX / F-ALI。

导出本身不写交易。解析通过的页面仍只进待确认（`CaptureSource=ACCESSIBILITY`，`confirmedByUser=false`），不会因为这份文件变成已确认。

本机这次没有安装微信或支付宝，所以 `files/m6-3-snapshots` 还不存在。这是预期，不是导出失败。
