package com.yuanman.app.data.local.dao

import androidx.room.*
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY recordTime DESC, id DESC")
    fun getAllRecords(): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY recordTime DESC, id DESC")
    suspend fun getAllRecordsDirect(): List<RecordWithCategory>

    @Transaction
    @Query("SELECT * FROM records WHERE categoryId = :categoryId AND deletedAt IS NULL ORDER BY recordTime DESC, id DESC")
    fun getRecordsByCategoryId(categoryId: Long): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt IS NULL AND recordTime >= :startTime AND recordTime <= :endTime ORDER BY recordTime DESC, id DESC")
    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE deletedAt IS NULL ORDER BY recordTime DESC, id DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 10): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun getRecordById(id: Long): Flow<RecordWithCategory?>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getRecordByIdDirect(id: Long): RecordWithCategory?

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getRecordEntityByIdIncludingDeleted(id: Long): RecordEntity?

    /**
     * 监听 records 表的变更。使用更新时间聚合值避免为分页刷新加载整张账单表，
     * 但仍会在新增、编辑和软删除后触发 Room Flow 重新发射。
     */
    @Query("SELECT COALESCE(MAX(updatedAt), 0) FROM records")
    fun observeLatestRecordUpdate(): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<RecordEntity>)

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Query("UPDATE records SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDeleteRecordById(id: Long, deletedAt: Long)

    @Query("UPDATE records SET deletedAt = NULL, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun restoreRecordById(id: Long, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM records WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun countRecordsByCategoryId(categoryId: Long): Int

    @Query("UPDATE records SET categoryId = :newCategoryId, updatedAt = :updatedAt, revision = revision + 1 WHERE categoryId = :oldCategoryId")
    suspend fun updateCategoryId(oldCategoryId: Long, newCategoryId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT SUM(amount) FROM records WHERE deletedAt IS NULL AND type = :type AND recordTime >= :startTime AND recordTime <= :endTime")
    fun getTotalAmountByTypeAndDateRange(type: String, startTime: Long, endTime: Long): Flow<Long?>

    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome,
            COUNT(*) AS totalCount
        FROM records
        WHERE deletedAt IS NULL AND recordTime >= :startTime AND recordTime <= :endTime
          AND (:type IS NULL OR type = :type)
          AND (:categoryFilterEnabled = 0 OR categoryId IN (:categoryIds))
          AND (:paymentMethodFilterEnabled = 0 OR paymentMethod IN (:paymentMethods))
          AND (:searchQuery = '' OR remark LIKE '%' || :searchQuery || '%' OR paymentMethod LIKE '%' || :searchQuery || '%')
    """)
    fun getFilteredSummary(
        startTime: Long,
        endTime: Long,
        type: String?,
        categoryIds: List<Long>,
        categoryFilterEnabled: Int,
        paymentMethods: List<String>,
        paymentMethodFilterEnabled: Int,
        searchQuery: String
    ): Flow<RecordFilterSummary>

    @Transaction
    @Query("""
        SELECT * FROM records 
        WHERE deletedAt IS NULL AND recordTime >= :startTime AND recordTime <= :endTime
          AND (:type IS NULL OR type = :type)
          AND (:categoryFilterEnabled = 0 OR categoryId IN (:categoryIds))
          AND (:paymentMethodFilterEnabled = 0 OR paymentMethod IN (:paymentMethods))
          AND (:searchQuery = '' OR remark LIKE '%' || :searchQuery || '%' OR paymentMethod LIKE '%' || :searchQuery || '%')
        ORDER BY 
          CASE WHEN :sortOrder = 'TIME_DESC' THEN recordTime END DESC,
          CASE WHEN :sortOrder = 'TIME_ASC' THEN recordTime END ASC,
          CASE WHEN :sortOrder = 'AMOUNT_DESC' THEN amount END DESC,
          CASE WHEN :sortOrder = 'AMOUNT_ASC' THEN amount END ASC,
          id DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getRecordsFilteredPaged(
        startTime: Long,
        endTime: Long,
        type: String?,
        categoryIds: List<Long>,
        categoryFilterEnabled: Int,
        paymentMethods: List<String>,
        paymentMethodFilterEnabled: Int,
        searchQuery: String,
        sortOrder: String,
        limit: Int,
        offset: Int
    ): List<RecordWithCategory>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL")
    fun getTotalRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL")
    suspend fun getTotalRecordCountDirect(): Int

    @Query("""
        SELECT
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome,
            COUNT(*) AS recordCount
        FROM records
        WHERE deletedAt IS NULL AND recordTime >= :startTime AND recordTime <= :endTime
    """)
    suspend fun getWidgetMonthSummary(startTime: Long, endTime: Long): WidgetMonthSummary

    @Query("UPDATE records SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE deletedAt IS NULL")
    suspend fun softDeleteAllRecords(deletedAt: Long)
}

data class RecordFilterSummary(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val totalCount: Int = 0
)


data class WidgetMonthSummary(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val recordCount: Int = 0
)
