package com.yuanman.app.data.model

import com.yuanman.app.utils.DateTimeUtils
import java.util.Calendar

enum class BudgetReviewStatus {
    NO_BUDGET,
    PLANNED,
    ON_TRACK,
    SPENDING_FAST,
    OVER_BUDGET,
    CLOSED
}

data class BudgetReviewData(
    val budgetCents: Long = 0L,
    val expenseCents: Long = 0L,
    val remainingCents: Long = 0L,
    val usedPercent: Float = 0f,
    val calendarPercent: Float = 0f,
    val paceDeltaPercent: Float = 0f,
    val projectedExpenseCents: Long = 0L,
    val dailyAvailableCents: Long = 0L,
    val remainingDays: Int = 0,
    val status: BudgetReviewStatus = BudgetReviewStatus.NO_BUDGET
)

object BudgetReviewCalculator {
    fun calculate(
        year: Int,
        month: Int,
        budgetCents: Long,
        expenseCents: Long,
        nowMillis: Long = System.currentTimeMillis()
    ): BudgetReviewData {
        val safeBudget = budgetCents.coerceAtLeast(0L)
        val safeExpense = expenseCents.coerceAtLeast(0L)
        val totalDays = DateTimeUtils.getDaysInMonth(year, month)
        val now = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val relation = compareYearMonth(year, month, currentYear, currentMonth)

        val elapsedDays = when {
            relation < 0 -> totalDays
            relation > 0 -> 0
            else -> now.get(Calendar.DAY_OF_MONTH).coerceIn(1, totalDays)
        }
        val remainingDays = when {
            relation < 0 -> 0
            relation > 0 -> totalDays
            else -> (totalDays - elapsedDays + 1).coerceAtLeast(1)
        }

        if (safeBudget == 0L) {
            return BudgetReviewData(
                budgetCents = 0L,
                expenseCents = safeExpense,
                remainingCents = 0L,
                calendarPercent = elapsedDays.toFloat() / totalDays.toFloat(),
                projectedExpenseCents = if (relation == 0 && elapsedDays > 0) {
                    safeExpense * totalDays / elapsedDays
                } else {
                    safeExpense
                },
                remainingDays = remainingDays,
                status = BudgetReviewStatus.NO_BUDGET
            )
        }

        val remaining = safeBudget - safeExpense
        val usedPercent = safeExpense.toFloat() / safeBudget.toFloat()
        val calendarPercent = elapsedDays.toFloat() / totalDays.toFloat()
        val projectedExpense = when {
            relation < 0 -> safeExpense
            relation > 0 || elapsedDays == 0 -> safeExpense
            else -> safeExpense * totalDays / elapsedDays
        }
        val dailyAvailable = if (remaining > 0L && remainingDays > 0) {
            remaining / remainingDays
        } else {
            0L
        }
        val status = when {
            remaining < 0L -> BudgetReviewStatus.OVER_BUDGET
            relation < 0 -> BudgetReviewStatus.CLOSED
            relation > 0 -> BudgetReviewStatus.PLANNED
            usedPercent - calendarPercent > 0.08f -> BudgetReviewStatus.SPENDING_FAST
            else -> BudgetReviewStatus.ON_TRACK
        }

        return BudgetReviewData(
            budgetCents = safeBudget,
            expenseCents = safeExpense,
            remainingCents = remaining,
            usedPercent = usedPercent,
            calendarPercent = calendarPercent,
            paceDeltaPercent = usedPercent - calendarPercent,
            projectedExpenseCents = projectedExpense,
            dailyAvailableCents = dailyAvailable,
            remainingDays = remainingDays,
            status = status
        )
    }

    private fun compareYearMonth(year: Int, month: Int, otherYear: Int, otherMonth: Int): Int {
        return (year * 12 + month).compareTo(otherYear * 12 + otherMonth)
    }
}
