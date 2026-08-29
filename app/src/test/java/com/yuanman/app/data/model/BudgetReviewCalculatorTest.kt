package com.yuanman.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class BudgetReviewCalculatorTest {
    @Test
    fun currentMonthDetectsFastSpendingAndProjectsMonthEnd() {
        val now = calendar(2026, 8, 10)

        val review = BudgetReviewCalculator.calculate(
            year = 2026,
            month = 8,
            budgetCents = 310_000L,
            expenseCents = 180_000L,
            nowMillis = now
        )

        assertEquals(BudgetReviewStatus.SPENDING_FAST, review.status)
        assertEquals(130_000L, review.remainingCents)
        assertEquals(558_000L, review.projectedExpenseCents)
        assertEquals(22, review.remainingDays)
        assertTrue(review.usedPercent > review.calendarPercent)
    }

    @Test
    fun pastMonthIsClosedAndUsesActualExpenseAsProjection() {
        val review = BudgetReviewCalculator.calculate(
            year = 2026,
            month = 7,
            budgetCents = 500_000L,
            expenseCents = 420_000L,
            nowMillis = calendar(2026, 8, 10)
        )

        assertEquals(BudgetReviewStatus.CLOSED, review.status)
        assertEquals(420_000L, review.projectedExpenseCents)
        assertEquals(0, review.remainingDays)
    }

    @Test
    fun noBudgetStillKeepsExpenseForReview() {
        val review = BudgetReviewCalculator.calculate(
            year = 2026,
            month = 8,
            budgetCents = 0L,
            expenseCents = 12_300L,
            nowMillis = calendar(2026, 8, 10)
        )

        assertEquals(BudgetReviewStatus.NO_BUDGET, review.status)
        assertEquals(12_300L, review.expenseCents)
    }

    private fun calendar(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, 12, 0, 0)
        }.timeInMillis
}
