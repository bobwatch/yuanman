package com.yuanman.app.data.local.dao

import androidx.room.*
import com.yuanman.app.data.local.entity.CategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategoriesList(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllCategoriesIncludingDeleted(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE UPPER(TRIM(type)) = UPPER(TRIM(:type)) AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getCategoriesByType(type: String): Flow<List<CategoryEntity>>

    /**
     * 一次性统计所有分类的有效账单数量。
     *
     * 分类管理页之前会对每个分类单独查询一次使用次数，分类较多时会在首屏
     * 产生明显的查询抖动。改为 LEFT JOIN + GROUP BY 后，Room 只需执行一次
     * 查询，并且 records/categories 任一表发生变化时会自动重新发射结果。
     */
    @Query("""
        SELECT categories.id AS categoryId, COUNT(records.id) AS usageCount
        FROM categories
        LEFT JOIN records
            ON records.categoryId = categories.id
            AND records.deletedAt IS NULL
        WHERE categories.deletedAt IS NULL
        GROUP BY categories.id
    """)
    fun observeCategoryUsageCounts(): Flow<List<CategoryUsageCount>>

    @Query("SELECT * FROM categories WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getCategoryById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun getCategoryByIdFlow(id: Long): Flow<CategoryEntity?>

    @Query("SELECT * FROM categories WHERE UPPER(TRIM(type)) = UPPER(TRIM(:type)) AND TRIM(name) = TRIM(:name) ORDER BY (deletedAt IS NULL) DESC, id ASC LIMIT 1")
    suspend fun getCategoryByNameIncludingDeleted(type: String, name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Query("UPDATE categories SET sortOrder = :sortOrder, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :categoryId")
    suspend fun updateSortOrder(categoryId: Long, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :categoryId")
    suspend fun softDeleteCategory(categoryId: Long, deletedAt: Long)

    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE isDefault = 0 AND deletedAt IS NULL")
    suspend fun softDeleteCustomCategories(deletedAt: Long)

    @Delete
    suspend fun hardDeleteCategory(category: CategoryEntity)

    @Query("SELECT COUNT(*) FROM categories WHERE deletedAt IS NULL")
    suspend fun getCategoryCount(): Int

    @Query("DELETE FROM categories")
    suspend fun deleteAllCategories()
}

data class CategoryUsageCount(
    val categoryId: Long,
    val usageCount: Int
)
