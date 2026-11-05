# M3a 截图与 API JSON

模拟器：Android 14（API 34，`m3a_api34`，1080×2400）。截图来自 `UiAutomation.takeScreenshot`，不是 Compose Preview。
包 `com.zhangran.app` debug。所验 APK 的 `BuildConfig.GIT_SHA` 见 `git-sha.txt`，与各 JSON 的 `gitSha` 相同：`2d60c5ee5f4ffe4508a0672d9adb69c991af6515`。

金额一律 3850 分（¥38.50）。fixture 都来自 M6-2 的 `AccessibilityFixtures.wechatPaySuccess`。F-D 商户改为「瑞幸咖啡」，与 F-C 相差 30 秒，从而带上 `POSSIBLE_DUPLICATE`。

| 代号 | fixture id | 商户 |
|---|---|---|
| F-A | `m6-2-wechat-pay-success:F-A` | 星巴克咖啡 |
| F-B | `m6-2-wechat-pay-success:F-B` | 星巴克咖啡 |
| F-C | `m6-2-wechat-pay-success:F-C` | 星巴克咖啡 |
| F-D | `m6-2-wechat-pay-success:F-D` | 瑞幸咖啡 |
| F-E | `m6-2-wechat-pay-success:F-E` | 星巴克咖啡 |
| F-NEW | `m6-2-wechat-pay-success:F-NEW` | 星巴克咖啡 |

| 文件 | 验收 |
|---|---|
| `ac1-pending-actions.png` | AC-1。F-A / F-B / F-C / F-E 有「确认 / 改完再记 / 忽略」。F-D 只有「忽略」。没有「查看重复」 |
| `ac1.json` | AC-1。F-D `reasons` 含 `POSSIBLE_DUPLICATE`，`state=PENDING` |
| `ac2-pending-after-confirm.png` | AC-2。确认 F-A 之后列表里没有 F-A |
| `ac2.json` | AC-2。F-A `state=CONFIRMED`，`confirmedByUser=true`，`fieldEdits=[]` |
| `ac3-edit-before-save.png` | AC-3。F-B 编辑页，键盘已收起。同一屏可读分类「餐饮」、备注「改完再记备注」和「保存并入账」 |
| `ac3-pending-after-save.png` | AC-3。保存后列表里没有 F-B |
| `ac3.json` | AC-3。F-B `fieldEdits` 恰好 `categoryId` 与 `note` |
| `ac4-before.json` | AC-4。忽略 F-C 之前：`state=PENDING`，`evidenceCount=1`，`captureLogCount=1` |
| `ac4.json` | AC-4。忽略之后：`state=IGNORED`，证据与日志条数仍为 1 |
| `ac4-confirmed-check.json` | AC-4。同一时刻没有该笔的 `CONFIRMED` |
| `ac5-home-before.png` / `ac5-before.json` | AC-5 前。本月支出 ¥0.00，角标 5，`monthExpenseMinor=0` |
| `ac5-home-after.png` / `ac5-after.json` | AC-5 后。本月支出 ¥77.00（7700 = F-A 3850 + F-B 3850），角标 2。F-C 不计入 |
| `ac6-before.json` / `ac6-after.json` / `ac6-rerun.json` | AC-6。重跑同一 fixture 后 F-B 的分类与备注不变，`confirmedByUser` 仍为 true，F-C 仍为 IGNORED，新增行只是 PENDING |
| `ac7.json` / `git-sha.txt` | AC-7。F-NEW 为 PENDING。`gitSha` 与所验 APK 一致 |
| `ac8-empty-amount.png` | AC-8。金额为空，「金额不能为空」 |
| `ac8-zero-amount.png` | AC-8。金额为 0，「金额不能为 0」 |
| `ac8-illegal-amount.png` | AC-8。金额非法，「金额不合法」 |
| `ac8-empty-time.png` | AC-8。时间为空，「时间不能为空」 |
| `ac8.json` | AC-8。F-E 仍为 `PENDING`，`fieldEdits=[]`，`confirmedByUser=false` |
