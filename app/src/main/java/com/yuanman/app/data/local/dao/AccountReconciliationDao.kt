package com.yuanman.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.yuanman.app.data.local.entity.AccountReconciliationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountReconciliationDao {

    @Query(
        "SELECT * FROM account_reconciliations WHERE accountId = :accountId AND deletedAt IS NULL " +
            "ORDER BY asOfDate DESC, id DESC"
    )
    fun observeByAccount(accountId: Long): Flow<List<AccountReconciliationEntity>>

    @Query(
        "SELECT * FROM account_reconciliations WHERE accountId = :accountId AND deletedAt IS NULL " +
            "ORDER BY asOfDate DESC, id DESC"
    )
    suspend fun getByAccountList(accountId: Long): List<AccountReconciliationEntity>

    @Query("SELECT * FROM account_reconciliations WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getById(id: Long): AccountReconciliationEntity?

    @Query("SELECT * FROM account_reconciliations ORDER BY id ASC")
    suspend fun getAllIncludingDeleted(): List<AccountReconciliationEntity>

    @Query("UPDATE account_reconciliations SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :id AND deletedAt IS NULL")
    suspend fun softDeleteById(id: Long, deletedAt: Long)

    @Query("UPDATE account_reconciliations SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE deletedAt IS NULL")
    suspend fun softDeleteAll(deletedAt: Long)

    @Insert
    suspend fun insertReconciliation(reconciliation: AccountReconciliationEntity): Long

    @Insert
    suspend fun insertReconciliations(reconciliations: List<AccountReconciliationEntity>)
}
