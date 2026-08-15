package com.moneyhistory.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moneyhistory.app.Goal
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.dailySavingRate
import com.moneyhistory.app.goalEmojiCandidates
import java.util.Calendar

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
        modifier = modifier,
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
    val percent = (goal.progress * 100).toInt().coerceAtMost(100)
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
                        text = "${(goal.progress * 100).toInt().coerceAtMost(100)}%",
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

/** 新建攒钱目标 BottomSheet：名称 + 目标金额 + emoji + 可选目标日期。 */
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
                stringResource(R.string.goal_create_title),
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it.take(12)
                    nameError = false
                },
                label = { Text(stringResource(R.string.goal_name_hint)) },
                isError = nameError,
                supportingText = {
                    if (nameError) Text(stringResource(R.string.goal_name_error))
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
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                goalEmojiCandidates.forEach { candidate ->
                    FilterChip(
                        selected = emoji == candidate,
                        onClick = { emoji = candidate },
                        label = { Text(candidate) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.goal_deadline_toggle),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = deadlineEnabled,
                    onCheckedChange = { deadlineEnabled = it }
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
                        cents == null -> amountError = true
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_create))
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
            Column {
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
                NumPad(onKey = { onNumKey(it) })
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
                    else -> onConfirm(liveCents)
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
