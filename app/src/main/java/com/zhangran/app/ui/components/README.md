# ui/components —— 可复用组件

依据：`docs/06_UI组件与页面规格.md` §2、§3。

规划（本任务只建包与说明，不实现）：

- Common：`ZhangranCard`、`ZhangranSection`、`ZhangranSettingRow`、`ZhangranClickableRow`、
  `ZhangranSwitchRow`、`ZhangranBadge`、`ZhangranAmount`、`ZhangranEmptyState`、
  `ZhangranErrorState`、`ZhangranLoadingState`、`ZhangranDialog`、`ZhangranBottomSheet`、
  `ZhangranFieldRow`
- Transaction：`TransactionRow`、`TransactionAmount`、`TransactionStatus` 等
- AutoLedger / Pending：`ConfidenceBadge`、`PendingActionBar` 等（`docs/06` §2）

约束（`docs/06` §3）：

- 接收最小必要参数；不持有业务数据源；不访问 ViewModel / DAO；
- 不包含产品业务规则；不自行计算相似度 / 置信度 / 状态。

本任务允许实现 1–2 个纯展示组件用于验证 token 可用；带业务语义的组件不得实现。