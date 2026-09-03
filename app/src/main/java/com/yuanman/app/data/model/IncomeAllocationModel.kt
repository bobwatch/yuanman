package com.yuanman.app.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * 收入分配分配方式：百分比 或 固定金额
 */
enum class AllocationRuleType {
    PERCENTAGE,
    FIXED
}

/**
 * 单条收入分配规则
 */
data class IncomeAllocationRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val targetAccountId: Long,
    val targetAccountName: String = "",
    val type: AllocationRuleType = AllocationRuleType.PERCENTAGE,
    val percentage: Float = 0.5f, // 0.0 ~ 1.0 (例如 0.5f 代表 50%)
    val fixedAmountCents: Long = 0L,
    val sortOrder: Int = 0
) {
    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("name", name)
            put("targetAccountId", targetAccountId)
            put("targetAccountName", targetAccountName)
            put("type", type.name)
            put("percentage", percentage.toDouble())
            put("fixedAmountCents", fixedAmountCents)
            put("sortOrder", sortOrder)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): IncomeAllocationRule? {
            return try {
                val id = json.optString("id", java.util.UUID.randomUUID().toString())
                val name = json.optString("name", "")
                val targetAccountId = json.optLong("targetAccountId", 0L)
                val targetAccountName = json.optString("targetAccountName", "")
                val typeStr = json.optString("type", AllocationRuleType.PERCENTAGE.name)
                val type = try {
                    AllocationRuleType.valueOf(typeStr)
                } catch (e: Exception) {
                    AllocationRuleType.PERCENTAGE
                }
                val percentage = json.optDouble("percentage", 0.0).toFloat()
                val fixedAmountCents = json.optLong("fixedAmountCents", 0L)
                val sortOrder = json.optInt("sortOrder", 0)

                IncomeAllocationRule(
                    id = id,
                    name = name,
                    targetAccountId = targetAccountId,
                    targetAccountName = targetAccountName,
                    type = type,
                    percentage = percentage,
                    fixedAmountCents = fixedAmountCents,
                    sortOrder = sortOrder
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * 收入分配试算结果明细
 */
data class IncomeAllocationResultItem(
    val rule: IncomeAllocationRule,
    val allocatedAmountCents: Long,
    val actualPercentage: Float
)

/**
 * 收入分配计算引擎
 */
object IncomeAllocationCalculator {

    fun calculate(
        totalIncomeCents: Long,
        rules: List<IncomeAllocationRule>
    ): List<IncomeAllocationResultItem> {
        if (totalIncomeCents <= 0L || rules.isEmpty()) return emptyList()

        var remainingCents = totalIncomeCents
        val results = mutableListOf<IncomeAllocationResultItem>()

        // 1. 先计算固定金额规则
        val fixedRules = rules.filter { it.type == AllocationRuleType.FIXED }.sortedBy { it.sortOrder }
        for (rule in fixedRules) {
            val alloc = rule.fixedAmountCents.coerceAtMost(remainingCents).coerceAtLeast(0L)
            remainingCents -= alloc
            val percent = if (totalIncomeCents > 0) alloc.toFloat() / totalIncomeCents else 0f
            results.add(
                IncomeAllocationResultItem(
                    rule = rule,
                    allocatedAmountCents = alloc,
                    actualPercentage = percent
                )
            )
        }

        // 2. 剩余部分按百分比规则分配
        val percentRules = rules.filter { it.type == AllocationRuleType.PERCENTAGE }.sortedBy { it.sortOrder }
        val totalPercentage = percentRules.map { it.percentage }.sum()

        if (percentRules.isNotEmpty()) {
            val amountForPercentRules = if (fixedRules.isNotEmpty()) {
                remainingCents
            } else if (totalPercentage <= 1.0f) {
                totalIncomeCents
            } else {
                remainingCents
            }

            var percentRemaining = remainingCents
            percentRules.forEachIndexed { index, rule ->
                val isLast = index == percentRules.size - 1
                val alloc = if (isLast && totalPercentage >= 0.999f) {
                    percentRemaining.coerceAtLeast(0L)
                } else {
                    ((amountForPercentRules * rule.percentage).toLong()).coerceAtMost(percentRemaining).coerceAtLeast(0L)
                }
                percentRemaining -= alloc
                val percent = if (totalIncomeCents > 0) alloc.toFloat() / totalIncomeCents else 0f
                results.add(
                    IncomeAllocationResultItem(
                        rule = rule,
                        allocatedAmountCents = alloc,
                        actualPercentage = percent
                    )
                )
            }
        }

        return results
    }

    fun serializeRules(rules: List<IncomeAllocationRule>): String {
        val array = JSONArray()
        rules.forEach { array.put(it.toJson()) }
        return array.toString()
    }

    fun deserializeRules(jsonStr: String?): List<IncomeAllocationRule> {
        if (jsonStr.isNullOrBlank()) return emptyList()
        val list = mutableListOf<IncomeAllocationRule>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i)
                if (obj != null) {
                    IncomeAllocationRule.fromJson(obj)?.let { list.add(it) }
                }
            }
        } catch (e: Exception) {
            // fallback
        }
        return list
    }
}
