package com.yuanman.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.dao.AccountDao
import com.yuanman.app.data.local.dao.AccountSnapshotDao
import com.yuanman.app.data.local.dao.CategoryDao
import com.yuanman.app.data.local.dao.RecordDao
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.AccountSnapshotEntity
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class AccountRepository(
    private val database: AppDatabase,
    private val accountDao: AccountDao,
    private val accountSnapshotDao: AccountSnapshotDao,
    private val recordDao: RecordDao,
    private val categoryDao: CategoryDao,
    private val context: Context
) {

    private class TransferRejectedException : Exception()

    private fun rejectTransfer(): Nothing = throw TransferRejectedException()

    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()
    val activeAccounts: Flow<List<AccountEntity>> = accountDao.getActiveAccounts()
    val archivedAccounts: Flow<List<AccountEntity>> = accountDao.getArchivedAccounts()
    val allSnapshots: Flow<List<AccountSnapshotEntity>> = accountSnapshotDao.getAllSnapshots()

    suspend fun getSyncSnapshot(): AccountSyncSnapshot = withContext(Dispatchers.IO) {
        database.withTransaction {
            AccountSyncSnapshot(
                accounts = accountDao.getAllForSync(),
                accountSnapshots = accountSnapshotDao.getAllForSync()
            )
        }
    }

    suspend fun getAllAccountsForBackup(): List<AccountEntity> = withContext(Dispatchers.IO) {
        accountDao.getAllForSync()
    }

    suspend fun getAllAccountSnapshotsForBackup(): List<AccountSnapshotEntity> = withContext(Dispatchers.IO) {
        accountSnapshotDao.getAllForSync()
    }

    suspend fun getAccountById(id: Long): Flow<AccountEntity?> = accountDao.getAccountById(id)

    suspend fun getAccountByIdSync(id: Long): AccountEntity? = withContext(Dispatchers.IO) {
        accountDao.getAccountByIdSync(id)
    }

    suspend fun getAccountByName(name: String): AccountEntity? = withContext(Dispatchers.IO) {
        accountDao.getAccountByName(name)
    }

    suspend fun ensureDefaultAccounts() = withContext(Dispatchers.IO) {
        val count = accountDao.countActiveAccounts()
        if (count == 0) {
            val defaults = listOf(
                AccountEntity(
                    name = "微信零钱",
                    type = AccountType.CHECKING.name,
                    icon = "wechat",
                    colorHex = "#07C160",
                    sortOrder = 1
                ),
                AccountEntity(
                    name = "支付宝",
                    type = AccountType.CHECKING.name,
                    icon = "alipay",
                    colorHex = "#1677FF",
                    sortOrder = 2
                ),
                AccountEntity(
                    name = "银行借记卡",
                    type = AccountType.CHECKING.name,
                    icon = "bank_card",
                    colorHex = "#DC2626",
                    sortOrder = 3
                ),
                AccountEntity(
                    name = "现金",
                    type = AccountType.CHECKING.name,
                    icon = "cash",
                    colorHex = "#D97706",
                    sortOrder = 4
                ),
                AccountEntity(
                    name = "信用卡",
                    type = AccountType.CREDIT.name,
                    icon = "credit_card",
                    colorHex = "#EA580C",
                    sortOrder = 5
                ),
                AccountEntity(
                    name = "股票理财账户",
                    type = AccountType.INVESTMENT.name,
                    icon = "stock",
                    colorHex = "#2563EB",
                    sortOrder = 6
                )
            )
            accountDao.insertAll(defaults)
        }
    }

    suspend fun addAccount(account: AccountEntity): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val toInsert = account.copy(
            syncId = if (account.syncId.isBlank()) UUID.randomUUID().toString() else account.syncId,
            createdAt = now,
            updatedAt = now,
            revision = 1L
        )
        accountDao.insert(toInsert)
    }

    suspend fun updateAccount(
        account: AccountEntity,
        balanceAdjustmentRemark: String = ""
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = accountDao.getAccountByIdIncludingDeleted(account.id)
                ?: error("账户不存在：${account.id}")
            check(existing.deletedAt == null) { "账户已删除，无法编辑" }

            val now = System.currentTimeMillis()
            val balanceDiffCents = Math.subtractExact(account.balanceCents, existing.balanceCents)
            val metadata = account.copy(
                syncId = existing.syncId,
                createdAt = existing.createdAt,
                initialBalanceCents = existing.initialBalanceCents,
                // The balance is changed by the adjustment record below so it
                // remains reversible through the normal record lifecycle.
                balanceCents = existing.balanceCents,
                updatedAt = now,
                revision = existing.revision + 1L
            )
            accountDao.update(metadata)

            if (balanceDiffCents != 0L) {
                val adjustmentType = AccountRecordCalculator.reconciliationTypeFor(metadata, balanceDiffCents)
                    ?: error("无效的余额调整")
                val adjustmentCategory = getOrCreateBalanceAdjustmentCategory(adjustmentType, now)
                insertAccountRecordInTransaction(
                    record = RecordEntity(
                        type = adjustmentType,
                        amount = kotlin.math.abs(balanceDiffCents),
                        categoryId = adjustmentCategory.id,
                        recordTime = now,
                        remark = "【余额调整】${balanceAdjustmentRemark.trim().ifBlank { "手动更新账户余额" }}",
                        paymentMethod = metadata.name,
                        accountId = metadata.id,
                        isAdjustment = true,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    ),
                    accountsById = mapOf(metadata.id to metadata)
                )
            }
        }
    }

    suspend fun deleteAccount(id: Long) = withContext(Dispatchers.IO) {
        accountDao.softDelete(id)
    }

    suspend fun setAccountArchived(id: Long, isArchived: Boolean) = withContext(Dispatchers.IO) {
        accountDao.setArchived(id, isArchived)
    }

    suspend fun updateBalance(id: Long, newBalanceCents: Long) = withContext(Dispatchers.IO) {
        accountDao.updateBalance(id, newBalanceCents)
    }

    /**
     * Merge accounts before records so account ids from another device can be
     * translated to local ids. Account balances are part of the backup because
     * they are the current ledger baseline; snapshots are merged by sync id or
     * period and their embedded account ids are translated as well.
     */
    suspend fun mergeSyncedAccounts(
        remoteAccounts: List<AccountEntity>,
        remoteSnapshots: List<AccountSnapshotEntity>
    ): AccountSyncMergeResult = withContext(Dispatchers.IO) {
        database.withTransaction {
            mergeSyncedAccountsInTransaction(remoteAccounts, remoteSnapshots)
        }
    }

    /**
     * Captures the portion of each current balance that is not explained by
     * active account-linked records. It preserves legacy/manual balance edits
     * while a sync or restore rebuilds the derived balance projection.
     */
    suspend fun captureBalanceBaselinesInTransaction(): Map<Long, Long> {
        val accounts = accountDao.getAllForSync()
        val ledgerDeltas = calculateLedgerDeltas(accounts)
        return accounts.associate { account ->
            account.id to Math.subtractExact(
                account.balanceCents,
                ledgerDeltas[account.id] ?: 0L
            )
        }
    }

    /**
     * Rebuilds account balances from the complete active ledger. The account
     * row remains a projection, so incoming records from independent devices
     * cannot leave a balance based on whichever account row happened to win.
     */
    suspend fun recalculateBalancesInTransaction(
        balanceBaselines: Map<Long, Long> = emptyMap()
    ) {
        val accounts = accountDao.getAllForSync()
        val ledgerDeltas = calculateLedgerDeltas(accounts)
        accounts.forEach { account ->
            val baseline = balanceBaselines[account.id] ?: account.initialBalanceCents
            val expectedBalance = Math.addExact(baseline, ledgerDeltas[account.id] ?: 0L)
            if (account.balanceCents != expectedBalance) {
                check(accountDao.setDerivedBalance(account.id, expectedBalance) == 1) {
                    "账户余额重算失败：${account.id}"
                }
            }
        }
    }

    /**
     * Merges account data without opening a nested transaction.
     *
     * Callers that also merge categories and records must invoke this method
     * from their outer database transaction so a failed cross-table merge
     * cannot leave account state behind.
     */
    suspend fun mergeSyncedAccountsInTransaction(
        remoteAccounts: List<AccountEntity>,
        remoteSnapshots: List<AccountSnapshotEntity>
    ): AccountSyncMergeResult {
        val localAccounts = accountDao.getAllForSync().toMutableList()
        val localBySyncId = localAccounts.associateByTo(mutableMapOf()) { it.syncId }
        val claimedLocalIds = mutableSetOf<Long>()
        val remoteToLocalIds = mutableMapOf<Long, Long>()
        var changedAccountCount = 0

        remoteAccounts.forEach { rawRemote ->
                val remote = rawRemote.normalizedForSync()
                if (remote.syncId.isBlank() || remote.name.isBlank()) return@forEach

                val local = localBySyncId[remote.syncId]
                    ?: localAccounts.firstOrNull {
                        it.id !in claimedLocalIds && it.logicalKey() == remote.logicalKey()
                    }

                if (local == null) {
                    val insertedId = accountDao.insert(remote.copy(id = 0L))
                    val inserted = remote.copy(id = insertedId)
                    localAccounts += inserted
                    localBySyncId[inserted.syncId] = inserted
                    remoteToLocalIds[rawRemote.id] = insertedId
                    changedAccountCount += 1
                    claimedLocalIds += insertedId
                } else {
                    claimedLocalIds += local.id
                    val canonicalSyncId = canonicalSyncId(local, remote, localAccounts)
                    val canonicalLocal = local.copy(syncId = canonicalSyncId)
                    val canonicalRemote = remote.copy(id = local.id, syncId = canonicalSyncId)
                    val merged = if (canonicalRemote.winsAgainst(canonicalLocal)) {
                        canonicalRemote
                    } else {
                        canonicalLocal
                    }
                    if (merged != local) {
                        accountDao.update(merged)
                        val index = localAccounts.indexOfFirst { it.id == local.id }
                        if (index >= 0) localAccounts[index] = merged
                        localBySyncId.remove(local.syncId)
                        localBySyncId[merged.syncId] = merged
                        changedAccountCount += 1
                    }
                    remoteToLocalIds[rawRemote.id] = local.id
                }
            }

        val localSnapshots = accountSnapshotDao.getAllForSync().toMutableList()
        val localSnapshotsBySyncId = localSnapshots.associateByTo(mutableMapOf()) { it.syncId }
        var changedSnapshotCount = 0

        remoteSnapshots.forEach { rawRemote ->
                val local = localSnapshotsBySyncId[rawRemote.syncId]
                    ?: localSnapshots.firstOrNull {
                        it.periodKey == rawRemote.periodKey && it.periodType == rawRemote.periodType
                    }
                val remapped = rawRemote.copy(
                    snapshotDataJson = remapSnapshotAccountIds(rawRemote.snapshotDataJson, remoteToLocalIds)
                )

                if (local == null) {
                    val insertedId = accountSnapshotDao.insert(remapped.copy(id = 0L))
                    val inserted = remapped.copy(id = insertedId)
                    localSnapshots += inserted
                    localSnapshotsBySyncId[inserted.syncId] = inserted
                    changedSnapshotCount += 1
                } else {
                    val canonicalSyncId = canonicalSnapshotSyncId(local, remapped, localSnapshots)
                    val canonicalLocal = local.copy(syncId = canonicalSyncId)
                    val canonicalRemote = remapped.copy(id = local.id, syncId = canonicalSyncId)
                    val merged = if (canonicalRemote.winsAgainst(canonicalLocal)) {
                        canonicalRemote
                    } else {
                        canonicalLocal
                    }
                    if (merged != local) {
                        accountSnapshotDao.update(merged)
                        val index = localSnapshots.indexOfFirst { it.id == local.id }
                        if (index >= 0) localSnapshots[index] = merged
                        localSnapshotsBySyncId.remove(local.syncId)
                        localSnapshotsBySyncId[merged.syncId] = merged
                        changedSnapshotCount += 1
                    }
                }
            }

        return AccountSyncMergeResult(
            remoteToLocalAccountIds = remoteToLocalIds,
            changedAccountCount = changedAccountCount,
            changedSnapshotCount = changedSnapshotCount
        )
    }

    fun remapRecordAccountIds(
        records: List<RecordEntity>,
        remoteToLocalAccountIds: Map<Long, Long>,
        includesAccounts: Boolean
    ): List<RecordEntity> {
        if (!includesAccounts && records.any { it.accountId != null || it.targetAccountId != null }) {
            error("备份缺少账户数据，无法恢复账户关联账单，请重新导出完整备份")
        }
        return records.map { record ->
            record.copy(
                accountId = record.accountId?.let { remoteToLocalAccountIds[it] ?: error("备份中存在无法匹配的账户：$it") },
                targetAccountId = record.targetAccountId?.let {
                    remoteToLocalAccountIds[it] ?: error("备份中存在无法匹配的目标账户：$it")
                }
            )
        }
    }

    private fun remapSnapshotAccountIds(
        snapshotDataJson: String,
        remoteToLocalAccountIds: Map<Long, Long>
    ): String {
        if (!snapshotDataJson.trimStart().startsWith("[")) return snapshotDataJson
        val array = JSONArray(snapshotDataJson)
        repeat(array.length()) { index ->
            val item = array.getJSONObject(index)
            if (item.has("accountId") && !item.isNull("accountId")) {
                val remoteId = item.getLong("accountId")
                item.put(
                    "accountId",
                    remoteToLocalAccountIds[remoteId]
                        ?: error("账户快照中存在无法匹配的账户：$remoteId")
                )
            }
        }
        return array.toString()
    }

    private fun AccountEntity.normalizedForSync(): AccountEntity = copy(
        name = name.trim(),
        type = type.trim().uppercase(Locale.ROOT),
        currency = currency.trim().ifBlank { "CNY" },
        updatedAt = maxOf(updatedAt, createdAt, deletedAt ?: Long.MIN_VALUE)
    )

    private fun AccountEntity.logicalKey(): String =
        "${type.trim().uppercase(Locale.ROOT)}\u0001${name.trim()}"

    private fun AccountEntity.winsAgainst(local: AccountEntity): Boolean = when {
        revision != local.revision -> revision > local.revision
        updatedAt != local.updatedAt -> updatedAt > local.updatedAt
        (deletedAt != null) != (local.deletedAt != null) -> deletedAt != null
        else -> deterministicValue() > local.deterministicValue()
    }

    private fun AccountEntity.deterministicValue(): String = listOf(
        name, type, balanceCents, initialBalanceCents, currency, includeInNetWorth,
        icon, colorHex, remark, sortOrder, isArchived, revision, deletedAt
    ).joinToString("\u0001")

    private fun canonicalSyncId(
        local: AccountEntity,
        remote: AccountEntity,
        allAccounts: List<AccountEntity>
    ): String {
        val candidate = minOf(local.syncId, remote.syncId)
        return if (allAccounts.any { it.id != local.id && it.syncId == candidate }) local.syncId else candidate
    }

    private fun AccountSnapshotEntity.winsAgainst(local: AccountSnapshotEntity): Boolean = when {
        revision != local.revision -> revision > local.revision
        updatedAt != local.updatedAt -> updatedAt > local.updatedAt
        (deletedAt != null) != (local.deletedAt != null) -> deletedAt != null
        else -> snapshotDataJson > local.snapshotDataJson
    }

    private fun canonicalSnapshotSyncId(
        local: AccountSnapshotEntity,
        remote: AccountSnapshotEntity,
        allSnapshots: List<AccountSnapshotEntity>
    ): String {
        val candidate = minOf(local.syncId, remote.syncId)
        return if (allSnapshots.any { it.id != local.id && it.syncId == candidate }) local.syncId else candidate
    }

    /**
     * 账户间转账 (Transfer)
     */
    suspend fun transfer(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        remark: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            database.withTransaction {
                transferInTransaction(
                    fromAccountId = fromAccountId,
                    toAccountId = toAccountId,
                    amountCents = amountCents,
                    remark = remark,
                    timestamp = timestamp
                )
            }
            true
        } catch (_: TransferRejectedException) {
            false
        }
    }

    private suspend fun transferInTransaction(
        fromAccountId: Long,
        toAccountId: Long,
        amountCents: Long,
        remark: String,
        timestamp: Long
    ) {
        if (fromAccountId == toAccountId || amountCents <= 0L) rejectTransfer()

        val fromAccount = accountDao.getActiveAccountByIdSync(fromAccountId) ?: rejectTransfer()
        val toAccount = accountDao.getActiveAccountByIdSync(toAccountId) ?: rejectTransfer()
        val preview = AccountTransferCalculator.preview(fromAccount, toAccount, amountCents)
        if (!preview.isValid) rejectTransfer()

        // 查找或创建转账分类
        var transferCategory = categoryDao.getAllCategoriesList().firstOrNull { it.name == "转账" || it.name == "账户互转" }
        if (transferCategory == null) {
            val catId = categoryDao.insertCategory(
                CategoryEntity(
                    name = "账户互转",
                    type = "EXPENSE",
                    iconName = "swap_horiz",
                    colorHex = 0xFF2563EBL,
                    sortOrder = 99
                )
            )
            transferCategory = categoryDao.getCategoryById(catId)
        }

        val categoryId = transferCategory?.id ?: 1L
        // 余额变更与流水写入必须共用同一条路径。普通流水仓库会在编辑、删除、恢复
        // 时按流水反向调整余额；这里也按同一规则只应用一次，避免转账后续操作造成漂移。
        insertAccountRecordInTransaction(
            record = RecordEntity(
                type = "TRANSFER",
                amount = amountCents,
                categoryId = categoryId,
                recordTime = timestamp,
                remark = if (remark.isNotBlank()) remark else "${fromAccount.name} → ${toAccount.name}",
                paymentMethod = fromAccount.name,
                accountId = fromAccountId,
                targetAccountId = toAccountId,
                createdAt = timestamp,
                updatedAt = timestamp,
                revision = 1L
            ),
            accountsById = mapOf(fromAccountId to fromAccount, toAccountId to toAccount)
        )
    }

    /**
     * 执行周期对账平账并归档快照
     */
    suspend fun executeReconciliation(
        periodInfo: PeriodInfo,
        items: List<AccountReconciliationItem>,
        createAdjustmentRecords: Boolean = true
    ) = withContext(Dispatchers.IO) {
        database.withTransaction {
            val now = System.currentTimeMillis()

            // 查找或创建平账调整分类
            var adjExpenseCat = categoryDao.getAllCategoriesList().firstOrNull { it.name == "平账-漏记" || it.name == "记账调整" }
            if (adjExpenseCat == null) {
                val catId = categoryDao.insertCategory(
                    CategoryEntity(
                        name = "平账-漏记",
                        type = "EXPENSE",
                        iconName = "tune",
                        colorHex = 0xFF64748BL,
                        sortOrder = 98
                    )
                )
                adjExpenseCat = categoryDao.getCategoryById(catId)
            }

            var adjIncomeCat = categoryDao.getAllCategoriesList().firstOrNull { it.name == "平账-少记" || it.name == "投资收益调整" }
            if (adjIncomeCat == null) {
                val catId = categoryDao.insertCategory(
                    CategoryEntity(
                        name = "平账-少记",
                        type = "INCOME",
                        iconName = "tune",
                        colorHex = 0xFF64748BL,
                        sortOrder = 98
                    )
                )
                adjIncomeCat = categoryDao.getCategoryById(catId)
            }

            val snapshotArray = JSONArray()

            for (item in items) {
                if (!item.isIncluded) continue

                // The UI snapshot can become stale while the sheet is open. Do
                // not apply a delta to a newer balance, otherwise a concurrent
                // write would be silently overwritten by reconciliation.
                val currentAccount = accountDao.getActiveAccountByIdSync(item.account.id)
                    ?: error("对账账户不存在或已归档：${item.account.id}")
                check(currentAccount.balanceCents == item.bookBalanceCents) {
                    "对账数据已过期，请刷新后重试"
                }
                val diffCents = item.actualBalanceCents - currentAccount.balanceCents

                if (diffCents != 0L) {
                    if (createAdjustmentRecords) {
                        // Apply the adjustment through the same path as every
                        // other account-linked record. This keeps edit/delete/
                        // restore reversible and uses liability-aware semantics.
                        val type = AccountRecordCalculator.reconciliationTypeFor(currentAccount, diffCents)
                            ?: error("无效的对账差额")
                        val categoryId = if (type == "INCOME") {
                            adjIncomeCat?.id ?: 1L
                        } else {
                            adjExpenseCat?.id ?: 1L
                        }
                        insertAccountRecordInTransaction(
                            record = RecordEntity(
                                type = type,
                                amount = kotlin.math.abs(diffCents),
                                categoryId = categoryId,
                                recordTime = now,
                                remark = "【对账平账】${currentAccount.name} 差异校准",
                                paymentMethod = currentAccount.name,
                                accountId = currentAccount.id,
                                isAdjustment = true,
                                createdAt = now,
                                updatedAt = now,
                                revision = 1L
                            ),
                            accountsById = mapOf(currentAccount.id to currentAccount)
                        )
                    } else {
                        // Without an adjustment record, the explicit choice is
                        // to accept the external balance as the new baseline.
                        accountDao.updateBalance(currentAccount.id, item.actualBalanceCents, now)
                    }
                }

                val itemObj = JSONObject().apply {
                    put("accountId", currentAccount.id)
                    put("accountName", currentAccount.name)
                    put("accountType", currentAccount.type)
                    put("bookBalanceCents", currentAccount.balanceCents)
                    put("actualBalanceCents", item.actualBalanceCents)
                    put("diffCents", diffCents)
                }
                snapshotArray.put(itemObj)
            }

            // 3. 统计生成当前周期的资产快照
            val currentAccounts = accountDao.getActiveAccountsSync()
            val regularAsset = currentAccounts.filter { it.includeInNetWorth && !AccountType.fromString(it.type).isLiability }
                .sumOf { it.balanceCents }
            val liabilitySurplus = currentAccounts.filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents < 0 }
                .sumOf { -it.balanceCents }
            val totalAsset = regularAsset + liabilitySurplus
            val totalDebt = currentAccounts.filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents > 0 }
                .sumOf { it.balanceCents }
            val netWorth = totalAsset - totalDebt

            val existingSnapshot = accountSnapshotDao.getSnapshotByPeriod(periodInfo.periodKey, periodInfo.periodType.name)
            val snapshot = AccountSnapshotEntity(
                id = existingSnapshot?.id ?: 0L,
                syncId = existingSnapshot?.syncId ?: UUID.randomUUID().toString(),
                periodKey = periodInfo.periodKey,
                periodType = periodInfo.periodType.name,
                periodStartTimestamp = periodInfo.startTimestamp,
                periodEndTimestamp = periodInfo.endTimestamp,
                totalAssetCents = totalAsset,
                totalDebtCents = totalDebt,
                netWorthCents = netWorth,
                snapshotDataJson = snapshotArray.toString(),
                reconciledAt = now,
                createdAt = existingSnapshot?.createdAt ?: now,
                updatedAt = now,
                revision = (existingSnapshot?.revision ?: 0L) + 1L
            )

            accountSnapshotDao.insert(snapshot)
        }
    }

    /**
     * 执行收入分配划转
     */
    suspend fun executeIncomeAllocation(
        sourceAccountId: Long,
        totalIncomeCents: Long,
        results: List<IncomeAllocationResultItem>,
        remark: String = "发薪收入智能分配"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            database.withTransaction {
                if (totalIncomeCents <= 0L || results.isEmpty()) rejectTransfer()
                if (results.any { it.allocatedAmountCents < 0L }) rejectTransfer()
                if (results.any { it.allocatedAmountCents > 0L && it.rule.targetAccountId <= 0L }) rejectTransfer()

                val allocatedTotal = results.sumOf { it.allocatedAmountCents }
                if (allocatedTotal > totalIncomeCents) rejectTransfer()

                // 在任何写入前检查源账户和所有目标账户，避免规则失效后只完成部分分流。
                val sourceAccount = accountDao.getActiveAccountByIdSync(sourceAccountId) ?: rejectTransfer()
                results.asSequence()
                    .filter { it.allocatedAmountCents > 0L && it.rule.targetAccountId != sourceAccountId }
                    .map { it.rule.targetAccountId }
                    .distinct()
                    .forEach { targetId ->
                        accountDao.getActiveAccountByIdSync(targetId) ?: rejectTransfer()
                    }

                // “本次收入总额”必须先真正入账，否则零余额账户无法完成分配，
                // 且分配后的账户余额无法在流水中追溯到收入来源。
                val now = System.currentTimeMillis()
                var incomeCategory = categoryDao.getAllCategoriesList().firstOrNull {
                    it.type.equals("INCOME", ignoreCase = true) && it.name == "工资"
                }
                if (incomeCategory == null) {
                    val categoryId = categoryDao.insertCategory(
                        CategoryEntity(
                            name = "工资",
                            type = "INCOME",
                            iconName = "salary",
                            colorHex = 0xFF2E7D32L,
                            sortOrder = 1
                        )
                    )
                    incomeCategory = categoryDao.getCategoryById(categoryId)
                }
                val incomeCategoryId = incomeCategory?.id ?: rejectTransfer()
                val incomeDelta = if (AccountType.fromString(sourceAccount.type).isLiability) {
                    -totalIncomeCents
                } else {
                    totalIncomeCents
                }
                if (incomeDelta > 0L && sourceAccount.balanceCents > Long.MAX_VALUE - incomeDelta) rejectTransfer()
                if (incomeDelta < 0L && sourceAccount.balanceCents < Long.MIN_VALUE - incomeDelta) rejectTransfer()
                insertAccountRecordInTransaction(
                    record = RecordEntity(
                        type = "INCOME",
                        amount = totalIncomeCents,
                        categoryId = incomeCategoryId,
                        recordTime = now,
                        remark = "【收入分配】$remark",
                        paymentMethod = sourceAccount.name,
                        accountId = sourceAccountId,
                        createdAt = now,
                        updatedAt = now,
                        revision = 1L
                    ),
                    accountsById = mapOf(sourceAccountId to sourceAccount)
                )

                results.forEach { item ->
                    if (item.rule.targetAccountId > 0L &&
                        item.rule.targetAccountId != sourceAccountId &&
                        item.allocatedAmountCents > 0L
                    ) {
                        transferInTransaction(
                            fromAccountId = sourceAccountId,
                            toAccountId = item.rule.targetAccountId,
                            amountCents = item.allocatedAmountCents,
                            remark = if (item.rule.name.isBlank()) remark else "【收入分配】${item.rule.name}",
                            timestamp = now
                        )
                    }
                }
            }
            true
        } catch (_: TransferRejectedException) {
            false
        }
    }

    /**
     * 监听当前周期与上一周期的对比看板数据
     */
    fun observePeriodComparison(
        periodType: AccountPeriodType,
        startDay: Int = 1
    ): Flow<AccountPeriodComparison> {
        val currentPeriodInfo = AccountPeriodType.getPeriodInfo(
            timestamp = System.currentTimeMillis(),
            periodType = periodType,
            startDayOfMonth = startDay
        )

        return combine(
            accountDao.getActiveAccounts(),
            accountSnapshotDao.getSnapshotsByPeriodType(periodType.name, limit = 12)
        ) { accounts, snapshots ->
            val regularAsset = accounts
                .filter { it.includeInNetWorth && !AccountType.fromString(it.type).isLiability }
                .sumOf { it.balanceCents }

            val liabilitySurplus = accounts
                .filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents < 0 }
                .sumOf { -it.balanceCents }

            val totalAsset = regularAsset + liabilitySurplus

            val totalDebt = accounts
                .filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents > 0 }
                .sumOf { it.balanceCents }

            val netWorth = totalAsset - totalDebt

            val liquidAsset = accounts
                .filter { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.CHECKING }
                .sumOf { it.balanceCents.coerceAtLeast(0L) }

            val investmentAsset = accounts
                .filter { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.INVESTMENT }
                .sumOf { it.balanceCents.coerceAtLeast(0L) }

            val otherAsset = accounts
                .filter { it.includeInNetWorth && AccountType.fromString(it.type) == AccountType.ASSET }
                .sumOf { it.balanceCents.coerceAtLeast(0L) }

            val prevSnapshot = snapshots.firstOrNull { it.periodKey == currentPeriodInfo.prevPeriodKey }
            val currentSnapshot = snapshots.firstOrNull { it.periodKey == currentPeriodInfo.periodKey }

            val prevNetWorth = prevSnapshot?.netWorthCents ?: 0L
            val prevAsset = prevSnapshot?.totalAssetCents ?: 0L
            val prevDebt = prevSnapshot?.totalDebtCents ?: 0L

            val historySummaryList = snapshots.map {
                PeriodSnapshotSummary(
                    periodKey = it.periodKey,
                    periodName = it.periodKey,
                    periodType = AccountPeriodType.fromString(it.periodType),
                    totalAssetCents = it.totalAssetCents,
                    totalDebtCents = it.totalDebtCents,
                    netWorthCents = it.netWorthCents,
                    reconciledAt = it.reconciledAt
                )
            }

            AccountPeriodComparison(
                currentPeriod = currentPeriodInfo,
                currentNetWorthCents = netWorth,
                prevNetWorthCents = prevNetWorth,
                netWorthDiffCents = if (prevSnapshot != null) netWorth - prevNetWorth else 0L,
                netWorthDiffPercent = if (prevSnapshot != null && prevNetWorth > 0L) {
                    (netWorth - prevNetWorth).toFloat() / prevNetWorth * 100f
                } else null,
                totalAssetCents = totalAsset,
                prevTotalAssetCents = prevAsset,
                totalDebtCents = totalDebt,
                prevTotalDebtCents = prevDebt,
                liquidAssetCents = liquidAsset,
                investmentAssetCents = investmentAsset,
                creditDebtCents = totalDebt,
                otherAssetCents = otherAsset,
                hasReconciledInCurrentPeriod = currentSnapshot != null,
                historySnapshots = historySummaryList
            )
        }
    }

    fun getRecordsByAccountId(accountId: Long): Flow<List<RecordWithCategory>> {
        return recordDao.getRecordsByAccountId(accountId)
    }

    /**
     * Inserts an account-linked record and applies its balance effect exactly once.
     * Later edits/deletes/restores are handled by RecordRepository using the same
     * AccountRecordCalculator rules.
     */
    private suspend fun insertAccountRecordInTransaction(
        record: RecordEntity,
        accountsById: Map<Long, AccountEntity> = emptyMap()
    ): Long {
        val accountIds = listOfNotNull(record.accountId, record.targetAccountId).distinct()
        val accounts = accountIds.associateWith { accountId ->
            accountsById[accountId]
                ?: accountDao.getAccountByIdIncludingDeleted(accountId)
                ?: error("关联账户不存在：$accountId")
        }
        val id = recordDao.insertRecord(record)
        AccountRecordCalculator.changesFor(record, accounts).forEach { change ->
            check(accountDao.adjustBalance(change.accountId, change.deltaCents, record.updatedAt) == 1) {
                "关联账户不存在或已删除：${change.accountId}"
            }
        }
        return id
    }

    private suspend fun getOrCreateBalanceAdjustmentCategory(
        type: String,
        now: Long
    ): CategoryEntity {
        val name = "账户余额调整"
        val existing = categoryDao.getCategoryByNameIncludingDeleted(type, name)
        if (existing != null) {
            if (existing.deletedAt != null) {
                categoryDao.updateCategory(
                    existing.copy(
                        deletedAt = null,
                        updatedAt = now,
                        revision = existing.revision + 1L
                    )
                )
            }
            return categoryDao.getCategoryById(existing.id) ?: error("余额调整分类不可用")
        }

        val categoryId = categoryDao.insertCategory(
            CategoryEntity(
                name = name,
                type = type,
                iconName = "tune",
                colorHex = 0xFF64748BL,
                sortOrder = 97,
                createdAt = now,
                updatedAt = now
            )
        )
        return categoryDao.getCategoryById(categoryId) ?: error("余额调整分类创建失败")
    }

    private suspend fun calculateLedgerDeltas(
        accounts: List<AccountEntity>
    ): Map<Long, Long> {
        val accountsById = accounts.associateBy { it.id }
        val deltas = mutableMapOf<Long, Long>()
        recordDao.getAllRecordsDirect().forEach { item ->
            val record = item.record
            val linkedAccountIds = listOfNotNull(record.accountId, record.targetAccountId).distinct()
            check(linkedAccountIds.all(accountsById::containsKey)) {
                "关联账户不存在：${linkedAccountIds.first { it !in accountsById }}"
            }
            AccountRecordCalculator.changesFor(record, accountsById).forEach { change ->
                deltas[change.accountId] = Math.addExact(
                    deltas[change.accountId] ?: 0L,
                    change.deltaCents
                )
            }
        }
        return deltas
    }
}

data class AccountSyncMergeResult(
    val remoteToLocalAccountIds: Map<Long, Long>,
    val changedAccountCount: Int,
    val changedSnapshotCount: Int
)

data class AccountSyncSnapshot(
    val accounts: List<AccountEntity>,
    val accountSnapshots: List<AccountSnapshotEntity>
)
