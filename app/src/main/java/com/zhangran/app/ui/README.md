# ui —— UI 层

职责（`docs/04` §3、§4）：

- 只负责展示状态、接收用户操作、发送事件；
- **不直接**访问 DAO / 修改数据库 / 执行业务规则；
- 不自行计算相似度、置信度、状态（只展示 Domain 给出的结论）。

子包：

- `theme/`：设计 token 唯一落点（见同目录 README）
- `components/`：可复用组件（`docs/06` §2）
- `navigation/`：路由常量与 NavHost
- `screen/`：18 个页面（见同目录 README）

状态管理约定（`docs/04` §16）：

- `UiState`：不可变数据类，页面展示所需的全部状态；可序列化以支持进程重建恢复。
- `UiEvent`：用户操作（点击、输入、提交）的封装，由 Composable 发往 ViewModel。
- `UiEffect`：一次性事件（导航、Toast、Snackbar），**不承载业务状态**。
- Composable 只接收需要的 state 与 event，**不接收整个 ViewModel**（`docs/04` §4）。
- 业务状态不得存放在 Activity 或 Composable 局部变量中。

本任务（T1b）只建立包结构与约定，**不实现任何 ViewModel**。