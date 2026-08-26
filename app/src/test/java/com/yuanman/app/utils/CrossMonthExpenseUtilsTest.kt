package com.yuanman.app.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CrossMonthExpenseUtilsTest {
    @Test
    fun splitAmount_preservesTotalAndDistributesRemainder() {
        val parts = CrossMonthExpenseUtils.splitAmount(100L, 3)

        assertEquals(listOf(34L, 33L, 33L), parts)
        assertEquals(100L, parts.sum())
    }

    @Test
    fun addMonthsKeepingDay_clampsShortMonth() {
        val start = Calendar.getInstance().apply {
            set(2024, Calendar.JANUARY, 31, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val result = Calendar.getInstance().apply {
            timeInMillis = CrossMonthExpenseUtils.addMonthsKeepingDay(start, 1)
        }

        assertEquals(2024, result.get(Calendar.YEAR))
        assertEquals(Calendar.FEBRUARY, result.get(Calendar.MONTH))
        assertEquals(29, result.get(Calendar.DAY_OF_MONTH))
    }
}
