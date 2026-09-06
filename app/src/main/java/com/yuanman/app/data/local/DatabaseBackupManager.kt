package com.yuanman.app.data.local

import android.content.ContentUris
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private const val SHARED_BACKUP_TEMP_PREFIX = ".yuanman_database_backup_"
    private const val SHARED_BACKUP_DIR_NAME = "Yuanman"
    private const val SHARED_BACKUP_MIME_TYPE = "application/vnd.sqlite3"
    private const val MIN_VALID_DB_SIZE = 4096L
    private val backupLock = Any()

    // DataStore 偏好文件(个人习惯: 预算、默认方式、主题、隐私、自定义标签等)
    private const val PREFERENCES_FILE_NAME = "yuanman_preferences.preferences_pb"
    private const val PREFERENCES_DATASTORE_REL_PATH = "datastore/$PREFERENCES_FILE_NAME"
    private const val SHARED_PREFERENCES_NAME = PREFERENCES_FILE_NAME
    private const val SHARED_PREFERENCES_FILE_PREFIX = "yuanman_preferences"
    private const val SHARED_PREFERENCES_TEMP_PREFIX = ".yuanman_preferences_"
    private const val SHARED_PREFERENCES_MIME_TYPE = "application/octet-stream"
    private const val MAX_PREFERENCES_SIZE = 2 * 1024 * 1024L

    // 数据变更后的防抖自动备份(避免每次记账都立刻全量写 Documents)
    private const val DEBOUNCE_BACKUP_DELAY_MS = 4_000L
    private val debounceHandler = Handler(Looper.getMainLooper())
    private val backupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile
    private var pendingBackupRunnable: Runnable? = null
    @Volatile
    private var attachedAppContext: Context? = null

    /** 应用启动时注册进程级上下文，供无 Context 的数据层(仓库)触发防抖备份。 */
    fun attach(context: Context) {
        attachedAppContext = context.applicationContext
    }

    /**
     * 是否已授予"所有文件访问"权限(Android 11+ 设置页授予)。
     * 卸载重装后系统可能清除 MediaStore 索引行(实体文件仍留在公共目录)，
     * 持有该权限时可直接按路径扫描 文档/Yuanman 下的实体文件完成恢复。
     */
    fun hasAllFilesAccess(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

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

                // 应用私有目录会随卸载删除，额外写入公共 Documents，供重装后自动恢复。
                if (!createSharedBackup(context, dbFile)) {
                    Log.w(TAG, "Shared uninstall-safe backup was not updated.")
                }

                // 个人习惯偏好(DataStore)同样发布到公共 Documents，重装后随数据库一并还原。
                val preferencesFile = getLocalPreferencesFile(context)
                if (preferencesFile.isFile) {
                    if (!createSharedPreferencesBackup(context, preferencesFile)) {
                        Log.w(TAG, "Shared preferences snapshot was not updated.")
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
                // 已使用过的应用即使账单为空，也不能被旧快照“复活”。
                if (!isDatabaseInitialized(appContext)) {
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

    /**
     * 从公共 Documents/Yuanman 目录直接扫描并恢复最近的数据库与偏好快照。
     * 不依赖 MediaStore(卸载重装后其索引行可能已被系统清除)，但需要"所有文件访问"权限。
     * 调用方应在恢复成功后重启应用(数据库文件在被占用时执行替换)。
     */
    suspend fun restoreFromDocuments(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        synchronized(backupLock) {
            try {
                val appContext = context.applicationContext
                if (!hasAllFilesAccess(appContext)) {
                    return@withContext Result.failure(Exception("未授予文件访问权限"))
                }

                val dbFile = appContext.getDatabasePath(DB_NAME)
                val dbCandidate = listPublicSnapshotFiles(appContext, SHARED_BACKUP_FILE_PREFIX)
                    .firstOrNull { it.name.endsWith(".db") && isDatabaseUsable(it) }
                    ?: return@withContext Result.failure(Exception("文档/Yuanman 目录中未找到有效的数据库备份"))

                val tempFile = File(dbFile.parentFile, "$DB_NAME.recovery.tmp")
                tempFile.delete()
                copyFile(dbCandidate, tempFile)
                deleteSqliteSidecars(tempFile)
                if (!isDatabaseUsable(tempFile) || !replaceDatabaseFile(tempFile, dbFile)) {
                    deleteSqliteSidecars(tempFile)
                    tempFile.delete()
                    return@withContext Result.failure(Exception("替换数据库文件失败，请稍后重试"))
                }
                tempFile.delete()

                // 偏好快照与数据库一并还原；不存在时(如早期版本)不视为失败。
                val prefsSnapshot = listPublicSnapshotFiles(appContext, SHARED_PREFERENCES_FILE_PREFIX)
                    .firstOrNull { isValidPreferencesFile(it) }
                if (prefsSnapshot != null) {
                    restorePreferencesLocked(appContext, prefsSnapshot)
                }

                Log.i(TAG, "Restored database and preferences from public Documents directory: ${dbCandidate.absolutePath}")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Restore from Documents failed: ${e.message}", e)
                Result.failure(e)
            }
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

        findSharedBackup(context)?.let(candidates::add)
        legacySharedFilePath(SHARED_BACKUP_NAME)?.takeIf { it.isFile }?.let(candidates::add)

        // 卸载重装后 MediaStore 索引行可能已被系统清除；持有"所有文件访问"权限时
        // 直接扫描公共目录实体文件兜底，不依赖 MediaStore。
        listPublicSnapshotFiles(context, SHARED_BACKUP_FILE_PREFIX)
            .filter { it.name.endsWith(".db") }
            .forEach(candidates::add)

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
     * 持有"所有文件访问"权限时，直接扫描公共 Documents/Yuanman 目录下的实体文件
     * (MediaStore 索引行被卸载清除后的兜底数据源)。按修改时间倒序返回，供选择最新快照。
     */
    private fun listPublicSnapshotFiles(context: Context, filePrefix: String): List<File> {
        if (!hasAllFilesAccess(context) || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            SHARED_BACKUP_DIR_NAME
        )
        val files = try {
            dir.listFiles { file -> file.isFile && file.name.startsWith(filePrefix) }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to list public snapshot dir $dir: ${e.message}")
            null
        } ?: return emptyList()
        return files.sortedByDescending { it.lastModified() }
    }

    /**
     * 将快照写入卸载后仍保留的共享存储。Android 10+ 使用 MediaStore，避免申请广泛存储权限。
     */
    private fun createSharedBackup(context: Context, source: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreSnapshot(
                context = context,
                source = source,
                displayName = SHARED_BACKUP_NAME,
                filePrefix = SHARED_BACKUP_FILE_PREFIX,
                tempPrefix = SHARED_BACKUP_TEMP_PREFIX,
                mimeType = SHARED_BACKUP_MIME_TYPE
            )
        } else {
            val destination = legacySharedFilePath(SHARED_BACKUP_NAME) ?: return false
            createVerifiedBackup(source, destination)
        }
    }

    /** 个人习惯偏好(DataStore)快照，写入同一公共目录，重装后随数据库还原。 */
    private fun createSharedPreferencesBackup(context: Context, source: File): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            createMediaStoreSnapshot(
                context = context,
                source = source,
                displayName = SHARED_PREFERENCES_NAME,
                filePrefix = SHARED_PREFERENCES_FILE_PREFIX,
                tempPrefix = SHARED_PREFERENCES_TEMP_PREFIX,
                mimeType = SHARED_PREFERENCES_MIME_TYPE
            )
        } else {
            val destination = legacySharedFilePath(SHARED_PREFERENCES_NAME) ?: return false
            copyFileReplacing(source, destination)
        }
    }

    private fun createMediaStoreSnapshot(
        context: Context,
        source: File,
        displayName: String,
        filePrefix: String,
        tempPrefix: String,
        mimeType: String
    ): Boolean {
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val pendingName = "$tempPrefix${System.currentTimeMillis()}.tmp"
        val pendingValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pendingName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, sharedBackupRelativePath())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val pendingUri = resolver.insert(collection, pendingValues) ?: return false

        return try {
            copyFileToUri(context, source, pendingUri)
            val publishedValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            check(resolver.update(pendingUri, publishedValues, null, null) == 1)

            // 新快照已发布后再清理旧版本，避免更新过程中没有可恢复快照。
            val staleUris = querySharedFileUris(context, filePrefix).filterNot { it == pendingUri }
            Log.d(TAG, "Cleanup for $displayName: total stale rows = ${staleUris.size}")
            staleUris.forEach { uri -> deleteMediaStoreRowAndFile(resolver, uri) }

            // 某些系统会因同名文件自动追加 "(1)"，旧文件清理后尝试恢复为稳定文件名。
            resolver.update(
                pendingUri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                },
                null,
                null
            )
            true
        } catch (e: Exception) {
            resolver.delete(pendingUri, null, null)
            Log.e(TAG, "MediaStore backup failed for $displayName: ${e.message}", e)
            false
        }
    }

    /**
     * 删除过期快照：先移除 MediaStore 行，再尝试直接删除实体文件。
     * 部分厂商只移除索引而不回收实体，快照文件属于本应用 uid，可直接删除兜底。
     */
    private fun deleteMediaStoreRowAndFile(resolver: android.content.ContentResolver, uri: Uri) {
        var dataPath: String? = null
        try {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    dataPath = cursor.getString(0)
                }
            }
            val deleted = resolver.delete(uri, null, null)
            Log.d(TAG, "Removed stale media row $uri deleted=$deleted")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove stale media row $uri: ${e.message}", e)
        }
        val path = dataPath ?: return
        try {
            val file = File(path)
            if (file.exists() && !file.delete()) {
                Log.w(TAG, "Stale media file still exists after delete: $path")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to delete stale media file $path: ${e.message}", e)
        }
    }

    private fun querySharedFileUris(context: Context, filePrefix: String): List<Uri> {
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
        val selectionArgs = arrayOf("$filePrefix%")

        return try {
            buildList {
                resolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    "_id DESC"
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                    val pendingIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_PENDING)
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(nameIndex)
                        val relativePath = cursor.getString(pathIndex)
                        val isPending = cursor.getInt(pendingIndex) != 0
                        if (name.startsWith(filePrefix) &&
                            relativePath == sharedBackupRelativePath() &&
                            !isPending
                        ) {
                            add(ContentUris.withAppendedId(collection, cursor.getLong(idIndex)))
                        } else {
                            Log.d(TAG, "Filtered out media row: name=$name path=$relativePath pending=$isPending")
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
        // 按 MediaStore 行号倒序取最近发布的行；不依赖 DISPLAY_NAME(厂商可能追加 (N) 后缀)。
        val backupUri = querySharedFileUris(context, SHARED_BACKUP_FILE_PREFIX).firstOrNull() ?: return null
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

    /**
     * 从公共 Documents 读取偏好快照(个人习惯)到缓存文件。
     * Android 9- 读取旧版直写路径；Android 10+ 走 MediaStore；
     * MediaStore 行被卸载清除时，持有"所有文件访问"权限则直接读取公共实体文件兜底。
     */
    private fun findSharedPreferences(context: Context): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return legacySharedFilePath(SHARED_PREFERENCES_NAME)?.takeIf { it.isFile }
        }
        val prefsUri = querySharedFileUris(context, SHARED_PREFERENCES_FILE_PREFIX)
            .firstOrNull()
        if (prefsUri != null) {
            val cachedFile = File(context.cacheDir, "$SHARED_PREFERENCES_NAME.recovery")
            cachedFile.delete()

            return try {
                copyUriToFile(context, prefsUri, cachedFile)
                if (isValidPreferencesFile(cachedFile)) {
                    cachedFile
                } else {
                    cachedFile.delete()
                    null
                }
            } catch (e: Exception) {
                cachedFile.delete()
                Log.w(TAG, "Unable to read shared preferences backup: ${e.message}")
                null
            }
        }
        // 卸载重装后 MediaStore 索引行可能已被清除，直接读取公共目录实体(需权限)。
        return listPublicSnapshotFiles(context, SHARED_PREFERENCES_FILE_PREFIX)
            .firstOrNull { isValidPreferencesFile(it) }
    }

    private fun getLocalPreferencesFile(context: Context): File =
        File(context.filesDir, PREFERENCES_DATASTORE_REL_PATH)

    private fun isValidPreferencesFile(file: File): Boolean =
        file.isFile && file.length() > 0L && file.length() <= MAX_PREFERENCES_SIZE

    /**
     * 将偏好快照原子替换到本地 DataStore 文件。
     * 必须在任何 DataStore 读取发生前调用(Application.onCreate 同步阶段)。
     */
    private fun restorePreferencesLocked(context: Context, snapshot: File): Boolean {
        val target = getLocalPreferencesFile(context)
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temp = File(parent, "$PREFERENCES_FILE_NAME.recovery.tmp")
        return try {
            temp.delete()
            copyFile(snapshot, temp)
            if (!isValidPreferencesFile(temp) || !replaceFile(temp, target)) {
                temp.delete()
                false
            } else {
                temp.delete()
                Log.i(TAG, "Shared preferences snapshot restored to ${target.absolutePath}")
                true
            }
        } catch (e: Exception) {
            temp.delete()
            Log.e(TAG, "Preferences restore failed: ${e.message}", e)
            false
        }
    }

    /**
     * 重装后首次冷启动(尚未初始化数据库)时，同步还原 Documents 中的偏好快照。
     * 必须在任何 DataStore 读取之前执行，因此由 Application.onCreate 同步调用。
     */
    fun restorePreferencesForReinstallNow(context: Context) {
        val appContext = context.applicationContext
        synchronized(backupLock) {
            if (isDatabaseInitialized(appContext)) return
            val snapshot = findSharedPreferences(appContext) ?: return
            restorePreferencesLocked(appContext, snapshot)
        }
    }

    /** 从用户选择的文件手动还原偏好(DataStore 文件)。 */
    suspend fun restorePreferencesFromUri(context: Context, uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        try {
            val tempFile = File(appContext.cacheDir, "temp_restore_preferences.pb")
            tempFile.delete()
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext Result.failure(Exception("无法读取备份文件内容"))

            if (!isValidPreferencesFile(tempFile)) {
                tempFile.delete()
                return@withContext Result.failure(Exception("偏好备份文件无效或为空"))
            }
            val restored = synchronized(backupLock) {
                restorePreferencesLocked(appContext, tempFile)
            }
            tempFile.delete()
            if (restored) Result.success(Unit) else Result.failure(Exception("替换偏好文件失败"))
        } catch (e: Exception) {
            Log.e(TAG, "Restore preferences from URI failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 注册防抖自动备份：数据(账单/分类/个人习惯)变更后调用，
     * 多次连续写入只合并为一次快照，4 秒后执行。
     */
    fun scheduleAutoBackupSoon() {
        val appContext = attachedAppContext ?: return
        scheduleAutoBackupSoon(appContext)
    }

    /** 带进程级上下文的防抖自动备份。 */
    fun scheduleAutoBackupSoon(context: Context) {
        val appContext = context.applicationContext
        attachedAppContext = appContext
        synchronized(backupLock) {
            pendingBackupRunnable?.let { debounceHandler.removeCallbacks(it) }
            val runnable = Runnable {
                pendingBackupRunnable = null
                backupScope.launch {
                    try {
                        autoBackup(appContext)
                    } catch (e: Exception) {
                        Log.e(TAG, "Scheduled auto backup failed: ${e.message}", e)
                    }
                }
            }
            pendingBackupRunnable = runnable
            debounceHandler.postDelayed(runnable, DEBOUNCE_BACKUP_DELAY_MS)
        }
    }

    /**
     * 手动备份到公共 Documents(数据库 + 个人习惯偏好)。
     * 返回是否成功。位置为 Documents/Yuanman/yuanman_database_backup.db
     * 与 Documents/Yuanman/yuanman_preferences.preferences_pb。
     */
    suspend fun createManualBackup(context: Context): Boolean = withContext(Dispatchers.IO) {
        synchronized(backupLock) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (!dbFile.exists() || dbFile.length() == 0L) return@withContext false
                if (!checkpointDatabase(dbFile) || !isDatabaseUsable(dbFile)) return@withContext false

                var dbOk = createSharedBackup(context, dbFile)
                val preferencesFile = getLocalPreferencesFile(context)
                if (preferencesFile.isFile && !createSharedPreferencesBackup(context, preferencesFile)) {
                    Log.w(TAG, "Manual preferences snapshot failed.")
                }
                if (dbOk) {
                    Log.i(TAG, "Manual uninstall-safe backup completed.")
                }
                dbOk
            } catch (e: Exception) {
                Log.e(TAG, "Manual backup failed: ${e.message}", e)
                false
            }
        }
    }

    private fun legacySharedFilePath(fileName: String): File? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ||
            Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED
        ) {
            return null
        }
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "$SHARED_BACKUP_DIR_NAME/$fileName"
        )
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

    private fun copyFileReplacing(source: File, destination: File): Boolean {
        val parent = destination.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temp = File(parent, ".${destination.name}.recovery.tmp")
        return try {
            temp.delete()
            copyFile(source, temp)
            val replaced = replaceFile(temp, destination)
            if (!replaced) temp.delete()
            replaced
        } catch (e: Exception) {
            temp.delete()
            Log.e(TAG, "Replacing file failed for ${destination.name}: ${e.message}", e)
            false
        }
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
