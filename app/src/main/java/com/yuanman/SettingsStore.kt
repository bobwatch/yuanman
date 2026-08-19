package com.yuanman

import android.content.Context
import org.json.JSONObject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val json: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromJson(value: String?): ThemeMode = when (value) {
            LIGHT.json -> LIGHT
            DARK.json -> DARK
            else -> SYSTEM
        }
    }
}

/**
 * 应用设置（SharedPreferences 持久化，StateFlow 驱动 UI 重组）：
 * 深色模式、月度预算、首次启动引导标记。
 */
class SettingsStore private constructor(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(ThemeMode.fromJson(prefs.getString(KEY_THEME, null)))
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    /** 月度预算（分），0 表示未设置。 */
    private val _budgetCents = MutableStateFlow(prefs.getLong(KEY_BUDGET, 0L))
    val budgetCents: StateFlow<Long> = _budgetCents.asStateFlow()

    /** 月度预算历史：月份 "yyyy-MM" → 预算分（用户手动设置过的月份，0 = 该月清除了预算）。 */
    private val _budgetHistory = MutableStateFlow(loadBudgetHistory())
    val budgetHistory: StateFlow<Map<String, Long>> = _budgetHistory.asStateFlow()

    private val _onboardingSeen =
        MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING, false))
    val onboardingSeen: StateFlow<Boolean> = _onboardingSeen.asStateFlow()

    /** 上月小结卡已关闭的月份（"2026-07" 格式），同月关闭后不再显示。 */
    private val _summaryDismissedMonth =
        MutableStateFlow(prefs.getString(KEY_SUMMARY_DISMISSED, "") ?: "")
    val summaryDismissedMonth: StateFlow<String> = _summaryDismissedMonth.asStateFlow()

    /** 最近使用的分类（新→旧，最多 5 个；记账 Sheet 排序与默认值用）。 */
    private val _recentExpenseCategories =
        MutableStateFlow(loadRecentCategories(KEY_RECENT_EXPENSE_CATS))
    val recentExpenseCategories: StateFlow<List<String>> =
        _recentExpenseCategories.asStateFlow()

    private val _recentIncomeCategories =
        MutableStateFlow(loadRecentCategories(KEY_RECENT_INCOME_CATS))
    val recentIncomeCategories: StateFlow<List<String>> =
        _recentIncomeCategories.asStateFlow()

    /** 已解锁勋章：id → 解锁日期（yyyy-MM-dd）。 */
    private val _badgeUnlocks = MutableStateFlow(loadBadgeUnlocks())
    val badgeUnlocks: StateFlow<Map<String, String>> = _badgeUnlocks.asStateFlow()

    /** 用户点「稍后再说」的升级版本号；同版本不再弹窗。 */
    private val _updateDismissedVersion =
        MutableStateFlow(prefs.getString(KEY_UPDATE_DISMISSED, "") ?: "")
    val updateDismissedVersion: StateFlow<String> = _updateDismissedVersion.asStateFlow()

    fun setUpdateDismissedVersion(version: String) {
        _updateDismissedVersion.value = version
        prefs.edit().putString(KEY_UPDATE_DISMISSED, version).apply()
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        prefs.edit().putString(KEY_THEME, mode.json).apply()
    }

    fun setBudgetCents(cents: Long) {
        _budgetCents.value = cents
        prefs.edit().putLong(KEY_BUDGET, cents).apply()
        // 每次设置都记入「本月」历史点，预算趋势图/历史页按设置过的月份向前继承
        val updated = _budgetHistory.value + (DateUtils.monthPrefix() to cents)
        _budgetHistory.value = updated
        prefs.edit().putString(KEY_BUDGET_HISTORY, budgetHistoryToJson(updated)).apply()
    }

    private fun loadBudgetHistory(): Map<String, Long> {
        val result = mutableMapOf<String, Long>()
        val raw = prefs.getString(KEY_BUDGET_HISTORY, "") ?: ""
        if (raw.isNotBlank()) {
            try {
                val obj = JSONObject(raw)
                obj.keys().forEach { key -> result[key] = obj.optLong(key, 0L) }
            } catch (e: Exception) {
                result.clear()
            }
        }
        // 老版本只有单值：没有任何历史点时，把当前预算当作「本月设置」补一个点，
        // 否则历史页/趋势图对老用户是空的
        if (result.isEmpty()) {
            val legacy = prefs.getLong(KEY_BUDGET, 0L)
            if (legacy > 0L) result[DateUtils.monthPrefix()] = legacy
        }
        return result
    }

    private fun budgetHistoryToJson(map: Map<String, Long>): String {
        val obj = JSONObject()
        map.forEach { (key, value) -> obj.put(key, value) }
        return obj.toString()
    }

    fun setOnboardingSeen() {
        _onboardingSeen.value = true
        prefs.edit().putBoolean(KEY_ONBOARDING, true).apply()
    }

    fun dismissSummary(monthKey: String) {
        _summaryDismissedMonth.value = monthKey
        prefs.edit().putString(KEY_SUMMARY_DISMISSED, monthKey).apply()
    }

    fun pushRecentCategory(type: Transaction.Type, category: String) {
        if (type == Transaction.Type.EXPENSE) {
            val updated =
                (listOf(category) + _recentExpenseCategories.value.filter { it != category })
                    .take(MAX_RECENT)
            _recentExpenseCategories.value = updated
            prefs.edit()
                .putString(KEY_RECENT_EXPENSE_CATS, updated.joinToString("\n"))
                .apply()
        } else {
            val updated =
                (listOf(category) + _recentIncomeCategories.value.filter { it != category })
                    .take(MAX_RECENT)
            _recentIncomeCategories.value = updated
            prefs.edit()
                .putString(KEY_RECENT_INCOME_CATS, updated.joinToString("\n"))
                .apply()
        }
    }

    private fun loadRecentCategories(key: String): List<String> =
        prefs.getString(key, "")
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun setBadgeUnlocks(map: Map<String, String>) {
        _badgeUnlocks.value = map
        prefs.edit()
            .putString(
                KEY_BADGES,
                map.entries.joinToString(";") { "${it.key}=${it.value}" }
            )
            .apply()
    }

    private fun loadBadgeUnlocks(): Map<String, String> {
        val raw = prefs.getString(KEY_BADGES, "") ?: ""
        if (raw.isEmpty()) return emptyMap()
        return raw.split(";").mapNotNull { entry ->
            val idx = entry.indexOf('=')
            if (idx > 0) entry.substring(0, idx) to entry.substring(idx + 1) else null
        }.toMap()
    }

    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_BUDGET = "monthly_budget_cents"
        private const val KEY_BUDGET_HISTORY = "budget_history"
        private const val KEY_ONBOARDING = "onboarding_seen"
        private const val KEY_SUMMARY_DISMISSED = "last_summary_dismissed_month"
        private const val KEY_RECENT_EXPENSE_CATS = "recent_expense_categories"
        private const val KEY_RECENT_INCOME_CATS = "recent_income_categories"
        private const val KEY_BADGES = "badge_unlocks"
        private const val KEY_UPDATE_DISMISSED = "update_dismissed_version"
        private const val MAX_RECENT = 5

        @Volatile
        private var instance: SettingsStore? = null

        fun getInstance(context: Context): SettingsStore =
            instance ?: synchronized(this) {
                instance ?: SettingsStore(context.applicationContext).also { instance = it }
            }
    }
}
