package com.moneyhistory.app

/**
 * 预设分类（emoji + 名称），硬编码常量。
 * 分类以完整字符串（含 emoji）存储在流水记录里。
 */
object Categories {

    val expense = listOf(
        "🍜 餐饮",
        "🚌 交通",
        "🛍 购物",
        "🏠 居住",
        "💡 水电煤",
        "🎮 娱乐",
        "💊 医疗",
        "📚 学习",
        "📱 通讯",
        "👕 服饰",
        "🐾 宠物",
        "⚽ 运动",
        "🎁 人情",
        "💅 美容",
        "📦 其他"
    )

    val income = listOf(
        "💰 工资",
        "🧧 红包",
        "📈 理财",
        "💼 兼职",
        "📦 其他"
    )

    /** 自定义分类的 emoji 候选（分类管理页选择）。 */
    val emojiCandidates = listOf(
        "🐱", "🐶", "🌹", "🎁", "🧸",
        "🍺", "☕", "🍰", "🥗", "🏋️",
        "💇", "🧴", "👶", "🎓", "🚗",
        "✈️", "🏥", "💡", "🧾", "🎯"
    )

    /** 从「emoji 名称」中取出 emoji 部分。 */
    fun emojiOf(category: String): String =
        category.substringBefore(" ").ifEmpty { "📦" }

    /** 从「emoji 名称」中取出名称部分（无空格时原样返回）。 */
    fun nameOf(category: String): String =
        category.substringAfter(" ", category)
}
