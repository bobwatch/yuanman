package com.yuanman.app

import android.app.Application
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YuanmanApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }

    val categoryRepository: CategoryRepository by lazy {
        CategoryRepository(database.categoryDao(), database.recordDao())
    }

    val recordRepository: RecordRepository by lazy {
        RecordRepository(database.recordDao())
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncManager: com.yuanman.app.sync.FamilySyncManager by lazy {
        com.yuanman.app.sync.FamilySyncManager(
            context = this,
            recordRepository = recordRepository,
            categoryRepository = categoryRepository,
            scope = appScope
        )
    }

    val updateManager: com.yuanman.app.utils.UpdateManager by lazy {
        com.yuanman.app.utils.UpdateManager(
            context = this,
            scope = appScope
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        appScope.launch {
            // 1. 灾难自愈检测与恢复（若版本升级或意外出现表数据丢失，自动从安全备份恢复）
            // 必须在 Room 初始化前执行，避免在打开的数据库连接上覆盖文件。
            com.yuanman.app.data.local.DatabaseBackupManager.checkAndAutoRecover(this@YuanmanApplication)

            // 2. 确保默认分类与体系正常
            categoryRepository.ensureDefaultCategories()

            // 3. 运行中周期性安全快照备份
            com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(this@YuanmanApplication)
        }
    }

    companion object {
        lateinit var instance: YuanmanApplication
            private set
    }
}
