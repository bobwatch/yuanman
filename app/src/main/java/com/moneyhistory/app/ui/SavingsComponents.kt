package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moneyhistory.app.Goal
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.dailySavingRate
import com.moneyhistory.app.goalEmojiCandidates
import java.util.Calendar
import kotlin.math.roundToInt

// 目标名称上限：与 emoji 并排时一屏可读，输入时边输边显示计数
private const val GOAL_NAME_MAX = 12

/** 圆环进度（Canvas 自绘，animateFloatAsState 过渡动画），中心可放文字。 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val animated by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = androidx.compose.animation.core.tween(600),
        label = "ring"
    )
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    androidx.compose.foundation.layout.Box(
        // 环形进度对读屏暴露进度条语义（与图表 TalkBack 描述同一标准），
        // 中心文字并入同一节点读出
        modifier = modifier.semantics(mergeDescendants = true) {
            progressBarRangeInfo = ProgressBarRangeInfo(
                current = animated.coerceIn(0f, 1f),
                range = 0f..1f
            )
        },
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(
            Modifier.matchParentSize()
        ) {
            val strokeWidth = size.minDimension * 0.12f
            val diameter = size.minDimension - strokeWidth
            val topLeft = androidx.compose.ui.geometry.Offset(
                (size.width - diameter) / 2f,
                (size.height - diameter) / 2f
            )
            val arcSize = androidx.compose.ui.geometry.Size(diameter, diameter)
            val style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = strokeWidth,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            // 轨道
            drawArc(
                color = track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
            // 进度
            drawArc(
                color = primary,
                startAngle = -90f,
                sweepAngle = 360f * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = style
            )
        }
        content()
    }
}

/** 目标卡片的预测文案（按近 30 天存入速度推算）。 */
@Composable
private fun goalPredictionText(goal: Goal): String {
    val percent = (goal.progress * 100).roundToInt().coerceAtMost(100)
    if (goal.savedCents >= goal.targetCents) {
        return stringResource(R.string.goal_prediction_done)
    }
    val rate = goal.dailySavingRate()
    if (rate <= 0) return stringResource(R.string.goal_prediction_percent, percent)
    val daysLeft = (goal.targetCents - goal.savedCents + rate - 1) / rate
    if (daysLeft <= 0 || daysLeft > 36_500) {
        return stringResource(R.string.goal_prediction_percent, percent)
    }
    val cal = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, daysLeft.toInt())
    }
    return stringResource(
        R.string.goal_prediction_date,
        cal.get(Calendar.MONTH) + 1,
        cal.get(Calendar.DAY_OF_MONTH)
    )
}

/** 首页目标卡片（LazyRow 项）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCard(goal: Goal, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(210.dp)
            .pressScale()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(
                    icon = goalIcon(goal.emoji),
                    tint = MaterialTheme.colorScheme.primary,
                    container = MaterialTheme.colorScheme.primaryContainer,
                    size = 32.dp,
                    iconSize = 16.dp
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = goal.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(
                    progress = goal.progress,
                    modifier = Modifier.size(56.dp)
                ) {
                    Text(
                        text = stringResource(
                            R.string.goal_progress_percent,
                            (goal.progress * 100).roundToInt().coerceAtMost(100)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        text = stringResource(
                            R.string.goal_card_saved,
                            MoneyUtils.formatCents(goal.savedCents)
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.goal_card_target,
                            MoneyUtils.formatCents(goal.targetCents)
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = goalPredictionText(goal),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 「+ 新建攒钱目标」占位卡片。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalCard(onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .width(140.dp)
            .pressScale()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .height(110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.goal_add_card),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 目标列表弹层：多个目标时点首页「攒钱目标」行弹出，横向滑动选目标或新建。
 * 与 GoalCreateSheet 同款底部 Dialog（M3 sheet 与输入法问题见 GoalCreateSheet 注释）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalListSheet(
    goals: List<Goal>,
    onGoalClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 弹层窗口透明系统条：背景（品牌蓝窗底）铺满屏幕，sheet 浮在上面
        DialogEdgeToEdge()
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
                        .padding(bottom = 28.dp)
                ) {
                    SheetDragHandle(onDismiss = onDismiss)
                    Text(
                        text = stringResource(R.string.home_section_goal),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(goals, key = { it.id }) { goal ->
                            GoalCard(goal = goal, onClick = { onGoalClick(goal.id) })
                        }
                        item {
                            AddGoalCard(onClick = onCreateClick)
                        }
                    }
                }
            }
        }
    }
}

/**
 * 新建攒钱目标底部弹层：名称 + 目标金额 + emoji + 可选目标日期。
 * 用 Dialog 而非 ModalBottomSheet：M3 1.2.1 的 sheet 与输入法互相踩坏
 * （见 TransactionSheet 注释），Dialog + decorFitsSystemWindows=false
 * 能收到 IME inset，配合 imePadding 键盘弹起时弹层自动上移。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GoalCreateSheet(
    onDismiss: () -> Unit,
    onCreate: (Goal) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var amount by rememberSaveable { mutableStateOf("") }
    var amountError by rememberSaveable { mutableStateOf(false) }
    var nameError by rememberSaveable { mutableStateOf(false) }
    var emoji by rememberSaveable { mutableStateOf(goalEmojiCandidates.first()) }
    var deadlineEnabled by rememberSaveable { mutableStateOf(false) }
    var deadlineMillis by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // 弹层窗口透明系统条：背景（品牌蓝窗底）铺满屏幕，sheet 浮在上面
        DialogEdgeToEdge()
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
                    SheetDragHandle(onDismiss = onDismiss)

                    Text(
                        stringResource(R.string.goal_create_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(GOAL_NAME_MAX)
                            nameError = false
                        },
                        label = { Text(stringResource(R.string.goal_name_hint)) },
                        isError = nameError,
                        supportingText = {
                            if (nameError) {
                                Text(stringResource(R.string.goal_name_error))
                            } else if (name.isNotEmpty()) {
                                // 边输边显示计数，避免「打字突然卡住」的错觉（与分类命名一致）
                                Text(
                                    stringResource(
                                        R.string.goal_name_count,
                                        name.length,
                                        GOAL_NAME_MAX
                                    )
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = amount,
                        onValueChange = {
                            amount = it
                            amountError = false
                        },
                        label = { Text(stringResource(R.string.goal_target_hint)) },
                        isError = amountError,
                        supportingText = {
                            if (amountError) Text(stringResource(R.string.sheet_amount_error))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        stringResource(R.string.goal_emoji_pick),
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(Modifier.height(8.dp))
                    EmojiPickerRow(
                        candidates = goalEmojiCandidates,
                        selected = emoji,
                        onSelect = { emoji = it }
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 无障碍：开关自身读出身旁文案，TalkBack 不悬空
                        val deadlineLabel = stringResource(R.string.goal_deadline_toggle)
                        Text(
                            text = deadlineLabel,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = deadlineEnabled,
                            onCheckedChange = { deadlineEnabled = it },
                            modifier = Modifier.semantics {
                                contentDescription = deadlineLabel
                            }
                        )
                    }
                    if (deadlineEnabled) {
                        DatePickerButton(
                            label = stringResource(R.string.goal_deadline_label),
                            millis = deadlineMillis,
                            onDateSelected = { deadlineMillis = it }
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    Button(
                        onClick = {
                            val cents = MoneyUtils.parseToCents(amount)
                            when {
                                name.isBlank() -> nameError = true
                                // 目标金额必须大于 0：0 元目标会让进度恒满、永远「已完成」
                                cents == null || cents <= 0 -> amountError = true
                                else -> {
                                    onCreate(
                                        Goal(
                                            name = name.trim(),
                                            emoji = emoji,
                                            targetCents = cents,
                                            deadlineMillis =
                                            if (deadlineEnabled) deadlineMillis else null
                                        )
                                    )
                                    onDismiss()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.common_create))
                    }
                }
            }
        }
    }
}

/**
 * 存入/取出对话框：内置九宫格键盘输入（支持连加），
 * [extraContent] 可附加「同时记一笔支出流水」开关等。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AmountPadDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
    maxCents: Long? = null,
    extraContent: @Composable () -> Unit = {}
) {
    var segments by rememberSaveable { mutableStateOf(listOf("")) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    val liveCents = segments.mapNotNull { MoneyUtils.parseToCents(it) }.sum()
    val view = LocalView.current

    fun onNumKey(key: String) {
        errorRes = null
        val list = segments.toMutableList()
        val last = list.last()
        when (key) {
            "⌫" -> {
                if (last.isEmpty()) {
                    if (list.size > 1) list.removeAt(list.size - 1)
                } else {
                    list[list.size - 1] = last.dropLast(1)
                }
            }
            "+" -> if (last.isNotEmpty()) list.add("")
            "." -> when {
                last.isEmpty() -> list[list.size - 1] = "0."
                !last.contains(".") -> list[list.size - 1] = last + "."
            }
            else -> when {
                last == "0" -> list[list.size - 1] = key
                last.contains(".") && last.substringAfter(".").length >= 2 -> Unit
                !last.contains(".") && last.length >= 7 -> Unit
                else -> list[list.size - 1] = last + key
            }
        }
        segments = list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 内容含整块数字键盘，小屏/大字号下可能超高：
            // 加滚动兜底，键盘行与按钮永不超出窗口被裁
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    Text(
                        text = segments.joinToString(" + ").ifEmpty { "0" },
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "≈ ${MoneyUtils.formatCents(liveCents)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                extraContent()
                Spacer(Modifier.height(8.dp))
                NumPad(
                    onKey = { onNumKey(it) },
                    plusEnabled = segments.last().isNotEmpty()
                )
                errorRes?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    liveCents <= 0 -> errorRes = R.string.sheet_amount_error
                    maxCents != null && liveCents > maxCents ->
                        errorRes = R.string.pad_amount_exceeds
                    else -> {
                        // 钱动了：确认触感让「存入/取出」这个动作落定
                        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        onConfirm(liveCents)
                    }
                }
            }) {
                Text(stringResource(R.string.common_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
