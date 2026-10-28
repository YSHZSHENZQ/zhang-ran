/**
 * 搜索条件（`M4e` §4.1；口径 SSOT：`docs/02` §13）。
 *
 * 9 个维度全部**可选且可任意组合**（AND）；`page` 为 0-based，默认每页 50 条。
 * `keyword` 一个输入框同时匹配 `merchant` 与 `note`（`06` §4.17 的这两个维度在 UI 上合并）。
 */
package com.zhangran.app.domain.model

/** 搜索默认页大小（`02` §13）。 */
const val SEARCH_PAGE_SIZE: Int = 50

data class SearchCriteria(
    /** 同时匹配 merchant 与 note（包含、不区分大小写）；空串视为未填。 */
    val keyword: String? = null,
    /** 金额等值（分）；与区间同时给时**等值优先**（区间被忽略）。 */
    val amountExactMinor: Long? = null,
    val amountMinMinor: Long? = null,
    val amountMaxMinor: Long? = null,
    /** 起始时间（含），UTC epoch millis。 */
    val dateFromInclusive: Long? = null,
    /** 结束时间（不含），UTC epoch millis。 */
    val dateToExclusive: Long? = null,
    val categoryId: String? = null,
    val accountId: String? = null,
    val type: TransactionType? = null,
    val source: CaptureSource? = null,
    val state: TransactionState? = null,
    /** 0-based。 */
    val page: Int = 0,
    val pageSize: Int = SEARCH_PAGE_SIZE,
) {

    /**
     * 归一化（在任何查询前调用）：
     * - 关键字去首尾空白，空串 → null；
     * - 金额等值优先：给了等值就清空 min/max；
     * - 负页码 → 0；页大小固定为 [SEARCH_PAGE_SIZE]。
     */
    fun normalized(): SearchCriteria = copy(
        keyword = keyword?.trim()?.takeIf { it.isNotEmpty() },
        amountMinMinor = if (amountExactMinor != null) null else amountMinMinor,
        amountMaxMinor = if (amountExactMinor != null) null else amountMaxMinor,
        page = page.coerceAtLeast(0),
        pageSize = SEARCH_PAGE_SIZE,
    )

    /** 是否设置了任何筛选（分页字段不算）。空条件时不发起搜索（A11）。 */
    fun hasAnyFilter(): Boolean {
        val normalized = normalized()
        return normalized.keyword != null ||
            normalized.amountExactMinor != null ||
            normalized.amountMinMinor != null ||
            normalized.amountMaxMinor != null ||
            normalized.dateFromInclusive != null ||
            normalized.dateToExclusive != null ||
            normalized.categoryId != null ||
            normalized.accountId != null ||
            normalized.type != null ||
            normalized.source != null ||
            normalized.state != null
    }
}
