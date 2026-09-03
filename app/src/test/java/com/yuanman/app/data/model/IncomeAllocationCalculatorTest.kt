package com.yuanman.app.data.model

import org.junit.Assert.*
import org.junit.Test

class IncomeAllocationCalculatorTest {

    @Test
    fun testPercentageAllocation503020() {
        val rules = listOf(
            IncomeAllocationRule(
                name = "生活",
                targetAccountId = 1L,
                type = AllocationRuleType.PERCENTAGE,
                percentage = 0.50f,
                sortOrder = 1
            ),
            IncomeAllocationRule(
                name = "投资",
                targetAccountId = 2L,
                type = AllocationRuleType.PERCENTAGE,
                percentage = 0.30f,
                sortOrder = 2
            ),
            IncomeAllocationRule(
                name = "储蓄",
                targetAccountId = 3L,
                type = AllocationRuleType.PERCENTAGE,
                percentage = 0.20f,
                sortOrder = 3
            )
        )

        val totalIncomeCents = 1000000L // 10,000 元
        val results = IncomeAllocationCalculator.calculate(totalIncomeCents, rules)

        assertEquals(3, results.size)
        assertEquals(500000L, results[0].allocatedAmountCents) // 5,000 元
        assertEquals(300000L, results[1].allocatedAmountCents) // 3,000 元
        assertEquals(200000L, results[2].allocatedAmountCents) // 2,000 元
        assertEquals(1000000L, results.sumOf { it.allocatedAmountCents })
    }

    @Test
    fun testFixedAndPercentageAllocation() {
        val rules = listOf(
            IncomeAllocationRule(
                name = "房租固定预留",
                targetAccountId = 1L,
                type = AllocationRuleType.FIXED,
                fixedAmountCents = 300000L, // 3,000 元
                sortOrder = 1
            ),
            IncomeAllocationRule(
                name = "结余投资",
                targetAccountId = 2L,
                type = AllocationRuleType.PERCENTAGE,
                percentage = 0.50f,
                sortOrder = 2
            )
        )

        val totalIncomeCents = 1000000L // 10,000 元
        val results = IncomeAllocationCalculator.calculate(totalIncomeCents, rules)

        assertEquals(2, results.size)
        assertEquals(300000L, results[0].allocatedAmountCents)
        // 剩余 7,000 元，50% -> 3,500 元
        assertEquals(350000L, results[1].allocatedAmountCents)
    }

    @Test
    fun zeroIncomeDoesNotProduceAllocation() {
        val rules = listOf(
            IncomeAllocationRule(
                name = "储蓄",
                targetAccountId = 1L,
                percentage = 1.0f
            )
        )

        assertTrue(IncomeAllocationCalculator.calculate(0L, rules).isEmpty())
    }

    @Test
    fun testSerializeAndDeserialize() {
        val rules = listOf(
            IncomeAllocationRule(
                id = "rule-1",
                name = "储蓄",
                targetAccountId = 1L,
                targetAccountName = "招商卡",
                type = AllocationRuleType.PERCENTAGE,
                percentage = 0.40f,
                sortOrder = 1
            )
        )

        val json = IncomeAllocationCalculator.serializeRules(rules)
        assertTrue(json.contains("rule-1"))
        assertTrue(json.contains("储蓄"))

        val deserialized = IncomeAllocationCalculator.deserializeRules(json)
        assertEquals(1, deserialized.size)
        assertEquals("rule-1", deserialized[0].id)
        assertEquals("储蓄", deserialized[0].name)
        assertEquals(0.40f, deserialized[0].percentage, 0.001f)
    }
}
