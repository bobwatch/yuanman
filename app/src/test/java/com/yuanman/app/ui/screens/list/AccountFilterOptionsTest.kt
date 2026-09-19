package com.yuanman.app.ui.screens.list

import com.yuanman.app.ui.screens.account.AccountUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「全部账户」筛选项的归类规则：归类错位会让用户选了账户却看不到自己的账单，
 * 或把别家账户的账单混进来；暂无账单的账户还必须点得动（筛出空结果而不是退化成不筛选）。
 */
class AccountFilterOptionsTest {

    private fun account(id: Long, name: String, sortOrder: Int, icon: String = "bank") = AccountUiModel(
        id = id,
        name = name,
        label = "银行卡",
        iconName = icon,
        colorHex = 0xFF059669L,
        openingBalanceCents = 0L,
        balanceCents = 100_00L,
        sortOrder = sortOrder
    )

    private val accounts = listOf(
        account(1L, "中国银行", sortOrder = 1),
        account(2L, "微信", sortOrder = 2, icon = "wechat"),
        account(3L, "支付宝", sortOrder = 3, icon = "alipay"),
        account(4L, "备用金", sortOrder = 4)
    )

    @Test
    fun `账户名精确命中归到该账户`() {
        val result = buildAccountFilterOptions(accounts, listOf("中国银行", "微信"))
        assertEquals(listOf("中国银行"), result.options.first { it.accountId == 1L }.filterMethods)
        assertEquals(listOf("微信"), result.options.first { it.accountId == 2L }.filterMethods)
        assertTrue(result.unmatchedMethods.isEmpty())
    }

    @Test
    fun `历史支付方式写法按渠道归到对应账户`() {
        // 「微信支付」「微信零钱」都应落在「微信」账户名下
        val result = buildAccountFilterOptions(accounts, listOf("微信支付", "微信零钱"))
        val wechat = result.options.first { it.accountId == 2L }
        assertEquals(listOf("微信支付", "微信零钱"), wechat.filterMethods)
        assertTrue(result.unmatchedMethods.isEmpty())
    }

    @Test
    fun `全部账户都会列出`() {
        val result = buildAccountFilterOptions(accounts, listOf("中国银行"))
        assertEquals(listOf(1L, 2L, 3L, 4L), result.options.map { it.accountId })
    }

    @Test
    fun `暂无账单的账户用占位值保证点得动且不退化`() {
        val result = buildAccountFilterOptions(accounts, listOf("中国银行"))
        val empty = result.options.first { it.accountId == 3L }
        assertEquals(listOf(ACCOUNT_FILTER_NO_MATCH), empty.filterMethods)
        // 占位值绝不能被误判成"已选中"状态之外的任何真实方式
        assertFalse(ACCOUNT_FILTER_NO_MATCH in listOf("中国银行", "支付宝", "微信"))
    }

    @Test
    fun `未归属的支付方式进入其它方式`() {
        val result = buildAccountFilterOptions(accounts, listOf("中国银行", "现金", "其他"))
        assertEquals(setOf("现金", "其他"), result.unmatchedMethods.toSet())
    }

    @Test
    fun `筛选项按账户排序给出`() {
        val ordered = listOf(
            account(3L, "支付宝", sortOrder = 3, icon = "alipay"),
            account(1L, "中国银行", sortOrder = 1),
            account(2L, "微信", sortOrder = 2, icon = "wechat")
        )
        val result = buildAccountFilterOptions(ordered, listOf("微信", "中国银行", "支付宝"))
        assertEquals(listOf(1L, 2L, 3L), result.options.map { it.accountId })
    }

    @Test
    fun `空方式与纯空白被忽略`() {
        val result = buildAccountFilterOptions(accounts, listOf("", "   ", "中国银行 "))
        assertEquals(listOf("中国银行"), result.options.first { it.accountId == 1L }.filterMethods)
        assertTrue(result.unmatchedMethods.isEmpty())
    }

    @Test
    fun `没有账户时全部分到其它方式`() {
        val result = buildAccountFilterOptions(emptyList(), listOf("微信支付", "现金"))
        assertTrue(result.options.isEmpty())
        assertEquals(listOf("微信支付", "现金"), result.unmatchedMethods)
    }

    @Test
    fun `没有任何账单时账户仍可选且标记为空结果`() {
        val result = buildAccountFilterOptions(accounts, emptyList())
        assertEquals(accounts.size, result.options.size)
        assertTrue(result.options.all { it.filterMethods == listOf(ACCOUNT_FILTER_NO_MATCH) })
        assertTrue(result.unmatchedMethods.isEmpty())
    }
}
