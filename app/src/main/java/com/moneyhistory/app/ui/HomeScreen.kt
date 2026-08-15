package com.moneyhistory.app.ui

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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moneyhistory.app.Categories
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
import com.moneyhistory.app.ui.theme.WarningOrange
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
    val successNonce by viewModel.successNonce.collectAsStateWithLifecycle()
    val summaryDismissed by viewModel.settings.summaryDismissedMonth
        .collectAsStateWithLifecycle()

    // 记账/编辑 BottomSheet：editingId 为编辑，duplicatingId 为「再记一笔」预填
    // （rememberSaveable + id 引用，进程重建不丢输入现场）
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var duplicatingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
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
    val savedText = stringResource(R.string.record_saved)

    // 下月按钮到达当前月后禁用
    val isAtCurrentMonth = remember(month) {
        val c = Calendar.getInstance()
        month.year * 12 + month.month >=
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) + 1
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            HomeHeader(
                month = month,
                onPrev = viewModel::prevMonth,
                onNext = viewModel::nextMonth,
                nextEnabled = !isAtCurrentMonth,
                expense = monthExpense,
                income = monthIncome,
                todayExpense = todayStats.first,
                todayCount = todayStats.second,
                streak = streak,
                onSearch = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                },
                onMenu = { menuOpen = true },
                menuOpen = menuOpen,
                onDismissMenu = { menuOpen = false },
                onOpenStats = onNavigateToStats
            )

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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
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

                item(key = "budget") {
                    BudgetCard(
                        expense = monthExpense,
                        budgetCents = budgetCents,
                        onSetBudget = { showBudgetDialog = true }
                    )
                }

                item(key = "goals") {
                    if (goals.isEmpty()) {
                        AppCard(
                            onClick = { showGoalSheet = true },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.home_goals_guide),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(goals, key = { it.id }) { goal ->
                                GoalCard(
                                    goal = goal,
                                    onClick = { onNavigateToGoal(goal.id) }
                                )
                            }
                            item(key = "add_goal") {
                                AddGoalCard(onClick = { showGoalSheet = true })
                            }
                        }
                    }
                }

                if (filteredTransactions.isEmpty()) {
                    item(key = "empty") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotBlank()) {
                                    stringResource(
                                        R.string.home_search_empty,
                                        searchQuery
                                    )
                                } else {
                                    stringResource(R.string.home_empty)
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
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
        }

        // 顶部菜单已并入页头（HomeHeader），此处仅保留 FAB
        // FAB：记一笔
        val interactionSource = remember { MutableInteractionSource() }
        val pressed by interactionSource.collectIsPressedAsState()
        val fabScale by animateFloatAsState(
            targetValue = if (pressed) 0.88f else 1f,
            label = "fabScale"
        )
        FloatingActionButton(
            onClick = {
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
            TransactionSheet(
                initial = editing,
                prefill = duplicating,
                expenseCategories = expenseCategories,
                incomeCategories = incomeCategories,
                onDismiss = { sheetOpen = false },
                onSave = { t ->
                    if (editing == null) viewModel.add(t) else viewModel.update(t)
                    viewModel.onTransactionSaved(t)
                    viewModel.postMessage(savedText, MessageVariant.SUCCESS)
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

        SuccessOverlay(trigger = successNonce)
    }
}

/** 品牌蓝渐变大页头：标题 + 月份切换 + 本月支出大数字 + 快速指标。 */
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
    todayCount: Int,
    streak: Int,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onOpenStats: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
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

        Column(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpenStats)
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(R.string.home_overview_expense),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f)
            )
            AnimatedContent(targetState = expense, label = "headerExpense") { value ->
                Text(
                    text = MoneyUtils.formatCents(value),
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            QuickStat(
                label = stringResource(R.string.home_header_income),
                value = MoneyUtils.formatCents(income),
                modifier = Modifier.weight(1f)
            )
            QuickStat(
                label = stringResource(R.string.home_header_today),
                value = MoneyUtils.formatCents(todayExpense) +
                    if (todayCount > 0) " · $todayCount" else "",
                modifier = Modifier.weight(1f)
            )
            QuickStat(
                label = stringResource(R.string.home_header_streak),
                value = stringResource(R.string.home_header_streak_short, streak),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 渐变大页头上的小指标列。 */
@Composable
private fun QuickStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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

    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
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

/** 月度预算卡：进度条 + 使用比例；未设置时提供入口。 */
@Composable
private fun BudgetCard(
    expense: Long,
    budgetCents: Long,
    onSetBudget: () -> Unit
) {
    AppCard(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.budget_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (budgetCents > 0) {
                    Text(
                        text = MoneyUtils.formatCents(budgetCents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (budgetCents > 0) {
                Spacer(Modifier.height(12.dp))
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
                    color = progressColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                )
                Spacer(Modifier.height(6.dp))
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
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onSetBudget) {
                    Text(stringResource(R.string.home_budget_set))
                }
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
            .padding(horizontal = 20.dp, vertical = 8.dp),
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
                        text = Categories.nameOf(t.category),
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
