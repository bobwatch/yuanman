package com.yuanman.app.ui.screens.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.DateGroupHeader
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.MonthPickerModal
import com.yuanman.app.ui.screens.home.BitgetTransactionItem
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.clickableDebounce

/**
 * 🌟 专属分类账单明细页 (Category-Specific Transaction Screen)
 *
 * 视觉与布局特色：
 * 1. 【沉浸式毛玻璃 Hero 卡片】：呈现该分类专属色调的半透明渐变底座 + 呼吸微光图标。
 * 2. 【多维数据概览】：大字号月度总金额 + 笔数 + 笔均 + 最高单笔。
 * 3. 【时序账单流】：按自然日分组，每日小计，支持点击防抖穿透进入详情。
 * 4. 【闪电记账 FAB】：一键快速为该分类记账。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryRecordsScreen(
    viewModel: CategoryRecordsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToAddRecord: ((RecordType, Long) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val category = uiState.category
    val categoryName = category?.name ?: "分类账单"
    val isExpense = category?.type != RecordType.INCOME.name
    val categoryColor = Color(category?.colorHex ?: 0xFF4CAF50L)

    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "$categoryName · 账单明细",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.clickableDebounce(debounceTimeMs = 500L, onClick = onNavigateBack)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 月份选择快捷胶囊
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickableDebounce(debounceTimeMs = 300L) { showMonthPicker = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (uiState.isAllTime) "全部时间" else "${uiState.selectedYear}年${uiState.selectedMonth}月",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (onNavigateToAddRecord != null && category != null) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME
                        onNavigateToAddRecord(type, category.id)
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("记一笔$categoryName", fontWeight = FontWeight.Bold) },
                    containerColor = categoryColor,
                    contentColor = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🌟 1. 专属分类主题毛玻璃 Hero 概览卡片
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = categoryColor.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, categoryColor.copy(alpha = 0.22f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 左侧：大图标与分类属性标签
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (category != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(54.dp)
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    categoryColor.copy(alpha = 0.35f),
                                                    Color.Transparent
                                                )
                                            ),
                                            shape = CircleShape
                                        )
                                ) {
                                    CategoryIconView(
                                        iconName = category.iconName,
                                        colorHex = category.colorHex,
                                        size = 46.dp,
                                        iconSize = 26.dp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column {
                                Text(
                                    text = categoryName,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isExpense) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                ) {
                                    Text(
                                        text = if (isExpense) "支出分类" else "收入分类",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // 右侧：大字号金额
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isExpense) "支出总额" else "收入总额",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = MoneyUtils.formatCurrency(uiState.totalAmount),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 26.sp
                                ),
                                color = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = categoryColor.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 底部多维指标栏：[记账笔数] · [笔均支出] · [单笔最高]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        CategoryMetricColumn(
                            title = "记账笔数",
                            value = "${uiState.recordCount} 笔"
                        )
                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CategoryMetricColumn(
                            title = "单笔平均",
                            value = MoneyUtils.formatCurrency(uiState.avgAmount)
                        )
                        VerticalDivider(
                            modifier = Modifier.height(28.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        CategoryMetricColumn(
                            title = "最高单笔",
                            value = MoneyUtils.formatCurrency(uiState.maxAmount)
                        )
                    }
                }
            }

            // 🌟 2. 筛选胶囊栏 (本月 / 全部历史)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = !uiState.isAllTime,
                    onClick = {
                        if (uiState.isAllTime) {
                            val cal = java.util.Calendar.getInstance()
                            viewModel.selectMonth(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
                        }
                    },
                    label = { Text("${uiState.selectedYear}年${uiState.selectedMonth}月（${uiState.records.size}笔）", fontSize = 12.sp) },
                    leadingIcon = if (!uiState.isAllTime) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else null
                )

                FilterChip(
                    selected = uiState.isAllTime,
                    onClick = { viewModel.toggleAllTime() },
                    label = { Text("全部历史账单", fontSize = 12.sp) },
                    leadingIcon = if (uiState.isAllTime) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(15.dp)) }
                    } else null
                )
            }

            // 🌟 3. 账单明细列表
            if (uiState.records.isEmpty()) {
                EmptyStateView(
                    title = "暂无${categoryName}账单",
                    description = "点击下方按钮，开始记录生活中的每一笔${categoryName}收支",
                    actionButtonText = "记一笔$categoryName",
                    onActionClick = {
                        val type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME
                        if (category != null) {
                            onNavigateToAddRecord?.invoke(type, category.id)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 88.dp)
                ) {
                    uiState.groupedRecords.forEach { (dayTimestamp, recordsInDay) ->
                        val summary = uiState.daySummaries[dayTimestamp]

                        item(key = "header_$dayTimestamp") {
                            DateGroupHeader(
                                timestamp = dayTimestamp,
                                totalExpense = summary?.totalExpense ?: 0L,
                                totalIncome = summary?.totalIncome ?: 0L
                            )
                        }

                        items(
                            items = recordsInDay,
                            key = { it.record.id }
                        ) { item ->
                            BitgetTransactionItem(
                                item = item,
                                onClick = { onNavigateToEdit(item.record.id) },
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .clip(RoundedCornerShape(14.dp))
                            )
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
private fun CategoryMetricColumn(
    title: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
