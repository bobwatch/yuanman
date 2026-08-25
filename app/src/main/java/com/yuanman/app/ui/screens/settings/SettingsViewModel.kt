package com.yuanman.app.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import com.yuanman.app.utils.CsvExportUtils
import com.yuanman.app.utils.JsonBackupUtils
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
    val allCategories: List<CategoryEntity> = emptyList(),
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
        categoryRepository.getAllCategories()
    ) { general, feature, records, categories ->
        SettingsUiState(
            themeMode = general.theme,
            defaultRecordType = general.defaultType,
            defaultPaymentMethod = general.defaultMethod,
            monthlyBudget = feature.budget,
            privacyMode = feature.privacy,
            hapticEnabled = feature.haptic,
            totalRecordCount = records.size,
            allRecords = records,
            allCategories = categories,
            isClearedSuccess = _isClearedSuccess.value,
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

    fun exportJsonBackup(context: Context) {
        val categories = uiState.value.allCategories
        val records = uiState.value.allRecords
        JsonBackupUtils.shareBackupFile(context, categories, records)
    }

    fun restoreFromJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = JsonBackupUtils.parseFromJsonString(jsonString)
                if (data.categories.isNotEmpty()) {
                    categoryRepository.insertCategories(data.categories)
                }
                if (data.records.isNotEmpty()) {
                    recordRepository.insertRecords(data.records)
                }
                onResult(true, "成功恢复 ${data.records.size} 笔账单与 ${data.categories.size} 个分类！")
            } catch (e: Exception) {
                onResult(false, "备份解析失败：${e.message}")
            }
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
