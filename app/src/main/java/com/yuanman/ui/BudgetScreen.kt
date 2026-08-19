package com.yuanman.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.DateUtils
import com.yuanman.MainViewModel
import com.yuanman.MoneyUtils
import com.yuanman.R
import com.yuanman.Transaction
import com.yuanman.YearMonth
import com.yuanman.ui.theme.expenseAmountColor
import com.yuanman.ui.theme.incomeAmountColor
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * 月度预算页：本月状态卡 + 近 12 个月预算/支出双折线趋势 + 逐月历史列表。
 * 历史月份「生效预算」取该月及以前最后一次设置的值（向前继承），
 * 点任意月份回到首页并定位到那一月。
 */
@Composable
fun BudgetScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val settings = viewModel.settings
    val budgetHistory by settings.budgetHistory.collectAsStateWithLifecycle()
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val currentMonth = DateUtils.monthPrefix()
    val view = LocalView.current

    // 月份序列：设置过预算的月份 ∪ 有支出的月份 ∪ 当前月，只保留最近 12 个
    val monthKeys = remember(budgetHistory, transactions) {
        val months = budgetHistory.keys.toMutableSet()
        transactions.forEach { months.add(DateUtils.monthPrefix(it.timestamp)) }
        months.add(currentMonth)
        months.sorted().takeLast(12)
    }

    // 每月支出合计（分）
    val spentByMonth = remember(transactions) {
        transactions
            .filter { it.type == Transaction.Type.EXPENSE }
            .groupBy { DateUtils.monthPrefix(it.timestamp) }
            .mapValues { (_, list) -> list.sumOf { it.amountCents } }
    }

    // 某月生效预算：该月及以前最后一次设置的值（"yyyy-MM" 字符串序即时间序）
    fun effectiveBudget(monthKey: String): Long {
        var value = 0L
        budgetHistory.forEach { (k, v) -> if (k <= monthKey) value = v }
        return value
    }

    val monthLabelPattern = stringResource(R.string.budget_month_pattern)
    val chartMonthPattern = stringResource(R.string.budget_chart_month_pattern)
    fun formatMonth(key: String, pattern: String): String {
        val time = SimpleDateFormat("yyyy-MM", Locale.CHINA).parse(key) ?: return key
        return SimpleDateFormat(pattern, Locale.getDefault()).format(time)
    }

    val chartPoints = monthKeys.map { key ->
        BudgetMonthPoint(
            label = formatMonth(key, chartMonthPattern),
            spendingCents = spentByMonth[key] ?: 0L,
            budgetCents = effectiveBudget(key),
            isCurrent = key == currentMonth
        )
    }

    val currentSpent = spentByMonth[currentMonth] ?: 0L
    val currentBudget = effectiveBudget(currentMonth)
    var showBudgetDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(
            title = stringResource(R.string.budget_title),
            subtitle = formatMonth(currentMonth, monthLabelPattern),
            onBack = onBack,
            actions = {
                IconButton(onClick = { showBudgetDialog = true }) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.budget_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 本月卡：预算 / 支出 / 剩余（超支红、剩余绿），点卡片直接改预算
            AppCard(onClick = { showBudgetDialog = true }) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.budget_current_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (currentBudget > 0) {
                                stringResource(
                                    R.string.home_card_budget_amount,
                                    MoneyUtils.formatCents(currentBudget)
                                )
                            } else {
                                stringResource(R.string.budget_not_set)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = MoneyUtils.formatCents(currentSpent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (currentBudget > 0) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = {
                                (currentSpent.toFloat() / currentBudget).coerceIn(0f, 1f)
                            },
                            // 花超预算进度条转红，一眼看出状态
                            color = if (currentSpent <= currentBudget) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(
                                R.string.budget_month_spent,
                                MoneyUtils.formatCents(currentSpent)
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.weight(1f))
                        if (currentBudget > 0) {
                            val diff = currentBudget - currentSpent
                            Text(
                                text = if (diff >= 0) {
                                    stringResource(
                                        R.string.home_card_budget_left,
                                        MoneyUtils.formatCents(diff)
                                    )
                                } else {
                                    stringResource(
                                        R.string.home_card_budget_over,
                                        MoneyUtils.formatCents(-diff)
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (diff >= 0) {
                                    incomeAmountColor()
                                } else {
                                    expenseAmountColor()
                                }
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.budget_set_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 近 12 个月预算/支出双折线
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.budget_trend_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.budget_legend_budget),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(expenseAmountColor())
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.budget_legend_spent),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.budget_trend_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    BudgetTrendChart(
                        points = chartPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            }

            // 逐月历史：支出 + 剩余/超支，点任意月份回到首页定位
            AppCard {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.budget_history_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    monthKeys.sortedDescending().forEach { key ->
                        val spent = spentByMonth[key] ?: 0L
                        val budget = effectiveBudget(key)
                        val diff = budget - spent
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.KEYBOARD_TAP
                                    )
                                    viewModel.goToMonth(
                                        YearMonth(
                                            key.substring(0, 4).toInt(),
                                            key.substring(5, 7).toInt()
                                        )
                                    )
                                    onBack()
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = formatMonth(key, monthLabelPattern),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (budget > 0) {
                                        stringResource(
                                            R.string.home_card_budget_amount,
                                            MoneyUtils.formatCents(budget)
                                        )
                                    } else {
                                        stringResource(R.string.budget_not_set)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = MoneyUtils.formatCents(spent),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                if (budget > 0) {
                                    Text(
                                        text = if (diff >= 0) {
                                            stringResource(
                                                R.string.home_card_budget_left,
                                                MoneyUtils.formatCents(diff)
                                            )
                                        } else {
                                            stringResource(
                                                R.string.home_card_budget_over,
                                                MoneyUtils.formatCents(-diff)
                                            )
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (diff >= 0) {
                                            incomeAmountColor()
                                        } else {
                                            expenseAmountColor()
                                        }
                                    )
                                }
                            }
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        BudgetDialog(
            currentCents = currentBudget,
            onDismiss = { showBudgetDialog = false },
            onSave = { settings.setBudgetCents(it) }
        )
    }
}
