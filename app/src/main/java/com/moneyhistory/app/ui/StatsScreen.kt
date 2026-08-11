package com.moneyhistory.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.YearMonth
import com.moneyhistory.app.ofMonth
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import java.util.Calendar
import java.util.Locale

/** 统计页：支出/收入 Tab + 分类环形图 + 近 6 个月趋势 + 本月每日走势。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    // 月份为统计页本地状态（不与首页共享，返回首页月份不受影响）
    var month by remember {
        mutableStateOf(
            Calendar.getInstance().let {
                YearMonth(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1)
            }
        )
    }
    val isAtCurrentMonth = remember(month) {
        val c = Calendar.getInstance()
        month.year * 12 + month.month >=
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) + 1
    }

    fun shiftMonth(delta: Int) {
        val cur = Calendar.getInstance()
        val curValue = cur.get(Calendar.YEAR) * 12 + cur.get(Calendar.MONTH) + 1
        val nextValue = month.year * 12 + month.month + delta
        if (nextValue > curValue) return
        month = if (nextValue % 12 == 0) {
            YearMonth(nextValue / 12 - 1, 12)
        } else {
            YearMonth(nextValue / 12, nextValue % 12)
        }
    }

    var tab by remember { mutableIntStateOf(0) } // 0 支出 1 收入
    val currentType =
        if (tab == 0) Transaction.Type.EXPENSE else Transaction.Type.INCOME

    val monthTransactions = remember(transactions, month) { transactions.ofMonth(month) }
    var totalExpense = 0L
    var totalIncome = 0L
    monthTransactions.forEach {
        if (it.type == Transaction.Type.EXPENSE) totalExpense += it.amountCents
        else totalIncome += it.amountCents
    }
    val currentTotal = if (tab == 0) totalExpense else totalIncome

    // 当前类型的分类汇总（环形图 + 图例）
    val slices = remember(monthTransactions, currentType) {
        monthTransactions
            .filter { it.type == currentType }
            .groupBy { it.category }
            .map { (category, list) ->
                ChartSlice(category, list.sumOf { it.amountCents }.toFloat())
            }
            .sortedByDescending { it.value }
    }

    // 近 6 个月趋势（相对当前查看月份）
    val monthShortFormat = stringResource(R.string.stats_month_short)
    val trendPoints = remember(transactions, month, currentType, monthShortFormat) {
        val base = Calendar.getInstance().apply {
            set(Calendar.YEAR, month.year)
            set(Calendar.MONTH, month.month - 1)
        }
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance()
        (5 downTo 0).map { back ->
            val target = (base.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
            val y = target.get(Calendar.YEAR)
            val m = target.get(Calendar.MONTH) + 1
            val sum = transactions.filter { tx ->
                cal.timeInMillis = tx.timestamp
                tx.type == currentType &&
                    cal.get(Calendar.YEAR) == y && cal.get(Calendar.MONTH) + 1 == m
            }.sumOf { it.amountCents }
            MonthPoint(
                label = String.format(Locale.getDefault(), monthShortFormat, m),
                amountCents = sum,
                isCurrent = y == now.get(Calendar.YEAR) &&
                    m == now.get(Calendar.MONTH) + 1
            )
        }
    }

    // 本月每日走势
    val dailyValues = remember(transactions, month, currentType) {
        val first = Calendar.getInstance().apply { set(month.year, month.month - 1, 1) }
        val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
        val arr = LongArray(daysInMonth)
        val cal = Calendar.getInstance()
        transactions.forEach { tx ->
            if (tx.type != currentType) return@forEach
            cal.timeInMillis = tx.timestamp
            if (cal.get(Calendar.YEAR) == month.year &&
                cal.get(Calendar.MONTH) + 1 == month.month
            ) {
                arr[cal.get(Calendar.DAY_OF_MONTH) - 1] += tx.amountCents
            }
        }
        arr.toList()
    }
    val dayLabels = remember(dailyValues) {
        listOf(1, 5, 10, 15, 20, 25, 30).filter { it <= dailyValues.size }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stats_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            MonthSelector(
                month = month,
                onPrev = { shiftMonth(-1) },
                onNext = { shiftMonth(1) },
                nextEnabled = !isAtCurrentMonth
            )

            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text(stringResource(R.string.sheet_type_expense)) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text(stringResource(R.string.sheet_type_income)) }
                )
            }

            // 分类占比环形图
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            if (tab == 0) {
                                R.string.stats_donut_expense
                            } else {
                                R.string.stats_donut_income
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    if (slices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.stats_empty_month),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        DonutChart(
                            slices = slices,
                            centerTitle = stringResource(
                                if (tab == 0) {
                                    R.string.stats_total_expense
                                } else {
                                    R.string.stats_total_income
                                }
                            ),
                            centerValue = MoneyUtils.formatCents(currentTotal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        slices.forEachIndexed { i, slice ->
                            val percent = if (currentTotal > 0) {
                                slice.value / currentTotal * 100
                            } else {
                                0f
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(ChartPalette[i % ChartPalette.size])
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = slice.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = MoneyUtils.formatCents(slice.value.toLong()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = String.format(
                                        Locale.getDefault(), "%.1f%%", percent
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 近 6 个月趋势
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            if (tab == 0) {
                                R.string.stats_trend_expense
                            } else {
                                R.string.stats_trend_income
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    TrendBarChart(
                        points = trendPoints,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            // 本月每日走势
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(
                            if (tab == 0) {
                                R.string.stats_daily_expense
                            } else {
                                R.string.stats_daily_income
                            }
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(12.dp))
                    DailyLineChart(
                        dailyCents = dailyValues,
                        dayLabels = dayLabels,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    )
                }
            }

            // 顶部汇总
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.stats_total_expense_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyUtils.formatCents(totalExpense),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExpenseRed
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.stats_total_income_label),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = MoneyUtils.formatCents(totalIncome),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = IncomeGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
