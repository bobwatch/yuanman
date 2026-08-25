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
import java.util.Calendar

enum class RecordSortOrder(val title: String) {
    TIME_DESC("时间最新"),
    TIME_ASC("时间最早"),
    AMOUNT_DESC("金额最大"),
    AMOUNT_ASC("金额最小")
}

data class RecordListUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val selectedType: RecordType? = null, // null means 全部
    val selectedCategoryId: Long? = null,
    val selectedPaymentMethod: String? = null,
    val sortOrder: RecordSortOrder = RecordSortOrder.TIME_DESC,
    val searchQuery: String = "",
    val availableCategories: List<CategoryEntity> = emptyList(),
    val filteredRecords: List<RecordWithCategory> = emptyList(),
    val groupedRecords: Map<Long, List<RecordWithCategory>> = emptyMap(),
    val daySummaries: Map<Long, Pair<Long, Long>> = emptyMap(),
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val recordCount: Int = 0,
    val isPrivacyMode: Boolean = false,
    val isLoading: Boolean = false
)

private data class FilterParams(
    val year: Int,
    val month: Int,
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
        _selectedType,
        _selectedCategoryId,
        _selectedPaymentMethod
    ) { year, month, type, categoryId, paymentMethod ->
        Triple(Pair(year, month), Pair(type, categoryId), paymentMethod)
    }.combine(
        combine(_sortOrder, _searchQuery) { sort, query -> Pair(sort, query) }
    ) { mainFilters, secondFilters ->
        val (year, month) = mainFilters.first
        val (type, categoryId) = mainFilters.second
        val paymentMethod = mainFilters.third
        val (sortOrder, query) = secondFilters
        FilterParams(year, month, type, categoryId, paymentMethod, sortOrder, query)
    }

    val uiState: StateFlow<RecordListUiState> = combine(
        filtersFlow,
        monthRecordsFlow,
        allCategories,
        preferencesRepository.privacyMode
    ) { filters, rawRecords, categories, privacy ->

        val filtered = rawRecords.filter { item ->
            val matchType = filters.type == null || item.record.type == filters.type.name
            val matchCategory = filters.categoryId == null || item.record.categoryId == filters.categoryId
            val matchPayment = filters.paymentMethod == null || item.record.paymentMethod == filters.paymentMethod
            val matchQuery = filters.query.isBlank() ||
                    item.record.remark.contains(filters.query, ignoreCase = true) ||
                    (item.category?.name?.contains(filters.query, ignoreCase = true) == true) ||
                    item.record.paymentMethod.contains(filters.query, ignoreCase = true)

            matchType && matchCategory && matchPayment && matchQuery
        }

        // 排序处理
        val sorted = when (filters.sortOrder) {
            RecordSortOrder.TIME_DESC -> filtered.sortedByDescending { it.record.recordTime }
            RecordSortOrder.TIME_ASC -> filtered.sortedBy { it.record.recordTime }
            RecordSortOrder.AMOUNT_DESC -> filtered.sortedByDescending { it.record.amount }
            RecordSortOrder.AMOUNT_ASC -> filtered.sortedBy { it.record.amount }
        }

        var totalExp = 0L
        var totalInc = 0L
        val grouped = LinkedHashMap<Long, MutableList<RecordWithCategory>>()
        val daySums = HashMap<Long, Pair<Long, Long>>()

        sorted.forEach { item ->
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
                daySums[dayKey] = Pair(currentDaySum.first + item.record.amount, currentDaySum.second)
            } else {
                totalInc += item.record.amount
                daySums[dayKey] = Pair(currentDaySum.first, currentDaySum.second + item.record.amount)
            }
        }

        RecordListUiState(
            selectedYear = filters.year,
            selectedMonth = filters.month,
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
        _selectedCategoryId.value = categoryId
    }

    fun selectPaymentMethod(method: String?) {
        _selectedPaymentMethod.value = method
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
