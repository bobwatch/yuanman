package com.yuanman.app.ui.screens.add_edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.ui.components.KeypadEngine
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal

data class AddEditUiState(
    val isEditMode: Boolean = false,
    val recordId: Long = 0L,
    val type: RecordType = RecordType.EXPENSE,
    val expression: String = "",
    val selectedCategory: CategoryEntity? = null,
    val recordTime: Long = System.currentTimeMillis(),
    val remark: String = "",
    val paymentMethod: String = PaymentMethod.defaultMethod(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val quickRemarks: List<String> = emptyList(),
    val hapticEnabled: Boolean = true,
    val errorMessage: String? = null,
    val savedFeedbackMessage: String? = null,
    val isSavedSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class AddEditRecordViewModel(
    private val recordId: Long,
    initialType: RecordType?,
    private val recordRepository: RecordRepository,
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

    init {
        // 加载偏好设置
        viewModelScope.launch {
            preferencesRepository.hapticFeedbackEnabled.collectLatest { enabled ->
                _uiState.update { it.copy(hapticEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            if (recordId <= 0L) {
                preferencesRepository.defaultPaymentMethod.firstOrNull()?.let { method ->
                    _uiState.update { it.copy(paymentMethod = method) }
                }
                if (initialType == null) {
                    preferencesRepository.defaultRecordType.firstOrNull()?.let { type ->
                        _uiState.update { it.copy(type = type) }
                    }
                }
            }
        }

        // 加载分类列表与快捷备注
        viewModelScope.launch {
            _uiState.map { it.type }.distinctUntilChanged().collectLatest { currentType ->
                categoryRepository.getCategoriesByType(currentType).collectLatest { list ->
                    _uiState.update { state ->
                        val currentSelected = state.selectedCategory
                        val newSelected = if (currentSelected != null && list.any { it.id == currentSelected.id }) {
                            currentSelected
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
                            quickRemarks = remarks
                        )
                    }
                }
            }
        }
    }

    fun setRecordType(type: RecordType) {
        if (_uiState.value.type != type) {
            _uiState.update { it.copy(type = type, selectedCategory = null) }
        }
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
            val recordEntity = RecordEntity(
                id = if (state.isEditMode) state.recordId else 0L,
                type = state.type.name,
                amount = amountInCents,
                categoryId = category.id,
                recordTime = state.recordTime,
                remark = state.remark.trim(),
                paymentMethod = state.paymentMethod,
                updatedAt = System.currentTimeMillis()
            )

            if (state.isEditMode) {
                recordRepository.updateRecord(recordEntity)
            } else {
                recordRepository.insertRecord(recordEntity)
            }

            if (continueNext) {
                // 连记模式：清空金额与备注，重置时间为当前，弹出成功气泡
                _uiState.update {
                    it.copy(
                        expression = "",
                        remark = "",
                        recordTime = System.currentTimeMillis(),
                        savedFeedbackMessage = "已记下「${category.name} ¥${MoneyUtils.centsToYuanString(amountInCents)}」✨ 可继续记下一笔"
                    )
                }
            } else {
                _uiState.update { it.copy(isSavedSuccess = true) }
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
        private val recordId: Long,
        private val initialType: RecordType?,
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditRecordViewModel(
                recordId,
                initialType,
                recordRepository,
                categoryRepository,
                preferencesRepository
            ) as T
        }
    }
}
