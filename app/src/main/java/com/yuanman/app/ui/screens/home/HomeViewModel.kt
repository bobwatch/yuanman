package com.yuanman.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.MonthSummaryData
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val summary: MonthSummaryData = MonthSummaryData(),
    val groupedRecords: Map<Long, List<RecordWithCategory>> = emptyMap(),
    val daySummaries: Map<Long, Pair<Long, Long>> = emptyMap(), // dayTimestamp -> (expense, income)
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val recordRepository: RecordRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)

    val defaultRecordType = preferencesRepository.defaultRecordType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordType.EXPENSE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        _selectedYear,
        _selectedMonth,
        monthRecordsFlow
    ) { year, month, records ->
        var totalExp = 0L
        var totalInc = 0L
        var expCount = 0
        var incCount = 0

        val grouped = LinkedHashMap<Long, MutableList<RecordWithCategory>>()
        val daySums = HashMap<Long, Pair<Long, Long>>()

        // 按日期归一化（00:00:00 时间戳）
        records.forEach { item ->
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = item.record.recordTime
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val dayKey = cal.timeInMillis

            grouped.getOrPut(dayKey) { mutableListOf() }.add(item)

            val currentDaySum = daySums[dayKey] ?: Pair(0L, 0L)
            if (item.record.type == RecordType.EXPENSE.name) {
                totalExp += item.record.amount
                expCount++
                daySums[dayKey] = Pair(currentDaySum.first + item.record.amount, currentDaySum.second)
            } else {
                totalInc += item.record.amount
                incCount++
                daySums[dayKey] = Pair(currentDaySum.first, currentDaySum.second + item.record.amount)
            }
        }

        HomeUiState(
            selectedYear = year,
            selectedMonth = month,
            summary = MonthSummaryData(
                totalExpense = totalExp,
                totalIncome = totalInc,
                balance = totalInc - totalExp,
                expenseCount = expCount,
                incomeCount = incCount
            ),
            groupedRecords = grouped,
            daySummaries = daySums,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(
            selectedYear = currentYearMonth.first,
            selectedMonth = currentYearMonth.second,
            isLoading = true
        )
    )

    fun selectMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
    }

    fun deleteRecord(recordWithCategory: RecordWithCategory) {
        viewModelScope.launch {
            recordRepository.deleteRecord(recordWithCategory.record)
        }
    }

    class Factory(
        private val recordRepository: RecordRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(recordRepository, preferencesRepository) as T
        }
    }
}
