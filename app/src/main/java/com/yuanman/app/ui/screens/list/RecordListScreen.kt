package com.yuanman.app.ui.screens.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.DateGroupHeader
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.MonthPickerModal
import com.yuanman.app.ui.screens.home.RecordCardItem
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    onNavigateToDetail: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var showMonthPicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    // 顶部搜索输入框
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = { Text("搜索备注、分类或支付方式...", fontSize = 14.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "搜索",
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "清除",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            focusedIndicatorColor = MaterialTheme.colorScheme.primary,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    )
                },
                actions = {
                    // 月份选择按钮
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 筛选标签栏（类型 + 分类）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 6.dp)
            ) {
                // 类型筛选：全部、支出、收入
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = uiState.selectedType == null,
                            onClick = { viewModel.selectType(null) },
                            label = { Text("全部类型") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedType == RecordType.EXPENSE,
                            onClick = { viewModel.selectType(RecordType.EXPENSE) },
                            label = { Text("仅看支出") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = uiState.selectedType == RecordType.INCOME,
                            onClick = { viewModel.selectType(RecordType.INCOME) },
                            label = { Text("仅看收入") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 分类筛选
                val relevantCategories = uiState.availableCategories.filter {
                    uiState.selectedType == null || it.type == uiState.selectedType?.name
                }

                if (relevantCategories.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item {
                            SuggestionChip(
                                onClick = { viewModel.selectCategory(null) },
                                label = {
                                    Text(
                                        "全部分类",
                                        fontWeight = if (uiState.selectedCategoryId == null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.selectedCategoryId == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }

                        items(relevantCategories) { cat ->
                            val isSelected = uiState.selectedCategoryId == cat.id
                            SuggestionChip(
                                onClick = { viewModel.selectCategory(cat.id) },
                                label = {
                                    Text(
                                        cat.name,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                    }
                }
            }

            // 统计条
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${uiState.recordCount} 笔记录",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "支出 ${MoneyUtils.centsToYuanString(uiState.totalExpense)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                        Text(
                            text = "收入 ${MoneyUtils.centsToYuanString(uiState.totalIncome)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }
            }

            // 列表内容
            if (uiState.groupedRecords.isEmpty()) {
                EmptyStateView(
                    title = "未检索到符合条件的账单",
                    description = "请尝试更换月份或筛选条件",
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
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
