package com.yuanman.app.utils

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object JsonBackupUtils {

    data class BackupData(
        val version: String = "1.0",
        val exportedAt: Long = System.currentTimeMillis(),
        val categories: List<CategoryEntity>,
        val records: List<RecordEntity>
    )

    fun exportToJsonString(categories: List<CategoryEntity>, records: List<RecordWithCategory>): String {
        val root = JSONObject()
        root.put("version", "1.0")
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
            }
            catArray.put(obj)
        }
        root.put("categories", catArray)

        // 导出账单记录
        val recArray = JSONArray()
        records.forEach { rwc ->
            val r = rwc.record
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
            }
            recArray.put(obj)
        }
        root.put("records", recArray)

        return root.toString(2)
    }

    fun parseFromJsonString(jsonString: String): BackupData {
        val root = JSONObject(jsonString)
        val categories = mutableListOf<CategoryEntity>()
        val records = mutableListOf<RecordEntity>()

        if (root.has("categories")) {
            val catArray = root.getJSONArray("categories")
            for (i in 0 until catArray.length()) {
                val obj = catArray.getJSONObject(i)
                categories.add(
                    CategoryEntity(
                        id = obj.optLong("id", 0L),
                        name = obj.getString("name"),
                        type = obj.getString("type"),
                        iconName = obj.optString("iconName", "other"),
                        colorHex = obj.optLong("colorHex", 0xFF607D8BL),
                        isDefault = obj.optBoolean("isDefault", false),
                        sortOrder = obj.optInt("sortOrder", 0),
                        tags = obj.optString("tags", ""),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
        }

        if (root.has("records")) {
            val recArray = root.getJSONArray("records")
            for (i in 0 until recArray.length()) {
                val obj = recArray.getJSONObject(i)
                records.add(
                    RecordEntity(
                        id = obj.optLong("id", 0L),
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
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }

        return BackupData(
            version = root.optString("version", "1.0"),
            exportedAt = root.optLong("exportedAt", System.currentTimeMillis()),
            categories = categories,
            records = records
        )
    }

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
