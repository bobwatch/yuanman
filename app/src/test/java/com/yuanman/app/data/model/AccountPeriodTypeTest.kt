package com.yuanman.app.data.model

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class AccountPeriodTypeTest {

    @Test
    fun testMonthPeriodInfo() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        }
        val info = AccountPeriodType.getPeriodInfo(cal.timeInMillis, AccountPeriodType.MONTH, startDayOfMonth = 1)
        assertEquals("2026-M09", info.periodKey)
        assertEquals("2026年9月", info.periodName)
        assertEquals("2026-M08", info.prevPeriodKey)
        assertEquals("2026年8月", info.prevPeriodName)
    }

    @Test
    fun testMonthPeriodWithCustomStartDay() {
        // 9月5号，若起始日为10号，应属于上一个周期 (8月10号~9月9号)
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 5, 12, 0, 0)
        }
        val info = AccountPeriodType.getPeriodInfo(cal.timeInMillis, AccountPeriodType.MONTH, startDayOfMonth = 10)
        assertEquals("2026-M08", info.periodKey)
        assertEquals("2026年8月", info.periodName)
        assertEquals("2026-M07", info.prevPeriodKey)
    }

    @Test
    fun testQuarterPeriodInfo() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0) // Q3
        }
        val info = AccountPeriodType.getPeriodInfo(cal.timeInMillis, AccountPeriodType.QUARTER)
        assertEquals("2026-Q3", info.periodKey)
        assertEquals("2026年第3季度", info.periodName)
        assertEquals("2026-Q2", info.prevPeriodKey)
        assertEquals("2026年第2季度", info.prevPeriodName)
    }

    @Test
    fun testHalfYearPeriodInfo() {
        val calH2 = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 1, 12, 0, 0)
        }
        val infoH2 = AccountPeriodType.getPeriodInfo(calH2.timeInMillis, AccountPeriodType.HALF_YEAR)
        assertEquals("2026-H2", infoH2.periodKey)
        assertEquals("2026年下半年", infoH2.periodName)
        assertEquals("2026-H1", infoH2.prevPeriodKey)
    }

    @Test
    fun testYearPeriodInfo() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.MAY, 1, 12, 0, 0)
        }
        val info = AccountPeriodType.getPeriodInfo(cal.timeInMillis, AccountPeriodType.YEAR)
        assertEquals("2026", info.periodKey)
        assertEquals("2026年度", info.periodName)
        assertEquals("2025", info.prevPeriodKey)
        assertEquals("2025年度", info.prevPeriodName)
    }
}
