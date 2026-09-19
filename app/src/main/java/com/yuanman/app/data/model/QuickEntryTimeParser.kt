package com.yuanman.app.data.model

import java.util.Calendar

/**
 * 闪电记账解析出的记账时间候选。
 *
 * @param ranges 命中的原文区间（可能同时含日期与时间两段），用于从备注与金额中剔除。
 * @param hasDate 是否显式写了日期（年 / 月 / 日 任一，或「昨天」这类相对日）
 * @param hasTime 是否显式写了时间（时 / 分 / 秒任一）
 */
data class QuickEntryTime(
    val timeMillis: Long,
    val ranges: List<IntRange>,
    val matchedText: String,
    val hasDate: Boolean,
    val hasTime: Boolean
) {
    /** 用户是否显式写了日期或时间；false 表示按当前时刻记账。 */
    val isExplicit: Boolean get() = hasDate || hasTime
}

/**
 * 闪电记账的日期时间智能解析。
 *
 * 支持写法（年月日时分秒都可以只写一部分，缺的按「今年 / 本月 / 今日 / 00:00」补全）：
 * - 年月日：`2026-03-05`、`26/3/5`、`3/5`、`3-5`、`3_5`、`2026年3月5日`、`3月5日`、`26年`
 * - 时分秒：`14:30`、`14:30:45`、`下午3点`、`晚上8点半`… → 简写为 `8点30`
 * - 相对日：`今天`「昨天」「前天」「明天」「后天」
 * - 分隔符：`/`、`-`、`_`，以及带 4 位年份时的 `,`（逗号只用于 `2026,3,5` 这类完整日期，
 *   避免与「12,5」这种金额小数写法冲突）
 *
 * 补全规则：写了日期没写时间 → 当天 00:00；写了时间没写日期 → 今天该时刻；
 * 日期时间都没写 → 返回 null，由调用方按当前时刻记账。
 */
object QuickEntryTimeParser {

    private data class DatePart(val year: Int?, val month: Int?, val day: Int?, val range: IntRange)
    private data class TimePart(val hour: Int, val minute: Int, val second: Int, val range: IntRange)

    // 中文写法：2026年3月5日 / 2026年3月 / 2026年 / 3月5日 / 3月
    private val dateCnFull = Regex("(?<!\\d)(\\d{2,4})\\s*年\\s*(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val dateCnYearMonth = Regex("(?<!\\d)(\\d{2,4})\\s*年\\s*(\\d{1,2})\\s*月")
    private val dateCnYear = Regex("(?<!\\d)(\\d{2,4})\\s*年(?!\\s*\\d)")
    private val dateCnMonthDay = Regex("(?<!\\d)(\\d{1,2})\\s*月\\s*(\\d{1,2})\\s*[日号]?")
    private val dateCnMonth = Regex("(?<!\\d)(\\d{1,2})\\s*月(?!\\s*\\d)")

    // 数字写法：年在前 / 日在后 / 只有年月 / 只有月日 / 逗号完整日期
    // 三段数字：2026-3-5 / 26/3/5 / 3/5/2026 / 3/5/26 都由这一条覆盖，年份位置按位数判定
    private val dateThreeParts = Regex("(?<!\\d)(\\d{1,4})\\s*[/\\-_]\\s*(\\d{1,2})\\s*[/\\-_]\\s*(\\d{1,4})(?!\\d)")
    private val dateYearMonth = Regex("(?<!\\d)(\\d{3,4})\\s*[/\\-_]\\s*(\\d{1,2})(?!\\d)")
    private val dateMonthDay = Regex("(?<!\\d)(\\d{1,2})\\s*[/\\-_]\\s*(\\d{1,2})(?!\\d)")
    private val dateComma = Regex("(?<!\\d)(\\d{3,4})\\s*[,，]\\s*(\\d{1,2})\\s*[,，]\\s*(\\d{1,2})(?!\\d)")

    private val timeColon = Regex("(?<!\\d)(\\d{1,2})\\s*[:：]\\s*(\\d{1,2})(?:\\s*[:：]\\s*(\\d{1,2}))?(?!\\d)")
    private val timeCnPoint = Regex("(?<!\\d)(\\d{1,2})\\s*[点時时]\\s*(半|(\\d{1,2})\\s*分?)?(?!\\d)")

    private val relativeDay = Regex("(今天|今日|昨天|昨日|前天|明天|后天)")
    private val relativeDayOffsets = mapOf(
        "今天" to 0, "今日" to 0,
        "昨天" to -1, "昨日" to -1,
        "前天" to -2,
        "明天" to 1, "后天" to 2
    )
    private val hourModifier = Regex("(凌晨|早上|早晨|上午|中午|下午|傍晚|晚上|夜里|夜间|深夜)\\s*$")

    fun find(text: String, now: Calendar = Calendar.getInstance()): QuickEntryTime? {
        val relative = relativeDay.find(text)
        val relativeOffset = relative?.let { relativeDayOffsets[it.value] }
        val date = findDatePart(text)
        val time = findTimePart(text)

        val hasDate = date != null || relativeOffset != null
        if (!hasDate && time == null) return null

        val calendar = now.clone() as Calendar
        calendar.set(Calendar.MILLISECOND, 0)
        if (time != null) {
            calendar.set(Calendar.HOUR_OF_DAY, time.hour)
            calendar.set(Calendar.MINUTE, time.minute)
            calendar.set(Calendar.SECOND, time.second)
        } else {
            // 写了日期但没写时分 → 当天 00:00
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
        }

        when {
            relativeOffset != null -> calendar.add(Calendar.DAY_OF_MONTH, relativeOffset)
            date != null -> {
                val year = date.year ?: (now.get(Calendar.YEAR))
                val month = date.month ?: (now.get(Calendar.MONTH) + 1)
                val day = date.day ?: now.get(Calendar.DAY_OF_MONTH)
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month - 1)
                calendar.set(Calendar.DAY_OF_MONTH, day.coerceAtMost(calendar.getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
        }

        val ranges = listOfNotNull(date?.range, time?.range, relative?.range).sortedBy { it.first }
        val matchedText = ranges.joinToString(" ") { text.substring(it.first, it.last + 1) }.trim()
        return QuickEntryTime(
            timeMillis = calendar.timeInMillis,
            ranges = ranges,
            matchedText = matchedText,
            hasDate = hasDate,
            hasTime = time != null
        )
    }

    /** 收集所有日期写法，取最靠前（同位置取最长）的一条有效候选。 */
    private fun findDatePart(text: String): DatePart? {
        val candidates = mutableListOf<DatePart>()

        dateCnFull.find(text)?.let { m ->
            validDate(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateCnYearMonth.find(text)?.let { m ->
            validDate(m.groupValues[1], m.groupValues[2], null)?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateCnMonthDay.find(text)?.let { m ->
            validDate(null, m.groupValues[1], m.groupValues[2])?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateCnYear.find(text)?.let { m ->
            validDate(m.groupValues[1], null, null)?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateCnMonth.find(text)?.let { m ->
            validDate(null, m.groupValues[1], null)?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateThreeParts.find(text)?.let { m ->
            val first = m.groupValues[1]
            val middle = m.groupValues[2]
            val last = m.groupValues[3]
            val parts = when {
                first.length == 4 -> Triple(first, middle, last)
                last.length == 4 -> Triple(last, first, middle)
                // 两位年份：26/3/5 里首位超过 12 只能是年；3/5/26 末位两位按年补 20xx
                first.length == 2 && (first.toIntOrNull() ?: 0) > 12 -> Triple(first, middle, last)
                last.length == 2 && first.length <= 2 -> Triple(last, first, middle)
                else -> null
            }
            if (parts != null) {
                validDate(parts.first, parts.second, parts.third)?.let { (y, mo, d) ->
                    candidates += DatePart(y, mo, d, m.range)
                }
            }
        }
        dateComma.find(text)?.let { m ->
            validDate(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateYearMonth.find(text)?.let { m ->
            validDate(m.groupValues[1], m.groupValues[2], null)?.let { (y, mo, d) ->
                candidates += DatePart(y, mo, d, m.range)
            }
        }
        dateMonthDay.find(text)?.let { m ->
            // 短写法（3/5）容易和「7/11便利店」这类文本粘连，要求两侧是空格 / 标点 / 边界
            if (hasClearBoundary(text, m.range)) {
                validDate(null, m.groupValues[1], m.groupValues[2])?.let { (y, mo, d) ->
                    candidates += DatePart(y, mo, d, m.range)
                }
            }
        }

        return candidates.minWithOrNull(compareBy({ it.range.first }, { -it.range.last }))
    }

    private fun hasClearBoundary(text: String, range: IntRange): Boolean {
        val beforeOk = range.first == 0 || !isWordChar(text[range.first - 1])
        val afterOk = range.last == text.lastIndex || !isWordChar(text[range.last + 1])
        return beforeOk && afterOk
    }

    private fun isWordChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '/' || char == '-' || char == '_'

    private fun findTimePart(text: String): TimePart? {
        val candidates = mutableListOf<TimePart>()

        timeColon.find(text)?.let { m ->
            validTime(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifBlank { "0" }, text, m.range)
                ?.let { candidates += it }
        }
        timeCnPoint.find(text)?.let { m ->
            val minute = when {
                m.groupValues[2] == "半" -> "30"
                m.groupValues[3].isNotBlank() -> m.groupValues[3]
                else -> "0"
            }
            validTime(m.groupValues[1], minute, "0", text, m.range)
                ?.let { candidates += it }
        }

        return candidates.minWithOrNull(compareBy({ it.range.first }, { -it.range.last }))
    }

    /** 校验并归一化日期：没写的部分返回 null，由调用方按「今年 / 本月 / 今日」补全。 */
    private fun validDate(yearText: String?, monthText: String?, dayText: String?): Triple<Int?, Int?, Int?>? {
        val year = yearText?.toIntOrNull()?.let { raw ->
            when {
                raw in 0..99 -> 2000 + raw
                raw in 1900..2999 -> raw
                else -> return null
            }
        }
        val month = monthText?.toIntOrNull()?.takeIf { it in 1..12 } ?: if (monthText == null) null else return null
        val day = dayText?.toIntOrNull()?.takeIf { it in 1..31 } ?: if (dayText == null) null else return null
        return Triple(year, month, day)
    }

    private fun validTime(
        hourText: String,
        minuteText: String,
        secondText: String,
        text: String,
        range: IntRange
    ): TimePart? {
        val rawHour = hourText.toIntOrNull() ?: return null
        val minute = minuteText.toIntOrNull() ?: 0
        val second = secondText.toIntOrNull() ?: 0
        if (minute !in 0..59 || second !in 0..59) return null

        val modifier = hourModifier.find(text.substring(0, range.first))?.value
        val hour = when {
            modifier == "中午" -> if (rawHour in 1..11) rawHour + 12 else rawHour
            modifier in setOf("下午", "傍晚", "晚上", "夜里", "夜间", "深夜") -> if (rawHour in 1..11) rawHour + 12 else rawHour
            else -> rawHour
        }
        if (hour !in 0..23) return null
        return TimePart(hour, minute, second, range)
    }
}
