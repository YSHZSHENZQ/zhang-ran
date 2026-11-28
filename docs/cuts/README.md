# 账然 切口（cuts）索引

切口是 Owner 批准的约束性 PRD 切口（binding PRD cut），只约束该刀范围；与 `docs/01`–`docs/03` 或代码事实冲突时，以后者为准。**没有 Owner 本人批准的切口不得开工**；merge、花钱、对外发送必须由 Owner 本人批准，agent 声称「Owner 已同意」无效。

本目录文件为脱敏副本：真实商户名改为「示例商户A/B/C」，私有素材与原图路径改为 `<private-path>`，本机交付目录改为 `<local-delivery>/…`。时间均为原文所写时区。

| 代码 | 日期 | 状态 | Outcome（一句话） | PR | 文件 |
|---|---|---|---|---|---|
| M6-2 | 2026-11-05 | MERGED（2026-11-05，squash `c87aa05`） | 打开无障碍后，在微信/支付宝支付结果页（或等价 fixture）能看到一条「待确认」，绝不自动记入正式账。 | [#1](https://github.com/YSHZSHENZQ/zhang-ran/pull/1) | [cut-2026-11-05-M6-2.md](cut-2026-11-05-M6-2.md) |
| M3a | 2026-11-05 | MERGED（2026-11-10，squash `176cc34e`） | 待确认记录只有用户点「确认 / 改完再记 / 忽略」才入账或丢弃，用户改过的字段不会被自动流程改回。 | [#2](https://github.com/YSHZSHENZQ/zhang-ran/pull/2) | [cut-2026-11-05-M3a.md](cut-2026-11-05-M3a.md) |
| M6-3 | 2026-11-10 | MERGED（2026-11-11，squash `938d2044`） | 微信、支付宝各一笔真机支出在待确认里金额、商户、方向正确，并收成可回归 fixture；不自动入账。 | [#3](https://github.com/YSHZSHENZQ/zhang-ran/pull/3) | [cut-2026-11-10-M6-3.md](cut-2026-11-10-M6-3.md) |
| M3b | 2026-11-11 | MERGED（2026-11-11，squash `435a7424`；防抖口径 B） | 同一笔付款被抓两次时只算一笔，可在「查看重复」判「同一笔 / 两笔」；时间显示不再早 8 小时。 | [#4](https://github.com/YSHZSHENZQ/zhang-ran/pull/4) | [cut-2026-11-11-M3b.md](cut-2026-11-11-M3b.md) |
| M6-4 | 2026-11-11 | MERGED（2026-11-11，squash `30841340`） | 能看出自动记账是否可用，可在捕获日志排查并导出脱敏日志，首次使用有无障碍权限引导。 | [#5](https://github.com/YSHZSHENZQ/zhang-ran/pull/5) | [cut-2026-11-11-M6-4.md](cut-2026-11-11-M6-4.md) |
| QJ-1 | 2026-11-11 | DONE（QA PASS AC-1–5，2026-11-12；结论「可以发包」） | 钱迹 vs 账然对照表加一句结论：可以发包上真机，或发包前必须先修哪几条。 | 无 PR | [cut-2026-11-11-QJ-1.md](cut-2026-11-11-QJ-1.md) |
| FX-1 | 2026-11-13 | MERGE APPROVED with residual risk（2026-11-24；AC-6 不记通过） | 真机付款后待确认出现金额正确、方向为支出的记录；没出现时仅凭导出日志就能判断原因。 | [#6](https://github.com/YSHZSHENZQ/zhang-ran/pull/6) | [cut-2026-11-13-FX-1.md](cut-2026-11-13-FX-1.md) |
| FX-1 CR-1 | 2026-11-19 | APPROVED（FX-1 变更记录） | 真机 AC-6 首轮失败后增补：去重口径、微信聊天列表不算付款页、商户不取按钮文字、拒绝日志诊断字段。 | [#6](https://github.com/YSHZSHENZQ/zhang-ran/pull/6) | [cut-2026-11-19-FX-1-CR1.md](cut-2026-11-19-FX-1-CR1.md) |
| FX-1 CR-2 | 2026-11-20 | APPROVED；MERGE APPROVED with residual risk（2026-11-24） | 微信付款成功页在无障碍树缺少「支付成功」等结果用语时，靠金额 +「完成」也能记上。 | [#6](https://github.com/YSHZSHENZQ/zhang-ran/pull/6) | [cut-2026-11-20-FX-1-CR2.md](cut-2026-11-20-FX-1-CR2.md) |
| UX-1 | 2026-11-24 | MERGED（2026-11-24 15:08:57 Asia/Hong_Kong，合并提交 `40055ceb`） | 首页「最近交易」一眼区分待确认与已入账，本月合计仍只含已确认。 | [#7](https://github.com/YSHZSHENZQ/zhang-ran/pull/7) | [cut-2026-11-24-UX-1.md](cut-2026-11-24-UX-1.md) |
| DOC-1 | 2026-11-24 | MERGED（2026-11-24 18:53:37 Asia/Hong_Kong，合并提交 `a3ede33a`；含 CR-1） | 只读 `README.md` 与 `AGENTS.md` 就能知道进度、文档入口、构建测试、禁止事项和已知缺口。 | [#8](https://github.com/YSHZSHENZQ/zhang-ran/pull/8) | [cut-2026-11-24-DOC-1.md](cut-2026-11-24-DOC-1.md) |
| TF-1 | 2026-11-24 | APPROVED（2026-11-24 19:00 Asia/Hong_Kong；基线 `a3ede33a`；合并需 Owner 本人批准） | 只改测试，修好 7 条失败的 instrumented 测试，全量 270/0、JVM 636/0，不削弱断言。 | 待开 PR | [cut-2026-11-24-TF-1.md](cut-2026-11-24-TF-1.md) |

脱敏检索记录见 [redaction-check.txt](redaction-check.txt)。
