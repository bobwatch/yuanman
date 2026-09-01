package com.yuanman.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val defaultRecordType: RecordType = RecordType.EXPENSE,
    val defaultPaymentMethod: String = PaymentMethod.defaultMethod(),
    val monthlyBudget: Long = 0L,
    val privacyMode: Boolean = false,
    val hapticEnabled: Boolean = true,
    val quickEntryEnabled: Boolean = true,
    val totalRecordCount: Int = 0,
    val allRecords: List<RecordWithCategory> = emptyList(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val lastBackupAt: Long = 0L,
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
    val haptic: Boolean,
    val quickEntryEnabled: Boolean
)

private data class SettingsData(
    val categories: List<CategoryEntity>,
    val records: List<RecordWithCategory>,
    val lastBackupAt: Long
)

data class PendingJsonRestore(
    val json: String,
    val preview: JsonBackupUtils.BackupPreview
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
    val hasUnseenUpdate: StateFlow<Boolean> = updateManager.hasUnseenUpdate

    private val generalPrefsFlow = combine(
        preferencesRepository.themeMode,
        preferencesRepository.defaultRecordType,
        preferencesRepository.defaultPaymentMethod
    ) { theme, type, method -> GeneralPrefs(theme, type, method) }

    private val featurePrefsFlow = combine(
        preferencesRepository.monthlyBudget,
        preferencesRepository.privacyMode,
        preferencesRepository.hapticFeedbackEnabled,
        preferencesRepository.quickEntryEnabled
    ) { budget, privacy, haptic, quickEntryEnabled ->
        FeaturePrefs(budget, privacy, haptic, quickEntryEnabled)
    }

    val allCategories: StateFlow<List<CategoryEntity>> = categoryRepository.getAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allRecords: StateFlow<List<RecordWithCategory>> = recordRepository.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val quickEntryLearningRules: StateFlow<List<QuickEntryLearningEntity>> =
        categoryRepository.observeAllQuickEntryLearning()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val settingsDataFlow = combine(
        allCategories,
        allRecords,
        preferencesRepository.lastBackupAt
    ) { categories, records, lastBackupAt -> SettingsData(categories, records, lastBackupAt) }

    val uiState: StateFlow<SettingsUiState> = combine(
        generalPrefsFlow,
        featurePrefsFlow,
        settingsDataFlow,
        _isClearedSuccess
    ) { general, feature, data, cleared ->
        SettingsUiState(
            themeMode = general.theme,
            defaultRecordType = general.defaultType,
            defaultPaymentMethod = general.defaultMethod,
            monthlyBudget = feature.budget,
            privacyMode = feature.privacy,
            hapticEnabled = feature.haptic,
            quickEntryEnabled = feature.quickEntryEnabled,
            totalRecordCount = data.records.size,
            allRecords = data.records,
            allCategories = data.categories,
            lastBackupAt = data.lastBackupAt,
            isClearedSuccess = cleared,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(isLoading = true)
    )



    fun checkForUpdates(isManual: Boolean = true) {
        updateManager.checkForUpdates(isManual = isManual)
    }

    fun markUpdateSeen(versionName: String) {
        updateManager.markUpdateSeen(versionName)
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

    fun setQuickEntryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setQuickEntryEnabled(enabled)
        }
    }

    /** 清除快捷记账根据用户保存记录形成的个人分类习惯。 */
    fun clearQuickEntryLearning() {
        viewModelScope.launch {
            categoryRepository.clearQuickEntryLearning()
        }
    }

    fun updateQuickEntryLearning(rule: QuickEntryLearningEntity, phrase: String) {
        viewModelScope.launch { categoryRepository.updateQuickEntryLearning(rule, phrase) }
    }

    fun addQuickEntryLearning(type: RecordType, phrase: String, categorySyncId: String) {
        viewModelScope.launch { categoryRepository.learnQuickEntry(type, phrase, categorySyncId) }
    }

    fun updateQuickEntryLearning(rule: QuickEntryLearningEntity, phrase: String, type: RecordType, categorySyncId: String) {
        viewModelScope.launch {
            categoryRepository.updateQuickEntryLearning(rule, phrase, type.name, categorySyncId)
        }
    }

    fun deleteQuickEntryLearning(rule: QuickEntryLearningEntity) {
        viewModelScope.launch { categoryRepository.deleteQuickEntryLearning(rule) }
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
                    categoryRepository.backfillQuickEntryLearning()
                    onResult(true, result.message)
                } else {
                    onResult(false, result.message)
                }
            } catch (e: Exception) {
                onResult(false, "导入失败：${e.message ?: "表格格式错误"}")
            }
        }
    }

    fun exportJsonBackup(context: Context, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val snapshot = preferencesRepository.createSnapshot()
            val result = withContext(Dispatchers.IO) {
                JsonBackupUtils.createBackupFile(
                    context = context,
                    categories = uiState.value.allCategories,
                    records = uiState.value.allRecords,
                    preferences = snapshot,
                    quickEntryLearning = quickEntryLearningRules.value
                )
            }
            result.onSuccess { file ->
                JsonBackupUtils.shareBackupFile(context, file)
                preferencesRepository.markBackupCreated()
                onResult(true, "完整备份已生成，请选择保存位置")
            }.onFailure { onResult(false, "备份失败：${it.message ?: "无法创建文件"}") }
        }
    }

    fun previewJsonBackup(context: Context, uri: Uri, onResult: (Result<PendingJsonRestore>) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        val bytes = input.readBytes()
                        require(bytes.size <= 100 * 1024 * 1024) { "备份文件超过 100MB 限制" }
                        val json = bytes.toString(Charsets.UTF_8)
                        PendingJsonRestore(json, JsonBackupUtils.preview(json))
                    } ?: error("无法读取备份文件")
                }
            }
            onResult(result)
        }
    }

    fun restoreFromJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) {
                    JsonBackupUtils.parseFromJsonString(jsonString).also {
                        com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(
                            com.yuanman.app.YuanmanApplication.instance
                        )
                    }
                }
                categoryRepository.mergeSyncedData(data.categories, data.records)
                categoryRepository.mergeQuickEntryLearning(data.quickEntryLearning)
                data.preferences?.let { preferencesRepository.restoreSnapshot(it) }
                recordRepository.notifyDataChanged()
                onResult(true, "恢复完成：${data.records.count { it.deletedAt == null }} 笔账单、${data.categories.count { it.deletedAt == null }} 个分类")
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
            categoryRepository.clearQuickEntryLearning()
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
