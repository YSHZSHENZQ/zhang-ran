/**
 * 领域枚举的唯一落点（`docs/04` §3「枚举与领域值对象的落点纪律」）。
 *
 * 约束：
 * - 所有领域枚举只在本文件定义一次；其它层（`data/`、`autoaccounting/`、`capture/`、`ui/`）
 *   只能 import，不得复制定义。
 * - `data/local/entity` 可以直接依赖本包（data → domain 是允许方向）；
 *   禁止 `data/` → `autoaccounting/` 方向。
 * - 取值均为 SSOT：不得新增或改动（`03` §5/§7/§8/§9、`07` §5）。
 *
 * 持久化：Room 需要显式 TypeConverter，见 `data/local/Converters.kt`
 * （枚举一律以 `name` 存为 TEXT，保证可读与迁移稳定）。
 */
package com.zhangran.app.domain.model

/**
 * 交易类型（`docs/03` §5、§13）。金额与方向分离：方向由本枚举表达，金额恒非负。
 */
enum class TransactionType {
    /** 支出。 */
    EXPENSE,

    /** 收入。 */
    INCOME,

    /** 转账（单条 Transaction + 两侧账户，`categoryId` 必须为空）。 */
    TRANSFER,

    /** 退款（关联原交易，按退款发生月抵扣）。 */
    REFUND,
}

/**
 * 交易状态（`docs/03` §6 状态机与可见性）。
 */
enum class TransactionState {
    /** 待确认（无法可靠判断时的默认归宿）。 */
    PENDING,

    /** 已确认（正式账务）。 */
    CONFIRMED,

    /** 已忽略（用户明确不予记账）。 */
    IGNORED,

    /** 已删除（软删除，保留 30 天恢复窗口）。 */
    DELETED,

    /** 重复（被判定为已有交易的重复，保留不物理删除）。 */
    DUPLICATE,
}

/**
 * 采集来源（SSOT：`docs/07` §5）。**枚举值与顺序不得新增或改动。**
 *
 * `priority` 为多来源命中时的可信度排序（**数值越小越可信**）；
 * `docs/07` §5 已把顺序文档化：
 * `MANUAL > NOTIFICATION > HOOK > SMS > ACCESSIBILITY > AI > OCR`。
 *
 * 迁移说明：本枚举原定义在 `autoaccounting/CaptureSourceMapper.kt`（T3 落地）。
 * 因 `data/local/entity` 需要它、而实体**不得依赖 Adapter 层**，故按 `docs/04` §3
 * 的一次性搬迁纪律迁到本文件；`CaptureSourceMapper` 改为 import。
 */
enum class CaptureSource(val priority: Int) {
    /** 用户手工记账。 */
    MANUAL(0),

    /** 通知（含上游 Hook 系统通知、通知渠道）。 */
    NOTIFICATION(1),

    /** 上游 Hook 拿到的应用内账单数据。 */
    HOOK(2),

    /** 短信广播解析。 */
    SMS(3),

    /** 无障碍读取页面可见文本。 */
    ACCESSIBILITY(4),

    /** AI 辅助识别（在上游产生）。 */
    AI(5),

    /** Shizuku / 截图 + OCR 识别。 */
    OCR(6),
    ;

    companion object {
        /** 按可信度排序（可信在前）。 */
        val byPriority: List<CaptureSource> = entries.sortedBy { it.priority }
    }
}

/**
 * 账户类型（`docs/03` §8）。取值不得扩展。
 */
enum class AccountType {
    /** 现金。 */
    CASH,

    /** 储蓄卡。 */
    DEBIT_CARD,

    /** 信用卡（v0.1 不参与余额计算，仅作标签）。 */
    CREDIT_CARD,

    /** 第三方支付账户（如微信零钱、支付宝余额）。 */
    THIRD_PARTY,
}

/**
 * 支付渠道（`docs/03` §8）。是账户的一个属性，不是独立对象；可为空。
 */
enum class PaymentChannel {
    /** 支付宝。 */
    ALIPAY,

    /** 微信。 */
    WECHAT,

    /** 银行。 */
    BANK,

    /** 现金。 */
    CASH,

    /** 其它。 */
    OTHER,
}

/**
 * 分类适用范围（`docs/03` §7）。收入分类与支出分类互不可用。
 */
enum class CategoryType {
    /** 支出分类。 */
    EXPENSE,

    /** 收入分类。 */
    INCOME,
}

/**
 * 预算范围（`docs/03` §9）。取值不得扩展。
 */
enum class BudgetScope {
    /** 总预算。 */
    TOTAL,

    /** 分类预算。 */
    CATEGORY,
}

/**
 * 预算周期（`docs/03` §9）。v0.1 仅月度。
 */
enum class BudgetPeriod {
    /** 月度。 */
    MONTH,
}

/**
 * 币种（`docs/03` §5）。v0.1 仅 `CNY`。
 *
 * 非 CNY 的交易不得自动入账，必须进入 Pending（reason = AMOUNT_UNCERTAIN），
 * 且 v0.1 不提供多币种换算。
 */
enum class Currency {
    /** 人民币。 */
    CNY,
}
