package com.yuanman.app.data.repository

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.yuanman.app.data.local.AppDatabase
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.model.AccountType
import com.yuanman.app.data.model.AccountPeriodType
import com.yuanman.app.data.model.AccountReconciliationItem
import com.yuanman.app.data.model.IncomeAllocationCalculator
import com.yuanman.app.data.model.IncomeAllocationRule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountRepositoryTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: AccountRepository
    private lateinit var recordRepository: RecordRepository

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            database.categoryDao().insertCategories(AppDatabase.getDefaultCategories())
            database.accountDao().insertAll(
                listOf(
                    AccountEntity(id = 1L, name = "发薪账户", type = AccountType.CHECKING.name),
                    AccountEntity(id = 2L, name = "生活账户", type = AccountType.CHECKING.name),
                    AccountEntity(id = 3L, name = "储蓄账户", type = AccountType.ASSET.name)
                )
            )
        }
        repository = AccountRepository(
            database = database,
            accountDao = database.accountDao(),
            accountSnapshotDao = database.accountSnapshotDao(),
            recordDao = database.recordDao(),
            categoryDao = database.categoryDao(),
            context = context
        )
        recordRepository = RecordRepository(
            recordDao = database.recordDao(),
            context = context,
            database = database,
            accountDao = database.accountDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun incomeAllocationBooksIncomeBeforeTransfers() = runBlocking {
        val results = IncomeAllocationCalculator.calculate(
            totalIncomeCents = 100_000L,
            rules = listOf(
                IncomeAllocationRule(name = "生活", targetAccountId = 2L, percentage = 0.60f),
                IncomeAllocationRule(name = "储蓄", targetAccountId = 3L, percentage = 0.40f)
            )
        )

        assertTrue(repository.executeIncomeAllocation(1L, 100_000L, results))

        assertEquals(0L, database.accountDao().getActiveAccountByIdSync(1L)?.balanceCents)
        assertEquals(60_000L, database.accountDao().getActiveAccountByIdSync(2L)?.balanceCents)
        assertEquals(40_000L, database.accountDao().getActiveAccountByIdSync(3L)?.balanceCents)

        val records = database.recordDao().getAllRecordsDirect().map { it.record }
        assertEquals(3, records.size)
        assertEquals(1, records.count { it.type == "INCOME" && it.accountId == 1L && it.amount == 100_000L })
        assertEquals(2, records.count { it.type == "TRANSFER" && it.accountId == 1L })
    }

    @Test
    fun generatedTransferCanBeDeletedAndRestoredWithoutDoubleApplyingBalance() = runBlocking {
        database.accountDao().updateBalance(1L, 100_000L)

        assertTrue(repository.transfer(1L, 2L, 10_000L, remark = "测试转账", timestamp = 2L))
        assertEquals(90_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        assertEquals(10_000L, database.accountDao().getAccountByIdSync(2L)?.balanceCents)

        val transferId = database.recordDao().getAllRecordsDirect().single().record.id
        recordRepository.deleteRecordById(transferId)
        assertEquals(100_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        assertEquals(0L, database.accountDao().getAccountByIdSync(2L)?.balanceCents)

        recordRepository.restoreRecord(transferId)
        assertEquals(90_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        assertEquals(10_000L, database.accountDao().getAccountByIdSync(2L)?.balanceCents)
    }

    @Test
    fun reconciliationAdjustmentCanBeDeletedAndRestoredWithoutBalanceDrift() = runBlocking {
        database.accountDao().updateBalance(1L, 100_000L)
        val account = database.accountDao().getAccountByIdSync(1L)!!
        val period = AccountPeriodType.getPeriodInfo(
            timestamp = 1_700_000_000_000L,
            periodType = AccountPeriodType.MONTH
        )

        repository.executeReconciliation(
            periodInfo = period,
            items = listOf(
                AccountReconciliationItem(
                    account = account,
                    bookBalanceCents = 100_000L,
                    actualBalanceCents = 110_000L
                )
            )
        )

        assertEquals(110_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        val adjustment = database.recordDao().getAllRecordsDirect().single().record
        assertEquals("INCOME", adjustment.type)
        assertEquals(10_000L, adjustment.amount)

        recordRepository.deleteRecordById(adjustment.id)
        assertEquals(100_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)

        recordRepository.restoreRecord(adjustment.id)
        assertEquals(110_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
    }

    @Test
    fun reconciliationUsesExpenseWhenCreditDebtIncreases() = runBlocking {
        database.accountDao().insert(
            AccountEntity(
                id = 20L,
                name = "信用卡",
                type = AccountType.CREDIT.name,
                balanceCents = 50_000L,
                initialBalanceCents = 50_000L
            )
        )
        val account = database.accountDao().getAccountByIdSync(20L)!!
        val period = AccountPeriodType.getPeriodInfo(
            timestamp = 1_700_000_000_000L,
            periodType = AccountPeriodType.MONTH
        )

        repository.executeReconciliation(
            periodInfo = period,
            items = listOf(
                AccountReconciliationItem(
                    account = account,
                    bookBalanceCents = 50_000L,
                    actualBalanceCents = 65_000L
                )
            )
        )

        assertEquals(65_000L, database.accountDao().getAccountByIdSync(20L)?.balanceCents)
        val adjustment = database.recordDao().getAllRecordsDirect().single().record
        assertEquals("EXPENSE", adjustment.type)
        assertEquals(15_000L, adjustment.amount)
    }

    @Test
    fun regularRecordKeepsAccountBalanceInSyncAcrossEditDeleteAndRestore() = runBlocking {
        database.accountDao().insert(
            AccountEntity(
                id = 10L,
                name = "日常账户",
                type = AccountType.CHECKING.name,
                balanceCents = 100_000L,
                initialBalanceCents = 100_000L
            )
        )
        database.accountDao().insert(
            AccountEntity(
                id = 11L,
                name = "备用账户",
                type = AccountType.CHECKING.name,
                balanceCents = 100_000L,
                initialBalanceCents = 100_000L
            )
        )
        val categoryId = database.categoryDao().getAllCategoriesList().first { it.type == "EXPENSE" }.id

        val recordId = recordRepository.insertRecord(
            RecordEntity(
                type = "EXPENSE",
                amount = 2_000L,
                categoryId = categoryId,
                recordTime = 1L,
                accountId = 10L
            )
        )
        assertEquals(98_000L, database.accountDao().getAccountByIdSync(10L)?.balanceCents)

        val inserted = database.recordDao().getRecordEntityByIdIncludingDeleted(recordId)!!
        recordRepository.updateRecord(inserted.copy(amount = 5_000L, accountId = 11L))
        assertEquals(100_000L, database.accountDao().getAccountByIdSync(10L)?.balanceCents)
        assertEquals(95_000L, database.accountDao().getAccountByIdSync(11L)?.balanceCents)

        recordRepository.deleteRecordById(recordId)
        assertEquals(100_000L, database.accountDao().getAccountByIdSync(11L)?.balanceCents)

        recordRepository.restoreRecord(recordId)
        assertEquals(95_000L, database.accountDao().getAccountByIdSync(11L)?.balanceCents)
    }

    @Test
    fun editingAccountBalanceCreatesReversibleAdjustmentRecord() = runBlocking {
        database.accountDao().updateBalance(1L, 100_000L)
        val account = database.accountDao().getAccountByIdSync(1L)!!

        repository.updateAccount(
            account = account.copy(balanceCents = 125_000L, name = "发薪账户（已校准）"),
            balanceAdjustmentRemark = "银行 App 对账"
        )

        assertEquals(125_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        val adjustment = database.recordDao().getAllRecordsDirect().single().record
        assertEquals("INCOME", adjustment.type)
        assertEquals(25_000L, adjustment.amount)
        assertTrue(adjustment.isAdjustment)
        assertTrue(adjustment.remark.contains("银行 App 对账"))

        recordRepository.deleteRecordById(adjustment.id)
        assertEquals(100_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)

        recordRepository.restoreRecord(adjustment.id)
        assertEquals(125_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
    }

    @Test
    fun syncMergeRebuildsBalanceFromBothDevicesLedger() = runBlocking {
        val localAccount = database.accountDao().getAccountByIdSync(1L)!!
        val localCategory = database.categoryDao().getAllCategoriesList().first { it.type == "INCOME" }

        recordRepository.insertRecord(
            RecordEntity(
                type = "INCOME",
                amount = 100_000L,
                categoryId = localCategory.id,
                recordTime = 1L,
                accountId = localAccount.id,
                syncId = "device-a-income"
            )
        )
        val balanceBaselines = repository.captureBalanceBaselinesInTransaction()

        val remoteAccount = localAccount.copy(
            id = 101L,
            balanceCents = 50_000L,
            revision = localAccount.revision + 10L,
            updatedAt = localAccount.updatedAt + 10L
        )
        val remoteCategory = localCategory.copy(id = 501L)
        val remoteRecord = RecordEntity(
            id = 601L,
            type = "EXPENSE",
            amount = 20_000L,
            categoryId = remoteCategory.id,
            recordTime = 2L,
            accountId = remoteAccount.id,
            createdAt = 2L,
            updatedAt = 2L,
            revision = 1L,
            syncId = "device-b-expense"
        )
        val categoryRepository = CategoryRepository(
            categoryDao = database.categoryDao(),
            recordDao = database.recordDao(),
            syncDao = database.syncDao(),
            quickEntryLearningDao = database.quickEntryLearningDao(),
            database = database
        )

        database.withTransaction {
            val accountMerge = repository.mergeSyncedAccountsInTransaction(
                remoteAccounts = listOf(remoteAccount),
                remoteSnapshots = emptyList()
            )
            val remappedRecords = repository.remapRecordAccountIds(
                records = listOf(remoteRecord),
                remoteToLocalAccountIds = accountMerge.remoteToLocalAccountIds,
                includesAccounts = true
            )
            categoryRepository.mergeSyncedDataInTransaction(
                categories = listOf(remoteCategory),
                records = remappedRecords
            )
            repository.recalculateBalancesInTransaction(balanceBaselines)
        }

        assertEquals(80_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
        repository.recalculateBalancesInTransaction(balanceBaselines)
        assertEquals(80_000L, database.accountDao().getAccountByIdSync(1L)?.balanceCents)
    }

    @Test
    fun backupAccountIdsAreMappedBeforeRestoringLinkedRecords() = runBlocking {
        val remoteAccount = AccountEntity(
            id = 101L,
            syncId = "backup-account",
            name = "异设备账户",
            type = AccountType.CHECKING.name,
            balanceCents = 42_000L,
            initialBalanceCents = 42_000L,
            revision = 3L
        )
        val remoteSnapshot = com.yuanman.app.data.local.entity.AccountSnapshotEntity(
            id = 202L,
            syncId = "backup-snapshot",
            periodKey = "2026-08",
            periodType = AccountPeriodType.MONTH.name,
            snapshotDataJson = "[{\"accountId\":101,\"actualBalanceCents\":42000}]"
        )

        val merge = repository.mergeSyncedAccounts(listOf(remoteAccount), listOf(remoteSnapshot))
        val localAccountId = merge.remoteToLocalAccountIds.getValue(101L)
        assertTrue(localAccountId != 101L)

        val remapped = repository.remapRecordAccountIds(
            records = listOf(
                RecordEntity(
                    id = 303L,
                    type = "EXPENSE",
                    amount = 1_000L,
                    categoryId = database.categoryDao().getAllCategoriesList().first().id,
                    recordTime = 1L,
                    accountId = 101L
                )
            ),
            remoteToLocalAccountIds = merge.remoteToLocalAccountIds,
            includesAccounts = true
        )

        assertEquals(localAccountId, remapped.single().accountId)
        assertTrue(database.accountSnapshotDao().getAllForSync().single().snapshotDataJson.contains("\"accountId\":$localAccountId"))
    }

    @Test
    fun crossTableSyncMergeRollsBackAccountsWhenLaterLedgerMergeFails() = runBlocking {
        val categoryRepository = CategoryRepository(
            categoryDao = database.categoryDao(),
            recordDao = database.recordDao(),
            syncDao = database.syncDao(),
            quickEntryLearningDao = database.quickEntryLearningDao(),
            database = database
        )
        val remoteAccount = AccountEntity(
            id = 101L,
            syncId = "atomic-account",
            name = "同步账户",
            type = AccountType.CHECKING.name,
            balanceCents = 42_000L
        )
        val remoteCategory = CategoryEntity(
            id = 501L,
            syncId = "atomic-category",
            name = "同步分类",
            type = "EXPENSE",
            iconName = "other",
            colorHex = 1L
        )
        val remoteRecord = RecordEntity(
            id = 601L,
            syncId = "atomic-record",
            type = "EXPENSE",
            amount = 1_000L,
            categoryId = remoteCategory.id,
            recordTime = 1L,
            accountId = remoteAccount.id
        )

        val failure = runCatching {
            database.withTransaction {
                val accountMerge = repository.mergeSyncedAccountsInTransaction(
                    remoteAccounts = listOf(remoteAccount),
                    remoteSnapshots = emptyList()
                )
                val remappedRecords = repository.remapRecordAccountIds(
                    records = listOf(remoteRecord),
                    remoteToLocalAccountIds = accountMerge.remoteToLocalAccountIds,
                    includesAccounts = true
                )
                categoryRepository.mergeSyncedDataInTransaction(
                    categories = listOf(remoteCategory),
                    records = remappedRecords
                )
                error("模拟账本合并后的提交失败")
            }
        }

        assertTrue(failure.isFailure)
        assertTrue(database.accountDao().getAllForSync().none { it.syncId == remoteAccount.syncId })
        assertTrue(database.categoryDao().getAllCategoriesList().none { it.syncId == remoteCategory.syncId })
        assertTrue(database.recordDao().getAllRecordsDirect().none { it.record.syncId == remoteRecord.syncId })
    }

    @Test
    fun syncWithUnmatchedRecordCategoryRollsBackAccountMerge() = runBlocking {
        val categoryRepository = CategoryRepository(
            categoryDao = database.categoryDao(),
            recordDao = database.recordDao(),
            syncDao = database.syncDao(),
            quickEntryLearningDao = database.quickEntryLearningDao(),
            database = database
        )
        val remoteAccount = AccountEntity(
            id = 101L,
            syncId = "unmatched-category-account",
            name = "待回滚账户",
            type = AccountType.CHECKING.name,
            balanceCents = 42_000L
        )
        val remoteRecord = RecordEntity(
            id = 601L,
            syncId = "unmatched-category-record",
            type = "EXPENSE",
            amount = 1_000L,
            categoryId = 999_999L,
            recordTime = 1L,
            accountId = remoteAccount.id
        )

        val failure = runCatching {
            database.withTransaction {
                val accountMerge = repository.mergeSyncedAccountsInTransaction(
                    remoteAccounts = listOf(remoteAccount),
                    remoteSnapshots = emptyList()
                )
                val remappedRecords = repository.remapRecordAccountIds(
                    records = listOf(remoteRecord),
                    remoteToLocalAccountIds = accountMerge.remoteToLocalAccountIds,
                    includesAccounts = true
                )
                categoryRepository.mergeSyncedDataInTransaction(
                    categories = emptyList(),
                    records = remappedRecords
                )
            }
        }

        assertTrue(failure.isFailure)
        assertTrue(database.accountDao().getAllForSync().none { it.syncId == remoteAccount.syncId })
        assertTrue(database.recordDao().getAllRecordsDirect().none { it.record.syncId == remoteRecord.syncId })
    }
}
