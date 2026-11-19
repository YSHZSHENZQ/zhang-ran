# AC-9 会话列表不进待确认，拒绝日志只留结构

功能提交 `6d5f42c612db0768773fbf029349b240c9e08dec`。

`微信会话列表不入库并留下不含正文的拒绝日志` 通过。断言摘录：

```text
assertEquals(0, harness.transactions.countByState(TransactionState.PENDING))
assertEquals("NOT_PAYMENT_PAGE", log.reason)
assertTrue(exported.contains("\"windowId\":44349"))
assertTrue(exported.contains("\"rootClass\":\"android.widget.FrameLayout\""))
assertTrue(exported.contains("com.tencent.mm:id/jga"))
assertTrue(exported.contains("\"nodeCount\":4"))
assertTrue(exported.contains("\"hasAmountStyle\":true"))
assertTrue(exported.contains("\"hasPaymentKeyword\":true"))
listOf("王小明", "晚上吃饭", "更多功能", "头像", "3.90", "3.9", "390").forEach { secret ->
    assertFalse(exported.contains(secret))
    assertFalse(log.rawCaptureRef.orEmpty().contains(secret))
}
assertTrue(CaptureLogExport.sensitiveFindings(exported).isEmpty())
```

`同一会话列表隔七十分钟仍不产生待确认` 也通过：待确认 0，两条都是 `NOT_PAYMENT_PAGE`。隔了 70 分钟，所以 60 秒限流放行第二条。

仪器测试导出（`ac2-export-before.json`、`ac3-wrong-window-logs.json`、`ac4-export.json`）里，每条拒绝日志都有 `windowId`、`rootClass`、`viewIds`、`nodeCount`、`hasAmountStyle`、`hasPaymentKeyword`。有非空取值的只有 `fx-1-f-wrong-window`：`windowId` 38598，`viewIds` 为 `[]`，`nodeCount` 20，两个布尔都是 false，`rootClass` 仍是 null。没有页面快照的五种拒绝（`NO_MATCHING_WINDOW`、`NOT_PAYMENT_PAGE`、`WINDOW_NULL`、`NODE_EMPTY`、`SWITCH_OFF`）以及无快照的 `fx-1-WRONG_WINDOW`，这六个字段都是 null。带证据的 `NOT_PAYMENT_PAGE` 由上面这条 JVM 单测证明，不在这次仪器导出里。

真机回放见 `replay-summary-6d5f42c6.txt`。微信会话列表 `windowId` 44349 共 31 份，回放后待确认 0 条。拒绝日志只有 `NOT_PAYMENT_PAGE`：写下 8 条，另外 23 条落在 60 秒限流内，记为 `REJECTED_LOG_RATE_LIMITED`，没有再写一条。方法和限制与 `ac8-stay.md` 相同。真机原始快照和数据库不进仓库。
