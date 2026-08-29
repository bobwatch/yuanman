package com.yuanman.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.yuanman.app.widget.WidgetUpdateManager
import java.util.Calendar

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yuanman_preferences")

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_RECORD_TYPE = stringPreferencesKey("default_record_type")
        val DEFAULT_PAYMENT_METHOD = stringPreferencesKey("default_payment_method")
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget")
        val MONTHLY_BUDGETS = stringPreferencesKey("monthly_budgets")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val QUICK_ENTRY_ENABLED = booleanPreferencesKey("quick_entry_enabled")
        val CUSTOM_TAGS = stringPreferencesKey("custom_tags")
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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.THEME_MODE] = mode.name
        }
    }

    suspend fun setDefaultRecordType(type: RecordType) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_RECORD_TYPE] = type.name
        }
    }

    suspend fun setDefaultPaymentMethod(method: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_PAYMENT_METHOD] = method
        }
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
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVACY_MODE] = enabled
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun togglePrivacyMode() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PRIVACY_MODE] ?: false
            preferences[PreferencesKeys.PRIVACY_MODE] = !current
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun setQuickEntryEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.QUICK_ENTRY_ENABLED] = enabled
        }
    }

    suspend fun setCustomTags(tags: List<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_TAGS] = tags.joinToString(",")
        }
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
    }

    suspend fun deleteCustomTag(tag: String) {
        context.dataStore.edit { preferences ->
            val raw = preferences[PreferencesKeys.CUSTOM_TAGS]
            val current = if (raw.isNullOrBlank()) defaultPresetTags else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val updated = current.filterNot { it == tag }
            preferences[PreferencesKeys.CUSTOM_TAGS] = updated.joinToString(",")
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
        WidgetUpdateManager.requestUpdate(context)
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
