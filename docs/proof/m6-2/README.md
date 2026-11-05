# M6-2 截图证明

模拟器：Android 16（API 36），包 `com.zhangran.app`，分支上的 debug 包。

| 文件 | 验收 |
|---|---|
| `ac1-accessibility-settings.png` | 系统设置里「账然」无障碍开关（已打开） |
| `ac1-connectivity-log.png` | 同一次绑定的连通日志。logcat `capturedAt=1793873295188` / `1793873297863`，与 `capture_logs` 一致 |
| `ac4-pending.png` | 「待确认记录」同一屏：`¥38.50`、`星巴克咖啡`、`分类不确定、账户不确定` |
| `ac7-home-stats.png` | 同一 fixture 进待确认之后的首页：本月支出 `¥0.00`，INCOME `+0.00`，BALANCE `0.00`。角标为 2 |
| `ac7-home-stats.json` | 同期库查询：`confirmedCount=0`，`monthExpenseMinor=0`，`monthIncomeMinor=0`，两条均为 `PENDING` / `ACCESSIBILITY` |
