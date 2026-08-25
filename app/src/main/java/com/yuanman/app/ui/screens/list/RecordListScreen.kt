package com.yuanman.app.ui.screens.list

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.DateGroupHeader
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.MonthPickerModal
import com.yuanman.app.ui.screens.home.BitgetTransactionItem
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
    var showSortMenu by remember { mutableStateOf(false) }

    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("账单明细", fontWeight = FontWeight.Bold) },
                actions = {
                    // 月份快捷切换按钮 (< 2026年8月 >)
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
                                onClick = { viewModel.previousMonth() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "上月",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "${uiState.selectedYear}年${uiState.selectedMonth}月",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showMonthPicker = true }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )

                            IconButton(
                                onClick = { viewModel.nextMonth() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "下月",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 全宽搜索输入框
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("搜索备注、分类或支付方式...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "搜索",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateSearchQuery("") }) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "清除",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
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
            }

            // 筛选标签栏（类型 + 排序 + 分类）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                // 第一行：类型筛选与排序
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedType == null,
                                onClick = { viewModel.selectType(null) },
                                label = { Text("全部", fontSize = 12.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedType == RecordType.EXPENSE,
                                onClick = { viewModel.selectType(RecordType.EXPENSE) },
                                label = { Text("仅支出", fontSize = 12.sp) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = uiState.selectedType == RecordType.INCOME,
                                onClick = { viewModel.selectType(RecordType.INCOME) },
                                label = { Text("仅收入", fontSize = 12.sp) }
                            )
                        }
                    }

                    // 排序下拉菜单
                    Box {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSortMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(uiState.sortOrder.title, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            RecordSortOrder.values().forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.title) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                // 第二行：分类筛选
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
                                        fontSize = 11.sp,
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
                                        fontSize = 11.sp,
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
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${uiState.recordCount} 笔明细",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "支出 ¥${MoneyUtils.centsToYuanString(uiState.totalExpense)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                        Text(
                            text = "收入 ¥${MoneyUtils.centsToYuanString(uiState.totalIncome)}",
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
                    description = "请尝试更换月份、分类或搜索关键词",
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
                            BitgetTransactionItem(
                                item = item,
                                onClick = { onNavigateToDetail(item.record.id) },
                                onLongClick = { activeMenuRecord = item }
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

    // 长按快捷操作底部弹层
    if (activeMenuRecord != null) {
        val target = activeMenuRecord!!
        ModalBottomSheet(
            onDismissRequest = { activeMenuRecord = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "账单操作 · ${target.category?.name ?: "未分类"} ¥${MoneyUtils.centsToYuanString(target.record.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("复制再记一笔") },
                    supportingContent = { Text("以此分类与金额为模板快速新增一条今日账单") },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                        }
                )

                ListItem(
                    headlineContent = { Text("查看详情") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val id = target.record.id
                            activeMenuRecord = null
                            onNavigateToDetail(id)
                        }
                )

                ListItem(
                    headlineContent = { Text("删除该账单", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            recordToDelete = target
                            activeMenuRecord = null
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    ConfirmDeleteDialog(
        visible = recordToDelete != null,
        title = "删除账单",
        message = "确定要删除分类为「${recordToDelete?.category?.name}」金额为「${MoneyUtils.formatCurrency(recordToDelete?.record?.amount ?: 0L)}」的账单吗？",
        onConfirm = {
            recordToDelete?.let { viewModel.deleteRecord(it) }
            recordToDelete = null
        },
        onDismiss = { recordToDelete = null }
    )
}
