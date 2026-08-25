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

    @Update
    suspend fun updateRecord(record: RecordEntity)

    @Delete
    suspend fun deleteRecord(record: RecordEntity)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)

    @Query("SELECT COUNT(*) FROM records WHERE categoryId = :categoryId")
    suspend fun countRecordsByCategoryId(categoryId: Long): Int

    @Query("SELECT SUM(amount) FROM records WHERE type = :type AND recordTime >= :startTime AND recordTime <= :endTime")
    fun getTotalAmountByTypeAndDateRange(type: String, startTime: Long, endTime: Long): Flow<Long?>

    @Query("DELETE FROM records")
    suspend fun deleteAllRecords()
}
