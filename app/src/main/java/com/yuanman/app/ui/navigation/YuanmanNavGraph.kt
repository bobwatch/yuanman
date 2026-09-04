package com.yuanman.app.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.yuanman.app.YuanmanApplication
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.BottomNavBar
import com.yuanman.app.ui.screens.accounts.AccountsScreen
import com.yuanman.app.ui.screens.accounts.AccountStatisticsScreen
import com.yuanman.app.ui.screens.accounts.AccountsViewModel
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
import com.yuanman.app.ui.screens.settings.SettingsViewModel
import com.yuanman.app.ui.screens.stats.CategoryRecordsScreen
import com.yuanman.app.ui.screens.stats.CategoryRecordsViewModel
import com.yuanman.app.ui.screens.stats.StatisticsScreen
import com.yuanman.app.ui.screens.stats.StatisticsViewModel

private val TAB_ROUTES = listOf(
    Screen.Home.route,
    Screen.RecordList.route,
    Screen.Accounts.route,
    Screen.Settings.route
)

/**
 * 账户统计页返回时，经“账户 Tab 入口”SavedStateHandle 回传的待办动作键。
 *
 * 账户 Tab 与统计页各自通过 viewModel() 创建独立的 AccountsViewModel，转账/对账弹窗
 * 开关属于 VM 内存态：在统计页自己的 VM 上置位会随页面销毁而丢失（回主页后永不弹窗）。
 * 因此统计页的 CTA 只负责把意图写入下方账户入口的 SavedStateHandle，账户页每次进入
 * （含从统计页返回）时消费一次并立即清除，避免下次进入重复触发。
 */
private const val KEY_PENDING_ACCOUNT_ACTION_TRANSFER_TO = "pending_account_action_transfer_to"
private const val KEY_PENDING_ACCOUNT_ACTION_RECONCILE = "pending_account_action_reconcile"

@Composable
fun YuanmanNavGraph(
    navController: NavHostController,
    app: YuanmanApplication,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val defaultRecordType by app.preferencesRepository.defaultRecordType.collectAsState(initial = RecordType.EXPENSE)

    val showBottomBar = currentRoute in BottomNavTab.ALL.map { it.screen.route }

    Box(modifier = modifier.fillMaxSize()) {
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
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(durationMillis = 180))
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
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -fullWidth / 3 },
                        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                    ) + fadeOut(animationSpec = tween(durationMillis = 160))
                }
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 3 },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(durationMillis = 180))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(durationMillis = 160))
            }
        ) {
            // 1. 首页
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        recordRepository = app.recordRepository,
                        preferencesRepository = app.preferencesRepository,
                        categoryRepository = app.categoryRepository,
                        accountRepository = app.accountRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToEdit = { recordId ->
                        navController.navigate(Screen.AddEditRecord.createRoute(recordId = recordId))
                    },
                    onNavigateToStatistics = {
                        navController.navigate(Screen.Statistics.route)
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
                        navController.navigate(Screen.AddEditRecord.createRoute(recordId = recordId))
                    }
                )
            }

            // 3. 我的账户与资产管理
            composable(Screen.Accounts.route) { backStackEntry ->
                val accountsViewModel: AccountsViewModel = viewModel(
                    factory = AccountsViewModel.Factory(
                        accountRepository = app.accountRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )

                // 消费统计页回传的“去还款/立即对账”待办（每次进入本页读取一次并清除，
                // 避免残留值在后续再次进入时重复弹出弹窗）
                LaunchedEffect(Unit) {
                    val pendingTransferToId = backStackEntry.savedStateHandle
                        .get<Long>(KEY_PENDING_ACCOUNT_ACTION_TRANSFER_TO)
                    if (pendingTransferToId != null) {
                        backStackEntry.savedStateHandle.remove<Long>(KEY_PENDING_ACCOUNT_ACTION_TRANSFER_TO)
                        accountsViewModel.openTransfer(toId = pendingTransferToId)
                    }
                    val pendingReconcile = backStackEntry.savedStateHandle
                        .get<Boolean>(KEY_PENDING_ACCOUNT_ACTION_RECONCILE)
                    if (pendingReconcile == true) {
                        backStackEntry.savedStateHandle.remove<Boolean>(KEY_PENDING_ACCOUNT_ACTION_RECONCILE)
                        accountsViewModel.openReconciliation()
                    }
                }

                AccountsScreen(
                    viewModel = accountsViewModel,
                    onNavigateToAccountStats = {
                        navController.navigate(Screen.AccountStatistics.route)
                    }
                )
            }

            // 3.1 资产与账户统计分析大屏
            composable(Screen.AccountStatistics.route) {
                val accountsViewModel: AccountsViewModel = viewModel(
                    factory = AccountsViewModel.Factory(
                        accountRepository = app.accountRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )
                // 统计页只可能从账户 Tab 进入：CTA 先把待办动作写入其下方账户入口的
                // SavedStateHandle，再执行返回（账户页仍在 back stack 中存活并会消费它）
                val accountsEntry = navController.previousBackStackEntry
                AccountStatisticsScreen(
                    viewModel = accountsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onTransferToAccount = { targetId ->
                        accountsEntry?.savedStateHandle?.set(KEY_PENDING_ACCOUNT_ACTION_TRANSFER_TO, targetId)
                        navController.popBackStack()
                    },
                    onReconcile = {
                        accountsEntry?.savedStateHandle?.set(KEY_PENDING_ACCOUNT_ACTION_RECONCILE, true)
                        navController.popBackStack()
                    }
                )
            }

            // 4. 数据统计
            composable(Screen.Statistics.route) {
                val statsViewModel: StatisticsViewModel = viewModel(
                    factory = StatisticsViewModel.Factory(
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )
                StatisticsScreen(
                    viewModel = statsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onCategoryClick = { categoryId ->
                        navController.navigate(Screen.CategoryRecords.createRoute(categoryId))
                    }
                )
            }

            // 5. 分类管理
            composable(Screen.CategoryManage.route) {
                val categoryViewModel: CategoryManageViewModel = viewModel(
                    factory = CategoryManageViewModel.Factory(
                        categoryRepository = app.categoryRepository
                    )
                )
                CategoryManageScreen(
                    viewModel = categoryViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAddCategory = { type ->
                        navController.navigate(Screen.AddEditCategory.createRoute(type = type))
                    },
                    onNavigateToEditCategory = { categoryId ->
                        navController.navigate(Screen.AddEditCategory.createRoute(categoryId = categoryId))
                    }
                )
            }

            // 6. 设置 / 我的
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        preferencesRepository = app.preferencesRepository,
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository,
                        accountRepository = app.accountRepository,
                        database = app.database,
                        syncManager = app.syncManager,
                        updateManager = app.updateManager
                    )
                )
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToCategoryManage = {
                        navController.navigate(Screen.CategoryManage.route)
                    }
                )
            }

            // 7. 新增 / 编辑账单
            composable(
                route = Screen.AddEditRecord.route,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                    navArgument("categoryId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                val typeStr = backStackEntry.arguments?.getString("type") ?: ""
                val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
                val initialType = if (typeStr.isNotBlank()) RecordType.fromString(typeStr) else null

                val addEditViewModel: AddEditRecordViewModel = viewModel(
                    key = "add_edit_${recordId}_${typeStr}_$categoryId",
                    factory = AddEditRecordViewModel.Factory(
                        recordId = recordId,
                        initialType = initialType,
                        initialCategoryId = categoryId,
                        recordRepository = app.recordRepository,
                        accountRepository = app.accountRepository,
                        categoryRepository = app.categoryRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )

                AddEditRecordScreen(
                    viewModel = addEditViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToCategoryManage = {
                        navController.navigate(Screen.CategoryManage.route)
                    }
                )
            }

            // 8. 新增 / 编辑分类及其子标签
            composable(
                route = Screen.AddEditCategory.route,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
                val typeStr = backStackEntry.arguments?.getString("type") ?: ""
                val initialType = if (typeStr.isNotBlank()) RecordType.fromString(typeStr) else null

                val addEditCategoryViewModel: AddEditCategoryViewModel = viewModel(
                    key = "cat_edit_${categoryId}_$typeStr",
                    factory = AddEditCategoryViewModel.Factory(
                        categoryId = categoryId,
                        initialType = initialType,
                        categoryRepository = app.categoryRepository
                    )
                )

                AddEditCategoryScreen(
                    viewModel = addEditCategoryViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9. 分类账单详情
            composable(
                route = Screen.CategoryRecords.route,
                arguments = listOf(
                    navArgument("categoryId") {
                        type = NavType.LongType
                        defaultValue = 0L
                    }
                )
            ) { backStackEntry ->
                val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: 0L
                val categoryRecordsViewModel: CategoryRecordsViewModel = viewModel(
                    key = "category_records_$categoryId",
                    factory = CategoryRecordsViewModel.Factory(
                        categoryId = categoryId,
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository
                    )
                )
                CategoryRecordsScreen(
                    viewModel = categoryRecordsViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { recordId ->
                        navController.navigate(Screen.AddEditRecord.createRoute(recordId = recordId))
                    },
                    onNavigateToAddRecord = { type, catId ->
                        navController.navigate(Screen.AddEditRecord.createRoute(type = type, categoryId = catId))
                    }
                )
            }
        }

        if (showBottomBar) {
            BottomNavBar(
                navController = navController,
                onAddRecord = {
                    navController.navigate(
                        Screen.AddEditRecord.createRoute(type = defaultRecordType)
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
