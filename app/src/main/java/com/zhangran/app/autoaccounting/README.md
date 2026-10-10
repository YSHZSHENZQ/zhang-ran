# autoaccounting —— 上游结果适配（Adapter）

职责（`docs/04` §3、`docs/07` §3.3）：把上游结果转换为统一候选与证据 —— `TransactionCandidate`
+ `CaptureEvidence`，统一 `CaptureSource`，屏蔽第三方模型，传递 `engineVersion`。

约束：

- 纯转换：无数据库访问、无业务判断、无副作用（除写入证据与日志）；
- **不做** Normalize / Confidence / Dedup / Decision（`docs/03` §15）；
- 上游对象可能被就地修改（如手续费改写金额），必须只读投影（`docs/10` §5）。

本任务只建包与说明。