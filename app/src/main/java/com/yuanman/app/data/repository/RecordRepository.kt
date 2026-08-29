package com.yuanman.app.data.repository

import android.content.Context
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.yuanman.app.widget.WidgetUpdateManager

class RecordRepository(
    private val recordDao: RecordDao,
    private val context: Context
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
        categoryIds: List<Long>,
        categoryFilterEnabled: Int,
        paymentMethod: String?,
        searchQuery: String
    ) = recordDao.getFilteredSummary(startTime, endTime, type, categoryIds, categoryFilterEnabled, paymentMethod, searchQuery)

    suspend fun getRecordsFilteredPaged(
        startTime: Long,
        endTime: Long,
        type: String?,
        categoryIds: List<Long>,
        categoryFilterEnabled: Int,
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
            categoryIds = categoryIds,
            categoryFilterEnabled = categoryFilterEnabled,
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

    fun observeLatestRecordUpdate(): Flow<Long> = recordDao.observeLatestRecordUpdate()

    suspend fun getRecordByIdDirect(id: Long): RecordWithCategory? = withContext(Dispatchers.IO) {
        recordDao.getRecordByIdDirect(id)
    }

    suspend fun insertRecord(record: RecordEntity): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val id = recordDao.insertRecord(record.copy(deletedAt = null, updatedAt = now))
        WidgetUpdateManager.requestUpdate(context)
        id
    }

    suspend fun insertRecords(records: List<RecordEntity>) = withContext(Dispatchers.IO) {
        recordDao.insertRecords(records)
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun updateRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        val existing = recordDao.getRecordEntityByIdIncludingDeleted(record.id)
        val normalized = record.copy(
            syncId = existing?.syncId ?: record.syncId,
            createdAt = existing?.createdAt ?: record.createdAt,
            updatedAt = System.currentTimeMillis(),
            deletedAt = null
        )
        recordDao.updateRecord(normalized)
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        recordDao.softDeleteRecordById(record.id, System.currentTimeMillis())
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteRecordById(id: Long) = withContext(Dispatchers.IO) {
        recordDao.softDeleteRecordById(id, System.currentTimeMillis())
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun restoreRecord(id: Long) = withContext(Dispatchers.IO) {
        recordDao.restoreRecordById(id, System.currentTimeMillis())
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteAllRecords() = withContext(Dispatchers.IO) {
        recordDao.softDeleteAllRecords(System.currentTimeMillis())
        WidgetUpdateManager.requestUpdate(context)
    }

    fun notifyDataChanged() {
        WidgetUpdateManager.requestUpdate(context)
    }
}
