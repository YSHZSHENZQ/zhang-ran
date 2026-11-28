# 改代码前的单跑堆栈（Asia/Shanghai，API 36）

模拟器时区已经是 `Asia/Shanghai`。下面是不改测试代码时，7 条各自单跑的真实失败首行。顺序单跑，中间没有 `pm clear`。`首页正式收支不因待确认增加` 和 `A8_首页返回退出应用` 在这次单跑里通过，没有失败堆栈。

| 用例 | 结果 | 堆栈首行 |
|---|---|---|
| `Fx1ProofTest.看错窗口余额宝金额与疑似失效` | 失败 | `java.lang.IllegalStateException: 截图主机没有回应 ac2-logs` |
| 同上，截图主机接上之后再跑 | 失败 | `java.lang.AssertionError: Failed to inject touch input.` |
| `M3aAcceptanceProofTest.待确认三种处置与统计保护取证` | 失败 | `java.lang.AssertionError: Failed: assertDoesNotExist.` |
| `M62PendingProofTest.首页正式收支不因待确认增加` | 通过 | 无。这次前面的用例没有留下已确认支出 |
| `M62PendingProofTest.待确认页展示支付结果fixture` | 失败 | `java.lang.AssertionError: Failed to inject touch input.` |
| `M64StatusProofTest.状态引导日志导出清空恢复与疑似失效` | 失败 | `androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 30000 ms` |
| `NavigationBehaviorTest.A8_首页返回退出应用` | 通过 | 无。`waitForIdle` 之后 `isFinishing` 已经是 true |
| `MineScreenTest.A5_待确认与关于入口可跳转` | 失败 | `java.lang.AssertionError: Action performScrollTo() failed.` |

时区前置检查在 Fx1 第一次单跑里已经通过。第一次失败是截图主机的 shell 引号把命令拆开了，`shot-done` 没有写进应用目录。接上主机后再跑，失败点变成顶栏点击。

原因行（节点树里的 fixture 商户名已略去）：

- Fx1 再跑：`Expected exactly '1' node but found '3' nodes that satisfy: (ContentDescription contains '待确认')`。一个是顶栏 `待确认 2 条`，另外两个描述以 `，待确认` 结尾。
- M3a：`Did not expect any node but found '1' node that satisfies: (ContentDescription = 'm6-2-wechat-pay-success:F-D 确认')`。失败在「确认」仍存在，不是「查看重复」。
- M62 待确认页：`Expected exactly '1' node but found '2' nodes`。一个是 `待确认 1 条`，另一个描述以 `，待确认` 结尾。不是全量里的 5 个。
- M64：`awaitText` 调用点是 `awaitText("无障碍已授权")`（改前第 66 行）。「权限引导」和「无障碍未授权」已经出现，截图 `ac2-onboard-before`、`ac2-app-settings` 也已拍下。前往授权之后没有授权动作。
- 我的页：`Expected exactly '1' node but found '2' nodes that satisfy: (ContentDescription contains '自动记账状态')`。一个是 `自动记账状态：不可用`（页头，不可点），一个是 `自动记账状态，不可用，双击进入`（可点）。
