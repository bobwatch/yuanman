package com.yuanman.app.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.dao.RecordFilterSummary
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val selectedCategoryIds: Set<Long> = emptySet(),
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
    val hasMore: Boolean = true,
    val isLoadingMore: Boolean = false,
    val isLoading: Boolean = false
)

private data class FilterParams(
    val year: Int,
    val month: Int,
    val day: Int?,
    val type: RecordType?,
    val categoryIds: Set<Long>,
    val paymentMethod: String?,
    val sortOrder: RecordSortOrder,
    val query: String
) {
    fun calculateTimestamps(): Pair<Long, Long> {
        return if (day != null) {
            val cal = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            Pair(start, cal.timeInMillis)
        } else {
            Pair(
                DateTimeUtils.getMonthStartTimestamp(year, month),
                DateTimeUtils.getMonthEndTimestamp(year, month)
            )
        }
    }
}

class RecordListViewModel(
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 25
    }

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _selectedDay = MutableStateFlow<Int?>(null)
    private val _selectedType = MutableStateFlow<RecordType?>(null)
    private val _selectedCategoryIds = MutableStateFlow<Set<Long>>(emptySet())
    private val _selectedPaymentMethod = MutableStateFlow<String?>(null)
    private val _sortOrder = MutableStateFlow(RecordSortOrder.TIME_DESC)
    private val _searchQuery = MutableStateFlow("")

    private val _loadedRecords = MutableStateFlow<List<RecordWithCategory>>(emptyList())
    private val _hasMore = MutableStateFlow(true)
    private val _isLoadingMore = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var currentLoadJob: Job? = null
    private var currentFilterParams: FilterParams? = null

    val allCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val filtersFlow = combine(
        _selectedYear,
        _selectedMonth,
        _selectedDay,
        _selectedType,
        _selectedCategoryIds
    ) { year, month, day, type, categoryIds ->
        Tuple5(year, month, day, type, categoryIds)
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
            categoryIds = firstPart.e,
            paymentMethod = secondPart.first,
            sortOrder = secondPart.second,
            query = secondPart.third
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val summaryFlow: Flow<RecordFilterSummary> = filtersFlow.flatMapLatest { params ->
        val (start, end) = params.calculateTimestamps()
        recordRepository.getFilteredSummary(
            startTime = start,
            endTime = end,
            type = params.type?.name,
            categoryIds = params.categoryIds.toList(),
            categoryFilterEnabled = if (params.categoryIds.isEmpty()) 0 else 1,
            paymentMethod = params.paymentMethod,
            searchQuery = params.query.trim()
        )
    }

    init {
        // 监听筛选条件变化，自动触发第一页加载
        viewModelScope.launch {
            filtersFlow.collectLatest { params ->
                currentFilterParams = params
                reloadFirstPage(params)
            }
        }

        // 分页列表本身是一次性查询，因此编辑页返回后需要根据数据库变更重新拉取
        // 当前筛选条件。Home 页使用 Room Flow 可自动刷新，但这里的分页查询不会。
        viewModelScope.launch {
            recordRepository.observeLatestRecordUpdate()
                .drop(1) // 忽略首次订阅时的初始值
                .collect {
                    currentFilterParams?.let { params -> reloadFirstPage(params, preserveExisting = true) }
                }
        }
    }

    private fun reloadFirstPage(params: FilterParams, preserveExisting: Boolean = false) {
        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            if (!preserveExisting || _loadedRecords.value.isEmpty()) {
                _isLoading.value = true
                _hasMore.value = true
                if (!preserveExisting) _loadedRecords.value = emptyList()
            }

            val (start, end) = params.calculateTimestamps()
            val initialList = recordRepository.getRecordsFilteredPaged(
                startTime = start,
                endTime = end,
                type = params.type?.name,
                categoryIds = params.categoryIds.toList(),
                categoryFilterEnabled = if (params.categoryIds.isEmpty()) 0 else 1,
                paymentMethod = params.paymentMethod,
                searchQuery = params.query.trim(),
                sortOrder = params.sortOrder.name,
                limit = PAGE_SIZE,
                offset = 0
            )

            _loadedRecords.value = initialList
            _hasMore.value = initialList.size >= PAGE_SIZE
            _isLoading.value = false
        }
    }

    /**
     * 下拉刷新当前筛选结果。刷新时保留现有列表，避免用户看到空白闪烁；
     * 初次加载仍由筛选条件变化触发并显示完整加载态。
     */
    fun refresh() {
        val params = currentFilterParams ?: return
        if (_isRefreshing.value) return

        currentLoadJob?.cancel()
        currentLoadJob = viewModelScope.launch {
            _isRefreshing.value = true
            // 取消首屏加载时不能遗留 isLoading=true，否则刷新完成后会一直显示加载态。
            _isLoading.value = false
            try {
                val (start, end) = params.calculateTimestamps()
                val refreshedList = recordRepository.getRecordsFilteredPaged(
                    startTime = start,
                    endTime = end,
                    type = params.type?.name,
                    categoryIds = params.categoryIds.toList(),
                    categoryFilterEnabled = if (params.categoryIds.isEmpty()) 0 else 1,
                    paymentMethod = params.paymentMethod,
                    searchQuery = params.query.trim(),
                    sortOrder = params.sortOrder.name,
                    limit = PAGE_SIZE,
                    offset = 0
                )
                _loadedRecords.value = refreshedList
                _hasMore.value = refreshedList.size >= PAGE_SIZE
                // 避免极快的本地查询让刷新箭头一闪而过。
                delay(220)
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun loadNextPage() {
        val params = currentFilterParams ?: return
        if (_isLoadingMore.value || !_hasMore.value || _isLoading.value) return

        viewModelScope.launch {
            _isLoadingMore.value = true
            val currentOffset = _loadedRecords.value.size
            val (start, end) = params.calculateTimestamps()

            val nextBatch = recordRepository.getRecordsFilteredPaged(
                startTime = start,
                endTime = end,
                type = params.type?.name,
                categoryIds = params.categoryIds.toList(),
                categoryFilterEnabled = if (params.categoryIds.isEmpty()) 0 else 1,
                paymentMethod = params.paymentMethod,
                searchQuery = params.query.trim(),
                sortOrder = params.sortOrder.name,
                limit = PAGE_SIZE,
                offset = currentOffset
            )

            if (nextBatch.isNotEmpty()) {
                val existingIds = _loadedRecords.value.map { it.record.id }.toSet()
                val distinctNew = nextBatch.filter { it.record.id !in existingIds }
                _loadedRecords.value = _loadedRecords.value + distinctNew
            }

            _hasMore.value = nextBatch.size >= PAGE_SIZE
            _isLoadingMore.value = false
        }
    }

    val uiState: StateFlow<RecordListUiState> = combine(
        filtersFlow,
        _loadedRecords,
        summaryFlow,
        allCategories
    ) { filters, records, summary, categories ->
        FourCombine(filters, records, summary, categories)
    }.combine(
        combine(preferencesRepository.privacyMode, _hasMore, _isLoadingMore, _isLoading) { privacy, hasMore, isLoadingMore, isLoading ->
            FourFlags(privacy, hasMore, isLoadingMore, isLoading)
        }
    ) { part1, part2 ->
        val filters = part1.filters
        val records = part1.records
        val summary = part1.summary
        val categories = part1.categories

        val privacy = part2.privacy
        val hasMore = part2.hasMore
        val isLoadingMore = part2.isLoadingMore
        val isLoading = part2.isLoading

        // 按自然日分组 (截取当天 00:00:00 毫秒戳)
        val grouped = records.groupBy { item ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = item.record.recordTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }

        // 计算每日小计
        val daySums = mutableMapOf<Long, DayGroupSummary>()
        grouped.forEach { (dayTimestamp, list) ->
            var dayExp = 0L
            var dayInc = 0L
            list.forEach { rwc ->
                if (rwc.record.type == RecordType.EXPENSE.name) {
                    dayExp += rwc.record.amount
                } else {
                    dayInc += rwc.record.amount
                }
            }
            daySums[dayTimestamp] = DayGroupSummary(dayTimestamp, dayExp, dayInc)
        }

        RecordListUiState(
            selectedYear = filters.year,
            selectedMonth = filters.month,
            selectedDay = filters.day,
            selectedType = filters.type,
            selectedCategoryIds = filters.categoryIds,
            selectedPaymentMethod = filters.paymentMethod,
            sortOrder = filters.sortOrder,
            searchQuery = filters.query,
            availableCategories = categories,
            filteredRecords = records,
            groupedRecords = grouped,
            daySummaries = daySums,
            totalExpense = summary.totalExpense,
            totalIncome = summary.totalIncome,
            recordCount = summary.totalCount,
            isPrivacyMode = privacy,
            hasMore = hasMore,
            isLoadingMore = isLoadingMore,
            isLoading = isLoading
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
        if (day != null) {
            val candidate = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month - 1)
                set(Calendar.DAY_OF_MONTH, day)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (candidate.timeInMillis > System.currentTimeMillis()) return
        }
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
        _selectedCategoryIds.value = emptySet()
        // 支出和收入的支付方式集合不同，切换类型时清除旧的支付方式筛选。
        _selectedPaymentMethod.value = null
    }

    fun selectCategory(categoryId: Long?) {
        if (categoryId == null) {
            _selectedCategoryIds.value = emptySet()
        } else {
            _selectedCategoryIds.value = _selectedCategoryIds.value.toMutableSet().apply {
                if (!add(categoryId)) remove(categoryId)
            }
        }
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
                updatedAt = System.currentTimeMillis(),
                syncId = java.util.UUID.randomUUID().toString(),
                deletedAt = null
            )
            recordRepository.insertRecord(duplicate)
            currentFilterParams?.let { reloadFirstPage(it) }
        }
    }

    fun deleteRecord(recordWithCategory: RecordWithCategory) {
        viewModelScope.launch {
            recordRepository.deleteRecord(recordWithCategory.record)
            _loadedRecords.value = _loadedRecords.value.filter { it.record.id != recordWithCategory.record.id }
        }
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
    private data class FourCombine(
        val filters: FilterParams,
        val records: List<RecordWithCategory>,
        val summary: RecordFilterSummary,
        val categories: List<CategoryEntity>
    )
    private data class FourFlags(
        val privacy: Boolean,
        val hasMore: Boolean,
        val isLoadingMore: Boolean,
        val isLoading: Boolean
    )

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
