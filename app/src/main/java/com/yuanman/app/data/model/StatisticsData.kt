package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.CategoryEntity

data class CategoryStatItem(
    val category: CategoryEntity,
    val totalAmount: Long, // in 分
    val count: Int,
    val percentage: Float // 0.0 - 1.0
)

data class DailyTrendItem(
    val day: Int,
    val dateFormatted: String, // e.g. "8-25"
    val expenseAmount: Long,   // in 分
    val incomeAmount: Long     // in 分
)

data class MonthSummaryData(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val balance: Long = 0L,
    val expenseCount: Int = 0,
    val incomeCount: Int = 0,
    val maxDailyExpense: Long = 0L,
    val avgDailyExpense: Long = 0L
)
