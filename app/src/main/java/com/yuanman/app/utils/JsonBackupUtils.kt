package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.repository.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object JsonBackupUtils {

    // ------------------------------------------------------------------
    // 账户相关数据键（与 PreferencesRepository 中的 DataStore 键一一对应）
    // ------------------------------------------------------------------

    /** 账户 JSON（含对账记录 / 周期覆盖 / 跳过提醒） */
    const val PREF_ACCOUNTS_DATA = "accounts_data"
    /** 攒钱计划 JSON（含存/取事件流水） */
    const val PREF_SAVING_PLANS_DATA = "saving_plans_data"
    /** 发薪分配方案 JSON */
    const val PREF_PAYCHECK_SCHEME_DATA = "paycheck_scheme_data"
    /** 上次发薪执行明细 JSON */
    const val PREF_PAYCHECK_LAST_RUN_DATA = "paycheck_last_run_data"
    /** 发薪执行历史 JSON（最新在前，最多 30 条） */
    const val PREF_PAYCHECK_RUN_HISTORY_DATA = "paycheck_run_history_data"
    /** 全局对账周期 JSON（账户可各自覆盖） */
    const val PREF_RECONCILE_CYCLE_DATA = "reconcile_cycle_data"
    /** 工资到账自动分账总开关（布尔） */
    const val PREF_PAYCHECK_AUTO_ENABLED = "paycheck_auto_enabled"
    /** 已自动分账过的收入记录 id（逗号分隔字符串，幂等守卫） */
    const val PREF_PAYCHECK_AUTO_APPLIED_IDS = "paycheck_auto_applied_ids"
    const val PREF_THEME_MODE = "theme_mode"
    const val PREF_MONTHLY_BUDGET = "monthly_budget"
    const val PREF_MONTHLY_BUDGETS = "monthly_budgets"
    const val PREF_DEFAULT_PAYMENT_METHOD = "default_payment_method"
    const val PREF_DEFAULT_EXPENSE_ACCOUNT = "default_expense_account"
    const val PREF_DEFAULT_INCOME_ACCOUNT = "default_income_account"
    const val PREF_QUICK_ENTRY_ENABLED = "quick_entry_enabled"
    const val PREF_PRIVACY_MODE = "privacy_mode"
    const val PREF_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
    const val PREF_CUSTOM_TAGS = "custom_tags"

    /** 备份 JSON 顶层放账户数据的字段名（与 categories / records 平级；老备份无此段） */
    const val ACCOUNT_DATA_JSON_KEY = "accountData"

    /** 全部账户与偏好相关键（顺序即恢复顺序） */
    private val ACCOUNT_PREF_KEYS = listOf(
        PREF_ACCOUNTS_DATA,
        PREF_SAVING_PLANS_DATA,
        PREF_PAYCHECK_SCHEME_DATA,
        PREF_PAYCHECK_LAST_RUN_DATA,
        PREF_PAYCHECK_RUN_HISTORY_DATA,
        PREF_RECONCILE_CYCLE_DATA,
        PREF_PAYCHECK_AUTO_ENABLED,
        PREF_PAYCHECK_AUTO_APPLIED_IDS,
        PREF_THEME_MODE,
        PREF_MONTHLY_BUDGET,
        PREF_MONTHLY_BUDGETS,
        PREF_DEFAULT_PAYMENT_METHOD,
        PREF_DEFAULT_EXPENSE_ACCOUNT,
        PREF_DEFAULT_INCOME_ACCOUNT,
        PREF_QUICK_ENTRY_ENABLED,
        PREF_PRIVACY_MODE,
        PREF_HAPTIC_FEEDBACK_ENABLED,
        PREF_CUSTOM_TAGS
    )

    data class BackupData(
        val version: String = "2.0",
        val exportedAt: Long = System.currentTimeMillis(),
        val categories: List<CategoryEntity>,
        val records: List<RecordEntity>,
        /** 账户相关 DataStore 键 → 原始值（v0.0.4.6+，老备份文件无此段时为空） */
        val accountData: Map<String, String> = emptyMap()
    )

    /**
     * 导出分类 + 账单 + 账户相关数据 为一个 JSON 字符串。
     * @param accountData 账户相关 DataStore 键 → 原始值（无账户数据时传空 map，产出与旧版完全一致）
     */
    fun exportToJsonString(
        categories: List<CategoryEntity>,
        records: List<RecordWithCategory>,
        accountData: Map<String, String> = emptyMap()
    ): String {
        return exportEntitiesToJsonString(categories, records.map { it.record }, accountData)
    }

    fun exportEntitiesToJsonString(
        categories: List<CategoryEntity>,
        records: List<RecordEntity>,
        accountData: Map<String, String> = emptyMap()
    ): String {
        val root = JSONObject()
        root.put("version", "2.0")
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appName", "Yuanman")

        // 导出分类
        val catArray = JSONArray()
        categories.forEach { c ->
            val obj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("type", c.type)
                put("iconName", c.iconName)
                put("colorHex", c.colorHex)
                put("isDefault", c.isDefault)
                put("sortOrder", c.sortOrder)
                put("tags", c.tags)
                put("createdAt", c.createdAt)
                put("syncId", c.syncId)
                put("updatedAt", c.updatedAt)
                c.deletedAt?.let { put("deletedAt", it) }
            }
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // 导出账单记录
        val recArray = JSONArray()
        records.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.id)
                put("type", r.type)
                put("amount", r.amount)
                put("categoryId", r.categoryId)
                put("recordTime", r.recordTime)
                put("remark", r.remark)
                put("paymentMethod", r.paymentMethod)
                r.splitGroupId?.let { put("splitGroupId", it) }
                r.splitIndex?.let { put("splitIndex", it) }
                r.splitTotal?.let { put("splitTotal", it) }
                put("createdAt", r.createdAt)
                put("updatedAt", r.updatedAt)
                put("syncId", r.syncId)
                r.deletedAt?.let { put("deletedAt", it) }
            }
            recArray.put(obj)
        }
        root.put("records", recArray)

        // 账户相关数据段（key:value，值为 DataStore 原始字符串/布尔；老版本应用会忽略未知字段）
        if (accountData.isNotEmpty()) {
            root.put(
                ACCOUNT_DATA_JSON_KEY,
                JSONObject().apply {
                    accountData.forEach { (key, value) -> put(key, value) }
                }
            )
        }

        return root.toString(2)
    }

    fun parseFromJsonString(jsonString: String, legacySourceId: String = "backup"): BackupData {
        val root = JSONObject(jsonString)
        val categories = mutableListOf<CategoryEntity>()
        val records = mutableListOf<RecordEntity>()

        if (root.has("categories")) {
            val catArray = root.getJSONArray("categories")
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                val name = obj.getString("name").trim()
                val type = obj.getString("type").trim().uppercase(Locale.ROOT)
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                categories.add(
                    CategoryEntity(
                        id = obj.optLong("id", 0L),
                        name = name,
                        type = type,
                        iconName = obj.optString("iconName", "other"),
                        colorHex = obj.optLong("colorHex", 0xFF607D8BL),
                        isDefault = obj.optBoolean("isDefault", false),
                        sortOrder = obj.optInt("sortOrder", 0),
                        tags = obj.optString("tags", ""),
                        createdAt = createdAt,
                        syncId = obj.optString("syncId").ifBlank {
                            AppDatabase.stableCategorySyncId(type, name)
                        },
                        updatedAt = obj.optLong("updatedAt", createdAt),
                        deletedAt = obj.optionalLong("deletedAt")
                    )
                )
            }
        }

        if (root.has("records")) {
            val recArray = root.getJSONArray("records")
            for (i in 0 until recArray.length()) {
                val obj = recArray.getJSONObject(i)
                val legacyId = obj.optLong("id", 0L)
                val legacySyncId = if (legacyId > 0L) {
                    "legacy-record:$legacySourceId:$legacyId"
                } else {
                    "legacy-record:$legacySourceId:index:$i"
                }
                records.add(
                    RecordEntity(
                        id = legacyId,
                        type = obj.getString("type"),
                        amount = obj.getLong("amount"),
                        categoryId = obj.optLong("categoryId", -1L),
                        recordTime = obj.optLong("recordTime", System.currentTimeMillis()),
                        remark = obj.optString("remark", ""),
                        paymentMethod = obj.optString("paymentMethod", "现金"),
                        splitGroupId = obj.optString("splitGroupId", "").ifBlank { null },
                        splitIndex = if (obj.has("splitIndex")) obj.optInt("splitIndex") else null,
                        splitTotal = if (obj.has("splitTotal")) obj.optInt("splitTotal") else null,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        syncId = obj.optString("syncId").ifBlank {
                            legacySyncId
                        },
                        deletedAt = obj.optionalLong("deletedAt")
                    )
                )
            }
        }

        return BackupData(
            version = root.optString("version", "1.0"),
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            categories = categories,
            records = records,
            // 老备份文件没有 accountData 段 → 空 map，恢复方跳过账户段即可，不报错
            accountData = parseAccountDataObject(root.optJSONObject(ACCOUNT_DATA_JSON_KEY))
        )
    }

    // ------------------------------------------------------------------
    // 账户数据段编解码（与 categories/records 平级的 key:value 包）
    // ------------------------------------------------------------------

    /** 从 JSON 对象提取账户键值（仅认已知键；布尔自动转 "true"/"false" 字符串） */
    private fun parseAccountDataObject(section: JSONObject?): Map<String, String> {
        if (section == null) return emptyMap()
        val result = LinkedHashMap<String, String>()
        ACCOUNT_PREF_KEYS.forEach { key ->
            if (section.has(key) && !section.isNull(key)) {
                val v = section.get(key)
                result[key] = when (v) {
                    is Boolean -> if (v) "true" else "false"
                    is Number -> v.toString()
                    else -> section.optString(key, "")
                }
            }
        }
        return result
    }

    /**
     * 独立「账户数据」JSON 快照信封（DatabaseBackupManager 随 .db / 偏好文件一同发布的文件内容）。
     * 结构：{ appName, dataType: "accountData", exportedAt, accountData: { 键: 值 } }，
     * 与完整备份 JSON 中的 accountData 段兼容。
     */
    fun buildAccountDataEnvelopeJson(accountData: Map<String, String>): String =
        JSONObject()
            .put("appName", "Yuanman")
            .put("dataType", "accountData")
            .put("exportedAt", System.currentTimeMillis())
            .put(ACCOUNT_DATA_JSON_KEY, JSONObject().apply {
                accountData.forEach { (key, value) -> put(key, value) }
            })
            .toString(2)

    /**
     * 解析独立「账户数据」快照 JSON（也兼容直接塞 accountData 键值对的对象）。
     * 结构非法或无 accountData 段时返回空 map —— 恢复方跳过账户段，不报错。
     */
    fun parseAccountDataEnvelope(json: String): Map<String, String> = try {
        val root = JSONObject(json)
        val section = root.optJSONObject(ACCOUNT_DATA_JSON_KEY)
        if (section != null) {
            parseAccountDataObject(section)
        } else {
            // 兼容旧结构：整个对象就是键值对
            parseAccountDataObject(root)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        emptyMap()
    }

    /** 读取当前账户相关 DataStore 键 → 原始值（供备份/导出） */
    suspend fun collectAccountData(preferencesRepository: PreferencesRepository): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        suspend fun putIfPresent(key: String, raw: String?) {
            if (!raw.isNullOrBlank()) result[key] = raw
        }
        putIfPresent(PREF_ACCOUNTS_DATA, preferencesRepository.accountsData.first())
        putIfPresent(PREF_SAVING_PLANS_DATA, preferencesRepository.savingPlansData.first())
        putIfPresent(PREF_PAYCHECK_SCHEME_DATA, preferencesRepository.paycheckSchemeData.first())
        putIfPresent(PREF_PAYCHECK_LAST_RUN_DATA, preferencesRepository.paycheckLastRunData.first())
        putIfPresent(PREF_PAYCHECK_RUN_HISTORY_DATA, preferencesRepository.paycheckRunHistoryData.first())
        putIfPresent(PREF_RECONCILE_CYCLE_DATA, preferencesRepository.reconcileCycleData.first())
        putIfPresent(PREF_PAYCHECK_AUTO_APPLIED_IDS, preferencesRepository.paycheckAutoAppliedIds.first())
        result[PREF_PAYCHECK_AUTO_ENABLED] = if (preferencesRepository.paycheckAutoEnabled.first()) "true" else "false"
        putIfPresent(PREF_THEME_MODE, preferencesRepository.themeMode.first().name)
        putIfPresent(PREF_MONTHLY_BUDGETS, preferencesRepository.monthlyBudgetsRaw.first())
        putIfPresent(PREF_DEFAULT_PAYMENT_METHOD, preferencesRepository.defaultPaymentMethod.first())
        putIfPresent(PREF_DEFAULT_EXPENSE_ACCOUNT, preferencesRepository.defaultExpenseAccount.first())
        putIfPresent(PREF_DEFAULT_INCOME_ACCOUNT, preferencesRepository.defaultIncomeAccount.first())
        result[PREF_QUICK_ENTRY_ENABLED] = if (preferencesRepository.quickEntryEnabled.first()) "true" else "false"
        result[PREF_PRIVACY_MODE] = if (preferencesRepository.privacyMode.first()) "true" else "false"
        result[PREF_HAPTIC_FEEDBACK_ENABLED] = if (preferencesRepository.hapticFeedbackEnabled.first()) "true" else "false"
        putIfPresent(PREF_CUSTOM_TAGS, preferencesRepository.customTags.first().joinToString(","))
        return result
    }

    /** 读取当前账户数据并打包为独立快照 JSON 文本（挂起版本，供 suspend 调用方） */
    suspend fun collectAccountDataEnvelope(context: Context): String {
        val data = collectAccountData(PreferencesRepository(context))
        if (data.isEmpty()) return ""
        return buildAccountDataEnvelopeJson(data)
    }

    /** 阻塞版本：DatabaseBackupManager.autoBackup 等同步入口（非协程上下文）使用 */
    fun collectAccountDataEnvelopeBlocking(context: Context): String =
        runBlocking(Dispatchers.IO) { collectAccountDataEnvelope(context) }

    data class AccountDataRestoreSummary(
        val restoredKeys: Int = 0,
        val skippedKeys: Int = 0
    ) {
        fun isValid() = restoredKeys > 0
    }

    /**
     * 恢复账户相关 DataStore 键（逐键调用 PreferencesRepository 的 save 方法，
     * 写回后依赖既有 Flow 自动刷新 UI，无需重启）。
     *  - *_data 键要求是合法 JSON（数组/对象），自动分账开关要求 "true"/"false"，
     *    已执行 ID 要求逗号分隔的数字串 —— 校验失败整键跳过并计入 skippedKeys；
     *  - 已执行收入 ID（只增不改的幂等守卫）采用并集合并，不存在批量覆盖入口；
     *  - 老备份/无关文件没有这些键时返回空摘要，不会误清任何本地数据。
     */
    suspend fun restoreAccountData(
        preferencesRepository: PreferencesRepository,
        accountData: Map<String, String>
    ): AccountDataRestoreSummary {
        if (accountData.isEmpty()) return AccountDataRestoreSummary()
        var restored = 0
        var skipped = 0

        suspend fun apply(key: String, value: String) {
            when (key) {
                PREF_ACCOUNTS_DATA -> if (isJsonArray(value)) {
                    preferencesRepository.saveAccountsData(value); restored++
                } else skipped++
                PREF_SAVING_PLANS_DATA -> if (isJsonArray(value)) {
                    preferencesRepository.saveSavingPlansData(value); restored++
                } else skipped++
                PREF_PAYCHECK_SCHEME_DATA -> if (isJsonObject(value)) {
                    preferencesRepository.savePaycheckSchemeData(value); restored++
                } else skipped++
                PREF_PAYCHECK_LAST_RUN_DATA -> if (isJsonObject(value)) {
                    preferencesRepository.savePaycheckLastRunData(value); restored++
                } else skipped++
                PREF_PAYCHECK_RUN_HISTORY_DATA -> if (isJsonArray(value)) {
                    preferencesRepository.savePaycheckRunHistory(value); restored++
                } else skipped++
                PREF_RECONCILE_CYCLE_DATA -> if (isJsonObject(value)) {
                    preferencesRepository.saveReconcileCycleData(value); restored++
                } else skipped++
                PREF_PAYCHECK_AUTO_ENABLED -> when (value) {
                    "true" -> { preferencesRepository.setPaycheckAutoEnabled(true); restored++ }
                    "false" -> { preferencesRepository.setPaycheckAutoEnabled(false); restored++ }
                    else -> skipped++
                }
                PREF_PAYCHECK_AUTO_APPLIED_IDS -> {
                    val ids = value.split(",").map { it.trim() }
                    if (ids.all { it.isNotEmpty() && it.toLongOrNull() != null }) {
                        val currentIds = parseStoredAppliedIds(preferencesRepository)
                        val toAdd = ids.mapNotNull { it.toLongOrNull() }.filter { it !in currentIds }
                        toAdd.sorted().forEach { preferencesRepository.addPaycheckAutoAppliedId(it) }
                        restored++
                    } else {
                        skipped++
                    }
                }
                PREF_THEME_MODE -> runCatching {
                    preferencesRepository.setThemeMode(com.yuanman.app.data.model.ThemeMode.valueOf(value))
                    restored++
                }.onFailure { skipped++ }
                PREF_MONTHLY_BUDGET -> {
                    value.toLongOrNull()?.let {
                        preferencesRepository.setMonthlyBudget(it)
                        restored++
                    } ?: skipped++
                }
                PREF_MONTHLY_BUDGETS -> {
                    preferencesRepository.saveMonthlyBudgetsRaw(value)
                    restored++
                }
                PREF_DEFAULT_PAYMENT_METHOD -> {
                    preferencesRepository.setDefaultPaymentMethod(value)
                    restored++
                }
                PREF_DEFAULT_EXPENSE_ACCOUNT -> {
                    preferencesRepository.setDefaultExpenseAccount(value)
                    restored++
                }
                PREF_DEFAULT_INCOME_ACCOUNT -> {
                    preferencesRepository.setDefaultIncomeAccount(value)
                    restored++
                }
                PREF_QUICK_ENTRY_ENABLED -> {
                    preferencesRepository.setQuickEntryEnabled(value == "true")
                    restored++
                }
                PREF_PRIVACY_MODE -> {
                    preferencesRepository.setPrivacyMode(value == "true")
                    restored++
                }
                PREF_HAPTIC_FEEDBACK_ENABLED -> {
                    preferencesRepository.setHapticFeedbackEnabled(value == "true")
                    restored++
                }
                PREF_CUSTOM_TAGS -> {
                    val tags = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    preferencesRepository.setCustomTags(tags)
                    restored++
                }
                else -> skipped++ // 未知键跳过（向前兼容）
            }
        }

        ACCOUNT_PREF_KEYS.forEach { key ->
            val value = accountData[key] ?: return@forEach
            if (value.isNotBlank()) {
                try {
                    apply(key, value)
                } catch (e: Exception) {
                    skipped++
                }
            }
        }
        return AccountDataRestoreSummary(restoredKeys = restored, skippedKeys = skipped)
    }

    private fun isJsonArray(text: String): Boolean = try {
        JSONArray(text); true
    } catch (e: Exception) {
        false
    }

    private fun isJsonObject(text: String): Boolean = try {
        JSONObject(text); true
    } catch (e: Exception) {
        false
    }

    private suspend fun parseStoredAppliedIds(preferencesRepository: PreferencesRepository): Set<Long> =
        preferencesRepository.paycheckAutoAppliedIds.first()
            .orEmpty().split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()

    private fun JSONObject.optionalLong(name: String): Long? =
        if (has(name) && !isNull(name)) getLong(name) else null

    fun shareBackupFile(context: Context, categories: List<CategoryEntity>, records: List<RecordWithCategory>) {
        try {
            val jsonContent = exportToJsonString(categories, records)
            val exportDir = File(context.cacheDir, "backups").apply {
                if (!exists()) mkdirs()
            }

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val file = File(exportDir, "yuanman_backup_$timeStamp.json")

            FileOutputStream(file).use { fos ->
                fos.write(jsonContent.toByteArray(Charsets.UTF_8))
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "沅满记账数据全量备份 ($timeStamp)")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "备份并分享数据文件")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
