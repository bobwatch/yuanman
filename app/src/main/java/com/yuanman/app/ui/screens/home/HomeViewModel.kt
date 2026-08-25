package com.yuanman.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.MonthSummaryData
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.WarmAffirmation
import com.yuanman.app.utils.WarmAffirmationsHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val summary: MonthSummaryData = MonthSummaryData(),
    val groupedRecords: Map<Long, List<RecordWithCategory>> = emptyMap(),
    val daySummaries: Map<Long, Pair<Long, Long>> = emptyMap(), // dayTimestamp -> (expense, income)
    val monthlyBudget: Long = 0L,
    val isPrivacyMode: Boolean = false,
    val affirmation: WarmAffirmation = WarmAffirmationsHelper.getAffirmationForCurrentTime(),
    val remainingDays: Int = 1,
    val remainingBudgetCents: Long = 0L,
    val dailyAvailableCents: Long = 0L,
    val budgetUsedPercent: Float = 0f,
    val isLoading: Boolean = false
)

private data class MonthInfo(
    val year: Int,
    val month: Int,
    val records: List<RecordWithCategory>
)

private data class PrefsInfo(
    val budget: Long,
    val privacy: Boolean,
    val affirmation: WarmAffirmation
)

class HomeViewModel(
    private val recordRepository: RecordRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _affirmationIndex = MutableStateFlow(0)
    private val _currentAffirmation = MutableStateFlow(WarmAffirmationsHelper.getAffirmationForCurrentTime())

    val defaultRecordType = preferencesRepository.defaultRecordType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordType.EXPENSE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    private val monthInfoFlow = combine(_selectedYear, _selectedMonth, monthRecordsFlow) { year, month, records ->
        MonthInfo(year, month, records)
    }

    private val prefsInfoFlow = combine(
        preferencesRepository.monthlyBudget,
        preferencesRepository.privacyMode,
        _currentAffirmation
    ) { budget, privacy, affirmation ->
        PrefsInfo(budget, privacy, affirmation)
    }

    val uiState: StateFlow<HomeUiState> = combine(monthInfoFlow, prefsInfoFlow) { monthInfo, prefsInfo ->
        val year = monthInfo.year
        val month = monthInfo.month
        val records = monthInfo.records
        val budget = prefsInfo.budget
        val privacy = prefsInfo.privacy
        val affirmation = prefsInfo.affirmation

        var totalExp = 0L
        var totalInc = 0L
        var expCount = 0
        var incCount = 0

        val grouped = LinkedHashMap<Long, MutableList<RecordWithCategory>>()
        val daySums = HashMap<Long, Pair<Long, Long>>()

        // 按日期归一化（00:00:00 时间戳）
        records.forEach { item ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = item.record.recordTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
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

        // 计算本月剩余天数与预算健康度
        val nowCal = Calendar.getInstance()
        val totalDaysInMonth = DateTimeUtils.getDaysInMonth(year, month)
        val currentDay = if (year == nowCal.get(Calendar.YEAR) && month == (nowCal.get(Calendar.MONTH) + 1)) {
            nowCal.get(Calendar.DAY_OF_MONTH)
        } else if (year < nowCal.get(Calendar.YEAR) || (year == nowCal.get(Calendar.YEAR) && month < (nowCal.get(Calendar.MONTH) + 1))) {
            totalDaysInMonth
        } else {
            1
        }
        val remainingDays = (totalDaysInMonth - currentDay + 1).coerceAtLeast(1)

        val remainingBudgetCents = if (budget > 0L) (budget - totalExp) else 0L
        val dailyAvailable = if (budget > 0L && remainingBudgetCents > 0L) {
            remainingBudgetCents / remainingDays
        } else {
            0L
        }
        val usedPercent = if (budget > 0L) (totalExp.toFloat() / budget.toFloat()).coerceAtLeast(0f) else 0f

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
            monthlyBudget = budget,
            isPrivacyMode = privacy,
            affirmation = affirmation,
            remainingDays = remainingDays,
            remainingBudgetCents = remainingBudgetCents,
            dailyAvailableCents = dailyAvailable,
            budgetUsedPercent = usedPercent,
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

    fun previousMonth() {
        var y = _selectedYear.value
        var m = _selectedMonth.value - 1
        if (m < 1) {
            m = 12
            y -= 1
        }
        selectMonth(y, m)
    }

    fun nextMonth() {
        var y = _selectedYear.value
        var m = _selectedMonth.value + 1
        if (m > 12) {
            m = 1
            y += 1
        }
        selectMonth(y, m)
    }

    fun togglePrivacy() {
        viewModelScope.launch {
            preferencesRepository.togglePrivacyMode()
        }
    }

    fun nextAffirmation() {
        val (next, index) = WarmAffirmationsHelper.getRandomAffirmation(_affirmationIndex.value)
        _affirmationIndex.value = index
        _currentAffirmation.value = next
    }

    fun copyRecord(record: RecordEntity) {
        viewModelScope.launch {
            val duplicate = record.copy(
                id = 0L,
                recordTime = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            recordRepository.insertRecord(duplicate)
        }
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
