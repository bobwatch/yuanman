package com.moneyhistory.app

/**
 * 预设分类（emoji + 名称），硬编码常量。
 * 分类以完整字符串（含 emoji）存储在流水记录里。
 */
object Categories {

    val expense = listOf(
        "🍜 餐饮美食",
        "🚌 交通出行",
        "🚗 爱车养车",
        "💡 充值缴费",
        "👕 服饰装扮",
        "🛋 家居家装",
        "📱 数码电器",
        "⚽ 运动户外",
        "💅 美容美发",
        "👶 母婴亲子",
        "🐾 宠物",
        "🏢 住房物业",
        "✈️ 酒店旅游",
        "🎮 文化休闲",
        "📚 教育培训",
        "💊 医疗健康",
        "🛠 生活服务",
        "🎁 人情往来",
        "💹 投资理财",
        "📦 其他"
    )

    val income = listOf(
        "💰 工资",
        "🧧 红包转账",
        "📈 理财收益",
        "💼 兼职外快",
        "🔄 退款",
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
