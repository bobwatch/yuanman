package com.yuanman.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.QueryStats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddRecord: (RecordType) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToStatistics: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val defaultType by viewModel.defaultRecordType.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onNavigateToAddRecord(defaultType) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("记一笔", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(bottom = 76.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 顶部月度收支概览卡片 (毛玻璃质感，点击进入数据统计)
            MonthSummaryHeaderCard(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                totalExpense = uiState.summary.totalExpense,
                totalIncome = uiState.summary.totalIncome,
                balance = uiState.summary.balance,
                onMonthClick = { showMonthPicker = true },
                onCardClick = onNavigateToStatistics,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )

            // 账单明细列表
            if (uiState.groupedRecords.isEmpty()) {
                EmptyStateView(
                    title = "${uiState.selectedYear}年${uiState.selectedMonth}月暂无账单",
                    description = "点击下方「记一笔」按钮，记录生活中的每一笔开销与收入",
                    actionButtonText = "立即记一笔",
                    onActionClick = { onNavigateToAddRecord(defaultType) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 88.dp)
                ) {
                    uiState.groupedRecords.forEach { (dayTimestamp, records) ->
                        val daySum = uiState.daySummaries[dayTimestamp] ?: Pair(0L, 0L)

                        item(key = "header_$dayTimestamp") {
                            DateGroupHeader(
                                timestamp = dayTimestamp,
                                totalExpense = daySum.first,
                                totalIncome = daySum.second
                            )
                        }

                        items(
                            items = records,
                            key = { "record_${it.record.id}" }
                        ) { item ->
                            RecordCardItem(
                                item = item,
                                onClick = { onNavigateToDetail(item.record.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // 月份选择器弹窗
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
fun MonthSummaryHeaderCard(
    year: Int,
    month: Int,
    totalExpense: Long,
    totalIncome: Long,
    balance: Long,
    onMonthClick: () -> Unit,
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.55f),
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            primaryContainer.copy(alpha = 0.40f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                // 顶部：应用名称 + 统计提示标签 + 月份选择器
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "沅满记账",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                        )

                        // 点击卡片进入统计的轻量胶囊提示
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = primaryColor.copy(alpha = 0.12f),
                            border = BorderStroke(0.5.dp, primaryColor.copy(alpha = 0.25f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QueryStats,
                                    contentDescription = "数据统计",
                                    tint = primaryColor,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "统计 ›",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryColor
                                    )
                                )
                            }
                        }
                    }

                    // 月份选择器 (点击独立弹出选择器)
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.4f)),
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { onMonthClick() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${year}年${month}月",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "切换月份",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 中部：本月总支出
                Text(
                    text = "本月总支出 (元)",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = MoneyUtils.centsToYuanString(totalExpense, withGrouping = true),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Spacer(modifier = Modifier.height(12.dp))

                // 底部两栏：收入 与 结余
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "本月收入",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = MoneyUtils.formatCurrency(totalIncome, isExpense = false),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "本月结余",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val balanceColor = when {
                            balance > 0L -> MaterialTheme.colorScheme.primary
                            balance < 0L -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Text(
                            text = if (balance < 0L) "-${MoneyUtils.formatCurrency(-balance)}" else MoneyUtils.formatCurrency(balance),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = balanceColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecordCardItem(
    item: RecordWithCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val record = item.record
    val category = item.category
    val isExpense = record.type == RecordType.EXPENSE.name

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 分类图标
            CategoryIconView(
                iconName = category?.iconName ?: "other",
                colorHex = category?.colorHex ?: 0xFF607D8BL,
                size = 42.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            // 分类名称、备注、支付方式
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = category?.name ?: "未分类",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (record.paymentMethod.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = record.paymentMethod,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateTimeUtils.formatTime(record.recordTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (record.remark.isNotBlank()) {
                        Text(
                            text = " · ${record.remark}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 金额展示
            AmountDisplay(
                amountInCents = record.amount,
                type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME,
                showSign = true,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
