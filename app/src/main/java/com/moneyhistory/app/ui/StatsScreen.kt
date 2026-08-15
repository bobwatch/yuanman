package com.moneyhistory.app.ui

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
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
import kotlin.math.roundToInt

/** 统计页：支出/收入 Tab + 分类环形图 + 近 6 个月趋势 + 本月每日走势。 */
@Composable
fun StatsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val view = LocalView.current
    // 月份为统计页本地状态（不与首页共享，返回首页月份不受影响）。
    // 存相对当前月的偏移量（rememberSaveable 可存），旋转屏幕不丢查看位置
    var monthOffset by rememberSaveable { mutableIntStateOf(0) }
    val month = remember(monthOffset) {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, monthOffset) }
        YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
    // 月份偏移只在过去方向：翻到当前月后不允许再往前（未来无数据）
    val canGoNext = monthOffset < 0
    // 不在当前月时月份标题可点（下划线提示），一键回到本月
    val goToCurrentMonth: (() -> Unit)? =
        if (monthOffset == 0) null else ({ monthOffset = 0 })

    fun shiftMonth(delta: Int) {
        if (monthOffset + delta > 0) return
        monthOffset += delta
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
    // 与趋势柱一一对应的月份（点柱跳转首页明细用）
    val trendMonths = remember(month) {
        val base = Calendar.getInstance().apply {
            set(Calendar.YEAR, month.year)
            set(Calendar.MONTH, month.month - 1)
        }
        (5 downTo 0).map { back ->
            val target = (base.clone() as Calendar).apply { add(Calendar.MONTH, -back) }
            YearMonth(target.get(Calendar.YEAR), target.get(Calendar.MONTH) + 1)
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
        // 均匀抽样约 6 个标签，保证首日与月末最后一天都在
        val n = dailyValues.size
        if (n <= 7) {
            (1..n).toList()
        } else {
            val step = (n - 1) / 5
            buildList {
                var i = 1
                while (i <= n) {
                    add(i)
                    i += step
                }
                if (last() != n) add(n)
            }
        }
    }
    // 查看当月时标记「今天」的数据点（其他月份无意义）
    val todayIndex = remember(month) {
        val now = Calendar.getInstance()
        if (month.year == now.get(Calendar.YEAR) &&
            month.month == now.get(Calendar.MONTH) + 1
        ) {
            now.get(Calendar.DAY_OF_MONTH) - 1
        } else {
            null
        }
    }

    // 洞察数据：上月同类型总额（环比用）+ 日均口径（当月按已过天数，历史月按整月）
    val prevTotal = remember(transactions, month, currentType) {
        val cal = Calendar.getInstance()
        val prev = Calendar.getInstance().apply {
            set(Calendar.YEAR, month.year)
            set(Calendar.MONTH, month.month - 1)
            add(Calendar.MONTH, -1)
        }
        val py = prev.get(Calendar.YEAR)
        val pm = prev.get(Calendar.MONTH) + 1
        transactions.filter { t ->
            if (t.type != currentType) return@filter false
            cal.timeInMillis = t.timestamp
            cal.get(Calendar.YEAR) == py && cal.get(Calendar.MONTH) + 1 == pm
        }.sumOf { it.amountCents }
    }
    val insightDays = remember(month) {
        val now = Calendar.getInstance()
        val isCurrent = month.year == now.get(Calendar.YEAR) &&
            month.month == now.get(Calendar.MONTH) + 1
        val cal = Calendar.getInstance().apply { set(month.year, month.month - 1, 1) }
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        if (isCurrent) now.get(Calendar.DAY_OF_MONTH) else daysInMonth
    }

    Column(Modifier.fillMaxSize()) {
        SubPageHeader(
            title = stringResource(R.string.stats_title),
            onBack = onBack
        )

        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            MonthSelector(
                month = month,
                onPrev = { shiftMonth(-1) },
                onNext = { shiftMonth(1) },
                nextEnabled = canGoNext,
                onTitleClick = goToCurrentMonth
            )

            TabRow(
                selectedTabIndex = tab,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = tab == 0,
                    onClick = {
                        // 收支切换与全 App 的按键触感一致：点下去「咯噔」一声
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        tab = 0
                    },
                    text = {
                        Text(
                            stringResource(R.string.sheet_type_expense),
                            fontWeight = if (tab == 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = tab == 1,
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        tab = 1
                    },
                    text = {
                        Text(
                            stringResource(R.string.sheet_type_income),
                            fontWeight = if (tab == 1) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // 顶部汇总：支出 / 收入 / 结余
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                            color = ExpenseRed,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                            color = IncomeGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.stats_balance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val balance = totalIncome - totalExpense
                        Text(
                            text = MoneyUtils.formatCents(balance),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                balance > 0 -> IncomeGreen
                                balance < 0 -> ExpenseRed
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 本月洞察：最大分类 / 日均 / 环比上月（有数据才显示，空数据不加噪）
            if (currentTotal > 0) {
                val top = slices.first()
                val topPercent = (top.value / currentTotal * 100).roundToInt()
                // 日均取整除法：低位小数月份仍能显示 1 元级均值
                val avgCents = (currentTotal + insightDays / 2) / insightDays
                AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(
                                if (tab == 0) {
                                    R.string.stats_insight_title_expense
                                } else {
                                    R.string.stats_insight_title_income
                                }
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(12.dp))
                        InsightRow(
                            leading = "${Categories.emojiOf(top.label)} " +
                                Categories.displayName(top.label),
                            leadingBold = true,
                            trailing = stringResource(
                                R.string.stats_insight_top_value,
                                MoneyUtils.formatCents(top.value.toLong()),
                                topPercent
                            )
                        )
                        InsightRow(
                            leading = stringResource(
                                if (tab == 0) {
                                    R.string.stats_insight_daily_expense
                                } else {
                                    R.string.stats_insight_daily_income
                                }
                            ),
                            trailing = stringResource(
                                R.string.stats_insight_daily_value,
                                MoneyUtils.formatCents(avgCents)
                            )
                        )
                        if (prevTotal > 0) {
                            val diff = currentTotal - prevTotal
                            val diffPct = (Math.abs(diff) * 100 / prevTotal).toInt()
                            InsightRow(
                                leading = stringResource(R.string.stats_insight_vs),
                                trailing = when {
                                    diff > 0 -> stringResource(
                                        R.string.stats_insight_more,
                                        MoneyUtils.formatCents(diff),
                                        diffPct
                                    )
                                    diff < 0 -> stringResource(
                                        R.string.stats_insight_less,
                                        MoneyUtils.formatCents(-diff),
                                        diffPct
                                    )
                                    else -> stringResource(R.string.stats_insight_even)
                                },
                                trailingColor = when {
                                    diff > 0 ->
                                        if (tab == 0) ExpenseRed else IncomeGreen
                                    diff < 0 ->
                                        if (tab == 0) IncomeGreen else ExpenseRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // 分类占比环形图
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                        // 图例色块与环形图同源色板（含深色主题提亮版）
                        val palette = chartPalette()
                        slices.forEachIndexed { i, slice ->
                            // 与洞察行的整数百分比口径一致（如 35%，不做 34.6% 混用）
                            val percent = if (currentTotal > 0) {
                                (slice.value / currentTotal * 100).roundToInt()
                            } else {
                                0
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .pressScale()
                                    .clickable(
                                        interactionSource = remember {
                                            MutableInteractionSource()
                                        },
                                        indication = null
                                    ) {
                                        // 点分类 → 首页直达该分类流水（占比 → 逐笔明细闭环）
                                        view.performHapticFeedback(
                                            HapticFeedbackConstants.KEYBOARD_TAP
                                        )
                                        viewModel.setHomeFilter(slice.label)
                                        if (monthOffset != 0) {
                                            viewModel.goToMonth(month)
                                        }
                                        onBack()
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(palette[i % palette.size])
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = Categories.displayName(slice.label),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = MoneyUtils.formatCents(slice.value.toLong()),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = stringResource(
                                        R.string.stats_percent_format,
                                        percent
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.size(2.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            }

            // 近 6 个月趋势
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                    if (trendPoints.all { it.amountCents == 0L }) {
                        Text(
                            text = stringResource(R.string.stats_trend_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        TrendBarChart(
                            points = trendPoints,
                            // 轻点某月柱 → 首页直达该月流水（趋势 → 逐笔闭环）
                            onBarTap = { idx ->
                                if (idx in trendMonths.indices) {
                                    view.performHapticFeedback(
                                        HapticFeedbackConstants.KEYBOARD_TAP
                                    )
                                    viewModel.goToMonth(trendMonths[idx])
                                    onBack()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            // 本月每日走势
            AppCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
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
                    if (dailyValues.all { it == 0L }) {
                        Text(
                            text = stringResource(R.string.stats_empty_month),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        DailyLineChart(
                            dailyCents = dailyValues,
                            dayLabels = dayLabels,
                            todayIndex = todayIndex,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 洞察行：左侧说明（可选加粗强调）+ 右侧数值（可选语义色）。 */
@Composable
private fun InsightRow(
    leading: String,
    trailing: String,
    trailingColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingBold: Boolean = false
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leading,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (leadingBold) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = trailing,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = trailingColor,
            maxLines = 1
        )
    }
}
