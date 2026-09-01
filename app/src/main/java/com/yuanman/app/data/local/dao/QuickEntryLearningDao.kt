package com.yuanman.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickEntryLearningDao {
    @Query("SELECT * FROM quick_entry_learning ORDER BY sampleCount DESC, lastUsedAt DESC")
    fun observeAll(): Flow<List<QuickEntryLearningEntity>>

    @Query("SELECT * FROM quick_entry_learning WHERE type = :type ORDER BY sampleCount DESC, lastUsedAt DESC")
    fun observeByType(type: String): Flow<List<QuickEntryLearningEntity>>

    @Query("SELECT * FROM quick_entry_learning WHERE type = :type AND phrase = :phrase AND categorySyncId = :categorySyncId LIMIT 1")
    suspend fun find(type: String, phrase: String, categorySyncId: String): QuickEntryLearningEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: QuickEntryLearningEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rules: List<QuickEntryLearningEntity>)

    @Delete
    suspend fun delete(rule: QuickEntryLearningEntity)

    @Query("""
        UPDATE quick_entry_learning
        SET sampleCount = sampleCount + 1, lastUsedAt = :lastUsedAt
        WHERE type = :type AND phrase = :phrase AND categorySyncId = :categorySyncId
    """)
    suspend fun increment(type: String, phrase: String, categorySyncId: String, lastUsedAt: Long): Int

    @Query("DELETE FROM quick_entry_learning")
    suspend fun deleteAll()

    /** 仅清除用户积累的规则；sampleCount=0 的系统预置词库需要保留。 */
    @Query("DELETE FROM quick_entry_learning WHERE sampleCount > 0")
    suspend fun deleteUserRules()
}
