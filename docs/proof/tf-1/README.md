# TF-1 仪器测试

CR-1（2026-11-25）要求在将交付的 PR head 上重跑两次全量。这次没有新的 270/0 日志。主机 KVM 在创建 vCPU 时触发内核错误，模拟器只能用软件 CPU；在该环境下单跑 M3a 失败。下面的 270/0 数字仍是 2026-11-24、运行时 HEAD `61c18ba` 的结果，不是 CR-1 的复跑。说明见 `cr1/README.md`。

运行时 HEAD：`61c18bafecb52709dba4211a545d027d6ea9c3c4`（最后一个改测试代码的提交）。其后的提交只补 `docs/proof/tf-1/` 和 `AGENTS.md` 的基线行，不再改测试。

模拟器：`system-images;android-36;google_apis;x86_64`，指纹 `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`，API 36，系统时区 `Asia/Shanghai`（设备日期 Tue Nov 24 20:08:49 CST 2026）。Gradle 在 `app/` 执行。环境原文见 `env.txt`。

两次全量中间没有手动清数据。

## 7 条前后对照

改前堆栈是 Asia/Shanghai 上、未改这 7 条时的单跑。详见 `before/stacks.md`。断言计数是调用次数，不含 `import`。

| 用例 | 改前堆栈首行 | 分类 | 改动 | 已批准切口 | 改后单跑 |
|---|---|---|---|---|---|
| `Fx1ProofTest.看错窗口余额宝金额与疑似失效` | 时区检查已通过。先是 `java.lang.IllegalStateException: 截图主机没有回应 ac2-logs`（主机引号错误）。接上后再跑：`java.lang.AssertionError: Failed to inject touch input.`，子串「待确认」命中 3 个节点 | 测试定位过期，另加健康度不自足。时区前置检查未改 | `openPending` 改点顶栏；开头清健康度并把总开关关上 | `docs/cuts/cut-2026-11-24-UX-1.md` | `singles/fx1.xml` 1/0/0/0 |
| `M3aAcceptanceProofTest.待确认三种处置与统计保护取证` | `java.lang.AssertionError: Failed: assertDoesNotExist.` 找到 `m6-2-wechat-pay-success:F-D 确认` | 测试过期。失败点是「确认」仍在，不是「查看重复」已经出现。两侧商户都有且不同时不标疑似 | F-D 与 F-C 同一商户、相隔 30 秒；疑似行断言「查看重复」「忽略」存在，「确认」「改完再记」不存在。重跑 `newState` 改为原状态 | `docs/cuts/cut-2026-11-11-M3b.md`，`docs/cuts/cut-2026-11-19-FX-1-CR1.md` | `singles/m3a.xml` 1/0/0/0 |
| `M62PendingProofTest.首页正式收支不因待确认增加` | 这次单跑通过，没有失败堆栈。DOC-1 全量是未显示 `¥0.00` | 测试串数据。单跑时前面用例没有留下已确认支出 | 开头 `clearCapturedLedger()`；保留 `¥0.00` / `+0.00`；再断言 `monthExpenseMinor == 0` 且 `pendingCount == 1` | 无新切口。清库是为了全量同进程 | `singles/m62-home.xml` 1/0/0/0 |
| `M62PendingProofTest.待确认页展示支付结果fixture` | `java.lang.AssertionError: Failed to inject touch input.` 期望 1 个「待确认」，找到 2 个（顶栏 `待确认 1 条` 和一行以「，待确认」结尾）。不是全量里的 5 个 | 测试定位过期，兼串数据 | 开头清库；点精确描述 `待确认 1 条` | `docs/cuts/cut-2026-11-24-UX-1.md` | `singles/m62-pending.xml` 1/0/0/0 |
| `M64StatusProofTest.状态引导日志导出清空恢复与疑似失效` | `androidx.compose.ui.test.ComposeTimeoutException: Condition still not satisfied after 30000 ms`，调用点是改前第 66 行 `awaitText("无障碍已授权")` | 不自足，兼串数据。引导页已经出现；前往授权后没有授权。全量里引导可能已完成，总开关可能已开，fixture 捕获时间超过 24 小时 | 开头拨回未完成引导、无障碍未授权、总开关关闭；前往授权和返回之间用 shell 授权；断言「可用」前把健康度拨回 1 分钟 | `docs/cuts/cut-2026-11-11-M6-4.md` AC-2 | `singles/m64.xml` 1/0/0/0 |
| `NavigationBehaviorTest.A8_首页返回退出应用` | 这次单跑通过，没有失败堆栈。`waitForIdle` 之后 `isFinishing` 已是 true | 测试时序。产品风险已排除，见 AC-2：返回后前台是桌面 | 先断言首页 `hasEnabledCallbacks() == false`，再用 `waitUntil(5_000)` 等 `isFinishing \|\| isDestroyed` | `docs/06` §6.3，无新切口 | `singles/a8.xml` 1/0/0/0 |
| `MineScreenTest.A5_待确认与关于入口可跳转` | `java.lang.AssertionError: Action performScrollTo() failed.` 期望 1 个「自动记账状态」，找到 2 个 | 测试定位过期。页头胶囊不可点，入口行可点 | 定位改为可点击且描述含「自动记账状态」 | `docs/cuts/cut-2026-11-11-M6-4.md` 页头胶囊 | `singles/mine.xml` 1/0/0/0 |

单跑命令都是 `app/` 下 `./gradlew connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=…#方法"`。`Fx1` 的日志是运行时 HEAD 上重跑的。`M64` 的日志在 `94a9d31`（该文件之后没有再改）。其余四条的日志在 `3aa1832`（那些文件之后没有再改）。两次全量是在最终测试提交 `61c18ba` 上跑的，7 条都包含在内。

`m62-home.log` 与 `m62-pending.log` 的 Gradle 控制台输出相同（任务图 up-to-date，日志里不打印用例名）。区分以同目录 XML 的 `testcase name` 为准。

## 断言计数

相对 `a3ede33`，调用次数只增不减。没有新增 `@Ignore`、`assume*`、`Thread.sleep`。M3a 去掉的那条 `assertDoesNotExist("查看重复")` 换成两条更强的 `assertIsDisplayed`，「确认」「改完再记」的 `assertDoesNotExist` 还在。

| 文件 | 改前 | 改后 |
|---|---:|---:|
| `Fx1ProofTest.kt` | 50 | 50 |
| `M3aAcceptanceProofTest.kt` | 58 | 59 |
| `M62PendingProofTest.kt` | 6 | 8 |
| `M64StatusProofTest.kt` | 60 | 60 |
| `NavigationBehaviorTest.kt` | 12 | 13 |
| `MineScreenTest.kt` | 25 | 25 |

## AC-2

首页冷启动（跳过引导）后按系统返回，前台变成桌面。截图与 `dumpsys` 摘要在 `ac-2/`。用例里有「首页不应有启用中的返回回调」。

## AC-3

两次 `./gradlew connectedDebugAndroidTest`，中间不手动清数据。XML 属性 `tests` / `failures` / `errors` / `skipped`：

| 次数 | tests | failures | errors | skipped | 控制台 | sha256 |
|---|---:|---:|---:|---:|---|---|
| 1 | 270 | 0 | 0 | 0 | `full/connected-1.log`（BUILD SUCCESSFUL in 11m 14s） | `c64c90a4d2c07ec5b12ece9827eb92174021ce45a9828ca08492a3a47b09b3a2` |
| 2 | 270 | 0 | 0 | 0 | `full/connected-2.log`（BUILD SUCCESSFUL in 10m 57s） | `1caae7a172040994905439487c5bac51904f7d1fd2f70a6eaf51ec1eec04f558` |

XML：`full/connected-1.xml`、`full/connected-2.xml`。各 270 个 `testcase`。

## AC-4

`./gradlew testDebugUnitTest`：tests 636，failures 0，errors 0，skipped 0。日志 `jvm/testDebugUnitTest.log`（BUILD SUCCESSFUL in 4s），sha256 `66fab7254578f3ed4fb9ca044712e17fb528bc6255c85f78722edcbe9163fb57`。54 个 `testsuite` 合计 636，与 DOC-1 的 JVM 总数相同。
