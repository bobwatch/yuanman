package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.with
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.DateUtils
import com.moneyhistory.app.Habit
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.R
import com.moneyhistory.app.buildStreak
import com.moneyhistory.app.checkedOn
import com.moneyhistory.app.codePointLength
import com.moneyhistory.app.habitEmojiCandidates
import com.moneyhistory.app.quitDays
import com.moneyhistory.app.truncateByCodePoints
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

/** 列表排序位：待办 build → 已打卡 build → 干净 quit → 破戒 quit。 */
private fun habitRank(habit: Habit, today: String): Int = when (habit.type) {
    Habit.Type.BUILD -> if (habit.checkedOn(today)) 1 else 0
    Habit.Type.QUIT -> if (habit.resets.none { it == today }) 2 else 3
}

/** 打卡 Tab：今日进度总览 + 习惯卡片列表（点击看详情，长按删）。 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
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
    val undoText = stringResource(R.string.home_undo)
    val resetDoneText = stringResource(R.string.habit_reset_done)
    val uncheckedText = stringResource(R.string.habit_unchecked)

    val sortedHabits = remember(habits, today) {
        habits.sortedWith(
            compareBy({ habitRank(it, today) }, { it.name })
        )
    }

    var showCreate by rememberSaveable { mutableStateOf(false) }
    // 每次打开新建弹层换 key：清掉上次输入（rememberSaveable 在弹层关闭后仍会恢复）
    var createEpoch by rememberSaveable { mutableIntStateOf(0) }
    var detailId by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Habit?>(null) }
    var resetTarget by remember { mutableStateOf<Habit?>(null) }

    // 详情弹层跟随最新数据（补卡后列表刷新，弹层内网格同步点亮）
    val detailHabit = detailId?.let { id -> habits.firstOrNull { it.id == id } }

    // 重复点击底部「打卡」Tab：列表滚回顶部
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        viewModel.tabReclick.collect { route ->
            if (route == "habits") listState.animateScrollToItem(0)
        }
    }

    // 页头随列表滚动；滚出页头后浅色主题切深状态栏图标（白图标在浅底上看不见）
    val scrolledPastHeader = remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first != null && (first.index > 0 || first.offset < 0)
        }
    }
    ScrollingTabStatusBar(scrolledPastHeader.value)

    // 底部导航栏悬浮在页面之上（见 MainActivity），列表底部预留其高度
    Column(Modifier.fillMaxSize()) {
        // 今日进度总览：只统计 build 习惯，全部完成给庆祝态
        val buildHabits = remember(habits, today) {
            habits.filter { it.type == Habit.Type.BUILD }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item(key = "header") {
                YuanmanHeader(
                    title = stringResource(R.string.tab_habits),
                    subtitle = stringResource(R.string.habit_header_sub)
                )
            }
            if (buildHabits.isNotEmpty()) {
                item(key = "progress") {
                    TodayProgressCard(
                        checked = buildHabits.count { it.checkedOn(today) },
                        total = buildHabits.size
                    )
                }
            }

            if (habits.isEmpty()) {
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
                        onOpenDetail = { detailId = habit.id },
                        onCheckin = {
                            // 打卡成功是今天的「小成就」：重触感确认；取消打卡轻触感告知
                            val checked = viewModel.toggleCheckin(habit.id)
                            if (checked) {
                                view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            } else {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                // 取消打卡不弹确认框：直接撤销 + 可点 Toast 找回
                                viewModel.postMessage(
                                    uncheckedText,
                                    MessageVariant.INFO,
                                    undoText
                                ) {
                                    viewModel.toggleCheckin(habit.id)
                                }
                            }
                        },
                        onReset = { resetTarget = habit },
                        onDelete = { deleteTarget = habit }
                    )
                }
            }

            // 空态时不再放虚线卡：空态自带「新建」按钮，双入口变重复
            if (habits.isNotEmpty()) {
                item(key = "add") {
                    DashedAddCard(onClick = { showCreate = true })
                }
            }
        }
    }

    if (showCreate) {
        key(createEpoch) {
            HabitCreateSheet(
                onDismiss = {
                    showCreate = false
                    createEpoch++
                },
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
                    createEpoch++
                }
            )
        }
    }

    detailHabit?.let { habit ->
        HabitDetailSheet(
            habit = habit,
            today = today,
            onToggleDay = { day ->
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                viewModel.toggleCheckin(habit.id, day)
            },
            onDismiss = { detailId = null }
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
                    // 删除是不可逆动作：确认时给重触感，动作「落定」；
                    // 误删可点 Toast 撤销（HabitsStore 保留原 id，恢复不丢打卡记录）
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    viewModel.removeHabit(habit.id)
                    viewModel.postMessage(deletedText, MessageVariant.INFO, undoText) {
                        viewModel.addHabit(habit)
                    }
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

/** 今日打卡进度卡片：X/Y 计数 + 平滑进度条，全部完成时切换庆祝态。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TodayProgressCard(checked: Int, total: Int) {
    val done = checked >= total
    val progress by animateFloatAsState(
        targetValue = if (total > 0) checked / total.toFloat() else 0f,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "todayProgress"
    )
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (done) {
                        stringResource(R.string.habit_today_done)
                    } else {
                        stringResource(R.string.habit_today_progress, checked, total)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (done) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                )
            }
            Spacer(Modifier.width(12.dp))
            // 全部完成：对勾角标弹入，与打卡卡同款庆祝语言
            CheckBadge(
                checked = done,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

/** 新建习惯底部弹层：类型 + 预设 + 名称 + emoji。
 *  与 GoalCreateSheet 同款 Dialog 方案（M3 sheet 与输入法问题见其注释）。 */
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
    val view = LocalView.current
    // 收起动画：先下滑收起再真正关闭；协程随组合取消，组合被替换时不会误关新弹层
    var dismissing by remember { mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()
    fun closeSheet(after: () -> Unit = {}) {
        if (dismissing) return
        dismissing = true
        sheetScope.launch {
            delay(300)
            after()
        }
    }

    Dialog(
        onDismissRequest = { closeSheet(onDismiss) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogEdgeToEdge()
        AnimatedVisibility(
            visible = !dismissing,
            enter = slideInVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) { it } + fadeIn(animationSpec = tween(240)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) { it } + fadeOut(animationSpec = tween(220))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        SheetDragHandle(onDismiss = { closeSheet(onDismiss) })

                        Text(
                            stringResource(R.string.habit_create_title),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(16.dp))

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
                                // 按码点截断：emoji 占 2 个 UTF-16 单元，take() 会把表情切半成乱码
                                name = truncateByCodePoints(it, HABIT_NAME_MAX)
                                nameError = false
                            },
                            label = { Text(stringResource(R.string.habit_name_hint)) },
                            isError = nameError,
                            supportingText = {
                                // 计数行常驻：计数器出现时不顶动下方布局（与分类命名一致）
                                if (nameError) {
                                    Text(stringResource(R.string.habit_name_error))
                                } else {
                                    Text(
                                        stringResource(
                                            R.string.habit_name_count,
                                            codePointLength(name),
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
                    }
                }
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

/** 习惯卡片：点击看 30 天详情，长按弹删除菜单；已打卡降饱和。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitCard(
    habit: Habit,
    today: String,
    onOpenDetail: () -> Unit,
    onCheckin: () -> Unit,
    onReset: () -> Unit,
    onDelete: () -> Unit
) {
    val view = LocalView.current
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .pressScale()
            .combinedClickable(
                onClick = onOpenDetail,
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    menuOpen = true
                }
            )
    ) {
        if (habit.type == Habit.Type.BUILD) {
            BuildCardContent(habit, today, onCheckin)
        } else {
            QuitCardContent(habit, today, onReset)
        }
    }

    // 长按弹出的删除菜单：删除仍走确认框（会清掉打卡历史，先让用户看清楚）
    DropdownMenu(
        expanded = menuOpen,
        onDismissRequest = { menuOpen = false }
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.habit_delete_title)) },
            leadingIcon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                menuOpen = false
                onDelete()
            }
        )
    }
}

/** build 卡：图标 + 对勾角标 + 名称 + 连续天数 + 7 天点 + 打卡按钮。 */
@Composable
private fun BuildCardContent(habit: Habit, today: String, onCheckin: () -> Unit) {
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
                    tint = habitIconColor(habit).copy(alpha = if (checked) 0.5f else 1f),
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
                    overflow = TextOverflow.Ellipsis,
                    // 已打卡信息行整体降饱和：完成的事不必再抢视线
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = if (streak == 0) {
                        // 0 天不报「0-day streak」的丧气数：改成邀请式的鼓励
                        stringResource(R.string.habit_streak_zero)
                    } else {
                        stringResource(R.string.habit_build_streak, streak)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
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
        }
        Spacer(Modifier.height(10.dp))
        // 打卡按钮：整行宽，窄屏/大字号下始终可点；已打卡时点击直接取消（可撤销）
        if (checked) {
            OutlinedButton(
                onClick = onCheckin,
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
}

/** quit 卡：超大坚持天数为主视觉，天数切换带滚动动画。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun QuitCardContent(habit: Habit, today: String, onReset: () -> Unit) {
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
        }
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 坚持天数滚动动画：天数变化时旧数字上卷、新数字从下滚入
            val days = habit.quitDays(today)
            AnimatedContent(
                targetState = days,
                transitionSpec = {
                    (slideInVertically(
                        animationSpec = tween(300, easing = FastOutSlowInEasing)
                    ) { it / 3 } + fadeIn(animationSpec = tween(200))) with
                        (slideOutVertically(
                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                        ) { -it / 3 } + fadeOut(animationSpec = tween(200)))
                },
                label = "quitDays"
            ) { day ->
                Text(
                    text = stringResource(
                        R.string.habit_quit_days_big,
                        day
                    ),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    // 戒断用橙色保持卡片身份统一（icon 同为橙色），不用品牌蓝
                    color = habitIconColor(habit),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.habit_relapse))
            }
        }
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

/** 习惯详情弹层：30 天圆点网格（build 可点补卡）+ 连续天数 + 本月完成率。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitDetailSheet(
    habit: Habit,
    today: String,
    onToggleDay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val isBuild = habit.type == Habit.Type.BUILD
    // 收起动画：先下滑收起再真正关闭；协程随组合取消，组合被替换时不会误关新弹层
    var dismissing by remember { mutableStateOf(false) }
    val sheetScope = rememberCoroutineScope()
    fun closeSheet(after: () -> Unit = {}) {
        if (dismissing) return
        dismissing = true
        sheetScope.launch {
            delay(300)
            after()
        }
    }

    Dialog(
        onDismissRequest = { closeSheet(onDismiss) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogEdgeToEdge()
        AnimatedVisibility(
            visible = !dismissing,
            enter = slideInVertically(
                animationSpec = tween(320, easing = FastOutSlowInEasing)
            ) { it } + fadeIn(animationSpec = tween(240)),
            exit = slideOutVertically(
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) { it } + fadeOut(animationSpec = tween(220))
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .imePadding(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 3.dp
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 32.dp)
                    ) {
                        SheetDragHandle(onDismiss = { closeSheet(onDismiss) })

                        // 头部：图标 + 名称 + 类型徽标
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(
                                icon = habitIcon(habit.emoji),
                                tint = habitIconColor(habit),
                                container = habitIconColor(habit).copy(alpha = 0.12f),
                                size = 52.dp,
                                iconSize = 26.dp
                            )
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = habit.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = stringResource(
                                        if (isBuild) {
                                            R.string.habit_type_build
                                        } else {
                                            R.string.habit_type_quit
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = habitIconColor(habit)
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))

                        // 统计双卡：连续天数/已坚持 + 近 30 天概况
                        val days30 = (0 until 30).map {
                            DateUtils.addDays(today, -it)
                        }
                        val lit30 = days30.count { day ->
                            if (isBuild) {
                                habit.checkedOn(day)
                            } else {
                                habit.resets.none { it == day }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val bigNumber = if (isBuild) {
                                habit.buildStreak(today)
                            } else {
                                habit.quitDays(today)
                            }
                            val bigLabel = stringResource(
                                if (isBuild) {
                                    R.string.habit_detail_streak_label
                                } else {
                                    R.string.habit_detail_clean_label
                                }
                            )
                            DetailStat(
                                number = bigNumber,
                                label = bigLabel,
                                accent = habitIconColor(habit),
                                modifier = Modifier.weight(1f)
                            )
                            DetailStat(
                                number = lit30,
                                label = stringResource(
                                    if (isBuild) {
                                        R.string.habit_30_done_summary
                                    } else {
                                        R.string.habit_30_clean_summary
                                    },
                                    lit30
                                ),
                                accent = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(Modifier.height(16.dp))

                        // 30 天圆点网格：6 列 × 5 行，最旧在左上、今天在右下
                        val monthStart = "${today.substring(0, 7)}-01"
                        val monthDays = DateUtils.daysBetween(monthStart, today) + 1
                        val monthFilled = (0 until monthDays).count { offset ->
                            val day = DateUtils.addDays(monthStart, offset)
                            if (isBuild) {
                                habit.checkedOn(day)
                            } else {
                                habit.resets.none { it == day }
                            }
                        }
                        val monthRate = if (monthDays > 0) {
                            monthFilled * 100 / monthDays
                        } else {
                            0
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.habit_detail_30d),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = stringResource(
                                    if (isBuild) {
                                        R.string.habit_detail_month_done
                                    } else {
                                        R.string.habit_detail_month_clean
                                    },
                                    monthRate
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = habitIconColor(habit)
                            )
                        }
                        Spacer(Modifier.height(10.dp))

                        val summary = stringResource(
                            if (isBuild) {
                                R.string.habit_30_done_summary
                            } else {
                                R.string.habit_30_clean_summary
                            },
                            lit30
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clearAndSetSemantics {
                                    contentDescription = summary
                                }
                        ) {
                            (0 until 5).forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    (0 until 6).forEach { col ->
                                        val day = DateUtils.addDays(
                                            today, row * 6 + col - 29
                                        )
                                        DayDot(
                                            day = day,
                                            isToday = day == today,
                                            filled = if (isBuild) {
                                                habit.checkedOn(day)
                                            } else {
                                                habit.resets.none { it == day }
                                            },
                                            clickable = isBuild,
                                            onClick = { onToggleDay(day) }
                                        )
                                    }
                                }
                            }
                        }

                        if (isBuild) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.habit_detail_tap_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 详情统计小卡：大数字 + 说明文字。 */
@Composable
private fun DetailStat(
    number: Int,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = "$number",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
        )
    }
}

/** 30 天网格里的单日圆点：实心 = 已打卡/守住，今天带主色描边。 */
@Composable
private fun DayDot(
    day: String,
    isToday: Boolean,
    filled: Boolean,
    clickable: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val modifier = Modifier
        .size(44.dp)
        .clip(CircleShape)
        .background(
            when {
                filled -> primary.copy(alpha = 0.16f)
                isToday -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceContainerHighest
            }
        )
        .then(
            if (isToday) {
                Modifier.border(2.dp, primary, CircleShape)
            } else {
                Modifier
            }
        )
        .then(
            if (clickable) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        )
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.substring(8).toInt().toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal,
            color = when {
                filled -> primary
                isToday -> primary
                else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            }
        )
    }
}
