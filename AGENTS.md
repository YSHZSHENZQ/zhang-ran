# AGENTS.md

> 面向接手本项目的 agent / 人。**先读 §0 与 §12**，再按 §2 只取本任务相关文档。
> 本文件是唯一入口；其余细节一律**引用**对应文件，不在此重复。

---

## 0. 项目与当前状态

### 0.1 一句话

「账然」是一个 **Android 本地优先**的自动记账应用：不联网、不上传、数据全在本地；
自动识别宁可进 Pending，也不猜错入账。

- 产品与规则：`docs/01`–`docs/03`
- 架构：`docs/04`
- UI：`docs/05`、`docs/06`、`prototype/账然_UI原型.html`（视觉唯一真值）
- 自动记账：`docs/07`、`docs/10`
- 验收：`docs/08`
- 踩过的坑：`docs/09`（**动手前必读**）
- 真机验证记录：`docs/11`

### 0.2 当前阶段

**阶段一（手工记账闭环）**，正在打磨 M2a 记账表单的视觉一致性。

| 已完成 | 内容 |
|---|---|
| T1a / T1b | 工程骨架、单 Activity、主题骨架、18 页占位 |
| T2a / T2b | Room v2（9 实体）+ 真实迁移 + 采集日志表 |
| T3 / M1a / M1b | `Money` / `Normalize` / `Confidence` / `Dedup` / `Decision` |
| U0 | 采集接入层（**进程内接口**，不再有 URI 入口） |
| M1c' | 3 个 Repository、`AddManualTransactionUseCase`、首页真实列表 |
| M2d-0 | 首次安装初始数据（12 支出 + 6 收入 + 1 现金账户，幂等/不复活/不重跑） |
| UI-0 / 0b / 0c | 颜色·字号·圆角·间距·玻璃·动画 token 全部对齐原型 |
| M2a | 记账表单（金额状态机、分类网格、数字键盘、四态） |
| P-006～P-009 | 降级、置信度、时间口径、分类拆分、初始数据等规则 |

**未做**：M2b/M2c（转账、退款）、M2d（分类/账户管理）、统计/预算/备份页、底部导航图标、属性行彩色图标块。

测试现状：JVM 约 313 例、instrumented 约 102 例，见 §9.2。

### 0.3 五条最容易犯错的约定（速览）

1. **`review/` 只作指导，不入库、不得被引用**（§11.1）。
2. 页面**禁止**字面色值 / 裸尺寸 / 字面时长，一律用 `ui/theme` 的 token（§7.2）。
3. 金额全程 `Long amountMinor`，换算只经 `Money`，**禁止** `Double`/`Float`（§5）。
4. **测试只跑受影响的部分**，不全量重跑（§9.2）。
5. 视觉改动必须**装到设备上截图确认**，跑完测试要**重新安装保留 APK**（§9.3）。

---

## 1. 工作原则

必须遵守：

- 不猜业务规则。
- 不自行扩展产品范围。
- 不因为实现方便而改变领域模型。
- 不让 UI 直接操作数据库。
- 不让自动识别引擎直接修改最终 Transaction。
- 不覆盖用户已经手工修改的数据。
- 无法可靠判断时进入 Pending。
- 不为了"自动化率"牺牲账务正确性。
- 大改动前先检查相关文档。

核心原则：

> 宁可进入 Pending，也不要猜错并自动入账。

---

## 2. 按任务读取文档（只读需要的）

| 任务类型 | 必读 |
|---|---|
| 所有任务 | `README.md`、本文件、`docs/09_总结经验教训.md` |
| UI / Compose | `docs/05`、`docs/06`、`prototype/账然_UI原型.html` |
| 产品行为 | `docs/01`、`docs/02` |
| 领域 / 数据 | `docs/02`、`docs/03` |
| 架构 | `docs/03`、`docs/04` |
| 自动记账 | `docs/03`、`docs/04`、`docs/07`、`docs/10` |
| 测试 | 对应功能文档 + `docs/08` |
| 真机 / 环境 | `docs/09`、`docs/11` |

**不要**无条件读全部文档；也不要读与本任务无关的包（任务书通常会写明"不要读"清单）。

---

## 3. 技术约束

- Kotlin + Jetpack Compose + Material 3
- compileSdk / targetSdk / minSdk、AGP / Gradle / Kotlin / KSP / Hilt / Room 的**具体版本**见
  `app/build.gradle.kts` 与 `app/libs.versions.toml`（不在本文件重复，避免漂移）
- Room + Hilt + Coroutines/Flow/StateFlow + WorkManager

分层与数据流：

```text
UI → ViewModel / StateHolder → UseCase / Domain → Repository → Room
```

- UI 不直接访问 DAO，也不直接持有 Entity（走 Repository / 领域类型）。
- 具体架构决策与模块边界见 `docs/04`。

---

## 4. 自动记账约束

数据必须经过：

```text
Capture → Candidate → Normalize → Confidence → Dedup → Decision（Pending / Confirmed）→ Transaction
```

- v0.1 的 Capture 与识别来自**内嵌的采集核心**（AutoAccounting，GPL-3.0，个人自用、不分发），
  作为账然的 Gradle 模块；**不另行自建第二套采集实现**。
- 采集核心只能经 Adapter 交出候选与证据，**不得**直接写 Transaction，也**不得**决定 Pending/Confirmed。
- 权限矩阵与集成结论见 `docs/07` §9、`docs/10`。

---

## 5. 数据安全

- 金额使用最小货币单位：`Long amountMinor`。
- 解析允许 `BigDecimal`，但**最终不得**用 `Double` / `Float` 保存金额。
- 交易金额与方向分离：`EXPENSE` / `INCOME` / `TRANSFER` / `REFUND`。
- 金额口径（含上限、币种、小数位）唯一落点：`domain/service/Money.kt`。

---

## 6. 用户修改保护

- 任何用户主动修改后的字段都必须保留用户修改结果。
- 自动规则重新运行**不得**无条件覆盖用户修改。
- 字段级冲突与保护字段名单见 `domain/model/Decision.kt`。

---

## 7. UI 规则

### 7.1 交互

- HTML 原型是**视觉**参考；页面规格（`docs/06`）是**行为**参考。
- 组件优先复用。
- 普通信息：**点字段本身**修改；不要同时提供"可点字段 + 旁边的编辑按钮"。

### 7.2 token 纪律（硬约束）

- 页面/组件内**禁止**：字面色值（`Color(0x…)`、`Color.White`）、裸尺寸（`12.dp`）、
  字面时长（`tween(300)`）、内联缓动曲线。
- token 唯一落点（都在 `ui/theme/`）：
  | 文件 | 内容 |
  |---|---|
  | `Color.kt` | 亮/暗色、渐变、玻璃、光晕、遮罩（基础 14 项 + 扩展项） |
  | `Typography.kt` | Display/Headline/Title/Body/Label/Caption/Amount |
  | `Dimens.kt` | 间距（6 主刻度 + 扩展）、圆角（5 主项 + 扩展） |
  | `Shape.kt` | 组件形状 |
  | `Glass.kt` | 玻璃与光晕（`Modifier.zhangRanGlass` / `ZhangRanGlowLayer`） |
  | `Motion.kt` | 10 个时长、3 条缓动、位移与缩放常量 |
  | `Theme.kt` | 组装 + `Modifier.zhangRanBackground`（页面背景渐变） |
- 页面级尺寸若确实没有对应 token（例如键盘键高、网格列数），**集中**成一个页面内
  `object XxxLayout` 并注明原因，**不要**散落在各处。
- 玻璃背景模糊（`backdrop-filter`）是**有意识不做**的降级，理由见 `Glass.kt` 文件头；
  不要"顺手补上"。

### 7.3 字体

- 正文使用**内置微软雅黑**（`ui/theme/Theme.kt` 的 `ZhangRanFontFamily`，文件在 `res/font/`）。
- **只声明 `FontWeight.Normal` 一个条目**：多档字重指向同一文件会让 Compose 认为各字重已存在，
  从而**不再合成加粗**（标题会退化成 Regular）。
- 金额等宽文本用 `FontFamily.Monospace`。

### 7.4 一致性验证

- 视觉改动以 `prototype/账然_UI原型.html` 为准；逐项差异记录在 `review/` 的对照表（不入库）。
- 改动后必须在设备上截图对比（流程见 §9.3）。

---

## 8. 修改规模

以下修改**必须先说明影响范围**（`docs/04` 相关章节 + 受影响测试）：

- 修改核心领域模型
- 修改 Transaction 状态
- 修改自动记账 pipeline
- 修改数据库结构（含 schema、migration）
- 修改去重策略
- 引入新的核心依赖
- 修改超过 10 个核心文件
- 改动采集核心与领域层的边界

---

## 9. 完成标准

### 9.1 通用

完成代码不等于完成任务。至少：

- 编译通过
- 单元测试通过（受影响范围内）
- 关键业务路径验证（落库 / 查询 / 状态）
- UI 状态验证（含空/错误态）
- 数据库 migration 验证（若涉及）
- 自动记账关键路径验证（若涉及）

### 9.2 测试策略（**增量**）

- **只跑受本次改动影响的用例**，不做全量回归。
- JVM：
  ```powershell
  cd app
  $env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
  .\gradlew.bat testDebugUnitTest --tests "com.zhangran.app.<包>.*"
  ```
- Instrumented（**只指定要跑的类**）：
  ```powershell
  .\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.zhangran.app.<类>"
  ```
- 只有以下情况才全量：改数据库 schema / 改领域核心规则 / 改全局 token。

### 9.3 视觉改动的验证流程

1. `.\gradlew.bat installDebug`
2. `adb shell am start -n com.zhangran.app/.MainActivity`
3. `adb shell screencap -p /sdcard/x.png` + `adb pull` → 自己先看图，再请人确认
4. ⚠️ **`connectedDebugAndroidTest` 结束会自动卸载 app**：跑完测试若还要人看，必须重新 `installDebug`
   并用 `adb shell pm list packages | findstr zhangran` 确认。

### 9.4 设备与 adb

- 模拟器：`Medium_Phone_API_36`
- `adb` 完整路径：`$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`
- 设备上找不到 app 时：Pixel launcher 只在**应用抽屉**里显示新装应用（桌面不显示）。

---

## 10. 禁止事项

不要：

- 把业务规则写死在 Composable 中
- 在 Activity 中保存业务状态
- 让 Parser 访问 DAO
- 让 UI 直接操作 Room
- 使用 Double / Float 保存金额
- 删除用户数据而不提供明确恢复语义
- 用永久 FGS 作为自动记账的默认保活方案
- 为了通过测试修改产品规则
- 在 `docs/**`、`README.md`、本文件中引用 `review/` 下的文件
- 自己发明任务范围（转账/退款等原型没有的界面，未经裁定不得实现）

---

## 11. 文档纪律、变更流程与经验记录

### 11.1 文档引用纪律

- **`review/` 下的文件仅作指导，不是冻结文档，且不属于本仓库提交对象。**
- **禁止**在 `README.md`、本文件、`docs/**` 中引用 `review/` 下的任何文件。
- 任何需要长期引用的结论，必须先转成 `docs/` 下的正式文档（如 `docs/10`）再被引用。
- 除 `docs/09`、`docs/10` 外，`docs/01`–`docs/08`、`README.md`、本文件在编码期间视为冻结输入。

### 11.2 文档变更流程（开发期间唯一允许改文档的路径）

```text
发现文档缺口/错误
   ↓
写提案（现象 / 影响面 / 建议改法 / 受影响章节清单 / 需重跑的验收条目）
   ↓
人确认 → 改文档（单独 docs: 提交）→ 全库复查 § 引用
   ↓
重跑受影响验收条目 → 再改代码
```

必须写提案的典型场景：同一事实在两份文档取值不同、`§` 引用失效、`docs/08` 某条判据无法判定、
上游能力与 `docs/07` §9 权限矩阵不符。

**任务书 / 交办单**由人给出（放在 `review/`，不入库）。执行前确认：
文件白名单、验收判据、明确禁止事项。任务书与实际代码冲突时**停下报告**，不自行裁决。

### 11.3 经验记录

文件：`docs/09_总结经验教训.md`

- 遇到会阻塞工作、导致文件/数据损坏、或反复出现的问题，解决后必须追加"问题 + 对策"。
- **只加不改**：只在末尾追加，不修改/删除已有条目，不重排编号。
- 只记关键且重要的问题；每条两行（问题 / 对策），不写背景与代码示例。
- 动手前先读它，避免重复踩坑。

---

## 12. 上手手册（新接手者从这里开始）

### 12.1 环境

| 项 | 值 |
|---|---|
| 仓库根 | 本目录（含 `AGENTS.md`、`README.md`、`docs/`、`app/`、`prototype/`） |
| Gradle 根 | **`app/`**，单模块；所有 gradle 命令在 `app/` 内执行，**不带 `:app:` 前缀** |
| 包名 | `com.zhangran.app` |
| JDK | `C:\Program Files\Android\Android Studio\jbr`（每条命令前设 `$env:JAVA_HOME`） |
| 远程 | `git@gitee.com:KotiyaKirisame/zhang-ran.git`，分支 `dev` |

### 12.2 常用命令

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat assembleDebug                  # 编译
.\gradlew.bat installDebug                   # 装到设备（视觉改动后必做）
.\gradlew.bat testDebugUnitTest              # JVM 全量（一般用 --tests 只跑受影响）
.\gradlew.bat connectedDebugAndroidTest      # 设备测试（只指定 class）
```

截图与查看：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n com.zhangran.app/.MainActivity
& $adb shell input tap 540 2169             # 底部「记一笔」标签
& $adb shell screencap -p /sdcard/x.png
& $adb pull /sdcard/x.png "$env:TEMP\x.png"
```

### 12.3 代码地图

| 包 / 文件 | 职责（细节看文件头 KDoc） |
|---|---|
| `capture/` | 采集接入层：进程内接口、事件源、处理器（U0） |
| `data/local/` | Room：`ZhangRanDatabase`（v2，9 实体）、`entity/`、`dao/`、`Converters`、迁移、`DefaultData`、`DatabaseInitializer`、`DatabaseModule` |
| `data/repository/` | 3 个 Repository（接口 + Room 实现） |
| `domain/model/` | 枚举与数据类：`TransactionType`、`TransactionState`、`CaptureSource`、`PendingReason`、`Candidate`、`Decision` 等 |
| `domain/service/` | 规则唯一落点：`Money`、`TransactionTime`、`Normalize`、`Confidence`、`Dedup`、`Decision` |
| `domain/usecase/` | `AddManualTransactionUseCase`（手工记账唯一写入口）、`ObserveTransactionsUseCase` |
| `ui/theme/` | 全部设计 token 与主题（§7.2） |
| `ui/screen/home/` | 首页列表（四态） |
| `ui/screen/add/` | 记账表单：`AddTransactionScreen`（入口）、`AddTransactionContent`（无状态）、`AddTransactionUiState`（含 `AmountInput` 金额状态机）、`AddTransactionViewModel`、`components/` |
| `ui/common/` | `CategoryIconMapper`（语义标识 → emoji） |
| `di/` | Hilt 模块（含 `ApplicationScope`） |

### 12.4 当前进度与下一步

- 已完成见 §0.2。当前分支 `dev` 最新提交即最新状态（`git log --oneline -10` 可直接看任务脉络）。
- 下一步候选：**M2b/M2c**（转账/退款，需先裁定 UI）、**M2d**（分类/账户管理）、
  底部导航图标、属性行彩色图标块。
- 已知缺口（**尚未实现，接手别当成 bug**）：
  | 缺口 | 原因 |
  |---|---|
  | 「其他」分类只能复用、不能新建 | `CategoryRepository` 只有读方法，需补 `ensureCategory(...)` |
  | 列表非响应式（`suspend` 拉取） | DAO 无 `Flow` 查询；改需动 `data/local` |
  | 时间选择只支持"现在/按日期" | 原型无独立时间轴交互 |
  | 底部导航无图标 | 导航层仍是占位实现 |
  | 属性行图标为纯色块 | 缺 3 个浅色渐变 token |
  | 转账/退款无入口 | 原型无界面，属 M2c，需另行裁定 |

### 12.5 高频坑（完整清单见 `docs/09`）

1. **Kotlin 块注释可嵌套**：KDoc 里写 `xxx/**` 会开启第二层注释，把后续代码吞掉且**不报语法错误**
   （表现为 KSP 报 `X could not be resolved`）。
2. **`connectedDebugAndroidTest` 结束会卸载 app**，之后人找不到图标不是安装失败。
3. **`fillMaxHeight` 在高度无约束的 `Row` 里**会被解析成"填满剩余屏幕"，可把整页撑爆。
4. **结构相同的 Composable 用文本 Replace 批量改会互相污染**（已两次踩到）：按行定位。
5. **`am start` 恒打印 `Starting:`**：判定是否生效要看库里的数据或 `query-activities`，不看这行日志。

### 12.6 协作约定

- 任务书/交办单在 `review/`（不入库）。**没有交办就不自行开工**；有冲突先报告。
- 文档改动单独 `docs:` 提交；代码与文档不混在一个提交。
- 每次视觉调整后：装 → 截图 → 请人确认；不要只凭代码判断"看起来对"。
- 提交信息写清"为什么"（根因），不要只写"改了 X"。
