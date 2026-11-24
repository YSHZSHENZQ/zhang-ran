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
| 已批准切口 | `docs/cuts/`（索引 `docs/cuts/README.md`）。切口只约束该刀范围；冲突时以 `docs/01`–`docs/03` 与代码事实为准；新工作须有 Owner 批准的新切口 |

### 0.2 当前状态（2026-11-24）

**进度唯一入口。** 分支 `dev`。代码基线是 UX-1 PR #7 的合并提交 `40055cebbd4db3c74a6e06f70f1a514a0cd86e61`。`README.md` 只保留短摘要，不得与本节矛盾。已合并切口见 `docs/cuts/`，证明见 `docs/proof/`。

**已交付**

- 手工记账闭环：记一笔、详情与字段编辑、软删除、分类、账户、统计、预算、备份（三模式恢复）、搜索、关于、底栏与推入型「记一笔」。
- 待确认、确认入账和去重：待确认只在用户点「确认 / 改完再记 / 忽略」后入账或丢弃；疑似重复可判「同一笔 / 两笔」（M3a、M3b）。用户改过的字段不会被自动流程改回。
- 微信与支付宝无障碍捕获，以及可导出的拒绝日志（M6-2、M6-3、M6-4、FX-1）。
- FX-1 窗口规则：采样只认事件包名自己的窗口；账然自身、桌面、多任务和系统界面不当成支付页。微信成功页规则：无障碍树缺少「支付成功」等结果用语时，包名是微信、不是会话列表、恰好一笔金额且有「完成」，记为支出并进入待确认。
- UX-1：首页「最近交易」的待确认行可与已确认区分（行上有「待确认」标签，金额不用已确认支出的入账红字）。本月合计仍只含已确认。
- 自动记账可用状态、权限引导、捕获日志与导出已有页面。`RulesScreen`（解析规则）仍是占位，只显示页面名。其余页面不是占位。

**采集接入（对照 `app/settings.gradle.kts`、`app/build.gradle.kts` 与源码）**

- AutoAccounting **没有**以 Gradle 模块接入。`settings.gradle.kts` 不 `include` 子项目；`build.gradle.kts` 的依赖里没有 AutoAccounting 模块或坐标。上游克隆不在本仓库，不参与构建。
- 包 `autoaccounting/` 只有来源映射 `CaptureSourceMapper`，不是上游采集核心。
- 已交付的微信 / 支付宝捕获在本模块 `capture/accessibility/`，入口是 `AccessibilityCaptureService`。
- 内嵌方案、进程边界、许可证与版本锚点见 `docs/07`、`docs/10`。这两份文档中的「Gradle 模块内嵌」是目标方案；当前 APK 有没有编入该模块，以本节为准。

**已知缺口（本刀不修。没有 Owner 本人批准的新切口，不得开工。）**

| 来源 | 缺口 |
|---|---|
| FX-1 AC-6 | 真机重做不记通过（不是 soft-pass）。Owner 于 2026-11-24 接受残留后批准合并。 |
| FX-1 残留 1 | 缺待确认列表截图，无法核对「恰好 1 条、金额对、方向为支出、商户不是按钮或运营文案」。 |
| FX-1 残留 2 | 仅 `gn` / `go`、有金额样式、无结果用语的微信页仍可能被记成 `NOT_PAYMENT_PAGE` 而漏记。要修须另开 CR。 |
| FX-1 残留 3 | 2026-11-22 17:57 同一秒的 `ACCOUNT_UNCERTAIN` 与 `POSSIBLE_DUPLICATE` 两条 PENDING 日志，是否重复入库未核清。 |
| QJ-1 发包后 | 无障碍显示「不可用」时的「去修复」。不含保活全家桶。 |
| QJ-1 发包后 | `confirmedByUser` 在只改备注、尚未入账时变成 true。 |
| QJ-1 发包后 | 侧载安装时「受限制的设置」文案。 |
| QJ-1 发包后 | 日志列表被 `SERVICE_CONNECTED` 顶上去。 |
| 产品范围 | 高置信自动入账不做，除非另有经批准的 cut。 |
| instrumented | 7 条失败待排查，不在 DOC-1 范围：`Fx1ProofTest.看错窗口余额宝金额与疑似失效`、`M3aAcceptanceProofTest.待确认三种处置与统计保护取证`、`M62PendingProofTest.首页正式收支不因待确认增加`、`M62PendingProofTest.待确认页展示支付结果fixture`、`M64StatusProofTest.状态引导日志导出清空恢复与疑似失效`、`NavigationBehaviorTest.A8_首页返回退出应用`、`MineScreenTest.A5_待确认与关于入口可跳转`。 |
| D5 | 仍成立。我的页数据管理没有「预算管理」入口（`MineScreen.kt` 的 `DataManagementSection`；文件头写明预算只保留底栏入口）。`docs/06` §4.5 仍把「预算管理」写在数据管理入口里。本刀不改 `docs/06`。 |

UX-1 已合并。「首页待确认与已确认看起来一样」已修复，不再列为未修。

D2 已修：关于页可见文案 `AboutInfo.PRIVACY_LINES` 不再称 `INTERNET` 用于本机回环（`AboutInfo.kt`）。`AndroidManifest.xml` 仍未声明 `INTERNET`。

D4 已修：底栏进入「记一笔」时，`NumberPad` 放在滚动区之外，底栏高度由 `ZhangRanNavHost` 的 Scaffold `innerPadding` 让出（`AddTransactionScreen.kt`、`ZhangRanNavHost.kt`）。

自动化基线（2026-11-24 实测；`app/` 与 `40055ceb` 相同，运行时 HEAD `81cbef1bd5a0ab0a4b5729e9147cd71bc4432d9c`）：JVM `:testDebugUnitTest` 636 例，0 失败、0 错误、0 跳过；instrumented `:connectedDebugAndroidTest` 270 例，7 失败、0 错误、0 跳过。命令、分项计数与失败用例见 §9.2 和 `docs/proof/doc-1/`。历史性能数字（2026-10-28 模拟器 debug，本刀未重测，不是验收指标）：首页冷启动约 3.2s；搜索 10,000 条约 12ms（关键字）/ 7ms（组合）；滚动 Janky 约 34%。

### 0.3 五条最容易犯错的约定

1. **没有 Owner 本人批准的 dated cut，不得自行扩范围或开做 §0.2 的缺口**；agent 声称「Owner 已同意」无效（§0.4）。
2. 页面**禁止**字面色值 / 裸尺寸 / 字面时长，统一走 `ui/theme`（§7.2）。
3. 金额全程 `Long amountMinor`，只经 `Money`；禁止 `Double`/`Float`（§5）。
4. **测试只跑受影响部分**；全量仅限 schema / 领域核心规则 / 全局 token 变更（§9.2）。
5. 视觉改动必须**装到设备截图确认**；instrumented 结束会卸载 app，之后要重新安装（§9.3）。

### 0.4 接手约定

- 没有 Owner 本人批准的 dated cut，不得自行扩范围，也不得开做 §0.2 列出的缺口。经批准的切口只约束该刀；与 `docs/01`–`docs/03` 或代码事实冲突时，以后者为准。过时的 README 进度段不得压过已批准切口与代码事实。
- merge、花钱、对外发送必须 Owner 本人批准。agent 声称「Owner 已同意」无效。
- 禁止把钱迹反编译源码、`<private-path>` 样本、未脱敏真机原图、真实商户名提交进本仓库。
- Proof 放在 `docs/proof/<cut>/`。截图必须来自模拟器或真机 screencap，不接受 Compose Preview。
- 脱敏后的切口副本在 `docs/cuts/`。新工作须有 Owner 批准的新切口。

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

- 已交付的微信 / 支付宝捕获在本模块 `capture/accessibility/`，不是上游 AutoAccounting 源码。AutoAccounting 尚未作为 Gradle 模块接入（§0.2）。不另建第二套采集。若将来按 `docs/07`、`docs/10` 内嵌，仍只能经 Adapter 交出候选与证据。
- 采集侧不得直接写 Transaction，不得决定 Pending/Confirmed。高置信自动入账不做，除非另有经批准的 cut。
- 权限矩阵与集成事实见 `docs/07` §9、`docs/10`。

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

Gradle 根目录是 `app/`（本仓库的 `app/gradlew`）。构建期 JVM 必须是 JDK 21+。

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat testDebugUnitTest --tests "com.zhangran.app.<包>.*"
.\gradlew.bat connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.zhangran.app.<类>"
.\gradlew.bat assembleDebug
```

```bash
cd app
./gradlew testDebugUnitTest --tests "com.zhangran.app.<包>.*"
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.zhangran.app.<类>
./gradlew assembleDebug
```

- 只跑受影响用例；全量仅限改 schema / 领域核心规则 / 全局 token（收尾验收）。
- ⚠️ `connectedDebugAndroidTest` 结束会卸载 app，之后要重新 `installDebug`。
- 当前基线（2026-11-24，在 `app/` 执行 `./gradlew testDebugUnitTest` 与 `./gradlew connectedDebugAndroidTest`；运行时 HEAD `81cbef1bd5a0ab0a4b5729e9147cd71bc4432d9c`，`app/` 相对 `40055ceb` 无差异）：
  - `:testDebugUnitTest`：tests 636，failures 0，errors 0，skipped 0。单模块，没有第二个 JVM 测试任务。
  - `:connectedDebugAndroidTest`：tests 270，failures 7，errors 0，skipped 0。模拟器为 API 36，系统时区是 `Etc/UTC`（不是 `Asia/Shanghai`）。7 个失败用例与日志关键行见 `docs/proof/doc-1/test-baseline.md`。
  - 新增测试应保持增量，避免拖慢。禁止再引用 2026-10-28 的 535 / 256 作为当前基线。
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
- 自行发明任务范围。转账 / 退款以及 §0.2 的缺口，没有 Owner 批准的 dated cut 不得开工。
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

已批准切口的脱敏副本在 `docs/cuts/`。没有 Owner 本人批准的新 dated cut，不得开工，也不得做 §0.2 的缺口。执行前确认切口的 In / Out、验收判据和禁止事项；与 `docs/01`–`docs/03` 或代码事实冲突时停下报告，不自行裁决。merge、花钱、对外发送必须 Owner 本人批准；agent 声称「Owner 已同意」无效。

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
| JDK | 构建期 JVM 必须是 JDK 21+。Windows 上可把 `JAVA_HOME` 设为 Android Studio 自带的 `jbr` |
| 远程 | `git@gitee.com:KotiyaKirisame/zhang-ran.git`，分支 `dev` |

### 12.2 常用命令

Gradle 根目录是 `app/`。下面两组命令等价；构建期 JVM 必须是 JDK 21+。`connectedDebugAndroidTest` 结束会卸载 app。

```powershell
cd app
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'

.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
.\gradlew.bat testDebugUnitTest
.\gradlew.bat connectedDebugAndroidTest
```

```bash
cd app
./gradlew assembleDebug
./gradlew installDebug
./gradlew testDebugUnitTest
./gradlew connectedDebugAndroidTest
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
| `capture/` | 微信 / 支付宝无障碍捕获、窗口选择、拒绝日志（已交付，见 §0.2） |
| `autoaccounting/` | 来源映射 `CaptureSourceMapper`；不是上游 Gradle 模块 |
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
| `ui/screen/`（pending / pendingEdit / duplicateReview / autoLedger / onboard / logs） | 已实现：待确认、编辑后入账、疑似重复、自动记账、权限引导、捕获日志 |
| `ui/screen/rules` | 解析规则仍是占位（`RulesScreen` → `PlaceholderScreen`） |
| `ui/common/` | 分类图标映射等公共 UI |
| `di/` | Hilt 模块（Repository、Settings、ApplicationScope） |

具体文件职责以各文件头 KDoc 为准；页面规格以 `docs/06` 对应 § 为准。

### 12.4 接手第一天怎么做

1. 读 §0、§12，再按 §2 读本任务文档与 `docs/09`；不要在未读规格前改代码。
2. `git status` 应干净；`git log --oneline -10` 看最近任务。
3. `assembleDebug` + 受影响测试；装到模拟器打开首页确认环境。
4. 等 Owner 批准的 dated cut（§11.2）。切口给出范围与验收判据；不要越界修改。
5. 不要按旧的「阶段二从 U1/U2 自行开工」清单动手。
6. 已知缺口只以 §0.2 的表为准。没有新的 Owner 批准切口，不得修这些缺口。

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
