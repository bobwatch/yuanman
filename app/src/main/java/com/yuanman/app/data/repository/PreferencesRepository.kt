package com.yuanman.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yuanman.app.data.local.DatabaseBackupManager
import com.yuanman.app.widget.WidgetUpdateManager
import java.io.File
import java.util.Calendar

private val dataStoreLock = Any()
private val dataStores = HashMap<Context, DataStore<Preferences>>()

/**
 * 进程内单例 DataStore；若文件被意外写坏（如进程被强杀），自动以空配置替换，
 * 避免启动时直接崩溃闪退（MIUI 强杀场景实测会触发 CorruptionException）。
 */
val Context.dataStore: DataStore<Preferences>
    get() {
        val appContext = applicationContext
        synchronized(dataStoreLock) {
            dataStores[appContext]?.let { return it }
        }
        val store = PreferenceDataStoreFactory.create(
            produceFile = {
                File(appContext.filesDir, "datastore/yuanman_preferences.preferences_pb")
            },
            corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
        )
        synchronized(dataStoreLock) {
            dataStores[appContext] = store
            return store
        }
    }

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_RECORD_TYPE = stringPreferencesKey("default_record_type")
        val DEFAULT_PAYMENT_METHOD = stringPreferencesKey("default_payment_method")
        val DEFAULT_EXPENSE_ACCOUNT = stringPreferencesKey("default_expense_account")
        val DEFAULT_INCOME_ACCOUNT = stringPreferencesKey("default_income_account")
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget")
        val MONTHLY_BUDGETS = stringPreferencesKey("monthly_budgets")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val QUICK_ENTRY_ENABLED = booleanPreferencesKey("quick_entry_enabled")
        val CUSTOM_TAGS = stringPreferencesKey("custom_tags")
        val ACCOUNTS_DATA = stringPreferencesKey("accounts_data")
        val SAVING_PLANS_DATA = stringPreferencesKey("saving_plans_data")
        val PAYCHECK_SCHEME_DATA = stringPreferencesKey("paycheck_scheme_data")
        val PAYCHECK_LAST_RUN_DATA = stringPreferencesKey("paycheck_last_run_data")
        val RECONCILE_CYCLE_DATA = stringPreferencesKey("reconcile_cycle_data")
        val PAYCHECK_AUTO_ENABLED = booleanPreferencesKey("paycheck_auto_enabled")
        val PAYCHECK_AUTO_APPLIED_IDS = stringPreferencesKey("paycheck_auto_applied_ids")
        val PAYCHECK_RUN_HISTORY_DATA = stringPreferencesKey("paycheck_run_history_data")
    }

    val defaultPresetTags = listOf("早餐", "午餐", "晚餐", "奶茶咖啡", "外卖", "超市买菜", "地铁打车", "零食水果", "日用品", "房租水电", "聚会请客", "网购")

    val customTags: Flow<List<String>> = context.dataStore.data.map { preferences ->
        val raw = preferences[PreferencesKeys.CUSTOM_TAGS]
        if (raw.isNullOrBlank()) {
            defaultPresetTags
        } else {
            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeStr = preferences[PreferencesKeys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeStr)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    val defaultRecordType: Flow<RecordType> = context.dataStore.data.map { preferences ->
        val typeStr = preferences[PreferencesKeys.DEFAULT_RECORD_TYPE] ?: RecordType.EXPENSE.name
        try {
            RecordType.valueOf(typeStr)
        } catch (e: Exception) {
            RecordType.EXPENSE
        }
    }

    val defaultPaymentMethod: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_PAYMENT_METHOD] ?: PaymentMethod.defaultMethod()
    }

    val defaultExpenseAccount: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_EXPENSE_ACCOUNT]
            ?: preferences[PreferencesKeys.DEFAULT_PAYMENT_METHOD]
            ?: PaymentMethod.defaultMethod()
    }

    val defaultIncomeAccount: Flow<String> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.DEFAULT_INCOME_ACCOUNT] ?: ""
    }

    /** Legacy/default budget, kept for users who upgraded from the old single-budget version. */
    private val legacyMonthlyBudget: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MONTHLY_BUDGET] ?: 0L
    }

    /** Explicit budgets keyed by yyyy-MM, so changing the month also changes the budget shown on Home. */
    val monthlyBudgets: Flow<Map<String, Long>> = context.dataStore.data.map { preferences ->
        parseMonthlyBudgets(preferences[PreferencesKeys.MONTHLY_BUDGETS])
    }

    val monthlyBudget: Flow<Long> = combine(monthlyBudgets, legacyMonthlyBudget) { budgets, legacy ->
        budgets[monthKey(currentYear(), currentMonth())] ?: legacy
    }

    val privacyMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PRIVACY_MODE] ?: false
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] ?: true
    }

    /** Natural-language quick entry is enabled by default and can be hidden from Settings. */
    val quickEntryEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.QUICK_ENTRY_ENABLED] ?: true
    }

    val accountsData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.ACCOUNTS_DATA]
    }

    suspend fun saveAccountsData(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.ACCOUNTS_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    // ---- 攒钱计划 & 发薪分配（v0.3）：与账户同构的 JSON DataStore 持久化 ----

    val savingPlansData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.SAVING_PLANS_DATA]
    }

    val paycheckSchemeData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAYCHECK_SCHEME_DATA]
    }

    val paycheckLastRunData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAYCHECK_LAST_RUN_DATA]
    }

    suspend fun saveSavingPlansData(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.SAVING_PLANS_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun savePaycheckSchemeData(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAYCHECK_SCHEME_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun savePaycheckLastRunData(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAYCHECK_LAST_RUN_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    // ---- 账户核对全局周期（账户可各自自定义覆盖，见账户 JSON reconcileCycleOverride）----

    val reconcileCycleData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.RECONCILE_CYCLE_DATA]
    }

    suspend fun saveReconcileCycleData(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.RECONCILE_CYCLE_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    // ---- v0.0.4.5：工资到账自动分账（开关 / 已执行收入记录 / 执行历史）----

    /** 工资到账自动分账总开关，默认开 */
    val paycheckAutoEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAYCHECK_AUTO_ENABLED] ?: true
    }

    suspend fun setPaycheckAutoEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAYCHECK_AUTO_ENABLED] = enabled
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    /** 已自动分账过的收入记录 id（逗号分隔字符串）—— 幂等守卫，防止同一笔工资重复执行 */
    val paycheckAutoAppliedIds: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAYCHECK_AUTO_APPLIED_IDS]
    }

    suspend fun addPaycheckAutoAppliedId(recordId: Long) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.PAYCHECK_AUTO_APPLIED_IDS].orEmpty()
            val ids = raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableSet()
            if (ids.add(recordId)) {
                preferences[PreferencesKeys.PAYCHECK_AUTO_APPLIED_IDS] = ids.joinToString(",")
            }
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    /** 历次发薪分账执行记录（JSON 数组，最新在前，最多保留 30 条） */
    val paycheckRunHistoryData: Flow<String?> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PAYCHECK_RUN_HISTORY_DATA]
    }

    suspend fun savePaycheckRunHistory(json: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PAYCHECK_RUN_HISTORY_DATA] = json
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setDefaultRecordType(type: RecordType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_RECORD_TYPE] = type.name
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setDefaultPaymentMethod(method: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_PAYMENT_METHOD] = method
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setDefaultExpenseAccount(account: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_EXPENSE_ACCOUNT] = account
            if (account.isNotBlank()) {
                preferences[PreferencesKeys.DEFAULT_PAYMENT_METHOD] = account
            }
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setDefaultIncomeAccount(account: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_INCOME_ACCOUNT] = account
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setMonthlyBudget(budgetCents: Long) {
        setBudgetForMonth(currentYear(), currentMonth(), budgetCents)
    }

    suspend fun setBudgetForMonth(year: Int, month: Int, budgetCents: Long) {
        context.dataStore.edit { preferences ->
            val budgets = parseMonthlyBudgets(preferences[PreferencesKeys.MONTHLY_BUDGETS]).toMutableMap()
            budgets[monthKey(year, month)] = budgetCents.coerceAtLeast(0L)
            preferences[PreferencesKeys.MONTHLY_BUDGETS] = serializeMonthlyBudgets(budgets)
        }
        WidgetUpdateManager.requestUpdate(context)
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVACY_MODE] = enabled
        }
        WidgetUpdateManager.requestUpdate(context)
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun togglePrivacyMode() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PRIVACY_MODE] ?: false
            preferences[PreferencesKeys.PRIVACY_MODE] = !current
        }
        WidgetUpdateManager.requestUpdate(context)
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setQuickEntryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUICK_ENTRY_ENABLED] = enabled
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun setCustomTags(tags: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_TAGS] = tags.joinToString(",")
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun addCustomTag(tag: String) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_TAGS]
            val current = if (raw.isNullOrBlank()) defaultPresetTags else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (!current.contains(trimmed)) {
                preferences[PreferencesKeys.CUSTOM_TAGS] = (current + trimmed).joinToString(",")
            }
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun updateCustomTag(oldTag: String, newTag: String) {
        val trimmed = newTag.trim()
        if (trimmed.isEmpty()) return
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_TAGS]
            val current = if (raw.isNullOrBlank()) defaultPresetTags else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val updated = current.map { if (it == oldTag) trimmed else it }.distinct()
            preferences[PreferencesKeys.CUSTOM_TAGS] = updated.joinToString(",")
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun deleteCustomTag(tag: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_TAGS]
            val current = if (raw.isNullOrBlank()) defaultPresetTags else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val updated = current.filterNot { it == tag }
            preferences[PreferencesKeys.CUSTOM_TAGS] = updated.joinToString(",")
        }
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        WidgetUpdateManager.requestUpdate(context)
        DatabaseBackupManager.scheduleAutoBackupSoon(context)
    }

    suspend fun getWidgetPreferences(year: Int, month: Int): WidgetPreferences {
        val preferences = context.dataStore.data.first()
        val budgets = parseMonthlyBudgets(preferences[PreferencesKeys.MONTHLY_BUDGETS])
        val legacy = preferences[PreferencesKeys.MONTHLY_BUDGET] ?: 0L
        return WidgetPreferences(
            monthlyBudget = budgets[monthKey(year, month)] ?: legacy,
            privacyMode = preferences[PreferencesKeys.PRIVACY_MODE] ?: false
        )
    }

    companion object {
        fun monthKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

        private fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)
        private fun currentMonth(): Int = Calendar.getInstance().get(Calendar.MONTH) + 1

        private fun parseMonthlyBudgets(raw: String?): Map<String, Long> {
            if (raw.isNullOrBlank()) return emptyMap()
            return raw.split(',').mapNotNull { entry ->
                val parts = entry.split(':', limit = 2)
                if (parts.size != 2) return@mapNotNull null
                parts[0].takeIf { it.matches(Regex("\\d{4}-\\d{2}")) }
                    ?.let { key -> key to (parts[1].toLongOrNull()?.coerceAtLeast(0L) ?: return@mapNotNull null) }
            }.toMap()
        }

        private fun serializeMonthlyBudgets(budgets: Map<String, Long>): String =
            budgets.toSortedMap().entries.joinToString(",") { "${it.key}:${it.value}" }
    }
}

data class WidgetPreferences(
    val monthlyBudget: Long,
    val privacyMode: Boolean
)
