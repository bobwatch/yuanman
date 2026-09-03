package com.yuanman.app.data.model

import java.util.Calendar

/**
 * 账户统计与对账周期类型
 */
enum class AccountPeriodType(val title: String) {
    MONTH("月度"),
    QUARTER("季度"),
    HALF_YEAR("半年度"),
    YEAR("年度");

    companion object {
        fun fromString(value: String): AccountPeriodType {
            return try {
                valueOf(value.uppercase())
            } catch (e: Exception) {
                MONTH
            }
        }

        /**
         * 获取指定时间戳在指定周期类型下的 PeriodKey 及起止时间戳
         * @param timestamp 目标时间戳
         * @param periodType 周期类型
         * @param startDayOfMonth 周期起始日（1-28，例如 10 代表每月10号至下月9号）
         */
        fun getPeriodInfo(
            timestamp: Long = System.currentTimeMillis(),
            periodType: AccountPeriodType,
            startDayOfMonth: Int = 1
        ): PeriodInfo {
            val cal = Calendar.getInstance().apply {
                timeInMillis = timestamp
            }
            val safeStartDay = startDayOfMonth.coerceIn(1, 28)

            return when (periodType) {
                MONTH -> {
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1 // 1-12
                    val day = cal.get(Calendar.DAY_OF_MONTH)

                    val (periodYear, periodMonth) = if (safeStartDay == 1) {
                        Pair(year, month)
                    } else {
                        if (day >= safeStartDay) {
                            Pair(year, month)
                        } else {
                            if (month == 1) Pair(year - 1, 12) else Pair(year, month - 1)
                        }
                    }

                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, periodYear)
                        set(Calendar.MONTH, periodMonth - 1)
                        set(Calendar.DAY_OF_MONTH, safeStartDay)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.MONTH, 1)
                        add(Calendar.MILLISECOND, -1)
                    }

                    val key = "%04d-M%02d".format(periodYear, periodMonth)
                    val name = "%d年%d月".format(periodYear, periodMonth)

                    // 上一周期
                    val prevStartCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.MONTH, -1)
                    }
                    val prevPeriodYear = prevStartCal.get(Calendar.YEAR)
                    val prevPeriodMonth = prevStartCal.get(Calendar.MONTH) + 1
                    val prevKey = "%04d-M%02d".format(prevPeriodYear, prevPeriodMonth)
                    val prevName = "%d年%d月".format(prevPeriodYear, prevPeriodMonth)

                    PeriodInfo(
                        periodKey = key,
                        periodName = name,
                        periodType = periodType,
                        startTimestamp = startCal.timeInMillis,
                        endTimestamp = endCal.timeInMillis,
                        prevPeriodKey = prevKey,
                        prevPeriodName = prevName
                    )
                }

                QUARTER -> {
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val quarter = (month - 1) / 3 + 1 // 1-4

                    val startMonth = (quarter - 1) * 3 + 1
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, startMonth - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.MONTH, 3)
                        add(Calendar.MILLISECOND, -1)
                    }

                    val key = "%04d-Q%d".format(year, quarter)
                    val name = "%d年第%d季度".format(year, quarter)

                    val (prevYear, prevQuarter) = if (quarter == 1) Pair(year - 1, 4) else Pair(year, quarter - 1)
                    val prevKey = "%04d-Q%d".format(prevYear, prevQuarter)
                    val prevName = "%d年第%d季度".format(prevYear, prevQuarter)

                    PeriodInfo(
                        periodKey = key,
                        periodName = name,
                        periodType = periodType,
                        startTimestamp = startCal.timeInMillis,
                        endTimestamp = endCal.timeInMillis,
                        prevPeriodKey = prevKey,
                        prevPeriodName = prevName
                    )
                }

                HALF_YEAR -> {
                    val year = cal.get(Calendar.YEAR)
                    val month = cal.get(Calendar.MONTH) + 1
                    val half = if (month <= 6) 1 else 2

                    val startMonth = if (half == 1) 1 else 7
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, startMonth - 1)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.MONTH, 6)
                        add(Calendar.MILLISECOND, -1)
                    }

                    val key = "%04d-H%d".format(year, half)
                    val name = "%d年%s半年".format(year, if (half == 1) "上" else "下")

                    val (prevYear, prevHalf) = if (half == 1) Pair(year - 1, 2) else Pair(year, 1)
                    val prevKey = "%04d-H%d".format(prevYear, prevHalf)
                    val prevName = "%d年%s半年".format(prevYear, if (prevHalf == 1) "上" else "下")

                    PeriodInfo(
                        periodKey = key,
                        periodName = name,
                        periodType = periodType,
                        startTimestamp = startCal.timeInMillis,
                        endTimestamp = endCal.timeInMillis,
                        prevPeriodKey = prevKey,
                        prevPeriodName = prevName
                    )
                }

                YEAR -> {
                    val year = cal.get(Calendar.YEAR)
                    val startCal = Calendar.getInstance().apply {
                        set(Calendar.YEAR, year)
                        set(Calendar.MONTH, 0)
                        set(Calendar.DAY_OF_MONTH, 1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }

                    val endCal = Calendar.getInstance().apply {
                        timeInMillis = startCal.timeInMillis
                        add(Calendar.YEAR, 1)
                        add(Calendar.MILLISECOND, -1)
                    }

                    val key = "%04d".format(year)
                    val name = "%d年度".format(year)

                    val prevYear = year - 1
                    val prevKey = "%04d".format(prevYear)
                    val prevName = "%d年度".format(prevYear)

                    PeriodInfo(
                        periodKey = key,
                        periodName = name,
                        periodType = periodType,
                        startTimestamp = startCal.timeInMillis,
                        endTimestamp = endCal.timeInMillis,
                        prevPeriodKey = prevKey,
                        prevPeriodName = prevName
                    )
                }
            }
        }
    }
}

data class PeriodInfo(
    val periodKey: String,
    val periodName: String,
    val periodType: AccountPeriodType,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val prevPeriodKey: String,
    val prevPeriodName: String
)
