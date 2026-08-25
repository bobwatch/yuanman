package com.yuanman.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

enum class RecordSortOrder(val title: String) {
    TIME_DESC("时间最新"),
    TIME_ASC("时间最早"),
    AMOUNT_DESC("金额最大"),
    AMOUNT_ASC("金额最小")
}

data class DayGroupSummary(
    val dayTimestamp: Long,
    val totalExpense: Long,
    val totalIncome: Long
)

data class RecordListUiState(
    val selectedYear: Int = 2026,
    val selectedMonth: Int = 8,
    val selectedDay: Int? = null,
    val selectedType: RecordType? = null,
    val selectedCategoryId: Long? = null,
    val selectedPaymentMethod: String? = null,
    val sortOrder: RecordSortOrder = RecordSortOrder.TIME_DESC,
    val searchQuery: String = "",
    val availableCategories: List<CategoryEntity> = emptyList(),
    val filteredRecords: List<RecordWithCategory> = emptyList(),
    val groupedRecords: Map<Long, List<RecordWithCategory>> = emptyMap(),
    val daySummaries: Map<Long, DayGroupSummary> = emptyMap(),
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val recordCount: Int = 0,
    val isPrivacyMode: Boolean = false,
    val isLoading: Boolean = false
)

private data class FilterParams(
    val year: Int,
    val month: Int,
    val day: Int?,
    val type: RecordType?,
    val categoryId: Long?,
    val paymentMethod: String?,
    val sortOrder: RecordSortOrder,
    val query: String
)

class RecordListViewModel(
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _selectedDay = MutableStateFlow<Int?>(null)
    private val _selectedType = MutableStateFlow<RecordType?>(null)
    private val _selectedCategoryId = MutableStateFlow<Long?>(null)
    private val _selectedPaymentMethod = MutableStateFlow<String?>(null)
    private val _sortOrder = MutableStateFlow(RecordSortOrder.TIME_DESC)
    private val _searchQuery = MutableStateFlow("")

    val allCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    private val filtersFlow = combine(
        _selectedYear,
        _selectedMonth,
        _selectedDay,
        _selectedType,
        _selectedCategoryId
    ) { year, month, day, type, categoryId ->
        Tuple5(year, month, day, type, categoryId)
    }.combine(
        combine(_selectedPaymentMethod, _sortOrder, _searchQuery) { pay, sort, query ->
            Triple(pay, sort, query)
        }
    ) { firstPart, secondPart ->
        FilterParams(
            year = firstPart.a,
            month = firstPart.b,
            day = firstPart.c,
            type = firstPart.d,
            categoryId = firstPart.e,
            paymentMethod = secondPart.first,
            sortOrder = secondPart.second,
            query = secondPart.third
        )
    }

    val uiState: StateFlow<RecordListUiState> = combine(
        filtersFlow,
        monthRecordsFlow,
        allCategories,
        preferencesRepository.privacyMode
    ) { filters, rawRecords, categories, privacy ->

        val filtered = rawRecords.filter { item ->
            val matchDay = filters.day == null || DateTimeUtils.getDayOfMonth(item.record.recordTime) == filters.day
            val matchType = filters.type == null || item.record.type == filters.type.name
            val matchCategory = filters.categoryId == null || item.record.categoryId == filters.categoryId
            val matchPayment = filters.paymentMethod == null || item.record.paymentMethod == filters.paymentMethod
            val matchQuery = filters.query.isBlank() ||
                    item.record.remark.contains(filters.query, ignoreCase = true) ||
                    (item.category?.name?.contains(filters.query, ignoreCase = true) == true) ||
                    item.record.paymentMethod.contains(filters.query, ignoreCase = true)

            matchDay && matchType && matchCategory && matchPayment && matchQuery
        }

        // 排序处理
        val sorted = when (filters.sortOrder) {
            RecordSortOrder.TIME_DESC -> filtered.sortedByDescending { it.record.recordTime }
            RecordSortOrder.TIME_ASC -> filtered.sortedBy { it.record.recordTime }
            RecordSortOrder.AMOUNT_DESC -> filtered.sortedByDescending { it.record.amount }
            RecordSortOrder.AMOUNT_ASC -> filtered.sortedBy { it.record.amount }
        }

        // 按自然日分组 (截取当天 00:00:00 毫秒戳)
        val grouped = sorted.groupBy { item ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = item.record.recordTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }

        // 计算每日小计与月度总和
        val daySums = mutableMapOf<Long, DayGroupSummary>()
        var totalExp = 0L
        var totalInc = 0L

        grouped.forEach { (dayTimestamp, list) ->
            var dayExp = 0L
            var dayInc = 0L
            list.forEach { rwc ->
                if (rwc.record.type == RecordType.EXPENSE.name) {
                    dayExp += rwc.record.amount
                    totalExp += rwc.record.amount
                } else {
                    dayInc += rwc.record.amount
                    totalInc += rwc.record.amount
                }
            }
            daySums[dayTimestamp] = DayGroupSummary(dayTimestamp, dayExp, dayInc)
        }

        RecordListUiState(
            selectedYear = filters.year,
            selectedMonth = filters.month,
            selectedDay = filters.day,
            selectedType = filters.type,
            selectedCategoryId = filters.categoryId,
            selectedPaymentMethod = filters.paymentMethod,
            sortOrder = filters.sortOrder,
            searchQuery = filters.query,
            availableCategories = categories,
            filteredRecords = sorted,
            groupedRecords = grouped,
            daySummaries = daySums,
            totalExpense = totalExp,
            totalIncome = totalInc,
            recordCount = sorted.size,
            isPrivacyMode = privacy,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecordListUiState(
            selectedYear = currentYearMonth.first,
            selectedMonth = currentYearMonth.second,
            isLoading = true
        )
    )

    fun selectMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        _selectedDay.value = null
    }

    fun selectDay(day: Int?) {
        _selectedDay.value = if (_selectedDay.value == day) null else day
    }

    fun selectDate(year: Int, month: Int, day: Int?) {
        _selectedYear.value = year
        _selectedMonth.value = month
        _selectedDay.value = day
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

    fun selectType(type: RecordType?) {
        _selectedType.value = type
        _selectedCategoryId.value = null
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = if (_selectedCategoryId.value == categoryId) null else categoryId
    }

    fun selectPaymentMethod(method: String?) {
        _selectedPaymentMethod.value = if (_selectedPaymentMethod.value == method) null else method
    }

    fun setSortOrder(order: RecordSortOrder) {
        _sortOrder.value = order
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

    class Factory(
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecordListViewModel(recordRepository, categoryRepository, preferencesRepository) as T
        }
    }
}
