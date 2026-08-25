package com.yuanman.app.ui.screens.list

import android.app.DatePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
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
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showMonthPicker by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "账单明细",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    // 顶栏右侧：月份切换胶囊
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Text(
                                text = "${uiState.selectedYear}年${uiState.selectedMonth}月",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
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
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
            // 🌟 1. 全宽现代搜索框
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜索",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (uiState.searchQuery.isEmpty()) {
                            Text(
                                text = "搜索备注、分类或支付方式...",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.outline
                                ),
                                maxLines = 1
                            )
                        }
                        BasicTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            cursorBrush = SolidColor(primaryColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.updateSearchQuery("") },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 🌟 2. 筛选过滤区（类型切换 + 排序 + 日期天筛选）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 4.dp)
            ) {
                // 第一行：类型切换 (全部 / 仅支出 / 仅收入) 与 排序
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 和谐主题色类型分段胶囊
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        ModernFilterPill(
                            text = "全部",
                            selected = uiState.selectedType == null,
                            onClick = { viewModel.selectType(null) }
                        )
                        ModernFilterPill(
                            text = "仅支出",
                            selected = uiState.selectedType == RecordType.EXPENSE,
                            onClick = { viewModel.selectType(RecordType.EXPENSE) }
                        )
                        ModernFilterPill(
                            text = "仅收入",
                            selected = uiState.selectedType == RecordType.INCOME,
                            onClick = { viewModel.selectType(RecordType.INCOME) }
                        )
                    }

                    // 排序按钮
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

                Spacer(modifier = Modifier.height(6.dp))

                // 第二行：按天筛选快捷栏（支持精准选择到天）
                val todayCal = Calendar.getInstance()
                val isCurrentSelectedMonth = todayCal.get(Calendar.YEAR) == uiState.selectedYear &&
                        (todayCal.get(Calendar.MONTH) + 1) == uiState.selectedMonth
                val currentDay = todayCal.get(Calendar.DAY_OF_MONTH)

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 全月
                    item {
                        ModernFilterPill(
                            text = "全月",
                            selected = uiState.selectedDay == null,
                            onClick = { viewModel.selectDay(null) }
                        )
                    }

                    if (isCurrentSelectedMonth) {
                        item {
                            ModernFilterPill(
                                text = "今日 (${currentDay}日)",
                                selected = uiState.selectedDay == currentDay,
                                onClick = { viewModel.selectDay(currentDay) }
                            )
                        }

                        if (currentDay > 1) {
                            item {
                                ModernFilterPill(
                                    text = "昨日 (${currentDay - 1}日)",
                                    selected = uiState.selectedDay == currentDay - 1,
                                    onClick = { viewModel.selectDay(currentDay - 1) }
                                )
                            }
                        }
                    }

                    // 自定义日期选择器 📅
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (uiState.selectedDay != null && uiState.selectedDay != currentDay && uiState.selectedDay != (currentDay - 1)) {
                                primaryColor.copy(alpha = 0.15f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (uiState.selectedDay != null && uiState.selectedDay != currentDay && uiState.selectedDay != (currentDay - 1)) {
                                    primaryColor
                                } else {
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                }
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val pickerCal = Calendar.getInstance().apply {
                                        set(Calendar.YEAR, uiState.selectedYear)
                                        set(Calendar.MONTH, uiState.selectedMonth - 1)
                                        set(Calendar.DAY_OF_MONTH, uiState.selectedDay ?: 1)
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            viewModel.selectDate(y, m + 1, d)
                                        },
                                        pickerCal.get(Calendar.YEAR),
                                        pickerCal.get(Calendar.MONTH),
                                        pickerCal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CalendarMonth,
                                    contentDescription = "选择日期",
                                    tint = if (uiState.selectedDay != null) primaryColor else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (uiState.selectedDay != null) "${uiState.selectedMonth}月${uiState.selectedDay}日" else "选特定日期",
                                    fontSize = 11.sp,
                                    fontWeight = if (uiState.selectedDay != null) FontWeight.Bold else FontWeight.Normal,
                                    color = if (uiState.selectedDay != null) primaryColor else MaterialTheme.colorScheme.onSurface
                                )
                                if (uiState.selectedDay != null) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "清除天筛选",
                                        tint = primaryColor,
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { viewModel.selectDay(null) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 第三行：分类筛选横滑栏
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
                            ModernFilterPill(
                                text = "全部分类",
                                selected = uiState.selectedCategoryId == null,
                                onClick = { viewModel.selectCategory(null) }
                            )
                        }

                        items(relevantCategories) { cat ->
                            val isSelected = uiState.selectedCategoryId == cat.id
                            ModernFilterPill(
                                text = cat.name,
                                selected = isSelected,
                                onClick = { viewModel.selectCategory(cat.id) }
                            )
                        }
                    }
                }
            }

            // 🌟 3. 当月汇总收支条
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
                        text = if (uiState.selectedDay != null) {
                            "已筛选 ${uiState.selectedMonth}月${uiState.selectedDay}日 · 共 ${uiState.recordCount} 笔"
                        } else {
                            "共 ${uiState.recordCount} 笔记录"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "支 ¥${MoneyUtils.centsToYuanString(uiState.totalExpense)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        )
                        Text(
                            text = "收 ¥${MoneyUtils.centsToYuanString(uiState.totalIncome)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = primaryColor
                            )
                        )
                    }
                }
            }

            // 🌟 4. 账单列表区
            if (uiState.filteredRecords.isEmpty()) {
                EmptyStateView(
                    title = if (uiState.searchQuery.isNotEmpty()) "未找到相关账单" else "该时间段暂无账单记录",
                    description = if (uiState.searchQuery.isNotEmpty()) "可尝试搜索其他关键词" else "去记一笔新的账单吧",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp)
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
                                onClick = { onNavigateToDetail(item.record.id) },
                                onLongClick = { activeMenuRecord = item }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                thickness = 0.5.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // 月份选择器
    MonthPickerModal(
        visible = showMonthPicker,
        initialYear = uiState.selectedYear,
        initialMonth = uiState.selectedMonth,
        onMonthSelected = { y, m ->
            viewModel.selectMonth(y, m)
            showMonthPicker = false
        },
        onDismiss = { showMonthPicker = false }
    )

    // 长按操作 BottomSheet
    if (activeMenuRecord != null) {
        val target = activeMenuRecord!!
        ModalBottomSheet(
            onDismissRequest = { activeMenuRecord = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "账单操作",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                ListItem(
                    headlineContent = { Text("编辑账单") },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val id = target.record.id
                            activeMenuRecord = null
                            onNavigateToEdit(id)
                        }
                )

                ListItem(
                    headlineContent = { Text("复制这笔账单") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                        }
                )

                ListItem(
                    headlineContent = { Text("查看详情") },
                    leadingContent = { Icon(Icons.Default.Visibility, contentDescription = null) },
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

    // 删除确认弹窗
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

/**
 * 🌟 现代优雅过滤胶囊 (Bitget/iOS 风格，完美契合主题色)
 */
@Composable
private fun ModernFilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (selected) primaryColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            0.5.dp,
            if (selected) primaryColor else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface
        )
    }
}
