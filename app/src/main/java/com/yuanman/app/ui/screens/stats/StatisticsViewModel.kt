package com.yuanman.app.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.*
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

enum class StatisticsPeriod(val title: String) {
    WEEK("周"),
    MONTH("月"),
    YEAR("年")
}

data class StatisticsUiState(
    val selectedYear: Int,
    val selectedMonth: Int,
    val selectedWeek: Int,
    val weekStartTimestamp: Long = 0L,
    val weekEndTimestamp: Long = 0L,
    val periodMode: StatisticsPeriod = StatisticsPeriod.MONTH,
    val selectedType: RecordType = RecordType.EXPENSE,
    val summary: MonthSummaryData = MonthSummaryData(),
    val categoryStats: List<CategoryStatItem> = emptyList(),
    val selectedCategory: CategoryStatItem? = null,
    val dailyTrends: List<DailyTrendItem> = emptyList(),
    val smartInsight: String = "",
    val prevPeriodExpense: Long = 0L,
    val prevPeriodIncome: Long = 0L,
    val expenseDiffPercent: Float? = null,
    val incomeDiffPercent: Float? = null,
    val budgetReview: BudgetReviewData = BudgetReviewData(),
    val isLoading: Boolean = false
)

class StatisticsViewModel(
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val currentYearWeek = DateTimeUtils.getCurrentYearWeek()

    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _selectedWeek = MutableStateFlow(currentYearWeek.second)
    private val _periodMode = MutableStateFlow(StatisticsPeriod.MONTH)
    private val _selectedType = MutableStateFlow(RecordType.EXPENSE)
    private val _selectedCategory = MutableStateFlow<CategoryStatItem?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val activeRecordsFlow = combine(_selectedYear, _selectedMonth, _selectedWeek, _periodMode) { year, month, week, period ->
        PeriodQuery(year, month, week, period)
    }.flatMapLatest { query ->
        when (query.period) {
            StatisticsPeriod.WEEK -> recordRepository.getRecordsByWeek(query.year, query.week)
            StatisticsPeriod.MONTH -> recordRepository.getRecordsByMonth(query.year, query.month)
            StatisticsPeriod.YEAR -> recordRepository.getRecordsByYear(query.year)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val prevPeriodRecordsFlow = combine(_selectedYear, _selectedMonth, _selectedWeek, _periodMode) { year, month, week, period ->
        PeriodQuery(year, month, week, period)
    }.flatMapLatest { query ->
        when (query.period) {
            StatisticsPeriod.WEEK -> {
                val (pYear, pWeek) = if (query.week <= 1) {
                    val prevY = query.year - 1
                    Pair(prevY, DateTimeUtils.getMaxWeeksInYear(prevY))
                } else {
                    Pair(query.year, query.week - 1)
                }
                recordRepository.getRecordsByWeek(pYear, pWeek)
            }
            StatisticsPeriod.MONTH -> {
                val (pYear, pMonth) = if (query.month == 1) Pair(query.year - 1, 12) else Pair(query.year, query.month - 1)
                recordRepository.getRecordsByMonth(pYear, pMonth)
            }
            StatisticsPeriod.YEAR -> {
                recordRepository.getRecordsByYear(query.year - 1)
            }
        }
    }

    val uiState: StateFlow<StatisticsUiState> = combine(
        _selectedYear,
        _selectedMonth,
        _selectedWeek,
        _periodMode,
        _selectedType
    ) { year, month, week, period, type ->
        PeriodHeader(year, month, week, period, type)
    }.combine(_selectedCategory) { header, activeCategory ->
        SixParams(header.year, header.month, header.week, header.period, header.type, activeCategory)
    }.combine(activeRecordsFlow) { params, rawRecords ->
        Pair(params, rawRecords)
    }.combine(prevPeriodRecordsFlow) { pair, prevRecords ->
        val params = pair.first
        val rawRecords = pair.second

        val year = params.year
        val month = params.month
        val week = params.week
        val period = params.period
        val type = params.type
        val activeCategory = params.activeCategory

        val weekStart = if (period == StatisticsPeriod.WEEK) DateTimeUtils.getWeekStartTimestamp(year, week) else 0L
        val weekEnd = if (period == StatisticsPeriod.WEEK) DateTimeUtils.getWeekEndTimestamp(year, week) else 0L

        var totalExp = 0L
        var totalInc = 0L
        var expCount = 0
        var incCount = 0

        val daysInMonth = if (period == StatisticsPeriod.MONTH) DateTimeUtils.getDaysInMonth(year, month) else 12
        val trendSlots = when (period) {
            StatisticsPeriod.WEEK -> 7
            StatisticsPeriod.MONTH -> daysInMonth
            StatisticsPeriod.YEAR -> 12
        }

        val trendExpenseMap = LongArray(trendSlots + 1)
        val trendIncomeMap = LongArray(trendSlots + 1)

        val targetTypeRecords = mutableListOf<RecordWithCategory>()
        val cal = Calendar.getInstance()

        rawRecords.forEach { item ->
            val slotIndex = when (period) {
                StatisticsPeriod.WEEK -> {
                    DateTimeUtils.getDayOfWeekIndex(item.record.recordTime).coerceIn(1, 7)
                }
                StatisticsPeriod.MONTH -> {
                    DateTimeUtils.getDayOfMonth(item.record.recordTime).coerceIn(1, daysInMonth)
                }
                StatisticsPeriod.YEAR -> {
                    cal.timeInMillis = item.record.recordTime
                    (cal.get(Calendar.MONTH) + 1).coerceIn(1, 12)
                }
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

        // 计算上个周期收支以支持环比分析
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

        // 趋势数据生成
        val trends = (1..trendSlots).map { slot ->
            DailyTrendItem(
                day = slot,
                dateFormatted = when (period) {
                    StatisticsPeriod.WEEK -> DateTimeUtils.getWeekDayName(slot)
                    StatisticsPeriod.MONTH -> "$month-$slot"
                    StatisticsPeriod.YEAR -> "${slot}月"
                },
                expenseAmount = trendExpenseMap[slot],
                incomeAmount = trendIncomeMap[slot]
            )
        }

        val maxExp = trends.maxOfOrNull { it.expenseAmount } ?: 0L
        val avgExp = when (period) {
            StatisticsPeriod.WEEK -> totalExp / 7
            StatisticsPeriod.MONTH -> if (daysInMonth > 0) totalExp / daysInMonth else 0L
            StatisticsPeriod.YEAR -> totalExp / 12
        }

        // 智能消费洞察
        val topCategory = categoryStats.firstOrNull()
        val insightText = when (period) {
            StatisticsPeriod.WEEK -> {
                if (type == RecordType.EXPENSE) {
                    if (totalExp == 0L) {
                        "本周暂无支出记录，继续保持理性消费哦～ ✨"
                    } else if (topCategory != null) {
                        val pctStr = String.format(java.util.Locale.CHINA, "%.1f%%", topCategory.percentage * 100)
                        val peakDayItem = trends.maxByOrNull { it.expenseAmount }
                        val peakDayStr = peakDayItem?.dateFormatted ?: ""
                        "本周支出主要在「${topCategory.category.name}」(占 $pctStr)，周均日销 ¥${MoneyUtils.centsToYuanString(avgExp)}，开销最高为 $peakDayStr 📊"
                    } else {
                        "本周累计支出 ¥${MoneyUtils.centsToYuanString(totalExp)}，合理规划每一天 🌿"
                    }
                } else {
                    if (totalInc == 0L) {
                        "本周暂未记录收入，每一分积累都值得期待 🌱"
                    } else {
                        "本周累计进账 ¥${MoneyUtils.centsToYuanString(totalInc)}，辛勤付出收获满满 🎉"
                    }
                }
            }
            StatisticsPeriod.MONTH -> {
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
            }
            StatisticsPeriod.YEAR -> {
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
        }

        StatisticsUiState(
            selectedYear = year,
            selectedMonth = month,
            selectedWeek = week,
            weekStartTimestamp = weekStart,
            weekEndTimestamp = weekEnd,
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
            prevPeriodExpense = prevExp,
            prevPeriodIncome = prevInc,
            expenseDiffPercent = expenseDiff,
            incomeDiffPercent = incomeDiff,
            isLoading = false
        )
    }.combine(preferencesRepository.monthlyBudgets) { state, budgets ->
        if (state.periodMode != StatisticsPeriod.MONTH) {
            state.copy(budgetReview = BudgetReviewData())
        } else {
            val budget = budgets[PreferencesRepository.monthKey(state.selectedYear, state.selectedMonth)] ?: 0L
            state.copy(
                budgetReview = BudgetReviewCalculator.calculate(
                    year = state.selectedYear,
                    month = state.selectedMonth,
                    budgetCents = budget,
                    expenseCents = state.summary.totalExpense
                )
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StatisticsUiState(
            selectedYear = currentYearMonth.first,
            selectedMonth = currentYearMonth.second,
            selectedWeek = currentYearWeek.second,
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

    fun selectWeek(year: Int, week: Int) {
        _selectedYear.value = year
        _selectedWeek.value = week
        _selectedCategory.value = null
    }

    fun previousPeriod() {
        when (_periodMode.value) {
            StatisticsPeriod.WEEK -> {
                if (_selectedWeek.value <= 1) {
                    val prevYear = _selectedYear.value - 1
                    _selectedYear.value = prevYear
                    _selectedWeek.value = DateTimeUtils.getMaxWeeksInYear(prevYear)
                } else {
                    _selectedWeek.value -= 1
                }
                _selectedCategory.value = null
            }
            StatisticsPeriod.MONTH -> {
                var y = _selectedYear.value
                var m = _selectedMonth.value - 1
                if (m < 1) {
                    m = 12
                    y -= 1
                }
                selectMonth(y, m)
            }
            StatisticsPeriod.YEAR -> {
                _selectedYear.value -= 1
                _selectedCategory.value = null
            }
        }
    }

    fun nextPeriod() {
        when (_periodMode.value) {
            StatisticsPeriod.WEEK -> {
                val maxWeeks = DateTimeUtils.getMaxWeeksInYear(_selectedYear.value)
                if (_selectedWeek.value >= maxWeeks) {
                    _selectedYear.value += 1
                    _selectedWeek.value = 1
                } else {
                    _selectedWeek.value += 1
                }
                _selectedCategory.value = null
            }
            StatisticsPeriod.MONTH -> {
                var y = _selectedYear.value
                var m = _selectedMonth.value + 1
                if (m > 12) {
                    m = 1
                    y += 1
                }
                selectMonth(y, m)
            }
            StatisticsPeriod.YEAR -> {
                _selectedYear.value += 1
                _selectedCategory.value = null
            }
        }
    }

    fun selectType(type: RecordType) {
        _selectedType.value = type
        _selectedCategory.value = null
    }

    fun selectCategory(item: CategoryStatItem?) {
        _selectedCategory.value = item
    }

    fun setMonthlyBudget(budgetCents: Long) {
        viewModelScope.launch {
            preferencesRepository.setBudgetForMonth(
                _selectedYear.value,
                _selectedMonth.value,
                budgetCents
            )
        }
    }

    class Factory(
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StatisticsViewModel(recordRepository, categoryRepository, preferencesRepository) as T
        }
    }
}

private data class PeriodQuery(
    val year: Int,
    val month: Int,
    val week: Int,
    val period: StatisticsPeriod
)

private data class PeriodHeader(
    val year: Int,
    val month: Int,
    val week: Int,
    val period: StatisticsPeriod,
    val type: RecordType
)

private data class SixParams(
    val year: Int,
    val month: Int,
    val week: Int,
    val period: StatisticsPeriod,
    val type: RecordType,
    val activeCategory: CategoryStatItem?
)
