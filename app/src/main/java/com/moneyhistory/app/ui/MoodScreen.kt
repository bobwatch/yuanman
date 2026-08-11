package com.moneyhistory.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.DateUtils
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.Mood
import com.moneyhistory.app.MoodEntry
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.consecutiveNonAngryDays
import java.util.Calendar

/** 心情名称文案。 */
@Composable
private fun moodLabel(mood: Mood): String = stringResource(
    when (mood) {
        Mood.GREAT -> R.string.mood_great
        Mood.GOOD -> R.string.mood_good
        Mood.CALM -> R.string.mood_calm
        Mood.BAD -> R.string.mood_bad
        Mood.ANGRY -> R.string.mood_angry
    }
)

/** 心情 Tab：今日记录 + 本月统计 + 鼓励卡 + 心情网格 + 心情×消费交叉卡。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodScreen(viewModel: MainViewModel) {
    val moods by viewModel.moods.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val today = remember { DateUtils.today() }
    val monthPrefix = remember { DateUtils.monthPrefix() }
    val todayEntry = moods[today]
    var note by remember(today, todayEntry?.note) {
        mutableStateOf(todayEntry?.note ?: "")
    }
    // 一句话备注默认折叠；已有备注则展开
    var noteOpen by remember(today, todayEntry) {
        mutableStateOf(!todayEntry?.note.isNullOrEmpty())
    }

    // 本月记录
    val monthEntries = remember(moods, monthPrefix) {
        moods.filterKeys { it.startsWith(monthPrefix) }
    }
    val angryDays = monthEntries.count { it.value.mood == Mood.ANGRY }
    val nonAngryStreak = remember(moods) { consecutiveNonAngryDays(moods) }

    // 心情×消费交叉（全量数据，数据不足不显示）
    val crossAmount = remember(moods, transactions) {
        val dailyExpense = mutableMapOf<String, Long>()
        transactions.forEach { t ->
            if (t.type == Transaction.Type.EXPENSE) {
                val key = DateUtils.dateKey(t.timestamp)
                dailyExpense[key] = (dailyExpense[key] ?: 0L) + t.amountCents
            }
        }
        val angryKeys = moods.keys.filter { moods[it]?.mood == Mood.ANGRY }
        val calmKeys = moods.keys.filter { moods[it]?.mood != Mood.ANGRY }
        if (angryKeys.isNotEmpty() && calmKeys.size >= 3) {
            val angryAvg = angryKeys.map { dailyExpense[it] ?: 0L }.average()
            val calmAvg = calmKeys.map { dailyExpense[it] ?: 0L }.average()
            val diff = (angryAvg - calmAvg).toLong()
            if (diff > 0) diff else null
        } else {
            null
        }
    }

    // 本月网格数据
    val daysInMonth = remember {
        Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_mood)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 今日记录：点 emoji 即保存
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.mood_today_question),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Mood.entries.forEach { mood ->
                            MoodButton(
                                mood = mood,
                                selected = todayEntry?.mood == mood,
                                onClick = {
                                    viewModel.setMood(today, mood, note.trim())
                                }
                            )
                        }
                    }
                    if (todayEntry != null) {
                        if (!noteOpen) {
                            TextButton(onClick = { noteOpen = true }) {
                                Text(stringResource(R.string.mood_note_toggle))
                            }
                        } else {
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = note,
                                onValueChange = { note = it.take(50) },
                                label = {
                                    Text(stringResource(R.string.mood_note_hint))
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            TextButton(onClick = {
                                viewModel.setMood(today, todayEntry.mood, note.trim())
                            }) {
                                Text(stringResource(R.string.mood_note_save))
                            }
                        }
                    }
                }
            }

            // 鼓励卡（视觉强化）
            Card(shape = RoundedCornerShape(16.dp)) {
                Text(
                    text = encouragementText(moods, nonAngryStreak),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                )
            }

            // 本月统计
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.mood_stats_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    if (monthEntries.isEmpty()) {
                        Text(
                            text = stringResource(R.string.mood_stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(
                                slices = Mood.entries.mapNotNull { mood ->
                                    val count = monthEntries.count {
                                        it.value.mood == mood
                                    }
                                    if (count > 0) {
                                        ChartSlice(
                                            "${mood.emoji} ${moodLabel(mood)}",
                                            count.toFloat()
                                        )
                                    } else {
                                        null
                                    }
                                },
                                centerTitle = stringResource(R.string.mood_donut_center),
                                centerValue = "$angryDays",
                                modifier = Modifier.size(140.dp)
                            )
                            Spacer(Modifier.size(16.dp))
                            Column {
                                Text(
                                    text = stringResource(
                                        R.string.mood_angry_days, angryDays
                                    ),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (angryDays > 0) {
                                        Color(Mood.ANGRY.colorValue)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(
                                        R.string.mood_calm_streak, nonAngryStreak
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = stringResource(
                                        R.string.mood_recorded_days, monthEntries.size
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 心情×消费交叉卡
            crossAmount?.let {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Text(
                        text = stringResource(
                            R.string.mood_cross,
                            MoneyUtils.formatCents(it)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            // 本月心情网格
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.mood_grid_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    MoodGrid(
                        daysInMonth = daysInMonth,
                        monthPrefix = monthPrefix,
                        moods = moods
                    )
                }
            }
        }
    }
}

@Composable
private fun MoodButton(
    mood: Mood,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        label = "moodBorder"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                )
                .border(borderWidth, MaterialTheme.colorScheme.primary, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(text = mood.emoji, fontSize = 26.sp)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = moodLabel(mood),
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** 本月心情网格：7 列，每日一个色点（未记录灰色）。 */
@Composable
private fun MoodGrid(
    daysInMonth: Int,
    monthPrefix: String,
    moods: Map<String, MoodEntry>
) {
    val days = (1..daysInMonth).toList()
    days.chunked(7).forEach { week ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            week.forEach { day ->
                val key = "%s-%02d".format(monthPrefix, day)
                val entry = moods[key]
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                if (entry != null) {
                                    Color(entry.mood.colorValue)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = day.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (entry != null) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            // 补齐最后一周的空位
            repeat(7 - week.size) {
                Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 鼓励文案：按统计数据条件选择（7 条分支）。 */
@Composable
private fun encouragementText(
    moods: Map<String, MoodEntry>,
    nonAngryStreak: Int
): String {
    if (moods.isEmpty()) return stringResource(R.string.mood_encourage_start)

    val monthPrefix = DateUtils.monthPrefix()
    val monthEntries = moods.filterKeys { it.startsWith(monthPrefix) }
    val angryThisMonth = monthEntries.count { it.value.mood == Mood.ANGRY }

    if (angryThisMonth == 0 && monthEntries.size >= 10) {
        return stringResource(R.string.mood_encourage_calm_month)
    }
    if (angryThisMonth == 0 && nonAngryStreak >= 3) {
        return stringResource(R.string.mood_encourage_streak, nonAngryStreak)
    }
    val angryLastMonth = moods.keys
        .filter { it.startsWith(DateUtils.lastMonthPrefix()) }
        .count { moods[it]?.mood == Mood.ANGRY }
    if (angryThisMonth in 1..4 && angryThisMonth < angryLastMonth) {
        return stringResource(
            R.string.mood_encourage_better,
            angryThisMonth,
            angryLastMonth - angryThisMonth
        )
    }
    if (angryThisMonth >= 5) {
        return stringResource(R.string.mood_encourage_hard)
    }
    if (nonAngryStreak >= 1) {
        return stringResource(R.string.mood_encourage_stable, nonAngryStreak)
    }
    return stringResource(R.string.mood_encourage_default)
}
