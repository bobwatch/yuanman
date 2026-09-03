package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.AccountEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

class AccountsBusinessLogicTest {

    @Test
    fun netWorth_sumsAllAssetsAndSubtractsLiabilities() {
        val checking = createAccount(1L, AccountType.CHECKING, 10_000_00L, true) // +10,000
        val savings = createAccount(2L, AccountType.ASSET, 50_000_00L, true)   // +50,000
        val investment = createAccount(3L, AccountType.INVESTMENT, 20_000_00L, true) // +20,000
        val creditCard = createAccount(4L, AccountType.CREDIT, 5_000_00L, true) // -5,000 debt

        val accounts = listOf(checking, savings, investment, creditCard)

        val totalAsset = accounts.filter { !AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { it.balanceCents }
        val totalLiability = accounts.filter { AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { abs(it.balanceCents) }
        val netWorth = totalAsset - totalLiability

        assertEquals(80_000_00L, totalAsset)
        assertEquals(5_000_00L, totalLiability)
        assertEquals(75_000_00L, netWorth)
    }

    @Test
    fun netWorth_excludesAccountsWithIncludeInNetWorthFalse() {
        val checking = createAccount(1L, AccountType.CHECKING, 10_000_00L, true)
        val hiddenFund = createAccount(2L, AccountType.ASSET, 999_999_00L, false) // Excluded!
        val creditCard = createAccount(3L, AccountType.CREDIT, 2_000_00L, true)

        val accounts = listOf(checking, hiddenFund, creditCard)

        val totalAsset = accounts.filter { !AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { it.balanceCents }
        val totalLiability = accounts.filter { AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { abs(it.balanceCents) }
        val netWorth = totalAsset - totalLiability

        assertEquals(10_000_00L, totalAsset)
        assertEquals(2_000_00L, totalLiability)
        assertEquals(8_000_00L, netWorth)
    }

    @Test
    fun netWorth_handlesNegativeAssetBalancesCorrectly() {
        val overdrawnChecking = createAccount(1L, AccountType.CHECKING, -500_00L, true) // -500
        val savings = createAccount(2L, AccountType.ASSET, 10_000_00L, true) // +10000

        val accounts = listOf(overdrawnChecking, savings)

        val totalAsset = accounts.filter { !AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { it.balanceCents }
        val totalLiability = accounts.filter { AccountType.fromString(it.type).isLiability && it.includeInNetWorth }
            .sumOf { it.balanceCents.coerceAtLeast(0L) }
        val netWorth = totalAsset - totalLiability

        assertEquals(9_500_00L, totalAsset)
        assertEquals(0L, totalLiability)
        assertEquals(9_500_00L, netWorth)
    }

    @Test
    fun netWorth_handlesLiabilityOverpaymentCorrectly() {
        val checking = createAccount(1L, AccountType.CHECKING, 10_000_00L, true)
        // 信用卡溢缴款 -500 元（多还了），应当视作资产增量，而非负债
        val creditCardOverpaid = createAccount(2L, AccountType.CREDIT, -500_00L, true)

        val accounts = listOf(checking, creditCardOverpaid)

        val regularAsset = accounts
            .filter { it.includeInNetWorth && !AccountType.fromString(it.type).isLiability }
            .sumOf { it.balanceCents }
        val liabilitySurplus = accounts
            .filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents < 0 }
            .sumOf { -it.balanceCents }
        val totalAsset = regularAsset + liabilitySurplus
        val totalLiability = accounts
            .filter { it.includeInNetWorth && AccountType.fromString(it.type).isLiability && it.balanceCents > 0 }
            .sumOf { it.balanceCents }
        val netWorth = totalAsset - totalLiability

        assertEquals(10_500_00L, totalAsset)
        assertEquals(0L, totalLiability)
        assertEquals(10_500_00L, netWorth)
    }

    @Test
    fun periodRange_supportsFull1To28StartDays() {
        val cal = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 15, 12, 0, 0)
        }
        val timestamp = cal.timeInMillis

        for (day in 1..28) {
            val periodInfo = AccountPeriodType.getPeriodInfo(
                timestamp = timestamp,
                periodType = AccountPeriodType.MONTH,
                startDayOfMonth = day
            )

            assertTrue("Period end should be after start for day $day", periodInfo.endTimestamp >= periodInfo.startTimestamp)
            assertTrue("Period key should not be blank", periodInfo.periodKey.isNotBlank())
            assertTrue("Period name should not be blank", periodInfo.periodName.isNotBlank())
        }
    }

    @Test
    fun paymentMethod_matchesExistingAccountByName() {
        val alipayAccount = createAccount(101L, AccountType.CHECKING, 5000_00L, true).copy(name = "支付宝")
        val wechatAccount = createAccount(102L, AccountType.CHECKING, 3000_00L, true).copy(name = "微信钱包")
        val accounts = listOf(alipayAccount, wechatAccount)

        val parsedMethodAlipay = "支付宝"
        val matchedAlipay = accounts.firstOrNull { acc ->
            acc.name.contains(parsedMethodAlipay, ignoreCase = true) || parsedMethodAlipay.contains(acc.name, ignoreCase = true)
        }
        assertEquals(101L, matchedAlipay?.id)

        val parsedMethodWechat = "微信支付"
        val matchedWechat = accounts.firstOrNull { acc ->
            acc.name.take(2) == parsedMethodWechat.take(2)
        }
        assertEquals(102L, matchedWechat?.id)
    }

    @Test
    fun defaultExpenseAccount_resolutionLogic() {
        val defaultExpenseAccountId = 102L

        // Case 1: 用户手动指定了账户，以手动选择为准
        val userSelectedAccountId: Long? = 101L
        val resolvedWithUserSelect = if (userSelectedAccountId == -1L) null else userSelectedAccountId ?: defaultExpenseAccountId
        assertEquals(101L, resolvedWithUserSelect)

        // Case 2: 用户显式选择不关联账户 (-1L)，应解析为 null
        val userUnassignedAccountId: Long? = -1L
        val resolvedWithExplicitNone = if (userUnassignedAccountId == -1L) null else userUnassignedAccountId ?: defaultExpenseAccountId
        assertEquals(null, resolvedWithExplicitNone)

        // Case 3: 用户未指定账户且未输入支付方式，自动回退默认支出账户
        val noUserSelectAccountId: Long? = null
        val resolvedFallback = if (noUserSelectAccountId == -1L) null else noUserSelectAccountId ?: defaultExpenseAccountId
        assertEquals(102L, resolvedFallback)
    }

    private fun createAccount(id: Long, type: AccountType, balanceCents: Long, includeInNetWorth: Boolean) = AccountEntity(
        id = id,
        name = "测试账户$id",
        type = type.name,
        balanceCents = balanceCents,
        includeInNetWorth = includeInNetWorth
    )
}
