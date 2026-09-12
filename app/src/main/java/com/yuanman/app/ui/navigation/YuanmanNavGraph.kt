package com.yuanman.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yuanman.app.YuanmanApplication
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.BottomNavBar
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.PredictiveSwipeBackContainer
import com.yuanman.app.ui.screens.account.AccountDetailScreen
import com.yuanman.app.ui.screens.account.AccountReconcileScreen
import com.yuanman.app.ui.screens.account.AccountScreen
import com.yuanman.app.ui.screens.account.AccountViewModel
import com.yuanman.app.ui.screens.account.PaycheckRunScreen
import com.yuanman.app.ui.screens.account.PlanDetailScreen
import com.yuanman.app.ui.screens.add_edit.AddEditRecordScreen
import com.yuanman.app.ui.screens.add_edit.AddEditRecordViewModel
import com.yuanman.app.ui.screens.category.AddEditCategoryScreen
import com.yuanman.app.ui.screens.category.AddEditCategoryViewModel
import com.yuanman.app.ui.screens.category.CategoryManageScreen
import com.yuanman.app.ui.screens.category.CategoryManageViewModel
import com.yuanman.app.ui.screens.home.HomeScreen
import com.yuanman.app.ui.screens.home.HomeViewModel
import com.yuanman.app.ui.screens.list.RecordListScreen
import com.yuanman.app.ui.screens.list.RecordListViewModel
import com.yuanman.app.ui.screens.settings.SettingsScreen
import com.yuanman.app.ui.screens.settings.QuickRecordSettingsScreen
import com.yuanman.app.ui.screens.settings.SettingsViewModel
import com.yuanman.app.ui.screens.stats.CategoryRecordsScreen
import com.yuanman.app.ui.screens.stats.CategoryRecordsViewModel
import com.yuanman.app.ui.screens.panorama.AssetPanoramaScreen
import com.yuanman.app.ui.screens.panorama.AssetPanoramaViewModel
import com.yuanman.app.ui.screens.stats.StatisticsScreen
import com.yuanman.app.ui.screens.stats.StatisticsViewModel
import kotlin.math.abs
import kotlin.math.max

/**
 * 二级页面路由目标定义（用于分层预测性返回叠放栈）
 */
sealed class SecondaryScreen {
    data class AddEditRecord(
        val recordId: Long = 0L,
        val type: RecordType? = null,
        val categoryId: Long = 0L,
        val recordTime: Long? = null, // 明细页「天筛选」带入：预填新增记录到该日（补账）
        val nonce: Long = System.currentTimeMillis() // 每次打开生成新 ViewModel，避免复用旧实例的残留状态
    ) : SecondaryScreen()

    data class AddEditCategory(
        val categoryId: Long = 0L,
        val type: RecordType? = null,
        val nonce: Long = System.currentTimeMillis() // 每次打开生成新 ViewModel，避免复用旧实例的残留状态
    ) : SecondaryScreen()

    data class CategoryRecords(
        val categoryId: Long
    ) : SecondaryScreen()

    object CategoryManage : SecondaryScreen()
    object Statistics : SecondaryScreen()
    object AssetPanorama : SecondaryScreen()

    /** v0.0.4：发薪分配 / 账户核对 / 账户详情 / 计划详情 / 记账习惯设置 二级页 */
    object PaycheckRun : SecondaryScreen()
    object AccountReconcile : SecondaryScreen()

    /** 账户详情（账户行点击进入；对账记录 / 周期覆盖 / 账户操作） */
    data class AccountDetail(val accountId: Long) : SecondaryScreen()

    /** 攒钱计划详情（计划小卡点击进入；攒钱/取出记录与计划编辑） */
    data class PlanDetail(val planId: Long) : SecondaryScreen()
    object QuickRecordSettings : SecondaryScreen()
}

private val TAB_ROUTES = listOf(
    Screen.Home.route,
    Screen.RecordList.route,
    Screen.Account.route,
    Screen.Settings.route
)

@Composable
fun YuanmanNavGraph(
    navController: NavHostController,
    app: YuanmanApplication,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val defaultRecordType by app.preferencesRepository.defaultRecordType.collectAsState(initial = RecordType.EXPENSE)

    // 二级页面挂载栈（实现物理双层叠放，彻底消灭黑底）
    val secondaryStack = remember { mutableStateListOf<SecondaryScreen>() }
    var topSwipeProgress by remember { mutableFloatStateOf(0f) }
    // 当前返回手势来源侧（0 = 左缘，1 = 右缘，与系统 BackEvent.swipeEdge 一致）；
    // 决定底座 / 下层页面的缩放锚点在左还是在右，进度归零时复位为左缘
    var activeSwipeEdge by remember { mutableIntStateOf(0) }

    val isAtRoot = secondaryStack.isEmpty()

    // 监听从外部（如快捷微件 Widget）通过 navController.navigate 传入的二级路由
    LaunchedEffect(navBackStackEntry) {
        val route = navBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (route.startsWith("add_edit_record") || route.startsWith("statistics") || route.startsWith("category_manage") || route.startsWith("asset_panorama")) {
            when {
                route.startsWith("add_edit_record") -> {
                    val recordId = navBackStackEntry?.arguments?.getLong("recordId") ?: 0L
                    val typeStr = navBackStackEntry?.arguments?.getString("type") ?: ""
                    val categoryId = navBackStackEntry?.arguments?.getLong("categoryId") ?: 0L
                    val type = if (typeStr.isNotBlank()) RecordType.fromString(typeStr) else null
                    secondaryStack.add(SecondaryScreen.AddEditRecord(recordId, type, categoryId))
                }
                route.startsWith("statistics") -> {
                    secondaryStack.add(SecondaryScreen.Statistics)
                }
                route.startsWith("asset_panorama") -> {
                    secondaryStack.add(SecondaryScreen.AssetPanorama)
                }
                route.startsWith("category_manage") -> {
                    secondaryStack.add(SecondaryScreen.CategoryManage)
                }
            }
            navController.popBackStack()
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity
    val toast = LocalToastHostState.current

    // 一级页（无二级页悬浮）返回：2 秒内连续两次返回 → 退到系统桌面。
    // 用预测性返回「静默消费」手势进度：不绘制页面级返回动画，也抑制系统默认
    // 「整页缩放退回桌面」预览；tab 之间切换不记返回栈（单条目栈，见 BottomNavBar），
    // 因此任意一级页的返回都落到这里。第一次返回仅 toast 提示「再按一次返回退出」，
    // 不退出；2 秒内的第二次返回才把任务退到后台（moveTaskToBack），不结束进程、
    // 保留热状态，随时可从最近任务原样回来。
    var lastRootBackMillis by remember { mutableStateOf(0L) }
    PredictiveBackHandler(enabled = isAtRoot) { progressFlow ->
        progressFlow.collect { /* 静默消费进度：根层退出不做页面动画 */ }
        val now = System.currentTimeMillis()
        if (now - lastRootBackMillis <= 2000L) {
            activity?.moveTaskToBack(true)
        } else {
            lastRootBackMillis = now
            toast.info("再按一次返回退出")
        }
    }
    // 「连续两次返回」仅在根层连续发生才成立：切 tab 或进出二级页都会作废上一次计数
    LaunchedEffect(isAtRoot, currentRoute) {
        lastRootBackMillis = 0L
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ================= 1. 底座层：主 Tab 页面（首页、明细、账户、设置） =================
        // Tab 一级页互切仅做极短淡入淡出（无滑动），见下方 NavHost 转场配置
        // 底座层的「reveal 模型」：reveal = 1 全屏无遮罩；reveal = 0 缩小至 0.94 并压暗 22%。
        // 静止目标随栈角色（空栈 / 有悬浮页）用动画平滑过渡，消除推入、弹出的瞬间跳变；
        // 当底座正下方就是顶层（栈只有一层）且顶层被手势拖动时，由手势进度逐帧覆盖，保持跟手手感。
        val boundedProgress = topSwipeProgress.coerceIn(0f, 1f)
        val baseRestingReveal = remember { Animatable(1f) }
        val baseRevealTarget = if (secondaryStack.isEmpty()) 1f else 0f
        LaunchedEffect(baseRevealTarget) {
            // 动画前先同步到当前实际值（含手势已带到的进度；手势退出最后一层后进度仍冻结在高位，
            // 同步后可直接到达目标，避免先缩回 0.94 再弹回的脉冲），保证与上一帧视觉连续。
            val gestureEff = if (secondaryStack.size <= 1) boundedProgress else 0f
            val currentEff = max(baseRestingReveal.value, gestureEff)
            if (abs(currentEff - baseRestingReveal.value) > 0.001f) {
                baseRestingReveal.snapTo(currentEff)
            }
            if (secondaryStack.isEmpty() && boundedProgress <= 0.001f) {
                // 若无手势进度直接变空（硬性关闭），立即还原为全屏无遮罩，严禁裸露 0.94 缩小态与黑色遮罩闪屏
                baseRestingReveal.snapTo(1f)
            } else if (abs(baseRevealTarget - baseRestingReveal.value) > 0.001f) {
                baseRestingReveal.animateTo(
                    targetValue = baseRevealTarget,
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                )
            }
        }
        // 手势覆盖仅在底座正下方就是顶层（栈≤1 层，含手势退出后的冻结进度）时生效
        val baseGestureOverride = if (secondaryStack.size <= 1) boundedProgress else 0f
        val baseRevealEff = max(baseRestingReveal.value, baseGestureOverride)
        val baseScale = 0.94f + 0.06f * baseRevealEff
        val baseScrimAlpha = (0.22f * (1f - baseRevealEff)).coerceIn(0f, 0.22f)

        // Tab 路由切换时（底座无悬浮页）立即完成底座还原：避免 NavHost 左右滑动转场期间
        // 与「0.94→1.0 中心缩放 + 遮罩淡出」叠加成两种效果混杂（页面边滑动边从中间向两侧展开）的观感。
        LaunchedEffect(currentRoute) {
            if (secondaryStack.isEmpty()) {
                baseRestingReveal.snapTo(1f)
            }
        }

        // 底座层缩放锚点：还原（0.94 → 1.0）从返回手势侧展开（左缘 → 锚左，右缘 → 锚右）
        val baseLayerOrigin = TransformOrigin(
            pivotFractionX = if (activeSwipeEdge == 1) 1f else 0f,
            pivotFractionY = 0.5f
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = baseScale
                    scaleY = baseScale
                    // 手势跟手阶段从手势侧展开；静止/角色切换动画从中心缩放
                    transformOrigin = if (boundedProgress > 0.001f) baseLayerOrigin else TransformOrigin.Center
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize(),
                // Tab 一级页互切只保留极短的纯淡入淡出（无左右滑动）：
                // 新页面组合/布局需要若干帧，若旧页瞬间消失会出现「先闪出上一页内容」的硬切感；
                // 120ms 交叉淡化刚好盖住合成间隙，观感仍是快速切换（二级页叠放动效不受影响）。
                enterTransition = {
                    fadeIn(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing))
                },
                exitTransition = {
                    fadeOut(animationSpec = tween(durationMillis = 90))
                },
                popEnterTransition = {
                    fadeIn(animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing))
                },
                popExitTransition = {
                    fadeOut(animationSpec = tween(durationMillis = 90))
                }
            ) {
                // 1. 首页
                composable(Screen.Home.route) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModel.Factory(
                            recordRepository = app.recordRepository,
                            preferencesRepository = app.preferencesRepository,
                            categoryRepository = app.categoryRepository
                        )
                    )
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToEdit = { recordId ->
                            secondaryStack.add(SecondaryScreen.AddEditRecord(recordId = recordId))
                        },
                        onNavigateToStatistics = {
                            secondaryStack.add(SecondaryScreen.Statistics)
                        }
                    )
                }

                // 2. 账单明细列表
                composable(Screen.RecordList.route) {
                    val listViewModel: RecordListViewModel = viewModel(
                        factory = RecordListViewModel.Factory(
                            recordRepository = app.recordRepository,
                            categoryRepository = app.categoryRepository,
                            preferencesRepository = app.preferencesRepository
                        )
                    )
                    RecordListScreen(
                        viewModel = listViewModel,
                        onNavigateToEdit = { recordId ->
                            secondaryStack.add(SecondaryScreen.AddEditRecord(recordId = recordId))
                        }
                    )
                }

                // 3. 设置 / 我的
                composable(Screen.Settings.route) {
                    val settingsViewModel: SettingsViewModel = viewModel(
                        factory = SettingsViewModel.Factory(
                            preferencesRepository = app.preferencesRepository,
                            recordRepository = app.recordRepository,
                            categoryRepository = app.categoryRepository,
                            syncManager = app.syncManager,
                            updateManager = app.updateManager
                        )
                    )
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToCategoryManage = {
                            secondaryStack.add(SecondaryScreen.CategoryManage)
                        },
                        onOpenQuickRecordSettings = {
                            secondaryStack.add(SecondaryScreen.QuickRecordSettings)
                        }
                    )
                }

                // 4. 账户 (底部导航 Tab)
                composable(Screen.Account.route) {
                    val accountViewModel: AccountViewModel = viewModel(
                        factory = AccountViewModel.Factory(
                            preferencesRepository = app.preferencesRepository,
                            recordRepository = app.recordRepository
                        )
                    )
                    AccountScreen(
                        viewModel = accountViewModel,
                        onOpenAccountDetail = { accountId ->
                            secondaryStack.add(SecondaryScreen.AccountDetail(accountId))
                        },
                        onOpenPlanDetail = { planId ->
                            secondaryStack.add(SecondaryScreen.PlanDetail(planId))
                        },
                        onOpenPaycheckRun = {
                            secondaryStack.add(SecondaryScreen.PaycheckRun)
                        },
                        onOpenAccountReconcile = {
                            secondaryStack.add(SecondaryScreen.AccountReconcile)
                        },
                        onOpenAssetPanorama = {
                            secondaryStack.add(SecondaryScreen.AssetPanorama)
                        }
                    )
                }
            }

            // 二级页手势返回的底座还原进度由各容器上报（首页直退桌面由上方 PredictiveBackHandler 处理）

            // 底座压暗遮罩层
            if (baseScrimAlpha > 0.001f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = baseScrimAlpha))
                )
            }

            // 底部导航栏（当没有二级页面悬浮时平滑展示）
            val showBottomBar = currentRoute in BottomNavTab.ALL.map { it.screen.route } && secondaryStack.isEmpty()
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 160)),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 140)),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BottomNavBar(
                    navController = navController,
                    onAddRecord = {
                        // 明细 Tab 且存在「天筛选」时，把选中日期带进新增页（补记当天账）；
                        // 其它 Tab 不受影响，仍打开默认今天
                        val filteredDay = if (currentRoute == Screen.RecordList.route) {
                            com.yuanman.app.ui.screens.list.RecordListAddBridge.selectedDayStartMillis
                        } else {
                            null
                        }
                        secondaryStack.add(
                            SecondaryScreen.AddEditRecord(
                                type = defaultRecordType,
                                recordTime = filteredDay
                            )
                        )
                    }
                )
            }
        }

        // ================= 2. 悬浮层：二级页面叠放栈（分层预测性跟手微缩卡片） =================
        secondaryStack.forEachIndexed { index, screen ->
            val isTop = index == secondaryStack.lastIndex
            val isUnder = index == secondaryStack.lastIndex - 1

            // 仅绘制顶层卡片和紧邻其下的底层卡片（保证极致流畅度）
            if (index >= secondaryStack.lastIndex - 1) {
                // 以页面实例为 key 绑定叠放层状态：层内 remember（缩放/遮罩动画等）随页面角色变化存活，
                // 避免深度叠放（如 统计→分类流水→记一笔）增删时槽位错位导致动画状态串页。
                key(screen) {
                    // 本层 reveal：1 = 全屏（顶层静止），0 = 0.94 + 自身压暗（被上层压住时的静止态）。
                    // 静止目标随「顶层/下层」角色切换平滑过渡；紧邻顶层且顶层正被手势拖动时由手势进度逐帧覆盖。
                    val layerRestingReveal = remember { Animatable(if (isTop) 1f else 0f) }
                    val layerRevealTarget = if (isTop) 1f else 0f
                    LaunchedEffect(layerRevealTarget) {
                        // 手势退出上层后进度仍冻结在高位：同步后再向目标动画，保证与上一帧视觉连续、
                        // 不做无谓回缩（0.94 → 1.0 的升起动画只应发生在「页内返回键」这类无手势路径）。
                        val gestureEff = boundedProgress
                        val currentEff = max(layerRestingReveal.value, gestureEff)
                        if (abs(currentEff - layerRestingReveal.value) > 0.001f) {
                            layerRestingReveal.snapTo(currentEff)
                        }
                        if (abs(layerRevealTarget - layerRestingReveal.value) > 0.001f) {
                            layerRestingReveal.animateTo(
                                targetValue = layerRevealTarget,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                            )
                        }
                    }
                    // 顶层手势拖动时其下层（本层若正被压住）逐帧跟随；其余时刻该覆盖恒为 0 或残留的冻结进度
                    val layerRevealEff = max(layerRestingReveal.value, boundedProgress)
                    val layerScale = 0.94f + 0.06f * layerRevealEff
                    val layerScrimAlpha = (0.22f * (1f - layerRevealEff)).coerceIn(0f, 0.22f)

                    // 手势跟手阶段的还原锚点随手势侧；静止/角色切换动画从中心缩放
                    val layerOrigin = if (isUnder && boundedProgress > 0.001f) {
                        TransformOrigin(
                            pivotFractionX = if (activeSwipeEdge == 1) 1f else 0f,
                            pivotFractionY = 0.5f
                        )
                    } else {
                        TransformOrigin.Center
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (layerScale < 0.9999f) {
                                    scaleX = layerScale
                                    scaleY = layerScale
                                    transformOrigin = layerOrigin
                                }
                            }
                    ) {
                    PredictiveSwipeBackContainer(
                        isTop = isTop,
                        onDismiss = {
                            if (index < secondaryStack.size) {
                                secondaryStack.removeAt(index)
                            }
                        },
                        onProgressChange = { p ->
                            if (isTop) {
                                topSwipeProgress = p
                                // 进度归零（手势取消 / 状态复位）时，缩放锚点还原为左缘默认
                                if (p <= 0f) activeSwipeEdge = 0
                            }
                        },
                        onEdgeChange = { edge ->
                            if (isTop) activeSwipeEdge = edge
                        }
                    ) { requestBack ->
                        when (screen) {
                            is SecondaryScreen.AddEditRecord -> {
                                val addEditViewModel: AddEditRecordViewModel = viewModel(
                                    key = "add_edit_${screen.recordId}_${screen.type}_${screen.categoryId}_${screen.nonce}",
                                    factory = AddEditRecordViewModel.Factory(
                                        recordId = screen.recordId,
                                        initialType = screen.type,
                                        initialCategoryId = screen.categoryId,
                                        initialRecordTime = screen.recordTime,
                                        recordRepository = app.recordRepository,
                                        categoryRepository = app.categoryRepository,
                                        preferencesRepository = app.preferencesRepository
                                    )
                                )
                                AddEditRecordScreen(
                                    viewModel = addEditViewModel,
                                    onNavigateBack = requestBack,
                                    onNavigateToAddCategory = { type ->
                                        secondaryStack.add(SecondaryScreen.AddEditCategory(type = type))
                                    }
                                )
                            }

                            is SecondaryScreen.Statistics -> {
                                val statsViewModel: StatisticsViewModel = viewModel(
                                    factory = StatisticsViewModel.Factory(
                                        recordRepository = app.recordRepository,
                                        categoryRepository = app.categoryRepository,
                                        preferencesRepository = app.preferencesRepository
                                    )
                                )
                                StatisticsScreen(
                                    viewModel = statsViewModel,
                                    onNavigateBack = requestBack,
                                    onCategoryClick = { categoryId ->
                                        secondaryStack.add(SecondaryScreen.CategoryRecords(categoryId))
                                    }
                                )
                            }

                            is SecondaryScreen.AssetPanorama -> {
                                val panoramaViewModel: AssetPanoramaViewModel = viewModel(
                                    factory = AssetPanoramaViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                AssetPanoramaScreen(
                                    viewModel = panoramaViewModel,
                                    onBack = requestBack,
                                    onNavigateToAccount = { accountId ->
                                        secondaryStack.add(SecondaryScreen.AccountDetail(accountId))
                                    }
                                )
                            }

                            is SecondaryScreen.PaycheckRun -> {
                                val paycheckViewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                PaycheckRunScreen(
                                    viewModel = paycheckViewModel,
                                    onBack = requestBack
                                )
                            }

                            is SecondaryScreen.AccountReconcile -> {
                                val reconcileViewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                AccountReconcileScreen(
                                    viewModel = reconcileViewModel,
                                    onBack = requestBack
                                )
                            }

                            is SecondaryScreen.QuickRecordSettings -> {
                                val quickRecordViewModel: SettingsViewModel = viewModel(
                                    factory = SettingsViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository,
                                        categoryRepository = app.categoryRepository,
                                        syncManager = app.syncManager,
                                        updateManager = app.updateManager
                                    )
                                )
                                QuickRecordSettingsScreen(
                                    viewModel = quickRecordViewModel,
                                    onBack = requestBack
                                )
                            }

                            is SecondaryScreen.CategoryManage -> {
                                val categoryViewModel: CategoryManageViewModel = viewModel(
                                    factory = CategoryManageViewModel.Factory(
                                        categoryRepository = app.categoryRepository
                                    )
                                )
                                CategoryManageScreen(
                                    viewModel = categoryViewModel,
                                    onNavigateBack = requestBack,
                                    onNavigateToAddCategory = { type ->
                                        secondaryStack.add(SecondaryScreen.AddEditCategory(type = type))
                                    },
                                    onNavigateToEditCategory = { categoryId ->
                                        secondaryStack.add(SecondaryScreen.AddEditCategory(categoryId = categoryId))
                                    }
                                )
                            }

                            is SecondaryScreen.AddEditCategory -> {
                                val addEditCategoryViewModel: AddEditCategoryViewModel = viewModel(
                                    key = "cat_edit_${screen.categoryId}_${screen.type}_${screen.nonce}",
                                    factory = AddEditCategoryViewModel.Factory(
                                        categoryId = screen.categoryId,
                                        initialType = screen.type,
                                        categoryRepository = app.categoryRepository
                                    )
                                )
                                AddEditCategoryScreen(
                                    viewModel = addEditCategoryViewModel,
                                    onNavigateBack = requestBack
                                )
                            }

                            is SecondaryScreen.AccountDetail -> {
                                val accountDetailViewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                AccountDetailScreen(
                                    viewModel = accountDetailViewModel,
                                    accountId = screen.accountId,
                                    onBack = requestBack
                                )
                            }

                            is SecondaryScreen.PlanDetail -> {
                                val planDetailViewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                PlanDetailScreen(
                                    viewModel = planDetailViewModel,
                                    planId = screen.planId,
                                    onBack = requestBack
                                )
                            }

                            is SecondaryScreen.CategoryRecords -> {
                                val categoryRecordsViewModel: CategoryRecordsViewModel = viewModel(
                                    key = "category_records_${screen.categoryId}",
                                    factory = CategoryRecordsViewModel.Factory(
                                        categoryId = screen.categoryId,
                                        recordRepository = app.recordRepository,
                                        categoryRepository = app.categoryRepository
                                    )
                                )
                                CategoryRecordsScreen(
                                    viewModel = categoryRecordsViewModel,
                                    onNavigateBack = requestBack,
                                    onNavigateToEdit = { recordId ->
                                        secondaryStack.add(SecondaryScreen.AddEditRecord(recordId = recordId))
                                    },
                                    onNavigateToAddRecord = { type, catId ->
                                        secondaryStack.add(SecondaryScreen.AddEditRecord(type = type, categoryId = catId))
                                    }
                                )
                            }
                        }
                    }

                        // 本层被压住（或正从下层角色放大还原）时的暗色遮罩：随 reveal 平滑淡入淡出
                        if (layerScrimAlpha > 0.001f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = layerScrimAlpha))
                            )
                        }
                    }
                }
            }
        }
    }
}
