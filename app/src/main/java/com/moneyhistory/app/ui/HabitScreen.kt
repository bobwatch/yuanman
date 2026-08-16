package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.DateUtils
import com.moneyhistory.app.Habit
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.R
import com.moneyhistory.app.buildStreak
import com.moneyhistory.app.checkedOn
import com.moneyhistory.app.habitEmojiCandidates
import com.moneyhistory.app.quitDays
import kotlinx.coroutines.delay

private data class HabitPreset(val emoji: String, val nameRes: Int, val type: Habit.Type)

// 名称上限取英文预设最长（No staying up late = 18 字符），保证点选预设不被截断
private const val HABIT_NAME_MAX = 18

// 预设名称走字符串资源：点选后以当前语言写入（用户可见数据，不迁移历史）
private val habitPresets = listOf(
    HabitPreset("💪", R.string.habit_preset_workout, Habit.Type.BUILD),
    HabitPreset("📚", R.string.habit_preset_study, Habit.Type.BUILD),
    HabitPreset("📖", R.string.habit_preset_read, Habit.Type.BUILD),
    HabitPreset("🌅", R.string.habit_preset_early, Habit.Type.BUILD),
    HabitPreset("🥤", R.string.habit_preset_no_soda, Habit.Type.QUIT),
    HabitPreset("🍺", R.string.habit_preset_no_alcohol, Habit.Type.QUIT),
    HabitPreset("🚬", R.string.habit_preset_no_smoke, Habit.Type.QUIT),
    HabitPreset("🌙", R.string.habit_preset_no_late, Habit.Type.QUIT)
)

/** 打卡 Tab：习惯列表 + 行内新建表单（不再用弹窗，缩短操作路径）。 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class
)
@Composable
fun HabitScreen(viewModel: MainViewModel) {
    val habits by viewModel.habits.collectAsStateWithLifecycle()
    // 「今天」随真实时间推进（每 30 秒校一次）：跨零点后自动切到新的一天，
    // 打卡按钮与「今天已打卡」状态不会停留在昨天；值未变时不触发重组
    var today by remember { mutableStateOf(DateUtils.today()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            today = DateUtils.today()
        }
    }
    val context = LocalContext.current
    val view = LocalView.current
    val deletedText = stringResource(R.string.common_deleted)
    val resetDoneText = stringResource(R.string.habit_reset_done)

    // 正在进行的习惯排最前：build（今天/昨天有打卡）或 quit（今天未破戒）
    val sortedHabits = remember(habits, today) {
        habits.sortedByDescending { it.isActiveToday(today) }
    }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Habit?>(null) }
    var resetTarget by remember { mutableStateOf<Habit?>(null) }

    // 重复点击底部「打卡」Tab：列表滚回顶部
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        viewModel.tabReclick.collect { route ->
            if (route == "habits") listState.animateScrollToItem(0)
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        YuanmanHeader(
            title = stringResource(R.string.tab_habits),
            subtitle = stringResource(R.string.habit_header_sub)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .padding(top = 4.dp)
        ) {
            // 行内新建表单：直接在主列表里操作
            if (showCreate) {
                item(key = "create") {
                    InlineHabitForm(
                        onCancel = { showCreate = false },
                        onCreate = { name, emoji, type ->
                            viewModel.addHabit(
                                Habit(
                                    name = name,
                                    emoji = emoji,
                                    type = type,
                                    startDate = DateUtils.today()
                                )
                            )
                            viewModel.postMessage(
                                context.getString(R.string.habit_created, name),
                                MessageVariant.SUCCESS
                            )
                            showCreate = false
                        }
                    )
                }
            }

            if (habits.isEmpty() && !showCreate) {
                item(key = "empty") {
                    EmptyState(
                        emoji = "✅",
                        title = stringResource(R.string.habit_empty_title),
                        subtitle = stringResource(R.string.habit_empty_sub),
                        // 空态直达创建：想动手时不用再找虚线卡
                        actionLabel = stringResource(R.string.habit_add_new),
                        onAction = { showCreate = true }
                    )
                }
            }

            items(sortedHabits, key = { it.id }) { habit ->
                Box(Modifier.animateItemPlacement()) {
                    HabitCard(
                        habit = habit,
                        today = today,
                        onCheckin = {
                            // 打卡成功是今天的「小成就」：重触感确认；取消打卡轻触感告知
                            val checked = viewModel.toggleCheckin(habit.id)
                            if (checked) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                viewModel.postMessage(
                                    context.getString(R.string.habit_unchecked),
                                    MessageVariant.INFO
                                )
                            }
                        },
                        onReset = { resetTarget = habit },
                        onDelete = { deleteTarget = habit }
                    )
                }
            }

            if (!showCreate) {
                item(key = "add") {
                    DashedAddCard(onClick = { showCreate = true })
                }
            }
        }
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
                    // 删除是不可逆动作：确认时给重触感，动作「落定」
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.removeHabit(habit.id)
                    viewModel.postMessage(deletedText, MessageVariant.INFO)
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
                    // 破戒重置会清零坚持天数：重触感确认，避免误触后追悔
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.resetHabit(habit.id)
                    viewModel.postMessage(resetDoneText, MessageVariant.SUCCESS)
                    resetTarget = null
                }) {
                    Text(
                        stringResource(R.string.common_confirm),
                        // 破戒重置不是删除：用主色而非错误红，别让「坦白」看起来像惩罚
                        color = MaterialTheme.colorScheme.primary
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

/** 行内新建习惯表单：类型 + 预设 + 名称 + emoji + 创建/取消。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InlineHabitForm(
    onCancel: () -> Unit,
    onCreate: (name: String, emoji: String, type: Habit.Type) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var emoji by rememberSaveable { mutableStateOf(habitEmojiCandidates.first()) }
    var type by rememberSaveable { mutableStateOf(Habit.Type.BUILD) }
    val view = LocalView.current

    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.habit_create_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == Habit.Type.BUILD,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        type = Habit.Type.BUILD
                    },
                    label = { Text(stringResource(R.string.habit_type_build)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
                FilterChip(
                    selected = type == Habit.Type.QUIT,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        type = Habit.Type.QUIT
                    },
                    label = { Text(stringResource(R.string.habit_type_quit)) },
                    modifier = Modifier.heightIn(min = 48.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // 预设快捷填充
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                habitPresets.filter { it.type == type }.forEach { preset ->
                    val presetName = stringResource(preset.nameRes)
                    FilterChip(
                        selected = name == presetName,
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            name = presetName
                            emoji = preset.emoji
                            nameError = false
                        },
                        label = { Text("${preset.emoji} $presetName") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(HABIT_NAME_MAX)
                    nameError = false
                },
                label = { Text(stringResource(R.string.habit_name_hint)) },
                isError = nameError,
                supportingText = {
                    if (nameError) {
                        Text(stringResource(R.string.habit_name_error))
                    } else if (name.isNotEmpty()) {
                        // 边输边显示计数，避免「打字突然卡住」的错觉（与分类命名一致）
                        Text(
                            stringResource(
                                R.string.habit_name_count,
                                name.length,
                                HABIT_NAME_MAX
                            )
                        )
                    }
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
            EmojiPickerRow(
                candidates = habitEmojiCandidates,
                selected = emoji,
                onSelect = { emoji = it }
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                    } else {
                        onCreate(name.trim(), emoji, type)
                    }
                },
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.common_create),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
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
            .pressScale()
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
    // 已打卡状态下点击按钮是「取消打卡」，属于不可逆操作，先确认
    var confirmUncheck by remember { mutableStateOf(false) }
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        if (habit.type == Habit.Type.BUILD) {
            val streak = habit.buildStreak(today)
            val checked = habit.checkedOn(today)
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 已打卡：图标右下角叠对勾角标，打卡瞬间弹入
                    Box {
                            IconTile(
                                icon = habitIcon(habit.emoji),
                                tint = habitIconColor(habit),
                                container = habitIconColor(habit).copy(alpha = if (checked) 0.2f else 0.12f),
                                size = 46.dp,
                                iconSize = 22.dp
                            )
                            CheckBadge(
                                checked = checked,
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.habit_build_streak, streak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 最近 7 天小圆点（打卡日实心）
                    WeekDots(
                        today = today,
                        filled = { habit.checkedOn(it) },
                        dotColor = MaterialTheme.colorScheme.primary,
                        description = { lit ->
                            stringResource(R.string.habit_week_summary, lit)
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    HabitMenuButton(onDelete = onDelete)
                }
                Spacer(Modifier.height(10.dp))
                // 打卡按钮：整行宽，窄屏/大字号下始终可点
                if (checked) {
                    OutlinedButton(
                        onClick = { confirmUncheck = true },
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.habit_checked_today))
                    }
                } else {
                    Button(
                        onClick = onCheckin,
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.habit_checkin_today))
                    }
                }
            }
        } else {
            // 戒断卡片：超大坚持天数为主视觉
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(
                        icon = habitIcon(habit.emoji),
                        tint = habitIconColor(habit),
                        container = habitIconColor(habit).copy(alpha = 0.12f),
                        size = 46.dp,
                        iconSize = 22.dp
                    )
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.habit_quit_since),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    // 最近 7 天小圆点：未破戒日实心（橙色与戒断身份一致），
                    // 与打卡卡的 7 天点条同一套视觉节奏
                    WeekDots(
                        today = today,
                        filled = { day -> habit.resets.none { it == day } },
                        dotColor = habitIconColor(habit),
                        description = { lit ->
                            stringResource(R.string.habit_week_clean_summary, lit)
                        },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    HabitMenuButton(onDelete = onDelete)
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(
                            R.string.habit_quit_days_big,
                            habit.quitDays(today)
                        ),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        // 戒断用橙色保持卡片身份统一（icon 同为橙色），不用品牌蓝
                        color = habitIconColor(habit),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = onReset,
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.habit_relapse))
                    }
                }
            }
        }
    }

    if (confirmUncheck) {
        AlertDialog(
            onDismissRequest = { confirmUncheck = false },
            title = { Text(stringResource(R.string.habit_uncheck_title)) },
            text = {
                Text(stringResource(R.string.habit_uncheck_msg, habit.name))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUncheck = false
                    onCheckin()
                }) {
                    Text(
                        stringResource(R.string.habit_uncheck_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUncheck = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

/** 卡片右上角删除按钮：直接弹确认框（与全局删除交互一致，少一次点击）。 */
@Composable
private fun HabitMenuButton(onDelete: () -> Unit) {
    IconButton(onClick = onDelete) {
        Icon(
            Icons.Filled.Delete,
            contentDescription = stringResource(R.string.habit_delete_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 打卡对勾角标：打卡瞬间以弹簧动画弹入图标右下角。 */
@Composable
private fun CheckBadge(checked: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = checked,
        modifier = modifier,
        enter = scaleIn(
            initialScale = 0.3f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium)
        )
    ) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(11.dp)
            )
        }
    }
}

/** 最近 7 天小圆点（与打卡卡同一套视觉语言）：[filled] 判定某天是否
 *  点亮（build 卡 = 打卡日；quit 卡 = 未破戒日），[dotColor] 按习惯身份取色，
 *  [description] 按点亮天数生成读屏文案。 */
@Composable
private fun WeekDots(
    today: String,
    filled: (String) -> Boolean,
    dotColor: Color,
    description: @Composable (Int) -> String,
    modifier: Modifier = Modifier
) {
    val days = (6 downTo 0).map { DateUtils.addDays(today, -it) }
    val litCount = days.count(filled)
    val weekDesc = description(litCount)
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = weekDesc
        },
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        days.forEach { day ->
            val lit = filled(day)
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (lit) {
                            dotColor
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    )
            )
        }
    }
}

/** 正在进行的习惯：build（今天/昨天有打卡）或 quit（今天未破戒）。 */
private fun Habit.isActiveToday(today: String): Boolean = when (type) {
    Habit.Type.BUILD -> checkedOn(today) || checkedOn(DateUtils.addDays(today, -1))
    Habit.Type.QUIT -> resets.none { it == today }
}
