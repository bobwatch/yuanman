package com.yuanman.app.ui.screens.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.yuanman.app.data.local.DatabaseBackupManager
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
            quickEntryEnabled = feature.quickEntryEnabled,
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

    fun exportJsonBackup(context: Context) {
        val categories = uiState.value.allCategories
        val records = uiState.value.allRecords
        JsonBackupUtils.shareBackupFile(context, categories, records)
    }

    /**
     * 立即手动备份：分类、账单与个人习惯(偏好)整体快照到公共 Documents。
     * 卸载/重装后应用可从快照自动恢复，实现数据不丢失。
     */
    fun backupDataToDocumentsNow(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val ok = DatabaseBackupManager.createManualBackup(context)
            onResult(
                ok,
                if (ok) {
                    "已备份到 文档/Yuanman 目录，包含分类、全部账单与个人习惯"
                } else {
                    "备份失败，请稍后重试"
                }
            )
        }
    }

    /**
     * 从用户选择的备份文件整体还原(数据库快照或偏好快照)。
     * 文件名含 preferences 视为个人习惯快照，否则视为数据库快照。
     */
    fun restoreFromBackupFile(context: Context, uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val displayName = context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                val isPreferences = displayName?.contains("preferences", ignoreCase = true) == true

                val result = if (isPreferences) {
                    DatabaseBackupManager.restorePreferencesFromUri(context, uri)
                } else {
                    DatabaseBackupManager.restoreFromUri(context, uri)
                }
                result.onSuccess {
                    onResult(
                        true,
                        if (isPreferences) {
                            "个人习惯(预算/标签/快捷设置)已恢复，重启应用后生效"
                        } else {
                            "分类与全部账单已恢复，重启应用后生效"
                        }
                    )
                }.onFailure { e ->
                    onResult(false, e.message ?: "恢复失败")
                }
            } catch (e: Exception) {
                onResult(false, "恢复失败：${e.message ?: "无法读取所选文件"}")
            }
        }
    }

    /**
     * 从公共 Documents/Yuanman 目录自动扫描并恢复最近的备份(需"所有文件访问"权限)。
     * 用于卸载重装后 MediaStore 索引已被系统清除、无法从文件选择器定位备份的场景。
     */
    fun restoreFromDocumentsNow(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = DatabaseBackupManager.restoreFromDocuments(context)
            result.onSuccess {
                onResult(true, "已从 文档/Yuanman 恢复分类、全部账单与个人习惯")
            }.onFailure { e ->
                onResult(false, e.message ?: "从文档恢复失败")
            }
        }
    }

    fun restoreFromJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val data = JsonBackupUtils.parseFromJsonString(jsonString)
                categoryRepository.mergeSyncedData(data.categories, data.records)
                recordRepository.notifyDataChanged()
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
