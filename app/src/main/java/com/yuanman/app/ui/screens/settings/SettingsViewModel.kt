package com.yuanman.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.CsvExportUtils
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRecordType: RecordType = RecordType.EXPENSE,
    val defaultPaymentMethod: String = PaymentMethod.defaultMethod(),
    val monthlyBudget: Long = 0L,
    val privacyMode: Boolean = false,
    val hapticEnabled: Boolean = true,
    val totalRecordCount: Int = 0,
    val allRecords: List<RecordWithCategory> = emptyList(),
    val isClearedSuccess: Boolean = false,
    val isLoading: Boolean = false
)

private data class GeneralPrefs(
    val theme: ThemeMode,
    val defaultType: RecordType,
    val defaultMethod: String
)

private data class FeaturePrefs(
    val budget: Long,
    val privacy: Boolean,
    val haptic: Boolean
)

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val recordRepository: RecordRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    private val _isClearedSuccess = MutableStateFlow(false)

    private val generalPrefsFlow = combine(
        preferencesRepository.themeMode,
        preferencesRepository.defaultRecordType,
        preferencesRepository.defaultPaymentMethod
    ) { theme, type, method -> GeneralPrefs(theme, type, method) }

    private val featurePrefsFlow = combine(
        preferencesRepository.monthlyBudget,
        preferencesRepository.privacyMode,
        preferencesRepository.hapticFeedbackEnabled
    ) { budget, privacy, haptic -> FeaturePrefs(budget, privacy, haptic) }

    val uiState: StateFlow<SettingsUiState> = combine(
        generalPrefsFlow,
        featurePrefsFlow,
        recordRepository.getAllRecords(),
        _isClearedSuccess
    ) { general, feature, records, isCleared ->
        SettingsUiState(
            themeMode = general.theme,
            defaultRecordType = general.defaultType,
            defaultPaymentMethod = general.defaultMethod,
            monthlyBudget = feature.budget,
            privacyMode = feature.privacy,
            hapticEnabled = feature.haptic,
            totalRecordCount = records.size,
            allRecords = records,
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

    fun setMonthlyBudget(budgetCents: Long) {
        viewModelScope.launch {
            preferencesRepository.setMonthlyBudget(budgetCents)
        }
    }

    fun setPrivacyMode(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPrivacyMode(enabled)
        }
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setHapticFeedbackEnabled(enabled)
        }
    }

    fun exportRecordsCsv(context: Context) {
        val records = uiState.value.allRecords
        CsvExportUtils.shareCsvContent(context, records)
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
