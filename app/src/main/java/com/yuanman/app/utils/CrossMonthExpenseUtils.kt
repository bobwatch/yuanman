package com.yuanman.app.utils

import java.util.Calendar
import kotlin.math.min

/** Helpers for turning a one-time payment into month-based ledger entries. */
object CrossMonthExpenseUtils {
    fun splitAmount(totalCents: Long, monthCount: Int): List<Long> {
        require(totalCents > 0L) { "totalCents must be positive" }
        require(monthCount > 0) { "monthCount must be positive" }

        val base = totalCents / monthCount
        val remainder = (totalCents % monthCount).toInt()
        return List(monthCount) { index -> base + if (index < remainder) 1L else 0L }
    }

    /** Keeps the original day where possible and clamps it for short months (e.g. Jan 31 -> Feb 28). */
    fun addMonthsKeepingDay(timestamp: Long, monthOffset: Int): Long {
        val source = Calendar.getInstance().apply { timeInMillis = timestamp }
        val originalDay = source.get(Calendar.DAY_OF_MONTH)
        val target = (source.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, min(originalDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        return target.timeInMillis
    }
}
