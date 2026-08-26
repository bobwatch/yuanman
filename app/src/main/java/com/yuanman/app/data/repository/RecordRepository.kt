package com.yuanman.app.data.repository

import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RecordRepository(
    private val recordDao: RecordDao
) {
    fun getAllRecords(): Flow<List<RecordWithCategory>> = recordDao.getAllRecords()

    fun getRecordsByDateRange(startTime: Long, endTime: Long): Flow<List<RecordWithCategory>> {
        return recordDao.getRecordsByDateRange(startTime, endTime)
    }

    fun getRecordsByWeek(year: Int, weekOfYear: Int): Flow<List<RecordWithCategory>> {
        val start = DateTimeUtils.getWeekStartTimestamp(year, weekOfYear)
        val end = DateTimeUtils.getWeekEndTimestamp(year, weekOfYear)
        return recordDao.getRecordsByDateRange(start, end)
    }

    fun getRecordsByMonth(year: Int, month: Int): Flow<List<RecordWithCategory>> {
        val start = DateTimeUtils.getMonthStartTimestamp(year, month)
        val end = DateTimeUtils.getMonthEndTimestamp(year, month)
        return recordDao.getRecordsByDateRange(start, end)
    }

    fun getRecordsByYear(year: Int): Flow<List<RecordWithCategory>> {
        val start = DateTimeUtils.getYearStartTimestamp(year)
        val end = DateTimeUtils.getYearEndTimestamp(year)
        return recordDao.getRecordsByDateRange(start, end)
    }

    fun getFilteredSummary(
        startTime: Long,
        endTime: Long,
        type: String?,
        categoryId: Long?,
        paymentMethod: String?,
        searchQuery: String
    ) = recordDao.getFilteredSummary(startTime, endTime, type, categoryId, paymentMethod, searchQuery)

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
    ): List<RecordWithCategory> = withContext(Dispatchers.IO) {
        recordDao.getRecordsFilteredPaged(
            startTime = startTime,
            endTime = endTime,
            type = type,
            categoryId = categoryId,
            paymentMethod = paymentMethod,
            searchQuery = searchQuery,
            sortOrder = sortOrder,
            limit = limit,
            offset = offset
        )
    }

    fun getRecentRecords(limit: Int = 10): Flow<List<RecordWithCategory>> {
        return recordDao.getRecentRecords(limit)
    }

    fun getRecordsByCategoryId(categoryId: Long): Flow<List<RecordWithCategory>> {
        return recordDao.getRecordsByCategoryId(categoryId)
    }

    fun getRecordById(id: Long): Flow<RecordWithCategory?> {
        return recordDao.getRecordById(id)
    }

    suspend fun getRecordByIdDirect(id: Long): RecordWithCategory? = withContext(Dispatchers.IO) {
        recordDao.getRecordByIdDirect(id)
    }

    suspend fun insertRecord(record: RecordEntity): Long = withContext(Dispatchers.IO) {
        recordDao.insertRecord(record)
    }

    suspend fun insertRecords(records: List<RecordEntity>) = withContext(Dispatchers.IO) {
        recordDao.insertRecords(records)
    }

    suspend fun updateRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        recordDao.updateRecord(record)
    }

    suspend fun deleteRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        recordDao.deleteRecord(record)
    }

    suspend fun deleteRecordById(id: Long) = withContext(Dispatchers.IO) {
        recordDao.deleteRecordById(id)
    }

    suspend fun deleteAllRecords() = withContext(Dispatchers.IO) {
        recordDao.deleteAllRecords()
    }
}
