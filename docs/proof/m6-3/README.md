# M6-3 证明

功能提交（解析收窄，debug APK 的 `BuildConfig.GIT_SHA`）是 `3467840a91d61793a369d3000f99151ba632192e`，与 `git-sha.txt`、`ac4` / `ac5` / `ac6` JSON 里的 `gitSha` 相同。APK 从这次提交打出。后面的取证测试提交和本目录证明提交没有重打 APK。用证明提交重新 `assembleDebug` 会改掉 `GIT_SHA`，和 `git-sha.txt`、回放 JSON 对不上。QA 以 APK 里的 `BuildConfig.GIT_SHA`、`git-sha.txt`、回放 JSON 的 `gitSha` 三者一致为准。

AC-1 当时装到模拟器上的包是更早的 `0bcfc84b3c1747051ccba1e7da3ce84562118b11`（Owner 真机上的那一版）。那一版的开关和连通证明仍然有效，但解析是旧的：真机上已经进待确认的三笔标题不对，不会被新包改写。

2026-11-11 Owner 改定：AC-2 / AC-3 改为回放 `m6-3-f-wx` 与 `m6-3-f-ali`，不再要求新的真机付款。方向用现成的「改完再记」页，没有改产品，功能 SHA 仍是 `3467840…`。没有点「保存并入账」，两笔保持待确认。

模拟器：Android 14（API 34，1080×2400）。截图来自 `screencap` 帧缓冲，不是 Compose Preview。回放当时系统界面弹过「System UI isn't responding」，测试进程里落下的两张图被这层挡住。提交的 `ac6-home-*.png` 是同一台模拟器、同一套账本状态下补拍的：先拍有三笔待确认的首页，再清空数据库拍空首页。JSON 仍是仪器测试当场写的，没有手改。

| 文件 | 验收 |
|---|---|
| `git-sha.txt` | 所验 debug APK 的 `BuildConfig.GIT_SHA`（`3467840…`） |
| `ac1-accessibility-settings.png` | AC-1。系统设置里「账然」无障碍已打开。对应旧包 `0bcfc84` |
| `ac1-connectivity-log.png` | AC-1。同一次绑定的 logcat 与 `capture_logs`。`capturedAt=1794324648399` 与 `1794324651226`；`transactionId=null`；`transactions` 行数为 0。`detail` 为 `0bcfc84…` |
| `ac2-f-wx-pending-list.png` | AC-2 / AC-3。同一屏「待确认记录」：`m6-3-f-wx` 杭州深度求索 ¥10.00，`m6-3-f-ali` 富富 ¥129.99。没有键盘 |
| `ac2-f-wx-edit-direction.png` | AC-2。`m6-3-f-wx` 的「改完再记」：商户杭州深度求索、金额 10.00、类型「支出」（选中）。未保存 |
| `ac3-f-ali-edit-direction.png` | AC-3。`m6-3-f-ali` 的「改完再记」：商户富富、金额 129.99、类型「支出」（选中）。未保存 |
| `ac2-ac3-replay.json` | AC-2 / AC-3。与上图同一回放：两笔 `PENDING` / `EXPENSE` / `ACCESSIBILITY`，`booked=false`，正式收支 0，`gitSha` 为 `3467840…` |
| `ac4-f-wx.json` | AC-4。fixture `m6-3-f-wx`。`amountMinor=1000`，商户「杭州深度求索」，`EXPENSE`，`PENDING`，`ACCESSIBILITY`，`confirmedByUser=false`。正式收支 0 |
| `ac4-f-ali.json` | AC-4。fixture `m6-3-f-ali`。`amountMinor=12999`，商户「富富」，`EXPENSE`，`PENDING`，`ACCESSIBILITY`。正式收支 0 |
| `ac5-m6-2.json` | AC-5。fixture `m6-2-wechat-pay-success`。`amountMinor=3850`，商户「星巴克咖啡」，`EXPENSE`，`PENDING`，`ACCESSIBILITY` |
| `ac6-before.json` | AC-6。回放前：`pendingCount=0`，`monthExpenseMinor=0`，`monthIncomeMinor=0`，`rows` 空 |
| `ac6-after.json` | AC-6。三笔都在 `rows` 里，状态全是 `PENDING`，没有 `CONFIRMED`。`pendingCount=3`，本月支出/收入仍是 0 |
| `ac6-home-before.png` | AC-6。空首页：「还没有记录」，待确认角标没有数字。空列表不画余额卡片，所以图上没有「本月支出」；数字以 `ac6-before.json` 为准 |
| `ac6-home-after.png` | AC-6。「本月支出 ¥0.00」，INCOME `+0.00`，BALANCE `0.00`，角标 3。今天下列「杭州深度求索 -10.00」「富富 -129.99」，其下「星巴克咖啡 -38.50」。没有键盘 |
| `owner-2026-11-11/` | 旧包 `0bcfc84` 的真机上下文（小米 14）。不是这次回放证明 |

## AC-4 fixture

| id | 页面 | 期望 |
|---|---|---|
| `m6-3-f-wx` | 微信「支付完成」。节点来自 Owner 证据里能读到的文本：支付完成、¥10.00、支付总额、支付成功、订单状态、杭州深度求索。订单尾号没有写进 fixture | `1000` / 杭州深度求索 / `EXPENSE` / `PENDING` / `ACCESSIBILITY` |
| `m6-3-f-ali` | 支付宝结果页。节点：回首页、完成、付款方式、¥129.99、富富、说明「支付成功 ¥129.99」。卡号用占位「中国银行储蓄卡***」 | `12999` / 富富 / `EXPENSE` / `PENDING` / `ACCESSIBILITY` |

商户只从结果页上剩下的名字取。导航和按钮（更多信息、回首页、完成、我的、返回、付款等）不能当商户。找不到就留空，界面显示「未知」，不拿按钮凑。两个方向的结果用语同时出现时不解析，不把未知方向收成支出。

旧包已经写入的待确认行不会被新包改掉。

## AC-2 / AC-3（2026-11-11 改定：回放，不再新付款）

在 `3467840` 这只 debug APK 上只回放 `m6-3-f-wx` 与 `m6-3-f-ali`。

`ac2-f-wx-pending-list.png` 同一屏能读到两行：杭州深度求索 ¥10.00，富富 ¥129.99。

方向没有改列表行。两行各自打开「改完再记」（页面标题「编辑后入账」），金额、商户和类型「支出」在同一屏，键盘没有挡住。「支出」是选中项，「收入」在旁边未选。然后返回，没有点「保存并入账」。

`ac2-ac3-replay.json` 与这三张图是同一次回放：`amountMinor` 1000 / 12999，商户杭州深度求索 / 富富，`type=EXPENSE`，`state=PENDING`，`source=ACCESSIBILITY`，`confirmedByUser=false`，`booked=false`，本月支出和收入为 0。

## AC-6 正式账

首页「本月支出 / 本月收入」只计本月已确认。这三笔回放都是待确认，`confirmedByUser=false`。回放前后 `monthExpenseMinor` 与 `monthIncomeMinor` 都是 0。角标等于待确认条数（0，然后 3）。

## 首页「付款 -11.58」和标题「我的」

这是误解析，不是第三笔要保留的付款。Owner 没有 ¥11.58 的支付结果页；10:08 紧挨着支付宝 ¥129.99。旧解析只要白名单窗口里出现「支付成功」这类词，就取页面上第一笔金额，再用第一个剩下来的词当商户，所以按钮「付款」「我的」进了标题。

新规则要求结果页同时具备：整节点就是结果用语（或「结果用语 + 一笔金额」），以及锚点（节点里带着这笔金额，或页面上有「完成 / 确定 / 支付总额 / 付款方式」之一）。账单详情标记（全部账单、商户全称、交易单号等）整页拒绝。「交易成功」不是支出结果用语。多个不同金额又没有绑在结果节点上时不解析。

负例（都不产生候选）：

- `chromeAmountNotResult`：我的、付款、支付成功、¥11.58、回首页。对应「付款 -11.58」和标题「我的」。回放断言 `IGNORED_INVALID`，`transactionId=null`。
- `marketingSentence`：长句「恭喜支付成功，再付¥11.58可得红包」。
- `wechatPayList` / `wechatPayListWithStatus`：列表上两笔金额。
- `wechatBillDetail`：微信账单详情，即使写着支付成功和 ¥10.00。
- `alipayTradeSuccessBill`：支付宝账单「交易成功」和 -14.90。瑞幸那一笔的结果页没有随样本提供；待确认标题「我的」是导航词，不是 luckincoffee。

已经进旧包账本的那些行不会被删。疑似重复页（M3b）和捕获日志页（M6-4）这次不做。

## Owner 截图

目录 `owner-2026-11-11/`。入库前又对这 11 张做了 OCR：没有邮箱，没有完整银行卡号、手机号、身份证号，没有订单号中段。`05-pending-list.jpg` 上的 UUID 是账然自己的待确认记录 id，不是支付单号。`01` / `02` / `03` 里的 13 位数字是采样时间 `capturedAt`。

未入库：`01-wx-pending-detail-evidence.jpg`。证据 JSON 里能读到四位数字 `7769`，位置在「支付总额」和「支付成功」之间，像是订单尾号。其余 10 张入库。

| 文件 | 内容 |
|---|---|
| `13-wx-pay-success-1000.jpg` | 微信「支付成功」，¥10.00，杭州深度求索 |
| `12-wx-pay-complete-1000.jpg` | 微信「支付完成」。订单尾号被挡住 |
| `10-wx-bill-shendu-1000.jpg` | 微信账单详情。单号、邮箱被挡住 |
| `11-wx-pay-list.jpg` | 微信支付列表。汉堡王那一行不是本刀样本，卡号末四位被挡住 |
| `03-ali-pending-header-12999.jpg` | 旧包把支付宝 ¥129.99 的标题记成「回首页」 |
| `02-ali-pending-detail-evidence.jpg` | 该笔证据 JSON |
| `08-ali-bill-fufu-12999.jpg` | 支付宝账单，短名「富富」 |
| `09-ali-bill-luckin-1490.jpg` | 额外样本瑞幸 ¥14.90，不代替最小集 |
| `05-pending-list.jpg` | 旧包待确认列表 |
| `06-home.jpg` | 旧包首页：本月支出 ¥0.00，角标 15，含「付款 -11.58」 |

## 怎么把真机结果页再导出一份

debug 包仍把白名单窗口写到应用私有目录。release 不写。没有捕获日志产品页。解析不认的页面也会写。

只采样 `com.tencent.mm`、`com.eg.android.AlipayGphone`。

1. 安装与 `git-sha.txt` 一致的 debug APK，打开「账然」无障碍。logcat 出现 `status=SERVICE_CONNECTED` 和 `status=BUILD`，`detail` 应等于 `git-sha.txt`。
2. 打开微信或支付宝的支付结果页，停大约一秒。不要用转账、红包、退款、收款页做本刀样本。
3. 拉最近一页（`context.filesDir`，不是产品页）：

```text
adb logcat -d -s ZhangRanAccessibility:D
adb shell "run-as com.zhangran.app ls files/m6-3-snapshots"
adb exec-out run-as com.zhangran.app cat files/m6-3-snapshots/latest.txt
adb exec-out run-as com.zhangran.app cat files/m6-3-snapshots/latest.json > latest.json
```

`latest.txt` 和 `status=SNAPSHOT_EXPORTED` 没有页面正文。`latest.json` 在编码前按字段脱敏；`capturedAt` 保持原数字。同一目录最多 20 份，外加 `latest.json` 和 `latest.txt`。

导出本身不写交易。解析通过的页面仍只进待确认。
