package com.yuanman.app

import android.app.Application
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.repository.CategoryRepository
import com.yuanman.app.data.repository.PreferencesRepository
import com.yuanman.app.data.repository.RecordRepository

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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: YuanmanApplication
            private set
    }
}
