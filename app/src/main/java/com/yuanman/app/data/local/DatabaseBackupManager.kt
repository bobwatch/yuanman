package com.yuanman.app.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
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
    private const val MIN_VALID_DB_SIZE = 4096L
    private val backupLock = Any()

    /**
     * 在应用启动或升级前自动执行安全快照备份
     */
    fun autoBackup(context: Context) {
        synchronized(backupLock) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) {
                    Log.d(TAG, "Database file does not exist or is empty, skip auto backup.")
                    return
                }

                // Room 默认可能使用 WAL。先做 checkpoint，避免只复制主库而漏掉 WAL 中的已提交数据。
                if (!checkpointDatabase(dbFile) || !isDatabaseUsable(dbFile)) {
                    Log.w(TAG, "Database is busy or invalid, keep the previous backup instead of overwriting it.")
                    return
                }

                // 1. 内部备份目录（会随 Android Auto Backup 一起备份）
                val internalBackupDir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
                val internalLatestBackup = File(internalBackupDir, "auto_backup_latest.db")
                createVerifiedBackup(dbFile, internalLatestBackup)

                // 2. 外部安全备份目录（用于本机意外损坏时的额外恢复点）
                val externalDir = context.getExternalFilesDir("backups")
                if (externalDir != null) {
                    val externalLatest = File(externalDir, "yuanman_autobackup.db")
                    createVerifiedBackup(dbFile, externalLatest)

                    // 轮转按日备份，最多保留最近 7 份
                    val dateStr = SimpleDateFormat("yyyyMMdd", Locale.CHINA).format(Date())
                    val datedBackup = File(externalDir, "yuanman_backup_$dateStr.db")
                    if (!datedBackup.exists()) {
                        createVerifiedBackup(dbFile, datedBackup)
                    }

                    val backupFiles = externalDir.listFiles { _, name ->
                        name.startsWith("yuanman_backup_") && name.endsWith(".db")
                    }
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
    }

    /**
     * 灾难自愈检测：仅当数据库无法通过完整性检查时，才从有效快照恢复。
     * 合法的空账单数据库不会被误判为损坏。
     */
    suspend fun checkAndAutoRecover(context: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(backupLock) {
            val appContext = context.applicationContext
            val dbFile = appContext.getDatabasePath(DB_NAME)
            if (isDatabaseUsable(dbFile)) {
                false
            } else {
                restoreLatestBackupLocked(appContext)
            }
        }
    }

    /**
     * 在 Room 打开失败且连接已关闭后，尝试恢复最近的有效快照。
     */
    fun restoreLatestBackup(context: Context): Boolean {
        synchronized(backupLock) {
            return restoreLatestBackupLocked(context.applicationContext)
        }
    }

    /**
     * 手动创建外部备份文件
     */
    suspend fun createManualBackup(context: Context): File? = withContext(Dispatchers.IO) {
        synchronized(backupLock) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) return@withContext null
                if (!checkpointDatabase(dbFile) || !isDatabaseUsable(dbFile)) return@withContext null

                val externalDir = context.getExternalFilesDir("backups") ?: context.filesDir
                if (!externalDir.exists()) externalDir.mkdirs()
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(Date())
                val backupFile = File(externalDir, "yuanman_backup_$timestamp.db")

                if (createVerifiedBackup(dbFile, backupFile)) backupFile else null
            } catch (e: Exception) {
                Log.e(TAG, "Manual backup failed: ${e.message}", e)
                null
            }
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

            if (!isDatabaseUsable(tempFile)) {
                deleteSqliteSidecars(tempFile)
                tempFile.delete()
                return@withContext Result.failure(Exception("备份文件校验失败"))
            }

            // 调用方应在恢复前关闭 Room；这里使用可回滚的文件替换，避免留下半个数据库。
            if (!replaceDatabaseFile(tempFile, dbFile)) {
                deleteSqliteSidecars(tempFile)
                tempFile.delete()
                return@withContext Result.failure(Exception("替换数据库文件失败"))
            }
            deleteSqliteSidecars(tempFile)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Restore from URI failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun restoreLatestBackupLocked(context: Context): Boolean {
        val bestBackup = findValidBackups(context)
            .maxByOrNull { it.lastModified() }
            ?: return false

        val dbFile = context.getDatabasePath(DB_NAME)
        val tempFile = File(dbFile.parentFile, "$DB_NAME.recovery.tmp")
        return try {
            Log.w(TAG, "Restoring database from verified backup: ${bestBackup.absolutePath}")
            copyFile(bestBackup, tempFile)
            if (!isDatabaseUsable(tempFile) || !replaceDatabaseFile(tempFile, dbFile)) {
                deleteSqliteSidecars(tempFile)
                tempFile.delete()
                false
            } else {
                deleteSqliteSidecars(tempFile)
                Log.i(TAG, "Database recovery completed successfully.")
                true
            }
        } catch (e: Exception) {
            deleteSqliteSidecars(tempFile)
            tempFile.delete()
            Log.e(TAG, "Database recovery failed: ${e.message}", e)
            false
        }
    }

    private fun findValidBackups(context: Context): List<File> {
        val candidates = mutableListOf<File>()
        val internalBackup = File(context.filesDir, "backups/auto_backup_latest.db")
        if (internalBackup.exists()) candidates.add(internalBackup)

        context.getExternalFilesDir("backups")?.let { externalDir ->
            externalDir.listFiles { _, name -> name.endsWith(".db") }
                ?.let(candidates::addAll)
        }

        return candidates
            .distinctBy { it.absolutePath }
            .filter { candidate ->
                val usable = isDatabaseUsable(candidate)
                if (usable) deleteSqliteSidecars(candidate)
                usable
            }
    }

    private fun createVerifiedBackup(source: File, destination: File): Boolean {
        val parent = destination.parentFile ?: return false
        if (!parent.exists()) parent.mkdirs()
        val temp = File(parent, "${destination.name}.tmp")
        return try {
            temp.delete()
            copyFile(source, temp)
            if (!isDatabaseUsable(temp)) {
                deleteSqliteSidecars(temp)
                temp.delete()
                false
            } else {
                val replaced = replaceFile(temp, destination)
                deleteSqliteSidecars(temp)
                replaced
            }
        } catch (e: Exception) {
            deleteSqliteSidecars(temp)
            temp.delete()
            Log.e(TAG, "Verified backup failed for ${destination.name}: ${e.message}", e)
            false
        }
    }

    private fun checkpointDatabase(dbFile: File): Boolean {
        if (!dbFile.isFile) return false
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READWRITE
            ).use { database ->
                database.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getInt(0) == 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Database checkpoint skipped: ${e.message}")
            false
        }
    }

    private fun isDatabaseUsable(dbFile: File): Boolean {
        if (!dbFile.isFile || dbFile.length() < MIN_VALID_DB_SIZE) return false
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                val quickCheckOk = database.rawQuery("PRAGMA quick_check(1)", null).use { cursor ->
                    cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
                }
                quickCheckOk && hasTable(database, "categories") && hasTable(database, "records")
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasTable(database: SQLiteDatabase, tableName: String): Boolean {
        return database.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ? LIMIT 1",
            arrayOf(tableName)
        ).use { it.moveToFirst() }
    }

    private fun replaceDatabaseFile(temp: File, destination: File): Boolean {
        val parent = destination.parentFile ?: return false
        if (!parent.exists()) parent.mkdirs()

        val previous = File(parent, ".${destination.name}.previous")
        previous.delete()
        val hadDestination = destination.exists()
        if (hadDestination && !destination.renameTo(previous)) return false

        // 主库和 WAL/SHM 必须成套替换，避免旧 WAL 被新主库错误重放。
        File(parent, "$DB_NAME-wal").delete()
        File(parent, "$DB_NAME-shm").delete()

        if (!temp.renameTo(destination)) {
            if (hadDestination) previous.renameTo(destination)
            return false
        }
        previous.delete()
        return true
    }

    private fun replaceFile(temp: File, destination: File): Boolean {
        val parent = destination.parentFile ?: return false
        val previous = File(parent, ".${destination.name}.previous")
        previous.delete()
        val hadDestination = destination.exists()
        if (hadDestination && !destination.renameTo(previous)) return false
        if (!temp.renameTo(destination)) {
            if (hadDestination) previous.renameTo(destination)
            return false
        }
        previous.delete()
        return true
    }

    private fun deleteSqliteSidecars(databaseFile: File) {
        File(databaseFile.parentFile, "${databaseFile.name}-wal").delete()
        File(databaseFile.parentFile, "${databaseFile.name}-shm").delete()
    }

    private fun copyFile(source: File, dest: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(dest).use { output ->
                input.channel.transferTo(0, source.length(), output.channel)
                output.channel.force(true)
            }
        }
    }
}
