package com.yuanman.app.data.local

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DatabaseBackupManagerTest {
    @Test
    fun defaultCategoriesDoNotCountAsHistoricalData() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(context.cacheDir, "backup-empty-check-test.db")
        databaseFile.delete()
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()

        try {
            SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
                database.execSQL(
                    """
                    CREATE TABLE categories (
                        id INTEGER PRIMARY KEY,
                        isDefault INTEGER NOT NULL,
                        deletedAt INTEGER
                    )
                    """.trimIndent()
                )
                database.execSQL("INSERT INTO categories(id, isDefault, deletedAt) VALUES (1, 1, NULL)")
            }

            check(invokeIsDatabaseEmpty(databaseFile)) {
                "仅含系统默认分类的数据库不应阻止历史备份恢复"
            }

            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL("INSERT INTO categories(id, isDefault, deletedAt) VALUES (2, 0, NULL)")
            }
            check(!invokeIsDatabaseEmpty(databaseFile)) {
                "含自定义分类的数据库必须被视为已有用户数据"
            }

            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL("DELETE FROM categories WHERE id = 2")
                database.execSQL("UPDATE categories SET deletedAt = 1234 WHERE id = 1")
            }
            check(!invokeIsDatabaseEmpty(databaseFile)) {
                "含分类删除状态的数据库必须被视为已有用户数据"
            }

            SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { database ->
                database.execSQL("CREATE TABLE records (id INTEGER PRIMARY KEY)")
                database.execSQL("INSERT INTO records(id) VALUES (1)")
            }
            check(!invokeIsDatabaseEmpty(databaseFile)) {
                "含历史流水的数据库必须被视为已有用户数据"
            }
        } finally {
            databaseFile.delete()
            File(databaseFile.path + "-wal").delete()
            File(databaseFile.path + "-shm").delete()
        }
    }

    private fun invokeIsDatabaseEmpty(databaseFile: File): Boolean {
        val method = DatabaseBackupManager::class.java
            .getDeclaredMethod("isDatabaseEmpty", File::class.java)
            .apply { isAccessible = true }
        return method.invoke(DatabaseBackupManager, databaseFile) as Boolean
    }
}
