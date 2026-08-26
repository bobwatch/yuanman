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

import androidx.room.migration.Migration

@Database(
    entities = [CategoryEntity::class, RecordEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun recordDao(): RecordDao

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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // 启动或版本升级前自动对现有数据库进行安全快照备份
                DatabaseBackupManager.autoBackup(context.applicationContext)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "yuanman_database.db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .addMigrations(MIGRATION_2_3)
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getDefaultCategories(): List<CategoryEntity> {
            val expenseCategories = listOf(
                CategoryEntity(name = "餐饮美食", type = "EXPENSE", iconName = "food", colorHex = 0xFFFF5722L, tags = "早餐,午餐,晚餐,外卖,奶茶咖啡,水果零食,聚餐夜宵,买菜食材", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "交通出行", type = "EXPENSE", iconName = "traffic", colorHex = 0xFF2196F3L, tags = "地铁,公交,打车网约车,高铁火车,机票飞机,共享单车,过路费", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "爱车养车", type = "EXPENSE", iconName = "gas", colorHex = 0xFF0284C7L, tags = "加油充电,停车费,洗车美容,保养维修,车辆保险,车品配饰", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "充值缴费", type = "EXPENSE", iconName = "water_electric", colorHex = 0xFF00BCD4L, tags = "手机话费,宽带网费,水费,电费,燃气费,会员充值", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "服饰装扮", type = "EXPENSE", iconName = "clothes", colorHex = 0xFFE91E63L, tags = "衣服裤装,鞋靴箱包,内衣配饰,帽子手套,手表珠宝", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "家居家装", type = "EXPENSE", iconName = "furniture", colorHex = 0xFF795548L, tags = "家具家纺,五金建材,家装软装,日用百货,厨具餐具,收纳整理", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "数码电器", type = "EXPENSE", iconName = "digital", colorHex = 0xFF3F51B5L, tags = "手机数码,电脑办公,智能家电,数码配件,摄影器材,影音娱乐", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "运动户外", type = "EXPENSE", iconName = "fitness", colorHex = 0xFF4CAF50L, tags = "健身打卡,球类运动,户外露营,徒步骑行,运动装备,场馆门票", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "美容美发", type = "EXPENSE", iconName = "beauty", colorHex = 0xFF9C27B0L, tags = "美发理发,护肤美妆,美甲美睫,美容SPA,个护清洁", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "母婴亲子", type = "EXPENSE", iconName = "baby", colorHex = 0xFFFF7043L, tags = "奶粉辅食,纸尿裤,童装童鞋,玩具绘本,早教亲子,产检育儿", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "住房物业", type = "EXPENSE", iconName = "housing", colorHex = 0xFF009688L, tags = "房屋租金,房贷月供,物业管理费,房屋修缮,车位租金", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "酒店旅游", type = "EXPENSE", iconName = "travel", colorHex = 0xFF00ACC1L, tags = "酒店住宿,景点门票,跟团旅游,自由行,度假旅行,旅行装备", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "文化休闲", type = "EXPENSE", iconName = "movie", colorHex = 0xFF8E24AAL, tags = "电影院线,剧场演出,展览看展,书店阅读,桌游密室,游戏充值", isDefault = true, sortOrder = 13),
                CategoryEntity(name = "教育培训", type = "EXPENSE", iconName = "education", colorHex = 0xFF1E88E5L, tags = "学费教材,职业考证,技能培训,语言学习,在线课程,兴趣辅导", isDefault = true, sortOrder = 14),
                CategoryEntity(name = "医疗健康", type = "EXPENSE", iconName = "medical", colorHex = 0xFFD32F2FL, tags = "门诊挂号,西药中药,体检筛查,疫苗接种,牙科齿科,保健补剂", isDefault = true, sortOrder = 15),
                CategoryEntity(name = "生活服务", type = "EXPENSE", iconName = "cleaning", colorHex = 0xFF607D8BL, tags = "家政保洁,快递跑腿,干洗修鞋,搬家拉货,宠物服务,废品回收", isDefault = true, sortOrder = 16),
                CategoryEntity(name = "人情往来", type = "EXPENSE", iconName = "gift", colorHex = 0xFFFFB300L, tags = "礼金随礼,节日红包,长辈孝敬,晚辈压岁,请客送礼,聚会买单", isDefault = true, sortOrder = 17),
                CategoryEntity(name = "投资理财", type = "EXPENSE", iconName = "finance", colorHex = 0xFF2E7D32L, tags = "基金买入,股票证券,黄金理财,定投定存,商业保险,外汇理财", isDefault = true, sortOrder = 18)
            )

            val incomeCategories = listOf(
                CategoryEntity(name = "工资", type = "INCOME", iconName = "salary", colorHex = 0xFF2E7D32L, tags = "基本月薪,绩效奖金,岗位津贴,年终奖,全勤奖,餐补房补", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "红包转账", type = "INCOME", iconName = "card_gift", colorHex = 0xFFE53935L, tags = "微信红包,支付宝转账,节日长辈红包,生日礼物红包,亲友转账", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "理财收益", type = "INCOME", iconName = "finance", colorHex = 0xFFFFB300L, tags = "基金分红,股票盈利,银行利息,理财赎回结息,国债利息", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "兼职外快", type = "INCOME", iconName = "part_time", colorHex = 0xFF0284C7L, tags = "副业兼职,设计外包,投稿稿酬,劳务咨询,私域带货,闲置出清", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "退款", type = "INCOME", iconName = "refund", colorHex = 0xFF00897BL, tags = "网购退款,差价返还,押金退还,活动返现,退税入账", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "其他", type = "INCOME", iconName = "other", colorHex = 0xFF607D8BL, tags = "其他收入,中奖收入,意外所得,补贴津贴", isDefault = true, sortOrder = 6)
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
