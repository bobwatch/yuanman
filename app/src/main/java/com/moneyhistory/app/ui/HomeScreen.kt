package com.moneyhistory.app.ui

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
import com.moneyhistory.app.MainViewModel
import com.moneyhistory.app.MoneyUtils
import com.moneyhistory.app.R
import com.moneyhistory.app.Transaction
import com.moneyhistory.app.YearMonth
import com.moneyhistory.app.dayKeyOf
import com.moneyhistory.app.ofMonth
import com.moneyhistory.app.streakOf
import com.moneyhistory.app.ui.theme.ExpenseRed
import com.moneyhistory.app.ui.theme.IncomeGreen
import com.moneyhistory.app.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    onAddRequestHandled: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val month by viewModel.month.collectAsStateWithLifecycle()
    val budgetCents by viewModel.settings.budgetCents.collectAsStateWithLifecycle()
    val customCategories by viewModel.customCategories.collectAsStateWithLifecycle()
    val recentExpense by viewModel.settings.recentExpenseCategories
        .collectAsStateWithLifecycle()
    val recentIncome by viewModel.settings.recentIncomeCategories
        .collectAsStateWithLifecycle()
    val goals by viewModel.goals.collectAsStateWithLifecycle()
    val successNonce by viewModel.successNonce.collectAsStateWithLifecycle()
    val summaryDismissed by viewModel.settings.summaryDismissedMonth
        .collectAsStateWithLifecycle()

    // 记账/编辑 BottomSheet：editing 为编辑，duplicating 为「再记一笔」预填
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Transaction?>(null) }
    var duplicating by remember { mutableStateOf<Transaction?>(null) }
    var showGoalSheet by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showBudgetDialog by remember { mutableStateOf(false) }

    // 桌面快捷方式 / Widget「记一笔」直达
    LaunchedEffect(openAddRequest) {
        if (openAddRequest) {
            editing = null
            duplicating = null
            sheetOpen = true
            onAddRequestHandled()
        }
    }

    val monthTransactions = remember(transactions, month) { transactions.ofMonth(month) }
    val filteredTransactions = remember(monthTransactions, searchQuery) {
        if (searchQuery.isBlank()) {
            monthTransactions
        } else {
            monthTransactions.filter {
                it.note.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    var monthExpense = 0L
    var monthIncome = 0L
    monthTransactions.forEach {
        if (it.type == Transaction.Type.EXPENSE) monthExpense += it.amountCents
        else monthIncome += it.amountCents
    }
    // 今日支出
    val todayStats = remember(transactions) {
        val key = dayKeyOf(System.currentTimeMillis())
        var sum = 0L
        var n = 0
        transactions.forEach {
            if (it.type == Transaction.Type.EXPENSE && dayKeyOf(it.timestamp) == key) {
                sum += it.amountCents
                n++
            }
        }
        sum to n
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

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    ),
                    actions = {
                        IconButton(onClick = {
                            searchActive = !searchActive
                            if (!searchActive) searchQuery = ""
                        }) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.home_search)
                            )
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.home_menu)
                            )
                        }
                        DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_menu_stats)) },
                                onClick = {
                                    menuOpen = false
                                    onNavigateToStats()
                                }
                            )
                        }
                    }
                )
            },
            floatingActionButton = {
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val fabScale by animateFloatAsState(
                    targetValue = if (pressed) 0.88f else 1f,
                    label = "fabScale"
                )
                FloatingActionButton(
                    onClick = {
                        editing = null
                        duplicating = null
                        sheetOpen = true
                    },
                    modifier = Modifier.scale(fabScale),
                    interactionSource = interactionSource,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.home_add_desc)
                    )
                }
            }
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (searchActive) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text(stringResource(R.string.home_search_hint)) },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription =
                                            stringResource(R.string.home_search_clear)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // 上月小结卡
                if (summaryDismissed != summaryKey && lastMonthTransactions.isNotEmpty()) {
                    MonthSummaryCard(
                        list = lastMonthTransactions,
                        onClose = { viewModel.settings.dismissSummary(summaryKey) }
                    )
                }

                MonthSelector(
                    month = month,
                    onPrev = viewModel::prevMonth,
                    onNext = viewModel::nextMonth
                )

                OverviewCard(
                    expense = monthExpense,
                    income = monthIncome,
                    count = monthTransactions.size,
                    todayExpense = todayStats.first,
                    todayCount = todayStats.second,
                    budgetCents = budgetCents,
                    streak = streak,
                    onSetBudget = { showBudgetDialog = true }
                )

                // 攒钱目标区
                if (goals.isEmpty()) {
                    Text(
                        text = stringResource(R.string.home_goals_guide),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showGoalSheet = true }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(goals, key = { it.id }) { goal ->
                            GoalCard(goal = goal, onClick = { onNavigateToGoal(goal.id) })
                        }
                        item(key = "add_goal") {
                            AddGoalCard(onClick = { showGoalSheet = true })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))

                if (filteredTransactions.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) {
                                stringResource(R.string.home_search_empty, searchQuery)
                            } else {
                                stringResource(R.string.home_empty)
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
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
                                            scope.launch {
                                                val result = snackbarHostState
                                                    .showSnackbar(
                                                        message = deletedText,
                                                        actionLabel = undoText
                                                    )
                                                if (result == SnackbarResult.ActionPerformed) {
                                                    viewModel.restore(t)
                                                }
                                            }
                                        },
                                        onClick = {
                                            editing = t
                                            duplicating = null
                                            sheetOpen = true
                                        },
                                        onDuplicate = {
                                            editing = null
                                            duplicating = t
                                            sheetOpen = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (sheetOpen) {
                TransactionSheet(
                    initial = editing,
                    prefill = duplicating,
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    onDismiss = { sheetOpen = false },
                    onSave = { t ->
                        if (editing == null) viewModel.add(t) else viewModel.update(t)
                        viewModel.onTransactionSaved(t)
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

            if (showBudgetDialog) {
                BudgetDialog(
                    currentCents = budgetCents,
                    onDismiss = { showBudgetDialog = false },
                    onSave = { viewModel.settings.setBudgetCents(it) }
                )
            }
        }

        SuccessOverlay(trigger = successNonce)
    }
}

/** 上月小结卡（可关闭）。 */
@Composable
private fun MonthSummaryCard(list: List<Transaction>, onClose: () -> Unit) {
    var expense = 0L
    var income = 0L
    var maxSingle: Transaction? = null
    list.forEach {
        if (it.type == Transaction.Type.EXPENSE) {
            expense += it.amountCents
            if (maxSingle == null || it.amountCents > maxSingle!!.amountCents) {
                maxSingle = it
            }
        } else {
            income += it.amountCents
        }
    }
    val topCategory = list
        .filter { it.type == Transaction.Type.EXPENSE }
        .groupBy { it.category }
        .maxByOrNull { it.value.size }
        ?.key

    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.summary_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.summary_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = stringResource(
                    R.string.summary_totals,
                    MoneyUtils.formatCents(expense),
                    MoneyUtils.formatCents(income)
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            maxSingle?.let {
                Text(
                    text = stringResource(
                        R.string.summary_max_single,
                        it.category,
                        MoneyUtils.formatCents(it.amountCents)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            topCategory?.let {
                Text(
                    text = stringResource(R.string.summary_top_category, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 本月概览卡：支出大数字主视觉 + 收入/笔数 + 今日支出 + 预算进度 + 连续天数。 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun OverviewCard(
    expense: Long,
    income: Long,
    count: Int,
    todayExpense: Long,
    todayCount: Int,
    budgetCents: Long,
    streak: Int,
    onSetBudget: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.home_overview_expense),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            AnimatedContent(targetState = expense, label = "expense") { value ->
                Text(
                    text = MoneyUtils.formatCents(value),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.home_overview_income_count,
                    MoneyUtils.formatCents(income),
                    count
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(
                    R.string.home_today,
                    MoneyUtils.formatCents(todayExpense),
                    todayCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))
            if (budgetCents > 0) {
                val fraction = (expense.toFloat() / budgetCents).coerceIn(0f, 1f)
                val overBudget = expense > budgetCents
                val progressColor = when {
                    overBudget -> ExpenseRed
                    fraction > 0.8f -> WarningOrange
                    else -> MaterialTheme.colorScheme.primary
                }
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.home_budget_used,
                        MoneyUtils.formatCents(budgetCents),
                        expense * 100 / budgetCents,
                        if (overBudget) {
                            stringResource(R.string.home_budget_over)
                        } else {
                            ""
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overBudget) {
                        ExpenseRed
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            } else {
                TextButton(onClick = onSetBudget) {
                    Text(stringResource(R.string.home_budget_set))
                }
            }

            if (streak > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.home_streak, streak),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 日期分组头：日期 + 星期 + 当日小计。 */
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.home_day_header,
                month,
                day,
                weekdayName(list.first().timestamp, weekdays)
            ),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = subtotal,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 流水项：左滑删除；点击编辑；长按弹出「再记一笔 / 删除」菜单。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissItem(
    t: Transaction,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            onDelete()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
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
                onDelete = onDelete
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
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.combinedClickable(
                onClick = onClick,
                onLongClick = { menuOpen = true }
            )
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 分类 emoji 圆形浅底色块
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = Categories.emojiOf(t.category))
                }
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = Categories.nameOf(t.category),
                        style = MaterialTheme.typography.bodyLarge
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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = (if (isExpense) "-" else "+") +
                            MoneyUtils.formatCents(t.amountCents),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpense) ExpenseRed else IncomeGreen
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
