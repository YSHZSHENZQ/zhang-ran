# 07 自动记账与 AutoAccounting 集成

版本：v0.1  
定位：本文件是采集与识别侧（CaptureSource、工作模式、权限、上游接入边界）的 Single Source of Truth。领域规则见 `03`，层边界见 `04`，产品行为见 `02`。

## 1. 目标与已定前提

**v0.1 的采集层复用 AutoAccounting 的模块化核心（自用、不分发），账然不另行自建采集实现。**

上游的可行性结论、宿主接入契约事实、输出模型关键事实与版本锚点见 `10_AutoAccounting接入结论.md`；本文件定义规则与边界，`10` 记录结论与硬事实。

账然负责：

- 产品 UI
- 采集接入与适配（`capture/`、`autoaccounting/`）
- 领域模型与领域决策
- 最终交易
- Pending
- Dedup
- 统计
- 预算
- 备份
- 用户修改保护

AutoAccounting 的采集与识别核心提供基于 Xposed / LSPosed、无障碍、Shizuku 等方式的账单采集与识别能力，覆盖通知、短信、OCR / 屏幕识别以及规则 / AI 辅助等方向；项目许可证为 GPL-3.0。按 D2/D6（`review/DEV_PLAN_V2.md`），该核心**以 Gradle 模块形式内嵌于账然 APK**（含其 UI 代码），不再是独立安装的应用。

推论（本文件后续章节均以此为前提）：

- 账然**不实现**第二套采集实现；Android 侧 `NotificationListenerService` 不是 v0.1 的采集主体。
- 采集核心**内嵌于账然 APK**（Gradle 模块），账然负责领域模型、领域决策、UI 与账务；两者同进程、同 APK，但**领域边界仍由接口约束**（见 §3.5）。
- 账然的职责边界到“接收采集核心产出 → 生成候选与证据 → 领域决策”为止。
- 内嵌不等于可直接落库：采集核心**不得**持有账然 Repository / DAO 引用，也**不得**直接写 `transactions` 或决定 Pending / Confirmed。

## 2. 核心原则

不要：

```text
AutoAccounting 采集核心 → 直接写账然 Room
AutoAccounting 采集核心 → 决定 Pending / Confirmed
账然 → 自建第二套采集实现
```

应该：

```text
AutoAccounting 采集核心（内嵌 Gradle 模块：采集 / 外部规则 / 识别 / 证据）
      ↓  仅经进程内接口（§3.5）
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

### 2.1 受控上游补丁（已废弃）

**本节已废弃**（单 APK 方案下不再需要宿主适配器与注册补丁）。采集核心作为 Gradle 模块内嵌于账然 APK，版本管理见 §22。

> 历史说明：本节原规定"新增一个 `IAppAdapter` 适配器文件 + 在 `adapterList()` 追加一行注册"，属双 APK（宿主 + 外部插件）时期的唯一允许改动方式。该方式随宿主关系一起取消。

## 3. 职责划分（内嵌边界）

### 3.1 内嵌采集核心（AutoAccounting，Gradle 模块）

**本节已废弃**（v2 修订）：原"上游 AutoAccounting 模块（独立应用）"的职责描述已由 §3.2/§3.3 的边界取代。采集核心以 Gradle 模块内嵌于账然 APK，其内部实现不属账然领域模型；账然只依赖其**公开产出**（见 §3.5）。

### 3.2 账然 `capture/`（采集接入层）

负责：

1. **采集核心初始化与状态检测**：识别服务可用性、当前工作模式、最近是否有事件。
2. **事件接收与保真**：接收采集核心产出，原样落 `capture_logs` / `evidence`，不修改其数据。
3. **构造 Envelope**：通知类来源构造 `NotificationEnvelope`（含 `listenerConnectedAt` / `isHistoricalReplay`）。
4. **权限与模式引导**：按 §9 的权限矩阵驱动 OnboardScreen（引导对象是**本应用**）。
5. **失败隔离**：采集核心异常不得影响账然数据库与 UI。

### 3.3 账然 `autoaccounting/`（Adapter）
负责：

- 转换采集核心产出为统一候选
- 屏蔽上游模型（账然领域模型不得出现采集核心的类型）
- 统一 `CaptureSource`
- 生成 `CaptureEvidence`
- 传递 `engineVersion`（对应上游口径字段名 `parserVersion`）

不负责：Normalize、Confidence、Dedup、Decision。

### 3.4 账然 Domain

负责：

- Normalize
- Confidence
- Dedup
- **Decision（唯一决定 Pending / Confirmed / Duplicate / ClearlyInvalid 的地方）**
- 用户修改保护（FieldEdit）
- Transaction

### 3.5 采集层 → 领域层 进程内接口（v1）

**单 APK 方案下不再存在 URI scheme 与 Intent 契约**：采集核心与账然在**同一进程**内，接口是**进程内调用**，不是隐式 Intent。原 `zhangran://capture` 接收入口（无界面 Activity）已在 T4 之后按 D7 删除。

数据流（唯一通道）：

```text
采集核心产出（上游 BillInfoModel 等原始结果）
      ↓  采集接入层 capture/（保真：落 capture_logs / evidence；构造 Envelope）
      ↓  进程内接口（CaptureEventSource）
autoaccounting/ Adapter（纯转换：屏蔽上游模型 → TransactionCandidate + CaptureEvidence）
      ↓
账然 Domain Pipeline（Normalize → Confidence → Dedup → Decision）
```

| 项 | 规定 |
|---|---|
| 接口形态 | 进程内接口（`CaptureEventSource`），**无** scheme / Intent / Base64URL 载荷 / `v` 版本包 |
| 采集核心产出 | 其原始结果对象（如 `BillInfoModel`，字段事实见 `10` §5、映射见 `03` §18） |
| 保真落库 | 采集接入层把原始结果落 `capture_logs` / `evidence`（`rawCaptureRef` 必须按 `02` §20 脱敏） |
| 校验 / 解码 / 脱敏 | `CaptureProcessor` 的**校验、脱敏**逻辑保留；**去除** URI 解析与 `payload` / `v` 版本包装（随契约消失） |
| Adapter 职责 | 仍是**纯转换**：不做 Normalize / Confidence / Dedup / Decision（决策只在 Domain，§3.4） |
| 禁止 | 采集核心直接写 `transactions`、直接持有账然 DAO / Repository、直接决定 Pending / Confirmed |
| 副作用 | 上游对象可能被就地修改（如手续费改写 `money`），**必须只读投影**，不得复用或回写 |
| 回执 | v0.1 不做回执（同进程下无"对方是否入账"的不确定性） |
| 版本不匹配 | 由接口版本常量校验（`03` §19 的 `collectorApiVersion`，是否保留待 U0 裁决）；不匹配 → 写 `CaptureLog` + **一律 Pending** |

## 4. 接入接口

采集核心产出必须经 Adapter 转换，接口形态如下（以实际集成为准，但语义不得变）：

```kotlin
// 采集核心产出入口（capture/）：进程内接口，不再是 Intent / URI
interface CaptureEventSource {
    fun observeEvents(): Flow<ExternalCaptureEvent>
    suspend fun status(): CaptureStatus   // 采集核心可用性、工作模式、最近事件时间
}

// 采集核心原始结果 → 统一候选（autoaccounting/）
interface AutoAccountingAdapter {
    fun toCandidate(event: ExternalCaptureEvent): TransactionCandidate
    fun toEvidence(event: ExternalCaptureEvent): CaptureEvidence
}
```

约束：

- `ExternalCaptureEvent` 承载的是**采集核心产出的原始结果**（如 `BillInfoModel` 及其上下文），**不再**是 URI 原始字符串；不涉及 `Intent`、`data` URI 或 `payload` / `v` 版本包（见 §3.5）。
- Adapter 是**纯转换**：无数据库访问、无业务判断、无副作用（除写入证据与日志）。
- 允许采集核心结果不完整（候选字段可空）。
- 时间转换统一由 Adapter 完成：采集核心若提供本地时间字符串，必须补时区并转为 UTC epoch millis。
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

可信度顺序（高 → 低，用于多来源命中时选 `source`）：

MANUAL > NOTIFICATION > HOOK > SMS > ACCESSIBILITY > AI > OCR

- 该顺序是 `source` 选择的唯一依据；其余命中来源必须写入证据。
- 顺序是 SSOT 的一部分，修改需走文档变更流程；实现不得自行调整。
- 未命中的未知来源不得回退到某个枚举（见本节映射表）：应保留原始值并进入 Pending 建议，
  不得静默归类。

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

通知采集**来自内嵌的采集核心**，不是账然自建 `NotificationListenerService`。

| 工作模式 | 通知/数据采集机制 | 账然侧行为 |
|---|---|---|
| ~~LSPosed 模式~~ | Hook 系统通知；Hook 应用内数据 | **内嵌后不支持**（见下） |
| OCR 模式 | Shizuku / 无障碍截图 + 通知权限（通知渠道） | 接收采集核心识别结果 |
| 短信模式（可选） | 短信广播 | 接收采集核心事件 |
| 手工模式 | 用户手工记账 | 直接构造候选（`CaptureSource = MANUAL`） |

**Xposed / LSPosed 模式在内嵌后不支持**：采集核心库化时已剥离 Xposed 身份（D6-b），且目标设备无 Root（见 `DEVICE_ENVIRONMENT.md`）；因此该模式不得作为验收路径，也不得伪报通过。

通知类 Envelope 的字段清单权威定义见 `03` §2（含 `captureId` / `captureSource` / `listenerConnectedAt` / `isHistoricalReplay`），本节不重复列出；捕获实现必须完整填充该清单，缺失字段留空，不得用默认值伪造。

取值要求：

- 解析时优先使用 `CharSequence` 获取 notification extras，不假设一定是 String。
- 采集核心未提供的字段必须为空，不得用默认值伪造。

### 8.1 历史回灌隔离（必须实现）

首次启用监听 / 采集核心连接时，系统可能补发大量历史通知；重启后也可能补发。

规则：

- 以 `listenerConnectedAt` 为界：早于该时刻的 `postTime` 一律标记 `isHistoricalReplay = true`。
- 历史回灌事件**不得直接进入 CONFIRMED**，只能：
  - 进入 PENDING（reason = LOW_CONFIDENCE），或
  - 写捕获日志（若判定为 ClearlyInvalid）。
- 历史回灌事件同样参与去重（`03` §12），避免与已有交易重复入账。
- 单次连接内的历史回灌数量必须写入捕获日志，便于用户排查。

### 8.2 去抖与重复投递

- 同一 `packageName + notificationId + postTime` 的重复投递必须被识别为同一事件（按 notification identity 去重）。
- 采集核心事件缺失 identity 字段时，按 `merchant + amountMinor` 相等且时间差 ≤ **180 秒**的语义兜底规则处理，仍不能确定则进入 Pending。

## 9. 权限矩阵（按工作模式）

**单 APK 方案下，采集核心内嵌于账然 APK，因此下列权限全部由账然 APK 自身声明**（不再存在"由上游独立应用声明"的情形）。

| 工作模式 | 账然必需 | 可选 / 建议 |
|---|---|---|
| 通知渠道（基础） | `POST_NOTIFICATIONS`；前台服务 `FOREGROUND_SERVICE`（含 `FOREGROUND_SERVICE_SPECIAL_USE`） | 电池优化豁免（建议） |
| 识别服务通信（D1） | **`INTERNET`**——**仅用于与 `127.0.0.1` 本机识别服务的回环通信**，不用于上传账务数据 | — |
| 悬浮窗 / 记账面板（D3） | `SYSTEM_ALERT_WINDOW`（保留悬浮窗时必需） | `VIBRATE` |
| OCR 模式 | 内嵌采集核心的无障碍服务 **或** Shizuku + 截图能力（无需账然额外声明权限；Shizuku 由用户授权） | — |
| 短信模式（可选） | `RECEIVE_SMS` / `READ_SMS` | — |
| 应用识别（可选） | `QUERY_ALL_PACKAGES`（用于识别来源应用） | — |
| 保活 / 定时（可选） | `RECEIVE_BOOT_COMPLETED` | `SCHEDULE_EXACT_ALARM` |
| 手工模式 | 无 | — |

规则：

- OnboardScreen 只展示当前模式对应的步骤（见 `06` §4.11）；引导的**对象是本应用**（内嵌采集核心已随包安装，不存在"外部模块未启用"）。
- 权限被撤销后：自动记账状态必须显式变为不可用并提示；已捕获未处理的 Pending 保留不变。
- 不得把"通知监听权限"写成 LSPosed 模式的必需项：该模式在内嵌后不支持（见 §8）。
- 电池优化豁免不是必需项，不得作为阻断条件。
- 账然 APK **必须**声明 `INTERNET`（D1：识别经本机回环）；隐私说明须写明"仅本机回环，账务数据不外传"。
- 采集类能力（通知监听服务 / 无障碍服务 / 短信接收）由内嵌采集核心在**同一 APK 内**声明与持有，账然领域层不得自行再建一套。

### 9.1 账然不需要的权限 / 能力（对照清单）

| 权限 / 能力 | 归属 | 账然是否需要 |
|---|---|---|
| 通知监听（NLS） | 内嵌采集核心（同 APK） | 领域层不需要；由采集核心持有 |
| 无障碍服务 | 内嵌采集核心（同 APK） | 领域层不需要；由采集核心持有 |
| 短信接收 | 内嵌采集核心（同 APK） | 领域层不需要；由采集核心持有 |
| 持续前台服务（FGS） | 内嵌采集核心（同 APK） | 采集需要（见 §9 矩阵）；**不得**作为默认保活方案（`AGENTS.md` §10） |
| INTERNET | **账然自身** | **是**（D1：仅本机回环，不外联） |
| QUERY_ALL_PACKAGES | 内嵌采集核心（同 APK） | 领域层不需要；识别来源应用时由采集核心使用 |
| 电池优化豁免 | 用户设置 | 仅引导，不阻断 |

说明：

- 本表区分的是"**账然领域层是否需要**"，不是"APK 是否声明"——单 APK 下所有声明都在账然 Manifest 里（见 §9 矩阵）。
- 账然**不**自建第二套采集实现（§2）；领域层不得直接依赖采集核心的 NLS / 无障碍 / 短信能力。

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

按 D2/D3：采集核心整包内嵌（**含其 UI 代码**），其 UI **仅在记账面板 / 悬浮窗这一处**作为 Pending 确认与编辑入口出现。

边界（D3-边界，必须遵守）：

- 账然导航图**不得引用**采集核心的 UI 类；
- 采集核心 UI 不得成为账然的页面入口（唯一例外是悬浮窗 / 记账面板）；
- 上游 UI 暂作死代码存在，瘦身后置（D2）。

账然统一使用自己的：

- AutoLedgerScreen
- PendingScreen
- PendingEditScreen（**本期不实现**，见 `06` §4.7：确认与编辑由采集核心的记账面板承担）
- DuplicateReviewScreen
- LogsScreen
- OnboardScreen

## 17. 生命周期

自动识别模块不能假设账然主 Activity 一直存在。

采集核心事件产生后：

```text
采集核心事件 → capture/ 落 capture_logs + evidence（Room）→ 处理队列 → Adapter → Domain → Repository
```

规则：

- 接收与处理必须**先落库再处理**，禁止只放内存队列（进程被杀不得丢事件）。
- 提交必须走**应用级作用域**（不得用 Activity / UI 作用域提交，否则 `finish()` 会取消在途落库导致静默丢事件——`docs/09` §7 已记录该坑）。
- 处理可由 WorkManager 或前台短任务驱动；不使用永久 FGS。
- UI 通过 Flow 观察结果。

## 18. 故障隔离

采集核心出错：

- 不应导致账然数据库不可用
- 不应导致 UI 崩溃
- 不应破坏已有 Transaction

单次识别失败：

- 写捕获日志（状态 `FAILED`）并保留原始事件
- 可重试；重试仍失败则进入 Pending 或保持 FAILED 终态供用户查看

采集核心不可用 / Hook 失效 / 识别服务未启动：

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
- 内嵌后账然**是 GPL-3.0 衍生作品**；在"仅自用、不再分发"前提下不触发分发义务。

必须遵守的纪律（按 D5）：

1. 采集核心以 **Gradle 模块内嵌**；**记录来源与 commit**（版本锚点见 `10`）；升级走 §22。
2. 随包**保留上游 `LICENSE` 与版权声明**（含其依赖的第三方许可）。
3. 不对外分发构建产物（账然 APK 含 GPL-3.0 代码）。
4. 若未来要公开分发、闭源分发或商业化：必须先重新做许可证合规评估（内嵌 GPL-3.0 代码将使整个 APK 受 GPL 约束），或重做采集层（自建采集）。
5. 不得以"模块化 / 接口隔离"为由宣称不受 GPL 影响。

## 21. 集成策略

### 第一阶段：采集核心库化与编译打通（D6-a / D6-b）

- 上游 `:app` 整体降为 library 并编译通过（D6-a）
- 剥离 Xposed 身份（D6-b）：移除 Xposed 入口与依赖声明，确认无 Root 环境可运行
- 记录来源 commit 与许可文件（§20）

### 第二阶段：进程内接入与领域链路（U0 / U2）

- 冻结**进程内接口**（§3.5）：采集核心产出 → `capture/` → `autoaccounting/` Adapter
- 落地 `capture/`（事件接收、落库、Envelope / 原始结果）
- 落地 `autoaccounting/` Adapter（转换 + 证据 + 来源映射）
- 打通 `采集核心产出 → 采集接入层 → Candidate → Normalize → Confidence → Decision(Pending) → Pending 列表可见` 的竖切

### 第三阶段：权限合并、Room 隔离、UI 处置（U3 / U4 / U5）

- 权限合并：按 §9 权限矩阵合并账然 Manifest（含 `INTERNET`、`POST_NOTIFICATIONS`、FGS）
- Room 隔离（D4）：两套库文件独立、互不 import、互不查询；备份只覆盖账然账务数据
- UI 处置：OCR（内嵌采集核心）/ AI（内嵌，默认关闭）；悬浮窗与记账面板作为 Pending 确认入口；账然导航图不得引用采集核心 UI 类（§16）

不要一开始改造全部 AutoAccounting。

## 22. 升级原则

**采集核心版本合并**（不再是"上游模块升级 + Adapter 单独验证"）：

1. 不允许直接覆盖账然 Domain。
2. 记录新的来源 commit 与版本锚点（`10`）；确认其许可与身份声明未变化。
3. 重跑 `08` §17.2 的字段与类型映射用例（`03` §18）。
4. 运行识别 fixture（见 `08` §16）。
5. 验证 TransactionCandidate。
6. 验证 Dedup。
7. 验证 Decision 与 Pending。
8. 重跑 `08` §17 的采集核心集成用例（进程内接口）。
9. 检查 `07` §5 的映射表是否需扩充（新增来源取值时必须走文档变更流程，不得自行新增枚举值）。
10. 验证采集健康度（§19）与权限矩阵（§9）未变化。
11. 最后进入正式版本。

## 23. DoD

采集与识别集成完成必须至少满足：

- 内嵌采集核心的初始化与可用性检测正确，且状态正确展示
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
- 采集核心改动仅限"作为 Gradle 模块内嵌"，无独立补丁仓
- 进程内接口符合 §3.5（无 URI / Intent / `v` 版本包；采集核心不直接落业务库）
- 账然自身 Manifest 的权限与 §9 矩阵逐项一致（**含 `INTERNET`**，D1：仅本机回环）；无重复的采集类服务声明
