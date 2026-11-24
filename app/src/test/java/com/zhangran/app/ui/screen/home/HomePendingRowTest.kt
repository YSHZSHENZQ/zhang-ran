/**
 * 首页待确认行与已确认行的金额语义（UX-1）。
 *
 * 待确认金额不得落到已确认支出的入账红。警告色与支出色在亮暗两套 token 里都不是同一个值。
 */
package com.zhangran.app.ui.screen.home

import com.zhangran.app.domain.model.TransactionType
import com.zhangran.app.ui.theme.DarkColors
import com.zhangran.app.ui.theme.LightColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomePendingRowTest {

    @Test
    fun 待确认金额不用已确认支出色() {
        TransactionType.entries.forEach { type ->
            assertEquals(HomeRowAmountTone.PENDING, homeRowAmountTone(pending = true, type = type))
        }
        assertEquals(HomeRowAmountTone.EXPENSE, homeRowAmountTone(false, TransactionType.EXPENSE))
        assertEquals(HomeRowAmountTone.INCOME, homeRowAmountTone(false, TransactionType.INCOME))
        assertEquals(HomeRowAmountTone.TRANSFER, homeRowAmountTone(false, TransactionType.TRANSFER))
        assertEquals(HomeRowAmountTone.INCOME, homeRowAmountTone(false, TransactionType.REFUND))
        assertNotEquals(HomeRowAmountTone.EXPENSE, HomeRowAmountTone.PENDING)
    }

    @Test
    fun 警告色与支出红不是同一个色值() {
        assertNotEquals(LightColors.expense, LightColors.warning)
        assertNotEquals(DarkColors.expense, DarkColors.warning)
        assertEquals(HOME_PENDING_LABEL, "待确认")
    }
}
