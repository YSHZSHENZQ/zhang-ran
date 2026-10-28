/**
 * 预算写操作（`M4a` §4.4；规则 SSOT：`docs/03` §9、`docs/02` §12）。
 *
 * **唯一写入口**：新增/更新/删除预算都经过本用例。失败返回结构化错误（不落库、不抛异常）。
 *
 * 关键规则：
 * - V1 `limitMinor > 0` 且不超过 [MAX_AMOUNT_MINOR]（`Money` 上限）；
 * - V2 `TOTAL` 的 `categoryId` 必须为空，`CATEGORY` 必须非空；
 * - V3/V4 唯一性：同一 `periodKey` 的总预算、同一 `periodKey + categoryId` 的分类预算
 *   已存在时**更新而不是新增**（TOTAL 走 `insertTotalIfAbsent` 事务兜底）；
 * - V5 分类必须存在（`CategoryRepository`）；
 * - V6 `periodKey` 必须是 `YYYY-MM`（v0.1 仅月度，`period = MONTH` 固定）；
 * - V7 删除：物理删除，预算与账务解耦；
 * - 示例预算（§4.4.2 方案 B）：**不自动落库**，由用户点选后调用 [applyExampleBudgets]。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.BudgetEntity
import com.zhangran.app.data.repository.BudgetRepository
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.BudgetPeriod
import com.zhangran.app.domain.model.BudgetScope
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.service.MAX_AMOUNT_MINOR
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/** 一次预算写操作入参。 */
data class BudgetInput(
    val id: String? = null,
    val scope: BudgetScope,
    val categoryId: String? = null,
    val periodKey: String,
    val limitMinor: Long,
    val enabled: Boolean = true,
)

/** 预算写操作的拒绝原因。 */
enum class BudgetSaveError {
    /** V1：额度必须为正且不超过上限。 */
    LIMIT_INVALID,

    /** V2：分类预算必须有 categoryId。 */
    CATEGORY_REQUIRED,

    /** V2：总预算不得带 categoryId。 */
    CATEGORY_NOT_ALLOWED,

    /** V5：分类不存在。 */
    CATEGORY_MISSING,

    /** V6：periodKey 不是 `YYYY-MM`。 */
    PERIOD_KEY_INVALID,

    /** 预算不存在（按 id 更新/删除时）。 */
    NOT_FOUND,

    /** 更新时不允许改 scope / categoryId（避免一条预算变成另一条）。 */
    SCOPE_CHANGE_NOT_ALLOWED,

    /** 更新时不允许改 periodKey（v0.1 只支持当月，避免把预算搬到别的月份）。 */
    PERIOD_CHANGE_NOT_ALLOWED,
}

/** 预算写操作结果。 */
sealed interface SaveBudgetResult {

    data class Success(val budget: BudgetEntity, val created: Boolean) : SaveBudgetResult

    data class Rejected(val error: BudgetSaveError) : SaveBudgetResult
}

/** 删除结果。 */
enum class DeleteBudgetResult {
    DELETED,
    NOT_FOUND,
}

/**
 * 示例预算项（§4.4.2；金额为任务书给定的**推荐值**）。
 *
 * @property categoryName `null` 表示总预算
 */
data class ExampleBudgetItem(val categoryName: String?, val limitMinor: Long)

/** 示例预算（与任务书 §4.4.2 表格逐项一致；调整金额只改这里）。 */
val EXAMPLE_BUDGETS: List<ExampleBudgetItem> = listOf(
    ExampleBudgetItem(categoryName = null, limitMinor = 300_000L),          // 总预算 ¥3,000
    ExampleBudgetItem(categoryName = "餐饮", limitMinor = 120_000L),         // ¥1,200
    ExampleBudgetItem(categoryName = "交通", limitMinor = 30_000L),          // ¥300
    ExampleBudgetItem(categoryName = "购物", limitMinor = 60_000L),          // ¥600
    ExampleBudgetItem(categoryName = "娱乐", limitMinor = 30_000L),          // ¥300
)

/** 合法的 `periodKey`（`YYYY-MM`；`03` §9）。 */
private val PERIOD_KEY_PATTERN = Regex("^\\d{4}-(0[1-9]|1[0-2])$")

/**
 * @param budgets 预算读写
 * @param categories 分类读取（V5）
 * @param clock 时间源
 */
class SaveBudgetUseCase @Inject constructor(
    private val budgets: BudgetRepository,
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(input: BudgetInput): SaveBudgetResult {
        if (input.limitMinor <= 0L || input.limitMinor > MAX_AMOUNT_MINOR) {
            return SaveBudgetResult.Rejected(BudgetSaveError.LIMIT_INVALID)
        }
        if (!PERIOD_KEY_PATTERN.matches(input.periodKey)) {
            return SaveBudgetResult.Rejected(BudgetSaveError.PERIOD_KEY_INVALID)
        }
        when (input.scope) {
            BudgetScope.TOTAL -> if (input.categoryId != null) {
                return SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_NOT_ALLOWED)
            }

            BudgetScope.CATEGORY -> {
                val categoryId = input.categoryId
                    ?: return SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_REQUIRED)
                val exists = categories.all().any { it.id == categoryId && it.type == CategoryType.EXPENSE }
                if (!exists) return SaveBudgetResult.Rejected(BudgetSaveError.CATEGORY_MISSING)
            }
        }

        val now = clock.millis()

        // 按 id 更新
        if (input.id != null) {
            val existing = budgets.findById(input.id)
                ?: return SaveBudgetResult.Rejected(BudgetSaveError.NOT_FOUND)
            if (existing.scope != input.scope || existing.categoryId != input.categoryId) {
                return SaveBudgetResult.Rejected(BudgetSaveError.SCOPE_CHANGE_NOT_ALLOWED)
            }
            if (existing.periodKey != input.periodKey) {
                return SaveBudgetResult.Rejected(BudgetSaveError.PERIOD_CHANGE_NOT_ALLOWED)
            }
            val updated = existing.copy(
                limitMinor = input.limitMinor,
                enabled = input.enabled,
                updatedAt = now,
            )
            budgets.update(updated)
            return SaveBudgetResult.Success(updated, created = false)
        }

        // 无 id：按唯一键查找既有预算 → 更新；否则新增（V3/V4）
        val existing = when (input.scope) {
            BudgetScope.TOTAL -> budgets.findTotal(input.periodKey)
            BudgetScope.CATEGORY -> budgets.listByPeriodKey(input.periodKey)
                .firstOrNull { it.scope == BudgetScope.CATEGORY && it.categoryId == input.categoryId }
        }
        if (existing != null) {
            val updated = existing.copy(
                limitMinor = input.limitMinor,
                enabled = input.enabled,
                updatedAt = now,
            )
            budgets.update(updated)
            return SaveBudgetResult.Success(updated, created = false)
        }

        val entity = BudgetEntity(
            id = UUID.randomUUID().toString(),
            scope = input.scope,
            categoryId = input.categoryId,
            period = BudgetPeriod.MONTH,
            periodKey = input.periodKey,
            limitMinor = input.limitMinor,
            enabled = input.enabled,
            createdAt = now,
            updatedAt = now,
        )
        if (input.scope == BudgetScope.TOTAL) {
            if (!budgets.insertTotalIfAbsent(entity)) {
                // 并发：另一处刚插入 → 回退为更新（仍只有一条 TOTAL）
                val current = budgets.findTotal(input.periodKey)
                    ?: return SaveBudgetResult.Rejected(BudgetSaveError.NOT_FOUND)
                val updated = current.copy(
                    limitMinor = input.limitMinor,
                    enabled = input.enabled,
                    updatedAt = now,
                )
                budgets.update(updated)
                return SaveBudgetResult.Success(updated, created = false)
            }
        } else {
            runCatching { budgets.insert(entity) }.onFailure {
                // 分类预算唯一索引冲突 → 回退为更新
                val current = budgets.listByPeriodKey(input.periodKey)
                    .firstOrNull { it.scope == BudgetScope.CATEGORY && it.categoryId == input.categoryId }
                    ?: throw it
                budgets.update(current.copy(limitMinor = input.limitMinor, updatedAt = now))
            }
        }
        return SaveBudgetResult.Success(entity, created = true)
    }

    /** V7：物理删除（先确认存在，便于页面区分"已删除/不存在"）。 */
    suspend fun delete(id: String): DeleteBudgetResult {
        val existing = budgets.findById(id) ?: return DeleteBudgetResult.NOT_FOUND
        budgets.delete(existing.id)
        return DeleteBudgetResult.DELETED
    }

    /**
     * 写入示例预算（§4.4.2 方案 B：**用户点选后**才调用）。
     *
     * 幂等：已存在的预算**跳过**（不覆盖用户手设的金额）；分类不存在时跳过该项。
     *
     * @return 实际新建的条数（0 表示都已存在）
     */
    suspend fun applyExampleBudgets(periodKey: String): Int {
        if (!PERIOD_KEY_PATTERN.matches(periodKey)) return 0
        val expenseCategories = categories.all().filter { it.type == CategoryType.EXPENSE }
        var created = 0
        EXAMPLE_BUDGETS.forEach { item ->
            val categoryId = item.categoryName?.let { name ->
                expenseCategories.firstOrNull { it.name == name }?.id
            }
            if (item.categoryName != null && categoryId == null) return@forEach

            val existing = when {
                categoryId == null -> budgets.findTotal(periodKey)
                else -> budgets.listByPeriodKey(periodKey)
                    .firstOrNull { it.categoryId == categoryId }
            }
            if (existing != null) return@forEach

            val result = invoke(
                BudgetInput(
                    scope = if (categoryId == null) BudgetScope.TOTAL else BudgetScope.CATEGORY,
                    categoryId = categoryId,
                    periodKey = periodKey,
                    limitMinor = item.limitMinor,
                ),
            )
            if (result is SaveBudgetResult.Success && result.created) created++
        }
        return created
    }
}
