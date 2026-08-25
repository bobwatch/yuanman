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
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "yuanman_preferences")

class PreferencesRepository(private val context: Context) {

    private object PreferencesKeys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_RECORD_TYPE = stringPreferencesKey("default_record_type")
        val DEFAULT_PAYMENT_METHOD = stringPreferencesKey("default_payment_method")
        val MONTHLY_BUDGET = longPreferencesKey("monthly_budget")
        val PRIVACY_MODE = booleanPreferencesKey("privacy_mode")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
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

    val monthlyBudget: Flow<Long> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.MONTHLY_BUDGET] ?: 0L
    }

    val privacyMode: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.PRIVACY_MODE] ?: false
    }

    val hapticFeedbackEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] ?: true
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
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONTHLY_BUDGET] = budgetCents
        }
    }

    suspend fun setPrivacyMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.PRIVACY_MODE] = enabled
        }
    }

    suspend fun togglePrivacyMode() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.PRIVACY_MODE] ?: false
            preferences[PreferencesKeys.PRIVACY_MODE] = !current
        }
    }

    suspend fun setHapticFeedbackEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
