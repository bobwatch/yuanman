package com.yuanman.app.ui.screens.home

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.model.MonthSummaryData
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.QuickEntryResult
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.AccountRepository
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.WarmAffirmation
import com.yuanman.app.utils.WarmAffirmationsHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
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
    val quickEntryEnabled: Boolean = true,
    val quickEntryCategories: List<CategoryEntity> = emptyList(),
    val quickEntryLearningRules: List<QuickEntryLearningEntity> = emptyList(),
    val quickTemplates: List<QuickRecordTemplate> = emptyList(),
    val pinnedTemplateKeys: Set<String> = emptySet(),
    val isLoading: Boolean = false
)

data class QuickRecordTemplate(
    val key: String,
    val source: RecordWithCategory,
    val usageCount: Int,
    val lastUsedAt: Long,
    val isPinned: Boolean
)

private data class MonthInfo(
    val year: Int,
    val month: Int,
    val records: List<RecordWithCategory>
)

private data class PrefsInfo(
    val budgets: Map<String, Long>,
    val legacyBudget: Long,
    val privacy: Boolean,
    val quickEntryEnabled: Boolean,
    val affirmation: WarmAffirmation
)

private data class TemplateInfo(
    val templates: List<QuickRecordTemplate>,
    val pinnedKeys: Set<String>
)

class HomeViewModel(
    private val recordRepository: RecordRepository,
    private val preferencesRepository: PreferencesRepository,
    private val categoryRepository: CategoryRepository,
    private val accountRepository: AccountRepository
) : ViewModel() {

    val accounts: StateFlow<List<AccountEntity>> = accountRepository.activeAccounts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val defaultExpenseAccountId: StateFlow<Long?> = preferencesRepository.defaultExpenseAccountId
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    fun setDefaultExpenseAccount(accountId: Long?) {
        viewModelScope.launch {
            preferencesRepository.setDefaultExpenseAccountId(accountId)
        }
    }

    private val currentYearMonth = DateTimeUtils.getCurrentYearMonth()
    private val _selectedYear = MutableStateFlow(currentYearMonth.first)
    private val _selectedMonth = MutableStateFlow(currentYearMonth.second)
    private val _refreshToken = MutableStateFlow(0)
    private val _isRefreshing = MutableStateFlow(false)
    private val _affirmationIndex = MutableStateFlow(0)
    private val _currentAffirmation = MutableStateFlow(WarmAffirmationsHelper.getAffirmationForCurrentTime())
    private val _quickEntryDraft = MutableStateFlow("")

    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val quickEntryDraft: StateFlow<String> = _quickEntryDraft.asStateFlow()

    fun updateQuickEntryDraft(value: String) {
        _quickEntryDraft.value = value
    }

    fun clearQuickEntryDraft() {
        _quickEntryDraft.value = ""
    }

    val defaultRecordType = preferencesRepository.defaultRecordType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RecordType.EXPENSE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val monthRecordsFlow = combine(_selectedYear, _selectedMonth, _refreshToken) { year, month, _ ->
        Pair(year, month)
    }.flatMapLatest { (year, month) ->
        recordRepository.getRecordsByMonth(year, month)
    }

    private val monthInfoFlow = combine(_selectedYear, _selectedMonth, monthRecordsFlow) { year, month, records ->
        MonthInfo(year, month, records)
    }

    private val prefsInfoFlow = combine(
        preferencesRepository.monthlyBudgets,
        preferencesRepository.monthlyBudget,
        preferencesRepository.privacyMode,
        preferencesRepository.quickEntryEnabled,
        _currentAffirmation
    ) { budgets, legacyBudget, privacy, quickEntryEnabled, affirmation ->
        PrefsInfo(budgets, legacyBudget, privacy, quickEntryEnabled, affirmation)
    }

    private val templateInfoFlow = combine(
        recordRepository.getAllRecords(),
        preferencesRepository.pinnedTemplateKeys,
        preferencesRepository.hiddenTemplateKeys
    ) { records, pinnedKeys, hiddenKeys ->
        val templates = records
            .groupBy(::templateKey)
            .mapNotNull { (key, group) ->
                if (key in hiddenKeys || (group.size < 2 && key !in pinnedKeys)) return@mapNotNull null
                val source = group.maxByOrNull { it.record.recordTime } ?: return@mapNotNull null
                QuickRecordTemplate(
                    key = key,
                    source = source,
                    usageCount = group.size,
                    lastUsedAt = group.maxOf { it.record.recordTime },
                    isPinned = key in pinnedKeys
                )
            }
            .sortedWith(
                compareByDescending<QuickRecordTemplate> { it.isPinned }
                    .thenByDescending { it.usageCount }
                    .thenByDescending { it.lastUsedAt }
            )
            .take(5)
        TemplateInfo(templates, pinnedKeys)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        monthInfoFlow,
        prefsInfoFlow,
        categoryRepository.getAllCategories(),
        categoryRepository.observeAllQuickEntryLearning(),
        templateInfoFlow
    ) { monthInfo, prefsInfo, categories, learningRules, templateInfo ->
        val year = monthInfo.year
        val month = monthInfo.month
        val records = monthInfo.records
        val budget = prefsInfo.budgets[PreferencesRepository.monthKey(year, month)]
            ?: prefsInfo.legacyBudget
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
            quickEntryEnabled = prefsInfo.quickEntryEnabled,
            affirmation = affirmation,
            remainingDays = remainingDays,
            remainingBudgetCents = remainingBudgetCents,
            dailyAvailableCents = dailyAvailable,
            budgetUsedPercent = usedPercent,
            quickEntryCategories = categories,
            quickEntryLearningRules = learningRules,
            quickTemplates = templateInfo.templates,
            pinnedTemplateKeys = templateInfo.pinnedKeys,
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

    /** 重新订阅当前月份的 Room Flow，供首页下拉刷新使用。 */
    fun refresh() {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            val startedAt = SystemClock.elapsedRealtime()
            try {
                _refreshToken.update { it + 1 }
                // 保证刷新指示器至少展示一个可感知的时长，但查询本身就慢时不再叠加等待。
                val elapsed = SystemClock.elapsedRealtime() - startedAt
                val remaining = MIN_REFRESH_MILLIS - elapsed
                if (remaining > 0) delay(remaining)
            } finally {
                _isRefreshing.value = false
            }
        }
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
                updatedAt = System.currentTimeMillis(),
                revision = 0L,
                syncId = java.util.UUID.randomUUID().toString(),
                deletedAt = null
            )
            recordRepository.insertRecord(duplicate)
        }
    }

    /**
     * Saves a compact entry directly from Home and returns the parsed preview for immediate UI feedback.
     * @param overrideCategory 用户点击解析徽章手动选择的分类；为空时使用解析结果。
     */
    fun saveQuickEntry(
        input: String,
        type: RecordType,
        overrideCategory: CategoryEntity? = null,
        accountId: Long? = null
    ): QuickEntryResult? {
        val categories = uiState.value.quickEntryCategories.filter { it.type == type.name }
        val parsed = QuickEntryParser.parse(input, categories, uiState.value.quickEntryLearningRules) ?: return null
        val category = overrideCategory ?: parsed.category ?: categories.firstOrNull() ?: return null
        val amountCents = MoneyUtils.parseYuanToCents(parsed.amountYuan.toPlainString())
        if (amountCents <= 0L) return null

        val defaultExpenseId = if (type == RecordType.EXPENSE) defaultExpenseAccountId.value else null
        val resolvedAccountId = if (accountId == -1L) null else accountId ?: parsed.paymentMethod?.let { method ->
            accounts.value.firstOrNull { acc ->
                acc.name.contains(method, ignoreCase = true) || method.contains(acc.name, ignoreCase = true)
            }?.id
        } ?: defaultExpenseId

        viewModelScope.launch(Dispatchers.IO) {
            val paymentMethod = preferencesRepository.defaultPaymentMethod.first()
            recordRepository.insertRecord(
                RecordEntity(
                    type = type.name,
                    amount = amountCents,
                    categoryId = category.id,
                    recordTime = System.currentTimeMillis(),
                    remark = parsed.remark,
                    paymentMethod = parsed.paymentMethod ?: paymentMethod,
                    accountId = resolvedAccountId
                )
            )
            categoryRepository.learnQuickEntry(type, parsed.remark, category.syncId)
        }
        return parsed.copy(category = category)
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

    fun deleteRecord(record: RecordWithCategory) {
        viewModelScope.launch {
            recordRepository.deleteRecord(record.record)
        }
    }

    fun undoDelete(record: RecordEntity) {
        viewModelScope.launch {
            recordRepository.restoreRecord(record.id)
        }
    }

    fun replayTemplate(template: QuickRecordTemplate) {
        copyRecord(template.source.record)
    }

    fun setTemplatePinned(item: RecordWithCategory, pinned: Boolean) {
        viewModelScope.launch { preferencesRepository.setTemplatePinned(templateKey(item), pinned) }
    }

    fun setTemplatePinned(template: QuickRecordTemplate, pinned: Boolean) {
        viewModelScope.launch { preferencesRepository.setTemplatePinned(template.key, pinned) }
    }

    fun hideTemplate(template: QuickRecordTemplate) {
        viewModelScope.launch {
            preferencesRepository.setTemplateHidden(template.key, true)
            preferencesRepository.setTemplatePinned(template.key, false)
        }
    }

    fun isTemplatePinned(item: RecordWithCategory): Boolean = templateKey(item) in uiState.value.pinnedTemplateKeys

    fun setQuickEntryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setQuickEntryEnabled(enabled)
        }
    }

    companion object {
        /** 给品牌化刷新动效留出可感知但不拖沓的最短展示时间。 */
        private const val MIN_REFRESH_MILLIS = 650L

        fun templateKey(item: RecordWithCategory): String {
            val record = item.record
            val categoryKey = item.category?.syncId ?: "category:${record.categoryId}"
            val canonical = listOf(
                record.type.trim().uppercase(),
                record.amount.toString(),
                categoryKey,
                record.remark.trim().lowercase(),
                record.paymentMethod.trim()
            ).joinToString("\u0001")
            return MessageDigest.getInstance("SHA-256")
                .digest(canonical.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
        }
    }

    class Factory(
        private val recordRepository: RecordRepository,
        private val preferencesRepository: PreferencesRepository,
        private val categoryRepository: CategoryRepository,
        private val accountRepository: AccountRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HomeViewModel(recordRepository, preferencesRepository, categoryRepository, accountRepository) as T
        }
    }
}
