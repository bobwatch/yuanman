package com.yuanman.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.yuanman.app.YuanmanApplication
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.BottomNavBar
import com.yuanman.app.ui.screens.add_edit.AddEditRecordScreen
import com.yuanman.app.ui.screens.add_edit.AddEditRecordViewModel
import com.yuanman.app.ui.screens.category.CategoryManageScreen
import com.yuanman.app.ui.screens.category.CategoryManageViewModel
import com.yuanman.app.ui.screens.detail.RecordDetailScreen
import com.yuanman.app.ui.screens.detail.RecordDetailViewModel
import com.yuanman.app.ui.screens.home.HomeScreen
import com.yuanman.app.ui.screens.home.HomeViewModel
import com.yuanman.app.ui.screens.list.RecordListScreen
import com.yuanman.app.ui.screens.list.RecordListViewModel
import com.yuanman.app.ui.screens.stats.StatisticsScreen
import com.yuanman.app.ui.screens.stats.StatisticsViewModel
import com.yuanman.app.ui.screens.settings.SettingsScreen
import com.yuanman.app.ui.screens.settings.SettingsViewModel

@Composable
fun YuanmanNavGraph(
    navController: NavHostController,
    app: YuanmanApplication,
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomBar = currentRoute in BottomNavTab.ALL.map { it.screen.route }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. 首页
            composable(Screen.Home.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(
                        recordRepository = app.recordRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )
                HomeScreen(
                    viewModel = homeViewModel,
                    onNavigateToAddRecord = { type ->
                        navController.navigate(Screen.AddEditRecord.createRoute(type = type))
                    },
                    onNavigateToDetail = { recordId ->
                        navController.navigate(Screen.RecordDetail.createRoute(recordId))
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
                        categoryRepository = app.categoryRepository
                    )
                )
                RecordListScreen(
                    viewModel = listViewModel,
                    onNavigateToDetail = { recordId ->
                        navController.navigate(Screen.RecordDetail.createRoute(recordId))
                    }
                )
            }

            // 3. 数据统计 (由首页点击卡片进入)
            composable(Screen.Statistics.route) {
                val statsViewModel: StatisticsViewModel = viewModel(
                    factory = StatisticsViewModel.Factory(
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository
                    )
                )
                StatisticsScreen(
                    viewModel = statsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 4. 分类管理
            composable(Screen.CategoryManage.route) {
                val categoryViewModel: CategoryManageViewModel = viewModel(
                    factory = CategoryManageViewModel.Factory(
                        categoryRepository = app.categoryRepository
                    )
                )
                CategoryManageScreen(viewModel = categoryViewModel)
            }

            // 5. 设置
            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.Factory(
                        preferencesRepository = app.preferencesRepository,
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository
                    )
                )
                SettingsScreen(viewModel = settingsViewModel)
            }

            // 6. 新增 / 编辑账单
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
                    }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                val typeStr = backStackEntry.arguments?.getString("type") ?: ""
                val initialType = if (typeStr.isNotBlank()) RecordType.fromString(typeStr) else null

                val addEditViewModel: AddEditRecordViewModel = viewModel(
                    key = "add_edit_$recordId",
                    factory = AddEditRecordViewModel.Factory(
                        recordId = recordId,
                        initialType = initialType,
                        recordRepository = app.recordRepository,
                        categoryRepository = app.categoryRepository,
                        preferencesRepository = app.preferencesRepository
                    )
                )

                AddEditRecordScreen(
                    viewModel = addEditViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 7. 账单详情
            composable(
                route = Screen.RecordDetail.route,
                arguments = listOf(
                    navArgument("recordId") {
                        type = NavType.LongType
                    }
                )
            ) { backStackEntry ->
                val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L

                val detailViewModel: RecordDetailViewModel = viewModel(
                    key = "detail_$recordId",
                    factory = RecordDetailViewModel.Factory(
                        recordId = recordId,
                        recordRepository = app.recordRepository
                    )
                )

                RecordDetailScreen(
                    viewModel = detailViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToEdit = { editRecordId ->
                        navController.navigate(Screen.AddEditRecord.createRoute(recordId = editRecordId))
                    }
                )
            }
        }

        if (showBottomBar) {
            BottomNavBar(
                navController = navController,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
