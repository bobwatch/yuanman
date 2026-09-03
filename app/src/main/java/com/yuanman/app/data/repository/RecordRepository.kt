package com.yuanman.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.dao.AccountDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.AccountRecordCalculator
import com.yuanman.app.utils.DateTimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import com.yuanman.app.widget.WidgetUpdateManager

class RecordRepository(
    private val recordDao: RecordDao,
    private val context: Context,
    private val database: AppDatabase? = null,
    private val accountDao: AccountDao? = null
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
        paymentMethods: List<String>,
        paymentMethodFilterEnabled: Int,
        searchQuery: String
    ) = recordDao.getFilteredSummary(
        startTime = startTime,
        endTime = endTime,
        type = type,
        categoryIds = categoryIds,
        categoryFilterEnabled = categoryFilterEnabled,
        paymentMethods = paymentMethods,
        paymentMethodFilterEnabled = paymentMethodFilterEnabled,
        searchQuery = searchQuery
    )

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
    ): List<RecordWithCategory> = withContext(Dispatchers.IO) {
        recordDao.getRecordsFilteredPaged(
            startTime = startTime,
            endTime = endTime,
            type = type,
            categoryIds = categoryIds,
            categoryFilterEnabled = categoryFilterEnabled,
            paymentMethods = paymentMethods,
            paymentMethodFilterEnabled = paymentMethodFilterEnabled,
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
        val normalized = record.copy(deletedAt = null, updatedAt = now)
        val id = withAccountTransaction {
            recordDao.insertRecord(normalized).also {
                applyBalanceChanges(normalized, direction = 1, timestamp = now)
            }
        }
        WidgetUpdateManager.requestUpdate(context)
        id
    }

    suspend fun insertRecords(records: List<RecordEntity>) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val normalizedRecords = records.map { it.copy(deletedAt = null, updatedAt = now) }
        withAccountTransaction {
            recordDao.insertRecords(normalizedRecords)
            normalizedRecords.forEach { applyBalanceChanges(it, direction = 1, timestamp = now) }
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun updateRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        withAccountTransaction {
            val existing = recordDao.getRecordEntityByIdIncludingDeleted(record.id)
            val now = System.currentTimeMillis()
            val normalized = record.copy(
                syncId = existing?.syncId ?: record.syncId,
                createdAt = existing?.createdAt ?: record.createdAt,
                updatedAt = now,
                revision = (existing?.revision ?: record.revision) + 1L,
                deletedAt = null
            )
            if (existing != null && existing.deletedAt == null) {
                applyBalanceChanges(existing, direction = -1, timestamp = now)
            }
            recordDao.updateRecord(normalized)
            applyBalanceChanges(normalized, direction = 1, timestamp = now)
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteRecord(record: RecordEntity) = withContext(Dispatchers.IO) {
        deleteRecordByIdInternal(record.id)
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteRecordById(id: Long) = withContext(Dispatchers.IO) {
        deleteRecordByIdInternal(id)
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun restoreRecord(id: Long) = withContext(Dispatchers.IO) {
        withAccountTransaction {
            val existing = recordDao.getRecordEntityByIdIncludingDeleted(id)
            if (existing != null && existing.deletedAt != null) {
                val now = System.currentTimeMillis()
                applyBalanceChanges(existing, direction = 1, timestamp = now)
                recordDao.restoreRecordById(id, now)
            }
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    suspend fun deleteAllRecords() = withContext(Dispatchers.IO) {
        withAccountTransaction {
            val now = System.currentTimeMillis()
            recordDao.getAllRecordsDirect()
                .asSequence()
                .map { it.record }
                .forEach { applyBalanceChanges(it, direction = -1, timestamp = now) }
            recordDao.softDeleteAllRecords(now)
        }
        WidgetUpdateManager.requestUpdate(context)
    }

    fun notifyDataChanged() {
        WidgetUpdateManager.requestUpdate(context)
    }

    private suspend fun deleteRecordByIdInternal(id: Long) {
        withAccountTransaction {
            val existing = recordDao.getRecordEntityByIdIncludingDeleted(id)
            if (existing != null && existing.deletedAt == null) {
                val now = System.currentTimeMillis()
                applyBalanceChanges(existing, direction = -1, timestamp = now)
                recordDao.softDeleteRecordById(id, now)
            }
        }
    }

    private suspend fun <T> withAccountTransaction(block: suspend () -> T): T {
        val db = database
        return if (db == null) {
            block()
        } else {
            db.withTransaction { block() }
        }
    }

    private suspend fun applyBalanceChanges(
        record: RecordEntity,
        direction: Int,
        timestamp: Long
    ) {
        val dao = accountDao ?: return
        val accountIds = listOfNotNull(record.accountId, record.targetAccountId).distinct()
        if (accountIds.isEmpty()) return

        val accounts = accountIds.mapNotNull { id -> dao.getAccountByIdIncludingDeleted(id)?.let { id to it } }.toMap()
        check(accounts.size == accountIds.size) {
            "关联账户不存在：${accountIds.first { it !in accounts }}"
        }
        AccountRecordCalculator.changesFor(record, accounts).forEach { change ->
            val signedDelta = if (direction < 0) -change.deltaCents else change.deltaCents
            check(dao.adjustBalance(change.accountId, signedDelta, timestamp) == 1) {
                "关联账户不存在或已删除：${change.accountId}"
            }
        }
    }
}
