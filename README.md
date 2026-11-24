# 账然

> 个人本地优先、隐私优先的 Android 自动记账应用。

版本：v0.1 ｜ 分支：`dev` ｜ 基线：`40055cebbd4db3c74a6e06f70f1a514a0cd86e61`（UX-1 PR #7 合并提交）

## 项目目标

账然用于在 Android 手机上自动捕获微信、支付宝等支付产生的账务信息，经识别、规范化、置信度判断、去重和领域决策后，自动形成记账记录。

核心原则：

1. 本地优先：核心账务数据默认只保存在设备本地。
2. 隐私优先：不要求云端保存原始账务信息。
3. 宁可 Pending，也不要猜错：不确定的数据进入待确认，不强行自动入账。
4. UI 与识别引擎解耦：账然 UI 是独立产品层，识别与采集能力通过 Adapter 接入。
5. 用户修改优先：用户手工修改过的字段不得被自动识别流程覆盖。
6. 可恢复：账务数据、规则和备份格式必须可验证、可恢复。
7. Agent 友好：产品行为、领域规则、架构、UI、自动记账集成分别由独立文档定义。

## 当前进度

手工记账闭环已交付。待确认、确认入账和去重已交付。微信与支付宝无障碍捕获、可导出的拒绝日志、FX-1 的窗口规则与微信成功页规则已交付。首页「最近交易」可区分待确认与已确认（UX-1）。高置信自动入账不做。解析规则页仍是占位。

> 进度细节与已知缺口只写在 `AGENTS.md` §0.2（**唯一入口**）。本节不得与 §0.2 矛盾。

## 采集与识别能力来源

微信与支付宝无障碍捕获已在本仓库 `app` 模块交付。AutoAccounting **尚未**作为 Gradle 模块接入：`app/settings.gradle.kts` 没有子项目，`app/build.gradle.kts` 的依赖里也没有该模块；上游代码不入库、不参与构建。

目标内嵌方案、Adapter 边界与许可证见 `docs/07_自动记账与AutoAccounting集成.md` 与 `docs/10_AutoAccounting接入结论.md`。模块是否已经编进当前 APK，以 `AGENTS.md` §0.2 的代码事实为准。

## AutoAccounting 使用边界（自用，不分发）

- 本项目当前定位：**个人本地自用，不上架任何应用商店、不对外分发**。
- AutoAccounting 上游仓库声明许可证为 GPL-3.0；内嵌后账然为 GPL-3.0 衍生作品。按"仅自用、不再分发"的口径，不触发分发义务。
- 因此必须遵守（详见 `07` §20）：
  - 采集核心的目标形态仍是 **Gradle 模块内嵌**，来源与 commit 记录在 `docs/10`；当前构建尚未纳入该模块（`AGENTS.md` §0.2）；
  - 升级走 `07` §22 的采集核心版本合并流程；
  - 保留上游 LICENSE 与版权声明（含其依赖的第三方许可）；
  - 若未来要公开分发、闭源分发或商业化，必须先重新做许可证合规评估，或重做采集层。

## 快速开始

前置：Android Studio 的 JDK 21（构建期 JVM 必须是 JDK 21+）、一台 API 31+ 设备或模拟器。

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat assembleDebug             # 构建
.\gradlew.bat installDebug              # 安装到设备
.\gradlew.bat testDebugUnitTest         # JVM 测试
.\gradlew.bat connectedDebugAndroidTest # 设备测试（需要设备/模拟器）
```

打开应用：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n com.zhangran.app/.MainActivity
```

> 测试策略是**增量**的（只跑受影响用例），命令写法与注意事项见 `AGENTS.md` §9.2 / §9.3。
> ⚠️ `connectedDebugAndroidTest` 结束会**自动卸载** app；若还要看界面，需重新 `installDebug`。

## 技术基线

- Android 16 / API 36
- minSdk 31
- Kotlin
- Jetpack Compose
- Material 3
- Room
- Hilt
- Coroutines / Flow / StateFlow
- WorkManager
- Navigation Compose
- 已交付的微信 / 支付宝无障碍捕获在本模块 `capture/`。AutoAccounting（GPL-3.0）尚未以 Gradle 模块接入；方案见 `docs/07`、`docs/10`。仅自用不分发

**字体**：正文内置**微软雅黑**（`app/src/main/res/font/msyh.ttf`，从本机 TTC 提取为 TTF），
与原型在 Windows 上的实际渲染一致。字体文件使 APK 约 43 MB。
理由与注意事项见 `AGENTS.md` §7.3。

## 文档入口

- `AGENTS.md`：Agent 工作规则 + **当前进度 + 上手手册**（接手先读它）
- `docs/01_产品需求.md`：为什么做、做什么、不做什么
- `docs/02_产品规格.md`：产品行为规则
- `docs/03_领域模型与业务规则.md`：数据模型和业务不变量（含领域决策规则）
- `docs/04_技术架构.md`：实现架构和技术边界
- `docs/05_UI设计系统.md`：视觉设计系统
- `docs/06_UI组件与页面规格.md`：组件和页面行为
- `docs/07_自动记账与AutoAccounting集成.md`：采集与识别能力集成（采集侧 SSOT）
- `docs/08_测试与验收标准.md`：测试和完成标准
- `docs/09_总结经验教训.md`：已发生的关键问题与对策（只加不改；**动手前先读**）
- `docs/10_AutoAccounting接入结论.md`：采集核心可行性结论、内嵌接口事实、输出模型关键事实与版本锚点
- `docs/11_真机契约验证记录.md`：真机实测记录（历史说明见文件头；取库读库方法仍适用）
- `prototype/账然_UI原型.html`：UI 视觉参考（**视觉唯一真值**；示例数据不作为行为与数据依据）
- `docs/backup/`：历史审查报告与决策记录归档
- `app/`：**Gradle 工程根目录**（所有代码集中在此；工作区根目录不放代码）
  - 工程内：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew(.bat)`、`gradle/libs.versions.toml`（唯一版本来源）
  - 源码：`app/src/main/java/com/zhangran/app/`（包结构与代码地图见 `AGENTS.md` §12.3）
  - 资源：`app/src/main/res/`（含 `font/msyh.ttf`、`values/themes.xml` 系统栏外观）
  - 构建命令：`cd app; .\gradlew.bat <task>`（Windows）
  - **构建前置：构建期 JVM 必须是 JDK 21+**（Hilt 生成的 Java 编译任务不跟随 Gradle toolchain）
- `AutoAccounting/`：上游参考项目（本地克隆，仅用于接入分析，已 gitignore，不作为本项目提交对象）

## 构建基线

| 项 | 值 |
|---|---|
| AGP / Gradle | 9.4.1 / 9.8.1 |
| Kotlin | AGP 9 内建（**不再使用** `org.jetbrains.kotlin.android` 插件）；Compose 编译器插件 2.3.21 |
| KSP / Hilt / Room | 2.3.12 / 2.60.1 / 2.8.4 |
| Navigation Compose | 2.9.8 |
| JDK | **21**（构建期 JVM；Java 版本另由 Gradle toolchain 声明） |
| `compileSdk` / `targetSdk` / `minSdk` | **37** / 36 / 31 |
| Compose BOM | 2026.09.00 |

说明：`compileSdk` 为 37 是依赖侧硬要求（AndroidX/Compose 2026.09 要求依赖方 compile 到 API ≥ 37）；`targetSdk` 保持 36，运行时行为不变。版本唯一来源为 `app/gradle/libs.versions.toml`。

## 文档优先级

发生冲突时，按以下单一权威链裁决：

```text
01 产品需求
  → 02 产品规格
  → 03 领域模型与业务规则
  → 04 技术架构
  → 05 / 06 UI 规格
  → 实现代码
```

补充规则：

- 采集与识别侧的冲突（枚举取值、来源映射、工作模式、权限）以 `07` 为准；`02` §19 只是产品视角摘要。
- UI 原型只用于确认视觉表现，不作为业务规则来源；原型中的示例数字、示例文件名、示例账户/支付方式不作为数据依据。
- 本链路上不存在独立的"产品目标"文档，产品目标写在 `01_产品需求.md` §4。

## MVP 非目标

MVP 不要求：

- 云端账号体系
- 云端账务同步
- 多设备实时同步
- 社交功能
- 强制 AI
- 强制 OCR
- 永久前台服务维持自动记账
- 对外分发、上架应用商店、商业化（当前定位为个人自用）
