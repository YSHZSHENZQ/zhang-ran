/**
 * 分类写操作（`M2d-1` §4.2；规则 SSOT：`docs/02` §9、`docs/03` §7）。
 *
 * **唯一写入口**：新增/更新分类都只能经过本用例，页面与其它用例不得直接写仓储。
 * 校验失败返回结构化错误（不抛异常、不落库）；规则逐条对应任务书 V1–V8。
 *
 * P-011：**不提供物理删除**（本用例没有 delete，仓储也没有）。
 */
package com.zhangran.app.domain.usecase

import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import java.time.Clock
import java.util.UUID
import javax.inject.Inject

/**
 * 兜底分类名（`docs/02` §9）。
 *
 * **唯一常量落点**：记账表单的「其他」流程、分类管理页、两个用例都引用这里，
 * 不得再散落字面量（任务书 §4.2 注）。
 */
const val FALLBACK_CATEGORY_NAME: String = "其他"

/** 分类名称长度上限（任务书 §4.4：`03` §7 无上限，本任务取 20，属**补充**规则）。 */
const val MAX_CATEGORY_NAME_LENGTH: Int = 20

/**
 * 未知/缺失图标时使用的语义标识（`docs/02` §9：数据库只存语义标识，不存 emoji）。
 *
 * 取兜底分类的图标标识，保证历史数据缺失图标时仍能显示一个稳定字形。
 */
const val DEFAULT_CATEGORY_ICON: String = "other"

/**
 * 允许落库的分类图标语义标识（V7）。
 *
 * 与 `ui/common/CategoryIconMapper.supportedIcons` **必须完全一致**；
 * `SaveCategoryUseCaseTest` 有一条断言把两者逐项比对，防止漂移。
 * （领域层不依赖 UI，因此这里保留一份标识清单；emoji 字形仍只在 UI 层。）
 */
val KNOWN_CATEGORY_ICONS: Set<String> = setOf(
    // 支出
    "food", "transport", "shopping", "housing", "entertainment", "medical",
    "education", "communication", "gift", "travel", "pet", "other",
    // 收入
    "salary", "bonus", "investment", "parttime", "redpacket",
)

/**
 * 是否为兜底分类「其他」（`docs/02` §9：`systemDefined = true` 且名称等于兜底名）。
 *
 * 集中判断，页面/用例都不得自己拼条件（任务书 §4.2 注）。
 */
fun isFallbackCategory(category: CategoryEntity): Boolean =
    category.systemDefined && category.name == FALLBACK_CATEGORY_NAME

/**
 * 一次分类写操作入参。
 *
 * @property id 更新时必填；新增为 `null`
 * @property name 名称（会被 trim；空/超长会被拒）
 * @property type 类型（新增时决定分组；更新时只用于校验，**不得变更**，V8）
 * @property icon 图标语义标识（未知标识回退 [DEFAULT_CATEGORY_ICON]，V7）
 * @property enabled 是否启用（「其他」不得禁用，V3）
 * @property sortOrder 排序；`null` 表示新增时追加到同 type 末尾（V6）或更新时保持原值
 */
data class CategoryInput(
    val id: String? = null,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val enabled: Boolean = true,
    val sortOrder: Int? = null,
)

/** 分类写操作的拒绝原因（V1–V8）。 */
enum class CategorySaveError {
    /** V1：名称为空（trim 后为空）。 */
    NAME_EMPTY,

    /** 名称超过 [MAX_CATEGORY_NAME_LENGTH]（补充规则）。 */
    NAME_TOO_LONG,

    /** V2：同一 type 下重名。 */
    DUPLICATE_NAME,

    /** V3：「其他」不可改名、不可禁用。 */
    FALLBACK_PROTECTED,

    /** V8：更新时不允许改 type。 */
    TYPE_CHANGE_NOT_ALLOWED,

    /** 更新目标不存在。 */
    NOT_FOUND,
}

/** 分类写操作结果。 */
sealed interface SaveCategoryResult {

    /**
     * 成功。
     *
     * @property category 写入后的完整实体
     * @property created `true` 表示新建，`false` 表示更新
     */
    data class Success(val category: CategoryEntity, val created: Boolean) : SaveCategoryResult

    data class Rejected(val error: CategorySaveError) : SaveCategoryResult
}

/**
 * 新增/更新分类的唯一写入口。
 *
 * @param categories 分类读写（`insert` / `update` 为本任务新增）
 * @param clock 时间源（`createdAt` / `updatedAt`；测试注入固定时钟）
 */
class SaveCategoryUseCase @Inject constructor(
    private val categories: CategoryRepository,
    private val clock: Clock,
) {

    suspend operator fun invoke(input: CategoryInput): SaveCategoryResult {
        val name = input.name.trim()
        // V1
        if (name.isEmpty()) return SaveCategoryResult.Rejected(CategorySaveError.NAME_EMPTY)
        if (name.length > MAX_CATEGORY_NAME_LENGTH) {
            return SaveCategoryResult.Rejected(CategorySaveError.NAME_TOO_LONG)
        }
        // V7：未知图标回退默认（不允许把 emoji 或任意串写入数据库）
        val icon = input.icon.takeIf { it in KNOWN_CATEGORY_ICONS } ?: DEFAULT_CATEGORY_ICON
        val now = clock.millis()
        val all = categories.all()

        // V2 + V5：新增
        if (input.id == null) {
            if (all.any { it.type == input.type && it.name == name }) {
                return SaveCategoryResult.Rejected(CategorySaveError.DUPLICATE_NAME)
            }
            // V6：未指定排序 → 追加到同 type 末尾
            val sortOrder = input.sortOrder ?: appendedSortOrder(all, input.type)
            val entity = CategoryEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                type = input.type,
                icon = icon,
                sortOrder = sortOrder,
                enabled = input.enabled,
                systemDefined = false,
                createdAt = now,
                updatedAt = now,
            )
            categories.insert(entity)
            return SaveCategoryResult.Success(category = entity, created = true)
        }

        // 更新
        val existing = all.firstOrNull { it.id == input.id }
            ?: return SaveCategoryResult.Rejected(CategorySaveError.NOT_FOUND)
        // V8：类型不得变更
        if (existing.type != input.type) {
            return SaveCategoryResult.Rejected(CategorySaveError.TYPE_CHANGE_NOT_ALLOWED)
        }
        // V3：兜底分类不可改名、不可禁用（图标与排序仍可调整）
        if (isFallbackCategory(existing) && (name != existing.name || !input.enabled)) {
            return SaveCategoryResult.Rejected(CategorySaveError.FALLBACK_PROTECTED)
        }
        // V2：同 type 下重名（排除自身）
        if (all.any { it.id != existing.id && it.type == input.type && it.name == name }) {
            return SaveCategoryResult.Rejected(CategorySaveError.DUPLICATE_NAME)
        }
        val updated = existing.copy(
            name = name,
            icon = icon,
            sortOrder = input.sortOrder ?: existing.sortOrder,
            enabled = input.enabled,
            updatedAt = now,
        )
        categories.update(updated)
        return SaveCategoryResult.Success(category = updated, created = false)
    }

    /** 同 type 末尾排序：`max + 1`（V6）。 */
    private fun appendedSortOrder(all: List<CategoryEntity>, type: CategoryType): Int =
        (all.filter { it.type == type }.maxOfOrNull { it.sortOrder } ?: -1) + 1
}
