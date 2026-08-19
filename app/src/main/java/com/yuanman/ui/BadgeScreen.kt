package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.DateUtils
import com.yuanman.Habit
import com.yuanman.MainViewModel
import com.yuanman.R
import com.yuanman.allBadges
import com.yuanman.buildStreak
import com.yuanman.consecutiveNonAngryDays
import com.yuanman.moodStreakOf
import com.yuanman.quitDays
import com.yuanman.streakOf

/** 勋章墙：已获得彩色 + 获得日期；未获得灰色 + 解锁条件（可量化条件显示当前进度）。 */
@Composable
fun BadgeScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val unlocks by viewModel.settings.badgeUnlocks.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val grouped = allBadges.groupBy { it.categoryRes }

    // 本会话已弹过庆祝动画的勋章：同一天反复进出勋章页不重复庆祝，
    // 仪式感只在解锁当下给一次，而不是每次进页都「惊喜」一遍
    var celebratedToday by rememberSaveable { mutableStateOf(listOf<String>()) }

    // 各勋章的当前进度（current, target）；非线性条件（达成目标/佛系本月等）不在其中
    val badgeProgress = remember(transactions, habits, moods) {
        val txCount = transactions.size
        val txStreak = streakOf(transactions)
        val maxBuild = habits.filter { it.type == Habit.Type.BUILD }
            .maxOfOrNull { it.buildStreak() } ?: 0
        val maxQuit = habits.filter { it.type == Habit.Type.QUIT }
            .maxOfOrNull { it.quitDays() } ?: 0
        // 破戒后重新守住的进度：只看「破过戒」的戒断习惯
        val maxQuitAfterReset = habits.filter {
            it.type == Habit.Type.QUIT && it.resets.isNotEmpty()
        }.maxOfOrNull { it.quitDays() } ?: 0
        val anyCheckin = habits.any { it.checkins.isNotEmpty() }
        val totalCheckins = habits.sumOf { it.checkins.size }
        val moodStreak = moodStreakOf(moods.keys)
        val nonAngryStreak = consecutiveNonAngryDays(moods)
        mapOf(
            "first_tx" to (txCount to 1),
            "tx_10" to (txCount to 10),
            "streak_7" to (txStreak to 7),
            "streak_30" to (txStreak to 30),
            "streak_100" to (txStreak to 100),
            "tx_100" to (txCount to 100),
            "tx_500" to (txCount to 500),
            "first_checkin" to ((if (anyCheckin) 1 else 0) to 1),
            "habit_3" to (maxBuild to 3),
            "habit_7" to (maxBuild to 7),
            "habit_21" to (maxBuild to 21),
            "habit_30" to (maxBuild to 30),
            "habit_total_100" to (totalCheckins to 100),
            "quit_7" to (maxQuit to 7),
            "quit_30" to (maxQuit to 30),
            "comeback" to (maxQuitAfterReset to 7),
            "first_mood" to (moods.size to 1),
            "mood_7" to (moodStreak to 7),
            "mood_sunny_7" to (nonAngryStreak to 7),
            "mood_30" to (moods.size to 30)
        )
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = stringResource(
                R.string.badge_screen_title,
                unlocks.size,
                allBadges.size
            ),
            onBack = onBack
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            grouped.forEach { (categoryRes, badges) ->
                item(key = "header_$categoryRes") {
                    SectionTitle(stringResource(categoryRes))
                }
                items(badges.size, key = { badges[it].id }) { index ->
                    val badge = badges[index]
                    val unlockedDate = unlocks[badge.id]
                    val unlocked = unlockedDate != null
                    val datePattern = stringResource(R.string.date_pattern)
                    // 存储的解锁日期是固定 yyyy-MM-dd，展示时按当前语言环境重排
                    val obtainedText = remember(unlockedDate, datePattern) {
                        unlockedDate?.let { raw ->
                            DateUtils.parse(raw)?.let {
                                formatSheetDate(it, datePattern)
                            }
                        } ?: ""
                    }
                    // 今天刚解锁的勋章：进页时弹一下——努力被看见的时刻值得一个仪式感；
                    // 同一枚勋章只弹一次（celebratedToday 本会话记住）
                    val fresh = unlocked && unlockedDate == DateUtils.today()
                    val shouldPop = fresh && badge.id !in celebratedToday
                    val popScale = remember { Animatable(1f) }
                    val view = LocalView.current
                    LaunchedEffect(shouldPop) {
                        if (shouldPop) {
                            // 弹跳动效 + 确认触感：解锁的「落定感」让努力被看见
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            popScale.snapTo(0.6f)
                            popScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            )
                            celebratedToday = celebratedToday + badge.id
                        }
                    }
                    AppCard(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .graphicsLayer {
                                scaleX = popScale.value
                                scaleY = popScale.value
                            }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconTile(
                                icon = badgeIcon(badge.id),
                                tint = if (unlocked) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                container = if (unlocked) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                                size = 48.dp,
                                iconSize = 24.dp
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(badge.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (unlocked) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                if (unlocked) {
                                    Text(
                                        text = if (fresh) {
                                            stringResource(R.string.badge_obtained_today)
                                        } else {
                                            stringResource(
                                                R.string.badge_obtained,
                                                obtainedText
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (fresh) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                } else {
                                    // 未解锁：解锁条件 + 可量化进度的 x / y（有进度才显示，避免 0/7 式的沮丧）
                                    val progress = badgeProgress[badge.id]
                                    Column {
                                        Text(
                                            text = stringResource(badge.descRes),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (progress != null && progress.first > 0) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                text = stringResource(
                                                    R.string.badge_progress,
                                                    progress.first,
                                                    progress.second
                                                ),
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                            if (unlocked) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
