# data —— 数据层

职责（`docs/04` §3、§6）：Room、DAO、Repository、Data Mapper、DataStore（仅设置项）、
备份文件的读写与校验。

子包：

- `local/`：Room 数据库、DAO、DataStore
- `repository/`：数据访问边界（UI / Domain 只经过 Repository）
- `mapper/`：实体 ↔ 领域模型映射

约束：

- Room 是本地事实来源；schema 变化必须有 migration（`docs/04` §7）；
- 上游采集模块**不得**持有本层引用（`docs/07` §2）。

本任务只建包与说明，**不建任何实体 / DAO**（属 T2）。