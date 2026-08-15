package com.moneyhistory.app

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期工具（yyyy-MM-dd 字符串，打卡/心情按自然日记录用）。
 * 不使用 java.time（minSdk 24 且未启用 desugaring）。
 */
internal object DateUtils {

    private const val DAY_MILLIS = 86_400_000L

    fun today(): String = dateKey(System.currentTimeMillis())

    fun dateKey(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(millis))

    fun parse(key: String): Long? = try {
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).parse(key)?.time
    } catch (e: Exception) {
        null
    }

    /** [key] 日期加 [delta] 天（国内无 DST，按固定 24h 步进安全）。 */
    fun addDays(key: String, delta: Int): String {
        val time = parse(key) ?: return key
        return dateKey(time + delta * DAY_MILLIS)
    }

    /** 两个日期相差的天数（to - from）。 */
    fun daysBetween(from: String, to: String): Int {
        val f = parse(from) ?: return 0
        val t = parse(to) ?: return 0
        return ((t - f) / DAY_MILLIS).toInt()
    }

    /** 当前月份前缀 "yyyy-MM"。 */
    fun monthPrefix(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(millis))

    /** 由月份前缀与日号拼出 "yyyy-MM-dd"（月历网格的日期 key）。 */
    fun dateKeyOf(monthPrefix: String, day: Int): String =
        String.format(Locale.CHINA, "%s-%02d", monthPrefix, day)

    /** 上个月份前缀 "yyyy-MM"。 */
    fun lastMonthPrefix(): String {
        val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MONTH, -1) }
        return SimpleDateFormat("yyyy-MM", Locale.CHINA).format(cal.time)
    }
}
