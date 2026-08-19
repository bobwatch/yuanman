package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.Goal
import com.yuanman.MainViewModel
import com.yuanman.MessageVariant
import com.yuanman.MoneyUtils
import com.yuanman.R
import com.yuanman.dailySavingRate
import com.yuanman.ui.theme.expenseAmountColor
import com.yuanman.ui.theme.incomeAmountColor
import kotlin.math.roundToInt

/** 攒钱目标详情：大圆环 + 存入/取出 + 存入历史 + 删除目标。 */
@Composable
fun GoalDetailScreen(
    viewModel: MainViewModel,
    goalId: String,
    onBack: () -> Unit,
    toastHostState: ToastHostState
) {
    val goals by viewModel.goals.collectAsStateWithLifecycle()

    val goal = goals.firstOrNull { it.id == goalId }

    var showDepositDialog by remember { mutableStateOf(false) }
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var recordExpense by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // 删除的目标暂存：撤销删除时原样找回（id/存入记录/里程碑全保留）
    var deletedGoal by remember { mutableStateOf<Goal?>(null) }

    val deletedText = stringResource(R.string.goal_deleted)
    val undoText = stringResource(R.string.home_undo)
    val notFoundText = stringResource(R.string.goal_not_found)

    // 目标被删除后自动返回；异常 goalId（如直达链接指向已删目标）说明后返回
    LaunchedEffect(goal) {
        if (goal == null && deletedGoal == null) {
            viewModel.postMessage(notFoundText, MessageVariant.ERROR)
            onBack()
        }
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        SubPageHeader(
            title = goal?.name ?: "",
            onBack = onBack,
            actions = {
                // 目标已删（等撤销 Toast 的窗口期）时不渲染删除入口，避免空名二次删除
                if (goal != null) {
                    IconButton(onClick = { showDeleteConfirm = true }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.goal_delete_title),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        )

        if (goal != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Spacer(Modifier.height(8.dp))
            IconTile(
                icon = goalIcon(goal.emoji),
                tint = MaterialTheme.colorScheme.primary,
                container = MaterialTheme.colorScheme.primaryContainer,
                size = 56.dp,
                iconSize = 26.dp
            )
            Spacer(Modifier.height(8.dp))
            ProgressRing(
                progress = goal.progress,
                modifier = Modifier.size(180.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(
                            R.string.goal_progress_percent,
                            (goal.progress * 100).roundToInt().coerceAtMost(100)
                        ),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(
                    R.string.goal_detail_saved_of,
                    MoneyUtils.formatCents(goal.savedCents),
                    MoneyUtils.formatCents(goal.targetCents)
                ),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            goal.deadlineMillis?.let { deadline ->
                val datePattern = stringResource(R.string.date_pattern)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.goal_detail_deadline,
                        formatSheetDate(deadline, datePattern)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))

            // 速度统计
            val days = remember(goal.createdAt) {
                ((System.currentTimeMillis() - goal.createdAt) / 86_400_000L + 1)
                    .coerceAtLeast(1)
            }
            val totalDeposited = goal.deposits
                .filter { !it.isWithdraw }
                .sumOf { it.amountCents }
            val dailyAvg = totalDeposited / days
            val rate = goal.dailySavingRate()
            val remaining = (goal.targetCents - goal.savedCents).coerceAtLeast(0)
            val statsText = when {
                goal.savedCents >= goal.targetCents ->
                    stringResource(R.string.goal_detail_done)
                rate <= 0 -> stringResource(
                    R.string.goal_detail_stats_no_rate,
                    MoneyUtils.formatCents(dailyAvg),
                    MoneyUtils.formatCents(remaining)
                )
                else -> {
                    val daysLeft = (remaining + rate - 1) / rate
                    stringResource(
                        R.string.goal_detail_stats,
                        // 显示的日均与预测同口径（近 30 天速度）：
                        // 「按此速度」的 N 天与前面的数字对得上
                        MoneyUtils.formatCents(rate),
                        MoneyUtils.formatCents(remaining),
                        daysLeft
                    )
                }
            }
            Text(
                text = statsText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))
            // 两按钮均分整行：窄屏/大字号下不会溢出换位；
            // heightIn(48dp) 与全 App 触达下限一致（默认 40dp 偏小）
            Row(Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        recordExpense = false
                        showDepositDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.goal_deposit))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { showWithdrawDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Text(stringResource(R.string.goal_withdraw))
                }
            }

            Spacer(Modifier.height(16.dp))
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.goal_history),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (goal.deposits.isEmpty()) {
                        Text(
                            text = stringResource(R.string.goal_history_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // 页面是 verticalScroll 的普通 Column：历史全量渲染会随
                        // 记录增多卡顿，只展示最近 100 笔，超出部分明示剩余条数
                        val deposits = goal.deposits.sortedByDescending { it.timestamp }
                        val shown = deposits.take(100)
                        shown.forEach { d ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = MoneyUtils.formatDateTime(d.timestamp),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = (if (d.isWithdraw) "-" else "+") +
                                        MoneyUtils.formatCents(d.amountCents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (d.isWithdraw) {
                                        expenseAmountColor()
                                    } else {
                                        incomeAmountColor()
                                    }
                                )
                            }
                        }
                        if (deposits.size > shown.size) {
                            Text(
                                text = stringResource(
                                    R.string.goal_history_more,
                                    deposits.size - shown.size
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
        } else {
            // 目标已删、等撤销 Toast 窗口期：给个轻空态，不做空白页
            EmptyState(
                emoji = "🗑️",
                title = deletedText,
                subtitle = stringResource(R.string.goal_deleted_sub)
            )
        }
    }

    if (showDepositDialog) {
        AmountPadDialog(
            title = stringResource(R.string.goal_deposit_title, goal?.name ?: ""),
            onDismiss = { showDepositDialog = false },
            onConfirm = { cents ->
                viewModel.deposit(goalId, cents, isWithdraw = false, recordExpense = recordExpense)
                showDepositDialog = false
            },
            extraContent = {
                // 无障碍：开关自身读出身旁文案，TalkBack 不悬空
                val recordExpenseLabel = stringResource(R.string.goal_record_expense)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = recordExpenseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = recordExpense,
                        onCheckedChange = { recordExpense = it },
                        modifier = Modifier.semantics {
                            contentDescription = recordExpenseLabel
                        }
                    )
                }
            }
        )
    }

    if (showWithdrawDialog) {
        AmountPadDialog(
            title = stringResource(R.string.goal_withdraw_title, goal?.name ?: ""),
            onDismiss = { showWithdrawDialog = false },
            onConfirm = { cents ->
                viewModel.deposit(goalId, cents, isWithdraw = true, recordExpense = false)
                showWithdrawDialog = false
            },
            // 取出不得超过已存金额（SavingsStore 的 clamp 作兜底）
            maxCents = goal?.savedCents ?: 0L
        )
    }

    if (showDeleteConfirm) {
        val view = LocalView.current
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.goal_delete_title)) },
            text = {
                Text(stringResource(R.string.goal_delete_msg, goal?.name ?: ""))
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    deletedGoal = goal
                    viewModel.deleteGoal(goalId)
                    // 删除完成：全局 Toast 带撤销，误删可一键找回（与流水删除同一交互语言）
                    toastHostState.show(
                        message = deletedText,
                        variant = MessageVariant.INFO,
                        actionLabel = undoText,
                        onAction = { deletedGoal?.let { viewModel.addGoal(it) } }
                    )
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}
