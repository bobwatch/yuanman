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
    
    suspend fun getAllCategoriesDirect(): List<CategoryEntity> = withContext(Dispatchers.IO) {
        categoryDao.getAllCategoriesList()
    }

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

    suspend fun insertCategories(categories: List<CategoryEntity>) = withContext(Dispatchers.IO) {
        categoryDao.insertCategories(categories)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(category)
    }

    suspend fun updateCategoryOrder(categoryIds: List<Long>) = withContext(Dispatchers.IO) {
        categoryIds.forEachIndexed { index, categoryId ->
            categoryDao.updateSortOrder(categoryId, index)
        }
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

    /**
     * 智能补全并清理合并冗余分类：
     * 1. 自动补充缺失的标准分类（如 餐饮美食、爱车养车、红包转账等）
     * 2. 自动迁移并合并旧版冗余分类（如 餐饮 -> 餐饮美食，交通 -> 交通出行，理财 -> 理财收益等）
     * 3. 彻底清除重复项
     */
    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        val defaults = AppDatabase.getDefaultCategories()
        var currentCategories = categoryDao.getAllCategoriesList()

        if (currentCategories.isEmpty()) {
            categoryDao.insertCategories(defaults)
            return@withContext
        }

        // 1. 补全缺失的标准分类
        val existingKeys = currentCategories.map { "${it.type}_${it.name}" }.toSet()
        val missing = defaults.filter { "${it.type}_${it.name}" !in existingKeys }
        if (missing.isNotEmpty()) {
            categoryDao.insertCategories(missing)
        }

        // 2. 重新加载最新分类列表
        currentCategories = categoryDao.getAllCategoriesList()

        // 3. 旧分类 -> 新标准分类映射表
        val oldToNewMap = mapOf(
            "EXPENSE_餐饮" to "EXPENSE_餐饮美食",
            "EXPENSE_交通" to "EXPENSE_交通出行",
            "EXPENSE_住房" to "EXPENSE_住房物业",
            "EXPENSE_医疗" to "EXPENSE_医疗健康",
            "EXPENSE_教育" to "EXPENSE_教育培训",
            "EXPENSE_通讯" to "EXPENSE_充值缴费",
            "EXPENSE_娱乐" to "EXPENSE_文化休闲",
            "EXPENSE_购物" to "EXPENSE_家居家装",
            "INCOME_理财" to "INCOME_理财收益",
            "INCOME_兼职" to "INCOME_兼职外快",
            "INCOME_奖金" to "INCOME_工资"
        )

        for ((oldKey, newKey) in oldToNewMap) {
            val oldCats = currentCategories.filter { "${it.type}_${it.name}" == oldKey }
            val newCat = currentCategories.find { "${it.type}_${it.name}" == newKey }
            if (newCat != null && oldCats.isNotEmpty()) {
                for (oldCat in oldCats) {
                    if (oldCat.id != newCat.id) {
                        recordDao.updateCategoryId(oldCat.id, newCat.id)
                        categoryDao.deleteCategory(oldCat)
                    }
                }
            }
        }

        // 4. 清理同名重复分类（例如同名多份）
        val refreshed = categoryDao.getAllCategoriesList()
        val grouped = refreshed.groupBy { "${it.type}_${it.name}" }
        for ((_, list) in grouped) {
            if (list.size > 1) {
                val primary = list.first()
                for (dup in list.drop(1)) {
                    recordDao.updateCategoryId(dup.id, primary.id)
                    categoryDao.deleteCategory(dup)
                }
            }
        }
    }
}
