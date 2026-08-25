package com.yuanman.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRecordType: RecordType = RecordType.EXPENSE,
    val defaultPaymentMethod: String = PaymentMethod.defaultMethod(),
    val totalRecordCount: Int = 0,
    val isClearedSuccess: Boolean = false,
    val isLoading: Boolean = false
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _isClearedSuccess = MutableStateFlow(false)

    val uiState: StateFlow<SettingsUiState> = combine(
        preferencesRepository.themeMode,
        preferencesRepository.defaultRecordType,
        preferencesRepository.defaultPaymentMethod,
        recordRepository.getAllRecords(),
        _isClearedSuccess
    ) { theme, defaultType, defaultMethod, records, isCleared ->
        SettingsUiState(
            themeMode = theme,
            defaultRecordType = defaultType,
            defaultPaymentMethod = defaultMethod,
            totalRecordCount = records.size,
            isClearedSuccess = isCleared,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setDefaultRecordType(type: RecordType) {
        viewModelScope.launch {
            preferencesRepository.setDefaultRecordType(type)
        }
    }

    fun setDefaultPaymentMethod(method: String) {
        viewModelScope.launch {
            preferencesRepository.setDefaultPaymentMethod(method)
        }
    }

    /**
     * 清空全部数据：清空账单记录，恢复默认预置分类
     */
    fun clearAllData() {
        viewModelScope.launch {
            recordRepository.deleteAllRecords()
            categoryRepository.resetDefaultCategories()
            _isClearedSuccess.value = true
        }
    }

    fun resetClearedFlag() {
        _isClearedSuccess.value = false
    }

    class Factory(
        private val preferencesRepository: PreferencesRepository,
        private val recordRepository: RecordRepository,
        private val categoryRepository: CategoryRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                recordRepository,
                categoryRepository
            ) as T
        }
    }
}
