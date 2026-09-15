package com.yuanman.app.ui.screens.account

import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountDataCodecTest {

    @Test
    fun testTwoBankCardsDoNotDuplicateDeduction() {
        val account1 = AccountUiModel(
            id = 1L,
            name = "招商银行卡",
            label = "银行卡",
            iconName = "bank_card",
            colorHex = 0xFF2196F3L,
            openingBalanceCents = 1000000L,
            balanceCents = 1000000L,
            sortOrder = 1
        )
        val account2 = AccountUiModel(
            id = 2L,
            name = "工商银行卡",
            label = "银行卡",
            iconName = "bank_card",
            colorHex = 0xFFE91E63L,
            openingBalanceCents = 1000000L,
            balanceCents = 1000000L,
            sortOrder = 2
        )
        val accounts = listOf(account1, account2)

        val expenseRecord = RecordWithCategory(
            record = RecordEntity(
                id = 101L,
                type = "EXPENSE",
                amount = 520000L,
                categoryId = 1L,
                recordTime = System.currentTimeMillis(),
                paymentMethod = "招商银行卡"
            ),
            category = null
        )

        val enriched = enrichAccountsForMonth(
            rawAccounts = accounts,
            monthRecords = listOf(expenseRecord),
            globalReconcileCycle = ReconcileCycle.DEFAULT
        )

        val enrichedAccount1 = enriched.first { it.id == 1L }
        val enrichedAccount2 = enriched.first { it.id == 2L }

        assertEquals(520000L, enrichedAccount1.outCents)
        assertEquals(480000L, enrichedAccount1.balanceCents)

        assertEquals(0L, enrichedAccount2.outCents)
        assertEquals(1000000L, enrichedAccount2.balanceCents)
    }

    @Test
    fun testFindAccountForMethodPrecision() {
        val account1 = AccountUiModel(id = 1L, name = "招商银行卡", label = "", iconName = "bank_card", colorHex = 0L, openingBalanceCents = 0L, balanceCents = 0L, sortOrder = 1)
        val account2 = AccountUiModel(id = 2L, name = "工商银行卡", label = "", iconName = "bank_card", colorHex = 0L, openingBalanceCents = 0L, balanceCents = 0L, sortOrder = 2)
        val accounts = listOf(account1, account2)

        val match1 = findAccountForMethod("招商银行卡", accounts)
        assertEquals(1L, match1?.id)

        val match2 = findAccountForMethod("工商银行卡", accounts)
        assertEquals(2L, match2?.id)

        val matchAmbiguous = findAccountForMethod("银行卡", accounts)
        assertNull(matchAmbiguous)
    }

    @Test
    fun `已下架品牌图标的历史账户读取时归一化`() {
        val json = """
            [
              {"id":1,"name":"我的淘宝卡","iconName":"taobao","colorHex":${0xFF128564L}},
              {"id":2,"name":"百度钱包","iconName":"baidu","colorHex":${0xFF128564L}},
              {"id":3,"name":"微信零钱","iconName":"wechat","colorHex":${0xFF07C160L}}
            ]
        """.trimIndent()

        val accounts = parseAccountsJson(json)

        assertEquals("shopping_card", accounts.first { it.id == 1L }.iconName)
        assertEquals("wallet", accounts.first { it.id == 2L }.iconName)
        assertEquals("wechat", accounts.first { it.id == 3L }.iconName)
    }
}
