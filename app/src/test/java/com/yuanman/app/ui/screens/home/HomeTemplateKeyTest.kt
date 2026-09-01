package com.yuanman.app.ui.screens.home

import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.RecordEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class HomeTemplateKeyTest {
    private val category = CategoryEntity(1L, "餐饮美食", "EXPENSE", "food", 1L, syncId = "cat-a")

    @Test
    fun `template key ignores record identity and time`() {
        val first = item(id = 1L, amount = 1_800L, time = 100L)
        val second = item(id = 2L, amount = 1_800L, time = 200L)
        assertEquals(HomeViewModel.templateKey(first), HomeViewModel.templateKey(second))
    }

    @Test
    fun `template key changes when amount changes`() {
        assertNotEquals(HomeViewModel.templateKey(item(amount = 1_800L)), HomeViewModel.templateKey(item(amount = 2_000L)))
    }

    private fun item(id: Long = 1L, amount: Long, time: Long = 100L) = RecordWithCategory(
        record = RecordEntity(id, "EXPENSE", amount, 1L, time, "奶茶", "微信支付"),
        category = category
    )
}
