package com.yuanman.app.ui.screens.list

import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.AppHeaderSurface
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.DateGroupHeader
import com.yuanman.app.ui.components.EmptyStateView
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.MonthPickerModal
import com.yuanman.app.ui.components.SheetTitle
import com.yuanman.app.ui.components.RecordDetailCard
import com.yuanman.app.ui.components.SwipeRevealDeleteItem
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.ui.components.YuanmanDatePickerSheet
import com.yuanman.app.ui.components.YuanmanPullRefreshIndicator
import com.yuanman.app.ui.screens.home.BitgetTransactionItem
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils
import kotlin.math.abs
import java.util.Calendar

/**
 * 筛选区随滚动显隐的判定器（纯逻辑，单测见 FilterVisibilityControllerTest）。
 *
 * 三条纪律，缺一条就会退回「滚动时高度反复伸缩、抖到卡顿」的老问题：
 *  1. 阈值触发：朝同一方向累计滑够 [thresholdPx] 才切换，不是一滚动就收；
 *  2. 方向锁：累计方向一反转立刻清零重算，杜绝在阈值临界处来回反复切；
 *  3. 动画冻结：收起/展开动画期间（canJudge = false）完全不判定也不累计，切断
 *     「筛选区高度变化 → 列表视口变化 → 又被判成反向滚动」的反馈回路。
 */
internal class FilterVisibilityController(private val thresholdPx: Float) {
    private var direction = 0
    private var accumulated = 0f

    /**
     * @param consumedY 列表实际消费的滚动量：< 0 手指上滑（往下翻看更多账单），> 0 手指下滑（往回翻看）
     * @param canJudge false 表示当前处于收起/展开动画中，冻结判定
     * @return true 应展开筛选区；false 应收起；null 本次不切换
     */
    fun onScroll(consumedY: Float, canJudge: Boolean): Boolean? {
        if (!canJudge || consumedY == 0f) return null

        val dir = if (consumedY > 0f) 1 else -1
        if (dir != direction) {
            direction = dir
            accumulated = 0f
        }
        accumulated += abs(consumedY)
        if (accumulated < thresholdPx) return null

        accumulated = 0f
        return dir > 0
    }
}

/** 筛选区收起/展开的滚动触发阈值：朝同一方向连续滑够这么多才切换（配合方向锁与动画冻结避免抖动） */
private val FILTER_SCROLL_THRESHOLD = 28.dp

/**
 * 筛选区是否已经「完全恢复」：展开态且收起/展开动画已结束。
 *
 * 注意展开动画进行中（targetState 已置 true、currentState 还停在 false）同样算未恢复：
 * MutableTransitionState.isIdle 会在 targetState 一变就转为 false，靠它把整段动画窗口一起圈进来，
 * 下拉刷新必须等筛选区真正恢复到位才放行。
 */
private val MutableTransitionState<Boolean>.isFullyExpanded: Boolean
    get() = targetState && isIdle

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

    // 页面可见性：组合期间激活明细 VM 的写库监听；切走 Tab（组合销毁）后停掉，
    // 避免 VM 随状态保留时仍在后台重复执行分页/汇总查询
    DisposableEffect(Unit) {
        viewModel.setPageActive(true)
        onDispose { viewModel.setPageActive(false) }
    }
    var showSearchBar by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showDatePickerSheet by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var searchFocused by remember { mutableStateOf(false) }
    var openSwipeItemId by remember { mutableStateOf<Long?>(null) }

    // 🌟 筛选过滤区随滚动显隐（阈值触发 + 方向锁 + 动画期间冻结判定）：
    //   手指上滑（内容向上移动、往下翻看更多账单，consumed.y < 0）累计超过阈值 → 收起筛选区，把空间让给列表；
    //   仅在回到列表顶部（firstVisibleItemIndex == 0 且偏移为 0）时恢复展开，杜绝在翻看记录过程中反复往下弹。
    //   方向锁：累计方向一反转就清零重算，必须朝同一方向连续滑够阈值才切换，避免临界处来回抖动。
    //   动画期间不判定：收起/展开本身会改变列表视口高度，跳过这段时间的滚动量即可切断反馈回路。
    val filtersTransition = remember { MutableTransitionState(true) }

    // 🌟 下拉刷新的放行条件：筛选区必须已经完全恢复（展开且收起/展开动画已结束），否则禁用。
    //   不这样拦一道，「上滑收起筛选区 → 往回滑到顶部」这一下会被刷新抢走：筛选区还折叠着，
    //   刷新指示器就先冒出来并在松手时触发刷新。禁用后同样的下拉只负责恢复筛选区，
    //   等它恢复完成，余下的手势才交给下拉刷新。
    //   注：enabled 必须是稳定引用的 lambda（用 remember 固定），且内部按调用时刻读状态，
    //   否则 rememberSaveable 会随每次重组重建刷新状态，把已积累的下拉位移清掉。
    val pullRefreshEnabled = remember(filtersTransition) {
        { !viewModel.isRefreshing.value && filtersTransition.isFullyExpanded }
    }
    val pullRefreshState = rememberPullToRefreshState(enabled = pullRefreshEnabled)
    var refreshWasRunning by remember { mutableStateOf(false) }
    // 🌟 展开式搜索框：顶栏搜索图标切换显隐，展开后自动聚焦并弹出键盘
    val searchFocusRequester = remember { FocusRequester() }

    // 明细「天筛选」同步给底部「记一笔」入口：选中哪天，就把哪天零点带进新增页（补记当天账）
    LaunchedEffect(uiState.selectedYear, uiState.selectedMonth, uiState.selectedDay) {
        RecordListAddBridge.selectedDayStartMillis = uiState.selectedDay?.let { day ->
            java.util.Calendar.getInstance().apply {
                clear()
                set(uiState.selectedYear, uiState.selectedMonth - 1, day)
            }.timeInMillis
        }
    }

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

    // 展开搜索框时自动聚焦并弹出键盘
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(showSearchBar) {
        if (showSearchBar) {
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    // 收起/展开的手势判定（状态声明见上；筛选区恢复前下拉刷新被禁用，见 pullRefreshEnabled）
    val filterScrollThresholdPx = with(LocalDensity.current) { FILTER_SCROLL_THRESHOLD.toPx() }
    val filtersVisibilityConnection = remember(filterScrollThresholdPx, filtersTransition, listState) {
        val controller = FilterVisibilityController(filterScrollThresholdPx)
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 手指上滑（往下看账单，available.y < 0）：仅在列表允许向下翻看且为用户主动滑动时判定收起筛选区
                if (source == NestedScrollSource.UserInput && available.y < 0f && listState.canScrollForward) {
                    val shouldShow = controller.onScroll(available.y, canJudge = filtersTransition.isIdle)
                    if (shouldShow == false) {
                        filtersTransition.targetState = false
                    }
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // 仅响应用户手动下拉（过滤掉惯性回弹和物理拉伸 SideEffect），且仅在回到列表顶部时才判定展开，杜绝在翻看记录中途或回弹时误弹筛选区
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    val isAtTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                    if (isAtTop) {
                        val shouldShow = controller.onScroll(available.y, canJudge = filtersTransition.isIdle)
                        if (shouldShow == true) {
                            filtersTransition.targetState = true
                        }
                        // 筛选区尚未恢复时，这段下拉的余量全部由「恢复筛选区」占用并吃掉：
                        // 既不让列表被拉出平台回弹变形，也不给下拉刷新留下可积累的位移
                        // （刷新此刻本就处于禁用态，恢复完成后余下手势再正常交给它）
                        if (!filtersTransition.isFullyExpanded) return Offset(0f, available.y)
                    }
                }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }.collect { atTop ->
            // 仅当停止滚动且真正停留在顶部时恢复展开，避免上滑初期布局重排导致筛选区反复往下弹
            if (atTop && !listState.isScrollInProgress) {
                filtersTransition.targetState = true
            }
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
        // 顶部 Header 卡铺满内容区顶端（贴屏幕顶、与首页顶卡同构），因此内容区不再吃状态栏内边距
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // 🌟 0. 顶部 Header —— 与首页 FinancialOverviewCard 同款视觉（AppHeaderSurface：
                // 素面底 + 低对比斜向细纹理 + 主色柔光晕 + 1dp 细描边 + 3dp 柔和投影）。贴屏幕顶、
                // 仅底部 18dp 圆角，下方滚动列表之上留出间距承接投影。原标题行 / 搜索框 / 筛选区 /
                // 当月汇总统一收纳进这张 Header 卡，全部交互与显隐动画保持原样。
                AppHeaderSurface(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 顶栏行：账单明细标题 + 月份选择 + 搜索入口（原 TopAppBar 内容迁入卡内）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "账单明细",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )

                            val isSearchActive = showSearchBar || uiState.searchQuery.isNotEmpty()
                            val (currentYear, currentMonth) = remember { DateTimeUtils.getCurrentYearMonth() }
                            val canGoNextMonth = uiState.selectedYear < currentYear ||
                                (uiState.selectedYear == currentYear && uiState.selectedMonth < currentMonth)

                            // 1. 月份胶囊：‹ › 左右箭头翻月 / 胶囊上左右滑动翻月 / 点中间文字呼出月份选择器
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                border = BorderStroke(0.75.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .pointerInput(canGoNextMonth) {
                                        // 左右滑动翻月：左滑 → 下月，右滑 → 上月（超过阈值才触发）
                                        val flipThreshold = 36.dp.toPx()
                                        var totalDrag = 0f
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                when {
                                                    totalDrag <= -flipThreshold -> if (canGoNextMonth) viewModel.nextMonth()
                                                    totalDrag >= flipThreshold -> viewModel.previousMonth()
                                                }
                                                totalDrag = 0f
                                            },
                                            onDragCancel = { totalDrag = 0f }
                                        ) { change, dragAmount ->
                                            change.consume()
                                            totalDrag += dragAmount
                                        }
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable { viewModel.previousMonth() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = "上一个月",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = "${uiState.selectedYear}年${uiState.selectedMonth}月",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .clickable { showMonthPicker = true }
                                            .padding(horizontal = 5.dp, vertical = 6.dp)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .clickable(enabled = canGoNextMonth) { viewModel.nextMonth() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = "下一个月",
                                            tint = if (canGoNextMonth) {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                                            },
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 2. 统一风格的搜索圆形微胶囊（32dp x 32dp，展开时高亮激活）
                            Surface(
                                shape = CircleShape,
                                color = if (isSearchActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                },
                                border = BorderStroke(
                                    0.75.dp,
                                    if (isSearchActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                ),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        if (isSearchActive) {
                                            showSearchBar = false
                                            viewModel.updateSearchQuery("")
                                        } else {
                                            showSearchBar = true
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                                        contentDescription = if (isSearchActive) "收起搜索" else "展开搜索",
                                        tint = if (isSearchActive) MaterialTheme.colorScheme.onPrimaryContainer
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        // 🌟 1. 全宽现代搜索框（顶栏搜索图标展开/收起；有关键词输入时保持展开）
                        AnimatedVisibility(
                            visible = showSearchBar || uiState.searchQuery.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
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
                                                text = "搜索备注、金额、分类或账户...",
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
                                                .focusRequester(searchFocusRequester)
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
                        }

                        // 🌟 2. 筛选过滤区（类型切换 + 排序 + 日期天筛选）
                        // 收起/展开的判定见 filtersTransition（阈值 + 方向锁 + 动画期间冻结）
                        AnimatedVisibility(
                            visibleState = filtersTransition,
                            enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
                            exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(200))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    // 已并入 AppHeaderSurface 的素面底，去掉独立底色避免压住底纹纹理
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
                                        if (currentDay > 1) {
                                            item {
                                                ModernFilterPill(
                                                    text = "昨天 (${currentDay - 1}日)",
                                                    selected = uiState.selectedDay == currentDay - 1,
                                                    onClick = { viewModel.selectDay(currentDay - 1) }
                                                )
                                            }
                                        }

                                        if (currentDay > 2) {
                                            item {
                                                ModernFilterPill(
                                                    text = "前天 (${currentDay - 2}日)",
                                                    selected = uiState.selectedDay == currentDay - 2,
                                                    onClick = { viewModel.selectDay(currentDay - 2) }
                                                )
                                            }
                                        }
                                    }

                                    // 自定义日期选择器 📅
                                    item {
                                        val isYesterday = isCurrentSelectedMonth && currentDay > 1 && uiState.selectedDay == (currentDay - 1)
                                        val isDayBeforeYesterday = isCurrentSelectedMonth && currentDay > 2 && uiState.selectedDay == (currentDay - 2)
                                        val isCustomDaySelected = uiState.selectedDay != null && !isYesterday && !isDayBeforeYesterday
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

                                    // 第四行：资金账户筛选（账单只存支付方式文本，
                                    // 历史写法如「微信支付」按账户页同一套匹配归到对应账户）
                                    val accountFilters = uiState.accountFilters
                                    Spacer(modifier = Modifier.height(2.dp))
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        item {
                                            ModernFilterPill(
                                                text = "全部账户",
                                                selected = uiState.selectedPaymentMethods.isEmpty(),
                                                onClick = { viewModel.clearAccountFilter() }
                                            )
                                        }
                                        items(accountFilters.options, key = { it.accountId }) { option ->
                                            val isSelected = option.filterMethods
                                                .all { it in uiState.selectedPaymentMethods }
                                            ModernFilterPill(
                                                text = option.name,
                                                selected = isSelected,
                                                onClick = { viewModel.toggleAccountFilter(option.filterMethods) }
                                            )
                                        }
                                        // 未归属任何资金账户的历史方式（如未建现金账户时的「现金」）
                                        if (accountFilters.unmatchedMethods.isNotEmpty()) {
                                            item {
                                                val isSelected = accountFilters.unmatchedMethods
                                                    .all { it in uiState.selectedPaymentMethods }
                                                ModernFilterPill(
                                                    text = "其它方式",
                                                    selected = isSelected,
                                                    onClick = {
                                                        viewModel.toggleAccountFilter(accountFilters.unmatchedMethods)
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                        }

                        // 🌟 3. 当月汇总收支条（并入 Header 卡后去掉独立底色带，避免双重底色）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 9.dp),
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
                }

                // 🌟 4. 账单列表区
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .nestedScroll(pullRefreshState.nestedScrollConnection)
                        .nestedScroll(filtersVisibilityConnection)
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
                            contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)
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
                            } else if (!uiState.hasMore && uiState.filteredRecords.isNotEmpty()) {
                                // 🌟 到底提示：无论总数多少，加载完最后一页即展示
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

    // 月份选择器（整月筛选：点顶栏年月文本呼出）
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
            // 筛选只需回看，不允许选择今天之后的日期
            restrictFuture = true,
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
                SheetTitle(
                    title = "账单详情",
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
            recordToDelete?.let { target ->
                viewModel.deleteRecord(target)
                toast.info(
                    message = "账单已删除",
                    actionLabel = "撤销",
                    onAction = { viewModel.undoDeleteRecord(target) }
                )
            }
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
