package com.moneyhistory.app

/** 一枚勋章（文案走字符串资源）。 */
data class Badge(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val descRes: Int,
    val categoryRes: Int
)

/** 全部勋章（12 枚）。 */
val allBadges = listOf(
    Badge("first_tx", "🌱", R.string.badge_first_tx_title, R.string.badge_first_tx_desc, R.string.badge_cat_record),
    Badge("streak_7", "📖", R.string.badge_streak_7_title, R.string.badge_streak_7_desc, R.string.badge_cat_record),
    Badge("streak_30", "🗓", R.string.badge_streak_30_title, R.string.badge_streak_30_desc, R.string.badge_cat_record),
    Badge("tx_100", "💯", R.string.badge_tx_100_title, R.string.badge_tx_100_desc, R.string.badge_cat_record),
    Badge("goal_done", "💎", R.string.badge_goal_done_title, R.string.badge_goal_done_desc, R.string.badge_cat_record),
    Badge("first_checkin", "✅", R.string.badge_first_checkin_title, R.string.badge_first_checkin_desc, R.string.badge_cat_habit),
    Badge("habit_7", "🔥", R.string.badge_habit_7_title, R.string.badge_habit_7_desc, R.string.badge_cat_habit),
    Badge("habit_21", "🏆", R.string.badge_habit_21_title, R.string.badge_habit_21_desc, R.string.badge_cat_habit),
    Badge("quit_30", "🛡", R.string.badge_quit_30_title, R.string.badge_quit_30_desc, R.string.badge_cat_habit),
    Badge("first_mood", "🎨", R.string.badge_first_mood_title, R.string.badge_first_mood_desc, R.string.badge_cat_mood),
    Badge("mood_7", "🌈", R.string.badge_mood_7_title, R.string.badge_mood_7_desc, R.string.badge_cat_mood),
    Badge("calm_month", "😇", R.string.badge_calm_month_title, R.string.badge_calm_month_desc, R.string.badge_cat_mood)
)

internal fun badgeById(id: String): Badge? = allBadges.firstOrNull { it.id == id }

/** 勋章判定的聚合输入（由 MainViewModel 汇总各 store 数据）。 */
internal data class BadgeInput(
    val txCount: Int,
    val txStreak: Int,
    val goalAchieved: Boolean,
    val anyCheckin: Boolean,
    val maxBuildStreak: Int,
    val maxQuitDays: Int,
    val moodCount: Int,
    val moodStreak: Int,
    val calmMonth: Boolean
)

/** 计算当前已解锁的勋章 id 集合。 */
internal fun evaluateBadges(input: BadgeInput): Set<String> {
    val out = mutableSetOf<String>()
    if (input.txCount >= 1) out += "first_tx"
    if (input.txStreak >= 7) out += "streak_7"
    if (input.txStreak >= 30) out += "streak_30"
    if (input.txCount >= 100) out += "tx_100"
    if (input.goalAchieved) out += "goal_done"
    if (input.anyCheckin) out += "first_checkin"
    if (input.maxBuildStreak >= 7) out += "habit_7"
    if (input.maxBuildStreak >= 21) out += "habit_21"
    if (input.maxQuitDays >= 30) out += "quit_30"
    if (input.moodCount >= 1) out += "first_mood"
    if (input.moodStreak >= 7) out += "mood_7"
    if (input.calmMonth) out += "calm_month"
    return out
}
