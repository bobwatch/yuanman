package com.yuanman.app.data.local.dao

import androidx.room.*
import com.yuanman.app.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getAllAccountsSync(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 1 AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getArchivedAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE isArchived = 0 AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    suspend fun getActiveAccountsSync(): List<AccountEntity>

    @Query("UPDATE accounts SET isArchived = :isArchived, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun setArchived(id: Long, isArchived: Boolean, updatedAt: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM accounts WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    fun getAccountById(id: Long): Flow<AccountEntity?>

    @Query("SELECT * FROM accounts WHERE id = :id AND deletedAt IS NULL LIMIT 1")
    suspend fun getAccountByIdSync(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id LIMIT 1")
    suspend fun getAccountByIdIncludingDeleted(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id AND isArchived = 0 AND deletedAt IS NULL LIMIT 1")
    suspend fun getActiveAccountByIdSync(id: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE syncId = :syncId LIMIT 1")
    suspend fun getAccountBySyncId(syncId: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE name = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun getAccountByName(name: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE type = :type AND deletedAt IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getAccountsByType(type: String): Flow<List<AccountEntity>>

    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL")
    suspend fun countActiveAccounts(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(accounts: List<AccountEntity>): List<Long>

    @Update
    suspend fun update(account: AccountEntity)

    @Update
    suspend fun updateAll(accounts: List<AccountEntity>)

    @Query("UPDATE accounts SET balanceCents = :newBalanceCents, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun updateBalance(id: Long, newBalanceCents: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE accounts SET balanceCents = balanceCents + :deltaCents, updatedAt = :updatedAt, revision = revision + 1 WHERE id = :id")
    suspend fun adjustBalance(id: Long, deltaCents: Long, updatedAt: Long = System.currentTimeMillis()): Int

    /** 更新由完整账单投影出的余额，不生成新的账户版本。 */
    @Query("UPDATE accounts SET balanceCents = :balanceCents WHERE id = :id")
    suspend fun setDerivedBalance(id: Long, balanceCents: Long): Int

    @Query("UPDATE accounts SET deletedAt = :deletedAt, updatedAt = :deletedAt, revision = revision + 1 WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    suspend fun getAllForSync(): List<AccountEntity>
}
