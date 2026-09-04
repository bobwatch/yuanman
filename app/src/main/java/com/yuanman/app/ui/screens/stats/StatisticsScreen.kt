package com.yuanman.app.ui.screens.stats

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.model.BudgetReviewData
import com.yuanman.app.data.model.BudgetReviewStatus
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: () -> Unit = {},
    onCategoryClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }
    var showWeekPicker by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            YuanmanHeaderBackground {
                TopAppBar(
                    modifier = Modifier.offset(y = (-4).dp),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = { Text("数据统计", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        // 时间快捷切换
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            IconButton(
                                onClick = { viewModel.previousPeriod() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "上一周期",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        when (uiState.periodMode) {
                                            StatisticsPeriod.MONTH -> showMonthPicker = true
                                            StatisticsPeriod.YEAR -> showYearPicker = true
                                            StatisticsPeriod.WEEK -> showWeekPicker = true
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = when (uiState.periodMode) {
                                        StatisticsPeriod.WEEK -> "${uiState.selectedYear}年 第${uiState.selectedWeek}周"
                                        StatisticsPeriod.MONTH -> "${uiState.selectedYear}年${uiState.selectedMonth}月"
                                        StatisticsPeriod.YEAR -> "${uiState.selectedYear}年"
                                    },
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                )
                                if (uiState.periodMode == StatisticsPeriod.WEEK && uiState.weekStartTimestamp > 0L) {
                                    Text(
                                        text = DateTimeUtils.formatWeekRangeShort(uiState.weekStartTimestamp, uiState.weekEndTimestamp),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                    )
                                }
                            }

                            IconButton(
                                onClick = { viewModel.nextPeriod() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "下一周期",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 周期切换：使用独立胶囊滑块，周期数据本身不参与横向动画。
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(4.dp)
                    ) {
                        val periods = StatisticsPeriod.values()
                        val selectedIndex = periods.indexOf(uiState.periodMode).coerceAtLeast(0)
                        val tabWidth = (maxWidth - 8.dp) / periods.size
                        val sliderOffset by animateDpAsState(
                            targetValue = (tabWidth + 4.dp) * selectedIndex,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMediumLow
                            ),
                            label = "statistics-period-thumb"
                        )

                        Surface(
                            modifier = Modifier
                                .offset(x = sliderOffset)
                                .width(tabWidth)
                                .fillMaxHeight(),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary,
                            shadowElevation = 3.dp
                        ) {}

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            periods.forEach { period ->
                                val isSelected = uiState.periodMode == period
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .clickable { viewModel.selectPeriod(period) }
                                        .padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = period.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 1. 综合概览卡片
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatSummaryColumn(
                            title = when (uiState.periodMode) {
                                StatisticsPeriod.WEEK -> "本周收入"
                                StatisticsPeriod.MONTH -> "总收入"
                                StatisticsPeriod.YEAR -> "全年收入"
                            },
                            amount = uiState.summary.totalIncome,
                            amountColor = MaterialTheme.colorScheme.primary
                        )
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = when (uiState.periodMode) {
                                StatisticsPeriod.WEEK -> "本周支出"
                                StatisticsPeriod.MONTH -> "总支出"
                                StatisticsPeriod.YEAR -> "全年支出"
                            },
                            amount = uiState.summary.totalExpense,
                            amountColor = MaterialTheme.colorScheme.error
                        )
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = "结余",
                            amount = uiState.summary.balance,
                            amountColor = if (uiState.summary.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 2. 月预算复盘：把“花了多少”翻译为可执行的消费节奏
            if (uiState.periodMode == StatisticsPeriod.MONTH) {
                item {
                    BudgetReviewCard(
                        review = uiState.budgetReview,
                        onSetBudget = { showBudgetDialog = true }
                    )
                }
            }

            if (uiState.smartInsight.isNotBlank()) {
                item {
                    KeyInsightCard(
                        insight = uiState.smartInsight,
                        expenseDiffPercent = uiState.expenseDiffPercent,
                        period = uiState.periodMode
                    )
                }
            }

            // 3. 统计类型选择：支出结构 / 收入结构
            item {
                TabRow(
                    selectedTabIndex = if (uiState.selectedType == RecordType.EXPENSE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    Tab(
                        selected = uiState.selectedType == RecordType.EXPENSE,
                        onClick = { viewModel.selectType(RecordType.EXPENSE) },
                        text = {
                            Text(
                                "支出占比",
                                fontWeight = if (uiState.selectedType == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedType == RecordType.INCOME,
                        onClick = { viewModel.selectType(RecordType.INCOME) },
                        text = {
                            Text(
                                "收入占比",
                                fontWeight = if (uiState.selectedType == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 4. 结构分布图
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.selectedType == RecordType.EXPENSE) "支出占比" else "收入占比",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        val totalTarget = if (uiState.selectedType == RecordType.EXPENSE) {
                            uiState.summary.totalExpense
                        } else {
                            uiState.summary.totalIncome
                        }

                        DonutChart(
                            items = uiState.categoryStats,
                            totalAmount = totalTarget,
                            centerTitle = if (uiState.selectedType == RecordType.EXPENSE) "总支出" else "总收入",
                            selectedCategory = uiState.selectedCategory,
                            onSelectCategory = { clickedItem ->
                                viewModel.selectCategory(clickedItem)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // 5. 走势图
            if (uiState.dailyTrends.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = when (uiState.periodMode) {
                                        StatisticsPeriod.WEEK -> "7天支出走势"
                                        StatisticsPeriod.MONTH -> "每日支出走势"
                                        StatisticsPeriod.YEAR -> "12个月支出走势"
                                    },
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Text(
                                    text = when (uiState.periodMode) {
                                        StatisticsPeriod.WEEK, StatisticsPeriod.MONTH -> "日均 ¥${MoneyUtils.centsToYuanString(uiState.summary.avgDailyExpense)}"
                                        StatisticsPeriod.YEAR -> "月均 ¥${MoneyUtils.centsToYuanString(uiState.summary.avgDailyExpense)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            BarTrendChart(
                                items = uiState.dailyTrends,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }

            // 6. 分类排行榜清单
            item {
                Text(
                    text = "分类排行（${uiState.categoryStats.size} 项）",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    modifier = Modifier.padding(top = 2.dp, bottom = 0.dp)
                )
            }

            if (uiState.categoryStats.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.selectedType == RecordType.EXPENSE) "暂无支出数据" else "暂无收入数据",
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = uiState.categoryStats,
                    key = { _, it -> "${it.category.id}_${it.category.name}" }
                ) { index, item ->
                    val isSelected = uiState.selectedCategory?.category?.id == item.category.id
                    CategoryRankItem(
                        item = item,
                        rank = index + 1,
                        isSelected = isSelected,
                        onClick = {
                            onCategoryClick?.invoke(item.category.id)
                        }
                    )
                }
            }
        }
    }

    MonthPickerModal(
        visible = showMonthPicker,
        initialYear = uiState.selectedYear,
        initialMonth = uiState.selectedMonth,
        onMonthSelected = { year, month ->
            viewModel.selectMonth(year, month)
            showMonthPicker = false
        },
        onDismiss = { showMonthPicker = false }
    )

    YearPickerModal(
        visible = showYearPicker,
        initialYear = uiState.selectedYear,
        onYearSelected = { year ->
            viewModel.selectYear(year)
            showYearPicker = false
        },
        onDismiss = { showYearPicker = false }
    )

    WeekPickerModal(
        visible = showWeekPicker,
        initialYear = uiState.selectedYear,
        initialWeek = uiState.selectedWeek,
        onWeekSelected = { year, week ->
            viewModel.selectWeek(year, week)
            showWeekPicker = false
        },
        onDismiss = { showWeekPicker = false }
    )

    if (showBudgetDialog) {
        var budgetInput by remember(uiState.selectedYear, uiState.selectedMonth) {
            mutableStateOf(
                uiState.budgetReview.budgetCents.takeIf { it > 0L }
                    ?.let(MoneyUtils::centsToYuanString)
                    .orEmpty()
            )
        }
        val budgetError = if (budgetInput.isNotBlank() && !MoneyUtils.isValidAmountInput(budgetInput.trim())) {
            "请输入大于 0 的有效金额"
        } else {
            null
        }
        Dialog(onDismissRequest = { showBudgetDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "设置 ${uiState.selectedYear}年${uiState.selectedMonth}月预算",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "预算会同步到首页和桌面复盘部件。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("预算金额（元）") },
                        prefix = { Text("¥ ") },
                        isError = budgetError != null,
                        supportingText = {
                            if (budgetError != null) {
                                Text(budgetError, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("留空并保存可清除已设预算")
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showBudgetDialog = false }) { Text("取消") }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (budgetError == null) {
                                    viewModel.setMonthlyBudget(MoneyUtils.parseYuanToCents(budgetInput.trim()))
                                    showBudgetDialog = false
                                }
                            }
                        ) { Text("保存") }
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetReviewCard(
    review: BudgetReviewData,
    onSetBudget: () -> Unit
) {
    val statusColor = when (review.status) {
        BudgetReviewStatus.ON_TRACK, BudgetReviewStatus.CLOSED -> MaterialTheme.colorScheme.primary
        BudgetReviewStatus.PLANNED -> MaterialTheme.colorScheme.secondary
        BudgetReviewStatus.SPENDING_FAST -> Color(0xFFF59E0B)
        BudgetReviewStatus.OVER_BUDGET -> MaterialTheme.colorScheme.error
        BudgetReviewStatus.NO_BUDGET -> MaterialTheme.colorScheme.outline
    }
    val statusText = when (review.status) {
        BudgetReviewStatus.NO_BUDGET -> "待设置"
        BudgetReviewStatus.PLANNED -> "未开始"
        BudgetReviewStatus.ON_TRACK -> "节奏稳健"
        BudgetReviewStatus.SPENDING_FAST -> "支出偏快"
        BudgetReviewStatus.OVER_BUDGET -> "已经超支"
        BudgetReviewStatus.CLOSED -> "本月已结算"
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, statusColor.copy(alpha = 0.18f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    text = "预算复盘",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = statusColor.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                    )
                }
            }

            if (review.status == BudgetReviewStatus.NO_BUDGET) {
                Text(
                    text = "设置月预算后，这里会对比日历进度与花费进度，并估算月末支出。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = onSetBudget, modifier = Modifier.fillMaxWidth()) {
                    Text("设置本月预算")
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth()) {
                    BudgetMetric(
                        title = "已支出",
                        value = MoneyUtils.formatCurrency(review.expenseCents),
                        modifier = Modifier.weight(1f)
                    )
                    BudgetMetric(
                        title = if (review.remainingCents >= 0L) "预算剩余" else "超支金额",
                        value = MoneyUtils.formatCurrency(kotlin.math.abs(review.remainingCents)),
                        valueColor = statusColor,
                        modifier = Modifier.weight(1f)
                    )
                    BudgetMetric(
                        title = "月末预计",
                        value = MoneyUtils.formatCurrency(review.projectedExpenseCents),
                        modifier = Modifier.weight(1f)
                    )
                }

                LinearProgressIndicator(
                    progress = { review.usedPercent.coerceIn(0f, 1f) },
                    color = statusColor,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "花费进度 ${(review.usedPercent * 100).toInt()}% · 日历进度 ${(review.calendarPercent * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    TextButton(
                        onClick = onSetBudget,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text("调整", fontSize = 11.sp)
                    }
                }

                if (review.remainingDays > 0 && review.remainingCents > 0L) {
                    Text(
                        text = "剩余 ${review.remainingDays} 天，建议每天不超过 ${MoneyUtils.formatCurrency(review.dailyAvailableCents)}",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetMetric(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = valueColor,
            maxLines = 1
        )
    }
}

@Composable
private fun KeyInsightCard(
    insight: String,
    expenseDiffPercent: Float?,
    period: StatisticsPeriod
) {
    val comparison = expenseDiffPercent?.let {
        val direction = if (it >= 0f) "增加" else "减少"
        val label = when (period) {
            StatisticsPeriod.WEEK -> "上周"
            StatisticsPeriod.MONTH -> "上月总支出"
            StatisticsPeriod.YEAR -> "去年"
        }
        "较$label$direction ${String.format(java.util.Locale.CHINA, "%.1f%%", kotlin.math.abs(it) * 100)}"
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Lightbulb,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "关键结论",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    if (comparison != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = comparison,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Text(
                    text = insight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatSummaryColumn(
    title: String,
    amount: Long,
    amountColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = MoneyUtils.formatCurrency(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )
    }
}
