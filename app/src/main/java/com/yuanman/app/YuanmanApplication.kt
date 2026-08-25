package com.yuanman.app

import android.app.Application
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: YuanmanApplication
            private set
    }
}
