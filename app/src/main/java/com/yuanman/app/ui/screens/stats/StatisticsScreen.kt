package com.yuanman.app.ui.screens.stats

import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel,
    onNavigateBack: (() -> Unit)? = null,
    onCategoryClick: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = { Text("数据统计", fontWeight = FontWeight.Bold) },
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

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (uiState.periodMode == StatisticsPeriod.MONTH) {
                                            showMonthPicker = true
                                        }
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
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
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                        )
                                    )
                                }
                            }

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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 🌟 周期切换（周 / 月 / 年 3 Tab 现代分段胶囊）
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        StatisticsPeriod.values().forEach { period ->
                            val isSelected = uiState.periodMode == period
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                                shadowElevation = if (isSelected) 2.dp else 0.dp,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(11.dp))
                                    .clickable { viewModel.selectPeriod(period) }
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(vertical = 7.dp)
                                ) {
                                    Text(
                                        text = period.title,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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

            // 2. 统计类型选择：支出结构 / 收入结构
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

            // 3. 结构分布图
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

            // 4. 走势图
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

            // 5. 分类排行榜清单
            item {
                Text(
                    text = "分类排行 (${uiState.categoryStats.size} 项)",
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
