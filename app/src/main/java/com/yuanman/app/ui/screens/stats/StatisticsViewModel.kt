package com.yuanman.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.*
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class StatisticsUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val selectedType: RecordType = RecordType.EXPENSE,
    val summary: MonthSummaryData = MonthSummaryData(),
    val categoryStats: List<CategoryStatItem> = emptyList(),
    val dailyTrends: List<DailyTrendItem> = emptyList(),
    val isLoading: Boolean = false
)

class StatisticsViewModel(
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _selectedType = MutableStateFlow(RecordType.EXPENSE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        _selectedYear,
        _selectedMonth,
        _selectedType,
        monthRecordsFlow
    ) { year, month, type, rawRecords ->

        var totalExp = 0L
        var totalInc = 0L
        var expCount = 0
        var incCount = 0

        val daysInMonth = DateTimeUtils.getDaysInMonth(year, month)
        val dailyExpenseMap = LongArray(daysInMonth + 1)
        val dailyIncomeMap = LongArray(daysInMonth + 1)

        val targetTypeRecords = mutableListOf<RecordWithCategory>()

        rawRecords.forEach { item ->
            val day = DateTimeUtils.getDayOfMonth(item.record.recordTime).coerceIn(1, daysInMonth)
            if (item.record.type == RecordType.EXPENSE.name) {
                totalExp += item.record.amount
                expCount++
                dailyExpenseMap[day] += item.record.amount
            } else {
                totalInc += item.record.amount
                incCount++
                dailyIncomeMap[day] += item.record.amount
            }

            if (item.record.type == type.name) {
                targetTypeRecords.add(item)
            }
        }

        // 分类聚合统计
        val totalForTargetType = if (type == RecordType.EXPENSE) totalExp else totalInc
        val categoryGroupMap = targetTypeRecords.groupBy { it.category?.id ?: -1L }

        val categoryStats = categoryGroupMap.mapNotNull { (catId, records) ->
            val firstCategory = records.firstOrNull()?.category ?: CategoryEntity(
                id = -1L,
                name = "其他",
                type = type.name,
                iconName = "other",
                colorHex = 0xFF607D8BL
            )
            val sum = records.sumOf { it.record.amount }
            val count = records.size
            val pct = if (totalForTargetType > 0L) sum.toFloat() / totalForTargetType.toFloat() else 0f

            CategoryStatItem(
                category = firstCategory,
                totalAmount = sum,
                count = count,
                percentage = pct
            )
        }.sortedByDescending { it.totalAmount }

        // 每日趋势
        val dailyTrends = (1..daysInMonth).map { day ->
            DailyTrendItem(
                day = day,
                dateFormatted = "$month-$day",
                expenseAmount = dailyExpenseMap[day],
                incomeAmount = dailyIncomeMap[day]
            )
        }

        val maxExp = dailyTrends.maxOfOrNull { it.expenseAmount } ?: 0L
        val avgExp = if (daysInMonth > 0) totalExp / daysInMonth else 0L

        StatisticsUiState(
            selectedYear = year,
            selectedMonth = month,
            selectedType = type,
            summary = MonthSummaryData(
                totalExpense = totalExp,
                totalIncome = totalInc,
                balance = totalInc - totalExp,
                expenseCount = expCount,
                incomeCount = incCount,
                maxDailyExpense = maxExp,
                avgDailyExpense = avgExp
            ),
            categoryStats = categoryStats,
            dailyTrends = dailyTrends,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(
            selectedYear = currentYearMonth.first,
            selectedMonth = currentYearMonth.second,
            isLoading = true
        )
    )

    fun selectMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
    }

    fun selectType(type: RecordType) {
        _selectedType.value = type
    }

    class Factory(
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(recordRepository, categoryRepository) as T
        }
    }
}
