package com.yuanman.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.clickableDebounce
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = LocalToastHostState.current

    var showMonthPicker by remember { mutableStateOf(false) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showQuickEntryCloseConfirm by remember { mutableStateOf(false) }
    var quickEntryType by remember { mutableStateOf(RecordType.EXPENSE) }
    var selectedFilterType by remember { mutableStateOf<RecordType?>(null) }

    // 长按快捷菜单状态
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }
    var openSwipeItemId by remember { mutableStateOf<Long?>(null) }

    // 首页默认只展示今天的账单，支持按全部/支出/收入快速筛选。
    val today = Calendar.getInstance()
    val todayDayTimestamp = today.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val todayRecords = if (uiState.selectedYear == today.get(Calendar.YEAR) && uiState.selectedMonth == today.get(Calendar.MONTH) + 1) {
        uiState.groupedRecords[todayDayTimestamp].orEmpty()
    } else emptyList()
    val visibleRecords = if (selectedFilterType == null) todayRecords else todayRecords.filter {
        it.record.type == selectedFilterType?.name
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val pullRefreshState = rememberPullToRefreshState(enabled = { !isRefreshing })
    var refreshWasRunning by remember { mutableStateOf(false) }

    LaunchedEffect(pullRefreshState.isRefreshing) {
        if (pullRefreshState.isRefreshing) {
            refreshWasRunning = true
            viewModel.refresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            refreshWasRunning = true
        } else if (refreshWasRunning) {
            if (pullRefreshState.isRefreshing) {
                pullRefreshState.endRefresh()
            }
            toast.success("刷新成功")
            refreshWasRunning = false
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 🌟 1. 顶部收支看板与预算进度卡片
                FinancialOverviewCard(
                    year = uiState.selectedYear,
                    month = uiState.selectedMonth,
                    totalExpense = uiState.summary.totalExpense,
                    totalIncome = uiState.summary.totalIncome,
                    balance = uiState.summary.balance,
                    monthlyBudget = uiState.monthlyBudget,
                    budgetUsedPercent = uiState.budgetUsedPercent,
                    remainingBudgetCents = uiState.remainingBudgetCents,
                    dailyAvailableCents = uiState.dailyAvailableCents,
                    remainingDays = uiState.remainingDays,
                    onPrevMonth = { viewModel.previousMonth() },
                    onNextMonth = { viewModel.nextMonth() },
                    onMonthClick = { showMonthPicker = true },
                    onBudgetClick = { showBudgetDialog = true },
                    onCardClick = onNavigateToStatistics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                // 2. 首页直达的自然语言快捷记账（可在设置中关闭）
                if (uiState.quickEntryEnabled) {
                    QuickEntryStrip(
                        type = quickEntryType,
                        categories = uiState.quickEntryCategories,
                        learningRules = uiState.quickEntryLearningRules,
                        onTypeChange = { quickEntryType = it },
                        onSubmit = { input, type ->
                            val saved = viewModel.saveQuickEntry(input, type)
                            if (saved != null) {
                                val paymentSuffix = saved.paymentMethod?.let { " · $it" }.orEmpty()
                                toast.success(
                                    "已记下 ${saved.category?.name ?: "账单"} · ¥${saved.amountYuan.toPlainString()}$paymentSuffix"
                                )
                                true
                            } else {
                                toast.info("请输入类似“奶茶 18”的内容")
                                false
                            }
                        },
                        onClose = { showQuickEntryCloseConfirm = true },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // 3. 今日账单
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "今日账单",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    HomeFilterChip("全部", selectedFilterType == null) { selectedFilterType = null }
                    Spacer(modifier = Modifier.width(4.dp))
                    HomeFilterChip("支出", selectedFilterType == RecordType.EXPENSE) { selectedFilterType = RecordType.EXPENSE }
                    Spacer(modifier = Modifier.width(4.dp))
                    HomeFilterChip("收入", selectedFilterType == RecordType.INCOME) { selectedFilterType = RecordType.INCOME }
                }

                // 4. 当前时间范围账单列表
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                ) {
                    if (visibleRecords.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center
                        ) {
                            EmptyStateView(
                                title = if (todayRecords.isEmpty()) "今日暂无账单" else "暂无${if (selectedFilterType == RecordType.EXPENSE) "支出" else "收入"}账单",
                                description = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 2.dp, bottom = 96.dp)
                        ) {
                            items(
                                items = visibleRecords,
                                key = { "record_${it.record.id}" }
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
                                Spacer(modifier = Modifier.height(5.dp))
                            }
                        }
                    }

                    // 拖动阶段保留进度反馈，但使用透明容器，避免短距离下拉出现深色圆块。
                    if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) {
                        PullToRefreshContainer(
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp),
                            containerColor = Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.primary
                        )
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

    // 预算配置弹窗
    if (showBudgetDialog) {
        var budgetInput by remember {
            mutableStateOf(
                if (uiState.monthlyBudget > 0L) MoneyUtils.centsToYuanString(uiState.monthlyBudget) else ""
            )
        }

        Dialog(onDismissRequest = { showBudgetDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "设置 ${uiState.selectedYear}年${uiState.selectedMonth}月预算",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "只影响当前月份；切换月份即可查看和设置各月预算。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("预算金额 (元)") },
                        placeholder = { Text("如: 5000") },
                        prefix = { Text("¥ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setMonthlyBudget(0L)
                                showBudgetDialog = false
                            }
                        ) {
                            Text("清除预算", color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val cents = MoneyUtils.parseYuanToCents(budgetInput)
                                viewModel.setMonthlyBudget(cents)
                                showBudgetDialog = false
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // 长按快捷操作底部弹层（直接展示明细）
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
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "账单详情",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                RecordDetailCard(item = target)

                ListItem(
                    headlineContent = { Text("复制一笔") },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                            toast.success("已成功复制一笔账单")
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
        message = "确定要删除「${recordToDelete?.category?.name ?: "未分类"}」金额为 ¥${MoneyUtils.centsToYuanString(recordToDelete?.record?.amount ?: 0L)} 的账单吗？",
        onConfirm = {
            recordToDelete?.let { target ->
                viewModel.deleteRecord(target)
                toast.info(
                    message = "账单已删除",
                    actionLabel = "撤销",
                    onAction = { viewModel.undoDelete(target.record) }
                )
            }
            recordToDelete = null
        },
        onDismiss = { recordToDelete = null }
    )

    // 关闭快捷录入前明确告知用户仍可从设置中恢复，避免误触后找不到入口。
    ConfirmDeleteDialog(
        visible = showQuickEntryCloseConfirm,
        title = "关闭快捷记账",
        message = "首页快捷录入将被隐藏。你仍可在“设置 → 快捷记账”中随时重新开启。",
        icon = Icons.Default.Bolt,
        confirmButtonText = "关闭快捷记账",
        confirmButtonColor = MaterialTheme.colorScheme.primary,
        onConfirm = {
            viewModel.setQuickEntryEnabled(false)
            showQuickEntryCloseConfirm = false
        },
        onDismiss = { showQuickEntryCloseConfirm = false }
    )
}

@Composable
private fun HomeFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        modifier = Modifier.clip(shape).clickable(onClick = onClick),
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuickEntryStrip(
    type: RecordType,
    categories: List<com.yuanman.app.data.local.entity.CategoryEntity>,
    learningRules: List<com.yuanman.app.data.local.entity.QuickEntryLearningEntity>,
    onTypeChange: (RecordType) -> Unit,
    onSubmit: (String, RecordType) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val availableCategories = remember(categories, type) {
        categories.filter { it.type == type.name }
    }
    val preview = remember(text, availableCategories, learningRules) {
        QuickEntryParser.parse(text, availableCategories, learningRules)
    }
    val isExpense = type == RecordType.EXPENSE
    val accent = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val isReady = preview != null
    val btnBgColor by animateColorAsState(
        targetValue = if (isReady) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        label = "btn_bg"
    )
    val btnIconColor by animateColorAsState(
        targetValue = if (isReady) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        label = "btn_icon"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 5.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 1. 支 / 收 极简切换胶囊
            QuickTypeTogglePill(
                selectedType = type,
                onTypeChange = onTypeChange
            )

            // 2. 原生无框极简输入框
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 2.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = if (isReady) ImeAction.Done else ImeAction.Default
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (isReady) {
                            if (onSubmit(text, type)) text = ""
                        }
                    }
                ),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                text = "✨ 闪电记账 如: 咖啡15块",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.75f),
                                    fontSize = 13.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // 3. 实时智能解析徽章 (单行平铺，防止折行)
            AnimatedVisibility(
                visible = isReady,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                if (preview != null) {
                    val cat = preview.category
                    val catColor = cat?.let { Color(it.colorHex) } ?: accent
                    val iconVector = cat?.let { CategoryIconHelper.getIcon(it.iconName) } ?: Icons.Default.Bolt

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = accent.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.25f)),
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = null,
                                tint = catColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = cat?.name ?: "账单",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                    color = accent
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "¥${preview.amountYuan.toPlainString()}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.sp,
                                    color = accent
                                ),
                                maxLines = 1,
                                softWrap = false
                            )
                            preview.paymentMethod?.let { method ->
                                Text(
                                    text = "·$method",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.outline
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }

            // 4. 动态操作按钮 (一键保存 / 清空 / 关闭)
            IconButton(
                onClick = {
                    if (isReady) {
                        if (onSubmit(text, type)) text = ""
                    } else if (text.isNotEmpty()) {
                        text = ""
                    } else {
                        onClose()
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(btnBgColor)
            ) {
                Icon(
                    imageVector = when {
                        isReady -> Icons.Default.Check
                        text.isNotEmpty() -> Icons.Default.Clear
                        else -> Icons.Default.Close
                    },
                    contentDescription = if (isReady) "记下" else if (text.isNotEmpty()) "清空" else "关闭快捷记账",
                    tint = btnIconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun QuickTypeTogglePill(
    selectedType: RecordType,
    onTypeChange: (RecordType) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        modifier = Modifier.clip(RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier.padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickTypeToggleItem(
                label = "支",
                selected = selectedType == RecordType.EXPENSE,
                activeColor = MaterialTheme.colorScheme.error,
                onClick = { onTypeChange(RecordType.EXPENSE) }
            )
            QuickTypeToggleItem(
                label = "收",
                selected = selectedType == RecordType.INCOME,
                activeColor = MaterialTheme.colorScheme.primary,
                onClick = { onTypeChange(RecordType.INCOME) }
            )
        }
    }
}

@Composable
private fun QuickTypeToggleItem(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) activeColor else Color.Transparent,
        label = "type_item_bg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "type_item_text"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = textColor
            )
        )
    }
}

/**
 * 🌟 核心财务看板 (强化收支表达 + 预算进度条)
 */
@Composable
private fun FinancialOverviewCard(
    year: Int,
    month: Int,
    totalExpense: Long,
    totalIncome: Long,
    balance: Long,
    monthlyBudget: Long,
    budgetUsedPercent: Float,
    remainingBudgetCents: Long,
    dailyAvailableCents: Long,
    remainingDays: Int,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthClick: () -> Unit,
    onBudgetClick: () -> Unit,
    onCardClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val cardSurfaceColor = MaterialTheme.colorScheme.surface
    val textureLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.022f)
    val textureGlowColor = primaryColor.copy(alpha = 0.035f)
    val cardShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomEnd = 18.dp,
        bottomStart = 18.dp
    )

    Card(
        modifier = modifier
            .clip(cardShape)
            .drawBehind {
                // 低对比度斜向纹理 + 柔和光晕，增强层次但不干扰金额阅读。
                drawRect(cardSurfaceColor)

                val spacing = 22.dp.toPx()
                val lineWidth = 1.dp.toPx()
                var x = -size.height
                while (x < size.width + size.height) {
                    drawLine(
                        color = textureLineColor,
                        start = Offset(x, 0f),
                        end = Offset(x + size.height, size.height),
                        strokeWidth = lineWidth
                    )
                    drawLine(
                        color = textureLineColor.copy(alpha = 0.012f),
                        start = Offset(x + size.height * 0.45f, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = lineWidth
                    )
                    x += spacing
                }

                drawCircle(
                    color = textureGlowColor,
                    radius = size.minDimension * 0.72f,
                    center = Offset(size.width * 0.96f, size.height * 0.04f)
                )
                drawCircle(
                    color = textureGlowColor.copy(alpha = 0.022f),
                    radius = size.minDimension * 0.52f,
                    center = Offset(size.width * 0.02f, size.height * 0.98f)
                )
            }
            .then(
                if (onCardClick != null) {
                    Modifier.clickableDebounce(debounceTimeMs = 500L, onClick = onCardClick)
                } else Modifier
            ),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // 顶栏：账本标题 + 月份切换器
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                    Text(
                        text = "沅满账本",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 月份胶囊
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                    ) {
                        IconButton(
                            onClick = onPrevMonth,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "上月",
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Text(
                            text = "${year}年${month}月",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onMonthClick() }
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        )

                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "下月",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🌟 支出与收入并重展示区 (紧凑清晰表达)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 支出列
                Column(modifier = Modifier.weight(1.2f)) {
                    Text(
                        text = "本月总支出",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "¥",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = MoneyUtils.centsToYuanString(totalExpense, withGrouping = true),
                            style = MaterialTheme.typography.displayMedium.copy(
                                fontSize = 25.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.5).sp
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                // 收入与结余列
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = "本月总收入",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = "¥${MoneyUtils.centsToYuanString(totalIncome, withGrouping = true)}",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = primaryColor
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (balance >= 0) "结余 +¥${MoneyUtils.centsToYuanString(balance)}" else "结余 -¥${MoneyUtils.centsToYuanString(-balance)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = if (balance >= 0) primaryColor else MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(6.dp))

            // 🌟 预算设置与进度条展示区
            if (monthlyBudget > 0L) {
                val budgetColor = when {
                    budgetUsedPercent <= 0.75f -> primaryColor
                    budgetUsedPercent <= 0.95f -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.error
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBudgetClick() }
                        .padding(vertical = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "月预算 ¥${MoneyUtils.centsToYuanString(monthlyBudget)}",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "修改预算",
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(10.dp)
                            )
                        }

                        Text(
                            text = if (remainingBudgetCents >= 0) {
                                "剩余 ¥${MoneyUtils.centsToYuanString(remainingBudgetCents)} · 已用 ${(budgetUsedPercent * 100).toInt()}%"
                            } else {
                                "已超支 ¥${MoneyUtils.centsToYuanString(-remainingBudgetCents)}"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = budgetColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { budgetUsedPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = budgetColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    if (remainingDays > 0 && remainingBudgetCents > 0L) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "本月剩余 $remainingDays 天 · 日均建议支出 ¥${MoneyUtils.centsToYuanString(dailyAvailableCents)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                // 未设置预算提示行
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onBudgetClick() }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "暂未设置月度预算，点击快速设定",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Text(
                        text = "设预算",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun BitgetFilterPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.5.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.5.dp)
        )
    }
}

/**
 * 🌟 交易记录卡片
 */
@Composable
fun BitgetTransactionItem(
    item: RecordWithCategory,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val record = item.record
    val category = item.category
    val isExpense = record.type == RecordType.EXPENSE.name
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickableDebounce(
                debounceTimeMs = 500L,
                onLongClick = onLongClick,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconView(
                iconName = category?.iconName ?: "other",
                colorHex = category?.colorHex ?: 0xFF607D8BL,
                size = 32.dp,
                iconSize = 16.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "未分类",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = listOf(record.remark, record.paymentMethod)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isExpense) "-¥${MoneyUtils.centsToYuanString(record.amount)}" else "+¥${MoneyUtils.centsToYuanString(record.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = if (isExpense) MaterialTheme.colorScheme.error else primaryColor
                )

                Text(
                    text = DateTimeUtils.formatTime(record.recordTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
