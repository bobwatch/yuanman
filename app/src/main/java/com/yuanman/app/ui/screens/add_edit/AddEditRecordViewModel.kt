package com.yuanman.app.ui.screens.add_edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class AddEditUiState(
    val isEditMode: Boolean = false,
    val recordId: Long = 0L,
    val type: RecordType = RecordType.EXPENSE,
    val amountInput: String = "",
    val selectedCategory: CategoryEntity? = null,
    val recordTime: Long = System.currentTimeMillis(),
    val remark: String = "",
    val paymentMethod: String = PaymentMethod.defaultMethod(),
    val availableCategories: List<CategoryEntity> = emptyList(),
    val errorMessage: String? = null,
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
        // 加载偏好设置默认支付方式
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

        // 加载分类列表
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
                        state.copy(
                            availableCategories = list,
                            selectedCategory = newSelected
                        )
                    }
                }
            }
        }

        // 如果是编辑模式，加载已有数据
        if (recordId > 0L) {
            viewModelScope.launch {
                val recordWithCategory = recordRepository.getRecordByIdDirect(recordId)
                if (recordWithCategory != null) {
                    val record = recordWithCategory.record
                    val recType = RecordType.fromString(record.type)
                    _uiState.update {
                        it.copy(
                            isEditMode = true,
                            type = recType,
                            amountInput = MoneyUtils.centsToYuanString(record.amount, withGrouping = false),
                            selectedCategory = recordWithCategory.category,
                            recordTime = record.recordTime,
                            remark = record.remark,
                            paymentMethod = record.paymentMethod
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

    fun setAmountInput(amount: String) {
        // 允许空、数字和小数点，最多两位小数
        if (amount.isEmpty() || amount.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
            _uiState.update { it.copy(amountInput = amount, errorMessage = null) }
        }
    }

    fun selectCategory(category: CategoryEntity) {
        _uiState.update { it.copy(selectedCategory = category, errorMessage = null) }
    }

    fun setRecordTime(timestamp: Long) {
        _uiState.update { it.copy(recordTime = timestamp) }
    }

    fun setRemark(remark: String) {
        _uiState.update { it.copy(remark = remark) }
    }

    fun setPaymentMethod(method: String) {
        _uiState.update { it.copy(paymentMethod = method) }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun saveRecord() {
        val state = _uiState.value

        // 校验金额
        if (!MoneyUtils.isValidAmountInput(state.amountInput)) {
            _uiState.update { it.copy(errorMessage = "请输入大于 0 的有效金额（最多保留两位小数）") }
            return
        }

        // 校验分类
        val category = state.selectedCategory
        if (category == null) {
            _uiState.update { it.copy(errorMessage = "请选择一个分类") }
            return
        }

        val amountInCents = MoneyUtils.parseYuanToCents(state.amountInput)
        if (amountInCents <= 0L) {
            _uiState.update { it.copy(errorMessage = "金额必须大于 0") }
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

            _uiState.update { it.copy(isSavedSuccess = true) }
        }
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
