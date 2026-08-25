package com.yuanman.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuanman.app.data.local.dao.CategoryDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [CategoryEntity::class, RecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yuanman_database.db"
                )
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDefaultCategories(): List<CategoryEntity> {
            val expenseCategories = listOf(
                CategoryEntity(name = "餐饮", type = "EXPENSE", iconName = "food", colorHex = 0xFFFF5722L, isDefault = true, sortOrder = 1),
                CategoryEntity(name = "交通", type = "EXPENSE", iconName = "traffic", colorHex = 0xFF2196F3L, isDefault = true, sortOrder = 2),
                CategoryEntity(name = "购物", type = "EXPENSE", iconName = "shopping", colorHex = 0xFFFF9800L, isDefault = true, sortOrder = 3),
                CategoryEntity(name = "娱乐", type = "EXPENSE", iconName = "entertainment", colorHex = 0xFF9C27B0L, isDefault = true, sortOrder = 4),
                CategoryEntity(name = "住房", type = "EXPENSE", iconName = "housing", colorHex = 0xFF009688L, isDefault = true, sortOrder = 5),
                CategoryEntity(name = "医疗", type = "EXPENSE", iconName = "medical", colorHex = 0xFFE91E63L, isDefault = true, sortOrder = 6),
                CategoryEntity(name = "教育", type = "EXPENSE", iconName = "education", colorHex = 0xFF3F51B5L, isDefault = true, sortOrder = 7),
                CategoryEntity(name = "通讯", type = "EXPENSE", iconName = "communication", colorHex = 0xFF00BCD4L, isDefault = true, sortOrder = 8),
                CategoryEntity(name = "其他", type = "EXPENSE", iconName = "other", colorHex = 0xFF607D8BL, isDefault = true, sortOrder = 9)
            )

            val incomeCategories = listOf(
                CategoryEntity(name = "工资", type = "INCOME", iconName = "salary", colorHex = 0xFF4CAF50L, isDefault = true, sortOrder = 1),
                CategoryEntity(name = "奖金", type = "INCOME", iconName = "bonus", colorHex = 0xFFFFC107L, isDefault = true, sortOrder = 2),
                CategoryEntity(name = "理财", type = "INCOME", iconName = "finance", colorHex = 0xFF009688L, isDefault = true, sortOrder = 3),
                CategoryEntity(name = "兼职", type = "INCOME", iconName = "part_time", colorHex = 0xFF2196F3L, isDefault = true, sortOrder = 4),
                CategoryEntity(name = "其他", type = "INCOME", iconName = "other", colorHex = 0xFF607D8BL, isDefault = true, sortOrder = 5)
            )

            return expenseCategories + incomeCategories
        }

        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        database.categoryDao().insertCategories(getDefaultCategories())
                    }
                }
            }
        }
    }
}
