package com.moneyhistory.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Checkroom
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.InvertColors
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentNeutral
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.outlined.SentimentVerySatisfied
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.SmokeFree
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material.icons.outlined.SportsBar
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.Weekend
import androidx.compose.material.icons.outlined.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.moneyhistory.app.Habit
import com.moneyhistory.app.Mood

// ---------- 分类 ----------

/** 预设分类的 emoji 前缀 → Material 矢量图标。 */
private val categoryIcons = mapOf(
    "🍜" to Icons.Outlined.Restaurant,
    "🚌" to Icons.Outlined.DirectionsBus,
    "🛍" to Icons.Outlined.ShoppingBag,
    "🏠" to Icons.Outlined.Home,
    "💡" to Icons.Outlined.Bolt,
    "🎮" to Icons.Outlined.SportsEsports,
    "💊" to Icons.Outlined.LocalPharmacy,
    "📚" to Icons.Outlined.School,
    "📱" to Icons.Outlined.Smartphone,
    "👕" to Icons.Outlined.Checkroom,
    "🐾" to Icons.Outlined.Pets,
    "⚽" to Icons.Outlined.SportsSoccer,
    "🎁" to Icons.Outlined.CardGiftcard,
    "💅" to Icons.Outlined.Brush,
    "📦" to Icons.Outlined.Category,
    "💰" to Icons.Outlined.Payments,
    "🧧" to Icons.Outlined.Redeem,
    "📈" to Icons.Outlined.TrendingUp,
    "💼" to Icons.Outlined.Work
)

/** 分类 → 矢量图标（自定义分类未匹配时用通用分类图标）。 */
fun categoryIcon(category: String): ImageVector {
    val token = category.substringBefore(" ")
    return categoryIcons[token] ?: Icons.Outlined.Category
}

// ---------- 习惯 ----------

private val habitIcons = mapOf(
    "💪" to Icons.Outlined.FitnessCenter,
    "📚" to Icons.Outlined.School,
    "📖" to Icons.Outlined.MenuBook,
    "🌅" to Icons.Outlined.WbSunny,
    "🏃" to Icons.Outlined.DirectionsRun,
    "🧘" to Icons.Outlined.SelfImprovement,
    "🥗" to Icons.Outlined.Restaurant,
    "💤" to Icons.Outlined.Bedtime,
    "🥤" to Icons.Outlined.LocalDrink,
    "🍺" to Icons.Outlined.SportsBar,
    "🚬" to Icons.Outlined.SmokeFree,
    "🌙" to Icons.Outlined.NightsStay
)

fun habitIcon(emoji: String): ImageVector = habitIcons[emoji] ?: Icons.Outlined.CheckCircle

/** 习惯的类型无关展示色：build 主色 / quit 橙色。 */
fun habitIconColor(habit: Habit): androidx.compose.ui.graphics.Color =
    if (habit.type == Habit.Type.BUILD) {
        androidx.compose.ui.graphics.Color(0xFF2AABEE)
    } else {
        androidx.compose.ui.graphics.Color(0xFFFF9800)
    }

// ---------- 攒钱目标 ----------

private val goalIcons = mapOf(
    "✈️" to Icons.Outlined.Flight,
    "📱" to Icons.Outlined.Smartphone,
    "💻" to Icons.Outlined.Laptop,
    "🚗" to Icons.Outlined.DirectionsCar,
    "🏠" to Icons.Outlined.Home,
    "🎁" to Icons.Outlined.CardGiftcard,
    "🐱" to Icons.Outlined.Pets,
    "🎓" to Icons.Outlined.School,
    "💍" to Icons.Outlined.Diamond,
    "📷" to Icons.Outlined.PhotoCamera,
    "🎮" to Icons.Outlined.SportsEsports,
    "🛋" to Icons.Outlined.Weekend
)

fun goalIcon(emoji: String): ImageVector = goalIcons[emoji] ?: Icons.Outlined.Savings

// ---------- 心情 ----------

fun moodIcon(mood: Mood): ImageVector = when (mood) {
    Mood.GREAT -> Icons.Outlined.SentimentVerySatisfied
    Mood.GOOD -> Icons.Outlined.SentimentSatisfied
    Mood.CALM -> Icons.Outlined.SentimentNeutral
    Mood.BAD -> Icons.Outlined.SentimentDissatisfied
    Mood.ANGRY -> Icons.Outlined.SentimentVeryDissatisfied
}

// ---------- 勋章 ----------

private val badgeIcons = mapOf(
    "first_tx" to Icons.Outlined.Eco,
    "streak_7" to Icons.Outlined.MenuBook,
    "streak_30" to Icons.Outlined.CalendarMonth,
    "tx_100" to Icons.Outlined.CheckCircle,
    "goal_done" to Icons.Outlined.Diamond,
    "first_checkin" to Icons.Outlined.TaskAlt,
    "habit_7" to Icons.Outlined.LocalFireDepartment,
    "habit_21" to Icons.Outlined.EmojiEvents,
    "quit_30" to Icons.Outlined.Shield,
    "first_mood" to Icons.Outlined.Palette,
    "mood_7" to Icons.Outlined.InvertColors,
    "calm_month" to Icons.Outlined.Spa
)

fun badgeIcon(id: String): ImageVector = badgeIcons[id] ?: Icons.Outlined.EmojiEvents
