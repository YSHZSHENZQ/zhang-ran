# DOC-1 AC-4 测试基线

运行前核对：`git diff --stat 40055cebbd4db3c74a6e06f70f1a514a0cd86e61 HEAD -- app/` 为空。`app/` 与基线一致。Gradle 根目录是 `app/`，只有一个模块。

- 运行时 HEAD：`81cbef1bd5a0ab0a4b5729e9147cd71bc4432d9c`（该提交只改 `README.md` 与 `AGENTS.md`）
- 构建期 JVM：OpenJDK 21.0.13
- 数字来自 Gradle test-results XML 的 `tests` / `failures` / `errors` / `skipped` 合计，并与 `testcase` 节点复核

## JVM

在 `app/` 执行：

```text
./gradlew testDebugUnitTest
```

| 任务 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `:testDebugUnitTest` | 636 | 0 | 0 | 0 |

没有第二个 JVM 测试任务。XML 共 54 个 `testsuite` 文件，合计就是上表。HTML 摘要同样是 636 tests、0 failures、0 skipped。

日志关键行：

```text
> Task :testDebugUnitTest
BUILD SUCCESSFUL in 1m 50s
33 actionable tasks: 33 executed
```

## instrumented

在 `app/` 执行（当时已有一台启动完成的 API 36 模拟器）：

```text
./gradlew connectedDebugAndroidTest
```

系统映像是 `system-images;android-36;google_apis;x86_64`。模拟器时区是 `Etc/UTC`，不是 `Asia/Shanghai`。

| 任务 | tests | failures | errors | skipped |
|---|---:|---:|---:|---:|
| `:connectedDebugAndroidTest` | 270 | 7 | 0 | 0 |

日志关键行：

```text
> Task :connectedDebugAndroidTest
Running tests on devices: doc1(AVD) - 16
> Task :connectedDebugAndroidTest FAILED
BUILD FAILED in 10m 38s
79 actionable tasks: 49 executed, 5 from cache, 25 up-to-date
```

7 个失败（本刀不改测试，也不把失败记成通过）：

1. `Fx1ProofTest.看错窗口余额宝金额与疑似失效`：设备时区不是 `Asia/Shanghai`，当时是 `Etc/UTC`。
2. `M3aAcceptanceProofTest.待确认三种处置与统计保护取证`：断言节点不应存在，但找到了 1 个。
3. `M62PendingProofTest.首页正式收支不因待确认增加`：未显示 `¥0.00`。
4. `M62PendingProofTest.待确认页展示支付结果fixture`：期望 1 个「待确认」节点，找到 5 个。
5. `M64StatusProofTest.状态引导日志导出清空恢复与疑似失效`：等待文案超过 30000 ms。
6. `NavigationBehaviorTest.A8_首页返回退出应用`：首页返回没有结束 Activity。
7. `MineScreenTest.A5_待确认与关于入口可跳转`：`performScrollTo` 期望 1 个「自动记账状态」节点，找到 2 个。

以上替换 2026-10-28 的 JVM 535 / instrumented 256。不要再沿用那两个旧数字。
