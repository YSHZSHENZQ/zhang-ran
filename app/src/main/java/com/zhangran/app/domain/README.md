# domain —— 领域层

职责（`docs/04` §3、§5）：Transaction 业务规则、Normalize、Confidence、Dedup、
**Decision（唯一决定 Pending / Confirmed / Duplicate / ClearlyInvalid 的地方）**、Refund、
Transfer、用户修改保护（FieldEdit）、统计口径计算。

子包：

- `model/`：领域对象（`docs/03` §5–§11）
- `service/`：Normalize / Confidence / Dedup / Decision
- `usecase/`：面向 UI 的用例入口

约束：

- 不依赖 Compose 与 Android framework 类型；
- 决策只发生在 Decision 环节（`docs/03` §16），其他层不得决定状态。

## 决策与判重的唯一落点（M1a）

- `service/Dedup.kt`：只产出**判重结论**（`None` / `Duplicate` / `PossibleDuplicate`；
  另有 §12.1 捕获级去抖 `SameEvent` / `PossibleSameEvent` / `DifferentEvent`），
  **不产出任何交易状态**，也不得访问 DAO / Room / Repository（既有交易由调用方传入）。
- `service/Decision.kt`：`docs/03` §16 的**唯一**决策落点，产出
  `Confirmed` / `Pending(reasons)` / `Duplicate` / `ClearlyInvalid`
  （结果类型见 `model/Decision.kt`）。
- 阈值（整体 0.8、字段 0.5）通过 `Decision` 构造参数注入；
  `PendingReason` 的全量取值在 `model/Enums.kt`（`docs/02` §4，10 项，不得扩展）。
- 被保护字段（`docs/03` §11）只记录冲突、保留用户值，**不得改写候选**。
