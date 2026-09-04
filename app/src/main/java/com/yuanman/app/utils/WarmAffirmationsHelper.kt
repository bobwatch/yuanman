package com.yuanman.app.utils

import java.util.Calendar

data class WarmAffirmation(
    val quote: String,
    val authorOrTag: String,
    val emoji: String
)

object WarmAffirmationsHelper {

    private val MORNING_AFFIRMATIONS = listOf(
        WarmAffirmation("晨光熹微，用心记录生活的每一笔烟火气与美好。", "晨光物语", "☀️"),
        WarmAffirmation("新的一天开启，理清手头每一分收支，做生活从容的掌控者。", "理财心法", "🌱"),
        WarmAffirmation("吃一顿营养好早餐，元气满满地向着理想奔赴吧！", "元气满满", "🥐"),
        WarmAffirmation("每一笔清晰的记录，都是未来自由与从容的基石。", "自律之美", "✨")
    )

    private val AFTERNOON_AFFIRMATIONS = listOf(
        WarmAffirmation("午后微风正好，忙碌之余别忘了喝杯水，适度休憩。", "温柔陪伴", "☕"),
        WarmAffirmation("买真正喜欢且需要的物品，是取悦自己最好的方式。", "理性消费", "🌿"),
        WarmAffirmation("生活的节奏由你掌握，稳扎稳打，步步皆有回响。", "静心从容", "🪴"),
        WarmAffirmation("保持对生活的热爱，每一份专注都在悄悄增值。", "成长同行", "📈")
    )

    private val EVENING_AFFIRMATIONS = listOf(
        WarmAffirmation("万家灯火初上，辛苦了一整天，今晚犒劳一下自己吧。", "人间烟火", "🌇"),
        WarmAffirmation("今天把控得很棒哦！每一笔储蓄，都是写给未来的底气。", "踏实安全", "🛡️"),
        WarmAffirmation("记下今天的收支，卸下一天的疲惫，享受属于自己的宁静时光。", "晚间复盘", "🌙"),
        WarmAffirmation("生活不在别处，就在这一蔬一饭、一分一厘的踏实里。", "温暖归途", "🍲")
    )

    private val NIGHT_AFFIRMATIONS = listOf(
        WarmAffirmation("夜深了，世界已安静。放下今日琐碎，今晚做个好梦。", "晚安好梦", "🌌"),
        WarmAffirmation("每一天的认真生活都值得被奖赏，明天又是全新的一天。", "温柔治愈", "🌟"),
        WarmAffirmation("愿你心有繁星，兜有余量，踏实入眠，未来可期。", "星光守护", "✨")
    )

    private val ALL_AFFIRMATIONS = MORNING_AFFIRMATIONS + AFTERNOON_AFFIRMATIONS + EVENING_AFFIRMATIONS + NIGHT_AFFIRMATIONS

    /**
     * 根据当前时间段获取默认温暖寄语
     */
    fun getAffirmationForCurrentTime(seed: Int = 0): WarmAffirmation {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val list = when (hour) {
            in 5..9 -> MORNING_AFFIRMATIONS
            in 10..16 -> AFTERNOON_AFFIRMATIONS
            in 17..21 -> EVENING_AFFIRMATIONS
            else -> NIGHT_AFFIRMATIONS
        }
        val index = (Math.abs(seed) + Calendar.getInstance().get(Calendar.DAY_OF_YEAR)) % list.size
        return list[index]
    }

    /**
     * 随机获取一条灵感心语
     */
    fun getRandomAffirmation(currentIndex: Int = -1): Pair<WarmAffirmation, Int> {
        var nextIndex = (currentIndex + 1) % ALL_AFFIRMATIONS.size
        if (nextIndex == currentIndex && ALL_AFFIRMATIONS.size > 1) {
            nextIndex = (nextIndex + 1) % ALL_AFFIRMATIONS.size
        }
        return Pair(ALL_AFFIRMATIONS[nextIndex], nextIndex)
    }

}
