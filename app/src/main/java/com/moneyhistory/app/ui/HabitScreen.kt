package com.moneyhistory.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.DateUtils
import com.moneyhistory.app.Habit
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.R
import com.moneyhistory.app.buildStreak
import com.moneyhistory.app.checkedOn
import com.moneyhistory.app.habitEmojiCandidates
import com.moneyhistory.app.quitDays
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen

private data class HabitPreset(val emoji: String, val name: String, val type: Habit.Type)

// 预设为「用户可见数据」（填充后即存库），保持中文原样
private val habitPresets = listOf(
    HabitPreset("💪", "健身运动", Habit.Type.BUILD),
    HabitPreset("📚", "学习打卡", Habit.Type.BUILD),
    HabitPreset("📖", "阅读", Habit.Type.BUILD),
    HabitPreset("🌅", "早起", Habit.Type.BUILD),
    HabitPreset("🥤", "戒饮料", Habit.Type.QUIT),
    HabitPreset("🍺", "戒酒", Habit.Type.QUIT),
    HabitPreset("🚬", "戒烟", Habit.Type.QUIT),
    HabitPreset("🌙", "戒熬夜", Habit.Type.QUIT)
)

/** 打卡 Tab：习惯列表 + 虚线「+ 新习惯」入口卡片。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HabitScreen(viewModel: MainViewModel) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    val today = remember { DateUtils.today() }

    var showCreateSheet by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Habit?>(null) }
    var resetTarget by remember { mutableStateOf<Habit?>(null) }

    Column(Modifier.fillMaxSize()) {
        YuanmanHeader(
            title = stringResource(R.string.tab_habits),
            subtitle = stringResource(R.string.habit_header_sub)
        )

        LazyColumn(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(top = 4.dp)
        ) {
            if (habits.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.habit_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp)
                    )
                }
            }
            items(habits, key = { it.id }) { habit ->
                HabitCard(
                    habit = habit,
                    today = today,
                    onCheckin = { viewModel.toggleCheckin(habit.id) },
                    onReset = { resetTarget = habit },
                    onDelete = { deleteTarget = habit }
                )
            }
            item(key = "add") {
                DashedAddCard(onClick = { showCreateSheet = true })
            }
        }
    }

    if (showCreateSheet) {
        HabitCreateSheet(
            onDismiss = { showCreateSheet = false },
            onCreate = { name, emoji, type ->
                viewModel.addHabit(
                    Habit(
                        name = name,
                        emoji = emoji,
                        type = type,
                        startDate = DateUtils.today()
                    )
                )
                showCreateSheet = false
            }
        )
    }

    deleteTarget?.let { habit ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(stringResource(R.string.habit_delete_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.habit_delete_msg,
                        "${habit.emoji} ${habit.name}"
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removeHabit(habit.id)
                    deleteTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    resetTarget?.let { habit ->
        AlertDialog(
            onDismissRequest = { resetTarget = null },
            title = { Text(stringResource(R.string.habit_relapse_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.habit_relapse_msg,
                        "${habit.emoji} ${habit.name}",
                        habit.quitDays(today)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetHabit(habit.id)
                    resetTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_confirm),
                        color = ExpenseRed
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { resetTarget = null }) {
                    Text(stringResource(R.string.habit_relapse_cancel))
                }
            }
        )
    }
}

/** 虚线「+ 新习惯」入口卡片。 */
@Composable
private fun DashedAddCard(onClick: () -> Unit) {
    val dashColor = MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .dashedBorder(dashColor, 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.habit_add_new),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun Modifier.dashedBorder(color: Color, cornerRadius: Dp): Modifier =
    drawBehind {
        val strokeWidth = 2.dp.toPx()
        drawRoundRect(
            color = color,
            size = size,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(16f, 12f), 0f
                )
            )
        )
    }

@Composable
private fun HabitCard(
    habit: Habit,
    today: String,
    onCheckin: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (habit.type == Habit.Type.BUILD) {
            val streak = habit.buildStreak(today)
            val checked = habit.checkedOn(today)
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(
                                if (checked) {
                                    IncomeGreen.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = habit.emoji, fontSize = 22.sp)
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.habit_build_streak, streak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 最近 7 天小圆点（打卡日实心）
                    LastSevenDots(
                        habit = habit,
                        today = today,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    // 打卡按钮：整块右侧大按钮，单手拇指可达
                    if (checked) {
                        OutlinedButton(
                            onClick = onCheckin,
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text(stringResource(R.string.habit_checked_today))
                        }
                    } else {
                        Button(
                            onClick = onCheckin,
                            modifier = Modifier.heightIn(min = 52.dp)
                        ) {
                            Text(stringResource(R.string.habit_checkin_today))
                        }
                    }
                    HabitMenuButton(onOpen = { menuOpen = true })
                }
            }
        } else {
            // 戒断卡片：超大坚持天数为主视觉
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = habit.emoji, fontSize = 22.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = habit.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.habit_quit_since),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            R.string.habit_quit_days_big,
                            habit.quitDays(today)
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    OutlinedButton(onClick = onReset) {
                        Text(stringResource(R.string.habit_relapse))
                    }
                }
                HabitMenuButton(onOpen = { menuOpen = true })
            }
        }
    }

    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.habit_delete_title)) },
            onClick = {
                menuOpen = false
                onDelete()
            }
        )
    }
}

/** 卡片右上角「⋯」操作按钮。 */
@Composable
private fun HabitMenuButton(onOpen: () -> Unit) {
    IconButton(onClick = onOpen) {
        Icon(
            Icons.Filled.MoreVert,
            contentDescription = stringResource(R.string.home_menu),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 最近 7 天打卡小圆点：7 个小圆，打卡日实心主色，未打卡浅灰。 */
@Composable
private fun LastSevenDots(
    habit: Habit,
    today: String,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (6 downTo 0).forEach { back ->
            val day = DateUtils.addDays(today, -back)
            val checked = habit.checkedOn(day)
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}

/** 新建习惯 Sheet：名称 + emoji + 类型 + 预设快捷填充。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitCreateSheet(
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String, type: Habit.Type) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var emoji by rememberSaveable { mutableStateOf(habitEmojiCandidates.first()) }
    var type by rememberSaveable { mutableStateOf(Habit.Type.BUILD) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
        ) {
            Text(
                stringResource(R.string.habit_create_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == Habit.Type.BUILD,
                    onClick = { type = Habit.Type.BUILD },
                    label = { Text(stringResource(R.string.habit_type_build)) }
                )
                FilterChip(
                    selected = type == Habit.Type.QUIT,
                    onClick = { type = Habit.Type.QUIT },
                    label = { Text(stringResource(R.string.habit_type_quit)) }
                )
            }
            Spacer(Modifier.height(8.dp))

            // 预设快捷填充
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                habitPresets.filter { it.type == type }.forEach { preset ->
                    FilterChip(
                        selected = name == preset.name,
                        onClick = {
                            name = preset.name
                            emoji = preset.emoji
                            nameError = false
                        },
                        label = { Text("${preset.emoji} ${preset.name}") }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(12)
                    nameError = false
                },
                label = { Text(stringResource(R.string.habit_name_hint)) },
                isError = nameError,
                supportingText = {
                    if (nameError) Text(stringResource(R.string.habit_name_error))
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            Text(
                stringResource(R.string.habit_pick_emoji),
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                habitEmojiCandidates.forEach { candidate ->
                    FilterChip(
                        selected = emoji == candidate,
                        onClick = { emoji = candidate },
                        label = { Text(candidate) }
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onCreate(name.trim(), emoji, type)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_create))
            }
        }
    }
}
