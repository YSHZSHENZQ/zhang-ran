/**
 * 分类管理 ViewModel（`M2d-1` §4.5；`docs/06` §4.12）。
 *
 * 职责：
 * - 订阅 `CategoryRepository.observeAll()` → 两组（支出/收入）网格，写操作后自动刷新；
 * - 编辑弹层的草稿、就地错误、保存中状态都在 [CategoriesUiState.Content.editor]；
 * - 所有写操作只经 `SaveCategoryUseCase`（唯一写入口）；
 * - 排序用"上移/下移"（不做拖拽，避免手势与滚动冲突，任务书 §4.4）：**交换相邻两项的 `sortOrder`**。
 *   两次写不是数据库事务（DAO 冻结），第二步失败时会尝试回滚第一步，并在失败时给出提示。
 */
package com.zhangran.app.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zhangran.app.data.local.entity.CategoryEntity
import com.zhangran.app.data.repository.CategoryRepository
import com.zhangran.app.domain.model.CategoryType
import com.zhangran.app.domain.usecase.CategoryInput
import com.zhangran.app.domain.usecase.DEFAULT_CATEGORY_ICON
import com.zhangran.app.domain.usecase.FALLBACK_CATEGORY_NAME
import com.zhangran.app.domain.usecase.KNOWN_CATEGORY_ICONS
import com.zhangran.app.domain.usecase.MAX_CATEGORY_NAME_LENGTH
import com.zhangran.app.domain.usecase.SaveCategoryResult
import com.zhangran.app.domain.usecase.SaveCategoryUseCase
import com.zhangran.app.domain.usecase.isFallbackCategory
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * @param categories 分类读取（响应式）
 * @param saveCategory 分类写操作（唯一写入口）
 */
class CategoriesViewModel @Inject constructor(
    private val categories: CategoryRepository,
    private val saveCategory: SaveCategoryUseCase,
) : ViewModel() {

    /** 内部数据源状态（把仓储异常收进 Flow，避免异常冒泡）。 */
    private sealed interface SourceState {
        data object Loading : SourceState
        data class Ready(val entities: List<CategoryEntity>) : SourceState
        data class Failure(val message: String) : SourceState
    }

    private val refreshTrigger = MutableStateFlow(0)
    private val editor = MutableStateFlow<CategoryEditorUi?>(null)

    private val _events = MutableSharedFlow<CategoriesEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CategoriesEvent> = _events.asSharedFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val source: Flow<SourceState> = refreshTrigger.flatMapLatest {
        categories.observeAll()
            .map<List<CategoryEntity>, SourceState> { SourceState.Ready(it) }
            .catch { throwable -> emit(SourceState.Failure(throwable.message ?: "读取分类失败")) }
            .onStart { emit(SourceState.Loading) }
    }

    /** 页面状态：数据 + 编辑弹层。 */
    val uiState: StateFlow<CategoriesUiState> = combine(source, editor) { data, currentEditor ->
        when (data) {
            SourceState.Loading -> CategoriesUiState.Loading
            is SourceState.Failure -> CategoriesUiState.Error(data.message)
            is SourceState.Ready -> if (data.entities.isEmpty()) {
                CategoriesUiState.Empty
            } else {
                CategoriesUiState.Content(
                    groups = groupsOf(data.entities),
                    editor = currentEditor,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = CategoriesUiState.Loading,
    )

    /** 错误态重试（重新订阅数据源）。 */
    fun retry() {
        refreshTrigger.value += 1
    }

    // ---------------------------------------------------------------- 编辑弹层

    /** 打开"添加"：类型可选，默认支出。 */
    fun openAdd() {
        editor.value = CategoryEditorUi(
            editingId = null,
            type = CategoryType.EXPENSE,
            name = "",
            icon = DEFAULT_CATEGORY_ICON,
            enabled = true,
        )
    }

    /** 打开编辑既有分类（`type` 只读，V8）。 */
    fun openEdit(item: CategoryUiItem) {
        editor.value = CategoryEditorUi(
            editingId = item.id,
            type = item.type,
            name = item.name,
            icon = item.icon,
            enabled = item.enabled,
            isFallback = item.isFallback,
        )
    }

    fun closeEditor() {
        editor.value = null
    }

    /** 名称草稿（按上限截断；补充规则：≤ 20 字）。 */
    fun onNameChange(value: String) {
        editor.value = editor.value?.copy(
            name = value.take(MAX_CATEGORY_NAME_LENGTH),
            error = null,
        )
    }

    /** 图标只能在已知语义标识里选（V7）。 */
    fun onIconChange(icon: String) {
        if (icon !in KNOWN_CATEGORY_ICONS) return
        editor.value = editor.value?.copy(icon = icon, error = null)
    }

    fun onEnabledChange(enabled: Boolean) {
        val current = editor.value ?: return
        if (current.isFallback) return
        editor.value = current.copy(enabled = enabled, error = null)
    }

    /** 类型只在"添加"时可改（V8）。 */
    fun onTypeChange(type: CategoryType) {
        val current = editor.value ?: return
        if (!current.typeEditable) return
        editor.value = current.copy(type = type, error = null)
    }

    /** 保存（成功关闭弹层；失败留在弹层里就地提示，A4）。 */
    fun save() {
        val current = editor.value ?: return
        if (current.saving) return
        editor.value = current.copy(saving = true, error = null)
        viewModelScope.launch {
            val outcome = runCatching {
                saveCategory(
                    CategoryInput(
                        id = current.editingId,
                        name = current.name,
                        type = current.type,
                        icon = current.icon,
                        enabled = current.enabled,
                    ),
                )
            }.getOrNull()
            when (outcome) {
                is SaveCategoryResult.Success -> {
                    editor.value = null
                    _events.emit(CategoriesEvent.Saved)
                }

                is SaveCategoryResult.Rejected -> editor.value = current.copy(
                    saving = false,
                    error = categoryErrorTextOf(outcome.error),
                )

                null -> editor.value = current.copy(saving = false, error = "保存失败，请重试")
            }
        }
    }

    /**
     * 上移/下移（交换相邻两项的 `sortOrder`）。
     *
     * 只对**编辑中的既有分类**生效；首/末项不移动。
     */
    fun move(up: Boolean) {
        val current = editor.value ?: return
        val editingId = current.editingId ?: return
        val content = uiState.value as? CategoriesUiState.Content ?: return
        val group = content.groups.firstOrNull { it.type == current.type } ?: return
        val index = group.items.indexOfFirst { it.id == editingId }
        val targetIndex = if (up) index - 1 else index + 1
        if (index < 0 || targetIndex !in group.items.indices) return
        val item = group.items[index]
        val neighbour = group.items[targetIndex]

        viewModelScope.launch {
            val movedResult = runCatching { saveCategory(inputOf(item, sortOrder = neighbour.sortOrder)) }.getOrNull()
            if (movedResult !is SaveCategoryResult.Success) {
                _events.emit(CategoriesEvent.Message("调整顺序失败，请重试"))
                return@launch
            }
            val neighbourResult = runCatching {
                saveCategory(inputOf(neighbour, sortOrder = item.sortOrder))
            }.getOrNull()
            if (neighbourResult !is SaveCategoryResult.Success) {
                // 回滚第一步，避免两者 sortOrder 相同（DAO 冻结，无法单事务）
                runCatching { saveCategory(inputOf(item, sortOrder = item.sortOrder)) }
                _events.emit(CategoriesEvent.Message("调整顺序失败，请重试"))
            }
        }
    }

    // ---------------------------------------------------------------- 内部映射

    private fun inputOf(item: CategoryUiItem, sortOrder: Int): CategoryInput = CategoryInput(
        id = item.id,
        name = item.name,
        type = item.type,
        icon = item.icon,
        enabled = item.enabled,
        sortOrder = sortOrder,
    )

    private fun groupsOf(entities: List<CategoryEntity>): List<CategoryGroupUi> = listOf(
        groupOf(CategoryType.EXPENSE, "支出分类", entities),
        groupOf(CategoryType.INCOME, "收入分类", entities),
    )

    private fun groupOf(
        type: CategoryType,
        title: String,
        entities: List<CategoryEntity>,
    ): CategoryGroupUi = CategoryGroupUi(
        type = type,
        title = title,
        items = entities
            .filter { it.type == type }
            // 稳定排序：sortOrder 升序，同值时按 createdAt（避免交换瞬间顺序抖动）
            .sortedWith(compareBy({ it.sortOrder }, { it.createdAt }))
            .map { it.toUiItem() },
    )

    private fun CategoryEntity.toUiItem(): CategoryUiItem = CategoryUiItem(
        id = id,
        name = name,
        type = type,
        icon = icon,
        enabled = enabled,
        isFallback = isFallbackCategory(this),
        sortOrder = sortOrder,
    )

    private companion object {
        /** 无人订阅后保留上游收集的时长（与首页一致）。 */
        const val SUBSCRIPTION_TIMEOUT_MILLIS: Long = 5_000L
    }
}
