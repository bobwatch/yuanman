package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.repository.PreferenceSnapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object JsonBackupUtils {
    const val CURRENT_VERSION = "3.0"
    private val SUPPORTED_VERSIONS = setOf("1.0", "2.0", CURRENT_VERSION)

    data class BackupData(
        val version: String,
        val exportedAt: Long,
        val categories: List<CategoryEntity>,
        val records: List<RecordEntity>,
        val preferences: PreferenceSnapshot? = null,
        val quickEntryLearning: List<QuickEntryLearningEntity> = emptyList(),
        val checksumVerified: Boolean = false
    )

    data class BackupPreview(
        val version: String,
        val exportedAt: Long,
        val categoryCount: Int,
        val recordCount: Int,
        val learningRuleCount: Int,
        val includesPreferences: Boolean,
        val checksumVerified: Boolean
    )

    fun exportToJsonString(categories: List<CategoryEntity>, records: List<RecordWithCategory>): String =
        exportEntitiesToJsonString(categories, records.map { it.record })

    fun exportEntitiesToJsonString(
        categories: List<CategoryEntity>,
        records: List<RecordEntity>,
        preferences: PreferenceSnapshot? = null,
        quickEntryLearning: List<QuickEntryLearningEntity> = emptyList()
    ): String {
        val payload = JSONObject().apply {
            put("version", CURRENT_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("appName", "Yuanman")
            put("categories", categoriesToJson(categories))
            put("records", recordsToJson(records))
            put("quickEntryLearning", learningToJson(quickEntryLearning))
            preferences?.let { put("preferences", preferencesToJson(it)) }
        }
        val checksum = sha256(payload.toString().toByteArray(Charsets.UTF_8))
        return JSONObject().apply {
            put("envelopeVersion", 1)
            put("checksumAlgorithm", "SHA-256")
            put("checksumSha256", checksum)
            put("payload", payload)
        }.toString(2)
    }

    fun preview(jsonString: String): BackupPreview {
        val data = parseFromJsonString(jsonString)
        return BackupPreview(
            version = data.version,
            exportedAt = data.exportedAt,
            categoryCount = data.categories.count { it.deletedAt == null },
            recordCount = data.records.count { it.deletedAt == null },
            learningRuleCount = data.quickEntryLearning.size,
            includesPreferences = data.preferences != null,
            checksumVerified = data.checksumVerified
        )
    }

    fun parseFromJsonString(jsonString: String, legacySourceId: String = "backup"): BackupData {
        require(jsonString.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "备份文件超过 100MB 限制" }
        val envelope = JSONObject(jsonString)
        val payload = envelope.optJSONObject("payload")
        val checksumVerified = if (payload != null) {
            val expected = envelope.optString("checksumSha256", "").lowercase(Locale.ROOT)
            require(expected.matches(Regex("[0-9a-f]{64}"))) { "备份校验值缺失或格式错误" }
            val actual = sha256(payload.toString().toByteArray(Charsets.UTF_8))
            require(MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())) { "备份校验失败，文件可能已损坏" }
            true
        } else false

        val root = payload ?: envelope
        val version = root.optString("version", "1.0")
        require(version in SUPPORTED_VERSIONS) { "不支持的备份版本：$version" }
        val categories = parseCategories(root.optJSONArray("categories") ?: JSONArray())
        val categoryIds = categories.map { it.id }.toSet()
        val records = parseRecords(root.optJSONArray("records") ?: JSONArray(), legacySourceId)
        require(records.all { it.amount > 0L }) { "备份中存在无效金额" }
        require(records.all { it.categoryId in categoryIds || it.categoryId == -1L }) { "备份中存在无法匹配分类的账单" }

        return BackupData(
            version = version,
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            categories = categories,
            records = records,
            preferences = root.optJSONObject("preferences")?.let(::parsePreferences),
            quickEntryLearning = parseLearning(root.optJSONArray("quickEntryLearning") ?: JSONArray()),
            checksumVerified = checksumVerified
        )
    }

    fun shareBackupFile(
        context: Context,
        categories: List<CategoryEntity>,
        records: List<RecordWithCategory>,
        preferences: PreferenceSnapshot? = null,
        quickEntryLearning: List<QuickEntryLearningEntity> = emptyList()
    ): Result<File> = createBackupFile(context, categories, records, preferences, quickEntryLearning)
        .onSuccess { shareBackupFile(context, it) }

    fun createBackupFile(
        context: Context,
        categories: List<CategoryEntity>,
        records: List<RecordWithCategory>,
        preferences: PreferenceSnapshot? = null,
        quickEntryLearning: List<QuickEntryLearningEntity> = emptyList()
    ): Result<File> = runCatching {
        val jsonContent = exportEntitiesToJsonString(categories, records.map { it.record }, preferences, quickEntryLearning)
        val exportDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(exportDir, "yuanman_backup_$timeStamp.json")
        FileOutputStream(file).use { it.write(jsonContent.toByteArray(Charsets.UTF_8)) }
        file
    }

    fun shareBackupFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "沅满记账完整备份")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        Intent.createChooser(shareIntent, "保存或分享完整备份").also {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(it)
        }
    }

    private fun categoriesToJson(categories: List<CategoryEntity>) = JSONArray().apply {
        categories.forEach { c -> put(JSONObject().apply {
            put("id", c.id); put("name", c.name); put("type", c.type); put("iconName", c.iconName)
            put("colorHex", c.colorHex); put("isDefault", c.isDefault); put("sortOrder", c.sortOrder); put("tags", c.tags)
            put("createdAt", c.createdAt); put("updatedAt", c.updatedAt); put("revision", c.revision); put("syncId", c.syncId)
            c.deletedAt?.let { put("deletedAt", it) }
        }) }
    }

    private fun recordsToJson(records: List<RecordEntity>) = JSONArray().apply {
        records.forEach { r -> put(JSONObject().apply {
            put("id", r.id); put("type", r.type); put("amount", r.amount); put("categoryId", r.categoryId)
            put("recordTime", r.recordTime); put("remark", r.remark); put("paymentMethod", r.paymentMethod)
            r.splitGroupId?.let { put("splitGroupId", it) }; r.splitIndex?.let { put("splitIndex", it) }
            r.splitTotal?.let { put("splitTotal", it) }; put("createdAt", r.createdAt); put("updatedAt", r.updatedAt)
            put("revision", r.revision); put("syncId", r.syncId); r.deletedAt?.let { put("deletedAt", it) }
        }) }
    }

    private fun learningToJson(rules: List<QuickEntryLearningEntity>) = JSONArray().apply {
        rules.forEach { rule -> put(JSONObject().apply {
            put("type", rule.type); put("phrase", rule.phrase); put("categorySyncId", rule.categorySyncId)
            put("sampleCount", rule.sampleCount); put("lastUsedAt", rule.lastUsedAt)
        }) }
    }

    private fun preferencesToJson(snapshot: PreferenceSnapshot) = JSONObject().apply {
        put("themeMode", snapshot.themeMode); put("defaultRecordType", snapshot.defaultRecordType)
        put("defaultPaymentMethod", snapshot.defaultPaymentMethod); put("legacyMonthlyBudget", snapshot.legacyMonthlyBudget)
        put("privacyMode", snapshot.privacyMode); put("hapticFeedbackEnabled", snapshot.hapticFeedbackEnabled)
        put("quickEntryEnabled", snapshot.quickEntryEnabled); put("monthlyBudgets", JSONObject(snapshot.monthlyBudgets))
        put("customTags", JSONArray(snapshot.customTags)); put("pinnedTemplateKeys", JSONArray(snapshot.pinnedTemplateKeys.toList()))
        put("hiddenTemplateKeys", JSONArray(snapshot.hiddenTemplateKeys.toList()))
    }

    private fun parseCategories(array: JSONArray): List<CategoryEntity> = buildList {
        repeat(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val name = obj.getString("name").trim()
            val type = obj.getString("type").trim().uppercase(Locale.ROOT)
            require(name.isNotBlank() && type in setOf("EXPENSE", "INCOME")) { "分类数据无效" }
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            add(CategoryEntity(
                id = obj.optLong("id", 0L), name = name, type = type,
                iconName = obj.optString("iconName", "other"), colorHex = obj.optLong("colorHex", 0xFF607D8BL),
                isDefault = obj.optBoolean("isDefault", false), sortOrder = obj.optInt("sortOrder", 0), tags = obj.optString("tags", ""),
                createdAt = createdAt, syncId = obj.optString("syncId").ifBlank { AppDatabase.stableCategorySyncId(type, name) },
                updatedAt = obj.optLong("updatedAt", createdAt), revision = obj.optLong("revision", 0L).coerceAtLeast(0L),
                deletedAt = obj.optionalLong("deletedAt")
            ))
        }
    }

    private fun parseRecords(array: JSONArray, legacySourceId: String): List<RecordEntity> = buildList {
        repeat(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val legacyId = obj.optLong("id", 0L)
            val legacySyncId = if (legacyId > 0L) "legacy-record:$legacySourceId:$legacyId" else "legacy-record:$legacySourceId:index:$index"
            val type = obj.getString("type").trim().uppercase(Locale.ROOT)
            require(type in setOf("EXPENSE", "INCOME")) { "账单类型无效" }
            add(RecordEntity(
                id = legacyId, type = type, amount = obj.getLong("amount"), categoryId = obj.optLong("categoryId", -1L),
                recordTime = obj.optLong("recordTime", System.currentTimeMillis()), remark = obj.optString("remark", ""),
                paymentMethod = obj.optString("paymentMethod", "现金"), splitGroupId = obj.optString("splitGroupId", "").ifBlank { null },
                splitIndex = if (obj.has("splitIndex")) obj.optInt("splitIndex") else null,
                splitTotal = if (obj.has("splitTotal")) obj.optInt("splitTotal") else null,
                createdAt = obj.optLong("createdAt", System.currentTimeMillis()), updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                revision = obj.optLong("revision", 0L).coerceAtLeast(0L), syncId = obj.optString("syncId").ifBlank { legacySyncId },
                deletedAt = obj.optionalLong("deletedAt")
            ))
        }
    }

    private fun parseLearning(array: JSONArray): List<QuickEntryLearningEntity> = buildList {
        repeat(array.length()) { index ->
            val obj = array.getJSONObject(index)
            val type = obj.optString("type").uppercase(Locale.ROOT)
            val phrase = obj.optString("phrase").trim()
            val categorySyncId = obj.optString("categorySyncId").trim()
            if (type in setOf("EXPENSE", "INCOME") && phrase.isNotBlank() && categorySyncId.isNotBlank()) {
                add(QuickEntryLearningEntity(type, phrase, categorySyncId, obj.optInt("sampleCount", 0).coerceAtLeast(0), obj.optLong("lastUsedAt", 0L)))
            }
        }
    }

    private fun parsePreferences(obj: JSONObject): PreferenceSnapshot {
        val budgetsObj = obj.optJSONObject("monthlyBudgets") ?: JSONObject()
        val budgets = buildMap {
            budgetsObj.keys().forEach { key ->
                if (key.matches(Regex("\\d{4}-\\d{2}"))) put(key, budgetsObj.optLong(key, 0L).coerceAtLeast(0L))
            }
        }
        return PreferenceSnapshot(
            themeMode = obj.optString("themeMode", "SYSTEM"), defaultRecordType = obj.optString("defaultRecordType", "EXPENSE"),
            defaultPaymentMethod = obj.optString("defaultPaymentMethod", ""), monthlyBudgets = budgets,
            legacyMonthlyBudget = obj.optLong("legacyMonthlyBudget", 0L).coerceAtLeast(0L), privacyMode = obj.optBoolean("privacyMode", false),
            hapticFeedbackEnabled = obj.optBoolean("hapticFeedbackEnabled", true), quickEntryEnabled = obj.optBoolean("quickEntryEnabled", true),
            customTags = obj.optJSONArray("customTags").toStringList(),
            pinnedTemplateKeys = obj.optJSONArray("pinnedTemplateKeys").toStringList().toSet(),
            hiddenTemplateKeys = obj.optJSONArray("hiddenTemplateKeys").toStringList().toSet()
        )
    }

    private fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else buildList {
        repeat(length()) { index -> optString(index).trim().takeIf { it.isNotEmpty() }?.let(::add) }
    }

    private fun JSONObject.optionalLong(name: String): Long? = if (has(name) && !isNull(name)) getLong(name) else null

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private const val MAX_BACKUP_BYTES = 100L * 1024L * 1024L
}
