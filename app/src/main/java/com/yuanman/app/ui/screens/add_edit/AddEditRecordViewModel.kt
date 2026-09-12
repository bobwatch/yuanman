package com.yuanman.app.ui.screens.add_edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.ui.components.KeypadEngine
import com.yuanman.app.utils.CrossMonthExpenseUtils
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.yuanman.app.ui.screens.account.AccountUiModel
import com.yuanman.app.ui.screens.account.PaycheckExecutor
import com.yuanman.app.ui.screens.account.isSalaryCategoryName
import com.yuanman.app.ui.screens.account.parseAccountsJson
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
    val spreadMonths: Int = 1,
    val expenseCategories: List<CategoryEntity> = emptyList(),
    val incomeCategories: List<CategoryEntity> = emptyList(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val quickRemarks: List<String> = emptyList(),
    val hapticEnabled: Boolean = true,
    val quickEntryEnabled: Boolean = true,
    val quickEntryLearningRules: List<QuickEntryLearningEntity> = emptyList(),
    val accounts: List<AccountUiModel> = emptyList(),
    val defaultExpenseAccount: String = "",
    val defaultIncomeAccount: String = "",
    val errorMessage: String? = null,
    val savedFeedbackMessage: String? = null,
    val isSavedSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class AddEditRecordViewModel(
    private val recordId: Long,
    initialType: RecordType?,
    private val initialCategoryId: Long = 0L,
    private val initialRecordTime: Long? = null,
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    companion object {
        @Volatile
        var cachedExpenseCategories: List<CategoryEntity> = emptyList()
        @Volatile
        var cachedIncomeCategories: List<CategoryEntity> = emptyList()
    }

    private val _uiState = MutableStateFlow(
        run {
            val type = initialType ?: RecordType.EXPENSE
            val categories = if (type == RecordType.EXPENSE) cachedExpenseCategories else cachedIncomeCategories
            val initialCat = if (initialCategoryId > 0L) {
                categories.find { it.id == initialCategoryId }
            } else {
                categories.firstOrNull()
            }
            AddEditUiState(
                isEditMode = recordId > 0L,
                recordId = recordId,
                type = type,
                recordTime = initialRecordTime ?: System.currentTimeMillis(),
                expenseCategories = cachedExpenseCategories,
                incomeCategories = cachedIncomeCategories,
                availableCategories = categories,
                selectedCategory = initialCat,
                quickRemarks = initialCat?.getTagList() ?: emptyList()
            )
        }
    )
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    // 工资到账自动分账执行器（v0.0.4.5）：保存「工资」类收入后按发薪规则自动分配
    private val paycheckExecutor: PaycheckExecutor by lazy {
        PaycheckExecutor(preferencesRepository, recordRepository)
    }

    private var lastSelectedExpenseCategory: CategoryEntity? = null
    private var lastSelectedIncomeCategory: CategoryEntity? = null

    init {
        // 合并偏好设置初始化到后台，避免进页瞬间多次并发 update 造成 UI 重组掉帧
        viewModelScope.launch(Dispatchers.IO) {
            val haptic = preferencesRepository.hapticFeedbackEnabled.firstOrNull() ?: true
            val quick = preferencesRepository.quickEntryEnabled.firstOrNull() ?: false
            val accJson = preferencesRepository.accountsData.firstOrNull()
            val parsedAcc = parseAccountsJson(accJson)
            val defExp = preferencesRepository.defaultExpenseAccount.firstOrNull().orEmpty()
            val defInc = preferencesRepository.defaultIncomeAccount.firstOrNull().orEmpty()
            val defMethod = preferencesRepository.defaultPaymentMethod.firstOrNull().orEmpty()
            val defType = if (initialType == null && initialCategoryId <= 0L && recordId <= 0L) {
                preferencesRepository.defaultRecordType.firstOrNull()
            } else null

            _uiState.update { state ->
                val newType = defType ?: state.type
                val newMethod = if (state.paymentMethod.isBlank() || state.paymentMethod == PaymentMethod.defaultMethod()) {
                    if (newType == RecordType.EXPENSE && defExp.isNotBlank()) defExp
                    else if (newType == RecordType.INCOME && defInc.isNotBlank()) defInc
                    else defMethod.ifBlank { state.paymentMethod }
                } else state.paymentMethod

                state.copy(
                    hapticEnabled = haptic,
                    quickEntryEnabled = quick,
                    accounts = parsedAcc,
                    defaultExpenseAccount = defExp,
                    defaultIncomeAccount = defInc,
                    type = newType,
                    paymentMethod = newMethod
                )
            }
        }

        viewModelScope.launch {
            categoryRepository.observeAllQuickEntryLearning().collectLatest { rules ->
                _uiState.update { it.copy(quickEntryLearningRules = rules) }
            }
        }

        // 双向预加载并常驻缓存支出与收入分类（合并原子更新，避免多次重组与跳动）
        viewModelScope.launch {
            categoryRepository.getCategoriesByType(RecordType.EXPENSE).collectLatest { list ->
                val prevList = cachedExpenseCategories
                cachedExpenseCategories = list
                _uiState.update { state ->
                    if (state.type == RecordType.EXPENSE) {
                        val cur = state.selectedCategory
                        val newlyAdded = if (prevList.isNotEmpty() && list.size > prevList.size) {
                            list.firstOrNull { item -> prevList.none { it.id == item.id } }
                        } else null
                        val match = newlyAdded ?: if (initialCategoryId > 0L && (cur == null || cur.id == initialCategoryId)) {
                            list.find { it.id == initialCategoryId }
                        } else null
                        val sel = match ?: if (cur != null && list.any { it.id == cur.id }) cur else list.firstOrNull()
                        state.copy(
                            expenseCategories = list,
                            availableCategories = list,
                            selectedCategory = sel,
                            quickRemarks = sel?.getTagList() ?: emptyList()
                        )
                    } else {
                        state.copy(expenseCategories = list)
                    }
                }
            }
        }

        viewModelScope.launch {
            categoryRepository.getCategoriesByType(RecordType.INCOME).collectLatest { list ->
                val prevList = cachedIncomeCategories
                cachedIncomeCategories = list
                _uiState.update { state ->
                    if (state.type == RecordType.INCOME) {
                        val cur = state.selectedCategory
                        val newlyAdded = if (prevList.isNotEmpty() && list.size > prevList.size) {
                            list.firstOrNull { item -> prevList.none { it.id == item.id } }
                        } else null
                        val match = newlyAdded ?: if (initialCategoryId > 0L && (cur == null || cur.id == initialCategoryId)) {
                            list.find { it.id == initialCategoryId }
                        } else null
                        val sel = match ?: if (cur != null && list.any { it.id == cur.id }) cur else list.firstOrNull()
                        state.copy(
                            incomeCategories = list,
                            availableCategories = list,
                            selectedCategory = sel,
                            quickRemarks = sel?.getTagList() ?: emptyList()
                        )
                    } else {
                        state.copy(incomeCategories = list)
                    }
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
                    val categories = if (recType == RecordType.EXPENSE) cachedExpenseCategories else cachedIncomeCategories
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            type = recType,
                            expression = MoneyUtils.centsToYuanString(record.amount, withGrouping = false),
                            availableCategories = if (categories.isNotEmpty()) categories else it.availableCategories,
                            selectedCategory = cat,
                            recordTime = record.recordTime,
                            remark = record.remark,
                            paymentMethod = record.paymentMethod,
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

            val currentMethod = _uiState.value.paymentMethod
            val defExp = _uiState.value.defaultExpenseAccount
            val defInc = _uiState.value.defaultIncomeAccount
            val newPaymentMethod = if (!_uiState.value.isEditMode) {
                if (type == RecordType.EXPENSE && (currentMethod == defInc || currentMethod.isBlank())) {
                    defExp.ifBlank { currentMethod }
                } else if (type == RecordType.INCOME && (currentMethod == defExp || currentMethod.isBlank() || currentMethod == PaymentMethod.defaultMethod())) {
                    defInc.ifBlank { "" }
                } else {
                    currentMethod
                }
            } else {
                currentMethod
            }

            _uiState.update {
                it.copy(
                    type = type,
                    availableCategories = targetList,
                    selectedCategory = newSelected,
                    quickRemarks = remarks,
                    spreadMonths = if (type == RecordType.EXPENSE) it.spreadMonths else 1,
                    paymentMethod = newPaymentMethod
                )
            }
        }
    }

    fun setDefaultPaymentAccount(accountName: String, isExpense: Boolean) {
        viewModelScope.launch {
            if (isExpense) {
                preferencesRepository.setDefaultExpenseAccount(accountName)
            } else {
                preferencesRepository.setDefaultIncomeAccount(accountName)
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

        val amountInCents = computedBd.multiply(BigDecimal(100)).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact()

        val category = state.selectedCategory
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "请选择一个分类") }
            return
        }

        viewModelScope.launch {
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
                    splitGroupId = splitGroupId,
                    splitIndex = if (monthCount > 1) index + 1 else null,
                    splitTotal = if (monthCount > 1) monthCount else null,
                    createdAt = now,
                    updatedAt = now
                )
            }

            // 单笔插入时拿到记录 id（工资自动分账的幂等锚点）
            var insertedRecordId = 0L
            if (state.isEditMode) {
                recordRepository.updateRecord(records.first())
            } else if (records.size == 1) {
                insertedRecordId = recordRepository.insertRecord(records.first())
            } else {
                recordRepository.insertRecords(records)
            }

            if (state.remark.isNotBlank()) {
                categoryRepository.learnQuickEntry(state.type, state.remark, category.syncId)
            }

            // ---- v0.0.4.5：工资类收入保存后自动按发薪规则分账（开关/来源匹配在 PaycheckExecutor 内判定）----
            var autoFeedback: String? = null
            if (!state.isEditMode && insertedRecordId > 0L &&
                state.type == RecordType.INCOME &&
                isSalaryCategoryName(category.name)
            ) {
                autoFeedback = paycheckExecutor.onSalaryRecordSaved(
                    recordId = insertedRecordId,
                    amountCents = amountInCents,
                    paymentMethod = state.paymentMethod
                )
            }

            if (continueNext) {
                // 连记模式：清空金额与备注，重置时间为当前，弹出成功气泡（自动分账结果拼在尾部）
                _uiState.update {
                    it.copy(
                        expression = "",
                        remark = "",
                        spreadMonths = 1,
                        recordTime = System.currentTimeMillis(),
                        savedFeedbackMessage = "已记下「${category.name} ¥${MoneyUtils.centsToYuanString(amountInCents)}」✨ 可继续记下一笔" +
                            (autoFeedback?.let { "；$it" } ?: "")
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isSavedSuccess = true,
                        savedFeedbackMessage = autoFeedback // 单笔保存：有自动分账结果时顺带 toast 提示
                    )
                }
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
        private val initialRecordTime: Long? = null,
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditRecordViewModel(
                recordId = recordId,
                initialType = initialType,
                initialCategoryId = initialCategoryId,
                initialRecordTime = initialRecordTime,
                recordRepository = recordRepository,
                categoryRepository = categoryRepository,
                preferencesRepository = preferencesRepository
            ) as T
        }
    }
}
