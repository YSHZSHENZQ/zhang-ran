此目录为账然 Android 工程根目录（app 模块）。

状态：v0.1 文档基线冻结后，编码尚未开始，本目录刻意保持为空。

编码开始后，预期结构（见 `docs/04_技术架构.md` §3）：

```text
app/
├── src/main/java/<package>/
│   ├── ui/            // theme / components / screen
│   ├── domain/        // model / service（Normalize、Confidence、Dedup、Decision）/ usecase
│   ├── data/          // local(Room/DataStore) / repository / mapper
│   ├── capture/       // 接收上游采集事件
│   ├── autoaccounting/ // 上游结果 → TransactionCandidate + Evidence 的 Adapter
│   ├── backup/        // 备份导出/导入、恢复三模式、版本迁移
│   ├── log/           // 捕获日志与证据
│   └── common/
└── src/main/res/
```

约束：Gradle 工程骨架应在 `app/` 与工作区根目录同时落地（根目录放 `settings.gradle.kts`、`build.gradle.kts`、`gradle/`、`gradlew`）。
