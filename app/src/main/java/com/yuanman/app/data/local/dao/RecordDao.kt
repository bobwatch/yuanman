package com.yuanman.app.data.local.dao

import androidx.room.*
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Transaction
    @Query("SELECT * FROM records ORDER BY recordTime DESC, id DESC")
    fun getAllRecords(): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE categoryId = :categoryId ORDER BY recordTime DESC, id DESC")
    fun getRecordsByCategoryId(categoryId: Long): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE recordTime >= :startTime AND recordTime <= :endTime ORDER BY recordTime DESC, id DESC")
    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records ORDER BY recordTime DESC, id DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 10): Flow<List<RecordWithCategory>>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    fun getRecordById(id: Long): Flow<RecordWithCategory?>

    @Transaction
    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getRecordByIdDirect(id: Long): RecordWithCategory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: RecordEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<RecordEntity>)

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Delete
    suspend fun deleteRecord(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("SELECT COUNT(*) FROM records WHERE categoryId = :categoryId")
    suspend fun countRecordsByCategoryId(categoryId: Long): Int

    @Query("UPDATE records SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategoryId(oldCategoryId: Long, newCategoryId: Long)

    @Query("SELECT SUM(amount) FROM records WHERE type = :type AND recordTime >= :startTime AND recordTime <= :endTime")
    fun getTotalAmountByTypeAndDateRange(type: String, startTime: Long, endTime: Long): Flow<Long?>

    @Query("""
        SELECT 
            COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END), 0) AS totalExpense,
            COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END), 0) AS totalIncome,
            COUNT(*) AS totalCount
        FROM records
        WHERE recordTime >= :startTime AND recordTime <= :endTime
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:paymentMethod IS NULL OR paymentMethod = :paymentMethod)
          AND (:searchQuery = '' OR remark LIKE '%' || :searchQuery || '%' OR paymentMethod LIKE '%' || :searchQuery || '%')
    """)
    fun getFilteredSummary(
        startTime: Long,
        endTime: Long,
        type: String?,
        categoryId: Long?,
        paymentMethod: String?,
        searchQuery: String
    ): Flow<RecordFilterSummary>

    @Transaction
    @Query("""
        SELECT * FROM records 
        WHERE recordTime >= :startTime AND recordTime <= :endTime 
          AND (:type IS NULL OR type = :type)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
          AND (:paymentMethod IS NULL OR paymentMethod = :paymentMethod)
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
        categoryId: Long?,
        paymentMethod: String?,
        searchQuery: String,
        sortOrder: String,
        limit: Int,
        offset: Int
    ): List<RecordWithCategory>

    @Query("SELECT COUNT(*) FROM records")
    fun getTotalRecordCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM records")
    suspend fun getTotalRecordCountDirect(): Int

    @Query("DELETE FROM records")
    suspend fun deleteAllRecords()
}

data class RecordFilterSummary(
    val totalExpense: Long = 0L,
    val totalIncome: Long = 0L,
    val totalCount: Int = 0
)
