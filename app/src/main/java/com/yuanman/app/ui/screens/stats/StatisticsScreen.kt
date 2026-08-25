package com.yuanman.app.ui.screens.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                title = { Text("数据统计", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { showMonthPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${uiState.selectedYear}年${uiState.selectedMonth}月",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
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
            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
        ) {
            // 月度综合概览卡片
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatSummaryColumn(
                            title = "总收入",
                            amount = uiState.summary.totalIncome,
                            amountColor = MaterialTheme.colorScheme.primary
                        )
                        Divider(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = "总支出",
                            amount = uiState.summary.totalExpense,
                            amountColor = MaterialTheme.colorScheme.error
                        )
                        Divider(
                            modifier = Modifier
                                .height(36.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        )
                        StatSummaryColumn(
                            title = "净结余",
                            amount = uiState.summary.balance,
                            amountColor = if (uiState.summary.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 统计类型选择：支出占比 / 收入占比
            item {
                TabRow(
                    selectedTabIndex = if (uiState.selectedType == RecordType.EXPENSE) 0 else 1,
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = uiState.selectedType == RecordType.EXPENSE,
                        onClick = { viewModel.selectType(RecordType.EXPENSE) },
                        text = {
                            Text(
                                "支出分类统计",
                                fontWeight = if (uiState.selectedType == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                    Tab(
                        selected = uiState.selectedType == RecordType.INCOME,
                        onClick = { viewModel.selectType(RecordType.INCOME) },
                        text = {
                            Text(
                                "收入分类统计",
                                fontWeight = if (uiState.selectedType == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 环形分类占比图
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (uiState.selectedType == RecordType.EXPENSE) "支出结构占比" else "收入结构占比",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        val totalTarget = if (uiState.selectedType == RecordType.EXPENSE) {
                            uiState.summary.totalExpense
                        } else {
                            uiState.summary.totalIncome
                        }

                        DonutChart(
                            items = uiState.categoryStats,
                            totalAmount = totalTarget,
                            centerTitle = if (uiState.selectedType == RecordType.EXPENSE) "总支出" else "总收入"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 分类支出/收入 排行榜
            if (uiState.categoryStats.isNotEmpty()) {
                item {
                    Text(
                        text = if (uiState.selectedType == RecordType.EXPENSE) "各分类支出排行" else "各分类收入排行",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            uiState.categoryStats.forEachIndexed { index, statItem ->
                                CategoryRankItem(
                                    item = statItem,
                                    rank = index + 1
                                )
                                if (index < uiState.categoryStats.lastIndex) {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // 每日支出走势图（仅在有支出或选支出时展示）
            if (uiState.selectedType == RecordType.EXPENSE) {
                item {
                    Text(
                        text = "每日支出趋势",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "单日最高: ${MoneyUtils.formatCurrency(uiState.summary.maxDailyExpense)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = "日均支出: ${MoneyUtils.formatCurrency(uiState.summary.avgDailyExpense)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            BarTrendChart(items = uiState.dailyTrends)
                        }
                    }
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
    amountColor: androidx.compose.ui.graphics.Color
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
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = amountColor
        )
    }
}
