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

    /** 该短语是否已被用户“使用或手动整理过”（sampleCount>0 或曾设置 lastUsedAt）。 */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM quick_entry_learning
            WHERE type = :type AND phrase = :phrase AND (sampleCount > 0 OR lastUsedAt > 0)
        )
    """)
    suspend fun existsUserManagedRule(type: String, phrase: String): Boolean

    /**
     * 删除与目标分类不同的“纯系统预置”规则（从未被使用、也未被用户编辑过），
     * 让用户记账内容覆盖同名的预置映射。
     */
    @Query("""
        DELETE FROM quick_entry_learning
        WHERE type = :type AND phrase = :phrase
            AND sampleCount = 0 AND lastUsedAt = 0
            AND categorySyncId != :keepCategorySyncId
    """)
    suspend fun deleteUnusedPresetsForPhrase(type: String, phrase: String, keepCategorySyncId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: QuickEntryLearningEntity)

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
