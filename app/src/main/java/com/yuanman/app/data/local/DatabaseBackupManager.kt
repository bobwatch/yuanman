package com.yuanman.app.data.local

import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    private const val SHARED_BACKUP_NAME = "yuanman_database_backup.db"
    private const val SHARED_BACKUP_FILE_PREFIX = "yuanman_database_backup"
    private const val BACKUP_STATE_PREFERENCES = "database_backup_state"
    private const val DATABASE_INITIALIZED_KEY = "database_initialized"
    private const val UNINSTALL_SAFE_BACKUP_ENABLED_KEY = "uninstall_safe_backup_enabled"
    private const val SHARED_BACKUP_TEMP_PREFIX = ".yuanman_database_backup_"
    private const val SHARED_BACKUP_DIR_NAME = "Yuanman"
    private const val SHARED_BACKUP_MIME_TYPE = "application/vnd.sqlite3"
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

                // 公共 Documents 对其他文件管理工具可见，只在用户明确授权后写入。
                if (isUninstallSafeBackupEnabled(context)) {
                    if (!createSharedBackup(context, dbFile)) {
                        Log.w(TAG, "Shared uninstall-safe backup was not updated.")
                    }
                } else {
                    // v0.0.3 曾默认创建公共副本；升级后按新的默认隐私策略主动收口。
                    removeSharedBackups(context)
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
        checkAndAutoRecoverNow(context)
    }

    /**
     * 在 Room 创建前同步检查，避免异步 Application 初始化与首帧打开数据库产生竞态。
     */
    fun checkAndAutoRecoverNow(context: Context): Boolean {
        return synchronized(backupLock) {
            val appContext = context.applicationContext
            val dbFile = appContext.getDatabasePath(DB_NAME)
            if (isDatabaseUsable(dbFile)) {
                // 全新安装时 Android 可能会先恢复一个合法但为空的 Room 数据库。
                // 只有在本应用尚未完成过初始化时，才允许公共快照覆盖这个空库；
                // 已有历史数据的升级包即使尚未写入初始化标记，也绝不能被旧快照覆盖。
                if (!isDatabaseInitialized(appContext) && isDatabaseEmpty(dbFile)) {
                    val restored = restoreLatestBackupLocked(appContext)
                    Log.i(TAG, "Initial database recovery attempted. Restored: $restored")
                    restored
                } else {
                    false
                }
            } else {
                restoreLatestBackupLocked(appContext)
            }
        }
    }

    fun markDatabaseInitialized(context: Context) {
        context.applicationContext
            .getSharedPreferences(BACKUP_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DATABASE_INITIALIZED_KEY, true)
            .apply()
    }

    fun isUninstallSafeBackupEnabled(context: Context): Boolean =
        context.applicationContext
            .getSharedPreferences(BACKUP_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(UNINSTALL_SAFE_BACKUP_ENABLED_KEY, false)

    fun setUninstallSafeBackupEnabled(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(BACKUP_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(UNINSTALL_SAFE_BACKUP_ENABLED_KEY, enabled)
            .apply()
        if (enabled) autoBackup(context) else removeSharedBackups(context)
    }

    /** Called only after the user explicitly disables public uninstall-safe backups. */
    private fun removeSharedBackups(context: Context) {
        runCatching {
            querySharedBackupUris(context).forEach { context.contentResolver.delete(it, null, null) }
            legacySharedBackupFile()?.takeIf { it.isFile }?.delete()
        }.onFailure { Log.w(TAG, "Unable to remove shared backups: ${it.message}") }
    }

    private fun isDatabaseInitialized(context: Context): Boolean {
        return context.getSharedPreferences(BACKUP_STATE_PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(DATABASE_INITIALIZED_KEY, false)
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
        val validBackups = findValidBackups(context)
        // 优先选择仍包含用户数据的快照，避免最新一次空库快照把较早的历史快照“顶掉”。
        val bestBackup = validBackups
            .filterNot(::isDatabaseEmpty)
            .maxByOrNull { it.lastModified() }
            ?: validBackups.maxByOrNull { it.lastModified() }
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

        findSharedBackup(context)?.let(candidates::add)
        findLegacySharedBackup()?.let(candidates::add)

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

    /**
     * 将快照写入卸载后仍保留的共享存储。Android 10+ 使用 MediaStore，避免申请广泛存储权限。
     */
    private fun createSharedBackup(context: Context, source: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreBackup(context, source)
        } else {
            val destination = legacySharedBackupFile() ?: return false
            createVerifiedBackup(source, destination)
        }
    }

    private fun createMediaStoreBackup(context: Context, source: File): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val pendingName = "$SHARED_BACKUP_TEMP_PREFIX${System.currentTimeMillis()}.tmp"
        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pendingName)
            put(MediaStore.MediaColumns.MIME_TYPE, SHARED_BACKUP_MIME_TYPE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, sharedBackupRelativePath())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val pendingUri = resolver.insert(collection, pendingValues) ?: return false

        return try {
            copyFileToUri(context, source, pendingUri)
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, SHARED_BACKUP_NAME)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            check(resolver.update(pendingUri, publishedValues, null, null) == 1)

            // 新快照已发布后再清理旧版本，避免更新过程中没有可恢复快照。
            querySharedBackupUris(context)
                .filterNot { it == pendingUri }
                .forEach { resolver.delete(it, null, null) }

            // 某些系统会因同名文件自动追加 "(1)"，旧文件清理后尝试恢复为稳定文件名。
            resolver.update(
                pendingUri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, SHARED_BACKUP_NAME)
                },
                null,
                null
            )
            true
        } catch (e: Exception) {
            resolver.delete(pendingUri, null, null)
            Log.e(TAG, "MediaStore backup failed: ${e.message}", e)
            false
        }
    }

    private fun querySharedBackupUris(context: Context): List<Uri> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()

        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING
        )
        // 部分厂商 MediaProvider 对 RELATIVE_PATH 和 IS_PENDING 放在 selection 中的处理不一致。
        // 先按名称前缀查询，再在游标中校验目录和发布状态，兼容性更好。
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("$SHARED_BACKUP_FILE_PREFIX%")

        return try {
            buildList {
                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "CASE WHEN ${MediaStore.MediaColumns.DISPLAY_NAME} = '$SHARED_BACKUP_NAME' THEN 0 ELSE 1 END, " +
                        "${MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    val pendingIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        val relativePath = cursor.getString(pathIndex)
                        val isPending = cursor.getInt(pendingIndex) != 0
                        if (name.startsWith(SHARED_BACKUP_FILE_PREFIX) &&
                            relativePath == sharedBackupRelativePath() &&
                            !isPending
                        ) {
                            add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to query shared backup: ${e.message}", e)
            emptyList()
        }
    }

    private fun findSharedBackup(context: Context): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val backupUri = querySharedBackupUris(context).firstOrNull() ?: return null
        val cachedFile = File(context.cacheDir, "$SHARED_BACKUP_NAME.recovery")
        deleteSqliteSidecars(cachedFile)
        cachedFile.delete()

        return try {
            copyUriToFile(context, backupUri, cachedFile)
            if (isDatabaseUsable(cachedFile)) {
                deleteSqliteSidecars(cachedFile)
                cachedFile
            } else {
                deleteSqliteSidecars(cachedFile)
                cachedFile.delete()
                null
            }
        } catch (e: Exception) {
            deleteSqliteSidecars(cachedFile)
            cachedFile.delete()
            Log.w(TAG, "Unable to read shared backup: ${e.message}")
            null
        }
    }

    private fun legacySharedBackupFile(): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED
        ) {
            return null
        }
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$SHARED_BACKUP_DIR_NAME/$SHARED_BACKUP_NAME"
        )
    }

    private fun findLegacySharedBackup(): File? {
        return legacySharedBackupFile()?.takeIf { it.isFile }
    }

    private fun sharedBackupRelativePath(): String {
        return "${Environment.DIRECTORY_DOCUMENTS}/$SHARED_BACKUP_DIR_NAME/"
    }

    private fun copyFileToUri(context: Context, source: File, destination: Uri) {
        context.contentResolver.openOutputStream(destination, "w")?.use { output ->
            FileInputStream(source).use { input ->
                input.copyTo(output)
            }
            output.flush()
        } ?: throw IllegalStateException("Unable to open shared backup for writing")
    }

    private fun copyUriToFile(context: Context, source: Uri, destination: File) {
        context.contentResolver.openInputStream(source)?.use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
                output.channel.force(true)
            }
        } ?: throw IllegalStateException("Unable to open shared backup for reading")
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

    /**
     * 只把真正的新空库视为可被旧快照替换的恢复目标。
     *
     * 旧版本升级时 SharedPreferences 可能没有 DATABASE_INITIALIZED_KEY；如果只看这个标记，
     * 一个合法的、有历史流水的旧数据库就会被较旧备份覆盖，表现为“升级后历史数据清空”。
     */
    private fun isDatabaseEmpty(dbFile: File): Boolean {
        return try {
            SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            ).use { database ->
                val hasUserCategories = if (hasTable(database, "categories")) {
                    val categoryWhere = when {
                        hasColumn(database, "categories", "isDefault") &&
                            hasColumn(database, "categories", "deletedAt") ->
                            "isDefault != 1 OR deletedAt IS NOT NULL"
                        hasColumn(database, "categories", "isDefault") ->
                            "isDefault != 1"
                        else -> null
                    }
                    countRows(database, "categories", categoryWhere) > 0L
                } else {
                    false
                }

                hasUserCategories || listOf(
                    "records",
                    "quick_entry_learning",
                    "accounts",
                    "account_snapshots"
                ).filter { hasTable(database, it) }
                    .any { countRows(database, it) > 0L }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to inspect database contents before recovery: ${e.message}")
            false
        }
    }

    private fun countRows(
        database: SQLiteDatabase,
        tableName: String,
        whereClause: String? = null
    ): Long {
        val query = buildString {
            append("SELECT COUNT(*) FROM ")
            append(tableName)
            if (!whereClause.isNullOrBlank()) {
                append(" WHERE ")
                append(whereClause)
            }
        }
        return database.rawQuery(query, null).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun hasColumn(database: SQLiteDatabase, tableName: String, columnName: String): Boolean {
        return database.rawQuery("PRAGMA table_info($tableName)", null).use { cursor ->
            val nameIndex = cursor.getColumnIndex("name")
            nameIndex >= 0 && generateSequence {
                if (cursor.moveToNext()) cursor.getString(nameIndex) else null
            }.any { it == columnName }
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
