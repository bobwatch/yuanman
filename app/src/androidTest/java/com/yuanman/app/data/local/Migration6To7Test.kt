package com.yuanman.app.data.local

import android.content.ContentValues
import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration6To7Test {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
        emptyList<AutoMigrationSpec>(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate6To7PreservesHistoricalCategoriesRecordsAndLearning() {
        helper.createDatabase(TEST_DB, 6).use { db ->
            db.insert("categories", 0, ContentValues().apply {
                put("id", 11L)
                put("name", "旧分类")
                put("type", "EXPENSE")
                put("iconName", "food")
                put("colorHex", 0xFFFF5722L)
                put("isDefault", 0)
                put("sortOrder", 99)
                put("tags", "历史")
                put("createdAt", 1_700_000_000_000L)
                put("syncId", "category:legacy")
                put("updatedAt", 1_700_000_000_100L)
                put("revision", 3)
            })
            db.insert("records", 0, ContentValues().apply {
                put("id", 21L)
                put("type", "EXPENSE")
                put("amount", 12_345L)
                put("categoryId", 11L)
                put("recordTime", 1_700_000_001_000L)
                put("remark", "升级前流水")
                put("paymentMethod", "旧账户")
                put("createdAt", 1_700_000_001_000L)
                put("updatedAt", 1_700_000_001_100L)
                put("revision", 4)
                put("syncId", "record:legacy")
            })
            db.insert("quick_entry_learning", 0, ContentValues().apply {
                put("type", "EXPENSE")
                put("phrase", "早餐")
                put("categorySyncId", "category:legacy")
                put("sampleCount", 2)
                put("lastUsedAt", 1_700_000_002_000L)
            })
        }

        helper.runMigrationsAndValidate(TEST_DB, 7, true, AppDatabase.MIGRATION_6_7).use { db ->
            db.query("SELECT name, tags, revision FROM categories WHERE id = 11").use { cursor ->
                check(cursor.moveToFirst()) { "历史分类在升级后丢失" }
                check(cursor.getString(0) == "旧分类")
                check(cursor.getString(1) == "历史")
                check(cursor.getInt(2) == 3)
            }
            db.query("SELECT amount, remark, accountId, targetAccountId, isAdjustment FROM records WHERE id = 21").use { cursor ->
                check(cursor.moveToFirst()) { "历史流水在升级后丢失" }
                check(cursor.getLong(0) == 12_345L)
                check(cursor.getString(1) == "升级前流水")
                check(cursor.isNull(2) && cursor.isNull(3) && cursor.getInt(4) == 0)
            }
            db.query("SELECT sampleCount FROM quick_entry_learning WHERE phrase = '早餐'").use { cursor ->
                check(cursor.moveToFirst()) { "历史学习数据在升级后丢失" }
                check(cursor.getInt(0) == 2)
            }
        }
    }

    private companion object { const val TEST_DB = "migration-6-7-history-test" }
}
