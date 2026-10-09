# AGENTS.md

## 1. 工作原则

你正在开发“账然”，一个 Android 本地优先自动记账应用。

必须遵守：

- 不猜业务规则。
- 不自行扩展产品范围。
- 不因为实现方便而改变领域模型。
- 不让 UI 直接操作数据库。
- 不让自动识别引擎直接修改最终 Transaction。
- 不覆盖用户已经手工修改的数据。
- 无法可靠判断时进入 Pending。
- 不为了“自动化率”牺牲账务正确性。
- 大改动前先检查相关文档。

核心原则：

> 宁可进入 Pending，也不要猜错并自动入账。

## 2. 按任务读取文档

所有任务：

- README.md
- AGENTS.md
- docs/09_总结经验教训.md

UI / Compose：

- docs/05_UI设计系统.md
- docs/06_UI组件与页面规格.md

产品行为：

- docs/01_产品需求.md
- docs/02_产品规格.md

领域 / 数据：

- docs/02_产品规格.md
- docs/03_领域模型与业务规则.md

架构：

- docs/03_领域模型与业务规则.md
- docs/04_技术架构.md

自动记账：

- docs/03_领域模型与业务规则.md
- docs/04_技术架构.md
- docs/07_自动记账与AutoAccounting集成.md
- docs/10_AutoAccounting接入结论.md

测试：

- 对应功能文档
- docs/08_测试与验收标准.md

不要无条件读取全部文档。

## 3. 技术约束

- Kotlin
- Jetpack Compose
- Material 3
- Android API 36
- minSdk 31
- Room
- Hilt
- Coroutines / Flow / StateFlow
- WorkManager

采用分层架构和单向数据流。

推荐关系：

UI → ViewModel / StateHolder → UseCase / Domain → Repository → Room

UI 不直接访问 DAO。

## 4. 自动记账约束

自动识别数据必须经过：

Capture
→ Candidate
→ Normalize
→ Confidence
→ Dedup
→ Decision（Pending / Confirmed）
→ Transaction

说明：

- v0.1 的 Capture 与识别来自外部上游模块（AutoAccounting，仅个人自用、不分发），账然不另行自建采集实现。
- 外部上游模块只能经 Adapter 交出候选与证据，不得直接写 Transaction，也不得决定 Pending / Confirmed。
- 识别引擎不能直接写最终 Transaction。

## 5. 数据安全

金额使用最小货币单位：

`Long amountMinor`

解析金额时允许使用 BigDecimal，但最终不得使用 Double / Float 保存金额。

交易金额与交易方向分离：

- EXPENSE
- INCOME
- TRANSFER
- REFUND

## 6. 用户修改保护

任何用户主动修改后的字段都必须保留用户修改结果。

自动规则重新运行不得无条件覆盖用户修改。

## 7. UI 规则

HTML 原型是视觉参考。

页面规格是行为参考。

组件应优先复用。

普通信息：

- 点击字段本身修改。

不要同时提供：

- 可点击字段
- 旁边重复的“编辑”按钮

除非该按钮具有明确独立意义。

## 8. 修改规模

以下修改必须先说明影响范围：

- 修改核心领域模型
- 修改 Transaction 状态
- 修改自动记账 pipeline
- 修改数据库结构
- 修改去重策略
- 引入新的核心依赖
- 修改超过 10 个核心文件
- 改变 AutoAccounting 集成边界

## 9. 完成标准

完成代码不等于完成任务。

至少需要：

- 编译通过
- 单元测试通过
- 关键业务路径验证
- UI 状态验证
- 数据库 migration 验证
- 自动记账关键路径验证

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

## 11. 文档纪律、变更流程与经验记录

### 11.1 文档引用纪律

- **`review/` 下的文件仅作指导，不是冻结文档，且不属于本仓库提交对象。**
- **禁止**在 `README.md`、`AGENTS.md`、`docs/**` 中引用 `review/` 下的任何文件。
- 任何需要长期引用的结论，必须先转成 `docs/` 下的正式文档（如 `docs/10_AutoAccounting接入结论.md`），再被引用。
- 除 `docs/09`、`docs/10` 外，`docs/01`–`docs/08`、`README.md`、`AGENTS.md` 在编码期间视为冻结输入。

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

必须写提案的典型场景：同一事实在两份文档取值不同、`§` 引用失效、`docs/08` 某条判据无法判定、上游能力与 `docs/07` §9 权限矩阵不符。

### 11.3 经验记录

文件：`docs/09_总结经验教训.md`

规则：

- 遇到会阻塞工作、导致文件/数据损坏、或反复出现的问题，解决后必须把“问题 + 对策”追加到该文件。
- **只加不改**：只能在末尾追加新条目，不修改、不删除已有条目，不重排编号。
- 只记关键且重要的问题；小问题不记。
- 每条写成最短直白的两行：一行问题、一行对策；不写背景、推理与代码示例。
- 动手前先读该文件，避免重复踩坑。
