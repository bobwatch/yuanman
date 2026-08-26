package com.yuanman.app.ui.screens.settings

import android.content.Context
import android.net.Uri
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
import com.yuanman.app.sync.FamilySyncManager
import com.yuanman.app.utils.CsvExportUtils
import com.yuanman.app.utils.CsvImportUtils
import com.yuanman.app.utils.ImportResult
import com.yuanman.app.utils.JsonBackupUtils
import com.yuanman.app.utils.UpdateInfo
import com.yuanman.app.utils.UpdateManager
import com.yuanman.app.utils.UpdateState
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
    private val categoryRepository: CategoryRepository,
    val syncManager: FamilySyncManager,
    val updateManager: UpdateManager
) : ViewModel() {

    private val _isClearedSuccess = MutableStateFlow(false)

    val updateState: StateFlow<UpdateState> = updateManager.updateState

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

    val allCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<RecordWithCategory>> = recordRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<SettingsUiState> = combine(
        generalPrefsFlow,
        featurePrefsFlow,
        allCategories,
        allRecords,
        _isClearedSuccess
    ) { general, feature, categories, records, cleared ->
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
            isClearedSuccess = cleared,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )



    fun checkForUpdates() {
        updateManager.checkForUpdates(isManual = true)
    }

    fun startDownload(info: UpdateInfo) {
        updateManager.startDownload(info)
    }

    fun installApk(file: java.io.File) {
        updateManager.installApk(file)
    }

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

    fun setMonthlyBudget(budget: Long) {
        viewModelScope.launch {
            preferencesRepository.setMonthlyBudget(budget)
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

    fun importRecordsFromCsv(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val result = CsvImportUtils.importFromCsvUri(
                    context = context,
                    uri = uri,
                    categoryRepository = categoryRepository,
                    recordRepository = recordRepository
                )
                if (result.successCount > 0) {
                    onResult(true, result.message)
                } else {
                    onResult(false, result.message)
                }
            } catch (e: Exception) {
                onResult(false, "导入失败：${e.message ?: "表格格式错误"}")
            }
        }
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
        private val categoryRepository: CategoryRepository,
        private val syncManager: FamilySyncManager,
        private val updateManager: UpdateManager
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(
                preferencesRepository,
                recordRepository,
                categoryRepository,
                syncManager,
                updateManager
            ) as T
        }
    }
}
