# M3b 取证

功能提交 `02cf742c961b45c2fee34bf28a1c487813473c36`。
`git-sha.txt` 与 debug APK 的 `BuildConfig.GIT_SHA` 都是这个值。
本目录是取证提交，没有重新打包 APK。

截图来自模拟器帧缓冲 `screencap -p`（`adb exec-out screencap` 的同一条命令），不是 Compose Preview。
模拟器以 `-timezone Asia/Shanghai` 启动。状态栏是 12 小时制的上海时间，账本行是 24 小时制。

## 时区

微信样本 `m6-3-f-wx` 的 `transactionTime` 是 `1794373522722`，即 `2026-11-11T05:05:22.722Z`。
按设备当前时区 `Asia/Shanghai` 显示为 `2026-11-11 13:05:22`。
支付宝样本 `m6-3-f-ali` / `m3b-ac6-ali` 的 `transactionTime` 是 `1794362941004`，即 `2026-11-11T02:09:01.004Z`，显示为 `10:09`。

M6-3 截图把这两笔显示成 `05:05` / `02:09`，是因为当时模拟器默认时区是 UTC，展示用了 `ZoneId.systemDefault()` 以及入库时抄下来的 `transactionTimeZone`。存储的 epoch 本身是对的。这次展示改为每次读取设备当前时区，不改 epoch。

## Fixture

| id | 用途 |
|---|---|
| `m3b-ac1-wx` | 同一通知投递两次 |
| `m6-3-f-wx` | 同商户同金额，180 秒内的第二条 |
| `m3b-ac3a-wx` | 同商户同金额，晚 181 秒 |
| `m3b-ac3b-fufu-1000` | 同金额不同商户（富富 ¥10.00） |
| `m3b-ac3c-refund` | 同商户同金额的退款 |
| `m6-3-f-ali` | 富富 ¥129.99，对照行 |
| `m3b-ac6-ali` | 富富 ¥129.99 的第二条，两笔之后确认 |

## AC

| AC | 文件 | 看到的结果 |
|---|---|---|
| AC-1 | `ac1.json`、`ac1-ledger.json` | 同一 `packageName + notificationId + postTime` 两次投递：1 条捕获、1 条交易、1 条证据，状态 PENDING |
| AC-2 | `ac2.json`、`ac2-home.png`、`ac2-pending.png` | `m6-3-f-wx` 回放两次。第一条普通待确认，第二条 `POSSIBLE_DUPLICATE`。本月支出 0。没有 CONFIRMED / DUPLICATE |
| AC-3a | `ac3a.json` | 相差 181 秒的两笔都是普通待确认，没有 `POSSIBLE_DUPLICATE` |
| AC-3b | `ac3b.json` | 杭州深度求索与富富都是 ¥10.00、时间相同，都是普通待确认 |
| AC-3c | `ac3c.json` | 支出与退款同额，都是普通待确认，类型分别是 EXPENSE / REFUND |
| AC-4 | `ac4-pending.png`、`ac4-review.png` | 疑似重复行只有「查看重复」「忽略」。对照页并排新记录和原记录（商户、金额、方向、时间、来源），底部是「同一笔」「两笔」 |
| AC-5 | `ac5-before.json`、`ac5-after.json`、`ac5-counts.json`、`ac5-pending.png`、`ac5-home.png` | 「同一笔」后第二条变成 DUPLICATE，1 条 `SAME_TRANSACTION`。捕获日志 4、证据 4，前后不变。待确认从 4 减到 3。本月支出仍是 0 |
| AC-6 | `ac6-after-different.json`、`ac6-pending-ordinary.png`、`ac6-after-confirm.json`、`ac6-home.png` | 「两笔」去掉 `POSSIBLE_DUPLICATE`，行上是「确认 / 改完再记 / 忽略」。确认后该笔 CONFIRMED，本月支出增加 12999 |
| AC-7 | `ac7-pending.png`、`ac7-edit.png`、`ac7-home.png`、`ac8-before-taps.json` | 待确认列表、编辑后入账、首页都按 UTC+8 显示。编辑页时间是 `2026-11-11 13:05:22`。首页是 `13:05` 和 `10:09` |
| AC-8 | `ac8-before-taps.json`、`ac8.json`、`git-sha.txt` | 点击之前没有 CONFIRMED / DUPLICATE。CONFIRMED 只来自「确认」，DUPLICATE 只来自「同一笔」。`gitSha` 等于功能提交 |
