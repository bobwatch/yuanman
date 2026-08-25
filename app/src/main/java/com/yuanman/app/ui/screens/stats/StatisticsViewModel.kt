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
import java.util.Calendar

enum class StatisticsPeriod(val title: String) {
    MONTH("按月统计"),
    YEAR("按年统计")
}

data class StatisticsUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val periodMode: StatisticsPeriod = StatisticsPeriod.MONTH,
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
    private val _periodMode = MutableStateFlow(StatisticsPeriod.MONTH)
    private val _selectedType = MutableStateFlow(RecordType.EXPENSE)
    private val _selectedCategory = MutableStateFlow<CategoryStatItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeRecordsFlow = combine(_selectedYear, _selectedMonth, _periodMode) { year, month, period ->
        Triple(year, month, period)
    }.flatMapLatest { (year, month, period) ->
        if (period == StatisticsPeriod.MONTH) {
            recordRepository.getRecordsByMonth(year, month)
        } else {
            recordRepository.getRecordsByYear(year)
        }
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
        _periodMode,
        _selectedType,
        _selectedCategory
    ) { year, month, period, type, activeCategory ->
        FiveParams(year, month, period, type, activeCategory)
    }.combine(activeRecordsFlow) { params, rawRecords ->
        Pair(params, rawRecords)
    }.combine(prevMonthRecordsFlow) { pair, prevRecords ->
        val params = pair.first
        val rawRecords = pair.second

        val year = params.year
        val month = params.month
        val period = params.period
        val type = params.type
        val activeCategory = params.activeCategory

        var totalExp = 0L
        var totalInc = 0L
        var expCount = 0
        var incCount = 0

        val isMonthMode = period == StatisticsPeriod.MONTH
        val daysInMonth = if (isMonthMode) DateTimeUtils.getDaysInMonth(year, month) else 12
        val trendExpenseMap = LongArray(if (isMonthMode) daysInMonth + 1 else 13)
        val trendIncomeMap = LongArray(if (isMonthMode) daysInMonth + 1 else 13)

        val targetTypeRecords = mutableListOf<RecordWithCategory>()
        val cal = Calendar.getInstance()

        rawRecords.forEach { item ->
            val slotIndex = if (isMonthMode) {
                DateTimeUtils.getDayOfMonth(item.record.recordTime).coerceIn(1, daysInMonth)
            } else {
                cal.timeInMillis = item.record.recordTime
                (cal.get(Calendar.MONTH) + 1).coerceIn(1, 12)
            }

            if (item.record.type == RecordType.EXPENSE.name) {
                totalExp += item.record.amount
                expCount++
                trendExpenseMap[slotIndex] += item.record.amount
            } else {
                totalInc += item.record.amount
                incCount++
                trendIncomeMap[slotIndex] += item.record.amount
            }

            if (item.record.type == type.name) {
                targetTypeRecords.add(item)
            }
        }

        // 计算上月收支以支持环比分析（月度模式下）
        var prevExp = 0L
        var prevInc = 0L
        if (isMonthMode) {
            prevRecords.forEach { item ->
                if (item.record.type == RecordType.EXPENSE.name) {
                    prevExp += item.record.amount
                } else {
                    prevInc += item.record.amount
                }
            }
        }

        val expenseDiff = if (isMonthMode && prevExp > 0L) ((totalExp - prevExp).toFloat() / prevExp.toFloat()) else null
        val incomeDiff = if (isMonthMode && prevInc > 0L) ((totalInc - prevInc).toFloat() / prevInc.toFloat()) else null

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

        // 趋势数据生成
        val trends = (1..daysInMonth).map { slot ->
            DailyTrendItem(
                day = slot,
                dateFormatted = if (isMonthMode) "$month-$slot" else "${slot}月",
                expenseAmount = trendExpenseMap[slot],
                incomeAmount = trendIncomeMap[slot]
            )
        }

        val maxExp = trends.maxOfOrNull { it.expenseAmount } ?: 0L
        val avgExp = if (isMonthMode) {
            if (daysInMonth > 0) totalExp / daysInMonth else 0L
        } else {
            totalExp / 12
        }

        // 智能消费洞察
        val topCategory = categoryStats.firstOrNull()
        val insightText = if (isMonthMode) {
            if (type == RecordType.EXPENSE) {
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
        } else {
            // 年度洞察
            if (type == RecordType.EXPENSE) {
                if (totalExp == 0L) {
                    "${year}年暂无支出记录，时光沉淀财富，未来皆可期 🌟"
                } else {
                    val peakMonthItem = trends.maxByOrNull { it.expenseAmount }
                    val peakMonthStr = peakMonthItem?.let { "${it.day}月" } ?: ""
                    "${year}年累计总支出 ¥${MoneyUtils.centsToYuanString(totalExp)}，月均支出 ¥${MoneyUtils.centsToYuanString(avgExp)}，开销最高月份为 $peakMonthStr 🏆"
                }
            } else {
                "${year}年累计总收入 ¥${MoneyUtils.centsToYuanString(totalInc)}，每一份收获都值得自豪与庆祝 🎊"
            }
        }

        StatisticsUiState(
            selectedYear = year,
            selectedMonth = month,
            periodMode = period,
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
            dailyTrends = trends,
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

    fun selectPeriod(period: StatisticsPeriod) {
        _periodMode.value = period
        _selectedCategory.value = null
    }

    fun selectMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
        _selectedCategory.value = null
    }

    fun previousPeriod() {
        if (_periodMode.value == StatisticsPeriod.YEAR) {
            _selectedYear.value -= 1
        } else {
            var y = _selectedYear.value
            var m = _selectedMonth.value - 1
            if (m < 1) {
                m = 12
                y -= 1
            }
            selectMonth(y, m)
        }
    }

    fun nextPeriod() {
        if (_periodMode.value == StatisticsPeriod.YEAR) {
            _selectedYear.value += 1
        } else {
            var y = _selectedYear.value
            var m = _selectedMonth.value + 1
            if (m > 12) {
                m = 1
                y += 1
            }
            selectMonth(y, m)
        }
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

private data class FiveParams(
    val year: Int,
    val month: Int,
    val period: StatisticsPeriod,
    val type: RecordType,
    val activeCategory: CategoryStatItem?
)
