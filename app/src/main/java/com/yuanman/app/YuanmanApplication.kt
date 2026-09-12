package com.yuanman.app

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.StartupSeedState
import com.yuanman.app.data.repository.AccountPresetBootstrap
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
            this,
            database.categoryDao(),
            database.recordDao(),
            database.syncDao(),
            database.quickEntryLearningDao(),
            database
        )
    }

    val recordRepository: RecordRepository by lazy {
        RecordRepository(
            recordDao = database.recordDao(),
            context = this,
            database = database,
            accountDao = database.accountDao()
        )
    }

    val accountRepository: com.yuanman.app.data.repository.AccountRepository by lazy {
        com.yuanman.app.data.repository.AccountRepository(
            database = database,
            accountDao = database.accountDao(),
            accountSnapshotDao = database.accountSnapshotDao(),
            recordDao = database.recordDao(),
            categoryDao = database.categoryDao(),
            context = this
        )
    }

    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(this)
    }

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val syncManager: com.yuanman.app.sync.FamilySyncManager by lazy {
        com.yuanman.app.sync.FamilySyncManager(
            context = this,
            categoryRepository = categoryRepository,
            accountRepository = accountRepository,
            database = database,
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
        // 重装或新机恢复场景：首启同步还原 Documents 中的个人习惯偏好快照。
        // 必须在任何 DataStore 读取(首屏组合收集)之前完成，否则进程内将一直读到旧值。
        com.yuanman.app.data.local.DatabaseBackupManager.restorePreferencesForReinstallNow(this)
        com.yuanman.app.data.local.DatabaseBackupManager.attach(this)
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

            // 2. 后台预热数据库与仓库：getDatabase 内含「自愈复查 + 启动安全快照备份 + 急切建库」，
            //    若拖到 MainActivity 首帧组合期才由主线程首次触发，整段全量备份会阻塞首帧；
            //    在这里尽早完成，让首帧组合对仓库 lazy 的触碰命中已建实例。
            database
            categoryRepository
            recordRepository

            // 3. 确保默认分类与体系正常
            categoryRepository.ensureDefaultCategories()

            // 4. 将系统预置词库同步到分类学习页（幂等，重置时仍保留）。词库随版本扩充、
            //    或分类数量变化（新增自定义分类）时才重跑，避免每次冷启动对每个分类的
            //    每个预置词做数千次存在性查询。
            val categories = categoryRepository.getAllCategoriesDirect()
            if (StartupSeedState.lastSeedVersion(this@YuanmanApplication) != BuildConfig.VERSION_CODE ||
                StartupSeedState.lastSeedCategoryCount(this@YuanmanApplication) != categories.size
            ) {
                categoryRepository.ensureDefaultQuickEntryLearning()
                StartupSeedState.markSeeded(
                    this@YuanmanApplication,
                    BuildConfig.VERSION_CODE,
                    categories.size
                )
            }

            // 5. 为升级前已有账单补建分类学习样本（幂等，不重复累计）：增量游标只处理
            //    上次启动之后新增/更新的账单，不再每次冷启动全表扫描历史流水。
            val newBackfillCursor = categoryRepository.backfillQuickEntryLearningIncremental(
                StartupSeedState.backfillCursor(this@YuanmanApplication)
            )
            StartupSeedState.markBackfillCursor(this@YuanmanApplication, newBackfillCursor)

            // 6. 老用户兼容（需求3）：存在历史流水但 accounts_data 为空时，自动落一套零余额常用账户
            //    （幂等：accounts_data 非空即不再写；全新安装无流水不预置；失败静默不崩溃）
            AccountPresetBootstrap.ensurePresetAccountsForLegacyUsers(
                recordDao = database.recordDao(),
                preferencesRepository = preferencesRepository
            )

            // 7. 运行中周期性安全快照备份
            com.yuanman.app.data.local.DatabaseBackupManager.autoBackup(this@YuanmanApplication)
        }
    }

    companion object {
        lateinit var instance: YuanmanApplication
            private set
    }
}
