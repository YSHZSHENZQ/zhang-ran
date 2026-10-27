# 账然

> 个人本地优先、隐私优先的 Android 自动记账应用。

版本：v0.1 ｜ 当前阶段：**阶段一（手工记账闭环）进行中**

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

**已完成**

- 工程骨架与主题骨架（单 Activity、18 页占位）
- 数据库 v2（9 实体）+ 真实迁移
- 领域规则唯一落点：金额、时间、商户规范化、置信度、去重、决策
- 采集接入层**进程内接口**（不再有 URI / 第二 APK 入口）
- 数据层 3 个 Repository + 手工记账用例 + 首页真实列表
- 首次安装初始数据（12 支出 + 6 收入 + 1 现金账户，幂等 / 删除不复活 / 升级不重跑）
- 设计 token（颜色 / 字号 / 圆角 / 间距 / 玻璃 / 动画）全部对齐原型
- **记账表单**（金额状态机、分类网格、数字键盘、四态）

**未做**

- 转账、退款（原型无界面，需另行裁定）
- 分类 / 账户管理页
- 统计、预算、备份页
- 底部导航图标、属性行彩色图标块

> 逐项细节、已知缺口与接手约定见 `AGENTS.md` §0.2 与 §12.4（**唯一入口，不在此重复**）。

## 采集与识别能力来源（v0.1 已定前提）

v0.1 **采集层复用 AutoAccounting 的模块化核心**，账然不另行自建一套采集实现；最终以 **Gradle 模块内嵌于账然 APK**（单 APK 交付，D6）。

**当前状态（勿误解）**：

| 项 | 状态 |
|---|---|
| 账然侧**进程内接入接口** | ✅ 已完成（`capture/`，无 URI、无导出组件） |
| 采集核心作为 Gradle 模块接入 | ❌ **尚未接入**（`app/settings.gradle.kts` 目前只有一个模块） |
| 上游代码 | 仍是本地参考克隆，**不入库、不参与构建** |

- 内嵌采集核心将负责：捕获（通知 / 无障碍 / OCR / 短信）、规则匹配、识别、证据。
- 账然负责：**采集接入与适配**（`capture/`、`autoaccounting/`）、领域模型、领域决策、UI、Pending、统计、预算、备份、用户修改保护。
- 边界：采集核心**不得直接写账然的 Room / Transaction**，只能经 Adapter 交出候选与证据（详见 `docs/07_自动记账与AutoAccounting集成.md` §2/§3.5）。
- `docs/04_技术架构.md` §2 的自动记账链路与本条一致；若措辞有差异，以 `07` 为采集/识别侧 SSOT，以 `03` 为领域侧 SSOT。

## AutoAccounting 使用边界（自用，不分发）

- 本项目当前定位：**个人本地自用，不上架任何应用商店、不对外分发**。
- AutoAccounting 上游仓库声明许可证为 GPL-3.0；内嵌后账然为 GPL-3.0 衍生作品。按"仅自用、不再分发"的口径，不触发分发义务。
- 因此必须遵守（详见 `07` §20）：
  - 采集核心以 **Gradle 模块内嵌**，来源与 commit 记录在 `docs/10`；
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
- 采集与识别能力由**内嵌采集核心**（AutoAccounting，GPL-3.0，Gradle 模块）提供（含通知 / 无障碍 / OCR / 短信等模式，按模式决定所需权限）；仅自用不分发

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
