package com.yuanman.app.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateTimeUtils {
    private val dateTimeFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINESE)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.CHINESE)
    private val timeFormatter = SimpleDateFormat("HH:mm", Locale.CHINESE)
    private val yearMonthFormatter = SimpleDateFormat("yyyy年M月", Locale.CHINESE)
    private val monthDayFormatter = SimpleDateFormat("M月d日", Locale.CHINESE)

    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormatter.format(Date(timestamp))
    }

    fun formatDate(timestamp: Long): String {
        return dateFormatter.format(Date(timestamp))
    }

    fun formatTime(timestamp: Long): String {
        return timeFormatter.format(Date(timestamp))
    }

    fun formatYearMonth(timestamp: Long): String {
        return yearMonthFormatter.format(Date(timestamp))
    }

    fun formatYearMonth(year: Int, month: Int): String {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, year)
        cal.set(Calendar.MONTH, month - 1)
        return "${year}年${month}月"
    }

    fun formatMonthDayWithWeek(timestamp: Long): String {
        val date = Date(timestamp)
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()

        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        now.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val weekDay = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SUNDAY -> "星期日"
            Calendar.MONDAY -> "星期一"
            Calendar.TUESDAY -> "星期二"
            Calendar.WEDNESDAY -> "星期三"
            Calendar.THURSDAY -> "星期四"
            Calendar.FRIDAY -> "星期五"
            Calendar.SATURDAY -> "星期六"
            else -> ""
        }

        val dateStr = monthDayFormatter.format(date)
        return when {
            isToday -> "$dateStr 今天 · $weekDay"
            isYesterday -> "$dateStr 昨天 · $weekDay"
            else -> "$dateStr · $weekDay"
        }
    }

    fun formatRecordDateShort(timestamp: Long): String {
        val date = Date(timestamp)
        val cal = Calendar.getInstance().apply { time = date }
        val now = Calendar.getInstance()

        val isToday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        now.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)

        val timeStr = timeFormatter.format(date)
        return when {
            isToday -> "今天 $timeStr"
            isYesterday -> "昨天 $timeStr"
            cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) -> {
                "${cal.get(Calendar.MONTH) + 1}月${cal.get(Calendar.DAY_OF_MONTH)}日 $timeStr"
            }
            else -> dateTimeFormatter.format(date)
        }
    }

    fun getMonthStartTimestamp(year: Int, month: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getMonthEndTimestamp(year: Int, month: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, maxDay)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getYearStartTimestamp(year: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getYearEndTimestamp(year: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, 11)
            set(Calendar.DAY_OF_MONTH, 31)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun getCurrentYearMonth(): Pair<Int, Int> {
        val cal = Calendar.getInstance()
        return Pair(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }

    fun getCurrentYearWeek(): Pair<Int, Int> {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
        }
        val year = cal.get(Calendar.YEAR)
        val week = cal.get(Calendar.WEEK_OF_YEAR)
        return Pair(year, week)
    }

    fun getWeekStartTimestamp(year: Int, weekOfYear: Int): Long {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.WEEK_OF_YEAR, weekOfYear)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    fun getWeekEndTimestamp(year: Int, weekOfYear: Int): Long {
        val start = getWeekStartTimestamp(year, weekOfYear)
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            timeInMillis = start
            add(Calendar.DAY_OF_YEAR, 6)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return cal.timeInMillis
    }

    fun formatWeekRangeShort(startTime: Long, endTime: Long): String {
        val startCal = Calendar.getInstance().apply { timeInMillis = startTime }
        val endCal = Calendar.getInstance().apply { timeInMillis = endTime }
        val startM = startCal.get(Calendar.MONTH) + 1
        val startD = startCal.get(Calendar.DAY_OF_MONTH)
        val endM = endCal.get(Calendar.MONTH) + 1
        val endD = endCal.get(Calendar.DAY_OF_MONTH)
        return String.format(Locale.CHINA, "%02d.%02d - %02d.%02d", startM, startD, endM, endD)
    }

    fun getDayOfWeekIndex(timestamp: Long): Int {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            timeInMillis = timestamp
        }
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }
    }

    fun getWeekDayName(dayIndex: Int): String {
        return when (dayIndex) {
            1 -> "周一"
            2 -> "周二"
            3 -> "周三"
            4 -> "周四"
            5 -> "周五"
            6 -> "周六"
            7 -> "周日"
            else -> "周一"
        }
    }

    fun getMaxWeeksInYear(year: Int): Int {
        val cal = Calendar.getInstance(Locale.CHINA).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, 11)
            set(Calendar.DAY_OF_MONTH, 28)
        }
        return cal.getActualMaximum(Calendar.WEEK_OF_YEAR)
    }

    fun getDayOfMonth(timestamp: Long): Int {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.DAY_OF_MONTH)
    }

    fun getDaysInMonth(year: Int, month: Int): Int {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
}
