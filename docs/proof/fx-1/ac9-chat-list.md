# AC-9 会话列表不进待确认，拒绝日志只留结构

功能提交 `6d5f42c612db0768773fbf029349b240c9e08dec`。

来源仍是 JVM `AccessibilitySnapshotIngestTest`，不是模拟器上导出的文件。本环境的模拟器停在 adb offline，没有生成新的导出 JSON。

`微信会话列表不入库并留下不含正文的拒绝日志`：通过。待确认 0 条，原因 `NOT_PAYMENT_PAGE`，没有交易。导出 JSON 含 `windowId` 44349、根节点 class `android.widget.FrameLayout`、资源 id `com.tencent.mm:id/jga`、`nodeCount` 4、`hasAmountStyle` true、`hasPaymentKeyword` true。导出和 `rawCaptureRef` 都不含「王小明」「晚上吃饭」「更多功能」「头像」「3.90」「3.9」「390」。`sensitiveFindings` 为空。

`同一会话列表隔七十分钟仍不产生待确认`：通过。待确认仍是 0。两次采样各写一条 `NOT_PAYMENT_PAGE`。

60 秒限流按原因码，不按包名。同一原因在 60 秒内只留一条，所以导出条数可以少于采样次数；刚好满 60 秒可以再记一条。上面隔 70 分钟所以是 2 条。`AccessibilityRejectionLogTest` 的「六种原因都进导出且同窗口内只留一条」和「窗口刚好结束时可以再记一条」仍通过。
