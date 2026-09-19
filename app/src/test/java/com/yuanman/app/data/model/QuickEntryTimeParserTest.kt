package com.yuanman.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 闪电记账的日期时间解析：缺省补全规则与分隔符最容易出错，
 * 一旦错位就会把金额当成日期或把日期写进备注，所以逐条锁定。
 */
class QuickEntryTimeParserTest {

    /** 固定一个基准时刻，避免测试随运行时间漂移：2026-09-18 15:42:07（周五） */
    private fun fixedNow(): Calendar = Calendar.getInstance().apply {
        set(Calendar.YEAR, 2026)
        set(Calendar.MONTH, Calendar.SEPTEMBER)
        set(Calendar.DAY_OF_MONTH, 18)
        set(Calendar.HOUR_OF_DAY, 15)
        set(Calendar.MINUTE, 42)
        set(Calendar.SECOND, 7)
        set(Calendar.MILLISECOND, 0)
    }

    private fun fields(millis: Long): List<Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return listOf(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }

    @Test
    fun `完整年月日与时分秒`() {
        val time = QuickEntryTimeParser.find("2026-03-05 14:30:45", fixedNow())
        assertNotNull(time)
        assertEquals(listOf(2026, 3, 5, 14, 30, 45), fields(time!!.timeMillis))
        assertTrue(time.hasDate)
        assertTrue(time.hasTime)
    }

    @Test
    fun `斜杠 减号 下划线 逗号分隔符等价`() {
        val expected = listOf(2026, 3, 5, 0, 0, 0)
        listOf("2026/3/5", "2026-3-5", "2026_3_5", "2026,3,5").forEach { input ->
            val time = QuickEntryTimeParser.find(input, fixedNow())
            assertNotNull("未解析: $input", time)
            assertEquals("解析不一致: $input", expected, fields(time!!.timeMillis))
        }
    }

    @Test
    fun `没写年默认今年 没写日默认今日`() {
        // 3/5 → 今年 3 月 5 日
        assertEquals(listOf(2026, 3, 5, 0, 0, 0), fields(QuickEntryTimeParser.find("3/5", fixedNow())!!.timeMillis))
        // 2025年 → 2025 年 9 月（本月）18 日（今日）
        assertEquals(listOf(2025, 9, 18, 0, 0, 0), fields(QuickEntryTimeParser.find("2025年", fixedNow())!!.timeMillis))
        // 3月 → 今年 3 月 18 日（今日）
        assertEquals(listOf(2026, 3, 18, 0, 0, 0), fields(QuickEntryTimeParser.find("3月", fixedNow())!!.timeMillis))
        // 2026年3月 → 3 月 18 日
        assertEquals(listOf(2026, 3, 18, 0, 0, 0), fields(QuickEntryTimeParser.find("2026年3月", fixedNow())!!.timeMillis))
    }

    @Test
    fun `只写时间按今天算且没写时分时为零点`() {
        assertEquals(listOf(2026, 9, 18, 14, 30, 0), fields(QuickEntryTimeParser.find("14:30", fixedNow())!!.timeMillis))
        // 只写日期不写时间 → 当天 00:00
        assertEquals(listOf(2026, 3, 5, 0, 0, 0), fields(QuickEntryTimeParser.find("3月5日", fixedNow())!!.timeMillis))
    }

    @Test
    fun `中午与晚上这类时段修饰`() {
        assertEquals(listOf(2026, 9, 18, 20, 30, 0), fields(QuickEntryTimeParser.find("晚上8点半", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 9, 18, 15, 0, 0), fields(QuickEntryTimeParser.find("下午3点", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 9, 18, 12, 0, 0), fields(QuickEntryTimeParser.find("中午12点", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 9, 18, 8, 5, 0), fields(QuickEntryTimeParser.find("早上8点5分", fixedNow())!!.timeMillis))
    }

    @Test
    fun `相对日按今天偏移`() {
        assertEquals(listOf(2026, 9, 17, 0, 0, 0), fields(QuickEntryTimeParser.find("昨天", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 9, 16, 0, 0, 0), fields(QuickEntryTimeParser.find("前天", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 9, 19, 21, 0, 0), fields(QuickEntryTimeParser.find("明天 21:00", fixedNow())!!.timeMillis))
    }

    @Test
    fun `两位年份补成两千年代 日在后的写法也能识别`() {
        assertEquals(listOf(2026, 3, 5, 0, 0, 0), fields(QuickEntryTimeParser.find("26/3/5", fixedNow())!!.timeMillis))
        assertEquals(listOf(2026, 3, 5, 0, 0, 0), fields(QuickEntryTimeParser.find("3/5/2026", fixedNow())!!.timeMillis))
    }

    @Test
    fun `没有日期时间信息时返回空`() {
        assertNull(QuickEntryTimeParser.find("奶茶 18", fixedNow()))
        assertNull(QuickEntryTimeParser.find("咖啡 15 微信", fixedNow()))
    }

    @Test
    fun `与文字粘连的短日期不当成日期`() {
        // 「7/11便利店」是店名，不是 7 月 11 日
        assertNull(QuickEntryTimeParser.find("7/11便利店 20", fixedNow()))
        assertNull(QuickEntryTimeParser.find("地铁7/11 20", fixedNow()))
        // 有空格分隔时仍然按日期解析
        assertNotNull(QuickEntryTimeParser.find("7/11 便利店 20", fixedNow()))
    }

    @Test
    fun `非法日期时间不误判`() {
        // 13 月 / 25 点不是合法日期时间，整段不当作日期
        assertNull(QuickEntryTimeParser.find("13/45", fixedNow()))
        assertNull(QuickEntryTimeParser.find("25:00", fixedNow()))
    }

    @Test
    fun `不存在的日期收到当月最后一天并由胶囊展示`() {
        // 2月30日 → 收敛到 2 月 28 日：解析结果会显示在时间胶囊里，用户可再改
        val time = QuickEntryTimeParser.find("2月30日", fixedNow())!!
        assertEquals(listOf(2026, 2, 28, 0, 0, 0), fields(time.timeMillis))
    }

    @Test
    fun `命中区间覆盖日期与时间两段原文`() {
        val time = QuickEntryTimeParser.find("2026-03-05 14:30 电影", fixedNow())!!
        val ranges = time.ranges.sortedBy { it.first }
        assertEquals(2, ranges.size)
        assertEquals("2026-03-05", "2026-03-05 14:30 电影".substring(ranges[0].first, ranges[0].last + 1))
        assertEquals("14:30", "2026-03-05 14:30 电影".substring(ranges[1].first, ranges[1].last + 1))
    }
}
