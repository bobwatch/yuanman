package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountTransferCalculatorTest {

    @Test
    fun assetTransferReducesSourceAndIncreasesTarget() {
        val preview = AccountTransferCalculator.preview(
            account(id = 1L, type = AccountType.CHECKING, balanceCents = 10_000L),
            account(id = 2L, type = AccountType.INVESTMENT, balanceCents = 2_000L),
            amountCents = 3_000L
        )

        assertTrue(preview.isValid)
        assertEquals(7_000L, preview.fromAfterBalanceCents)
        assertEquals(5_000L, preview.toAfterBalanceCents)
    }

    @Test
    fun assetTransferCannotOverdrawSource() {
        val preview = AccountTransferCalculator.preview(
            account(id = 1L, type = AccountType.CHECKING, balanceCents = 2_000L),
            account(id = 2L, type = AccountType.INVESTMENT, balanceCents = 0L),
            amountCents = 3_000L
        )

        assertFalse(preview.isValid)
        assertEquals(AccountTransferError.INSUFFICIENT_FUNDS, preview.error)
        assertEquals(2_000L, preview.fromAfterBalanceCents)
    }

    @Test
    fun transferToCreditAccountIsRepayment() {
        val preview = AccountTransferCalculator.preview(
            account(id = 1L, type = AccountType.CHECKING, balanceCents = 10_000L),
            account(id = 2L, type = AccountType.CREDIT, balanceCents = 6_000L),
            amountCents = 2_000L
        )

        assertTrue(preview.isValid)
        assertEquals(8_000L, preview.fromAfterBalanceCents)
        assertEquals(4_000L, preview.toAfterBalanceCents)
    }

    @Test
    fun transferFromCreditAccountIncreasesDebt() {
        val preview = AccountTransferCalculator.preview(
            account(id = 1L, type = AccountType.CREDIT, balanceCents = 6_000L),
            account(id = 2L, type = AccountType.CHECKING, balanceCents = 0L),
            amountCents = 2_000L
        )

        assertTrue(preview.isValid)
        assertEquals(8_000L, preview.fromAfterBalanceCents)
        assertEquals(2_000L, preview.toAfterBalanceCents)
    }

    @Test
    fun repaymentCannotExceedCreditBalance() {
        val preview = AccountTransferCalculator.preview(
            account(id = 1L, type = AccountType.CHECKING, balanceCents = 10_000L),
            account(id = 2L, type = AccountType.CREDIT, balanceCents = 1_000L),
            amountCents = 2_000L
        )

        assertFalse(preview.isValid)
        assertEquals(AccountTransferError.EXCEEDS_CREDIT_BALANCE, preview.error)
        assertEquals(1_000L, preview.toAfterBalanceCents)
    }

    private fun account(id: Long, type: AccountType, balanceCents: Long) = AccountEntity(
        id = id,
        name = "账户$id",
        type = type.name,
        balanceCents = balanceCents
    )
}
