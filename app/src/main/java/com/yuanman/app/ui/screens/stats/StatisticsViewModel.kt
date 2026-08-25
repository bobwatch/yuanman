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
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

data class StatisticsUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val selectedType: RecordType = RecordType.EXPENSE,
    val summary: MonthSummaryData = MonthSummaryData(),
    val categoryStats: List<CategoryStatItem> = emptyList(),
    val selectedCategory: CategoryStatItem? = null,
    val dailyTrends: List<DailyTrendItem> = emptyList(),
    val smartInsight: String = "",
    val prevMonthExpense: Long = 0L,
    val prevMonthIncome: Long = 0L,
    val expenseDiffPercent: Float? = null,
    val incomeDiffPercent: Float? = null,
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
    private val _selectedCategory = MutableStateFlow<CategoryStatItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val prevMonthRecordsFlow = combine(_selectedYear, _selectedMonth) { year, month ->
        val (pYear, pMonth) = if (month == 1) Pair(year - 1, 12) else Pair(year, month - 1)
        Pair(pYear, pMonth)
    }.flatMapLatest { (pYear, pMonth) ->
        recordRepository.getRecordsByMonth(pYear, pMonth)
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        _selectedYear,
        _selectedMonth,
        _selectedType,
        _selectedCategory,
        monthRecordsFlow
    ) { year, month, type, activeCategory, rawRecords ->
        Triple(Pair(year, month), Pair(type, activeCategory), rawRecords)
    }.combine(prevMonthRecordsFlow) { currentData, prevRecords ->
        val (year, month) = currentData.first
        val (type, activeCategory) = currentData.second
        val rawRecords = currentData.third

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

        // 计算上月收支以支持环比分析
        var prevExp = 0L
        var prevInc = 0L
        prevRecords.forEach { item ->
            if (item.record.type == RecordType.EXPENSE.name) {
                prevExp += item.record.amount
            } else {
                prevInc += item.record.amount
            }
        }

        val expenseDiff = if (prevExp > 0L) ((totalExp - prevExp).toFloat() / prevExp.toFloat()) else null
        val incomeDiff = if (prevInc > 0L) ((totalInc - prevInc).toFloat() / prevInc.toFloat()) else null

        // 分类聚合统计
        val totalForTargetType = if (type == RecordType.EXPENSE) totalExp else totalInc
        val categoryGroupMap = targetTypeRecords.groupBy { it.category?.id ?: -1L }

        val categoryStats = categoryGroupMap.mapNotNull { (_, records) ->
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

        // 生成智能消费洞察生活画像与温暖心语
        val topCategory = categoryStats.firstOrNull()
        val insightText = if (type == RecordType.EXPENSE) {
            if (totalExp == 0L) {
                "本月暂无支出记录，继续保持理性的生活节奏～ ✨"
            } else if (topCategory != null) {
                val pctStr = String.format(java.util.Locale.CHINA, "%.1f%%", topCategory.percentage * 100)
                when {
                    topCategory.category.name.contains("餐") ->
                        "本月最大开销是「${topCategory.category.name}」(占 $pctStr)，好好吃饭是最好的投资，但也别忘了荤素搭配、适度下厨哦～ 🍲"
                    topCategory.category.name.contains("购") || topCategory.category.name.contains("买") ->
                        "本月「${topCategory.category.name}」支出占了 $pctStr，理性拔草，给生活添置真正能带来幸福感的好物 🛍️"
                    topCategory.category.name.contains("住") || topCategory.category.name.contains("房") ->
                        "固定居住成本占了 $pctStr，守护属于自己的一方温馨天地，辛苦啦 🏡"
                    else ->
                        "本月消费主要集中在「${topCategory.category.name}」(占 $pctStr)，日均支出 ¥${MoneyUtils.centsToYuanString(avgExp)}，财务结构清晰有序 📈"
                }
            } else {
                "用心对待每一笔收支，让生活更有底气与从容 🌿"
            }
        } else {
            if (totalInc == 0L) {
                "本月暂未记录收入，期待每一份努力换来丰硕回报 🌱"
            } else {
                "本月累计收入 ¥${MoneyUtils.centsToYuanString(totalInc)}，每一笔进账都是辛勤付出的见证，继续加油！ 🎉"
            }
        }

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
            selectedCategory = activeCategory,
            dailyTrends = dailyTrends,
            smartInsight = insightText,
            prevMonthExpense = prevExp,
            prevMonthIncome = prevInc,
            expenseDiffPercent = expenseDiff,
            incomeDiffPercent = incomeDiff,
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
        _selectedCategory.value = null
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

    fun selectType(type: RecordType) {
        _selectedType.value = type
        _selectedCategory.value = null
    }

    fun selectCategory(item: CategoryStatItem?) {
        _selectedCategory.value = item
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
