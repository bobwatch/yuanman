package com.yuanman.app.data.model

import com.yuanman.app.data.local.entity.CategoryEntity
import java.math.BigDecimal

data class QuickEntryResult(
    val remark: String,
    val amountYuan: BigDecimal,
    val category: CategoryEntity?
)

/** Parses compact entries such as "奶茶 18" or "18元 午餐". */
object QuickEntryParser {
    private val amountPattern = Regex("(?<!\\d)(\\d+(?:[.,]\\d{1,2})?)(?:\\s*元)?(?!\\d)")

    fun parse(input: String, categories: List<CategoryEntity>): QuickEntryResult? {
        val text = input.trim()
        val amountMatch = amountPattern.findAll(text).lastOrNull() ?: return null
        val amount = runCatching {
            BigDecimal(amountMatch.groupValues[1].replace(',', '.'))
        }.getOrNull() ?: return null
        if (amount <= BigDecimal.ZERO) return null

        val description = text.removeRange(amountMatch.range)
            .replace("元", "")
            .replace(Regex("[：:，,]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        return QuickEntryResult(
            remark = description,
            amountYuan = amount,
            category = findCategory(description, categories)
        )
    }

    private fun findCategory(description: String, categories: List<CategoryEntity>): CategoryEntity? {
        if (description.isBlank()) return categories.firstOrNull()
        val normalized = description.replace(" ", "")

        val scored = categories.map { category ->
            val tags = category.tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            var score = 0
            if (normalized.contains(category.name.replace(" ", "")) || category.name.contains(normalized)) score += 100
            if (tags.any { normalized.contains(it.replace(" ", "")) || it.replace(" ", "").contains(normalized) }) score += 80
            score += keywordScore(normalized, category)
            category to score
        }
        return scored.maxByOrNull { it.second }?.takeIf { it.second > 0 }?.first
    }

    private fun keywordScore(text: String, category: CategoryEntity): Int {
        val name = category.name
        return when {
            name.contains("餐") || name.contains("美食") -> if (listOf("奶茶", "咖啡", "早餐", "午餐", "晚餐", "外卖", "吃饭", "零食").any(text::contains)) 60 else 0
            name.contains("住") || name.contains("物业") -> if (listOf("房租", "租金", "房贷", "物业").any(text::contains)) 60 else 0
            name.contains("交通") || name.contains("出行") -> if (listOf("地铁", "公交", "打车", "高铁", "火车", "机票").any(text::contains)) 60 else 0
            name.contains("服饰") -> if (listOf("衣服", "鞋", "包", "裤").any(text::contains)) 50 else 0
            name.contains("充值") || name.contains("缴费") -> if (listOf("话费", "宽带", "水费", "电费", "燃气").any(text::contains)) 50 else 0
            name.contains("工资") -> if (listOf("工资", "月薪", "薪资").any(text::contains)) 60 else 0
            else -> 0
        }
    }
}
