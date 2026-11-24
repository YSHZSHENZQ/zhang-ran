/**
 * 首页点进某一笔待确认时，只展示这一笔的操作（UX-1）。
 */
package com.zhangran.app.ui.screen.pending

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingFocusTest {

    private fun row(
        id: String,
        canConfirm: Boolean = true,
        canEdit: Boolean = true,
        canReview: Boolean = false,
    ) = PendingRow(
        transactionId = id,
        amountText = "¥12.00",
        merchantText = "示例商户",
        reasonText = if (canReview) "疑似重复" else "分类不确定",
        sourceText = "无障碍",
        timeText = "08:00",
        canConfirm = canConfirm,
        canEdit = canEdit,
        canIgnore = true,
        canReview = canReview,
    )

    @Test
    fun 不聚焦时保留全部待确认() {
        val rows = listOf(row("a"), row("b", canConfirm = false, canEdit = false, canReview = true))
        assertEquals(rows, PendingViewModel.rowsForFocus(rows, focusTransactionId = ""))
    }

    @Test
    fun 聚焦普通待确认只留这一笔且仍可确认() {
        val normal = row("normal")
        val duplicate = row("dup", canConfirm = false, canEdit = false, canReview = true)
        val shown = PendingViewModel.rowsForFocus(listOf(duplicate, normal), "normal")
        assertEquals(listOf(normal), shown)
        assertEquals(true, shown?.single()?.canConfirm)
        assertEquals(false, shown?.single()?.canReview)
    }

    @Test
    fun 聚焦疑似重复只留查看重复且不能直接确认() {
        val normal = row("normal")
        val duplicate = row("dup", canConfirm = false, canEdit = false, canReview = true)
        val shown = PendingViewModel.rowsForFocus(listOf(normal, duplicate), "dup")
        assertEquals(listOf(duplicate), shown)
        assertEquals(false, shown?.single()?.canConfirm)
        assertEquals(false, shown?.single()?.canEdit)
        assertEquals(true, shown?.single()?.canReview)
        assertEquals(true, shown?.single()?.canIgnore)
    }

    @Test
    fun 聚焦的记录已经不在待确认时返回空() {
        assertNull(PendingViewModel.rowsForFocus(listOf(row("a")), "gone"))
    }
}
