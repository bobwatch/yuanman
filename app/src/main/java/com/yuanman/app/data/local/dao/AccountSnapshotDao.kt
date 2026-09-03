package com.yuanman.app.data.local.dao

import androidx.room.*
import com.yuanman.app.data.local.entity.AccountSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountSnapshotDao {

    @Query("SELECT * FROM account_snapshots WHERE deletedAt IS NULL ORDER BY periodEndTimestamp DESC, id DESC")
    fun getAllSnapshots(): Flow<List<AccountSnapshotEntity>>

    @Query("SELECT * FROM account_snapshots WHERE periodType = :periodType AND deletedAt IS NULL ORDER BY periodEndTimestamp DESC, id DESC LIMIT :limit")
    fun getSnapshotsByPeriodType(periodType: String, limit: Int = 12): Flow<List<AccountSnapshotEntity>>

    @Query("SELECT * FROM account_snapshots WHERE periodType = :periodType AND deletedAt IS NULL ORDER BY periodEndTimestamp DESC, id DESC LIMIT :limit")
    suspend fun getSnapshotsByPeriodTypeSync(periodType: String, limit: Int = 12): List<AccountSnapshotEntity>

    @Query("SELECT * FROM account_snapshots WHERE periodKey = :periodKey AND periodType = :periodType AND deletedAt IS NULL LIMIT 1")
    suspend fun getSnapshotByPeriod(periodKey: String, periodType: String): AccountSnapshotEntity?

    @Query("SELECT * FROM account_snapshots WHERE deletedAt IS NULL ORDER BY periodEndTimestamp DESC, id DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<AccountSnapshotEntity?>

    @Query("SELECT * FROM account_snapshots WHERE deletedAt IS NULL ORDER BY periodEndTimestamp DESC, id DESC LIMIT 1")
    suspend fun getLatestSnapshotSync(): AccountSnapshotEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(snapshot: AccountSnapshotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(snapshots: List<AccountSnapshotEntity>): List<Long>

    @Update
    suspend fun update(snapshot: AccountSnapshotEntity)

    @Query("UPDATE account_snapshots SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM account_snapshots ORDER BY id ASC")
    suspend fun getAllForSync(): List<AccountSnapshotEntity>
}
