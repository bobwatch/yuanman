package com.yuanman.app.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeUtilsTest {

    @Test
    fun testGetMonthStartAndEndTimestamp() {
        val start = DateTimeUtils.getMonthStartTimestamp(2026, 8)
        val end = DateTimeUtils.getMonthEndTimestamp(2026, 8)

        assertTrue(end > start)
        assertEquals(31, DateTimeUtils.getDaysInMonth(2026, 8))
        assertEquals(28, DateTimeUtils.getDaysInMonth(2026, 2)) // 2026 is non-leap
    }

    @Test
    fun testFormatYearMonth() {
        assertEquals("2026年8月", DateTimeUtils.formatYearMonth(2026, 8))
    }
}
