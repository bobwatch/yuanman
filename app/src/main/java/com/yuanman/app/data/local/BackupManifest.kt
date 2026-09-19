package com.yuanman.app.data.local

import org.json.JSONObject

/**
 * 手动完整备份的完整性清单：记录每个快照文件的 SHA-256，恢复前逐项比对。
 * 老备份没有清单时仍按原有 SQLite 校验恢复；一旦清单存在，摘要不符即拒绝写入，
 * 避免"文件损坏或被人改过一个金额"的快照覆盖现有账本。
 */
data class BackupManifest(
    val createdAt: Long,
    /** 逻辑分组（database / preferences / accounts） → 小写十六进制 SHA-256。 */
    val digests: Map<String, String>
) {
    companion object {
        const val FILE_NAME = "yuanman_backup_manifest.json"
        const val FILE_PREFIX = "yuanman_backup_manifest"
        const val FORMAT_VERSION = 1

        const val GROUP_DATABASE = "database"
        const val GROUP_PREFERENCES = "preferences"
        const val GROUP_ACCOUNTS = "accounts"

        /** 文件名（可能被系统追加 "(1)" 等后缀）→ 逻辑分组。 */
        fun groupOf(fileName: String): String? = when {
            fileName.startsWith("yuanman_database_backup") && fileName.endsWith(".db") -> GROUP_DATABASE
            fileName.startsWith("yuanman_preferences") && fileName.endsWith(".preferences_pb") -> GROUP_PREFERENCES
            fileName.startsWith("yuanman_accounts_data") && fileName.endsWith(".json") -> GROUP_ACCOUNTS
            else -> null
        }

        fun encode(createdAt: Long, digests: Map<String, String>): String = JSONObject()
            .put("format", FORMAT_VERSION)
            .put("createdAt", createdAt)
            .put("files", JSONObject(digests.toMap()))
            .toString()

        /** 解析清单；格式不认识或没有任何有效摘要时返回 null，调用方按"无清单"处理。 */
        fun decode(text: String): BackupManifest? = try {
            val json = JSONObject(text)
            if (json.optInt("format", 0) != FORMAT_VERSION) {
                null
            } else {
                val files = json.optJSONObject("files")
                val digests = files?.keys()?.asSequence()
                    ?.mapNotNull { key ->
                        val value = files.optString(key)
                        if (value.length == 64) key to value.lowercase() else null
                    }
                    ?.toMap()
                    .orEmpty()
                if (digests.isEmpty()) null else BackupManifest(json.optLong("createdAt", 0L), digests)
            }
        } catch (e: Exception) {
            null
        }
    }
}
