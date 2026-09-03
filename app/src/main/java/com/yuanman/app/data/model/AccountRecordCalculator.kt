package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.local.entity.RecordEntity

/**
 * Calculates the balance changes caused by a record.
 *
 * Liability accounts store the outstanding debt as a positive number, so an
 * expense increases the balance while an income or repayment decreases it.
 */
object AccountRecordCalculator {
    /**
     * Returns the record type that represents a reconciliation delta.
     *
     * Asset balances grow with income, while liability balances store the
     * outstanding debt and therefore grow with expense.
     */
    fun reconciliationTypeFor(account: AccountEntity, diffCents: Long): String? {
        if (diffCents == 0L) return null
        val isLiability = AccountType.fromString(account.type).isLiability
        return when {
            isLiability && diffCents > 0L -> RecordType.EXPENSE.name
            isLiability -> RecordType.INCOME.name
            diffCents > 0L -> RecordType.INCOME.name
            else -> RecordType.EXPENSE.name
        }
    }

    fun changesFor(
        record: RecordEntity,
        accountsById: Map<Long, AccountEntity>
    ): List<AccountBalanceChange> {
        if (record.amount <= 0L) return emptyList()

        return when (record.type.uppercase()) {
            RecordType.EXPENSE.name,
            RecordType.INCOME.name -> {
                record.accountId?.let { accountId ->
                    accountsById[accountId]?.let { account ->
                        listOf(AccountBalanceChange(accountId, regularDelta(account, record.type, record.amount)))
                    }
                }.orEmpty()
            }

            "TRANSFER" -> {
                val from = record.accountId?.let(accountsById::get)
                val to = record.targetAccountId?.let(accountsById::get)
                buildList {
                    from?.let {
                        add(
                            AccountBalanceChange(
                                accountId = it.id,
                                deltaCents = if (it.isLiability()) record.amount else -record.amount
                            )
                        )
                    }
                    to?.let {
                        add(
                            AccountBalanceChange(
                                accountId = it.id,
                                deltaCents = if (it.isLiability()) -record.amount else record.amount
                            )
                        )
                    }
                }
            }

            else -> emptyList()
        }
    }

    private fun regularDelta(account: AccountEntity, type: String, amountCents: Long): Long {
        val isIncome = type.equals(RecordType.INCOME.name, ignoreCase = true)
        return if (account.isLiability()) {
            if (isIncome) -amountCents else amountCents
        } else {
            if (isIncome) amountCents else -amountCents
        }
    }

    private fun AccountEntity.isLiability(): Boolean = AccountType.fromString(type).isLiability
}

data class AccountBalanceChange(
    val accountId: Long,
    val deltaCents: Long
)
