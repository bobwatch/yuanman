package com.yuanman.app.data.repository

import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.dao.CategoryDao
import com.yuanman.app.data.local.dao.CategoryUsageCount
import com.yuanman.app.data.local.dao.QuickEntryLearningDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.dao.SyncDao
import com.yuanman.app.data.local.dao.SyncMergeResult
import com.yuanman.app.data.local.dao.SyncSnapshot
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.QuickEntryParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.Locale

class CategoryRepository(
    private val categoryDao: CategoryDao,
    private val recordDao: RecordDao,
    private val syncDao: SyncDao,
    private val quickEntryLearningDao: QuickEntryLearningDao
) {
    fun getAllCategories(): Flow<List<CategoryEntity>> = categoryDao.getAllCategories()
    
    suspend fun getAllCategoriesDirect(): List<CategoryEntity> = withContext(Dispatchers.IO) {
        categoryDao.getAllCategoriesList()
    }

    fun getCategoriesByType(type: RecordType): Flow<List<CategoryEntity>> {
        return categoryDao.getCategoriesByType(type.name)
    }

    fun observeCategoryUsageCounts(): Flow<List<CategoryUsageCount>> =
        categoryDao.observeCategoryUsageCounts()

    suspend fun getCategoryById(id: Long): CategoryEntity? = withContext(Dispatchers.IO) {
        categoryDao.getCategoryById(id)
    }

    fun getCategoryByIdFlow(id: Long): Flow<CategoryEntity?> = categoryDao.getCategoryByIdFlow(id)

    suspend fun insertCategory(category: CategoryEntity): Long = withContext(Dispatchers.IO) {
        val name = category.name.trim()
        val type = category.type.trim().uppercase(Locale.ROOT)
        val existing = categoryDao.getCategoryByNameIncludingDeleted(type, name)
        if (existing != null) {
            if (existing.deletedAt != null) {
                categoryDao.updateCategory(
                    category.copy(
                        id = existing.id,
                        name = name,
                        type = type,
                        syncId = existing.syncId,
                        createdAt = existing.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        revision = existing.revision + 1L,
                        deletedAt = null
                    )
                )
            }
            existing.id
        } else {
            categoryDao.insertCategory(
                category.copy(name = name, type = type, updatedAt = System.currentTimeMillis(), deletedAt = null)
            )
        }
    }

    suspend fun insertCategories(categories: List<CategoryEntity>) = withContext(Dispatchers.IO) {
        categoryDao.insertCategories(categories)
    }

    suspend fun mergeSyncedData(
        categories: List<CategoryEntity>,
        records: List<RecordEntity>
    ): SyncMergeResult = withContext(Dispatchers.IO) {
        val result = syncDao.merge(categories, records)
        backfillQuickEntryLearningInternal()
        result
    }

    suspend fun getSyncSnapshot(): SyncSnapshot = withContext(Dispatchers.IO) {
        syncDao.snapshot()
    }

    fun observeQuickEntryLearning(type: RecordType): Flow<List<QuickEntryLearningEntity>> =
        quickEntryLearningDao.observeByType(type.name)

    fun observeAllQuickEntryLearning(): Flow<List<QuickEntryLearningEntity>> =
        quickEntryLearningDao.observeAll()

    suspend fun learnQuickEntry(type: RecordType, phrase: String, categorySyncId: String) =
        withContext(Dispatchers.IO) {
            val normalizedPhrase = QuickEntryParser.normalizeLearningText(phrase)
            if (normalizedPhrase.isBlank() || categorySyncId.isBlank()) return@withContext
            val existing = quickEntryLearningDao.find(type.name, normalizedPhrase, categorySyncId)
            if (existing == null) {
                quickEntryLearningDao.upsert(
                    QuickEntryLearningEntity(
                        type = type.name,
                        phrase = normalizedPhrase,
                        categorySyncId = categorySyncId,
                        sampleCount = 1
                    )
                )
            } else {
                quickEntryLearningDao.increment(
                    type = type.name,
                    phrase = normalizedPhrase,
                    categorySyncId = categorySyncId,
                    lastUsedAt = System.currentTimeMillis()
                )
            }
        }

    suspend fun clearQuickEntryLearning() = withContext(Dispatchers.IO) {
        quickEntryLearningDao.deleteUserRules()
    }

    suspend fun mergeQuickEntryLearning(rules: List<QuickEntryLearningEntity>) = withContext(Dispatchers.IO) {
        val valid = rules.filter {
            it.type in RecordType.entries.map(RecordType::name) &&
                it.phrase.isNotBlank() && it.categorySyncId.isNotBlank() && it.sampleCount >= 0
        }
        if (valid.isNotEmpty()) quickEntryLearningDao.upsertAll(valid)
    }

    /** 将解析器内置词库同步到分类学习页，幂等执行，不覆盖用户已经积累的权重。 */
    suspend fun ensureDefaultQuickEntryLearning() = withContext(Dispatchers.IO) {
        val defaultKeys = AppDatabase.getDefaultCategories()
            .map { "${it.type.trim().uppercase(Locale.ROOT)}_${it.name.trim()}" }
            .toSet()
        categoryDao.getAllCategoriesList()
            // 兼容早期版本：部分系统分类的 isDefault 标记可能是 false，但名称仍是内置分类。
            .filter {
                it.deletedAt == null &&
                    (it.isDefault || "${it.type.trim().uppercase(Locale.ROOT)}_${it.name.trim()}" in defaultKeys)
            }
            .forEach { category ->
                QuickEntryParser.defaultLearningPhrases(category).forEach { phrase ->
                    if (quickEntryLearningDao.find(category.type, phrase, category.syncId) == null) {
                        quickEntryLearningDao.upsert(
                            QuickEntryLearningEntity(
                                type = category.type,
                                phrase = phrase,
                                categorySyncId = category.syncId,
                                sampleCount = 0,
                                lastUsedAt = 0L
                            )
                        )
                    }
                }
            }
    }

    /** 为升级前已有的历史账单建立学习样本；只补缺失项，不会重复累计权重。 */
    suspend fun backfillQuickEntryLearning() = withContext(Dispatchers.IO) {
        backfillQuickEntryLearningInternal()
    }

    private suspend fun backfillQuickEntryLearningInternal() {
        recordDao.getAllRecordsDirect().forEach { item ->
            val category = item.category ?: return@forEach
            val phrase = QuickEntryParser.normalizeLearningText(item.record.remark)
            if (phrase.isBlank()) return@forEach
            val existing = quickEntryLearningDao.find(item.record.type, phrase, category.syncId)
            if (existing == null) {
                quickEntryLearningDao.upsert(
                    QuickEntryLearningEntity(
                        type = item.record.type,
                        phrase = phrase,
                        categorySyncId = category.syncId,
                        sampleCount = 1,
                        lastUsedAt = item.record.updatedAt
                    )
                )
            } else if (existing.sampleCount == 0) {
                // 历史账单命中系统预置词时，转为用户习惯并开始累计权重。
                quickEntryLearningDao.increment(
                    type = existing.type,
                    phrase = existing.phrase,
                    categorySyncId = existing.categorySyncId,
                    lastUsedAt = item.record.updatedAt
                )
            }
        }
    }

    suspend fun updateQuickEntryLearning(
        rule: QuickEntryLearningEntity,
        phrase: String,
        type: String = rule.type,
        categorySyncId: String = rule.categorySyncId
    ) = withContext(Dispatchers.IO) {
        val normalizedPhrase = QuickEntryParser.normalizeLearningText(phrase)
        if (normalizedPhrase.isBlank()) return@withContext
        quickEntryLearningDao.delete(rule)
        quickEntryLearningDao.upsert(rule.copy(type = type, phrase = normalizedPhrase, categorySyncId = categorySyncId, lastUsedAt = System.currentTimeMillis()))
    }

    suspend fun deleteQuickEntryLearning(rule: QuickEntryLearningEntity) = withContext(Dispatchers.IO) {
        quickEntryLearningDao.delete(rule)
    }

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        categoryDao.updateCategory(
            category.copy(
                name = category.name.trim(),
                type = category.type.trim().uppercase(Locale.ROOT),
                updatedAt = System.currentTimeMillis(),
                revision = category.revision + 1L,
                deletedAt = null
            )
        )
    }

    suspend fun updateCategoryOrder(categoryIds: List<Long>) = withContext(Dispatchers.IO) {
        val updatedAt = System.currentTimeMillis()
        categoryIds.forEachIndexed { index, categoryId ->
            categoryDao.updateSortOrder(categoryId, index, updatedAt)
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
        val deletedAt = System.currentTimeMillis()
        val deleted = syncDao.deleteCategoryIfUnused(category.id, deletedAt)
        if (!deleted) {
            val usageCount = recordDao.countRecordsByCategoryId(category.id)
            Result.failure(IllegalStateException("该分类已被 $usageCount 条账单使用，无法直接删除"))
        } else {
            Result.success(Unit)
        }
    }

    suspend fun resetDefaultCategories() = withContext(Dispatchers.IO) {
        categoryDao.softDeleteCustomCategories(System.currentTimeMillis())
        val defaults = AppDatabase.getDefaultCategories()
        defaults.forEach { default ->
            val existing = categoryDao.getCategoryByNameIncludingDeleted(default.type, default.name)
            if (existing == null) {
                categoryDao.insertCategory(default)
            } else if (existing.deletedAt != null) {
                categoryDao.updateCategory(
                    default.copy(
                        id = existing.id,
                        syncId = existing.syncId,
                        createdAt = existing.createdAt,
                        updatedAt = System.currentTimeMillis(),
                        revision = existing.revision + 1L,
                        deletedAt = null
                    )
                )
            }
        }
    }

    /**
     * 智能补全并清理合并冗余分类：
     * 1. 自动补充缺失的标准分类（如 餐饮美食、爱车养车、红包转账等）
     * 2. 自动迁移并合并旧版冗余分类（如 餐饮 -> 餐饮美食，交通 -> 交通出行，理财 -> 理财收益等）
     * 3. 合并重复项并保留墓碑，避免同步时再次复活旧记录
     */
    suspend fun ensureDefaultCategories() = withContext(Dispatchers.IO) {
        val defaults = AppDatabase.getDefaultCategories()
        var currentCategories = categoryDao.getAllCategoriesIncludingDeleted()

        if (currentCategories.isEmpty()) {
            categoryDao.insertCategories(defaults)
            return@withContext
        }

        // 1. 补全缺失的标准分类
        val existingKeys = currentCategories.map { it.normalizedKey() }.toSet()
        val missing = defaults.filter { it.normalizedKey() !in existingKeys }
        if (missing.isNotEmpty()) {
            categoryDao.insertCategories(missing)
        }

        // 2. 重新加载最新分类列表
        currentCategories = categoryDao.getAllCategoriesIncludingDeleted()

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
            val oldCats = currentCategories.filter { it.deletedAt == null && it.normalizedKey() == oldKey }
            val newCat = currentCategories.find { it.deletedAt == null && it.normalizedKey() == newKey }
            if (newCat != null && oldCats.isNotEmpty()) {
                for (oldCat in oldCats) {
                    if (oldCat.id != newCat.id) {
                        recordDao.updateCategoryId(oldCat.id, newCat.id)
                        categoryDao.softDeleteCategory(oldCat.id, System.currentTimeMillis())
                    }
                }
            }
        }

        // 4. 清理同名重复分类（例如同名多份）
        val refreshed = categoryDao.getAllCategoriesList()
        val grouped = refreshed.groupBy { it.normalizedKey() }
        for ((_, list) in grouped) {
            if (list.size > 1) {
                val primary = list.first()
                for (dup in list.drop(1)) {
                    recordDao.updateCategoryId(dup.id, primary.id)
                    categoryDao.softDeleteCategory(dup.id, System.currentTimeMillis())
                }
            }
        }
    }

    private fun CategoryEntity.normalizedKey(): String =
        "${type.trim().uppercase(Locale.ROOT)}_${name.trim()}"
}
