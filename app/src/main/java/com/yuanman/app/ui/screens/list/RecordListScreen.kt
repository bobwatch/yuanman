package com.yuanman.app.ui.screens.list

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.DateGroupHeader
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.MonthPickerModal
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.RecordDetailCard
import com.yuanman.app.ui.components.SwipeRevealDeleteItem
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.ui.components.YuanmanPullRefreshIndicator
import com.yuanman.app.ui.components.YuanmanDatePickerSheet
import com.yuanman.app.ui.screens.home.BitgetTransactionItem
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun RecordListScreen(
    viewModel: RecordListViewModel,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val toast = LocalToastHostState.current
    val listState = rememberLazyListState()
    var showMonthPicker by remember { mutableStateOf(false) }
    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var openSwipeItemId by remember { mutableStateOf<Long?>(null) }
    var refreshWasRunning by remember { mutableStateOf(false) }
    val pullRefreshState = rememberPullToRefreshState(enabled = { !isRefreshing && !refreshWasRunning })

    // 下拉触发刷新；刷新期间禁用再次下拉，避免状态互相覆盖导致指示器卡住。
    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing && !refreshWasRunning) {
            refreshWasRunning = true
            viewModel.refresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (!isRefreshing && refreshWasRunning) {
            if (pullRefreshState.isRefreshing) {
                pullRefreshState.endRefresh()
            }
            toast.success("刷新成功")
            refreshWasRunning = false
        }
    }

    // 🌟 提前 4 项静默预加载下一页（无感加载）
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleItemIndex >= totalItems - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && uiState.hasMore && !uiState.isLoadingMore && !uiState.isLoading) {
            viewModel.loadNextPage()
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = {
                    Text(
                        text = "账单明细",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 🌟 1. 全宽现代搜索框
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(
                        width = if (searchFocused) 1.5.dp else 0.75.dp,
                        color = if (searchFocused) primaryColor.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                    text = "搜索备注、金额、分类或支付方式...",
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
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { searchFocused = it.isFocused }
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
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { showSortMenu = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "排序", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(uiState.sortOrder.title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
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
                            val isCustomDaySelected = uiState.selectedDay != null && uiState.selectedDay != currentDay && uiState.selectedDay != (currentDay - 1)
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isCustomDaySelected) {
                                    primaryColor.copy(alpha = 0.14f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (isCustomDaySelected) {
                                        primaryColor.copy(alpha = 0.8f)
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                                    }
                                ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { showDatePickerSheet = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CalendarMonth,
                                        contentDescription = "选择日期",
                                        tint = if (uiState.selectedDay != null) primaryColor else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (uiState.selectedDay != null) "${uiState.selectedMonth}月${uiState.selectedDay}日" else "选择日期",
                                        fontSize = 12.sp,
                                        fontWeight = if (uiState.selectedDay != null) FontWeight.Bold else FontWeight.Normal,
                                        color = if (uiState.selectedDay != null) primaryColor else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (uiState.selectedDay != null) {
                                        Spacer(modifier = Modifier.width(4.dp))
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
                                    selected = uiState.selectedCategoryIds.isEmpty(),
                                    onClick = { viewModel.selectCategory(null) }
                                )
                            }

                            items(relevantCategories) { cat ->
                                val isSelected = cat.id in uiState.selectedCategoryIds
                                ModernFilterPill(
                                    text = cat.name,
                                    selected = isSelected,
                                    onClick = { viewModel.selectCategory(cat.id) }
                                )
                            }
                        }
                    }

                    // 第四行：支付方式筛选
                    val paymentMethods = when (uiState.selectedType) {
                        RecordType.INCOME -> PaymentMethod.INCOME_ACCOUNTS
                        RecordType.EXPENSE -> PaymentMethod.EXPENSE_METHODS
                        null -> (PaymentMethod.EXPENSE_METHODS + PaymentMethod.INCOME_ACCOUNTS).distinct()
                    }.distinct()
                    Spacer(modifier = Modifier.height(2.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            ModernFilterPill(
                                text = "全部方式",
                                selected = uiState.selectedPaymentMethods.isEmpty(),
                                onClick = { viewModel.selectPaymentMethod(null) }
                            )
                        }
                        items(paymentMethods) { method ->
                            val isSelected = method in uiState.selectedPaymentMethods
                            ModernFilterPill(
                                text = method,
                                selected = isSelected,
                                onClick = { viewModel.selectPaymentMethod(method) }
                            )
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (uiState.filteredRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                title = if (uiState.searchQuery.isNotEmpty()) "未找到相关账单" else "该时间段暂无账单记录",
                                description = if (uiState.searchQuery.isNotEmpty()) "换个关键词试试" else "点击底部「＋」记账",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 88.dp)
                        ) {
                            uiState.groupedRecords.forEach { (dayTimestamp, recordsInDay) ->
                                val summary = uiState.daySummaries[dayTimestamp]
                                stickyHeader(key = "header_$dayTimestamp") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.background,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        DateGroupHeader(
                                            timestamp = dayTimestamp,
                                            totalExpense = summary?.totalExpense ?: 0L,
                                            totalIncome = summary?.totalIncome ?: 0L
                                        )
                                    }
                                }

                                items(
                                    items = recordsInDay,
                                    key = { it.record.id }
                                ) { item ->
                                    SwipeRevealDeleteItem(
                                        itemKey = item.record.id,
                                        openKey = openSwipeItemId,
                                        onOpen = { openSwipeItemId = it },
                                        onDelete = { recordToDelete = item }
                                    ) {
                                        BitgetTransactionItem(
                                            item = item,
                                            onClick = { onNavigateToEdit(item.record.id) },
                                            onLongClick = { activeMenuRecord = item }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            // 🌟 底部平滑加载/全部展示提示
                            if (uiState.isLoadingMore) {
                                item(key = "footer_loading_more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = primaryColor
                                        )
                                    }
                                }
                            } else if (!uiState.hasMore && uiState.filteredRecords.size >= 25) {
                                item(key = "footer_no_more_records") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "— 已展示全部账单 —",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 进度读取封装在指示器内部，避免拖动时重组整页账单。
                    YuanmanPullRefreshIndicator(
                        state = pullRefreshState,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 8.dp)
                    )
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

    if (showDatePickerSheet) {
        val initialDate = Calendar.getInstance().apply {
            set(Calendar.YEAR, uiState.selectedYear)
            set(Calendar.MONTH, uiState.selectedMonth - 1)
            val now = Calendar.getInstance()
            val defaultDay = if (uiState.selectedYear == now.get(Calendar.YEAR) && uiState.selectedMonth == now.get(Calendar.MONTH) + 1) {
                now.get(Calendar.DAY_OF_MONTH)
            } else {
                1
            }
            set(Calendar.DAY_OF_MONTH, uiState.selectedDay ?: defaultDay)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        YuanmanDatePickerSheet(
            initialDateMillis = initialDate.timeInMillis,
            onDateSelected = viewModel::selectDate,
            onDismiss = { showDatePickerSheet = false }
        )
    }

    // 长按操作 BottomSheet：直接展示账单明细
    if (activeMenuRecord != null) {
        val target = activeMenuRecord!!
        YuanmanModalBottomSheet(
            onDismissRequest = { activeMenuRecord = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "账单详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                RecordDetailCard(item = target)

                ListItem(
                    headlineContent = { Text("复制一笔") },
                    leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                        }
                )

                ListItem(
                    headlineContent = { Text("删除账单", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
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
        message = "确定要删除分类为「${recordToDelete?.category?.name ?: "未分类"}」金额为「${MoneyUtils.formatCurrency(recordToDelete?.record?.amount ?: 0L)}」的账单吗？",
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
        color = if (selected) primaryColor.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(
            1.dp,
            if (selected) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        ),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) primaryColor else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
