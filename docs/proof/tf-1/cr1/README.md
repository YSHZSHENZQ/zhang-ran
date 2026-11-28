# CR-1 重跑未能完成

日期：2026-11-25。检出 `c5b9eae57b8e3e3fb50d2124feb022a25105f1c6`。没有改 `app/src/main/`，没有改 Gradle 文件，没有改测试。

## 原因

本机 `/dev/kvm` 能打开、能建 VM，但 `KVM_CREATE_VCPU` 让进程段错误。内核日志：

```
kernel BUG at arch/x86/kvm/x86.c:689
RIP: kvm_spurious_fault
kvm_arch_vcpu_create
```

`kvm_intel` 编进内核，不能卸载。模拟器因此拒绝硬件加速。加上 `ANDROID_I_WANT_MY_TCG=yes -accel off` 后，API 36、`Asia/Shanghai`、映像 `system-images;android-36;google_apis;x86_64`（指纹 `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`）可以开机。软件 CPU 比 2026-11-24 的 KVM 慢一个数量级，既有 10 秒焦点等待和界面断言守不住。

## 在软件 CPU 上实际跑到的结果

用户目录的 Gradle 属性打开了已有的 `android.experimental.force-aot-compilation`（仓库里的 Gradle 文件没改）。这只影响安装后的编译，不改变断言。

| 跑法 | 结果 |
|---|---|
| 单跑 A8 | 通过，用例 44.8 秒 |
| 单跑 Fx1 | 通过，1/0/0/0 |
| 单跑 M3a | 失败。`M3aAcceptanceProofTest.kt:92` `assertIsDisplayed`，文案含 `m6-2-wechat-pay-success:F-A` 的节点未显示。XML：`m3a-isolated.xml`，tests 1 / failures 1 / errors 0 / skipped 0，用时 99.6 秒 |
| 一次全量（中途停下） | 同一进程里 M3a 再次失败（同一断言），M64 也失败。这次全量不是 270/0，不作为 AC-3 |

M3a 这一条在 2026-11-24 的 KVM 全量里是通过的。单跑复现说明不是前面用例留下的数据。没有把它当成产品缺陷去改正式代码。

## 没有交付的东西

没有新的连续两次 `connectedDebugAndroidTest` 270/0 日志，因此日志里的运行时 HEAD 也不能等于这次提交。`AGENTS.md` 的基线仍指向 2026-11-24 的 `61c18bafecb52709dba4211a545d027d6ea9c3c4`。
