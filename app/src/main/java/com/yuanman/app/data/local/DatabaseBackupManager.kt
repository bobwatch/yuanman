package com.yuanman.app.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DatabaseBackupManager {
    private const val TAG = "DatabaseBackupManager"
    private const val DB_NAME = "yuanman_database.db"

    /**
     * 在应用启动或升级前自动执行安全快照备份
     */
    fun autoBackup(context: Context) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) {
                Log.d(TAG, "Database file does not exist or is empty, skip auto backup.")
                return
            }

            // 1. 内部备份目录 (files/backups)
            val internalBackupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
            val internalLatestBackup = File(internalBackupDir, "auto_backup_latest.db")
            copyFile(dbFile, internalLatestBackup)

            // 复制 WAL 与 SHM 辅助文件（如果存在）
            val walFile = File(dbFile.parentFile, "$DB_NAME-wal")
            if (walFile.exists()) copyFile(walFile, File(internalBackupDir, "auto_backup_latest.db-wal"))
            val shmFile = File(dbFile.parentFile, "$DB_NAME-shm")
            if (shmFile.exists()) copyFile(shmFile, File(internalBackupDir, "auto_backup_latest.db-shm"))

            // 2. 外部安全备份目录 (跨升级与重装最稳妥的沙盒备份)
            val externalDir = context.getExternalFilesDir("backups")
            if (externalDir != null) {
                val externalLatest = File(externalDir, "yuanman_autobackup.db")
                copyFile(dbFile, externalLatest)

                // 轮转按日备份，最多保留最近 5 份
                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
                val datedBackup = File(externalDir, "yuanman_backup_$dateStr.db")
                if (!datedBackup.exists()) {
                    copyFile(dbFile, datedBackup)
                }

                // 清理超过 7 天的历史备份
                val backupFiles = externalDir.listFiles { _, name -> name.startsWith("yuanman_backup_") && name.endsWith(".db") }
                if (backupFiles != null && backupFiles.size > 7) {
                    backupFiles.sortedBy { it.lastModified() }
                        .take(backupFiles.size - 7)
                        .forEach { it.delete() }
                }
            }

            Log.i(TAG, "Database auto-backup completed successfully. Size: ${dbFile.length()} bytes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform auto backup: ${e.message}", e)
        }
    }

    /**
     * 灾难自愈检测：如果数据库表记录为空，但存在有效的历史备份，自动从最新备份恢复
     */
    suspend fun checkAndAutoRecover(context: Context, database: AppDatabase): Boolean = withContext(Dispatchers.IO) {
        try {
            val recordCount = database.recordDao().getTotalRecordCountDirect()
            if (recordCount > 0) {
                return@withContext false // 数据正常，无需恢复
            }

            // 寻找最新的有效备份文件
            val candidateBackups = mutableListOf<File>()

            val internalBackup = File(context.filesDir, "backups/auto_backup_latest.db")
            if (internalBackup.exists() && internalBackup.length() > 0L) {
                candidateBackups.add(internalBackup)
            }

            val externalDir = context.getExternalFilesDir("backups")
            if (externalDir != null && externalDir.exists()) {
                val externalFiles = externalDir.listFiles { _, name -> name.endsWith(".db") }
                if (externalFiles != null) {
                    candidateBackups.addAll(externalFiles.filter { it.length() > 0L })
                }
            }

            val bestBackup = candidateBackups.maxByOrNull { it.lastModified() }
            if (bestBackup != null && bestBackup.length() > 4096L) {
                Log.w(TAG, "Disaster recovery triggered! Restoring from backup: ${bestBackup.absolutePath} (${bestBackup.length()} bytes)")
                
                // 执行恢复
                val dbFile = context.getDatabasePath(DB_NAME)
                copyFile(bestBackup, dbFile)
                
                // 删除可能存在冲突的临时 WAL 文件
                File(dbFile.parentFile, "$DB_NAME-wal").delete()
                File(dbFile.parentFile, "$DB_NAME-shm").delete()
                
                Log.i(TAG, "Auto-recovery completed successfully!")
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-recovery failed: ${e.message}", e)
        }
        return@withContext false
    }

    /**
     * 手动创建外部备份文件
     */
    suspend fun createManualBackup(context: Context): File? = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists() || dbFile.length() == 0L) return@withContext null

            val externalDir = context.getExternalFilesDir("backups") ?: context.filesDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
            val backupFile = File(externalDir, "yuanman_backup_$timestamp.db")

            copyFile(dbFile, backupFile)
            backupFile
        } catch (e: Exception) {
            Log.e(TAG, "Manual backup failed: ${e.message}", e)
            null
        }
    }

    /**
     * 从外部文件或 URI 恢复数据库
     */
    suspend fun restoreFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dbFile = context.getDatabasePath(DB_NAME)
            val tempFile = File(context.cacheDir, "temp_restore.db")

            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("无法读取备份文件内容"))

            if (tempFile.length() < 1024L) {
                tempFile.delete()
                return@withContext Result.failure(Exception("备份文件无效或已损坏"))
            }

            // 替换主数据库文件
            copyFile(tempFile, dbFile)
            tempFile.delete()

            // 清理 WAL / SHM
            File(dbFile.parentFile, "$DB_NAME-wal").delete()
            File(dbFile.parentFile, "$DB_NAME-shm").delete()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore from URI failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.channel.transferTo(0, source.length(), output.channel)
            }
        }
    }
}
