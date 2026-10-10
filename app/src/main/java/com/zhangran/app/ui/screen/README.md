# ui/screen —— 页面

依据：`docs/06_UI组件与页面规格.md` §1（目录）、§4（18 个页面）。

本任务（T1b）共 18 个**占位页面**，每个只显示页面名称：

| 分组 | 页面 |
|---|---|
| 底部导航（5） | `home`、`stats`、`add`、`budget`、`mine` |
| 其余（13） | `detail`、`pending`、`pendingEdit`、`duplicateReview`、`autoLedger`、`onboard`、`categories`、`accounts`、`rules`、`backup`、`logs`、`search`、`about` |

规则：

- 占位页只显示名称：**不得**写业务规则、假数据、真实金额或原型里的示例数字（`06` §7）；
- 尺寸 / 颜色只引用 `ui/theme` 的 token；
- 统一占位实现见 `PlaceholderScreen.kt`，真实页面实现后应删除该文件。