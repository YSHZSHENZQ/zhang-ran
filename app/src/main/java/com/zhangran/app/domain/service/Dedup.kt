/**
 * 判重服务（纯 Kotlin/JVM，无 Android / Room / DAO 依赖）。
 *
 * 唯一依据：`docs/03` §12 去重规则（SSOT）。
 * - §12.1 捕获级去抖：**不产出业务判重结论**，只判断“是否同一次投递”；
 * - §12.2 交易级判重：产出 `Duplicate` / `POSSIBLE_DUPLICATE` / 无结论。
 *
 * 职责边界（`docs/03` §15、`docs/07` §15）：
 * - 判重归账然（本服务），不归上游；
 * - Dedup **不决定交易状态**（`docs/03` §16：状态只由 Decision 产出），
 *   只交回判重结论；
 * - Dedup **不得**访问 DAO / Repository：既有交易由调用方（M1c）查询后通过参数传入
 *   （任务书 §4.3、A11）；
 * - Dedup **不得**修改候选、不得覆盖用户受保护字段、不得自行决定业务分类。
 *
 * 窗口语义（任务书 R1）：一律用“交易时间差的绝对值 ≤ 180 秒”，
 * 不允许单侧 180 秒或 360 秒窗口。
 */
package com.zhangran.app.domain.service

import com.zhangran.app.domain.model.Candidate
import com.zhangran.app.domain.model.TransactionState
import com.zhangran.app.domain.model.TransactionType
import kotlin.math.abs

/**
 * 参与判重的既有交易（`docs/03` §12.2 的轻量视图）。
 *
 * 为什么不直接用 `TransactionEntity`：domain 不得依赖 `data/`（分层方向为 data → domain）。
 * 调用方（M1c）从 Repository 取出实体后投影成本类型再传入。
 *
 * @property id 既有交易 id（判重命中时回传给 Decision）
 * @property type 交易类型；语义判重时跨类型一律跳过（`docs/03` §12.2）
 * @property amountMinor 金额（最小货币单位）
 * @property transactionTime 交易时间（UTC epoch millis）
 * @property state 既有交易状态；只有 `CONFIRMED` / `PENDING` / `DUPLICATE` 参与判重
 * @property merchant 规范化后的商户名
 * @property accountId 账户 id
 * @property externalTransactionId 上游交易号
 * @property channelOrderId 渠道单号
 * @property capturedAt 捕获时间（UTC epoch millis；渠道单号规则的 24 小时窗口）
 */
data class ExistingTransaction(
    val id: String,
    val type: TransactionType,
    val amountMinor: Long,
    val transactionTime: Long,
    val state: TransactionState,
    val merchant: String? = null,
    val accountId: String? = null,
    val externalTransactionId: String? = null,
    val channelOrderId: String? = null,
    val capturedAt: Long? = null,
)

/**
 * 捕获级去抖输入（`docs/03` §12.1）。
 *
 * @property packageName 来源应用包名
 * @property notificationId 通知 id
 * @property postTime 通知投递时间（上游原值）
 * @property merchant 规范化后的商户名（identity 缺失时的兜底判据）
 * @property amountMinor 金额（identity 缺失时的兜底判据）
 * @property transactionTime 交易时间（identity 缺失时的 180 秒兜底窗口）
 */
data class CaptureIdentity(
    val packageName: String? = null,
    val notificationId: String? = null,
    val postTime: Long? = null,
    val merchant: String? = null,
    val amountMinor: Long? = null,
    val transactionTime: Long? = null,
)

/**
 * 捕获级去抖结论（`docs/03` §12.1）。**不是**交易级判重结论。
 *
 * [PossibleSameEvent] 表示“疑似同一次投递”，按 §12.1 只能进 Pending，
 * **不得**据此判定为重复（不得写 `DUPLICATE`）。
 */
sealed interface CaptureDebounceResult {

    /** 同一事件（`packageName + notificationId + postTime` 全等），只保留一条捕获记录。 */
    data object SameEvent : CaptureDebounceResult

    /** 疑似同一次投递但无法确定：一律 Pending，不得直接判重。 */
    data object PossibleSameEvent : CaptureDebounceResult

    /** 可以确定为不同事件。 */
    data object DifferentEvent : CaptureDebounceResult
}

/**
 * 交易级判重结论（`docs/03` §12.2）。
 *
 * 只表达“是否与既有交易重复”，**不对应任何 `TransactionState`**；
 * 状态由 Decision 规则 4/5 产出（任务书 R2）。
 */
sealed interface DedupResult {

    /** 无判重结论（不是重复，也没有疑似重复）。 */
    data object None : DedupResult

    /**
     * 判定为重复（`docs/03` §12.2 规则 1/2/3 任一命中）。
     *
     * @property matchedTransactionId 命中的既有交易 id（按输入顺序取第一条）
     */
    data class Duplicate(
        val matchedTransactionId: String,
    ) : DedupResult

    /**
     * 疑似重复：`docs/03` §12.2 “任一条件缺失 → 不判 Duplicate，标记 POSSIBLE_DUPLICATE”。
     *
     * @property candidateTransactionIds 全部疑似命中的既有交易 id（保持输入顺序、去重）
     */
    data class PossibleDuplicate(
        val candidateTransactionIds: List<String>,
    ) : DedupResult
}

/**
 * 判重服务（无状态，纯函数）。
 */
object Dedup {

    /** 语义兜底的时间窗：交易时间差**绝对值** ≤ 180 秒（`docs/03` §12.2）。 */
    const val SEMANTIC_TIME_WINDOW_SECONDS: Long = 180L

    /** 渠道单号的时间窗：捕获时间差 ≤ 24 小时（`docs/03` §12.2）。 */
    const val CHANNEL_TIME_WINDOW_HOURS: Long = 24L

    /** 参与判重的既有交易时间范围：最近 7 天（`docs/03` §12.2）。 */
    const val COMPARISON_WINDOW_DAYS: Long = 7L

    private const val MILLIS_PER_SECOND: Long = 1_000L
    private const val MILLIS_PER_HOUR: Long = 60L * 60L * MILLIS_PER_SECOND
    private const val MILLIS_PER_DAY: Long = 24L * MILLIS_PER_HOUR

    private val SEMANTIC_WINDOW_MILLIS: Long = SEMANTIC_TIME_WINDOW_SECONDS * MILLIS_PER_SECOND
    private val CHANNEL_WINDOW_MILLIS: Long = CHANNEL_TIME_WINDOW_HOURS * MILLIS_PER_HOUR
    private val COMPARISON_WINDOW_MILLIS: Long = COMPARISON_WINDOW_DAYS * MILLIS_PER_DAY

    /** 只有这三种状态的交易参与判重（`docs/03` §12.2）。 */
    private val COMPARABLE_STATES: Set<TransactionState> = setOf(
        TransactionState.CONFIRMED,
        TransactionState.PENDING,
        TransactionState.DUPLICATE,
    )

    /**
     * 交易级判重（`docs/03` §12.2），严格按判定顺序 1 → 2 → 3 短路。
     *
     * 比较对象：最近 7 天内（相对候选交易时间）的 `CONFIRMED` / `PENDING` / `DUPLICATE` 交易。
     * 边界按“不满 7 天”处理：正好 7 天前的交易**不参与**判重（对齐 `docs/08` §2.5
     * “与 7 天前的交易比较 → 不参与判重”）。调用方负责排除“候选自身对应的既有交易”
     * （重新决策场景）。
     *
     * @param candidate 已规范化的候选
     * @param existing 既有交易（由调用方查询后传入，本服务不触数据层）
     */
    fun check(candidate: Candidate, existing: List<ExistingTransaction>): DedupResult {
        val comparable = existing.filter { it.state in COMPARABLE_STATES }
            .filter { abs(it.transactionTime - candidate.transactionTime) < COMPARISON_WINDOW_MILLIS }

        // 规则 1：externalTransactionId 精确相同 → Duplicate。
        val externalId = candidate.externalTransactionId?.takeIf { it.isNotBlank() }
        if (externalId != null) {
            comparable.firstOrNull { it.externalTransactionId == externalId }?.let {
                return DedupResult.Duplicate(it.id)
            }
        }

        // 规则 2：渠道单号相同且捕获时间在 24 小时内 → Duplicate。
        // 渠道单号相同但捕获时间无法确定 → 不能判 Duplicate，记为疑似。
        val possibleIds = LinkedHashSet<String>()
        val channelOrderId = candidate.channelOrderId?.takeIf { it.isNotBlank() }
        if (channelOrderId != null) {
            for (transaction in comparable) {
                if (transaction.channelOrderId != channelOrderId) continue
                val candidateCapturedAt = candidate.capturedAt
                val existingCapturedAt = transaction.capturedAt
                if (candidateCapturedAt == null || existingCapturedAt == null) {
                    possibleIds += transaction.id
                } else if (abs(candidateCapturedAt - existingCapturedAt) <= CHANNEL_WINDOW_MILLIS) {
                    return DedupResult.Duplicate(transaction.id)
                }
            }
        }

        // 规则 3：语义兜底。禁止跨 direction 判重（EXPENSE 与 REFUND 永不互判）。
        val direction = candidate.direction
        val amountMinor = candidate.amountMinor
        if (direction != null && amountMinor != null) {
            val candidateMerchant = merchantKey(candidate.merchant)
            for (transaction in comparable) {
                if (transaction.type != direction) continue
                if (transaction.amountMinor != amountMinor) continue
                if (abs(transaction.transactionTime - candidate.transactionTime) > SEMANTIC_WINDOW_MILLIS) continue

                val existingMerchant = merchantKey(transaction.merchant)
                val sameMerchant = candidateMerchant != null && candidateMerchant == existingMerchant
                val sameAccount = candidate.accountId != null && candidate.accountId == transaction.accountId
                if (sameMerchant || sameAccount) {
                    return DedupResult.Duplicate(transaction.id)
                }
                val merchantsKnownAndDifferent = candidateMerchant != null &&
                    existingMerchant != null &&
                    candidateMerchant != existingMerchant
                val accountsBothMissing = candidate.accountId == null && transaction.accountId == null
                if (merchantsKnownAndDifferent && accountsBothMissing) {
                    // 商户两边都有而且不同，账户又都空：商户比较已经否掉了，
                    // 没有账户能把它们再对齐成同一笔。这不是“条件缺失”，不标疑似。
                    // 账户都在但不相同的情况仍落到下面，标 POSSIBLE_DUPLICATE。
                    continue
                }
                // 金额与时间窗都命中，但商户或账户对不上、又缺了能下结论的字段 → 疑似。
                possibleIds += transaction.id
            }
        }

        return if (possibleIds.isEmpty()) {
            DedupResult.None
        } else {
            DedupResult.PossibleDuplicate(possibleIds.toList())
        }
    }

    /**
     * 捕获级去抖（`docs/03` §12.1）：只判断两次捕获是否同一次投递，**不产出判重结论**。
     *
     * 判定顺序：
     * 1. 双方都有 `packageName + notificationId + postTime`：三者全等 → 同一事件；
     *    任一不同 → 不同事件。
     * 2. identity 缺失时：`merchant` 与 `amountMinor` 相等且交易时间差绝对值 ≤ 180 秒
     *    → **疑似**同一次投递（只能进 Pending）；时间无法确定时同样按疑似处理。
     * 3. 其余 → 不同事件。
     */
    fun checkCaptureDebounce(first: CaptureIdentity, second: CaptureIdentity): CaptureDebounceResult {
        val firstKey = first.identityKey()
        val secondKey = second.identityKey()
        if (firstKey != null && secondKey != null) {
            return if (firstKey == secondKey) {
                CaptureDebounceResult.SameEvent
            } else {
                CaptureDebounceResult.DifferentEvent
            }
        }

        val firstMerchant = merchantKey(first.merchant)
        val sameMerchant = firstMerchant != null && firstMerchant == merchantKey(second.merchant)
        val sameAmount = first.amountMinor != null && first.amountMinor == second.amountMinor
        if (!sameMerchant || !sameAmount) return CaptureDebounceResult.DifferentEvent

        val firstTime = first.transactionTime
        val secondTime = second.transactionTime
        if (firstTime == null || secondTime == null) {
            // 无法证明是同一次投递，但也不能排除 → 按“仍不确定”处理，一律 Pending。
            return CaptureDebounceResult.PossibleSameEvent
        }
        return if (abs(firstTime - secondTime) <= SEMANTIC_WINDOW_MILLIS) {
            CaptureDebounceResult.PossibleSameEvent
        } else {
            CaptureDebounceResult.DifferentEvent
        }
    }

    /** `identity` 三要素齐全时返回比较键，否则返回 `null`（走兜底判定）。 */
    private fun CaptureIdentity.identityKey(): Triple<String, String, Long>? {
        val packageName = packageName?.takeIf { it.isNotBlank() } ?: return null
        val notificationId = notificationId?.takeIf { it.isNotBlank() } ?: return null
        val postTime = postTime ?: return null
        return Triple(packageName, notificationId, postTime)
    }

    /**
     * 商户比较键。
     *
     * 完整规范化属 Normalize（M1b）；候选本身已规范化，这里只做幂等的 trim + 小写，
     * 避免仅因首尾空白/大小写差异漏判（不新增任何裁剪规则）。
     */
    private fun merchantKey(value: String?): String? =
        value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}
