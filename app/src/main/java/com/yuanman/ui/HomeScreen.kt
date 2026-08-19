package com.yuanman.ui

import android.app.Activity
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.Categories
import com.yuanman.DateUtils
import com.yuanman.Goal
import com.yuanman.MainViewModel
import com.yuanman.MessageVariant
import com.yuanman.MoneyUtils
import com.yuanman.R
import com.yuanman.Transaction
import com.yuanman.YearMonth
import com.yuanman.dayKeyOf
import com.yuanman.ofMonth
import com.yuanman.streakOf
import com.yuanman.ui.theme.ExpenseRed
import com.yuanman.ui.theme.ExpenseRedText
import com.yuanman.ui.theme.IncomeGreen
import com.yuanman.ui.theme.LocalDarkTheme
import com.yuanman.ui.theme.WarningOrangeText
import com.yuanman.ui.theme.YuanmanBlueDeep
import com.yuanman.ui.theme.expenseAmountColor
import com.yuanman.ui.theme.incomeAmountColor
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class
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
    val homeFilter by viewModel.homeFilter.collectAsStateWithLifecycle()
    val budgetCents by viewModel.settings.budgetCents.collectAsStateWithLifecycle()
    val expenseCats by viewModel.expenseCategories.collectAsStateWithLifecycle()
    val incomeCats by viewModel.incomeCategories.collectAsStateWithLifecycle()
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
    // 打开序号：每次打开面板 +1，作废上一次的 rememberSaveable 输入现场——
    // 否则保存后再次打开，Dialog 会从 SaveableRegistry 恢复上次内容，
    // 看起来像「保存了还停在编辑态」
    var sheetEpoch by rememberSaveable { mutableIntStateOf(0) }

    /** 打开记账面板：新开一笔（默认）/ 编辑一条 / 再记一笔预填。 */
    fun openSheet(editId: String? = null, dupId: String? = null) {
        editingId = editId
        duplicatingId = dupId
        sheetEpoch++
        sheetOpen = true
    }
    // 「周期账单」空态直达：打开记账面板并预勾选周期开关；用完即清
    var recurringPrefill by rememberSaveable { mutableStateOf(false) }
    var showGoalSheet by rememberSaveable { mutableStateOf(false) }
    // 多个目标时点目标行弹出的列表选择层
    var showGoalList by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    // 流水行左滑展开的删除按钮：同一时间只展开一行（iOS 习惯）；条目移出列表即作废
    var revealedId by remember { mutableStateOf<String?>(null) }
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // 搜索类型筛选：null = 全部，不选时不影响关键词搜索
    var searchType by rememberSaveable { mutableStateOf<Transaction.Type?>(null) }
    var showBudgetDialog by rememberSaveable { mutableStateOf(false) }
    // 时间范围筛选：null = 按月份浏览；激活时列表按范围过滤（页头统计仍为当前月）
    var timeRange by rememberSaveable { mutableStateOf<TimeRangeOption?>(null) }
    var customRange by rememberSaveable { mutableStateOf<LongArray?>(null) }
    var showTimeRangeDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomRangeDialog by rememberSaveable { mutableStateOf(false) }
    // 自定义起止日期（对话框内状态，打开时按现有范围或今天初始化）
    var customStart by remember { mutableStateOf(0L) }
    var customEnd by remember { mutableStateOf(0L) }
    // 打开自定义对话框时预填：沿用上次范围，首次则选今天
    LaunchedEffect(showCustomRangeDialog) {
        if (showCustomRangeDialog) {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            customStart = customRange?.getOrNull(0) ?: cal.timeInMillis
            customEnd = customRange?.getOrNull(1)
                ?: (cal.timeInMillis + 24 * 3600_000L - 1)
        }
    }
    // 清除时间筛选：回到「按月份浏览」的当前月
    fun clearTimeRange() {
        timeRange = null
        customRange = null
        viewModel.goToCurrentMonth()
    }

    // 跨零点/跨月自动刷新：凌晨记账时「今日支出」「今天/昨天」标签、上月小结
    // 都要随真实日期推进。只监听日期变化，值不变时不自增，避免无谓重组
    var nowTick by remember { mutableIntStateOf(0) }
    var lastTickDay by remember { mutableStateOf(DateUtils.today()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            val day = DateUtils.today()
            if (day != lastTickDay) {
                lastTickDay = day
                nowTick++
            }
        }
    }

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
            openSheet()
            onAddRequestHandled()
        }
    }

    val monthTransactions = remember(transactions, month) { transactions.ofMonth(month) }
    // 时间范围筛选后的记录集（null = 未激活）：激活时列表基换为范围内记录，
    // 页头「本月支出」统计保持当前月不受影响
    val rangeTransactions = remember(transactions, timeRange, customRange, nowTick) {
        val range = timeRange
        if (range == null) {
            null
        } else {
            rangeMillisOf(range, customRange)?.let { (s, e) ->
                transactions.filter { it.timestamp in s..e }
            }
        }
    }
    val baseTransactions = rangeTransactions ?: monthTransactions
    // 分类在界面显示本地化名称，但存储的是原名——预计算显示名，搜索时两者都匹配
    val monthCategories = remember(baseTransactions) {
        baseTransactions.map { it.category }.distinct()
    }
    val categoryDisplayNames = monthCategories.associateWith { Categories.displayName(it) }
    val filteredTransactions = remember(
        baseTransactions, searchQuery, searchType, categoryDisplayNames, homeFilter
    ) {
        baseTransactions.filter { t ->
            if (homeFilter != null && t.category != homeFilter) return@filter false
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
    // 分类筛选条上的笔数（按当前数据基统计，不随搜索词变化）
    val homeFilterCount = remember(baseTransactions, homeFilter) {
        if (homeFilter == null) 0
        else baseTransactions.count { it.category == homeFilter }
    }
    var monthExpense = 0L
    var monthIncome = 0L
    monthTransactions.forEach {
        if (it.type == Transaction.Type.EXPENSE) monthExpense += it.amountCents
        else monthIncome += it.amountCents
    }
    // 今日支出金额（页头「今日」指标用，取代笔数：钱的事看钱更直观）
    val todayExpense = remember(transactions, nowTick) {
        val key = dayKeyOf(System.currentTimeMillis())
        var sum = 0L
        transactions.forEach {
            if (it.type == Transaction.Type.EXPENSE && dayKeyOf(it.timestamp) == key) {
                sum += it.amountCents
            }
        }
        sum
    }
    val streak = remember(transactions, nowTick) { streakOf(transactions) }
    val grouped = remember(filteredTransactions, nowTick) {
        filteredTransactions
            .groupBy { dayKeyOf(it.timestamp) }
            .toList()
            .sortedByDescending { it.first }
    }
    // 分类：最近使用的排前面
    val expenseCategories = remember(expenseCats, recentExpense) {
        recentExpense.filter { it in expenseCats } + expenseCats.filter { it !in recentExpense }
    }
    val incomeCategories = remember(incomeCats, recentIncome) {
        recentIncome.filter { it in incomeCats } + incomeCats.filter { it !in recentIncome }
    }

    // 上月小结（每月 1 日后首次打开且上月有流水时显示，可关闭）
    val lastMonth = remember(nowTick) {
        val cal = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
        YearMonth(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
    }
    val summaryKey = "%d-%02d".format(lastMonth.year, lastMonth.month)
    val lastMonthTransactions = remember(transactions, nowTick) {
        transactions.ofMonth(lastMonth)
    }
    val showSummary = summaryDismissed != summaryKey && lastMonthTransactions.isNotEmpty()

    val deletedText = stringResource(R.string.home_deleted)
    val undoText = stringResource(R.string.home_undo)
    val savedText = stringResource(R.string.record_saved)
    val recordAgainText = stringResource(R.string.record_again)

    // 下月按钮到达当前月后禁用
    val isAtCurrentMonth = remember(month, nowTick) {
        val c = Calendar.getInstance()
        month.year * 12 + month.month >=
            c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH) + 1
    }
    // 翻看历史月份后，点月份标题一键回当前月（编译器对方法引用进 if-else 有
    // psi2ir 内部错误，统一走显式 lambda）
    val monthTitleClick: (() -> Unit)? =
        if (isAtCurrentMonth) null else ({ viewModel.goToCurrentMonth() })

    // 时间筛选激活条的文案：自定义显示起止日期（紧凑格式），其余显示选项名
    val activeRange = timeRange
    val timeFilterLabel: String? = when (activeRange) {
        null -> null
        TimeRangeOption.CUSTOM -> customRange?.let { range ->
            if (range[1] > 0) {
                val fmt = SimpleDateFormat("yyyy/M/d", Locale.getDefault())
                "${fmt.format(Date(range[0]))} ~ ${fmt.format(Date(range[1]))}"
            } else {
                null
            }
        }
        else -> stringResource(activeRange.resId)
    }

    // 页头时段问候语：跨时段后一分钟内更新（值不变不触发重组）
    var greetingHour by remember {
        mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            greetingHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        }
    }

    // 整页单列表：蓝色页头随内容一起滚动。页头滚出状态栏区域后，
    // 状态栏露出页面背景色，浅色主题下把图标切深色保证可读（深色主题图标恒白）
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
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
    // 状态栏全透明（见 Theme），这里只需切图标深浅：
    // 页头在顶 → 白图标；滚出页头露出背景色 → 浅色主题切深图标
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

    // 统计页等子页的「记一笔」直达：返回首页的同时打开记账面板
    LaunchedEffect(Unit) {
        viewModel.requestAddSheet.collect {
            openSheet()
        }
    }

    // 「周期账单」空态直达：打开面板并预勾选周期开关，少一步寻找
    LaunchedEffect(Unit) {
        viewModel.requestRecurringSheet.collect {
            openSheet()
            recurringPrefill = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        // 整页随月份滑动（与标题同一套过渡），翻月不再是「标题滑、列表跳」
        AnimatedContent(
            targetState = month,
            transitionSpec = { monthPageTransition { it.year * 12 + it.month } },
            modifier = Modifier.fillMaxSize(),
            label = "homeMonth"
        ) {
        LazyColumn(
            state = listState,
            // 底部导航栏悬浮在页面之上（见 MainActivity），列表底部预留其高度
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
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
                    budgetCents = budgetCents,
                    goals = goals,
                    // 预算只约束当前月：翻看历史月时按钮只显示计划名，不显示预算余量
                    budgetActive = isAtCurrentMonth,
                    onBudgetClick = { showBudgetDialog = true },
                    onGoalClick = {
                        // 无目标时引导新建；单个目标直达详情；多个目标先弹列表选择
                        when {
                            goals.isEmpty() -> showGoalSheet = true
                            goals.size == 1 -> onNavigateToGoal(goals.first().id)
                            else -> showGoalList = true
                        }
                    },
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
                    onSearchExit = {
                        searchActive = false
                        searchQuery = ""
                    },
                    searchType = searchType,
                    onSearchTypeChange = { searchType = it },
                    searchFocus = searchFocus,
                    greetingHour = greetingHour,
                    onMonthTitleClick = monthTitleClick,
                    onTodayClick = {
                        // 点「今日」：定位到今天的分组；今天还没记账则直接引导记一笔
                        if (searchActive) {
                            searchActive = false
                            searchQuery = ""
                        }
                        val todayKey = dayKeyOf(System.currentTimeMillis())
                        val todayGroup = grouped.indexOfFirst { it.first == todayKey }
                        if (todayGroup < 0) {
                            openSheet()
                        } else {
                            val targetIndex = 1 +
                                (if (showSummary) 1 else 0) +
                                (if (homeFilter != null) 1 else 0) +
                                grouped.take(todayGroup).sumOf { 1 + it.second.size }
                            scrollScope.launch {
                                listState.animateScrollToItem(targetIndex)
                            }
                        }
                    },
                    timeFilterLabel = timeFilterLabel,
                    onTimeFilterClick = { showTimeRangeDialog = true },
                    onTimeFilterClear = { clearTimeRange() }
                )
            }

            if (showSummary) {
                item(key = "summary") {
                    MonthSummaryCard(
                        list = lastMonthTransactions,
                        onClose = {
                            viewModel.settings.dismissSummary(summaryKey)
                        }
                    )
                }
            }

            // 统计页点分类直达：顶部常驻筛选条，一键清除回到全部流水
            homeFilter?.let { filter ->
                item(key = "filter_$filter") {
                    CategoryFilterBar(
                        category = filter,
                        count = homeFilterCount,
                        onClear = { viewModel.setHomeFilter(null) }
                    )
                }
            }

            if (filteredTransactions.isEmpty()) {
                item(key = "empty") {
                    // delegated 属性不能智能转换，先捕获为局部变量再判空
                    val activeFilter = homeFilter
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
                    } else if (activeFilter != null) {
                        // 分类筛选无结果：说明 + 一键清除
                        EmptyState(
                            emoji = Categories.emojiOf(activeFilter),
                            title = stringResource(R.string.home_filter_empty_title),
                            subtitle = stringResource(R.string.home_filter_empty_sub),
                            actionLabel = stringResource(R.string.home_filter_clear),
                            onAction = { viewModel.setHomeFilter(null) }
                        )
                    } else if (rangeTransactions != null) {
                        // 时间范围筛选无结果：提示换个范围或清除
                        EmptyState(
                            emoji = "🗓️",
                            title = stringResource(R.string.time_filter_empty_title),
                            subtitle = stringResource(R.string.time_filter_empty_sub),
                            actionLabel = stringResource(R.string.home_filter_clear),
                            onAction = { clearTimeRange() }
                        )
                    } else if (transactions.isEmpty()) {
                        // 全新用户：欢迎 + 记第一笔引导
                        EmptyState(
                            emoji = "👋",
                            title = stringResource(R.string.home_welcome_title),
                            subtitle = stringResource(R.string.home_welcome_sub),
                            actionLabel = stringResource(R.string.home_welcome_action),
                            onAction = { openSheet() }
                        )
                    } else {
                        // 老用户但本月无记录：轻声提醒 + 一键开记
                        EmptyState(
                            emoji = "🌱",
                            title = stringResource(R.string.home_empty_title),
                            subtitle = stringResource(R.string.home_empty_sub),
                            actionLabel = stringResource(R.string.home_empty_action),
                            onAction = { openSheet() }
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
                                revealed = revealedId == t.id,
                                onReveal = { revealedId = t.id },
                                onClose = {
                                    if (revealedId == t.id) revealedId = null
                                },
                                onDelete = {
                                    revealedId = null
                                    viewModel.delete(t.id)
                                    toastHostState.show(
                                        message = deletedText,
                                        variant = MessageVariant.INFO,
                                        actionLabel = undoText,
                                        onAction = { viewModel.restore(t) }
                                    )
                                },
                                onClick = {
                                    openSheet(editId = t.id)
                                },
                                onDuplicate = {
                                    openSheet(dupId = t.id)
                                }
                            )
                        }
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
                openSheet()
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 底部导航栏悬浮层：FAB 抬到它上方（导航栏高 80dp + 20dp 间距）
                .navigationBarsPadding()
                .padding(20.dp)
                .padding(bottom = 80.dp)
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
            // key(sheetEpoch)：每次打开都是全新输入现场（见 sheetEpoch 注释）
            key(sheetEpoch) {
                TransactionSheet(
                    initial = editing,
                    prefill = duplicating,
                    expenseCategories = expenseCategories,
                    incomeCategories = incomeCategories,
                    recentCategories = recentCategories,
                    startWithRecurring = recurringPrefill,
                    onDismiss = {
                        recurringPrefill = false
                        editingId = null
                        duplicatingId = null
                        sheetOpen = false
                    },
                    onSave = { t ->
                        val isNew = editing == null
                        if (isNew) viewModel.add(t) else viewModel.update(t)
                        viewModel.onTransactionSaved(t)
                        // 分类筛选下记了其它分类：清掉筛选，新记录立即可见
                        if (homeFilter != null && t.category != homeFilter) {
                            viewModel.setHomeFilter(null)
                        }
                        if (isNew) {
                            // 成功 Toast 带「再记一笔」：连续记账不打断节奏（参考支付宝）
                            toastHostState.show(
                                message = savedText,
                                variant = MessageVariant.SUCCESS,
                                actionLabel = recordAgainText,
                                onAction = {
                                    openSheet(dupId = t.id)
                                }
                            )
                            // 夜深了：记完这笔早点休息（同一会话只提示一次）
                            viewModel.maybePostLateNightHint()
                        } else {
                            viewModel.postMessage(savedText, MessageVariant.SUCCESS)
                        }
                        // 记完跳到这笔所在月份：历史月补记/改日期后，新记录立刻可见
                        val cal = Calendar.getInstance().apply { timeInMillis = t.timestamp }
                        val targetMonth = YearMonth(
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH) + 1
                        )
                    if (month != targetMonth) viewModel.goToMonth(targetMonth)
                    // 面板关闭交给退场动画：数据此刻已入库，动画收尾后由
                    // onDismiss 统一清理编辑状态（editingId/duplicatingId/sheetOpen）
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
                    // 关闭同样走退场动画（见 onSave 注释）
                },
                    onAddRecurring = { viewModel.addRecurring(it) }
                )
            }
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

        if (showTimeRangeDialog) {
            TimeRangeDialog(
                current = timeRange,
                onSelect = { option ->
                    if (option == null) {
                        // 选「按月份浏览」：清除筛选并回当前月
                        clearTimeRange()
                        showTimeRangeDialog = false
                    } else if (option == TimeRangeOption.CUSTOM) {
                        // 选「自定义」：转去选起止日期，应用后筛选才生效
                        showTimeRangeDialog = false
                        showCustomRangeDialog = true
                    } else {
                        timeRange = option
                        customRange = null
                        showTimeRangeDialog = false
                    }
                },
                onDismiss = { showTimeRangeDialog = false }
            )
        }

        if (showCustomRangeDialog) {
            CustomRangeDialog(
                startMillis = customStart,
                endMillis = customEnd,
                onStartChange = { customStart = it },
                onEndChange = { customEnd = it },
                onApply = {
                    // 起止颠倒时自动交换；起止整日（start 0 点 / end 23:59:59）由
                    // 对话框初始化与 DatePickerButton 保留时分保证
                    val s = minOf(customStart, customEnd)
                    val e = maxOf(customStart, customEnd)
                    timeRange = TimeRangeOption.CUSTOM
                    customRange = longArrayOf(s, e)
                    showCustomRangeDialog = false
                },
                onDismiss = { showCustomRangeDialog = false }
            )
        }
    }
}

/**
 * 品牌蓝渐变大页头（随列表滚动）：时段问候 + 月份切换 + 本月支出大数字 +
 * 月度预算/攒钱目标进度卡 + 三个宽松小指标（收入 / 今日 / 连续）+ 激活时的搜索区。
 */
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
    budgetCents: Long,
    goals: List<Goal>,
    budgetActive: Boolean,
    onBudgetClick: () -> Unit,
    onGoalClick: () -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    menuOpen: Boolean,
    onDismissMenu: () -> Unit,
    onOpenStats: () -> Unit,
    searchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSearchExit: () -> Unit,
    searchType: Transaction.Type?,
    onSearchTypeChange: (Transaction.Type?) -> Unit,
    searchFocus: FocusRequester,
    greetingHour: Int,
    onMonthTitleClick: (() -> Unit)?,
    onTodayClick: () -> Unit,
    timeFilterLabel: String?,
    onTimeFilterClick: () -> Unit,
    onTimeFilterClear: () -> Unit
) {
    // 按时段换问候与副文案：早上 5-11 / 中午 11-13 / 下午 13-18 / 晚上 18-5
    val greeting = when (greetingHour) {
        in 5..10 -> stringResource(R.string.greeting_morning)
        in 11..12 -> stringResource(R.string.greeting_noon)
        in 13..17 -> stringResource(R.string.greeting_afternoon)
        else -> stringResource(R.string.greeting_evening)
    }
    val greetingSub = when (greetingHour) {
        in 5..10 -> stringResource(R.string.greeting_sub_morning)
        in 11..12 -> stringResource(R.string.greeting_sub_noon)
        in 13..17 -> stringResource(R.string.greeting_sub_afternoon)
        else -> stringResource(R.string.greeting_sub_evening)
    }
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
            Column(Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = greetingSub,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 时间范围筛选入口：选「今天 / 本周 / 近 3 个月 / 今年 / 全部 / 自定义…」；
            // 激活时加浅色圆底，提醒列表当前处于筛选视图
            Box(
                Modifier
                    .clip(CircleShape)
                    .then(
                        if (timeFilterLabel != null) {
                            Modifier.background(Color.White.copy(alpha = 0.22f))
                        } else {
                            Modifier
                        }
                    )
            ) {
                IconButton(onClick = onTimeFilterClick) {
                    Icon(
                        Icons.Filled.DateRange,
                        contentDescription = stringResource(R.string.time_filter_desc),
                        tint = Color.White
                    )
                }
            }
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

        if (timeFilterLabel == null) {
            MonthSelector(
                month = month,
                onPrev = onPrev,
                onNext = onNext,
                nextEnabled = nextEnabled,
                contentColor = Color.White,
                // 不在当前月时标题可点（下划线提示），一键回到本月
                onTitleClick = onMonthTitleClick,
                // 首页整页随月份滑动，标题不自带动画，避免双重位移
                animateTitle = false
            )
        } else {
            // 时间筛选激活：翻月与列表无关，换成范围条（点击改范围 / ✕ 清除回本月）
            TimeRangeActiveBar(
                label = timeFilterLabel,
                onEdit = onTimeFilterClick,
                onClear = onTimeFilterClear
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.home_overview_expense),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(2.dp))
            // 本月支出大数字：进页从 0 滚到当前值，之后每次变化（记一笔/翻月/
            // 删除撤销）都从旧值滚到新值——钱在动的实感。动画期间显示插值，
            // 动画结束用精确值展示（滚动中间值不落屏）
            val expenseAnim = remember { Animatable(0f) }
            LaunchedEffect(expense) {
                expenseAnim.animateTo(
                    targetValue = expense.toFloat(),
                    animationSpec = tween(520, easing = FastOutSlowInEasing)
                )
            }
            val rolling = expenseAnim.isRunning
            Text(
                text = MoneyUtils.formatCents(
                    if (rolling) expenseAnim.value.roundToLong() else expense
                ),
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(14.dp))
            // 月度预算 / 攒钱目标进度卡：本月支出大数字下方的空白区，余量 / 进度一眼可见
            val overBudget = budgetCents > 0 && budgetActive && expense > budgetCents
            // 接近上限（花掉 ≥90%）提前转橙预警：不等超支那一下才被提醒
            val nearBudget = !overBudget && budgetCents > 0 && budgetActive &&
                expense >= budgetCents * 9 / 10
            val budgetColor = when {
                overBudget -> ExpenseRedText
                nearBudget -> WarningOrangeText
                else -> YuanmanBlueDeep
            }
            val goalSaved = goals.sumOf { it.savedCents }
            val goalTarget = goals.filter { it.targetCents > 0 }.sumOf { it.targetCents }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HeaderProgressCard(
                    title = stringResource(R.string.budget_title),
                    value = when {
                        budgetCents <= 0 -> stringResource(R.string.home_card_budget_unset)
                        !budgetActive -> stringResource(
                            R.string.home_card_budget_amount,
                            MoneyUtils.formatCents(budgetCents)
                        )
                        overBudget -> stringResource(
                            R.string.home_card_budget_over,
                            MoneyUtils.formatCents(expense - budgetCents)
                        )
                        else -> stringResource(
                            R.string.home_card_budget_left,
                            MoneyUtils.formatCents(budgetCents - expense)
                        )
                    },
                    valueColor = budgetColor,
                    progress = if (budgetCents > 0 && budgetActive) {
                        expense.toFloat() / budgetCents
                    } else {
                        null
                    },
                    onClick = onBudgetClick
                )
                HeaderProgressCard(
                    title = stringResource(R.string.home_section_goal),
                    value = if (goals.isEmpty()) {
                        stringResource(R.string.home_card_goal_unset)
                    } else {
                        stringResource(
                            R.string.home_card_goal_saved,
                            MoneyUtils.formatCents(goalSaved)
                        )
                    },
                    valueColor = YuanmanBlueDeep,
                    progress = if (goalTarget > 0) {
                        goalSaved.toFloat() / goalTarget
                    } else {
                        null
                    },
                    onClick = onGoalClick
                )
            }
            Spacer(Modifier.height(14.dp))
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
                    onClick = onTodayClick,
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        .weight(1f)
                        .focusRequester(searchFocus)
                )
                // 「取消」与返回键同语义：退出搜索并清空（蓝头白字与白底输入框对比清晰）
                TextButton(onClick = onSearchExit) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = Color.White
                    )
                }
            }
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

/**
 * 页头进度卡：白底圆角卡，标题 + 主数值 + 细进度条（progress 为 null 时只显示
 * 引导文案，不画进度）。预算卡超支时数值与进度条整体转红，其余用主题蓝。
 * 卡片恒为白底，说明文字色固定中性灰，不随深浅主题变化。
 */
@Composable
private fun HeaderProgressCard(
    title: String,
    value: String,
    valueColor: Color,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val captionColor = Color(0xFF6B7A8A)
    val percent = progress?.let { (it * 100).roundToInt().coerceIn(0, 100) }
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .pressScale(pressedScale = 0.95f)
            .clickable {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = captionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (percent != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.goal_progress_percent, percent),
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = captionColor
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            HeaderProgressBar(progress = progress, fill = valueColor)
        }
    }
}

/** 页头进度卡里的细进度条：圆角 4dp，浅色底 + 主题色填充段（超预算时整条变红）。 */
@Composable
private fun HeaderProgressBar(
    progress: Float,
    fill: Color,
    modifier: Modifier = Modifier
) {
    val clamped = progress.coerceIn(0f, 1f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(4.dp)
    ) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = fill.copy(alpha = 0.14f), cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset.Zero,
                size = Size(size.width * clamped, size.height),
                cornerRadius = radius
            )
        }
    }
}

/** 页头小指标列：白字 label 在上、白字加粗 value 在下。可点的指标呈按钮样式（今日）。 */
@Composable
private fun HeaderStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    Column(
        modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .pressScale(pressedScale = 0.95f)
                        .clickable {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClick()
                        }
                        .semantics { role = Role.Button }
                } else {
                    Modifier
                }
            )
    ) {
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
        // 今天的分组用主色胶囊标注：滚动定位后一眼认出「今天在哪」
        if (dayKey == todayKey) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = dayLabel,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                text = dayLabel,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
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
 * 流水项：左滑露出「删除」按钮，点击即删——删除可经 Toast 撤销，不再弹窗二次确认；
 * 展开态点行本体 = 收起动作，闭合态点行 = 编辑。同一时间只展开一行，
 * [revealed] / [onReveal] / [onClose] 由列表层统一管理。
 */
@Composable
private fun SwipeToDismissItem(
    t: Transaction,
    revealed: Boolean,
    onReveal: () -> Unit,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onDuplicate: () -> Unit
) {
    val actionWidth = 96.dp
    val actionWidthPx = with(LocalDensity.current) { actionWidth.toPx() }
    // 行滑动位移：0 = 闭合，-actionWidthPx = 展开（露出右侧删除按钮）。
    // 状态随条目组合存在（remember(t.id)）：条目移出列表即销毁，撤销恢复的条目总是闭合态
    val offsetX = remember(t.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 另一行展开时本行自动收起：动画回弹而非瞬间归位
    LaunchedEffect(revealed) {
        if (!revealed && offsetX.value < 0f) {
            offsetX.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // 底层红色删除按钮（右侧固定宽）：内容左滑后露出，点击直接删除
        Box(
            Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(20.dp))
                .background(ExpenseRed),
            contentAlignment = Alignment.CenterEnd
        ) {
            Row(
                Modifier
                    .fillMaxHeight()
                    .width(actionWidth)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = rememberRipple(
                            color = Color.White.copy(alpha = 0.4f)
                        ),
                        onClick = {
                            view.performHapticFeedback(
                                HapticFeedbackConstants.KEYBOARD_TAP
                            )
                            onDelete()
                        }
                    ),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.common_delete),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        // 内容层：跟手左滑；松手时滑过一半（或向左甩）停在展开位，否则回弹
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .draggable(
                    state = rememberDraggableState { delta ->
                        scope.launch {
                            offsetX.snapTo(
                                (offsetX.value + delta).coerceIn(-actionWidthPx, 0f)
                            )
                        }
                    },
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        scope.launch {
                            val target = when {
                                velocity <= -1000f -> -actionWidthPx
                                velocity >= 1000f -> 0f
                                offsetX.value < -actionWidthPx / 2f -> -actionWidthPx
                                else -> 0f
                            }
                            offsetX.animateTo(
                                target,
                                tween(240, easing = FastOutSlowInEasing)
                            )
                            if (target < 0f) onReveal() else onClose()
                        }
                    }
                )
        ) {
            // 展开态点行 = 先收起（给「反悔」机会），闭合态点行 = 编辑
            TransactionRow(
                t = t,
                onClick = { if (revealed) onClose() else onClick() },
                onDuplicate = onDuplicate,
                onDelete = onDelete
            )
        }
    }
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
            modifier = Modifier.fillMaxWidth().pressScale()
        ) {
            // 点击放内容 Row 上：Card 的圆角裁剪会顺带裁掉水波纹（挂在
            // Card 修饰符上的 clickable 只裁背景不裁涟漪，按下去是方角）
            Row(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = { menuOpen = true }
                    )
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
                        color = if (isExpense) {
                            expenseAmountColor()
                        } else {
                            incomeAmountColor()
                        },
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

/** 统计页点分类直达后首页顶部的筛选条：分类名 + 笔数 + 一键清除。 */
@Composable
private fun CategoryFilterBar(
    category: String,
    count: Int,
    onClear: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = primary.copy(alpha = 0.1f)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${Categories.emojiOf(category)} ${Categories.displayName(category)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.home_filter_count, count),
                style = MaterialTheme.typography.bodySmall,
                color = primary
            )
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.home_filter_clear))
            }
        }
    }
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))

/** 页头时间筛选激活条：替代月份选择器显示当前范围，点击可改范围，✕ 清除回本月。 */
@Composable
private fun TimeRangeActiveBar(
    label: String,
    onEdit: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onEdit)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.DateRange,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.time_filter_active, label),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onClear) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.home_filter_clear),
                tint = Color.White
            )
        }
    }
}

/** 时间范围选择对话框：单选列表点击即生效；「自定义」转去选起止日期。 */
@Composable
private fun TimeRangeDialog(
    current: TimeRangeOption?,
    onSelect: (TimeRangeOption?) -> Unit,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.time_filter_title)) },
        text = {
            Column {
                val items = listOf<TimeRangeOption?>(null) + TimeRangeOption.entries
                items.forEach { option ->
                    val selected = current == option
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                onSelect(option)
                            }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(
                                option?.resId ?: R.string.time_filter_month
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier.weight(1f)
                        )
                        if (selected) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}

/** 自定义起止日期对话框：两个日期按钮 + 应用（起止颠倒在应用时自动交换）。 */
@Composable
private fun CustomRangeDialog(
    startMillis: Long,
    endMillis: Long,
    onStartChange: (Long) -> Unit,
    onEndChange: (Long) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.custom_range_title)) },
        text = {
            Column {
                DatePickerButton(
                    label = stringResource(R.string.custom_range_start),
                    millis = startMillis,
                    onDateSelected = onStartChange,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DatePickerButton(
                    label = stringResource(R.string.custom_range_end),
                    millis = endMillis,
                    onDateSelected = onEndChange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.custom_range_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/** 首页流水列表的时间范围筛选选项（UI 文案走字符串资源）。 */
private enum class TimeRangeOption(@StringRes val resId: Int) {
    TODAY(R.string.time_filter_today),
    WEEK(R.string.time_filter_week),
    LAST3(R.string.time_filter_last3),
    LAST6(R.string.time_filter_last6),
    YEAR(R.string.time_filter_year),
    ALL(R.string.time_filter_all),
    CUSTOM(R.string.time_filter_custom)
}

/**
 * 计算筛选范围的 [startMillis, endMillis]（闭区间）；ALL 与未设置的自定义返回 null。
 * 「今天 / 本周 / 近 N 个月 / 今年」都含今天全天，跨天后由 nowTick 触发重算。
 */
private fun rangeMillisOf(
    option: TimeRangeOption,
    custom: LongArray?
): Pair<Long, Long>? {
    fun todayStart(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun todayEnd(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 23)
        set(Calendar.MINUTE, 59)
        set(Calendar.SECOND, 59)
        set(Calendar.MILLISECOND, 999)
    }.timeInMillis

    val start = when (option) {
        TimeRangeOption.TODAY -> todayStart()
        TimeRangeOption.WEEK -> {
            // 本周一 0 点：周日起回退到上周一（(dow + 5) % 7，周一 = 2 不回退）
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                add(Calendar.DAY_OF_YEAR, -(get(Calendar.DAY_OF_WEEK) + 5) % 7)
            }
            cal.timeInMillis
        }
        TimeRangeOption.LAST3 -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -3)
        }.timeInMillis
        TimeRangeOption.LAST6 -> Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.MONTH, -6)
        }.timeInMillis
        TimeRangeOption.YEAR -> Calendar.getInstance().apply {
            set(Calendar.MONTH, 0)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        TimeRangeOption.CUSTOM -> custom?.getOrNull(0) ?: return null
        TimeRangeOption.ALL -> return null
    }
    val end = when (option) {
        TimeRangeOption.TODAY -> start + 24 * 3600_000L - 1
        TimeRangeOption.WEEK -> start + 7 * 24 * 3600_000L - 1
        TimeRangeOption.CUSTOM -> custom?.getOrNull(1) ?: return null
        else -> todayEnd()
    }
    return start to end
}
