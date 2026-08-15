package com.moneyhistory.app.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxDefaults
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.Goal
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MessageVariant
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.YearMonth
import com.moneyhistory.app.dayKeyOf
import com.moneyhistory.app.ofMonth
import com.moneyhistory.app.streakOf
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import com.moneyhistory.app.ui.theme.LocalDarkTheme
import com.moneyhistory.app.ui.theme.WarningOrange
import com.moneyhistory.app.ui.theme.YuanmanBlueDeep
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalAnimationApi::class
)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToStats: () -> Unit,
    onNavigateToGoal: (String) -> Unit,
    openAddRequest: Boolean,
    onAddRequestHandled: () -> Unit,
    toastHostState: ToastHostState
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val budgetCents by viewModel.settings.budgetCents.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val recentExpense by viewModel.settings.recentExpenseCategories
        .collectAsStateWithLifecycle()
    val recentIncome by viewModel.settings.recentIncomeCategories
        .collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val summaryDismissed by viewModel.settings.summaryDismissedMonth
        .collectAsStateWithLifecycle()

    // 记账/编辑 BottomSheet：editingId 为编辑，duplicatingId 为「再记一笔」预填
    // （rememberSaveable + id 引用，进程重建不丢输入现场）
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicatingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    // 多个目标时点目标行弹出的列表选择层
    var showGoalList by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // 搜索类型筛选：null = 全部，不选时不影响关键词搜索
    var searchType by rememberSaveable { mutableStateOf<Transaction.Type?>(null) }
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }

    val editing = transactions.firstOrNull { it.id == editingId }
    val duplicating = transactions.firstOrNull { it.id == duplicatingId }

    // 搜索开启时，返回键先关搜索
    BackHandler(enabled = searchActive) {
        searchActive = false
        searchQuery = ""
    }

    // 桌面快捷方式 / Widget「记一笔」直达
    LaunchedEffect(openAddRequest) {
        if (openAddRequest) {
            editingId = null
            duplicatingId = null
            sheetOpen = true
            onAddRequestHandled()
        }
    }

    val monthTransactions = remember(transactions, month) { transactions.ofMonth(month) }
    // 分类在界面显示本地化名称，但存储的是原名——预计算显示名，搜索时两者都匹配
    val monthCategories = remember(monthTransactions) {
        monthTransactions.map { it.category }.distinct()
    }
    val categoryDisplayNames = monthCategories.associateWith { Categories.displayName(it) }
    val filteredTransactions = remember(
        monthTransactions, searchQuery, searchType, categoryDisplayNames
    ) {
        monthTransactions.filter { t ->
            if (searchType != null && t.type != searchType) return@filter false
            if (searchQuery.isBlank()) return@filter true
            val q = searchQuery.trim()
            // 纯数字关键词按金额精确匹配（如输 35 找到 ¥35.00），否则匹配备注/分类
            val amountMatch = MoneyUtils.parseToCents(q)?.let { it == t.amountCents }
                ?: false
            amountMatch ||
                t.note.contains(q, ignoreCase = true) ||
                t.category.contains(q, ignoreCase = true) ||
                categoryDisplayNames[t.category].orEmpty().contains(q, ignoreCase = true)
        }
    }
    var monthExpense = 0L
    var monthIncome = 0L
    monthTransactions.forEach {
        if (it.type == Transaction.Type.EXPENSE) monthExpense += it.amountCents
        else monthIncome += it.amountCents
    }
    // 今日支出金额（页头「今日」指标用，取代笔数：钱的事看钱更直观）
    val todayExpense = remember(transactions) {
        val key = dayKeyOf(System.currentTimeMillis())
        var sum = 0L
        transactions.forEach {
            if (it.type == Transaction.Type.EXPENSE && dayKeyOf(it.timestamp) == key) {
                sum += it.amountCents
            }
        }
        sum
    }
    val streak = remember(transactions) { streakOf(transactions) }
    val grouped = remember(filteredTransactions) {
        filteredTransactions
            .groupBy { dayKeyOf(it.timestamp) }
            .toList()
            .sortedByDescending { it.first }
    }
    // 分类：最近使用的排前面
    val expenseCategories = remember(customCategories, recentExpense) {
        val all = Categories.expense + customCategories
        recentExpense.filter { it in all } + all.filter { it !in recentExpense }
    }
    val incomeCategories = remember(customCategories, recentIncome) {
        val all = Categories.income + customCategories
        recentIncome.filter { it in all } + all.filter { it !in recentIncome }
    }

    // 上月小结（每月 1 日后首次打开且上月有流水时显示，可关闭）
    val lastMonth = remember {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
    val summaryKey = "%d-%02d".format(lastMonth.year, lastMonth.month)
    val lastMonthTransactions = remember(transactions) { transactions.ofMonth(lastMonth) }

    val deletedText = stringResource(R.string.home_deleted)
    val undoText = stringResource(R.string.home_undo)
    val savedText = stringResource(R.string.record_saved)

    // 下月按钮到达当前月后禁用
    val isAtCurrentMonth = remember(month) {
        val c = Calendar.getInstance()
        month.year * 12 + month.month >=
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) + 1
    }

    // 整页单列表：蓝色页头随内容一起滚动。页头滚出状态栏区域后，
    // 状态栏露出页面背景色，浅色主题下把图标切深色保证可读（深色主题图标恒白）。
    val listState = rememberLazyListState()
    val darkTheme = LocalDarkTheme.current
    val scrolledPastHeader = remember {
        derivedStateOf {
            val first = listState.layoutInfo.visibleItemsInfo.firstOrNull()
            first != null && (first.index > 0 || first.offset < 0)
        }
    }
    val lightStatusIcons = !darkTheme && scrolledPastHeader.value
    val view = LocalView.current
    val window = (view.context as Activity).window
    // 搜索框自动聚焦（打开即弹键盘，关闭即收起）
    val searchFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(lightStatusIcons) {
        WindowCompat.getInsetsController(window, view)
            .isAppearanceLightStatusBars = lightStatusIcons
    }
    // 离开首页（切 Tab / 进子页）恢复白图标：其他页页头都是固定蓝色渐变
    DisposableEffect(Unit) {
        onDispose {
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }

    // 打开搜索时回到顶部，让搜索框出现在页头下方
    LaunchedEffect(searchActive) {
        if (searchActive) {
            listState.animateScrollToItem(0)
            searchFocus.requestFocus()
        } else {
            focusManager.clearFocus()
        }
    }

    // 重复点击底部「记一笔」Tab：整页滚回顶部（微信/支付宝式）
    LaunchedEffect(Unit) {
        viewModel.tabReclick.collect { route ->
            if (route == "home") listState.animateScrollToItem(0)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item(key = "header") {
                HomeHeader(
                    month = month,
                    onPrev = viewModel::prevMonth,
                    onNext = viewModel::nextMonth,
                    nextEnabled = !isAtCurrentMonth,
                    expense = monthExpense,
                    income = monthIncome,
                    todayExpense = todayExpense,
                    streak = streak,
                    onSearch = {
                        searchActive = !searchActive
                        if (!searchActive) searchQuery = ""
                    },
                    onMenu = { menuOpen = true },
                    menuOpen = menuOpen,
                    onDismissMenu = { menuOpen = false },
                    onOpenStats = onNavigateToStats,
                    searchActive = searchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchType = searchType,
                    onSearchTypeChange = { searchType = it },
                    searchFocus = searchFocus
                )
            }

            item(key = "budget_goals") {
                BudgetGoalCard(
                    expense = monthExpense,
                    budgetCents = budgetCents,
                    goals = goals,
                    onSetBudget = { showBudgetDialog = true },
                    onGoalClick = {
                        // 无目标时引导新建；单个目标直达详情；多个目标先弹列表选择
                        when {
                            goals.isEmpty() -> showGoalSheet = true
                            goals.size == 1 -> onNavigateToGoal(goals.first().id)
                            else -> showGoalList = true
                        }
                    }
                )
            }

            if (summaryDismissed != summaryKey &&
                lastMonthTransactions.isNotEmpty()
            ) {
                item(key = "summary") {
                    MonthSummaryCard(
                        list = lastMonthTransactions,
                        onClose = {
                            viewModel.settings.dismissSummary(summaryKey)
                        }
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "empty") {
                    if (searchActive) {
                        // 搜索无结果：轻量提示
                        EmptyState(
                            emoji = "🔍",
                            title = stringResource(R.string.home_search_empty_title),
                            subtitle = stringResource(
                                R.string.home_search_empty_sub,
                                searchQuery
                            )
                        )
                    } else if (transactions.isEmpty()) {
                        // 全新用户：欢迎 + 记第一笔引导
                        EmptyState(
                            emoji = "👋",
                            title = stringResource(R.string.home_welcome_title),
                            subtitle = stringResource(R.string.home_welcome_sub),
                            actionLabel = stringResource(R.string.home_welcome_action),
                            onAction = {
                                editingId = null
                                duplicatingId = null
                                sheetOpen = true
                            }
                        )
                    } else {
                        // 老用户但本月无记录：轻声提醒 + 一键开记
                        EmptyState(
                            emoji = "🌱",
                            title = stringResource(R.string.home_empty_title),
                            subtitle = stringResource(R.string.home_empty_sub),
                            actionLabel = stringResource(R.string.home_empty_action),
                            onAction = { sheetOpen = true }
                        )
                    }
                }
            } else {
                grouped.forEach { (dayKey, dayList) ->
                    item(key = "header_$dayKey") {
                        DayHeader(dayKey = dayKey, list = dayList)
                    }
                    items(dayList, key = { it.id }) { t ->
                        Box(Modifier.animateItemPlacement()) {
                            SwipeToDismissItem(
                                t = t,
                                onDelete = {
                                    viewModel.delete(t.id)
                                    toastHostState.show(
                                        message = deletedText,
                                        variant = MessageVariant.INFO,
                                        actionLabel = undoText,
                                        onAction = { viewModel.restore(t) }
                                    )
                                },
                                onClick = {
                                    editingId = t.id
                                    duplicatingId = null
                                    sheetOpen = true
                                },
                                onDuplicate = {
                                    editingId = null
                                    duplicatingId = t.id
                                    sheetOpen = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // FAB：记一笔
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (pressed) 0.88f else 1f,
            label = "fabScale"
        )
        FloatingActionButton(
            onClick = {
                // 高频入口加轻触感：按下的「咯噔」让开记一笔更跟手
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                editingId = null
                duplicatingId = null
                sheetOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .scale(fabScale),
            interactionSource = interactionSource,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.home_add_desc)
            )
        }

        if (sheetOpen) {
            // 最近使用的分类（时间倒序去重前 6）：记账页分类宫格置顶
            val recentCategories = remember(transactions) {
                transactions.sortedByDescending { it.timestamp }
                    .map { it.category }
                    .distinct()
                    .take(6)
            }
            TransactionSheet(
                initial = editing,
                prefill = duplicating,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                recentCategories = recentCategories,
                onDismiss = { sheetOpen = false },
                onSave = { t ->
                    if (editing == null) viewModel.add(t) else viewModel.update(t)
                    viewModel.onTransactionSaved(t)
                    viewModel.postMessage(savedText, MessageVariant.SUCCESS)
                    sheetOpen = false
                },
                onDelete = {
                    val target = editing
                    if (target != null) {
                        viewModel.delete(target.id)
                        toastHostState.show(
                            message = deletedText,
                            variant = MessageVariant.INFO,
                            actionLabel = undoText,
                            onAction = { viewModel.restore(target) }
                        )
                    }
                    sheetOpen = false
                },
                onAddRecurring = { viewModel.addRecurring(it) }
            )
        }

        if (showGoalSheet) {
            GoalCreateSheet(
                onDismiss = { showGoalSheet = false },
                onCreate = { viewModel.addGoal(it) }
            )
        }

        if (showGoalList) {
            GoalListSheet(
                goals = goals,
                onGoalClick = { id ->
                    showGoalList = false
                    onNavigateToGoal(id)
                },
                onCreateClick = {
                    showGoalList = false
                    showGoalSheet = true
                },
                onDismiss = { showGoalList = false }
            )
        }

        if (showBudgetDialog) {
            BudgetDialog(
                currentCents = budgetCents,
                onDismiss = { showBudgetDialog = false },
                onSave = { viewModel.settings.setBudgetCents(it) }
            )
        }
    }
}

/**
 * 品牌蓝渐变大页头（随列表滚动）：标题行 + 月份切换 + 本月支出大数字 +
 * 三个宽松小指标（收入 / 今日 / 连续）+ 激活时的搜索区。
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun HomeHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    nextEnabled: Boolean,
    expense: Long,
    income: Long,
    todayExpense: Long,
    streak: Int,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onOpenStats: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    searchType: Transaction.Type?,
    onSearchTypeChange: (Transaction.Type?) -> Unit,
    searchFocus: FocusRequester
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(brandHeaderBrush())
            .statusBarsPadding()
    ) {
        Row(
            Modifier.padding(start = 20.dp, end = 6.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onSearch) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = stringResource(R.string.home_search),
                    tint = Color.White
                )
            }
            Box {
                IconButton(onClick = onMenu) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.home_menu),
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = onDismissMenu
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.home_menu_stats)) },
                        onClick = {
                            onDismissMenu()
                            onOpenStats()
                        }
                    )
                }
            }
        }

        MonthSelector(
            month = month,
            onPrev = onPrev,
            onNext = onNext,
            nextEnabled = nextEnabled,
            contentColor = Color.White
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_overview_expense),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(2.dp))
            // 本月支出大数字：单独成行，突出主数字
            AnimatedContent(targetState = expense, label = "headerExpense") { value ->
                Text(
                    text = MoneyUtils.formatCents(value),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(18.dp))
            // 三个小指标：等宽三列，间距宽松，不再挤在数字右侧
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderStat(
                    label = stringResource(R.string.home_header_income),
                    value = MoneyUtils.formatCents(income),
                    modifier = Modifier.weight(1f)
                )
                HeaderStat(
                    label = stringResource(R.string.home_header_today),
                    value = MoneyUtils.formatCents(todayExpense),
                    modifier = Modifier.weight(1f)
                )
                HeaderStat(
                    label = stringResource(R.string.home_header_streak),
                    value = if (streak > 0) {
                        stringResource(R.string.home_header_streak_short, streak)
                    } else {
                        stringResource(R.string.home_header_today_none)
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (searchActive) {
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                singleLine = true,
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription =
                                    stringResource(R.string.home_search_clear)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .focusRequester(searchFocus)
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchTypeChip(
                    selected = searchType == null,
                    label = stringResource(R.string.search_filter_all),
                    onClick = { onSearchTypeChange(null) }
                )
                SearchTypeChip(
                    selected = searchType == Transaction.Type.EXPENSE,
                    label = stringResource(R.string.search_filter_expense),
                    onClick = { onSearchTypeChange(Transaction.Type.EXPENSE) }
                )
                SearchTypeChip(
                    selected = searchType == Transaction.Type.INCOME,
                    label = stringResource(R.string.search_filter_income),
                    onClick = { onSearchTypeChange(Transaction.Type.INCOME) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

/** 页头上的筛选胶囊：蓝底上用半透明白 / 纯白底对比。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTypeChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.14f),
            labelColor = Color.White,
            selectedContainerColor = Color.White,
            selectedLabelColor = YuanmanBlueDeep
        )
    )
}

/** 页头小指标列：白字 label 在上、白字加粗 value 在下。 */
@Composable
private fun HeaderStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 上月小结卡（可关闭，压缩为一行）。 */
@Composable
private fun MonthSummaryCard(list: List<Transaction>, onClose: () -> Unit) {
    var expense = 0L
    var income = 0L
    list.forEach {
        if (it.type == Transaction.Type.EXPENSE) {
            expense += it.amountCents
        } else {
            income += it.amountCents
        }
    }

    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.summary_totals,
                        MoneyUtils.formatCents(expense),
                        MoneyUtils.formatCents(income)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.summary_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 预算 + 攒钱目标合并的紧凑双行卡：单卡两行各自可点，压缩首页占位。 */
@Composable
private fun BudgetGoalCard(
    expense: Long,
    budgetCents: Long,
    goals: List<Goal>,
    onSetBudget: () -> Unit,
    onGoalClick: () -> Unit
) {
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressScale()
                    .clickable(onClick = onSetBudget)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "💰", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.budget_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (budgetCents > 0) {
                    Spacer(Modifier.width(12.dp))
                    val fraction = (expense.toFloat() / budgetCents).coerceIn(0f, 1f)
                    val overBudget = expense > budgetCents
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                        color = when {
                            overBudget -> ExpenseRed
                            fraction > 0.8f -> WarningOrange
                            else -> MaterialTheme.colorScheme.primary
                        },
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = Math.round(expense * 100f / budgetCents).toString() + "%" +
                            if (overBudget) {
                                stringResource(R.string.home_budget_over)
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (overBudget) {
                            ExpenseRed
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = stringResource(R.string.home_budget_set),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .pressScale()
                    .clickable(onClick = onGoalClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "🎯", fontSize = 16.sp)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.home_section_goal),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(12.dp))
                val topGoal = goals.maxByOrNull { it.progress }
                Text(
                    text = if (topGoal == null) {
                        stringResource(R.string.home_goal_set)
                    } else {
                        stringResource(
                            R.string.home_goal_progress,
                            topGoal.name,
                            (topGoal.progress * 100).roundToInt().coerceAtMost(100)
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = if (topGoal == null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

/** 日期分组头：日期 + 星期 + 当日小计（今天/昨天用相对文案，快速定位）。 */
@Composable
private fun DayHeader(dayKey: Int, list: List<Transaction>) {
    val weekdays = listOf(
        stringResource(R.string.weekday_sun),
        stringResource(R.string.weekday_mon),
        stringResource(R.string.weekday_tue),
        stringResource(R.string.weekday_wed),
        stringResource(R.string.weekday_thu),
        stringResource(R.string.weekday_fri),
        stringResource(R.string.weekday_sat)
    )
    val month = dayKey / 100 % 100
    val day = dayKey % 100
    // 今天/昨天显示相对文案，其余日期显示「周几, 月/日」
    val cal = Calendar.getInstance()
    val todayKey = dayKeyOf(cal.timeInMillis)
    cal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterdayKey = dayKeyOf(cal.timeInMillis)
    val dayLabel = when (dayKey) {
        todayKey -> stringResource(R.string.home_day_today)
        yesterdayKey -> stringResource(R.string.home_day_yesterday)
        else -> stringResource(
            R.string.home_day_header,
            month,
            day,
            weekdayName(list.first().timestamp, weekdays)
        )
    }
    var expense = 0L
    var income = 0L
    list.forEach {
        if (it.type == Transaction.Type.EXPENSE) expense += it.amountCents
        else income += it.amountCents
    }
    val subtotal = buildString {
        if (expense > 0) {
            append(
                stringResource(
                    R.string.home_day_sub_expense,
                    MoneyUtils.formatCents(expense)
                )
            )
        }
        if (expense > 0 && income > 0) append(" · ")
        if (income > 0) {
            append(
                stringResource(
                    R.string.home_day_sub_income,
                    MoneyUtils.formatCents(income)
                )
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = dayLabel,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Text(
            text = subtotal,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/**
 * 流水项：左滑（或长按菜单）删除需二次确认；点击编辑。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    t: Transaction,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: () -> Unit
) {
    // 不用 rememberSwipeToDismissBoxState（它走 rememberSaveable）：撤销删除后
    // 同 id 的条目重新进列表时，saveable 会把「已滑出」状态一并恢复——条目以
    // 滑出形态回归（不可见）并再次弹出删除确认框，看起来像撤销无效。
    // 改成随条目组合存在的普通状态：条目被移除即销毁，恢复的条目总是初始状态。
    val density = LocalDensity.current
    val defaultThreshold = SwipeToDismissBoxDefaults.positionalThreshold
    val dismissState = remember(t.id) {
        SwipeToDismissBoxState(
            initialValue = SwipeToDismissBoxValue.Settled,
            density = density,
            confirmValueChange = { true },
            positionalThreshold = defaultThreshold
        )
    }
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }

    // 左滑到位 → 弹确认框（先不删，等用户确认）
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            confirmDelete = true
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = {
                confirmDelete = false
                scope.launch { dismissState.reset() }
            },
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.home_delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.home_delete_confirm_msg,
                        t.category,
                        MoneyUtils.formatCents(t.amountCents)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { dismissState.reset() }
                }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(ExpenseRed)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = Color.White
                )
            }
        },
        content = {
            TransactionRow(
                t = t,
                onClick = onClick,
                onDuplicate = onDuplicate,
                onDelete = { confirmDelete = true }
            )
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransactionRow(
    t: Transaction,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val isExpense = t.type == Transaction.Type.EXPENSE
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .pressScale()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { menuOpen = true }
                )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类 emoji 圆形浅底色块
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (isExpense) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                IncomeGreen.copy(alpha = 0.14f)
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = Categories.emojiOf(t.category), fontSize = 20.sp)
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = Categories.displayName(t.category),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (t.note.isNotEmpty()) {
                        Text(
                            text = t.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Column(
                    Modifier.weight(1f, fill = false),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = (if (isExpense) "-" else "+") +
                            MoneyUtils.formatCents(t.amountCents),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) ExpenseRed else IncomeGreen,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatTime(t.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_item_duplicate)) },
                onClick = {
                    menuOpen = false
                    onDuplicate()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_item_delete)) },
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

private fun weekdayName(timestamp: Long, weekdays: List<String>): String {
    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
    return weekdays[cal.get(Calendar.DAY_OF_WEEK) - 1]
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
