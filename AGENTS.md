# AGENTS.md

> 面向接手本项目的 agent / 人：**先读 §0 与 §12**，再按 §2 只读本任务相关文档。
> 本文件是唯一入口；细节一律引用对应文件，不在此重复。

---

## 0. 项目与当前状态

### 0.1 一句话

「账然」是 Android **本地优先**的自动记账应用：账务数据不出本机，自动识别宁可进 Pending，也不猜错入账。

| 内容 | 唯一来源 |
|---|---|
| 产品、规则 | `docs/01`–`docs/03` |
| 架构与分层 | `docs/04` |
| UI 规范 / token | `docs/05`、`docs/06`、`prototype/账然_UI原型.html`（视觉唯一真值） |
| 自动记账 / 采集 | `docs/07`、`docs/10` |
| 验收判据 | `docs/08` |
| 经验教训 | `docs/09`（**动手前必读**） |
| 真机记录 | `docs/11` |

### 0.2 当前状态（2026-10-28）

**阶段一（手工记账闭环）已完成收尾验收；阶段二（采集核心接入）尚未开始。**

- 18 个页面均有实现：11 个完整页（首页 / 统计 / 记一笔 / 预算 / 我的 / 详情 / 分类 / 账户 / 备份 / 搜索 / 关于），
  7 个阶段二占位页（待确认 / 编辑后入账 / 疑似重复 / 自动记账 / 权限引导 / 解析规则 / 捕获日志）。
- 阶段一完成脉络：T1 工程/18 页骨架 → T2 Room v2 + 迁移 → T3/M1 领域规则 → U0 采集接入层 →
  M1c'/M2a/M2e-1 手工记账与首页 → M2b 详情/字段编辑/软删除 → M2d 分类/账户 → M4a 预算 →
  M4b 备份/三模式恢复 → M4c 设置与主题 → M4d 关于 → M4e 搜索 → M5-1 导航与底栏 → M5 收尾验收。
- 分支 `dev`，工作区应保持干净；最新提交即最新状态（`git log --oneline -10` 可看任务脉络）。
- 自动化基线：**JVM 535 例（1 例失败，见 D1）**；**instrumented 256 例全过**；
  `assembleDebug` 通过。全量命令与例外见 §9.2。
- 性能基线（模拟器 debug）：首页冷启动约 3.2s；搜索 10,000 条约 12ms（关键字）/ 7ms（组合）；
  滚动 Janky 约 34%。仅作基线，不是指标。

**已知问题（接手先看，详见 §12.4）**

| # | 问题 | 下一步 |
|---|---|---|
| D1 | `DestinationsTest.页面清单与文档一致` 仍断言 `detail`，实际路由为 `detail?transactionId={transactionId}`；JVM 全量因此 1 红 | 只改测试期望（测试文件；不得为绿灯改产品） |
| D2 | 关于页隐私文案称 `INTERNET` 用于本机回环，但当前 Manifest **零权限**、未声明 `INTERNET`（计划 U3 合并） | 阶段二 U3 前先裁定：改文案，或按 U3 声明权限 |
| D3 | 搜索结果行点击无跳转（`SearchScreen` 的 `onOpenTransaction` 默认空实现，NavHost 未接线） | 导航小任务接线 |
| D4 | 底栏进入「记一笔」时数字键盘最后一行被底栏部分遮挡 | UI 打磨：内容底部让出底栏高度 |
| D5 | 我的页已移除「预算管理」入口（产品决定），`docs/06` §4.5 仍写有该入口 | 走文档提案流程修正 `docs/06` |

**阶段二范围**：U1 上游库化 → U2 采集接入 → U3 权限合并 → U4 Room 隔离 → U5 UI 处置 → P0 覆盖率基线；
另含 M2c（转账/退款，需先裁定 UI）、M3a/M3b（Pending / Duplicate 处理）与 7 个占位页。

### 0.3 五条最容易犯错的约定

1. **任务书 / 交办单由人给出，不入库**；没有交办不自行开工，冲突先报告（§11）。
2. 页面**禁止**字面色值 / 裸尺寸 / 字面时长，统一走 `ui/theme`（§7.2）。
3. 金额全程 `Long amountMinor`，只经 `Money`；禁止 `Double`/`Float`（§5）。
4. **测试只跑受影响部分**；全量仅限 schema / 领域核心规则 / 全局 token 变更（§9.2）。
5. 视觉改动必须**装到设备截图确认**；instrumented 结束会卸载 app，之后要重新安装（§9.3）。

---

## 1. 工作原则

- 不猜业务规则，不自行扩展产品范围，不因实现方便改变领域模型。
- UI 不直接操作数据库；自动识别不直接修改最终 Transaction；不覆盖用户已修改的字段。
- 无法可靠判断时进入 Pending；不为了"自动化率"牺牲账务正确性。
- 大改动前先读相关文档；发现任务书与代码冲突，**停下报告**。

> 核心原则：**宁可进入 Pending，也不要猜错并自动入账。**

---

## 2. 按任务读取文档

| 任务类型 | 必读 |
|---|---|
| 所有任务 | `README.md`、本文件、`docs/09` |
| UI / Compose | `docs/05`、`docs/06`、`prototype/账然_UI原型.html` |
| 产品行为 | `docs/01`、`docs/02` |
| 领域 / 数据 | `docs/02`、`docs/03` |
| 架构 | `docs/03`、`docs/04` |
| 自动记账 | `docs/03`、`docs/04`、`docs/07`、`docs/10` |
| 测试 | 对应功能文档 + `docs/08` |
| 真机 / 环境 | `docs/09`、`docs/11` |

不要无条件读全部文档；不要读与本任务无关的包（任务书通常写明"不要读"清单）。

---

## 3. 技术约束

- Kotlin + Jetpack Compose + Material 3；Room + Hilt + Coroutines/Flow/StateFlow + WorkManager。
- 版本以 `app/build.gradle.kts`、`app/libs.versions.toml` 为准，不在本文件重复。
- 数据流：

```text
UI → ViewModel / StateHolder → UseCase / Domain → Repository → Room
```

- UI 不持有 Entity、不访问 DAO；边界与模块划分见 `docs/04`。

---

## 4. 自动记账约束

```text
Capture → Candidate → Normalize → Confidence → Dedup → Decision（Pending / Confirmed）→ Transaction
```

- 采集核心为内嵌 Gradle 模块（AutoAccounting，GPL-3.0，个人自用、不分发）；不另建第二套采集。
- 采集核心只能经 Adapter 交出候选与证据；不得直接写 Transaction，不得决定 Pending/Confirmed。
- 权限矩阵与集成事实见 `docs/07` §9、`docs/10`；当前阶段尚未接入（§0.2）。

---

## 5. 数据安全

- 金额用最小货币单位 `Long amountMinor`；解析可用 `BigDecimal`，**最终不得**用 `Double`/`Float` 保存。
- 方向与金额分离：`EXPENSE` / `INCOME` / `TRANSFER` / `REFUND`。
- 金额口径（上限、币种、小数位）唯一落点：`domain/service/Money.kt`。

---

## 6. 用户修改保护

- 用户主动修改的字段必须保留；自动规则重跑不得无条件覆盖。
- 保护字段名单与冲突规则见 `domain/model/Decision.kt` 与 `docs/03`。

---

## 7. UI 规则

### 7.1 交互

- HTML 原型是**视觉**参考，`docs/06` 是**行为**参考。
- 组件优先复用（如首页 `TransactionRow` 已被搜索页复用）。
- 普通信息点字段本身修改；不要"可点字段 + 旁边编辑按钮"。

### 7.2 token 纪律（硬约束）

- 禁止字面色值（`Color(0x…)`、`Color.White`）、裸尺寸（`12.dp`）、
  字面时长（`tween(300)`）与内联缓动曲线。
- token 唯一落点见 `ui/theme/` 各文件（`Color` / `Typography` / `Dimens` / `Shape` /
  `Glass` / `Motion` / `Theme`），文件职责见 `docs/05`。
- 确无 token 的页面级尺寸，集中到页面内 `object XxxLayout` 并注明原因。
- 玻璃模糊是**有意识不做**的降级（理由见 `Glass.kt` 文件头），不要顺手补。

### 7.3 字体

- 正文用内置微软雅黑（`Theme.kt` 的 `ZhangRanFontFamily`）。
- **只声明 `FontWeight.Normal` 一个条目**；否则 Compose 不再合成加粗，标题会退化。
- 金额等宽用 `FontFamily.Monospace`。

### 7.4 导航现状

底栏规则、推入型「记一笔」、返回键与切 tab 保栈均已实现，规格见 `docs/06` §6；
回归用例见 `BottomNavBehaviorTest` / `NavigationBehaviorTest`。

---

## 8. 修改规模

以下改动必须先说明影响范围（`docs/04` 对应章节 + 受影响测试）：

- 核心领域模型、Transaction 状态、去重策略；
- 自动记账 pipeline、采集核心与领域层边界；
- 数据库 schema / migration；
- 新核心依赖；
- 超过 10 个核心文件。

---

## 9. 完成标准

### 9.1 通用

完成代码 ≠ 完成任务。至少：编译通过、受影响单测通过、关键路径验证（落库/查询/状态）、
UI 状态（空/错/加载）验证、migration 验证（若涉及）、采集路径验证（若涉及）。

### 9.2 测试策略（增量）

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat testDebugUnitTest --tests "com.zhangran.app.<包>.*"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.zhangran.app.<类>"
.\gradlew.bat assembleDebug
```

- 只跑受影响用例；全量仅限改 schema / 领域核心规则 / 全局 token（收尾验收）。
- 当前基线：JVM 535（1 红，D1）、instrumented 256；新增测试应保持增量、避免拖慢。
- 若接口变更导致既有测试 fake 编译失败：只补空实现/适配，不改既有断言。

### 9.3 视觉验证

1. `.\gradlew.bat installDebug`
2. `adb shell am start -n com.zhangran.app/.MainActivity`
3. `adb shell screencap -p /sdcard/x.png` + `adb pull` → 自己先看图，再请人确认
4. ⚠️ `connectedDebugAndroidTest` 结束会卸载 app；之后必须重新 `installDebug`。

### 9.4 设备与 adb

- 模拟器：`Medium_Phone_API_36`（Android 16）。
- adb：`$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe`。
- 桌面找不到 app：Pixel launcher 只在应用抽屉显示新装应用。

---

## 10. 禁止事项

- 把业务规则写死在 Composable；在 Activity 保存业务状态；UI 直接操作 Room。
- 让 Parser 访问 DAO；用 `Double`/`Float` 保存金额。
- 删除用户数据而不提供恢复语义；用永久 FGS 作为自动记账保活方案。
- 为通过测试改产品规则、放宽判据或删测试。
- 在 `docs/**`、`README.md`、本文件中引用 `review/` 下的文件。
- 自行发明任务范围（转账/退款、Pending 等未裁定界面）。
- 修改 `versionName` / `versionCode`（除非任务书明确要求）。

---

## 11. 文档与任务纪律

### 11.1 文档引用

- `review/` 仅作指导、不入库；**禁止**在 `README.md`、本文件、`docs/**` 引用其文件。
- 需长期引用的结论先转入 `docs/`（如 `docs/10`）再引用。
- `docs/01`–`docs/08`、`README.md` 在编码期视为冻结输入（`docs/09`/`docs/10` 可按规则追加）。

### 11.2 文档变更流程

```text
发现缺口/冲突 → 写提案（现象/影响面/建议/受影响章节/需重跑验收）
→ 人确认 → 单独 docs: 提交 → 全库复查 § 引用 → 重跑受影响验收 → 再改代码
```

任务书 / 交办单由人提供（不入库）。执行前确认：文件白名单、验收判据、禁止事项；
与实际代码冲突时停下报告，不自行裁决。

### 11.3 经验记录

`docs/09`：只加不改、只记关键问题、每条两行（问题 / 对策），动手前先读。

---

## 12. 上手手册

### 12.1 环境

| 项 | 值 |
|---|---|
| 仓库根 | 本目录（`AGENTS.md`、`README.md`、`docs/`、`app/`、`prototype/`） |
| Gradle 根 | **`app/`**，单模块；命令在 `app/` 内执行，不带 `:app:` |
| 包名 | `com.zhangran.app` |
| JDK | `C:\Program Files\Android\Android Studio\jbr`（每条命令前设 `$env:JAVA_HOME`） |
| 远程 | `git@gitee.com:KotiyaKirisame/zhang-ran.git`，分支 `dev` |

### 12.2 常用命令

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb shell am start -n com.zhangran.app/.MainActivity
& $adb shell screencap -p /sdcard/x.png
& $adb pull /sdcard/x.png "$env:TEMP\x.png"
```

### 12.3 代码地图

| 包 / 文件 | 职责 |
|---|---|
| `capture/`、`autoaccounting/` | 采集接入层与 Adapter（U0；尚未接真实采集） |
| `data/local/` | Room v2（9 实体）、DAO、迁移、初始数据、日志表 |
| `data/repository/` | 交易 / 分类 / 账户 Repository（接口 + Room 实现） |
| `data/backup/` | 备份导出、三模式恢复、原子性/不变量修复 |
| `data/settings/` | DataStore 设置存储（主题等） |
| `domain/model/` | 枚举与数据类（Transaction、Candidate、Decision、SearchCriteria 等） |
| `domain/service/` | `Money`、`TransactionTime`、`Normalize`、`Confidence`、`Dedup`、`Decision` |
| `domain/usecase/` | 手工记账、列表、统计、预算、详情编辑/删除、搜索等用例 |
| `ui/theme/` | 全部设计 token、玻璃、动画、字体 |
| `ui/navigation/` | Destination、NavHost、底栏显隐与返回规则、图标 |
| `ui/screen/`（home / stats / add / budget / mine / detail / categories / accounts / backup / search / about） | 已实现页面 |
| `ui/screen/`（pending / pendingEdit / duplicateReview / autoLedger / onboard / rules / logs） | 阶段二占位页 |
| `ui/common/` | 分类图标映射等公共 UI |
| `di/` | Hilt 模块（Repository、Settings、ApplicationScope） |

具体文件职责以各文件头 KDoc 为准；页面规格以 `docs/06` 对应 § 为准。

### 12.4 接手第一天怎么做

1. 读 §0、§12，再按 §2 读本任务文档与 `docs/09`；不要在未读规格前改代码。
2. `git status` 应干净；`git log --oneline -10` 看最近任务。
3. `assembleDebug` + 受影响测试；装到模拟器打开首页确认环境。
4. 等任务书（§11.2）。任务书会给出**文件白名单**与验收判据；不要越界修改。
5. 当前没有"进行中的功能分支"；阶段二的起点是 U1/U2，但**不要自行开工**，先等人确认范围。
6. 若先做收尾修补，按 §0.2 的 D1–D5 顺序：D1 测试期望、D2 权限/文案裁定、D3 搜索行接线、
   D4 键盘遮挡、D5 文档提案；每项单独提交。

### 12.5 高频坑（完整见 `docs/09`）

1. **Kotlin 块注释可嵌套**：KDoc 里写 `xxx/**` 会开第二层注释吞掉后续代码且不报语法错误。
2. **`connectedDebugAndroidTest` 结束会卸载 app**，之后找不到图标不是安装失败。
3. **`fillMaxHeight` 在高度无约束的 `Row`** 会被解析为填满屏幕，可能撑爆整页。
4. **结构相同的 Composable 用文本 Replace 批量改会互相污染**：按行定位。
5. **`am start` 恒打印 `Starting:`**：是否生效看数据/`query-activities`，不看日志。
6. **给 Repository 接口加方法会打断所有测试 fake**：同步补空实现或更新 fake。
7. **改文件注意换行符**：`Set-Content` 可能把 LF 变 CRLF，产生整文件 diff；编辑后查 `git diff --stat`。

### 12.6 协作与提交

- 没有交办不自行开工；任务书冲突先报告。
- 文档改动单独 `docs:` 提交；代码与文档不混。
- 提交信息写清 **为什么**（根因），不要只写"改了 X"；一个任务一个提交。
- 视觉调整：装 → 截图 → 请人确认，不要只凭代码判断。
- 测试后重新安装保留 APK；最终报告列明命令、输出、截图与未覆盖项。
