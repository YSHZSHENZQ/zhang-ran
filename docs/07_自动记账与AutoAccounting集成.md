# 07 自动记账与 AutoAccounting 集成

版本：v0.1  
定位：本文件是采集与识别侧（CaptureSource、工作模式、权限、上游接入边界）的 Single Source of Truth。领域规则见 `03`，层边界见 `04`，产品行为见 `02`。

## 1. 目标与已定前提

**v0.1 的采集层复用 AutoAccounting 的模块化核心（自用、不分发），账然不另行自建采集实现。**

账然负责：

- 产品 UI
- 宿主接入与适配（`capture/`、`autoaccounting/`）
- 领域模型与领域决策
- 最终交易
- Pending
- Dedup
- 统计
- 预算
- 备份
- 用户修改保护

上游 AutoAccounting 提供基于 Xposed / LSPosed、无障碍、Shizuku 等方式的账单采集与识别能力，覆盖通知、短信、OCR / 屏幕识别以及规则 / AI 辅助等方向；项目许可证为 GPL-3.0。

推论（本文件后续章节均以此为前提）：

- 账然**不实现**第二套采集实现；Android 侧 `NotificationListenerService` 不是 v0.1 的采集主体。
- 上游是“账单信息采集模块，需要配合记账软件使用”，账然是**宿主应用**，必须实现接入面（见 §3）。
- 账然的职责边界到“接收上游结果 → 生成候选与证据 → 领域决策”为止。

## 2. 核心原则

不要：

```text
AutoAccounting → 直接写账然 Room
AutoAccounting → 决定 Pending / Confirmed
账然 → 改写上游既有逻辑（识别 / Room / UI / Hook）
账然 → 自建第二套采集实现
```

应该：

```text
AutoAccounting（上游：采集 / 外部规则 / 识别 / 证据）
      ↓  仅经接入面
账然 capture/ → autoaccounting/ Adapter
      ↓
TransactionCandidate + CaptureEvidence
      ↓
账然 Domain Pipeline（Normalize → Confidence → Dedup → Decision）
      ↓
Pending / Confirmed / Duplicate / ClearlyInvalid
      ↓
Repository → Room
```

### 2.1 受控上游补丁（唯一允许改上游的方式）

上游的宿主适配器列表是**硬编码**的（`AppAdapterManager.adapterList()`，无 SPI / 反射 / 配置文件扩展点），因此“让上游把账单发给账然”必须打一个**纯增量补丁**。这是 §1 前提下唯一的例外，边界如下：

| 项 | 规定 |
|---|---|
| 允许 | 新增一个实现 `IAppAdapter` 的适配器文件；在 `adapterList()` 追加**一行**注册 |
| 禁止 | 修改上游任何既有逻辑：识别引擎、Room、UI、Hook、权限、其他适配器 |
| 放置 | 独立仓 / 独立分支，不混入上游主线提交历史 |
| 锚定 | 必须记录基线 commit；升级走 §22，升级时 rebase 该最小 diff |
| 许可 | 补丁仓同为 GPL-3.0；仅自用，不得分发（见 §20） |
| 回归 | 补丁变更后必须重跑 `08` §17 的上游集成用例与 §16 fixture |

若上游未来提供可扩展点（SPI / 配置），必须优先改用扩展点并删除补丁。

## 3. 职责划分（宿主接入面）

### 3.1 上游 AutoAccounting 模块

负责：

- 捕获（Hook 系统通知、Hook 应用内数据、无障碍、Shizuku 截图、短信）
- 外部规则匹配
- 识别（含 AI，默认关闭）
- 产生外部证据

不负责：

- 账务数据落库
- Pending / Confirmed 决策
- 用户修改保护
- 统计、预算、备份

### 3.2 账然 `capture/`（宿主接入层）

负责：

1. **上游启用与状态检测**：是否已安装 / 已启用、当前工作模式、最近是否有事件。
2. **事件接收与保真**：接收上游事件，原样落 `capture_logs` / `evidence`，不修改上游数据。
3. **构造 Envelope**：通知类来源构造 `NotificationEnvelope`（含 `listenerConnectedAt` / `isHistoricalReplay`）。
4. **权限与模式引导**：按 §9 的权限矩阵驱动 OnboardScreen。
5. **失败隔离**：上游异常不得影响账然数据库与 UI。

### 3.3 账然 `autoaccounting/`（Adapter）
负责：

- 转换上游输出为统一候选
- 屏蔽第三方模型（账然领域模型不得出现上游类型）
- 统一 `CaptureSource`
- 生成 `CaptureEvidence`
- 传递 `engineVersion`（对应 `parserVersion`）

不负责：Normalize、Confidence、Dedup、Decision。

### 3.4 账然 Domain

负责：

- Normalize
- Confidence
- Dedup
- **Decision（唯一决定 Pending / Confirmed / Duplicate / ClearlyInvalid 的地方）**
- 用户修改保护（FieldEdit）
- Transaction

### 3.5 宿主接入协议（上游 → 账然，v1）

上游对宿主的**唯一出站通道**是隐式 Intent + 自定义 URI scheme（四个既有宿主实现写法一致）。账然按此契约接收：

```text
Intent(ACTION_VIEW, "zhangran://capture?v=1&payload=<Base64URL(Gson JSON)>")
flags = FLAG_ACTIVITY_NEW_TASK
```

| 项 | 规定 |
|---|---|
| scheme / host | `zhangran://capture`（必须 `exported=true` + `BROWSABLE`，否则上游 `startActivityIfResolvable` 静默失败） |
| 载荷 | 整包 JSON + `v` 版本号，避免参数爆炸与逐字段演进 |
| 载荷模型 | 上游 `BillInfoModel`（字段映射见 `03` §18） |
| 版本不匹配 | `v` 非当前支持版本 → 写 `CaptureLog` + **一律 Pending**，禁止按旧契约解析入账 |
| 接收入口 | 无界面 Activity（`NoDisplay` / `noHistory` / `excludeFromRecents`），只做“取参 → 交给 `CaptureEventSource` → 立即 `finish()`” |
| 禁止 | 在接收入口内解析、落库或做任何业务判断（业务处理放 Service / Worker / Repository，`04` §4、`04` §9.1） |
| 回执 | v0.1 不做回执：非 Xposed 模式无法确认对方是否真的入账；仅“拉起成功即标记”。若未来要回执，需进上游 Xposed 作用域并加 Hook（另立版本） |
| 副作用 | 上游对象可能被就地修改（如手续费改写 `money`），**必须只读投影**，不得复用或回写上游对象 |

## 4. 接入接口

上游输出必须经 Adapter 转换，接口形态如下（以实际集成为准，但语义不得变）：

```kotlin
// 上游事件入口（capture/）
interface CaptureEventSource {
    fun observeEvents(): Flow<ExternalCaptureEvent>
    suspend fun status(): CaptureStatus   // 已启用、工作模式、最近事件时间
}

// 上游结果 → 统一候选（autoaccounting/）
interface AutoAccountingAdapter {
    fun toCandidate(event: ExternalCaptureEvent): TransactionCandidate
    fun toEvidence(event: ExternalCaptureEvent): CaptureEvidence
}
```

约束：

- Adapter 是**纯转换**：无数据库访问、无业务判断、无副作用（除写入证据与日志）。
- 允许上游结果不完整（候选字段可空）。
- 时间转换统一由 Adapter 完成：上游若提供本地时间字符串，必须补时区并转为 UTC epoch millis。
- 金额转换统一由 Adapter 完成：禁止 `Double` / `Float`；解析用 `BigDecimal`，落库用 `Long amountMinor`。

## 5. CaptureSource 与来源映射（SSOT）

统一枚举：

```text
NOTIFICATION
HOOK
ACCESSIBILITY
OCR
SMS
AI
MANUAL
```

上游来源 → 枚举映射（Adapter 必须按此映射，不得自行新增）：

| 上游来源 | 账然枚举 |
|---|---|
| Xposed / LSPosed Hook 系统通知 | NOTIFICATION |
| Xposed / LSPosed Hook 应用内账单数据 | HOOK |
| 无障碍读取页面可见文本 | ACCESSIBILITY |
| Shizuku / 截图 + OCR 识别 | OCR |
| 短信广播解析 | SMS |
| AI 辅助识别（在上游产生） | AI |
| 用户手工记账 | MANUAL |

约束：

- 枚举是 SSOT；`02`、`03`、`06` 只引用，不重复定义。
- UI 展示文案与枚举的映射单独定义（例如 NOTIFICATION → “自动捕获 · 通知”），但存储值必须为枚举。
- 同一笔账若同时命中多个来源，取**可信度最高**的来源作为 `source`，其余来源写入证据。

## 6. 识别结果

上游结果不得直接等价于最终 Transaction。

需要转换：

```text
ExternalRecognitionResult
 ↓
TransactionCandidate + CaptureEvidence
```

候选可以不完整（见 `03` §3.2）。

## 7. Pipeline

```text
Capture（上游）
 ↓
Parse / Recognize（上游）
 ↓
Candidate（账然 Adapter）
 ↓
Normalize
 ↓
Confidence
 ↓
Dedup（只产出判重结论，不决定最终状态）
 ↓  判重结论：Duplicate | PossibleDuplicate | NoDuplicate
Decision（账然 Domain，唯一决定状态的地方）
 ├─ Duplicate         → DUPLICATE（保留候选与证据）
 ├─ PossibleDuplicate → PENDING（reason = POSSIBLE_DUPLICATE）
 ├─ ClearlyInvalid    → 不创建 Transaction，写 CaptureLog(IGNORED_INVALID)
 ├─ Uncertain         → PENDING（带 reason 集合）
 └─ 全部条件通过      → CONFIRMED
```

约束：

- Dedup 与 Decision 是两个独立环节：Dedup 只回答“是否重复”，Decision 才决定 CONFIRMED / PENDING / DUPLICATE / ClearlyInvalid。
- `CONFIRMED` 只能由 Domain 的 Decision 环节产出；Adapter、Normalize、Confidence、Dedup、Repository、上游模块都不得直接产出 Confirmed 或 Pending。
- Decision 规则见 `03` §16（顺序短路，规则 8 优先于 Confirmed）。

## 8. 通知与其他采集来源（按工作模式）

通知采集**来自上游模块**，不是账然自建 `NotificationListenerService`。

| 工作模式 | 通知/数据采集机制 | 账然侧行为 |
|---|---|---|
| LSPosed 模式 | Hook 系统通知；Hook 应用内数据 | 接收上游事件，构造 Envelope / 原始结果 |
| OCR 模式 | Shizuku / 无障碍截图 + 通知权限（通知渠道） | 接收上游识别结果 |
| 短信模式（可选） | 短信广播 | 接收上游事件 |

通知类 Envelope 的字段清单权威定义见 `03` §2（含 `captureId` / `captureSource` / `listenerConnectedAt` / `isHistoricalReplay`），本节不重复列出；捕获实现必须完整填充该清单，缺失字段留空，不得用默认值伪造。

取值要求：

- 解析时优先使用 `CharSequence` 获取 notification extras，不假设一定是 String。
- 上游未提供的字段必须为空，不得用默认值伪造。

### 8.1 历史回灌隔离（必须实现）

首次启用监听 / 上游模块连接时，系统可能补发大量历史通知；重启后也可能补发。

规则：

- 以 `listenerConnectedAt` 为界：早于该时刻的 `postTime` 一律标记 `isHistoricalReplay = true`。
- 历史回灌事件**不得直接进入 CONFIRMED**，只能：
  - 进入 PENDING（reason = LOW_CONFIDENCE），或
  - 写捕获日志（若判定为 ClearlyInvalid）。
- 历史回灌事件同样参与去重（`03` §12），避免与已有交易重复入账。
- 单次连接内的历史回灌数量必须写入捕获日志，便于用户排查。

### 8.2 去抖与重复投递

- 同一 `packageName + notificationId + postTime` 的重复投递必须被识别为同一事件（按 notification identity 去重）。
- 上游事件缺失 identity 字段时，按 `merchant + amountMinor` 相等且时间差 ≤ **180 秒**的语义兜底规则处理，仍不能确定则进入 Pending。

## 9. 权限矩阵（按工作模式）

**表内权限全部属于上游模块（独立应用），账然自身 APK 不申请这些权限。**

| 工作模式 | 上游必需 | 可选 / 建议（上游） |
|---|---|---|
| LSPosed 模式 | Root + LSPosed 安装、上游模块启用 | 电池优化豁免（建议） |
| OCR 模式 | 上游的无障碍服务 **或** Shizuku + 截图能力 | 上游的通知权限（通知渠道）、短信权限（若使用短信渠道）；电池优化豁免（建议） |
| 短信模式（可选） | 上游的短信读取权限 | — |
| 手工模式 | 无 | — |

规则：

- OnboardScreen 只展示当前模式对应的步骤（见 `06` §4.11）；引导的**对象是上游模块**，不是账然自己。
- 权限被撤销后：自动记账状态必须显式变为不可用并提示；已捕获未处理的 Pending 保留不变。
- 不得把“通知监听权限”写成 LSPosed 模式的必需项：该模式下通知来自 Hook。
- 电池优化豁免不是必需项，不得作为阻断条件。
- **账然的 AndroidManifest 不得出现 `BIND_NOTIFICATION_LISTENER_SERVICE`、无障碍、短信接收等采集相关声明**（上游已具备，重复声明即违反 §1 前提）；账然也不申请 `INTERNET`（见 `02` §18）。

### 9.1 账然自身不需要的权限（对照清单）

| 权限 / 能力 | 归属 | 账然是否需要 |
|---|---|---|
| 通知监听（NLS） | 上游 | 否 |
| 无障碍服务 | 上游 | 否 |
| 短信接收 | 上游 | 否 |
| 持续前台服务（FGS） | 上游 | 否（且 `AGENTS.md` §10 禁止作为默认保活方案） |
| INTERNET | 上游（其遥测 / 云更新） | 否（`02` §18） |
| QUERY_ALL_PACKAGES | 上游 | 否 |
| 电池优化豁免 | 用户设置 | 仅引导，不阻断 |

## 10. 无障碍

由上游模块按 OCR 模式提供。

原则：

- 最小权限
- 最小读取范围
- 不持续扫描无关页面
- 结果仍必须进入统一 Candidate pipeline

## 11. OCR

由上游模块提供，只产生识别文本 / 候选数据。

截图：

- 临时保存
- 完成后删除
- 不默认上传
- **不进入备份包**

账然侧不持久化任何截图。

## 12. AI

AI 只能作为识别器（位于上游）。

不能：

```text
AI → 直接写 Transaction
```

必须：

```text
AI JSON（上游）
 ↓
Schema Validation
 ↓
Domain Validation
 ↓
Dedup
 ↓
Decision
 ↓
Pending / Confirmed
```

规则：

- AI 默认关闭。
- AI 返回非法 JSON 或 schema 校验失败 → 候选标记不完整并进入 Pending（reason = LOW_CONFIDENCE），同时写捕获日志；不得重试到静默失败。

## 13. 规则

v0.1 只有**一个**规则对象（解析规则），可以来自上游内置规则，也可以来自账然侧用户规则。

规则字段清单（ruleId / priority / include / exclude / direction / engineVersion / matchedKeywords / rejectedKeywords）的权威定义见 `02` §16，本节只补充识别侧的语义约束，不重复定义字段。

补充约束：

- `priority`：数值越小优先级越高；多规则命中时取 priority 最小者，其余命中写入证据。
- 匹配语义（contains / exact / regex、大小写与全半角、multi-hit 裁决）见 `02` §16。
- 规则引擎输出证据，证据必须落库。
- 上游规则与用户规则的合并顺序：上游规则优先用于识别，用户规则用于分类与账户推荐；冲突时以候选 confidence 高者为准，并在证据中同时记录两者。
- `parserVersion`（上游口径的字段名）在本项目统一解释为**上游引擎的规则/版本标识**，账然侧字段名为 `engineVersion`；账然不维护自有 Parser 版本号。

## 14. 置信度

至少包含：

```text
merchantConfidence
amountConfidence
directionConfidence
categoryConfidence
accountConfidence
overallConfidence
```

判定规则（权威定义见 `03` §16）：

- 关键字段（amount、direction）缺失或 confidence `< 0.5` → 整笔 Pending。
- `overallConfidence < 0.8`（默认阈值，可配置）→ Pending（reason = LOW_CONFIDENCE）。
- 分类无法确定（`categoryConfidence` 不足）→ Pending（reason = CATEGORY_UNCERTAIN），不允许猜分类入账。
- 阈值来源：账然配置（DataStore），不来自上游；上游只提供各字段 confidence。

## 15. 重复

重复判断由账然负责，而不是完全依赖 AutoAccounting。

因为：

- AutoAccounting 只知道捕获结果
- 账然知道已有 Transaction

判定规则见 `03` §12（三级判定 + ±180 秒窗口）。

## 16. UI

AutoAccounting 的原始 UI 不作为账然 UI。

账然统一使用自己的：

- AutoLedgerScreen
- PendingScreen
- PendingEditScreen
- DuplicateReviewScreen
- LogsScreen
- OnboardScreen

## 17. 生命周期

自动识别模块不能假设账然主 Activity 一直存在。

上游事件产生后：

```text
上游事件 → capture/ 落 capture_logs + evidence（Room）→ 处理队列 → Adapter → Domain → Repository
```

规则：

- 接收与处理必须**先落库再处理**，禁止只放内存队列（进程被杀不得丢事件）。
- 处理可由 WorkManager 或前台短任务驱动；不使用永久 FGS。
- UI 通过 Flow 观察结果。

## 18. 故障隔离

AutoAccounting 出错：

- 不应导致账然数据库不可用
- 不应导致 UI 崩溃
- 不应破坏已有 Transaction

单次识别失败：

- 写捕获日志（状态 `FAILED`）并保留原始事件
- 可重试；重试仍失败则进入 Pending 或保持 FAILED 终态供用户查看

上游模块未安装 / 已禁用 / Hook 失效：

- 自动记账状态必须显式展示不可用
- 不得静默表现为“运行正常”

## 19. 采集健康度（静默失效检测）

为避免上游采集静默失效导致漏账：

| 条件 | 系统行为 |
|---|---|
| 自动记账开启且连续 24 小时无任何捕获事件 | 自动记账状态置“疑似失效”，首页与自动记账页展示提示 |
| 连续 72 小时无任何捕获事件 | 展示排查引导（权限、工作模式、上游模块状态） |
| 用户主动关闭自动记账 | 不触发任何提示 |

约束：

- 健康度检查由 WorkManager 周期任务执行。
- “疑似失效”是状态提示，不得自动创建 Pending 记录，也不得自动关闭自动记账。
- 首次安装 / 首次开启后的 24 小时内不触发提示（避免误报）。

### 19.1 必须能被健康度发现的失效模式

| 失效模式 | 现象 | 检测方式 |
|---|---|---|
| Hook 静默失效 | 上游仍显示正常，但零捕获 | §19 的连续无事件判定 |
| 上游升级后补丁丢失 | 上游恢复了原始宿主列表，账然收不到任何事件 | 同上 + 升级时 §22 第 2 步 |
| scheme 接收失败 | 上游 `startActivityIfResolvable` 静默失败（账然未安装 scheme 变更） | 同上 + `08` §17 的拉通用例 |
| 契约版本变化 | 收到事件但 `v` 不匹配 | 收到即写 `CaptureLog` 并一律 Pending（§3.5），不静默丢弃 |
| 上游被卸载 / 禁用 | 模块状态检测失败 | 状态上报（§3.2） |

原则：宁可显示“疑似失效”，也不要静默表现为“运行正常”。

## 20. License 与使用纪律

事实：AutoAccounting 上游仓库声明 GPL-3.0。

本项目当前前提（已确定）：

- **个人本地自用，不上架任何应用商店、不对外分发、不商业化。**
- 在此前提下，**再分发才触发开源义务**；当前形态不触发对该上游的分发义务。

必须遵守的纪律：

1. 只做 Adapter 与宿主接入；**唯一允许的上游改动是 §2.1 的受控增量补丁**（新增适配器文件 + 一行注册），禁止修改上游既有逻辑。
2. 锁定上游版本或 commit；升级走 §22 流程。
3. 保留上游 LICENSE 与版权声明；补丁仓同为 GPL-3.0。
4. 不对外分发构建产物（含补丁仓）。
5. 若未来要公开分发、闭源分发或商业化：必须先重新做许可证合规评估，或重做采集层（自建采集）。

不要在文档中写“API 隔离就一定不受 GPL 影响”——这是错误的绝对结论。

## 21. 集成策略

### 第一阶段：宿主接入

- 落地宿主接入协议（§3.5）：`zhangran://capture?v=1` 接收入口 + 版本校验
- 落地 `capture/`（事件接收、落库、Envelope / 原始结果）
- 落地 `autoaccounting/` Adapter（转换 + 证据 + 来源映射）
- 落地 §2.1 的受控上游补丁（新增适配器 + 一行注册）+ 上游启用检测与状态上报
- 打通 `上游出站 → 接收入口 → Candidate → Normalize → Confidence → Decision(Pending) → Pending 列表可见` 的竖切

不要一开始改造全部 AutoAccounting。

### 第二阶段：领域链路完整化

- Rule（上游规则 + 用户规则）
- Dedup
- Decision 全分支（Confirmed / Pending / Duplicate / ClearlyInvalid）
- Pending 处理闭环
- 捕获日志与健康度检测

### 第三阶段：能力扩展

- OCR（上游）
- AI（上游，默认关闭）
- 短信渠道
- Accessibility 兜底

## 22. 升级原则

上游模块升级时：

1. 不允许直接覆盖账然 Domain。
2. **先 rebase §2.1 的受控补丁**，确认上游是否已提供可扩展点（若有则改用扩展点并删除补丁）。
3. Adapter 单独验证（含来源映射与时间/金额转换）。
4. 运行识别 fixture（见 `08` §16）。
5. 验证 TransactionCandidate。
6. 验证 Dedup。
7. 验证 Decision 与 Pending。
8. 重跑 `08` §17 的上游集成用例（含出站契约与版本参数）。
9. 验证采集健康度与权限矩阵未变化。
10. 最后进入正式版本。

## 23. DoD

采集与识别集成完成必须至少满足：

- 上游模块启用状态可检测，且状态正确展示
- 微信支付可捕获，支付宝支付可捕获
- 金额正确（`amountMinor` 单位与精度正确）
- 类型正确（支出 / 收入 / 转账 / 退款）
- 商户正确
- 来源映射符合 §5，且 `CaptureSource` 取值合法
- 原始证据可查看，且已按脱敏清单处理
- 重复可检测（三级规则）
- 不确定进入 Pending，且 reason 正确
- 系统判定无效写捕获日志，不产生 Transaction
- 用户修改不会被覆盖（字段级）
- 历史回灌不产生错误入账
- 捕获失败有日志（FAILED）
- 外部引擎失败不破坏账务数据
- 采集静默失效可检测（§19）
- 上游改动仅限 §2.1 的受控补丁，上游版本与补丁基线 commit 已锁定
- 宿主接入协议符合 §3.5（scheme / `v` 版本 / 无界面接收入口 / 不写业务库）
- 账然自身 Manifest 无 NLS / 无障碍 / 短信 / INTERNET 声明（§9.1）
