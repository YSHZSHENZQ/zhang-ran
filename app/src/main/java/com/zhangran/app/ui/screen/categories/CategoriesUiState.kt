/**
 * 分类管理页的 UI 状态与展示映射（`M2d-1` §4.5；行为 SSOT：`docs/06` §4.12）。
 *
 * 设计要点：
 * - 页面**不持有实体**：`CategoryEntity` 在 ViewModel 内映射为 [CategoryUiItem]；
 * - 编辑弹层的草稿与错误是 **UiState 的一部分**（保存失败要留在弹层里就地提示，A4）；
 * - 三态：`Loading` / `Content` / `Error`；另有 [CategoriesUiState.Empty]——
 *   `06` §4.12 明确"仅系统分类时也有内容"，所以空列表意味着初始化异常，需提示而不是"暂无分类"。
 */
package com.zhangran.app.ui.screen.categories

import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.usecase.CategorySaveError

/**
 * 一个分类的展示模型。
 *
 * @property isFallback 是否为兜底分类「其他」（不可改名/禁用，V3）
 * @property sortOrder 当前排序（上移/下移与测试断言用）
 */
data class CategoryUiItem(
    val id: String,
    val name: String,
    val type: CategoryType,
    val icon: String,
    val enabled: Boolean,
    val isFallback: Boolean,
    val sortOrder: Int,
)

/**
 * 一组分类（支出 / 收入）。
 *
 * @property title 分组标题（"支出分类" / "收入分类"）
 * @property items 按 `sortOrder` 升序
 */
data class CategoryGroupUi(
    val type: CategoryType,
    val title: String,
    val items: List<CategoryUiItem>,
)

/**
 * 分类编辑弹层状态。
 *
 * @property editingId 非空 = 编辑既有分类；`null` = 新增
 * @property type 新增时可选；编辑时只读（V8）
 * @property name 草稿名称（已按上限截断）
 * @property enabled 草稿启用状态（「其他」锁定为 `true`）
 * @property isFallback 是否兜底分类（字段禁用 + 说明原因）
 * @property error 就地错误（重名/空名等）；`null` 表示无错误
 * @property saving 保存中（禁用交互）
 */
data class CategoryEditorUi(
    val editingId: String? = null,
    val type: CategoryType,
    val name: String = "",
    val icon: String,
    val enabled: Boolean = true,
    val isFallback: Boolean = false,
    val error: String? = null,
    val saving: Boolean = false,
) {
    /** 是否允许改类型（只在新增时允许，V8）。 */
    val typeEditable: Boolean get() = editingId == null

    /** 是否允许改名称/启用（「其他」不允许，V3）。 */
    val nameEditable: Boolean get() = !isFallback
    val enabledEditable: Boolean get() = !isFallback
}

/** 分类管理页状态。 */
sealed interface CategoriesUiState {

    /** 初始加载。 */
    data object Loading : CategoriesUiState

    /**
     * 有数据。
     *
     * @property groups 固定两组：支出在前、收入在后
     * @property editor 打开的编辑弹层（`null` = 未打开）
     */
    data class Content(
        val groups: List<CategoryGroupUi>,
        val editor: CategoryEditorUi? = null,
    ) : CategoriesUiState {
        /** 全部展示项（供移动排序时查邻位）。 */
        val allItems: List<CategoryUiItem> get() = groups.flatMap { it.items }
    }

    /**
     * 分类表为空：**初始化异常**（`06` §4.12：至少应有 12+6）。
     *
     * 不显示"暂无分类"，而是引导用户检查初始数据（A13）。
     */
    data object Empty : CategoriesUiState

    /** 读取失败（可重试）。 */
    data class Error(val message: String) : CategoriesUiState
}

/** 分类管理页的一次性事件。 */
sealed interface CategoriesEvent {

    /** 提示消息（保存失败不会走这里——失败留在弹层里就地提示）。 */
    data class Message(val text: String) : CategoriesEvent

    /** 保存成功（可用于关闭弹层/提示；弹层关闭已由状态驱动）。 */
    data object Saved : CategoriesEvent
}

/** 校验失败 → 中文提示（展示层职责）。 */
fun categoryErrorTextOf(error: CategorySaveError): String = when (error) {
    CategorySaveError.NAME_EMPTY -> "分类名称不能为空"
    CategorySaveError.NAME_TOO_LONG -> "分类名称最多 20 字"
    CategorySaveError.DUPLICATE_NAME -> "同一分组下已存在同名分类"
    CategorySaveError.FALLBACK_PROTECTED -> "「其他」是兜底分类，不可改名或禁用"
    CategorySaveError.TYPE_CHANGE_NOT_ALLOWED -> "分类类型不可修改"
    CategorySaveError.NOT_FOUND -> "分类不存在，请刷新后重试"
}

/** 分组类型的中文名（无障碍朗读与分组标题共用）。 */
fun typeLabelOf(type: CategoryType): String = when (type) {
    CategoryType.EXPENSE -> "支出分类"
    CategoryType.INCOME -> "收入分类"
}
