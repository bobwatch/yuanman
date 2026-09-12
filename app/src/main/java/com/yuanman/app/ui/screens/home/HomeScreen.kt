package com.yuanman.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.CategoryEntity
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.data.local.entity.RecordWithCategory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.yuanman.app.data.model.CategoryIconHelper
import com.yuanman.app.data.model.QuickEntryParser
import com.yuanman.app.data.model.QuickEntryResult
import com.yuanman.app.data.local.entity.AccountEntity
import com.yuanman.app.data.model.AccountIconHelper
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.MoneyOff
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Star
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.screens.account.AccountUiModel
import com.yuanman.app.ui.components.*
import com.yuanman.app.ui.components.YuanmanPullRefreshIndicator
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.clickableDebounce
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
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

    val accounts by viewModel.accounts.collectAsState()
    val defaultExpenseAccountId by viewModel.defaultExpenseAccountId.collectAsState()
    var quickEntryAccountId by remember { mutableStateOf<Long?>(null) }

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
    val currentYear = today.get(Calendar.YEAR)
    val currentMonth = today.get(Calendar.MONTH) + 1
    val todayDayTimestamp = today.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val isCurrentMonthView = uiState.selectedYear == currentYear && uiState.selectedMonth == currentMonth
    val todayRecords = if (isCurrentMonthView) {
        uiState.groupedRecords[todayDayTimestamp].orEmpty()
    } else emptyList()
    val visibleRecords = if (selectedFilterType == null) todayRecords else todayRecords.filter {
        it.record.type == selectedFilterType?.name
    }
    val isRefreshing by viewModel.isRefreshing.collectAsState()
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
                // 月份翻页只允许回看：以当前自然月为上限（首页看板不涉及未来）
                val canGoNextMonth =
                    uiState.selectedYear < today.get(Calendar.YEAR) ||
                        (uiState.selectedYear == today.get(Calendar.YEAR) &&
                            uiState.selectedMonth < today.get(Calendar.MONTH) + 1)

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
                    canGoNextMonth = canGoNextMonth,
                    onMonthClick = { showMonthPicker = true },
                    onBudgetClick = { showBudgetDialog = true },
                    onCardClick = onNavigateToStatistics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                )

                // 2. 首页直达的自然语言闪电记账（可在设置中关闭）
                if (uiState.quickEntryEnabled) {
                    QuickEntryStrip(
                        type = quickEntryType,
                        draftState = viewModel.quickEntryDraft,
                        categories = uiState.quickEntryCategories,
                        learningRules = uiState.quickEntryLearningRules,
                        accounts = uiState.accounts,
                        defaultExpenseAccount = uiState.defaultExpenseAccount,
                        defaultIncomeAccount = uiState.defaultIncomeAccount,
                        onTypeChange = { quickEntryType = it },
                        onSetDefaultAccount = { acc, isExp ->
                            viewModel.setDefaultPaymentAccount(acc, isExp)
                        },
                        onSubmit = { input, type, categoryOverride, accountOverride ->
                            val saved = viewModel.saveQuickEntry(input, type, categoryOverride, accountOverride)
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

                if (uiState.quickTemplates.isNotEmpty()) {
                    QuickTemplatesRow(
                        templates = uiState.quickTemplates,
                        onReplay = { template ->
                            viewModel.replayTemplate(template)
                            toast.success("已复记 ${template.source.category?.name ?: "账单"} · ¥${MoneyUtils.centsToYuanString(template.source.record.amount)}")
                        },
                        onTogglePin = { template -> viewModel.setTemplatePinned(template, !template.isPinned) },
                        onHide = viewModel::hideTemplate
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
                                title = if (!isCurrentMonthView) {
                                    "正在查看 ${uiState.selectedYear}年${uiState.selectedMonth}月"
                                } else if (todayRecords.isEmpty()) {
                                    "今日暂无账单"
                                } else {
                                    "暂无${if (selectedFilterType == RecordType.EXPENSE) "支出" else "收入"}账单"
                                },
                                description = if (!isCurrentMonthView) {
                                    "上方看板为所选月份的收支汇总；下方列表为今日账单。"
                                } else null,
                                actionButtonText = if (!isCurrentMonthView) "回到本月" else null,
                                onActionClick = if (!isCurrentMonthView) {
                                    { viewModel.selectMonth(currentYear, currentMonth) }
                                } else null,
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
                                // 条目间距与账单明细页保持一致
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                    }

                    // 自绘刷新指示器：无容器无阴影，拖动期显示进度弧、刷新期显示圆环
                    if (pullRefreshState.isRefreshing || pullRefreshState.progress > 0f) {
                        YuanmanPullRefreshIndicator(
                            state = pullRefreshState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
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

    // 预算配置弹窗（拖动滑杆设置金额）
    if (showBudgetDialog) {
        BudgetSliderDialog(
            title = "设置 ${uiState.selectedYear}年${uiState.selectedMonth}月预算",
            subtitle = "只影响当前月份；切换月份即可查看和设置各月预算。",
            initialBudgetCents = uiState.monthlyBudget,
            onSave = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
            },
            onClear = {
                viewModel.setMonthlyBudget(0L)
                showBudgetDialog = false
            },
            onDismiss = { showBudgetDialog = false }
        )
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

                val templatePinned = viewModel.isTemplatePinned(target)
                ListItem(
                    headlineContent = { Text(if (templatePinned) "取消常用" else "固定到常用") },
                    supportingContent = { Text(if (templatePinned) "仍会根据使用频率自动推荐" else "固定后会优先显示在首页") },
                    leadingContent = {
                        Icon(
                            if (templatePinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.setTemplatePinned(target, !templatePinned)
                            activeMenuRecord = null
                            toast.success(if (templatePinned) "已取消固定" else "已固定到常用账单")
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

    // 关闭闪电记账前明确告知用户仍可从设置中恢复，避免误触后找不到入口。
    ConfirmDeleteDialog(
        visible = showQuickEntryCloseConfirm,
        title = "关闭闪电记账",
        message = "首页闪电记账将被隐藏。你仍可在“设置 → 闪电记账”中随时重新开启。",
        icon = Icons.Default.Bolt,
        confirmButtonText = "关闭闪电记账",
        confirmButtonColor = MaterialTheme.colorScheme.primary,
        onConfirm = {
            viewModel.setQuickEntryEnabled(false)
            showQuickEntryCloseConfirm = false
        },
        onDismiss = { showQuickEntryCloseConfirm = false }
    )
}

@Composable
private fun QuickTemplatesRow(
    templates: List<QuickRecordTemplate>,
    onReplay: (QuickRecordTemplate) -> Unit,
    onTogglePin: (QuickRecordTemplate) -> Unit,
    onHide: (QuickRecordTemplate) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Bolt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "常用账单 · 点按复记",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(templates, key = QuickRecordTemplate::key) { template ->
                var menuExpanded by remember(template.key) { mutableStateOf(false) }
                val item = template.source
                Card(
                    modifier = Modifier
                        .widthIn(min = 150.dp, max = 190.dp)
                        .clickableDebounce(debounceTimeMs = 500L) { onReplay(template) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CategoryIconView(
                            iconName = item.category?.iconName ?: "other",
                            colorHex = item.category?.colorHex ?: 0xFF607D8BL,
                            size = 28.dp,
                            iconSize = 14.dp
                        )
                        Spacer(Modifier.width(7.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.record.remark.ifBlank { item.category?.name ?: "账单" },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "¥${MoneyUtils.centsToYuanString(item.record.amount)} · ${template.usageCount}次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Box {
                            IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(30.dp)) {
                                Icon(Icons.Default.MoreVert, contentDescription = "常用账单选项", modifier = Modifier.size(17.dp))
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (template.isPinned) "取消固定" else "固定到最前") },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = { menuExpanded = false; onTogglePin(template) }
                                )
                                DropdownMenuItem(
                                    text = { Text("不再推荐") },
                                    leadingIcon = { Icon(Icons.Outlined.VisibilityOff, contentDescription = null) },
                                    onClick = { menuExpanded = false; onHide(template) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEntryStrip(
    type: RecordType,
    categories: List<CategoryEntity>,
    learningRules: List<QuickEntryLearningEntity>,
    accounts: List<AccountUiModel>,
    defaultExpenseAccount: String,
    defaultIncomeAccount: String,
    onTypeChange: (RecordType) -> Unit,
    onSetDefaultAccount: (String, Boolean) -> Unit,
    onSubmit: (String, RecordType, CategoryEntity?, String?) -> Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    // 用户点按解析徽章手动选定的分类；非空时优先于自动解析结果。
    var manualCategory by remember { mutableStateOf<CategoryEntity?>(null) }
    var manualAccount by remember { mutableStateOf<String?>(null) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val availableCategories = remember(categories, type) {
        categories.filter { it.type == type.name }
    }

    var showCategoryPicker by remember { mutableStateOf(false) }
    var showAccountPicker by remember { mutableStateOf(false) }
    var categoryOverride by remember { mutableStateOf<Pair<String, com.yuanman.app.data.local.entity.CategoryEntity>?>(null) }

    val preview by produceState<QuickEntryResult?>(
        initialValue = null,
        text,
        availableCategories,
        learningRules,
        categoryOverride
    ) {
        if (text.isBlank()) {
            value = null
        } else {
            delay(120L)
            val overrideSnapshot = categoryOverride
            value = withContext(Dispatchers.Default) {
                val parsed = QuickEntryParser.parse(text, availableCategories, learningRules)
                if (parsed != null && overrideSnapshot != null && overrideSnapshot.first == parsed.remark) {
                    parsed.copy(category = overrideSnapshot.second)
                } else {
                    parsed
                }
            }
        }
    }
    // 手动选择后继续改金额等解析词时保留手动分类；分类被删除或与收支类型不符则回退解析结果。
    val effectiveCategory = manualCategory?.takeIf { manual ->
        availableCategories.any { it.id == manual.id }
    } ?: preview?.category
    val isManualCategory = manualCategory != null
    val isExpense = type == RecordType.EXPENSE
    val defaultAccountForType = if (isExpense) defaultExpenseAccount else defaultIncomeAccount
    val effectiveAccount = manualAccount
        ?: preview?.paymentMethod
        ?: defaultAccountForType.takeIf { it.isNotBlank() }
        ?: if (isExpense) "支出账户" else "入账账户"
    val isManualAccount = manualAccount != null
    val accent = if (isExpense) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    val isReady = preview != null
    val btnBgColor by animateColorAsState(
        targetValue = if (isReady) accent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "btn_bg"
    )
    val btnIconColor by animateColorAsState(
        targetValue = if (isReady) Color.White else MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
        label = "btn_icon"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 1.5.dp, shape = RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // === 第一行：类型切换 + 输入框 + 清除/关闭 ===
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 支 / 收 极简切换胶囊
                QuickTypeTogglePill(
                    selectedType = type,
                    onTypeChange = { newType ->
                        if (newType != type) {
                            manualCategory = null
                            manualAccount = null
                        }
                        onTypeChange(newType)
                    }
                )

                // 2. 原生无框极简输入框（独占整行剩余宽度）
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        if (newText.isEmpty()) manualCategory = null
                        text = newText
                    },
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
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
                                val submitAccount = effectiveAccount.takeIf { it != "支出账户" && it != "入账账户" }
                                if (onSubmit(text, type, effectiveCategory, submitAccount)) {
                                    text = ""
                                    manualCategory = null
                                    manualAccount = null
                                }
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
                                    text = "✨ 闪电记账 如: 咖啡15 / 午餐30微信",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f),
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

                // 3. 清空输入或关闭快捷条
                if (text.isNotEmpty()) {
                    IconButton(
                        onClick = {
                            text = ""
                            manualCategory = null
                            manualAccount = null
                        },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "清空输入",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭快捷记账",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // === 第二行（输入内容后才展开）：账户胶囊 + 智能解析结果 + 记一笔按钮 ===
            AnimatedVisibility(
                visible = text.isNotEmpty(),
                enter = expandVertically(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
                exit = shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(160))
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 左侧：账户选择胶囊（点击呼出账户选择与设为默认）
                QuickAccountSelectPill(
                    accountName = effectiveAccount,
                    isManual = isManualAccount,
                    onClick = {
                        focusManager.clearFocus()
                        showAccountPicker = true
                    }
                )

                // 右侧：实时智能解析徽章 + 记一笔保存按钮
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AnimatedVisibility(
                        visible = isReady,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        if (preview != null) {
                            val cat = effectiveCategory
                            val catColor = cat?.let { Color(it.colorHex) } ?: accent
                            val iconVector = cat?.let { CategoryIconHelper.getIcon(it.iconName) } ?: Icons.Default.Bolt

                            Surface(
                                onClick = {
                                    focusManager.clearFocus()
                                    showCategoryPicker = true
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isManualCategory) accent.copy(alpha = 0.16f) else catColor.copy(alpha = 0.12f),
                                border = BorderStroke(
                                    0.8.dp,
                                    if (isManualCategory) accent.copy(alpha = 0.6f) else catColor.copy(alpha = 0.35f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
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
                                            fontSize = 11.5.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                    if (isManualCategory) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "点击修改分类",
                                            tint = accent,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                    Text(
                                        text = "¥${preview.amountYuan.toPlainString()}",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 11.5.sp,
                                            color = accent
                                        ),
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }

                    // 记一笔保存按钮
                    Surface(
                        onClick = {
                            if (isReady) {
                                val submitAccount = effectiveAccount.takeIf { it != "支出账户" && it != "入账账户" }
                                if (onSubmit(text, type, effectiveCategory, submitAccount)) {
                                    text = ""
                                    manualCategory = null
                                    manualAccount = null
                                }
                            }
                        },
                        enabled = isReady,
                        shape = RoundedCornerShape(12.dp),
                        color = btnBgColor,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "记一笔",
                                tint = btnIconColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "记一笔",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = btnIconColor
                                )
                            )
                        }
                    }
                }
                }
            }
        }

        if (showCategoryPicker) {
            QuickCategoryPickSheet(
                categories = availableCategories,
                selectedCategoryId = effectiveCategory?.id,
                isManual = isManualCategory,
                accent = accent,
                onPick = { chosen ->
                    manualCategory = chosen
                    showCategoryPicker = false
                },
                onRestoreAuto = {
                    manualCategory = null
                    showCategoryPicker = false
                },
                onDismiss = { showCategoryPicker = false }
            )
        }

        if (showAccountPicker) {
            QuickAccountPickSheet(
                accounts = accounts,
                selectedAccountName = effectiveAccount,
                defaultAccountName = defaultAccountForType,
                isExpense = isExpense,
                isManual = isManualAccount,
                onPick = { chosen ->
                    manualAccount = chosen
                    showAccountPicker = false
                },
                onSetDefault = { accName, isExp ->
                    onSetDefaultAccount(accName, isExp)
                },
                onRestoreAuto = {
                    manualAccount = null
                    showAccountPicker = false
                },
                onDismiss = { showAccountPicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCategoryPickSheet(
    categories: List<CategoryEntity>,
    selectedCategoryId: Long?,
    isManual: Boolean,
    accent: Color,
    onPick: (CategoryEntity) -> Unit,
    onRestoreAuto: () -> Unit,
    onDismiss: () -> Unit
) {
    YuanmanModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 2.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "选择分类",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                if (isManual) {
                    TextButton(onClick = onRestoreAuto) {
                        Text(
                            text = "恢复自动识别",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent
                        )
                    }
                }
            }
            if (categories.isEmpty()) {
                Text(
                    text = "暂无可用分类",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    textAlign = TextAlign.Center
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                ) {
                    gridItems(categories, key = { it.id }) { category ->
                        val isSelected = category.id == selectedCategoryId
                        val categoryColor = Color(category.colorHex)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onPick(category) }
                                .padding(vertical = 6.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) categoryColor.copy(alpha = 0.18f)
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, categoryColor, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                CategoryIconView(
                                    iconName = category.iconName,
                                    colorHex = category.colorHex,
                                    size = 46.dp,
                                    iconSize = 22.dp
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAccountSelectPill(
    accountName: String,
    isManual: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isManual) scheme.primary.copy(alpha = 0.12f) else scheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(
            0.6.dp,
            if (isManual) scheme.primary.copy(alpha = 0.45f) else scheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CreditCard,
                contentDescription = null,
                tint = if (isManual) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = accountName,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (isManual) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 11.5.sp,
                    color = if (isManual) scheme.primary else scheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.widthIn(max = 120.dp)
            )
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "选择账户",
                tint = if (isManual) scheme.primary else scheme.outline,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAccountPickSheet(
    accounts: List<AccountUiModel>,
    selectedAccountName: String,
    defaultAccountName: String,
    isExpense: Boolean,
    isManual: Boolean,
    onPick: (String) -> Unit,
    onSetDefault: (String, Boolean) -> Unit,
    onRestoreAuto: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val titleText = if (isExpense) "闪电记账 · 选择支出账户" else "闪电记账 · 选择入账账户"

    YuanmanModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                if (isManual) {
                    TextButton(
                        onClick = onRestoreAuto,
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("跟随默认", fontSize = 12.sp, color = scheme.primary)
                    }
                }
            }

            // 1. 我的资金账户
            if (accounts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "我的资金账户",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    ),
                    color = scheme.outline
                )
                Spacer(modifier = Modifier.height(8.dp))

                accounts.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pair.forEach { account ->
                            val isSelected = selectedAccountName == account.name
                            val isDefault = account.name == defaultAccountName
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceVariant.copy(alpha = 0.4f),
                                border = BorderStroke(1.2.dp, if (isSelected) scheme.primary else Color.Transparent),
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPick(account.name) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CategoryIconView(
                                        iconName = account.iconName,
                                        colorHex = account.colorHex,
                                        size = 30.dp,
                                        iconSize = 15.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = account.name,
                                                fontSize = 12.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                                color = scheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            // 默认账户直接点徽章切换：默认态点一下取消，非默认态点一下设为默认
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Surface(
                                                onClick = {
                                                    onSetDefault(if (isDefault) "" else account.name, isExpense)
                                                },
                                                shape = RoundedCornerShape(3.dp),
                                                color = if (isDefault) {
                                                    scheme.primary.copy(alpha = 0.15f)
                                                } else {
                                                    scheme.surfaceVariant.copy(alpha = 0.45f)
                                                },
                                                border = if (isDefault) {
                                                    null
                                                } else {
                                                    BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
                                                }
                                            ) {
                                                Text(
                                                    text = if (isDefault) "默认" else "设默认",
                                                    fontSize = 8.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isDefault) scheme.primary else scheme.onSurfaceVariant,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "¥" + MoneyUtils.centsToYuanString(account.balanceCents, withGrouping = true),
                                            fontSize = 10.sp,
                                            color = scheme.outline,
                                            maxLines = 1
                                        )
                                    }
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = scheme.primary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // （默认账户设置已上收到每张账户卡的「默认 / 设默认」徽章，直接点按切换）
            }

            // 2.（已移除）「常用 / 其它方式」内置预设区块：支付方式 = 账户，预设字符串与账户列表冗余，
            //    只保留账户选择；无账户时给出引导提示。
            if (accounts.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "还没有资金账户：先创建账户后，即可在这里选择「${if (isExpense) "支出" else "入账"}账户」",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                    color = scheme.outline,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickAccountPickerSheet(
    accounts: List<AccountEntity>,
    selectedAccountId: Long?,
    defaultExpenseAccountId: Long?,
    onSetDefaultExpenseAccount: (Long?) -> Unit,
    onDismiss: () -> Unit,
    onSelectAccount: (Long?) -> Unit
) {
    val defaultAccount = remember(defaultExpenseAccountId, accounts) {
        accounts.firstOrNull { it.id == defaultExpenseAccountId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "选择账户",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (defaultExpenseAccountId != null) {
                            "当前默认支出账户：${defaultAccount?.name ?: "已设置"}"
                        } else {
                            "未设置默认支出账户，可点击下方快速设为默认"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 11.sp
                        )
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "关闭", modifier = Modifier.size(20.dp))
                }
            }

            // 未配置默认支出账户时的提示条
            if (defaultExpenseAccountId == null) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "提示：在任一账户右侧点击「设为默认」，后续记账免选账户更快捷",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. 首项选项
                if (defaultExpenseAccountId == null) {
                    item {
                        val isNone = selectedAccountId == null || selectedAccountId == -1L
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isNone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isNone) 1.dp else 0.5.dp,
                                if (isNone) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccount(null) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.AccountBalanceWallet,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "不指定账户（未设置默认支出账户）",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isNone) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isNone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "仅记收支明细，不联动具体账户资产余额",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                if (isNone) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // 已配置默认支出账户：首项使用默认账户
                    item {
                        val isDefaultSelected = selectedAccountId == defaultExpenseAccountId || selectedAccountId == null
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isDefaultSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isDefaultSelected) 1.dp else 0.5.dp,
                                if (isDefaultSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccount(defaultExpenseAccountId) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "使用默认账户（${defaultAccount?.name ?: "已配置"}）",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isDefaultSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDefaultSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "闪电记账未指定时将优先归入此账户",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                if (isDefaultSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 独立选项：不关联任何账户
                    item {
                        val isExplicitNone = selectedAccountId == -1L
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isExplicitNone) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                if (isExplicitNone) 1.dp else 0.5.dp,
                                if (isExplicitNone) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onSelectAccount(-1L) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.MoneyOff,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "不关联任何账户",
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isExplicitNone) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isExplicitNone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = "仅记纯收支，不联动任何账户资产",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = MaterialTheme.colorScheme.outline,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                                if (isExplicitNone) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. 全部账户列表
                items(accounts, key = { it.id }) { account ->
                    val isDefault = account.id == defaultExpenseAccountId
                    val isCurrent = account.id == selectedAccountId
                    val accColor = try {
                        Color(android.graphics.Color.parseColor(account.colorHex.ifBlank { "#1B5E20" }))
                    } catch (e: Exception) {
                        MaterialTheme.colorScheme.primary
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) accColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            if (isCurrent) 1.dp else 0.5.dp,
                            if (isCurrent) accColor.copy(alpha = 0.5f) else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelectAccount(account.id) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(accColor.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AccountIconHelper.getIcon(account.icon),
                                    contentDescription = null,
                                    tint = accColor,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = account.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isCurrent) accColor else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    if (isDefault) {
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(10.dp)
                                                )
                                                Text(
                                                    text = "默认支出",
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    text = "当前余额：¥${MoneyUtils.centsToYuanString(account.balanceCents)}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = MaterialTheme.colorScheme.outline,
                                        fontSize = 11.sp
                                    )
                                )
                            }

                            // 快捷设为默认操作按钮
                            if (!isDefault) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onSetDefaultExpenseAccount(account.id) }
                                ) {
                                    Text(
                                        text = "设为默认",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.primary
                                        ),
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "已选中",
                                    tint = accColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
    canGoNextMonth: Boolean,
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
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "上月",
                                modifier = Modifier.size(17.dp)
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
                            enabled = canGoNextMonth,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "下月",
                                modifier = Modifier.size(17.dp)
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
                            text = "暂未设置月预算，点击快速设定",
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

    // 行内格式化只随输入变化重算：避免相邻行状态（如滑动开关）变化导致整行重组合时
    // 反复 new BigDecimal / SimpleDateFormat，拖慢长列表滚动。
    val amountText = remember(record.amount, isExpense) {
        val yuan = MoneyUtils.centsToYuanString(record.amount)
        if (isExpense) "-¥$yuan" else "+¥$yuan"
    }
    val subtitle = remember(record.remark, record.paymentMethod) {
        listOf(record.remark, record.paymentMethod)
            .filter { it.isNotBlank() }
            .joinToString(" · ")
    }
    val timeText = remember(record.recordTime) { DateTimeUtils.formatTime(record.recordTime) }

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

                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = amountText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = if (isExpense) MaterialTheme.colorScheme.error else primaryColor
                )

                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
