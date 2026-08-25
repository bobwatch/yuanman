package com.yuanman.app.data.repository

import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.dao.CategoryDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.model.RecordType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao
) {
    fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    fun getCategoriesByType(type: RecordType): Flow<List<CategoryEntity>> {
        return categoryDao.getCategoriesByType(type.name)
    }

    suspend fun getCategoryById(id: Long): CategoryEntity? = withContext(Dispatchers.IO) {
        categoryDao.getCategoryById(id)
    }

    fun getCategoryByIdFlow(id: Long): Flow<CategoryEntity?> = categoryDao.getCategoryByIdFlow(id)

    suspend fun insertCategory(category: CategoryEntity): Long = withContext(Dispatchers.IO) {
        categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }

    suspend fun isCategoryUsed(categoryId: Long): Boolean = withContext(Dispatchers.IO) {
        recordDao.countRecordsByCategoryId(categoryId) > 0
    }

    suspend fun getCategoryUsageCount(categoryId: Long): Int = withContext(Dispatchers.IO) {
        recordDao.countRecordsByCategoryId(categoryId)
    }

    /**
     * 删除分类，如果已经被账单使用则抛出异常或返回失败
     */
    suspend fun deleteCategory(category: CategoryEntity): Result<Unit> = withContext(Dispatchers.IO) {
        val usageCount = recordDao.countRecordsByCategoryId(category.id)
        if (usageCount > 0) {
            Result.failure(IllegalStateException("该分类已被 $usageCount 条账单使用，无法直接删除"))
        } else {
            categoryDao.deleteCategory(category)
            Result.success(Unit)
        }
    }

    suspend fun resetDefaultCategories() = withContext(Dispatchers.IO) {
        val defaults = AppDatabase.getDefaultCategories()
        categoryDao.insertCategories(defaults)
    }
}
