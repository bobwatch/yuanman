package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountRecordCalculatorTest {
    private val checking = AccountEntity(id = 1L, name = "现金", type = AccountType.CHECKING.name)
    private val credit = AccountEntity(id = 2L, name = "信用卡", type = AccountType.CREDIT.name)

    @Test
    fun regularExpenseDecreasesAssetAndIncreasesDebt() {
        val expense = RecordEntity(type = "EXPENSE", amount = 1_800L, categoryId = 1L, recordTime = 1L, accountId = 1L)
        val debtExpense = expense.copy(accountId = 2L)

        assertEquals(-1_800L, AccountRecordCalculator.changesFor(expense, mapOf(1L to checking)).single().deltaCents)
        assertEquals(1_800L, AccountRecordCalculator.changesFor(debtExpense, mapOf(2L to credit)).single().deltaCents)
    }

    @Test
    fun regularIncomeIncreasesAssetAndPaysDownDebt() {
        val income = RecordEntity(type = "INCOME", amount = 5_000L, categoryId = 1L, recordTime = 1L, accountId = 1L)
        val debtIncome = income.copy(accountId = 2L)

        assertEquals(5_000L, AccountRecordCalculator.changesFor(income, mapOf(1L to checking)).single().deltaCents)
        assertEquals(-5_000L, AccountRecordCalculator.changesFor(debtIncome, mapOf(2L to credit)).single().deltaCents)
    }

    @Test
    fun reconciliationTypeUsesLiabilityAwareDirection() {
        assertEquals("INCOME", AccountRecordCalculator.reconciliationTypeFor(checking, 2_000L))
        assertEquals("EXPENSE", AccountRecordCalculator.reconciliationTypeFor(checking, -2_000L))
        assertEquals("EXPENSE", AccountRecordCalculator.reconciliationTypeFor(credit, 2_000L))
        assertEquals("INCOME", AccountRecordCalculator.reconciliationTypeFor(credit, -2_000L))
    }

    @Test
    fun transferChangesBothSidesWithLiabilitySemantics() {
        val transfer = RecordEntity(
            type = "TRANSFER",
            amount = 10_000L,
            categoryId = 1L,
            recordTime = 1L,
            accountId = checking.id,
            targetAccountId = credit.id
        )

        val changes = AccountRecordCalculator.changesFor(transfer, mapOf(1L to checking, 2L to credit))

        assertEquals(
            listOf(
                AccountBalanceChange(checking.id, -10_000L),
                AccountBalanceChange(credit.id, -10_000L)
            ),
            changes
        )
    }
}
