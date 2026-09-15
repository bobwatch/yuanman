package com.yuanman.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.yuanman.app.data.local.dao.CategoryDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.dao.SyncDao
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.local.dao.QuickEntryLearningDao
import com.yuanman.app.data.model.IconPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

import androidx.room.migration.Migration

@Database(
    entities = [CategoryEntity::class, RecordEntity::class, QuickEntryLearningEntity::class],
    version = 6,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao
    abstract fun syncDao(): SyncDao
    abstract fun quickEntryLearningDao(): QuickEntryLearningDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE records ADD COLUMN splitGroupId TEXT")
                db.execSQL("ALTER TABLE records ADD COLUMN splitIndex INTEGER")
                db.execSQL("ALTER TABLE records ADD COLUMN splitTotal INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_records_splitGroupId ON records(splitGroupId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE categories ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE categories ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE categories ADD COLUMN deletedAt INTEGER")
                db.execSQL("UPDATE categories SET type = upper(trim(type)), name = trim(name), syncId = 'category:' || lower(hex(randomblob(16))), updatedAt = createdAt")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_syncId ON categories(syncId)")

                db.execSQL("ALTER TABLE records ADD COLUMN syncId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE records ADD COLUMN deletedAt INTEGER")
                db.execSQL("UPDATE records SET type = upper(trim(type)), syncId = 'record:' || lower(hex(randomblob(16)))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_records_syncId ON records(syncId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_entry_learning (
                        type TEXT NOT NULL,
                        phrase TEXT NOT NULL,
                        categorySyncId TEXT NOT NULL,
                        sampleCount INTEGER NOT NULL DEFAULT 1,
                        lastUsedAt INTEGER NOT NULL,
                        PRIMARY KEY(type, phrase, categorySyncId)
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_entry_learning_type_phrase ON quick_entry_learning(type, phrase)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_entry_learning_categorySyncId ON quick_entry_learning(categorySyncId)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 只刷新「默认分类」且颜色仍是旧默认值的行：用户手动改过色（或改过名）的分类一律不动。
                DEFAULT_CATEGORY_COLOR_REFRESH.forEach { refresh ->
                    refresh.legacyColors.forEach { legacyColor ->
                        db.execSQL(
                            "UPDATE categories SET colorHex = ? " +
                                "WHERE type = ? AND name = ? AND isDefault = 1 AND colorHex = ?",
                            arrayOf(refresh.newColor, refresh.type, refresh.name, legacyColor)
                        )
                    }
                }
            }
        }

        /** 默认分类配色刷新规则；[legacyColors] 覆盖 0.0.4 正式版色板与开发期过渡色板 */
        private class DefaultCategoryColorRefresh(
            val type: String,
            val name: String,
            val newColor: Long,
            val legacyColors: List<Long>
        )

        private val DEFAULT_CATEGORY_COLOR_REFRESH: List<DefaultCategoryColorRefresh> = listOf(
            DefaultCategoryColorRefresh("EXPENSE", "餐饮美食", IconPalette.FOOD, listOf(0xFFFF5722L, 0xFFFF6B4AL)),
            DefaultCategoryColorRefresh("EXPENSE", "交通出行", IconPalette.TRAFFIC, listOf(0xFF2196F3L, 0xFF2563EBL)),
            DefaultCategoryColorRefresh("EXPENSE", "爱车养车", IconPalette.CAR, listOf(0xFF0284C7L)),
            DefaultCategoryColorRefresh("EXPENSE", "充值缴费", IconPalette.UTILITY, listOf(0xFF00BCD4L, 0xFF06B6D4L)),
            DefaultCategoryColorRefresh("EXPENSE", "服饰装扮", IconPalette.APPAREL, listOf(0xFFE91E63L, 0xFFEC4899L)),
            DefaultCategoryColorRefresh("EXPENSE", "家居家装", IconPalette.HOME, listOf(0xFF795548L, 0xFF8D6E63L)),
            DefaultCategoryColorRefresh("EXPENSE", "数码电器", IconPalette.DIGITAL, listOf(0xFF3F51B5L, 0xFF4F46E5L)),
            DefaultCategoryColorRefresh("EXPENSE", "运动户外", IconPalette.SPORTS, listOf(0xFF4CAF50L, 0xFF10B981L)),
            DefaultCategoryColorRefresh("EXPENSE", "美容美发", IconPalette.BEAUTY, listOf(0xFF9C27B0L, 0xFF9333EAL)),
            DefaultCategoryColorRefresh("EXPENSE", "母婴亲子", IconPalette.BABY, listOf(0xFFFF7043L, 0xFFFB923CL)),
            DefaultCategoryColorRefresh("EXPENSE", "住房物业", IconPalette.HOUSING, listOf(0xFF009688L, 0xFF0D9488L)),
            DefaultCategoryColorRefresh("EXPENSE", "酒店旅游", IconPalette.TRAVEL, listOf(0xFF00ACC1L, 0xFF0891B2L)),
            DefaultCategoryColorRefresh("EXPENSE", "文化休闲", IconPalette.LEISURE, listOf(0xFF8E24AAL, 0xFF7C3AEDL)),
            DefaultCategoryColorRefresh("EXPENSE", "教育培训", IconPalette.EDUCATION, listOf(0xFF1E88E5L, 0xFF3B82F6L)),
            DefaultCategoryColorRefresh("EXPENSE", "医疗健康", IconPalette.MEDICAL, listOf(0xFFD32F2FL, 0xFFEF4444L)),
            DefaultCategoryColorRefresh("EXPENSE", "生活服务", IconPalette.SERVICE, listOf(0xFF607D8BL, 0xFF64748BL)),
            DefaultCategoryColorRefresh("EXPENSE", "人情往来", IconPalette.SOCIAL, listOf(0xFFFFB300L, 0xFFF59E0BL)),
            DefaultCategoryColorRefresh("EXPENSE", "投资理财", IconPalette.INVEST, listOf(0xFF2E7D32L, 0xFF15803DL)),
            DefaultCategoryColorRefresh("INCOME", "工资", IconPalette.INVEST, listOf(0xFF2E7D32L, 0xFF15803DL)),
            DefaultCategoryColorRefresh("INCOME", "红包转账", IconPalette.MEDICAL, listOf(0xFFE53935L, 0xFFEF4444L)),
            DefaultCategoryColorRefresh("INCOME", "理财收益", IconPalette.SOCIAL, listOf(0xFFFFB300L, 0xFFF59E0BL)),
            DefaultCategoryColorRefresh("INCOME", "兼职外快", IconPalette.TRAVEL, listOf(0xFF0284C7L)),
            DefaultCategoryColorRefresh("INCOME", "退款", IconPalette.HOUSING, listOf(0xFF00897BL, 0xFF0D9488L)),
            DefaultCategoryColorRefresh("INCOME", "其他", IconPalette.SERVICE, listOf(0xFF607D8BL, 0xFF64748BL))
        )

        fun closeAndResetInstance() {
            synchronized(this) {
                INSTANCE?.let { db ->
                    try {
                        if (db.isOpen) {
                            db.close()
                        }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
                INSTANCE = null
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            val appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    // 首次安装/重装时必须先恢复公共快照，再让 Room 创建新库。
                    DatabaseBackupManager.checkAndAutoRecoverNow(appContext)
                    // 启动或版本升级前自动对现有数据库进行安全快照备份
                    DatabaseBackupManager.autoBackup(appContext)
                    openDatabaseWithRecovery(appContext).also {
                        DatabaseBackupManager.markDatabaseInitialized(appContext)
                    }
                }
            }
        }

        private fun openDatabaseWithRecovery(context: Context): AppDatabase {
            val instance = buildDatabase(context)
            // DatabaseCallback.onCreate 需要在数据库真正打开前拿到实例。
            INSTANCE = instance
            return try {
                // 提前打开，确保 Migration 错误能在这里被捕获，而不是异步启动后才崩溃。
                instance.openHelper.writableDatabase
                instance
            } catch (openError: Exception) {
                instance.close()
                INSTANCE = null

                if (!DatabaseBackupManager.restoreLatestBackup(context)) {
                    throw openError
                }

                val recovered = buildDatabase(context)
                INSTANCE = recovered
                try {
                    recovered.openHelper.writableDatabase
                    recovered
                } catch (retryError: Exception) {
                    recovered.close()
                    INSTANCE = null
                    throw openError
                }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "yuanman_database.db"
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .addMigrations(MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .addCallback(DatabaseCallback())
                .build()
        }

        fun getDefaultCategories(): List<CategoryEntity> {
            val expenseCategories = listOf(
                CategoryEntity(name = "餐饮美食", type = "EXPENSE", iconName = "food", colorHex = IconPalette.FOOD, tags = "早餐,午餐,晚餐,外卖,奶茶咖啡,水果零食,聚餐夜宵,买菜食材", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "交通出行", type = "EXPENSE", iconName = "traffic", colorHex = IconPalette.TRAFFIC, tags = "地铁,公交,打车网约车,高铁火车,机票飞机,共享单车,过路费", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "爱车养车", type = "EXPENSE", iconName = "gas", colorHex = IconPalette.CAR, tags = "加油充电,停车费,洗车美容,保养维修,车辆保险,车品配饰", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "充值缴费", type = "EXPENSE", iconName = "water_electric", colorHex = IconPalette.UTILITY, tags = "手机话费,宽带网费,水费,电费,燃气费,会员充值", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "服饰装扮", type = "EXPENSE", iconName = "clothes", colorHex = IconPalette.APPAREL, tags = "衣服裤装,鞋靴箱包,内衣配饰,帽子手套,手表珠宝", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "家居家装", type = "EXPENSE", iconName = "furniture", colorHex = IconPalette.HOME, tags = "家具家纺,五金建材,家装软装,日用百货,厨具餐具,收纳整理", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "数码电器", type = "EXPENSE", iconName = "digital", colorHex = IconPalette.DIGITAL, tags = "手机数码,电脑办公,智能家电,数码配件,摄影器材,影音娱乐", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "运动户外", type = "EXPENSE", iconName = "fitness", colorHex = IconPalette.SPORTS, tags = "健身打卡,球类运动,户外露营,徒步骑行,运动装备,场馆门票", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "美容美发", type = "EXPENSE", iconName = "beauty", colorHex = IconPalette.BEAUTY, tags = "美发理发,护肤美妆,美甲美睫,美容SPA,个护清洁", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "母婴亲子", type = "EXPENSE", iconName = "baby", colorHex = IconPalette.BABY, tags = "奶粉辅食,纸尿裤,童装童鞋,玩具绘本,早教亲子,产检育儿", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "住房物业", type = "EXPENSE", iconName = "housing", colorHex = IconPalette.HOUSING, tags = "房屋租金,房贷月供,物业管理费,房屋修缮,车位租金", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "酒店旅游", type = "EXPENSE", iconName = "travel", colorHex = IconPalette.TRAVEL, tags = "酒店住宿,景点门票,跟团旅游,自由行,度假旅行,旅行装备", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "文化休闲", type = "EXPENSE", iconName = "movie", colorHex = IconPalette.LEISURE, tags = "电影院线,剧场演出,展览看展,书店阅读,桌游密室,游戏充值", isDefault = true, sortOrder = 13),
                CategoryEntity(name = "教育培训", type = "EXPENSE", iconName = "education", colorHex = IconPalette.EDUCATION, tags = "学费教材,职业考证,技能培训,语言学习,在线课程,兴趣辅导", isDefault = true, sortOrder = 14),
                CategoryEntity(name = "医疗健康", type = "EXPENSE", iconName = "medical", colorHex = IconPalette.MEDICAL, tags = "门诊挂号,西药中药,体检筛查,疫苗接种,牙科齿科,保健补剂", isDefault = true, sortOrder = 15),
                CategoryEntity(name = "生活服务", type = "EXPENSE", iconName = "cleaning", colorHex = IconPalette.SERVICE, tags = "家政保洁,快递跑腿,干洗修鞋,搬家拉货,宠物服务,废品回收", isDefault = true, sortOrder = 16),
                CategoryEntity(name = "人情往来", type = "EXPENSE", iconName = "gift", colorHex = IconPalette.SOCIAL, tags = "礼金随礼,节日红包,长辈孝敬,晚辈压岁,请客送礼,聚会买单", isDefault = true, sortOrder = 17),
                CategoryEntity(name = "投资理财", type = "EXPENSE", iconName = "finance", colorHex = IconPalette.INVEST, tags = "基金买入,股票证券,黄金理财,定投定存,商业保险,外汇理财", isDefault = true, sortOrder = 18)
            )

            // 收入与支出分处两页，可复用同族语义色，不构成同屏撞色
            val incomeCategories = listOf(
                CategoryEntity(name = "工资", type = "INCOME", iconName = "salary", colorHex = IconPalette.INVEST, tags = "基本月薪,绩效奖金,岗位津贴,年终奖,全勤奖,餐补房补", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "红包转账", type = "INCOME", iconName = "card_gift", colorHex = IconPalette.MEDICAL, tags = "微信红包,支付宝转账,节日长辈红包,生日礼物红包,亲友转账", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "理财收益", type = "INCOME", iconName = "finance", colorHex = IconPalette.SOCIAL, tags = "基金分红,股票盈利,银行利息,理财赎回结息,国债利息", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "兼职外快", type = "INCOME", iconName = "part_time", colorHex = IconPalette.TRAVEL, tags = "副业兼职,设计外包,投稿稿酬,劳务咨询,私域带货,闲置出清", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "退款", type = "INCOME", iconName = "refund", colorHex = IconPalette.HOUSING, tags = "网购退款,差价返还,押金退还,活动返现,退税入账", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "其他", type = "INCOME", iconName = "other", colorHex = IconPalette.SERVICE, tags = "其他收入,中奖收入,意外所得,补贴津贴", isDefault = true, sortOrder = 6)
            )

            return (expenseCategories + incomeCategories).map { category ->
                category.copy(syncId = stableCategorySyncId(category.type, category.name))
            }
        }

        fun stableCategorySyncId(type: String, name: String): String =
            "category:${type.trim().uppercase(Locale.ROOT)}:${name.trim()}"

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
