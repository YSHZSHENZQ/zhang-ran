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

本任务只建包与说明，**不放任何类**。