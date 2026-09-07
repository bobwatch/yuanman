package com.yuanman.app.ui.navigation

import android.app.Activity
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.yuanman.app.ui.screens.account.AccountReconcileScreen
import com.yuanman.app.ui.screens.account.AccountScreen
import com.yuanman.app.ui.screens.account.AccountViewModel
import com.yuanman.app.ui.screens.account.PaycheckRunScreen
import com.yuanman.app.ui.screens.account.SavingPlansScreen
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
import com.yuanman.app.ui.screens.stats.StatisticsScreen
import com.yuanman.app.ui.screens.stats.StatisticsViewModel

/**
 * 二级页面路由目标定义（用于分层预测性返回叠放栈）
 */
sealed class SecondaryScreen {
    data class AddEditRecord(
        val recordId: Long = 0L,
        val type: RecordType? = null,
        val categoryId: Long = 0L
    ) : SecondaryScreen()

    data class AddEditCategory(
        val categoryId: Long = 0L,
        val type: RecordType? = null
    ) : SecondaryScreen()

    data class CategoryRecords(
        val categoryId: Long
    ) : SecondaryScreen()

    object CategoryManage : SecondaryScreen()
    object Statistics : SecondaryScreen()

    /** v0.3：攒钱计划二级页（方案编辑 / 计划管理 / 存撤专款） */
    object SavingPlans : SecondaryScreen()

    /** v0.0.4：发薪分配 / 账户核对 / 快捷记账设置 二级页（账户页 ⋮ 菜单与设置页入口） */
    object PaycheckRun : SecondaryScreen()
    object AccountReconcile : SecondaryScreen()
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
        if (route.startsWith("add_edit_record") || route.startsWith("statistics") || route.startsWith("category_manage")) {
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
                route.startsWith("category_manage") -> {
                    secondaryStack.add(SecondaryScreen.CategoryManage)
                }
            }
            navController.popBackStack()
        }
    }

    val context = LocalContext.current
    val activity = context as? Activity

    // 一级页（无二级页悬浮）返回 = 连续两次返回退出应用回桌面。
    // 用预测性返回「静默消费」手势进度：不绘制页面级返回动画，也抑制系统默认
    // 「整页缩放退回桌面」预览；tab 之间切换不记返回栈（单条目栈，见 BottomNavBar），
    // 因此任意一级页的返回都落到这里：第一次提示，2 秒内再按一次才真正退出。
    var lastRootBackMillis by remember { mutableStateOf(0L) }
    val toast = LocalToastHostState.current
    PredictiveBackHandler(enabled = isAtRoot) { progressFlow ->
        progressFlow.collect { /* 静默消费进度：根层退出不做页面动画 */ }
        val now = System.currentTimeMillis()
        if (now - lastRootBackMillis <= 2000L) {
            activity?.finishAffinity()
        } else {
            lastRootBackMillis = now
            toast.info("再按一次返回退出应用")
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
        // TabBar 页面之间采用项目原始的左滑/右滑平移切换方案
        // 当有二级页面悬浮时，底座层缩小至 0.94 并压暗 22%；二级页面手势返回时等比放大回 1.0，遮罩褪去
        val boundedProgress = topSwipeProgress.coerceIn(0f, 1f)
        val baseScale = if (secondaryStack.size == 1) {
            (0.94f + boundedProgress * 0.06f).coerceIn(0.94f, 1.0f)
        } else if (secondaryStack.size > 1) {
            0.94f
        } else {
            1.0f
        }

        val baseScrimAlpha = if (secondaryStack.size == 1) {
            (0.22f * (1f - boundedProgress)).coerceIn(0f, 0.22f)
        } else if (secondaryStack.size > 1) {
            0.22f
        } else {
            0f
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
                    transformOrigin = baseLayerOrigin
                }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize(),
                enterTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val fromIndex = TAB_ROUTES.indexOf(fromRoute)
                    val toIndex = TAB_ROUTES.indexOf(toRoute)

                    if (fromIndex != -1 && toIndex != -1) {
                        if (toIndex > fromIndex) {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> (fullWidth * 0.45f).toInt() },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(durationMillis = 180))
                        } else {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> (-fullWidth * 0.45f).toInt() },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            ) + fadeIn(animationSpec = tween(durationMillis = 180))
                        }
                    } else {
                        fadeIn(animationSpec = tween(durationMillis = 180))
                    }
                },
                exitTransition = {
                    val fromRoute = initialState.destination.route
                    val toRoute = targetState.destination.route
                    val fromIndex = TAB_ROUTES.indexOf(fromRoute)
                    val toIndex = TAB_ROUTES.indexOf(toRoute)

                    if (fromIndex != -1 && toIndex != -1) {
                        if (toIndex > fromIndex) {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (-fullWidth * 0.45f).toInt() },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(durationMillis = 160))
                        } else {
                            slideOutHorizontally(
                                targetOffsetX = { fullWidth -> (fullWidth * 0.45f).toInt() },
                                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                            ) + fadeOut(animationSpec = tween(durationMillis = 160))
                        }
                    } else {
                        fadeOut(animationSpec = tween(durationMillis = 160))
                    }
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
                        onOpenSavingPlans = {
                            secondaryStack.add(SecondaryScreen.SavingPlans)
                        },
                        onOpenPaycheckRun = {
                            secondaryStack.add(SecondaryScreen.PaycheckRun)
                        },
                        onOpenAccountReconcile = {
                            secondaryStack.add(SecondaryScreen.AccountReconcile)
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
                        secondaryStack.add(SecondaryScreen.AddEditRecord(type = defaultRecordType))
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
                val boundedP = topSwipeProgress.coerceIn(0f, 1f)
                val underScale = if (isUnder) (0.94f + boundedP * 0.06f).coerceIn(0.94f, 1.0f) else 1.0f
                val underScrim = if (isUnder) (0.22f * (1f - boundedP)).coerceIn(0f, 0.22f) else 0f

                // 下层页面的还原（0.94 → 1.0）同样从返回手势侧展开
                val underLayerOrigin = TransformOrigin(
                    pivotFractionX = if (activeSwipeEdge == 1) 1f else 0f,
                    pivotFractionY = 0.5f
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isUnder) {
                                scaleX = underScale
                                scaleY = underScale
                                transformOrigin = underLayerOrigin
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
                    ) {
                        when (screen) {
                            is SecondaryScreen.AddEditRecord -> {
                                val addEditViewModel: AddEditRecordViewModel = viewModel(
                                    key = "add_edit_${screen.recordId}_${screen.type}_${screen.categoryId}",
                                    factory = AddEditRecordViewModel.Factory(
                                        recordId = screen.recordId,
                                        initialType = screen.type,
                                        initialCategoryId = screen.categoryId,
                                        recordRepository = app.recordRepository,
                                        categoryRepository = app.categoryRepository,
                                        preferencesRepository = app.preferencesRepository
                                    )
                                )
                                AddEditRecordScreen(
                                    viewModel = addEditViewModel,
                                    onNavigateBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    },
                                    onNavigateToCategoryManage = {
                                        secondaryStack.add(SecondaryScreen.CategoryManage)
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
                                    onNavigateBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    },
                                    onCategoryClick = { categoryId ->
                                        secondaryStack.add(SecondaryScreen.CategoryRecords(categoryId))
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
                                    onBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    }
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
                                    onBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    }
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
                                    onBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    }
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
                                    onNavigateBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    },
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
                                    key = "cat_edit_${screen.categoryId}_${screen.type}",
                                    factory = AddEditCategoryViewModel.Factory(
                                        categoryId = screen.categoryId,
                                        initialType = screen.type,
                                        categoryRepository = app.categoryRepository
                                    )
                                )
                                AddEditCategoryScreen(
                                    viewModel = addEditCategoryViewModel,
                                    onNavigateBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    }
                                )
                            }

                            is SecondaryScreen.SavingPlans -> {
                                val savingPlansViewModel: AccountViewModel = viewModel(
                                    factory = AccountViewModel.Factory(
                                        preferencesRepository = app.preferencesRepository,
                                        recordRepository = app.recordRepository
                                    )
                                )
                                SavingPlansScreen(
                                    viewModel = savingPlansViewModel,
                                    onBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    }
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
                                    onNavigateBack = {
                                        if (secondaryStack.isNotEmpty()) secondaryStack.removeLast()
                                    },
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

                    // 底下那一层的暗色遮罩
                    if (isUnder && underScrim > 0.001f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = underScrim))
                        )
                    }
                }
            }
        }
    }
}
