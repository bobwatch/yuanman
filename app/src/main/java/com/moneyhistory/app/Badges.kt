package com.moneyhistory.app

import java.util.Calendar

/** 一枚勋章（文案走字符串资源）。 */
data class Badge(
    val id: String,
    val emoji: String,
    val titleRes: Int,
    val descRes: Int,
    val categoryRes: Int
)

/** 全部勋章（26 枚）。梯度设计：每类都有近端（几天/十笔内可得）、
 *  中远端（长期坚持）、累计型（断卡不归零，降低放弃）与功能引导型。 */
val allBadges = listOf(
    // ---- 记账 ----
    Badge("first_tx", "🌱", R.string.badge_first_tx_title, R.string.badge_first_tx_desc, R.string.badge_cat_record),
    Badge("tx_10", "🧾", R.string.badge_tx_10_title, R.string.badge_tx_10_desc, R.string.badge_cat_record),
    Badge("streak_7", "📖", R.string.badge_streak_7_title, R.string.badge_streak_7_desc, R.string.badge_cat_record),
    Badge("streak_30", "🗓", R.string.badge_streak_30_title, R.string.badge_streak_30_desc, R.string.badge_cat_record),
    Badge("streak_100", "💠", R.string.badge_streak_100_title, R.string.badge_streak_100_desc, R.string.badge_cat_record),
    Badge("tx_100", "💯", R.string.badge_tx_100_title, R.string.badge_tx_100_desc, R.string.badge_cat_record),
    Badge("tx_500", "📚", R.string.badge_tx_500_title, R.string.badge_tx_500_desc, R.string.badge_cat_record),
    Badge("month_full", "✅", R.string.badge_month_full_title, R.string.badge_month_full_desc, R.string.badge_cat_record),
    Badge("recurring_first", "🔁", R.string.badge_recurring_first_title, R.string.badge_recurring_first_desc, R.string.badge_cat_record),
    Badge("goal_deposit", "🏦", R.string.badge_goal_deposit_title, R.string.badge_goal_deposit_desc, R.string.badge_cat_record),
    Badge("goal_done", "💎", R.string.badge_goal_done_title, R.string.badge_goal_done_desc, R.string.badge_cat_record),
    // ---- 打卡 ----
    Badge("first_checkin", "☑️", R.string.badge_first_checkin_title, R.string.badge_first_checkin_desc, R.string.badge_cat_habit),
    Badge("habit_3", "🚀", R.string.badge_habit_3_title, R.string.badge_habit_3_desc, R.string.badge_cat_habit),
    Badge("habit_7", "🔥", R.string.badge_habit_7_title, R.string.badge_habit_7_desc, R.string.badge_cat_habit),
    Badge("habit_21", "🏆", R.string.badge_habit_21_title, R.string.badge_habit_21_desc, R.string.badge_cat_habit),
    Badge("habit_30", "🎖", R.string.badge_habit_30_title, R.string.badge_habit_30_desc, R.string.badge_cat_habit),
    Badge("habit_total_100", "🧱", R.string.badge_habit_total_100_title, R.string.badge_habit_total_100_desc, R.string.badge_cat_habit),
    Badge("quit_7", "🌿", R.string.badge_quit_7_title, R.string.badge_quit_7_desc, R.string.badge_cat_habit),
    Badge("quit_30", "🛡", R.string.badge_quit_30_title, R.string.badge_quit_30_desc, R.string.badge_cat_habit),
    Badge("comeback", "🌟", R.string.badge_comeback_title, R.string.badge_comeback_desc, R.string.badge_cat_habit),
    // ---- 心情 ----
    Badge("first_mood", "🎨", R.string.badge_first_mood_title, R.string.badge_first_mood_desc, R.string.badge_cat_mood),
    Badge("mood_7", "🌈", R.string.badge_mood_7_title, R.string.badge_mood_7_desc, R.string.badge_cat_mood),
    Badge("mood_sunny_7", "☀️", R.string.badge_mood_sunny_7_title, R.string.badge_mood_sunny_7_desc, R.string.badge_cat_mood),
    Badge("mood_30", "📓", R.string.badge_mood_30_title, R.string.badge_mood_30_desc, R.string.badge_cat_mood),
    Badge("mood_note", "💌", R.string.badge_mood_note_title, R.string.badge_mood_note_desc, R.string.badge_cat_mood),
    Badge("calm_month", "😇", R.string.badge_calm_month_title, R.string.badge_calm_month_desc, R.string.badge_cat_mood)
)

internal fun badgeById(id: String): Badge? = allBadges.firstOrNull { it.id == id }

/** 勋章判定的聚合输入（由 MainViewModel 汇总各 store 数据）。 */
internal data class BadgeInput(
    val txCount: Int,
    val txStreak: Int,
    /** 存在某自然月每天都记了账。 */
    val fullMonth: Boolean,
    val recurringAdded: Boolean,
    val goalDeposited: Boolean,
    val goalAchieved: Boolean,
    val anyCheckin: Boolean,
    val maxBuildStreak: Int,
    /** 全部 build 习惯的打卡次数总和（跨习惯累计，断卡不归零）。 */
    val totalCheckins: Int,
    val maxQuitDays: Int,
    /** 是否有过破戒记录（用于「浪子回头」）。 */
    val anyQuitReset: Boolean,
    val moodCount: Int,
    val moodStreak: Int,
    /** 连续无负面心情天数（有记录且非 bad/angry）。 */
    val nonAngryStreak: Int,
    val moodNote: Boolean,
    val calmMonth: Boolean
)

/** 计算当前已解锁的勋章 id 集合。 */
internal fun evaluateBadges(input: BadgeInput): Set<String> {
    val out = mutableSetOf<String>()
    if (input.txCount >= 1) out += "first_tx"
    if (input.txCount >= 10) out += "tx_10"
    if (input.txStreak >= 7) out += "streak_7"
    if (input.txStreak >= 30) out += "streak_30"
    if (input.txStreak >= 100) out += "streak_100"
    if (input.txCount >= 100) out += "tx_100"
    if (input.txCount >= 500) out += "tx_500"
    if (input.fullMonth) out += "month_full"
    if (input.recurringAdded) out += "recurring_first"
    if (input.goalDeposited) out += "goal_deposit"
    if (input.goalAchieved) out += "goal_done"
    if (input.anyCheckin) out += "first_checkin"
    if (input.maxBuildStreak >= 3) out += "habit_3"
    if (input.maxBuildStreak >= 7) out += "habit_7"
    if (input.maxBuildStreak >= 21) out += "habit_21"
    if (input.maxBuildStreak >= 30) out += "habit_30"
    if (input.totalCheckins >= 100) out += "habit_total_100"
    if (input.maxQuitDays >= 7) out += "quit_7"
    if (input.maxQuitDays >= 30) out += "quit_30"
    if (input.maxQuitDays >= 7 && input.anyQuitReset) out += "comeback"
    if (input.moodCount >= 1) out += "first_mood"
    if (input.moodStreak >= 7) out += "mood_7"
    if (input.nonAngryStreak >= 7) out += "mood_sunny_7"
    if (input.moodCount >= 30) out += "mood_30"
    if (input.moodNote) out += "mood_note"
    if (input.calmMonth) out += "calm_month"
    return out
}

/** 是否存在「全勤月」：某自然月的每一天都有记账。 */
internal fun hasFullMonth(txs: List<Transaction>): Boolean {
    if (txs.isEmpty()) return false
    val days = txs.map { dayKeyOf(it.timestamp) }.toSet()
    val months = days.map { it / 100 }.toSet()
    for (monthKey in months) {
        val year = monthKey / 100
        val month = monthKey % 100
        val cal = Calendar.getInstance()
        cal.clear()
        cal.set(year, month - 1, 1)
        val dayCount = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        var full = true
        for (d in 1..dayCount) {
            if (year * 10000 + month * 100 + d !in days) {
                full = false
                break
            }
        }
        if (full) return true
    }
    return false
}
