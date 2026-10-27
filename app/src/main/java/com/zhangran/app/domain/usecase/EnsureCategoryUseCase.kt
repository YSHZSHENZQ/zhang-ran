/**
 * 「其他」分类的按名创建或复用（`M2d-1` §4.3；规则 SSOT：`docs/02` §9）。
 *
 * 记账表单选择「其他」并输入名称后调用本用例：
 * - 当前 type 下**不存在**同名 → 创建（`systemDefined = false`，追加到末尾）并返回；
 * - 存在且启用 → 直接复用；
 * - 存在但**已禁用** → 复用并**自动重新启用**（否则本笔交易会引用一个"不出现在选择列表"的分类）；
 * - 相同名称连续调用是幂等的；
 * - 并发创建撞上唯一索引时回退为查询（`docs/03` §7 的唯一约束兜底）。
 *
 * 写操作仍经 [SaveCategoryUseCase]（唯一写入口），本用例只做"决策 + 幂等"。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import javax.inject.Inject

/**
 * @param categories 分类读取（`findByName` 含已禁用）
 * @param saveCategory 分类写入口
 */
class EnsureCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val saveCategory: SaveCategoryUseCase,
) {

    /**
     * 返回当前 `type` 下可用的同名分类（必要时创建/重新启用）。
     *
     * @throws IllegalArgumentException 名称为空或超过 [MAX_CATEGORY_NAME_LENGTH]
     * @throws IllegalStateException 创建被拒且回查不到（数据异常）
     */
    suspend operator fun invoke(type: CategoryType, name: String): CategoryEntity {
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "分类名称不能为空" }
        require(normalized.length <= MAX_CATEGORY_NAME_LENGTH) {
            "分类名称最多 $MAX_CATEGORY_NAME_LENGTH 字"
        }

        val existing = categories.findByName(type, normalized)
        if (existing != null) return if (existing.enabled) existing else reEnable(type, existing)

        val outcome = runCatching {
            saveCategory(
                CategoryInput(
                    id = null,
                    name = normalized,
                    type = type,
                    icon = DEFAULT_CATEGORY_ICON,
                    enabled = true,
                ),
            )
        }
        return when (val result = outcome.getOrNull()) {
            is SaveCategoryResult.Success -> result.category
            is SaveCategoryResult.Rejected -> {
                // 并发：另一个调用刚创建了同名分类 → 回退查询（§4.3）
                categories.findByName(type, normalized)
                    ?: throw IllegalStateException("创建分类失败：${result.error}")
            }

            null -> {
                // 数据库唯一约束冲突等异常 → 回退查询
                categories.findByName(type, normalized)
                    ?: throw (outcome.exceptionOrNull() ?: IllegalStateException("创建分类失败"))
            }
        }
    }

    /** 已禁用的同名分类：复用并重新启用（A11）。 */
    private suspend fun reEnable(type: CategoryType, existing: CategoryEntity): CategoryEntity =
        when (
            val result = saveCategory(
                CategoryInput(
                    id = existing.id,
                    name = existing.name,
                    type = type,
                    icon = existing.icon,
                    enabled = true,
                    sortOrder = existing.sortOrder,
                ),
            )
        ) {
            is SaveCategoryResult.Success -> result.category
            // 重新启用被拒（理论上不会：兜底分类本就不可禁用）→ 返回原实体
            is SaveCategoryResult.Rejected -> existing
        }
}
