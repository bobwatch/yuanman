package com.yuanman.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import java.util.Locale

/** Merges peer data without ever treating a peer's local database id as identity. */
@Dao
abstract class SyncDao {
    @Query("SELECT * FROM categories ORDER BY id ASC")
    protected abstract suspend fun getAllCategories(): List<CategoryEntity>

    @Query("SELECT * FROM records ORDER BY id ASC")
    protected abstract suspend fun getAllRecords(): List<RecordEntity>

    @Insert
    protected abstract suspend fun insertCategory(category: CategoryEntity): Long

    @Insert
    protected abstract suspend fun insertRecord(record: RecordEntity): Long

    @Update
    protected abstract suspend fun updateCategory(category: CategoryEntity)

    @Update
    protected abstract suspend fun updateRecord(record: RecordEntity)

    @Query("UPDATE records SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    protected abstract suspend fun updateRecordCategory(oldCategoryId: Long, newCategoryId: Long)

    @Query("UPDATE categories SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :categoryId")
    protected abstract suspend fun softDeleteCategory(categoryId: Long, deletedAt: Long)

    @Query("SELECT COUNT(*) FROM records WHERE categoryId = :categoryId AND deletedAt IS NULL")
    protected abstract suspend fun countActiveRecordsForCategory(categoryId: Long): Int

    @Transaction
    open suspend fun deleteCategoryIfUnused(categoryId: Long, deletedAt: Long): Boolean {
        if (countActiveRecordsForCategory(categoryId) > 0) return false
        softDeleteCategory(categoryId, deletedAt)
        return true
    }

    @Transaction
    open suspend fun snapshot(): SyncSnapshot = SyncSnapshot(
        categories = getAllCategories(),
        records = getAllRecords()
    )

    @Transaction
    open suspend fun merge(
        remoteCategories: List<CategoryEntity>,
        remoteRecords: List<RecordEntity>
    ): SyncMergeResult {
        val localCategories = getAllCategories().toMutableList()
        var changedCategoryCount = consolidateActiveCategoryDuplicates(localCategories)

        val localBySyncId = localCategories.associateByTo(mutableMapOf()) { it.syncId }
        val activeByKey = localCategories
            .filter { it.deletedAt == null }
            .associateByTo(mutableMapOf()) { it.syncKey() }
        // A deleted category is still an identity. If another device created the
        // same category independently, match it too instead of inserting a
        // second active row beside the tombstone.
        val localByKey = localCategories
            .sortedWith(compareBy<CategoryEntity> { it.deletedAt != null }
                .thenByDescending { it.updatedAt }
                .thenBy { it.id })
            .associateByTo(mutableMapOf()) { it.syncKey() }
        val remoteToLocalCategoryIds = mutableMapOf<Long, Long>()

        for (rawRemote in remoteCategories) {
            val remote = rawRemote.normalized()
            if (remote.syncId.isBlank() || remote.name.isBlank()) continue
            val localById = localBySyncId[remote.syncId]
            // A tombstoned row may be a duplicate retired by the local
            // consolidation pass. Active incoming data with the same logical
            // key must still map to the surviving active row, otherwise its
            // records would be attached to a tombstone and disappear.
            val local = if (remote.deletedAt == null && localById?.deletedAt != null) {
                activeByKey[remote.syncKey()] ?: localById
            } else {
                localById ?: activeByKey[remote.syncKey()] ?: localByKey[remote.syncKey()]
            }

            if (local == null) {
                val insertedId = insertCategory(remote.copy(id = 0L))
                val inserted = remote.copy(id = insertedId)
                localCategories += inserted
                localBySyncId[inserted.syncId] = inserted
                if (inserted.deletedAt == null) activeByKey[inserted.syncKey()] = inserted
                localByKey[inserted.syncKey()] = inserted
                remoteToLocalCategoryIds[rawRemote.id] = insertedId
                changedCategoryCount += 1
            } else {
                // Independently-created same-name categories converge on one stable id.
                val requestedCanonicalSyncId = minOf(local.syncId, remote.syncId)
                // Never assign an id already owned by a different local row
                // (notably a tombstoned duplicate), or Room's unique index
                // would reject the merge and leave the whole transaction out.
                val canonicalSyncId = if (localCategories.any {
                        it.id != local.id && it.syncId == requestedCanonicalSyncId
                    }
                ) {
                    local.syncId
                } else {
                    requestedCanonicalSyncId
                }
                val canonicalLocal = local.copy(syncId = canonicalSyncId)
                val canonicalRemote = remote.copy(id = local.id, syncId = canonicalSyncId)
                val merged = if (canonicalRemote.winsAgainst(canonicalLocal)) {
                    canonicalRemote
                } else {
                    canonicalLocal
                }
                if (merged != local) {
                    updateCategory(merged)
                    localBySyncId.remove(local.syncId)
                    localBySyncId[canonicalSyncId] = merged
                    val localIndex = localCategories.indexOfFirst { it.id == local.id }
                    if (localIndex >= 0) localCategories[localIndex] = merged
                    activeByKey.entries.removeAll { it.value.id == local.id }
                    if (merged.deletedAt == null) activeByKey[merged.syncKey()] = merged
                    localByKey[merged.syncKey()] = merged
                    changedCategoryCount += 1
                }
                remoteToLocalCategoryIds[rawRemote.id] = local.id
            }
        }

        val allLocalRecords = getAllRecords().toMutableList()
        val localRecordsBySyncId = allLocalRecords.associateByTo(mutableMapOf()) { it.syncId }
        val localRecordsByFingerprint = allLocalRecords
            .groupBy { it.fingerprint() }
            .mapValuesTo(mutableMapOf()) { (_, records) -> records.toMutableList() }
        var changedRecordCount = 0
        var skippedRecordCount = 0

        for (rawRemote in remoteRecords) {
            val localCategoryId = remoteToLocalCategoryIds[rawRemote.categoryId]
            if (localCategoryId == null || rawRemote.syncId.isBlank()) {
                skippedRecordCount += 1
                continue
            }

            var remote = rawRemote.normalized().copy(categoryId = localCategoryId)
            val mappedCategory = localBySyncId.values.firstOrNull { it.id == localCategoryId }
            if (mappedCategory?.deletedAt != null && remote.deletedAt == null) {
                if (mappedCategory.updatedAt >= remote.updatedAt) {
                    // A category can only be deleted after its active records are gone.
                    // Convert data from a stale peer into the same tombstone state.
                    remote = remote.copy(
                        updatedAt = mappedCategory.updatedAt,
                        deletedAt = mappedCategory.deletedAt
                    )
                } else {
                    val restoredCategory = mappedCategory.copy(
                        updatedAt = remote.updatedAt,
                        deletedAt = null
                    )
                    updateCategory(restoredCategory)
                    localBySyncId[restoredCategory.syncId] = restoredCategory
                    activeByKey[restoredCategory.syncKey()] = restoredCategory
                    localByKey[restoredCategory.syncKey()] = restoredCategory
                    changedCategoryCount += 1
                }
            }
            var local = localRecordsBySyncId[remote.syncId]

            // One-time bridge for databases that generated independent ids during migration.
            if (local == null && remote.isLegacySyncId()) {
                local = localRecordsByFingerprint[remote.fingerprint()]
                    ?.singleOrNull { it.isLegacySyncId() }
                if (local != null) {
                    val canonicalSyncId = minOf(local.syncId, remote.syncId)
                    if (canonicalSyncId != local.syncId) {
                        val converged = local.copy(syncId = canonicalSyncId)
                        updateRecord(converged)
                        localRecordsBySyncId.remove(local.syncId)
                        localRecordsBySyncId[canonicalSyncId] = converged
                        local = converged
                    }
                    remote = remote.copy(syncId = canonicalSyncId)
                }
            }

            if (local == null) {
                val insertedId = insertRecord(remote.copy(id = 0L))
                val inserted = remote.copy(id = insertedId)
                localRecordsBySyncId[inserted.syncId] = inserted
                localRecordsByFingerprint.getOrPut(inserted.fingerprint()) { mutableListOf() }.add(inserted)
                changedRecordCount += 1
            } else if (remote.winsAgainst(local)) {
                val merged = remote.copy(id = local.id, syncId = local.syncId)
                updateRecord(merged)
                localRecordsBySyncId[merged.syncId] = merged
                localRecordsByFingerprint[local.fingerprint()]?.removeAll { it.id == local.id }
                localRecordsByFingerprint.getOrPut(merged.fingerprint()) { mutableListOf() }.add(merged)
                changedRecordCount += 1
            }
        }

        return SyncMergeResult(
            receivedCategoryCount = remoteCategories.size,
            changedCategoryCount = changedCategoryCount,
            receivedRecordCount = remoteRecords.size,
            changedRecordCount = changedRecordCount,
            skippedRecordCount = skippedRecordCount
        )
    }

    private suspend fun consolidateActiveCategoryDuplicates(
        categories: MutableList<CategoryEntity>
    ): Int {
        var changed = 0
        val groups = categories
            .filter { it.deletedAt == null }
            .groupBy { it.syncKey() }
        for ((_, group) in groups) {
            // Prefer the most recently edited row, then the lowest local id for
            // a deterministic tie-break. This avoids silently discarding a
            // newer icon/name/tag change while cleaning legacy duplicates.
            val primary = group.sortedWith(
                compareByDescending<CategoryEntity> { it.revision }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id }
            ).first()
            for (category in group) {
                if (primary.id != category.id) {
                    updateRecordCategory(category.id, primary.id)
                    val deletedAt = maxOf(System.currentTimeMillis(), category.updatedAt + 1L)
                    softDeleteCategory(category.id, deletedAt)
                    val index = categories.indexOfFirst { it.id == category.id }
                    if (index >= 0) {
                        categories[index] = category.copy(
                            updatedAt = deletedAt,
                            revision = category.revision + 1L,
                            deletedAt = deletedAt
                        )
                    }
                    changed += 1
                }
            }
        }
        return changed
    }

    private fun CategoryEntity.normalized(): CategoryEntity = copy(
        type = type.trim().uppercase(Locale.ROOT),
        name = name.trim(),
        updatedAt = maxOf(updatedAt, createdAt, deletedAt ?: Long.MIN_VALUE)
    )

    private fun RecordEntity.normalized(): RecordEntity = copy(
        type = type.trim().uppercase(Locale.ROOT),
        updatedAt = maxOf(updatedAt, createdAt, deletedAt ?: Long.MIN_VALUE)
    )

    private fun CategoryEntity.syncKey(): CategoryKey = CategoryKey(
        type = type.trim().uppercase(Locale.ROOT),
        name = name.trim()
    )

    private fun CategoryEntity.winsAgainst(local: CategoryEntity): Boolean = when {
        revision != local.revision -> revision > local.revision
        updatedAt != local.updatedAt -> updatedAt > local.updatedAt
        (deletedAt != null) != (local.deletedAt != null) -> deletedAt != null
        else -> deterministicValue() > local.deterministicValue()
    }

    private fun RecordEntity.winsAgainst(local: RecordEntity): Boolean = when {
        revision != local.revision -> revision > local.revision
        updatedAt != local.updatedAt -> updatedAt > local.updatedAt
        (deletedAt != null) != (local.deletedAt != null) -> deletedAt != null
        else -> deterministicValue() > local.deterministicValue()
    }

    private fun CategoryEntity.deterministicValue(): String =
        listOf(type, name, iconName, colorHex, isDefault, sortOrder, tags, revision, deletedAt).joinToString("\u0001")

    private fun RecordEntity.deterministicValue(): String =
        listOf(type, amount, categoryId, recordTime, remark, paymentMethod, splitGroupId,
            splitIndex, splitTotal, createdAt, revision, deletedAt).joinToString("\u0001")

    private fun RecordEntity.fingerprint(): RecordFingerprint = RecordFingerprint(
        type = type,
        amount = amount,
        categoryId = categoryId,
        recordTime = recordTime,
        remark = remark,
        paymentMethod = paymentMethod,
        splitGroupId = splitGroupId,
        splitIndex = splitIndex,
        splitTotal = splitTotal,
        createdAt = createdAt,
        updatedAt = updatedAt,
        deletedAt = deletedAt
    )

    private fun RecordEntity.isLegacySyncId(): Boolean =
        syncId.startsWith("record:") || syncId.startsWith("legacy-record:")

    private data class CategoryKey(val type: String, val name: String)

    private data class RecordFingerprint(
        val type: String,
        val amount: Long,
        val categoryId: Long,
        val recordTime: Long,
        val remark: String,
        val paymentMethod: String,
        val splitGroupId: String?,
        val splitIndex: Int?,
        val splitTotal: Int?,
        val createdAt: Long,
        val updatedAt: Long,
        val deletedAt: Long?
    )
}

data class SyncSnapshot(
    val categories: List<CategoryEntity>,
    val records: List<RecordEntity>
)

data class SyncMergeResult(
    val receivedCategoryCount: Int,
    val changedCategoryCount: Int,
    val receivedRecordCount: Int,
    val changedRecordCount: Int,
    val skippedRecordCount: Int
)
