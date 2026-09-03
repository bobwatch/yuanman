package com.yuanman.app.ui.screens.add_edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.AccountRepository
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.ui.components.KeypadEngine
import com.yuanman.app.utils.CrossMonthExpenseUtils
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID

data class AddEditUiState(
    val isEditMode: Boolean = false,
    val recordId: Long = 0L,
    val type: RecordType = RecordType.EXPENSE,
    val expression: String = "",
    val selectedCategory: CategoryEntity? = null,
    val recordTime: Long = System.currentTimeMillis(),
    val remark: String = "",
    val paymentMethod: String = PaymentMethod.defaultMethod(),
    val selectedAccountId: Long? = null,
    val availableAccounts: List<AccountEntity> = emptyList(),
    val spreadMonths: Int = 1,
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val quickRemarks: List<String> = emptyList(),
    val hapticEnabled: Boolean = true,
    val quickEntryEnabled: Boolean = true,
    val quickEntryLearningRules: List<QuickEntryLearningEntity> = emptyList(),
    val errorMessage: String? = null,
    val savedFeedbackMessage: String? = null,
    val isSavedSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class AddEditRecordViewModel(
    private val recordId: Long,
    initialType: RecordType?,
    private val initialCategoryId: Long = 0L,
    private val recordRepository: RecordRepository,
    private val accountRepository: AccountRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditUiState(
            isEditMode = recordId > 0L,
            recordId = recordId,
            type = initialType ?: RecordType.EXPENSE
        )
    )
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var cachedExpenseCategories: List<CategoryEntity> = emptyList()
    private var cachedIncomeCategories: List<CategoryEntity> = emptyList()
    private var lastSelectedExpenseCategory: CategoryEntity? = null
    private var lastSelectedIncomeCategory: CategoryEntity? = null

    init {
        // 加载偏好设置
        viewModelScope.launch {
            preferencesRepository.hapticFeedbackEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(hapticEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            preferencesRepository.quickEntryEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(quickEntryEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            accountRepository.activeAccounts.collectLatest { accounts ->
                _uiState.update { it.copy(availableAccounts = accounts) }
            }
        }

        viewModelScope.launch {
            categoryRepository.observeAllQuickEntryLearning().collectLatest { rules ->
                _uiState.update { it.copy(quickEntryLearningRules = rules) }
            }
        }

        viewModelScope.launch {
            if (recordId <= 0L) {
                preferencesRepository.defaultPaymentMethod.firstOrNull()?.let { method ->
                    _uiState.update { it.copy(paymentMethod = method) }
                }
                preferencesRepository.defaultExpenseAccountId.firstOrNull()?.let { accId ->
                    _uiState.update { it.copy(selectedAccountId = accId) }
                }
                if (initialType == null && initialCategoryId <= 0L) {
                    preferencesRepository.defaultRecordType.firstOrNull()?.let { type ->
                        _uiState.update { it.copy(type = type) }
                    }
                }
            }
        }

        // 若传入了指定初始分类，提前加载该分类以确定收支类型与选中态
        if (recordId <= 0L && initialCategoryId > 0L) {
            viewModelScope.launch {
                val cat = categoryRepository.getCategoryById(initialCategoryId)
                if (cat != null) {
                    val catType = runCatching { RecordType.valueOf(cat.type) }.getOrDefault(RecordType.EXPENSE)
                    _uiState.update {
                        it.copy(
                            type = catType,
                            selectedCategory = cat,
                            quickRemarks = cat.getTagList()
                        )
                    }
                }
            }
        }

        // 双向预加载并常驻缓存支出与收入分类，确保类型切换 0 延迟秒切
        viewModelScope.launch {
            categoryRepository.getCategoriesByType(RecordType.EXPENSE).collectLatest { list ->
                cachedExpenseCategories = list
                _uiState.update { it.copy(expenseCategories = list) }
                if (_uiState.value.type == RecordType.EXPENSE) {
                    applyCategories(list)
                }
            }
        }

        viewModelScope.launch {
            categoryRepository.getCategoriesByType(RecordType.INCOME).collectLatest { list ->
                cachedIncomeCategories = list
                _uiState.update { it.copy(incomeCategories = list) }
                if (_uiState.value.type == RecordType.INCOME) {
                    applyCategories(list)
                }
            }
        }

        // 编辑模式加载
        if (recordId > 0L) {
            viewModelScope.launch {
                val recordWithCategory = recordRepository.getRecordByIdDirect(recordId)
                if (recordWithCategory != null) {
                    val record = recordWithCategory.record
                    val recType = RecordType.fromString(record.type)
                    val cat = recordWithCategory.category
                    val remarks = cat?.getTagList() ?: emptyList()
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            type = recType,
                            expression = MoneyUtils.centsToYuanString(record.amount, withGrouping = false),
                            selectedCategory = cat,
                            recordTime = record.recordTime,
                            remark = record.remark,
                            paymentMethod = record.paymentMethod,
                            selectedAccountId = record.accountId,
                            quickRemarks = remarks
                        )
                    }
                }
            }
        }
    }

    private fun applyCategories(list: List<CategoryEntity>) {
        _uiState.update { state ->
            val currentSelected = state.selectedCategory
            val matchInitial = if (initialCategoryId > 0L && (currentSelected == null || currentSelected.id == initialCategoryId)) {
                list.find { it.id == initialCategoryId }
            } else null

            val newSelected = matchInitial
                ?: if (currentSelected != null && list.any { it.id == currentSelected.id }) {
                    currentSelected
                } else if (initialCategoryId > 0L && list.any { it.id == initialCategoryId }) {
                    list.find { it.id == initialCategoryId }
                } else {
                    list.firstOrNull()
                }

            val remarks = newSelected?.getTagList() ?: emptyList()
            state.copy(
                availableCategories = list,
                selectedCategory = newSelected,
                quickRemarks = remarks
            )
        }
    }

    fun setRecordType(type: RecordType) {
        if (_uiState.value.type != type) {
            // 记录切换前的选中分类偏好记忆
            if (_uiState.value.type == RecordType.EXPENSE) {
                lastSelectedExpenseCategory = _uiState.value.selectedCategory
            } else {
                lastSelectedIncomeCategory = _uiState.value.selectedCategory
            }

            val targetList = if (type == RecordType.EXPENSE) cachedExpenseCategories else cachedIncomeCategories
            val rememberedCategory = if (type == RecordType.EXPENSE) lastSelectedExpenseCategory else lastSelectedIncomeCategory
            val newSelected = if (rememberedCategory != null && targetList.any { it.id == rememberedCategory.id }) {
                rememberedCategory
            } else {
                targetList.firstOrNull()
            }

            val remarks = newSelected?.getTagList() ?: emptyList()

            _uiState.update {
                it.copy(
                    type = type,
                    availableCategories = targetList,
                    selectedCategory = newSelected,
                    quickRemarks = remarks,
                    spreadMonths = if (type == RecordType.EXPENSE) it.spreadMonths else 1
                )
            }
        }
    }

    fun setSpreadMonths(months: Int) {
        _uiState.update { it.copy(spreadMonths = months.coerceIn(1, 36)) }
    }

    fun setExpression(expr: String) {
        _uiState.update { it.copy(expression = expr, errorMessage = null) }
    }

    fun selectCategory(category: CategoryEntity) {
        val remarks = category.getTagList()
        _uiState.update {
            it.copy(
                selectedCategory = category,
                quickRemarks = remarks,
                errorMessage = null
            )
        }
    }

    fun setRecordTime(timestamp: Long) {
        _uiState.update { it.copy(recordTime = timestamp) }
    }

    fun setRemark(remark: String) {
        _uiState.update { it.copy(remark = remark) }
    }

    fun selectQuickRemark(tag: String) {
        val current = _uiState.value.remark.trim()
        val updated = if (current == tag) {
            // 已选中该标签，再次点击取消选中
            ""
        } else if (current.contains(tag)) {
            // 包含该标签，剔除并整理空格
            current.replace(tag, "").replace(Regex("\\s+"), " ").trim()
        } else if (current.isEmpty()) {
            tag
        } else {
            "$current $tag"
        }
        _uiState.update { it.copy(remark = updated) }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update {
            val newMethod = if (it.paymentMethod == method) "" else method
            it.copy(paymentMethod = newMethod)
        }
    }

    fun selectAccount(accountId: Long?) {
        _uiState.update { state ->
            val validId = accountId?.takeIf { id -> state.availableAccounts.any { it.id == id } }
            state.copy(selectedAccountId = validId, errorMessage = null)
        }
    }

    fun clearPaymentSelection() {
        _uiState.update { it.copy(paymentMethod = "", selectedAccountId = null, spreadMonths = 1) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun clearFeedbackMessage() {
        _uiState.update { it.copy(savedFeedbackMessage = null) }
    }

    fun saveQuickEntry(input: String) {
        val state = _uiState.value
        val parsed = QuickEntryParser.parse(input, state.availableCategories, state.quickEntryLearningRules)
        if (parsed == null) {
            _uiState.update { it.copy(errorMessage = "请输入类似“奶茶 18”的内容") }
            return
        }
        val category = parsed.category ?: state.selectedCategory ?: state.availableCategories.firstOrNull()
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "未能识别分类，请先选择分类") }
            return
        }
        _uiState.update {
            it.copy(
                expression = parsed.amountYuan.toPlainString(),
                selectedCategory = category,
                remark = parsed.remark,
                paymentMethod = parsed.paymentMethod ?: it.paymentMethod
            )
        }
        saveRecord()
    }

    fun saveRecord(continueNext: Boolean = false) {
        val state = _uiState.value
        if (state.isLoading) return
        val expr = state.expression.trim()

        if (expr.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "请输入记账金额") }
            return
        }

        // 解析并计算金额
        val computedBd: BigDecimal? = if (expr.contains("+") || expr.contains("-")) {
            KeypadEngine.evaluateExpression(expr)
        } else {
            try { BigDecimal(expr) } catch (e: Exception) { null }
        }

        if (computedBd == null || computedBd <= BigDecimal.ZERO) {
            _uiState.update { it.copy(errorMessage = "请输入大于 0 的有效金额") }
            return
        }

        val amountInCents = try {
            computedBd.multiply(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()
        } catch (_: ArithmeticException) {
            _uiState.update { it.copy(errorMessage = "金额超出可记账范围") }
            return
        }

        val category = state.selectedCategory
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "请选择一个分类") }
            return
        }

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val monthCount = if (!state.isEditMode && state.type == RecordType.EXPENSE) {
                    state.spreadMonths.coerceAtLeast(1)
                } else {
                    1
                }
                val now = System.currentTimeMillis()
                val splitGroupId = if (monthCount > 1) UUID.randomUUID().toString() else null
                val splitAmounts = CrossMonthExpenseUtils.splitAmount(amountInCents, monthCount)
                val records = splitAmounts.mapIndexed { index, splitAmount ->
                    val splitRemark = if (monthCount > 1) {
                        listOfNotNull(
                            state.remark.trim().takeIf { it.isNotBlank() },
                            "跨月分摊 ${index + 1}/$monthCount"
                        ).joinToString(" · ")
                    } else {
                        state.remark.trim()
                    }
                    RecordEntity(
                        id = if (state.isEditMode) state.recordId else 0L,
                        type = state.type.name,
                        amount = splitAmount,
                        categoryId = category.id,
                        recordTime = if (monthCount > 1) {
                            CrossMonthExpenseUtils.addMonthsKeepingDay(state.recordTime, index)
                        } else {
                            state.recordTime
                        },
                        remark = splitRemark,
                        paymentMethod = state.paymentMethod,
                        accountId = state.selectedAccountId,
                        splitGroupId = splitGroupId,
                        splitIndex = if (monthCount > 1) index + 1 else null,
                        splitTotal = if (monthCount > 1) monthCount else null,
                        createdAt = now,
                        updatedAt = now
                    )
                }

                if (state.isEditMode) recordRepository.updateRecord(records.first())
                else recordRepository.insertRecords(records)

                if (state.remark.isNotBlank()) {
                    categoryRepository.learnQuickEntry(state.type, state.remark, category.syncId)
                }

                if (continueNext) {
                    // 连记模式：清空金额与备注，重置时间为当前，弹出成功气泡
                    _uiState.update {
                        it.copy(
                            expression = "",
                            remark = "",
                            spreadMonths = 1,
                            recordTime = System.currentTimeMillis(),
                            savedFeedbackMessage = "已记下「${category.name} ¥${MoneyUtils.centsToYuanString(amountInCents)}」✨ 可继续记下一笔"
                        )
                    }
                } else {
                    _uiState.update { it.copy(isSavedSuccess = true) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "保存失败，请稍后重试") }
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun clearSavedFeedbackMessage() {
        _uiState.update { it.copy(savedFeedbackMessage = null) }
    }

    fun deleteRecord() {
        if (_uiState.value.isEditMode && _uiState.value.recordId > 0L) {
            viewModelScope.launch {
                recordRepository.deleteRecordById(_uiState.value.recordId)
                _uiState.update { it.copy(isSavedSuccess = true) }
            }
        }
    }

    class Factory(
        private val recordId: Long = 0L,
        private val initialType: RecordType? = null,
        private val initialCategoryId: Long = 0L,
        private val recordRepository: RecordRepository,
        private val accountRepository: AccountRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditRecordViewModel(
                recordId = recordId,
                initialType = initialType,
                initialCategoryId = initialCategoryId,
                recordRepository = recordRepository,
                accountRepository = accountRepository,
                categoryRepository = categoryRepository,
                preferencesRepository = preferencesRepository
            ) as T
        }
    }
}
