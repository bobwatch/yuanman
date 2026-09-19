package com.yuanman.app.ui.screens.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 计划展示顺序与折叠挑选：规则错位会让用户自己置顶的计划消失、
 * 或把「余额低于专款」的警示藏到折叠区外，所以逐条锁定。
 */
class SavingPlanOrderingTest {

    private fun plan(id: Long, sortOrder: Int, pinned: Boolean = false) = SavingPlanUiModel(
        id = id,
        name = "计划$id",
        holderAccountId = 1L,
        targetAmountCents = 1000L,
        earmarkedCents = 0L,
        colorHex = 0xFF059669L,
        sortOrder = sortOrder,
        createdAt = id,
        isPinned = pinned
    )

    @Test
    fun `展示顺序为置顶优先其余按创建顺序`() {
        val plans = listOf(plan(1, 1), plan(2, 2, pinned = true), plan(3, 3))
        assertEquals(listOf(2L, 1L, 3L), plans.inDisplayOrder().map { it.id })
    }

    @Test
    fun `多个置顶之间仍按创建顺序`() {
        val plans = listOf(plan(1, 5, pinned = true), plan(2, 2, pinned = true), plan(3, 1))
        assertEquals(listOf(2L, 1L, 3L), plans.inDisplayOrder().map { it.id })
    }

    @Test
    fun `数量未超上限时不做折叠`() {
        val plans = listOf(plan(1, 1), plan(2, 2), plan(3, 3), plan(4, 4))
        assertEquals(plans.map { it.id }, selectVisiblePlans(plans, emptySet(), 4).map { it.id })
    }

    @Test
    fun `超出上限时按顺序保留前几个`() {
        val plans = (1L..6L).map { plan(it, it.toInt()) }
        assertEquals(listOf(1L, 2L, 3L, 4L), selectVisiblePlans(plans, emptySet(), 4).map { it.id })
    }

    @Test
    fun `超额警示的计划一定进可见区且不打乱原顺序`() {
        val plans = (1L..6L).map { plan(it, it.toInt()) }
        // 第 6 个超额：它挤进可见区，其余名额留给靠前的计划，最后的 4、5 被折叠
        val visible = selectVisiblePlans(plans, setOf(6L), 4)
        assertEquals(listOf(1L, 2L, 3L, 6L), visible.map { it.id })
        assertTrue(visible.any { it.id == 6L })
    }

    @Test
    fun `上限为零时不展示任何计划`() {
        val plans = listOf(plan(1, 1))
        assertTrue(selectVisiblePlans(plans, emptySet(), 0).isEmpty())
    }

    @Test
    fun `置顶标记随 JSON 往返保留`() {
        val plans = listOf(plan(1, 1, pinned = true), plan(2, 2))
        val restored = parseSavingPlans(serializeSavingPlans(plans))
        assertTrue(restored.first { it.id == 1L }.isPinned)
        assertFalse(restored.first { it.id == 2L }.isPinned)
    }

    @Test
    fun `老数据没有置顶字段时按未置顶处理`() {
        val legacyJson = """[{"id":1,"name":"旅行基金","holderAccountId":1,"targetAmountCents":100000,
            "earmarkedCents":20000,"colorHex":3919032,"sortOrder":1,"createdAt":1700000000000}]"""
        val restored = parseSavingPlans(legacyJson)
        assertEquals(1, restored.size)
        assertFalse(restored.first().isPinned)
        assertEquals("旅行基金", restored.first().name)
    }
}
