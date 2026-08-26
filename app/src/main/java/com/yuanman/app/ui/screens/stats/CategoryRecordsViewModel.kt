package com.yuanman.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class CategoryRecordsUiState(
    val category: CategoryEntity? = null,
    val selectedYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val selectedMonth: Int = Calendar.getInstance().get(Calendar.MONTH) + 1,
    val isAllTime: Boolean = false,
    val records: List<RecordWithCategory> = emptyList(),
    val groupedRecords: Map<Long, List<RecordWithCategory>> = emptyMap(),
    val daySummaries: Map<Long, CategoryDaySummary> = emptyMap(),
    val totalAmount: Long = 0L,
    val recordCount: Int = 0,
    val avgAmount: Long = 0L,
    val maxAmount: Long = 0L,
    val isLoading: Boolean = false
)

data class CategoryDaySummary(
    val dayTimestamp: Long,
    val totalExpense: Long,
    val totalIncome: Long
)

/**
 * 分类专属账单详情页 ViewModel
 */
class CategoryRecordsViewModel(
    private val categoryId: Long,
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val currentCal = Calendar.getInstance()
    private val _selectedYear = MutableStateFlow(currentCal.get(Calendar.YEAR))
    private val _selectedMonth = MutableStateFlow(currentCal.get(Calendar.MONTH) + 1)
    private val _isAllTime = MutableStateFlow(false)

    val uiState: StateFlow<CategoryRecordsUiState> = combine(
        categoryRepository.getCategoryByIdFlow(categoryId),
        recordRepository.getRecordsByCategoryId(categoryId),
        _selectedYear,
        _selectedMonth,
        _isAllTime
    ) { category, allRecords, year, month, isAllTime ->
        val filteredRecords = if (isAllTime) {
            allRecords
        } else {
            allRecords.filter { rwc ->
                val cal = Calendar.getInstance().apply { timeInMillis = rwc.record.recordTime }
                cal.get(Calendar.YEAR) == year && (cal.get(Calendar.MONTH) + 1) == month
            }
        }

        var total = 0L
        var maxAmt = 0L
        filteredRecords.forEach { item ->
            total += item.record.amount
            if (item.record.amount > maxAmt) {
                maxAmt = item.record.amount
            }
        }

        val count = filteredRecords.size
        val avg = if (count > 0) total / count else 0L

        // 按自然日分组 (截取当天 00:00:00 毫秒戳)
        val grouped = filteredRecords.groupBy { item ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = item.record.recordTime
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            cal.timeInMillis
        }

        val daySums = mutableMapOf<Long, CategoryDaySummary>()
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
            daySums[dayTimestamp] = CategoryDaySummary(dayTimestamp, dayExp, dayInc)
        }

        CategoryRecordsUiState(
            category = category,
            selectedYear = year,
            selectedMonth = month,
            isAllTime = isAllTime,
            records = filteredRecords,
            groupedRecords = grouped,
            daySummaries = daySums,
            totalAmount = total,
            recordCount = count,
            avgAmount = avg,
            maxAmount = maxAmt,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoryRecordsUiState(isLoading = true)
    )

    fun selectMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        _isAllTime.value = false
    }

    fun toggleAllTime() {
        _isAllTime.value = !_isAllTime.value
    }

    class Factory(
        private val categoryId: Long,
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CategoryRecordsViewModel(categoryId, recordRepository, categoryRepository) as T
        }
    }
}
