package com.yuanman.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
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
        CategoryRepository(
            database.categoryDao(),
            database.recordDao(),
            database.syncDao(),
            database.quickEntryLearningDao()
        )
    }

    val recordRepository: RecordRepository by lazy {
        RecordRepository(database.recordDao(), this)
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncManager: com.yuanman.app.sync.FamilySyncManager by lazy {
        com.yuanman.app.sync.FamilySyncManager(
            context = this,
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
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            private var startedActivityCount = 0

            override fun onActivityStarted(activity: Activity) {
                startedActivityCount += 1
            }

            override fun onActivityStopped(activity: Activity) {
                startedActivityCount = (startedActivityCount - 1).coerceAtLeast(0)
                if (startedActivityCount == 0) {
                    // 进入后台时立刻更新公共快照，确保随后卸载不会丢掉最近一次记账。
                    appScope.launch(Dispatchers.IO) {
                        com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(this@YuanmanApplication)
                    }
                }
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        appScope.launch {
            // 1. 灾难自愈检测与恢复（若版本升级或意外出现表数据丢失，自动从安全备份恢复）
            // 必须在 Room 初始化前执行，避免在打开的数据库连接上覆盖文件。
            com.yuanman.app.data.local.DatabaseBackupManager.checkAndAutoRecover(this@YuanmanApplication)

            // 2. 确保默认分类与体系正常
            categoryRepository.ensureDefaultCategories()
            // 3. 将系统预置词库同步到分类学习页（幂等，重置时仍保留）
            categoryRepository.ensureDefaultQuickEntryLearning()
            // 4. 为升级前已有账单补建分类学习样本（幂等，不重复累计）
            categoryRepository.backfillQuickEntryLearning()

            // 5. 运行中周期性安全快照备份
            com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(this@YuanmanApplication)
        }
    }

    companion object {
        lateinit var instance: YuanmanApplication
            private set
    }
}
