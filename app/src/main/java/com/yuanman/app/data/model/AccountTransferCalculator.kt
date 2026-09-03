package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity

enum class AccountTransferError {
    SAME_ACCOUNT,
    NON_POSITIVE_AMOUNT,
    INSUFFICIENT_FUNDS,
    EXCEEDS_CREDIT_BALANCE
}

data class AccountTransferPreview(
    val fromAfterBalanceCents: Long,
    val toAfterBalanceCents: Long,
    val error: AccountTransferError? = null
) {
    val isValid: Boolean
        get() = error == null
}

/**
 * 统一账户互转的余额语义：信用负债账户的余额表示待还欠款，转入是还款，转出是借款。
 */
object AccountTransferCalculator {

    fun preview(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amountCents: Long
    ): AccountTransferPreview {
        if (fromAccount.id == toAccount.id) {
            return invalid(fromAccount, toAccount, AccountTransferError.SAME_ACCOUNT)
        }
        if (amountCents <= 0L) {
            return invalid(fromAccount, toAccount, AccountTransferError.NON_POSITIVE_AMOUNT)
        }

        val fromIsLiability = AccountType.fromString(fromAccount.type).isLiability
        val toIsLiability = AccountType.fromString(toAccount.type).isLiability

        if (!fromIsLiability && fromAccount.balanceCents < amountCents) {
            return invalid(fromAccount, toAccount, AccountTransferError.INSUFFICIENT_FUNDS)
        }
        if (toIsLiability && toAccount.balanceCents < amountCents) {
            return invalid(fromAccount, toAccount, AccountTransferError.EXCEEDS_CREDIT_BALANCE)
        }

        val fromAfter = if (fromIsLiability) {
            fromAccount.balanceCents + amountCents
        } else {
            fromAccount.balanceCents - amountCents
        }
        val toAfter = if (toIsLiability) {
            toAccount.balanceCents - amountCents
        } else {
            toAccount.balanceCents + amountCents
        }
        return AccountTransferPreview(fromAfter, toAfter)
    }

    private fun invalid(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        error: AccountTransferError
    ) = AccountTransferPreview(
        fromAfterBalanceCents = fromAccount.balanceCents,
        toAfterBalanceCents = toAccount.balanceCents,
        error = error
    )
}
