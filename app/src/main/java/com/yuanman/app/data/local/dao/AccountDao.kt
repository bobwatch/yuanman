package com.yuanman.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.AccountReconciliationEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AccountDao {

    // ---- 账户 CRUD / 排序 / 归档 ----

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    abstract fun observeAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    abstract fun observeActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL ORDER BY isArchived ASC, sortOrder ASC, id ASC")
    abstract suspend fun getAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE deletedAt IS NULL AND isArchived = 0 ORDER BY sortOrder ASC, id ASC")
    abstract suspend fun getActiveAccountsList(): List<AccountEntity>

    @Query("SELECT * FROM accounts ORDER BY id ASC")
    abstract suspend fun getAllAccountsIncludingDeleted(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :accountId AND deletedAt IS NULL LIMIT 1")
    abstract suspend fun getAccountById(accountId: Long): AccountEntity?

    @Query("SELECT * FROM accounts WHERE TRIM(name) = TRIM(:name) ORDER BY (deletedAt IS NULL) DESC, id ASC LIMIT 1")
    abstract suspend fun getAccountByNameIncludingDeleted(name: String): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts WHERE deletedAt IS NULL")
    abstract suspend fun countAccounts(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAccount(account: AccountEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAccounts(accounts: List<AccountEntity>)

    @Update
    abstract suspend fun updateAccount(account: AccountEntity)

    @Query("UPDATE accounts SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :accountId")
    abstract suspend fun updateAccountSortOrder(accountId: Long, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE accounts SET isArchived = :archived, updatedAt = :updatedAt WHERE id = :accountId")
    abstract suspend fun setAccountArchived(accountId: Long, archived: Boolean, updatedAt: Long)

    @Query("UPDATE accounts SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :accountId")
    abstract suspend fun softDeleteAccount(accountId: Long, deletedAt: Long)

    @Query("UPDATE accounts SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE deletedAt IS NULL")
    abstract suspend fun softDeleteAllAccounts(deletedAt: Long)

    @Query("UPDATE accounts SET openingBalanceCents = openingBalanceCents + :deltaCents, updatedAt = :updatedAt WHERE id = :accountId")
    abstract suspend fun adjustOpeningBalance(accountId: Long, deltaCents: Long, updatedAt: Long)

    // ---- 引用与对账记录的跨表维护 ----

    @Query("SELECT COUNT(*) FROM records WHERE deletedAt IS NULL AND (accountId = :accountId OR toAccountId = :accountId)")
    abstract suspend fun countRecordsForAccount(accountId: Long): Int

    @Query("UPDATE records SET accountId = :targetId WHERE accountId = :sourceId")
    abstract suspend fun moveRecordAccount(sourceId: Long, targetId: Long)

    @Query("UPDATE records SET toAccountId = :targetId WHERE toAccountId = :sourceId")
    abstract suspend fun moveRecordToAccount(sourceId: Long, targetId: Long)

    @Query("UPDATE account_reconciliations SET deletedAt = :deletedAt, updatedAt = :deletedAt WHERE accountId = :accountId AND deletedAt IS NULL")
    abstract suspend fun softDeleteReconciliationsForAccount(accountId: Long, deletedAt: Long)

    @Insert
    abstract suspend fun insertReconciliation(reconciliation: AccountReconciliationEntity): Long

    /** 删除仅无流水的账户；有流水（含转账任一端）的账户拒绝删除。 */
    @Transaction
    open suspend fun deleteAccountIfUnused(accountId: Long, deletedAt: Long): Boolean {
        if (countRecordsForAccount(accountId) > 0) return false
        softDeleteAccount(accountId, deletedAt)
        softDeleteReconciliationsForAccount(accountId, deletedAt)
        return true
    }

    /**
     * 把源账户合并进目标账户：双向重写 records 引用（accountId / toAccountId），
     * 软删源账户及其对账记录（对账语义绑定原账户流水，合并后留痕无意义）。
     */
    @Transaction
    open suspend fun mergeAccountInto(sourceId: Long, targetId: Long, now: Long) {
        if (sourceId == targetId) return
        moveRecordAccount(sourceId, targetId)
        moveRecordToAccount(sourceId, targetId)
        softDeleteReconciliationsForAccount(sourceId, now)
        softDeleteAccount(sourceId, now)
    }

    /**
     * 对账：按截至 asOfEnd（当日结束毫秒）的账面余额与真实余额比较，落一条对账记录。
     * applyCorrection = true 且差额非 0 时，把差额并入期初（以对账日为基线校正），
     * 此后流水照常自动累计。余额永远由期初 + 流水实时得出，对账记录不参与计算。
     */
    @Transaction
    open suspend fun reconcile(
        accountId: Long,
        asOfEnd: Long,
        actualBalanceCents: Long,
        applyCorrection: Boolean
    ): ReconcileResult {
        val book = getBookBalanceAt(accountId, asOfEnd)
            ?: throw IllegalArgumentException("账户不存在或已删除")
        val diff = actualBalanceCents - book
        val corrected = applyCorrection && diff != 0L
        val now = System.currentTimeMillis()
        if (corrected) {
            adjustOpeningBalance(accountId, diff, now)
        }
        insertReconciliation(
            AccountReconciliationEntity(
                accountId = accountId,
                asOfDate = asOfEnd,
                actualBalanceCents = actualBalanceCents,
                bookBalanceCents = book,
                diffCents = diff,
                corrected = corrected,
                createdAt = now,
                updatedAt = now
            )
        )
        return ReconcileResult(bookBalanceCents = book, diffCents = diff, corrected = corrected)
    }

    // ---- 余额聚合（唯一口径：期初 + 流水；转账双端各自记账）----

    @Query(
        """
        SELECT
            a.id AS accountId,
            a.openingBalanceCents + COALESCE(SUM(
                CASE
                    WHEN r.type = 'INCOME' AND r.accountId = a.id THEN r.amount
                    WHEN r.type = 'EXPENSE' AND r.accountId = a.id THEN -r.amount
                    WHEN r.type = 'TRANSFER' AND r.accountId = a.id THEN -r.amount
                    WHEN r.type = 'TRANSFER' AND r.toAccountId = a.id THEN r.amount
                    ELSE 0
                END
            ), 0) AS balanceCents
        FROM accounts a
        LEFT JOIN records r ON r.deletedAt IS NULL AND (r.accountId = a.id OR r.toAccountId = a.id)
            AND (:asOfEnd = 0 OR r.recordTime <= :asOfEnd)
        WHERE a.deletedAt IS NULL
        GROUP BY a.id
        ORDER BY a.sortOrder ASC, a.id ASC
        """
    )
    abstract fun observeBalances(asOfEnd: Long = 0L): Flow<List<AccountBalance>>

    /** 某账户截至 asOfEnd 的账面余额（对账对话框用）。 */
    @Query(
        """
        SELECT a.openingBalanceCents + COALESCE(SUM(
            CASE
                WHEN r.type = 'INCOME' THEN r.amount
                WHEN r.type = 'EXPENSE' THEN -r.amount
                WHEN r.type = 'TRANSFER' AND r.accountId = a.id THEN -r.amount
                WHEN r.type = 'TRANSFER' AND r.toAccountId = a.id THEN r.amount
                ELSE 0
            END
        ), 0) AS balanceCents
        FROM accounts a
        LEFT JOIN records r ON r.deletedAt IS NULL AND r.recordTime <= :asOfEnd
            AND (r.accountId = a.id OR r.toAccountId = a.id)
        WHERE a.id = :accountId AND a.deletedAt IS NULL
        GROUP BY a.id
        """
    )
    protected abstract suspend fun getBookBalanceAt(accountId: Long, asOfEnd: Long): Long?

    /** 时间段内各账户入账/出账合计（账户页本月入/出小字用）。 */
    @Query(
        """
        SELECT
            a.id AS accountId,
            COALESCE(SUM(CASE
                WHEN r.type = 'INCOME' AND r.accountId = a.id THEN r.amount
                WHEN r.type = 'TRANSFER' AND r.toAccountId = a.id THEN r.amount
                ELSE 0
            END), 0) AS inCents,
            COALESCE(SUM(CASE
                WHEN r.type = 'EXPENSE' AND r.accountId = a.id THEN r.amount
                WHEN r.type = 'TRANSFER' AND r.accountId = a.id THEN r.amount
                ELSE 0
            END), 0) AS outCents
        FROM accounts a
        LEFT JOIN records r ON r.deletedAt IS NULL AND (r.accountId = a.id OR r.toAccountId = a.id)
            AND (:monthStart = 0 OR r.recordTime >= :monthStart)
            AND (:monthEnd = 0 OR r.recordTime <= :monthEnd)
        WHERE a.deletedAt IS NULL
        GROUP BY a.id
        ORDER BY a.sortOrder ASC, a.id ASC
        """
    )
    abstract fun observeMonthInOut(monthStart: Long = 0L, monthEnd: Long = 0L): Flow<List<AccountInOut>>
}

data class AccountBalance(
    val accountId: Long,
    val balanceCents: Long
)

data class AccountInOut(
    val accountId: Long,
    val inCents: Long,
    val outCents: Long
)

data class ReconcileResult(
    val bookBalanceCents: Long,
    val diffCents: Long,
    val corrected: Boolean
)
