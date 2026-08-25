package com.yuanman.app.ui.screens.stats

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("数据洞察与统计", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
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
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "上一周期",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = if (uiState.periodMode == StatisticsPeriod.MONTH) "${uiState.selectedYear}年${uiState.selectedMonth}月" else "${uiState.selectedYear}年",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (uiState.periodMode == StatisticsPeriod.MONTH) {
                                            showMonthPicker = true
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            IconButton(
                                onClick = { viewModel.nextPeriod() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "下一周期",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 周期切换（按月 / 按年）
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatisticsPeriod.values().forEach { period ->
                        val isSelected = uiState.periodMode == period
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(period.title, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 1. 综合概览卡片
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatSummaryColumn(
                            title = if (uiState.periodMode == StatisticsPeriod.MONTH) "本月收入" else "全年收入",
                            amount = uiState.summary.totalIncome,
                            amountColor = MaterialTheme.colorScheme.primary,
                            diffPercent = uiState.incomeDiffPercent,
                            isExpense = false
                        )
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = if (uiState.periodMode == StatisticsPeriod.MONTH) "本月支出" else "全年支出",
                            amount = uiState.summary.totalExpense,
                            amountColor = MaterialTheme.colorScheme.error,
                            diffPercent = uiState.expenseDiffPercent,
                            isExpense = true
                        )
                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = "净结余",
                            amount = uiState.summary.balance,
                            amountColor = if (uiState.summary.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 2. 消费生活画像与智能洞察卡片
            if (uiState.smartInsight.isNotBlank()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = if (uiState.periodMode == StatisticsPeriod.MONTH) "月度消费画像 · 智能洞察" else "年度财务画像 · 智能洞察",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = uiState.smartInsight,
                                    style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            // 3. 统计类型选择：支出分类 / 收入分类
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
                                "支出结构占比",
                                fontWeight = if (uiState.selectedType == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedType == RecordType.INCOME,
                        onClick = { viewModel.selectType(RecordType.INCOME) },
                        text = {
                            Text(
                                "收入来源占比",
                                fontWeight = if (uiState.selectedType == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            // 4. 高阶交互式环形分类占比图
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.selectedType == RecordType.EXPENSE) "结构分布与占比" else "来源分布与占比",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(14.dp))

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
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (uiState.periodMode == StatisticsPeriod.MONTH) "每日支出走势" else "12个月支出走势",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )

                                Text(
                                    text = if (uiState.periodMode == StatisticsPeriod.MONTH) "日均 ¥${MoneyUtils.centsToYuanString(uiState.summary.avgDailyExpense)}" else "月均 ¥${MoneyUtils.centsToYuanString(uiState.summary.avgDailyExpense)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

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
                    text = "分类排行榜明细 (${uiState.categoryStats.size} 项)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
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
                                text = if (uiState.selectedType == RecordType.EXPENSE) "该周期暂无支出" else "该周期暂无收入",
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
                            viewModel.selectCategory(if (isSelected) null else item)
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
}

@Composable
private fun StatSummaryColumn(
    title: String,
    amount: Long,
    amountColor: Color,
    diffPercent: Float? = null,
    isExpense: Boolean = false
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = MoneyUtils.formatCurrency(amount),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )

        if (diffPercent != null) {
            val isPositive = diffPercent > 0f
            val pctVal = (kotlin.math.abs(diffPercent) * 100).toInt()
            val badgeColor = if (isExpense) {
                if (isPositive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            } else {
                if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            }
            val arrow = if (isPositive) "↑" else "↓"
            val label = if (isExpense) {
                if (isPositive) "比上月多 $pctVal%" else "比上月省 $pctVal%"
            } else {
                if (isPositive) "比上月增 $pctVal%" else "比上月少 $pctVal%"
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "$arrow $label",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium),
                color = badgeColor
            )
        }
    }
}
