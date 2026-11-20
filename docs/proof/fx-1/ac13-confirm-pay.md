# AC-13 Proof — confirm-pay + chat-list

## fx-1-f-wx-confirm-pay
- Result: **pendingCount = 0**
- Export `ac13-confirm-pay-export.json`: reason `NOT_PAYMENT_PAGE`, fixtureId present; no page body / merchant / amount digits.
- Diagnostic fields present: windowId=88012, nodeCount=5, hasAmountStyle=false, hasPaymentKeyword=false

## WeChat chat list (CR-1 AC-9 path)
- Fixture ingest id `fx-1-f-wx-chat-list` (`wechatConversationList`)
- Result: **pendingCount = 0**
- Export `ac13-chat-list-export.json`: `NOT_PAYMENT_PAGE`; viewIds includes `com.tencent.mm:id/jga`; nodeCount=4; hasAmountStyle=true; hasPaymentKeyword=true
- No person names / preview text / amount digits in export

- APK GIT_SHA / export gitSha: `383a0c5f62a8e7378b9a2d08097fa21bbf7d0b49`
- JVM: `微信确认支付页不进待确认`, `微信会话列表不入库并留下不含正文的拒绝日志` PASS
