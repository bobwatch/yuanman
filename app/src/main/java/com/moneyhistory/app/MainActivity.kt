package com.moneyhistory.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneyhistory.app.ui.BadgeScreen
import com.moneyhistory.app.ui.CategoriesScreen
import com.moneyhistory.app.ui.ConfettiOverlay
import com.moneyhistory.app.ui.FamilySyncScreen
import com.moneyhistory.app.ui.GoalDetailScreen
import com.moneyhistory.app.ui.HabitScreen
import com.moneyhistory.app.ui.HomeScreen
import com.moneyhistory.app.ui.MineScreen
import com.moneyhistory.app.ui.MoodScreen
import com.moneyhistory.app.ui.OnboardingScreen
import com.moneyhistory.app.ui.RecurringScreen
import com.moneyhistory.app.ui.StatsScreen
import com.moneyhistory.app.ui.theme.YuanmanTheme

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomTabs = listOf(
    BottomTab("home", R.string.tab_record, Icons.Filled.Receipt),
    BottomTab("habits", R.string.tab_habits, Icons.Filled.CheckCircle),
    BottomTab("mood", R.string.tab_mood, Icons.Filled.Face),
    BottomTab("mine", R.string.tab_mine, Icons.Filled.Person)
)

class MainActivity : ComponentActivity() {

    /** 桌面快捷方式 / Widget「记一笔」直达标记。 */
    private val openAddRequest = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
            val onboardingSeen by viewModel.settings.onboardingSeen
                .collectAsStateWithLifecycle()
            val confettiVisible by viewModel.confettiVisible.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            YuanmanTheme(darkTheme = darkTheme) {
                if (!onboardingSeen) {
                    OnboardingScreen(
                        onFinish = { viewModel.settings.setOnboardingSeen() }
                    )
                } else {
                    val navController = rememberNavController()
                    val snackbarHostState = remember { SnackbarHostState() }

                    // 顶层统一 Snackbar（同步 / 周期账单 / 勋章解锁等提示）
                    LaunchedEffect(Unit) {
                        viewModel.messages.collect { msg ->
                            snackbarHostState.showSnackbar(msg)
                        }
                    }

                    // 前台启动家庭同步并结算周期账单，退后台停止
                    val lifecycleOwner = LocalLifecycleOwner.current
                    DisposableEffect(lifecycleOwner) {
                        val observer = LifecycleEventObserver { _, event ->
                            when (event) {
                                Lifecycle.Event.ON_START -> viewModel.onForeground()
                                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                                else -> Unit
                            }
                        }
                        lifecycleOwner.lifecycle.addObserver(observer)
                        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                    }

                    Box(Modifier.fillMaxSize()) {
                        Scaffold(
                            snackbarHost = {
                                // 避开底部导航 / FAB 区域
                                SnackbarHost(
                                    snackbarHostState,
                                    modifier = Modifier.padding(bottom = 80.dp)
                                )
                            },
                            bottomBar = {
                                NavigationBar {
                                    val backStackEntry by navController
                                        .currentBackStackEntryAsState()
                                    val currentRoute =
                                        backStackEntry?.destination?.route
                                    bottomTabs.forEach { tab ->
                                        val tabLabel = stringResource(tab.labelRes)
                                        NavigationBarItem(
                                            selected = currentRoute == tab.route,
                                            onClick = {
                                                navController.navigate(tab.route) {
                                                    popUpTo(
                                                        navController.graph
                                                            .findStartDestination().id
                                                    ) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = {
                                                Icon(
                                                    tab.icon,
                                                    contentDescription = tabLabel
                                                )
                                            },
                                            label = { Text(tabLabel) }
                                        )
                                    }
                                }
                            }
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier.padding(padding),
                                enterTransition = { fadeIn(tween(250)) },
                                exitTransition = { fadeOut(tween(200)) },
                                popEnterTransition = { fadeIn(tween(250)) },
                                popExitTransition = { fadeOut(tween(200)) }
                            ) {
                                composable("home") {
                                    HomeScreen(
                                        viewModel = viewModel,
                                        onNavigateToStats = {
                                            navController.navigate("stats")
                                        },
                                        onNavigateToGoal = { id ->
                                            navController.navigate("goal_detail/$id")
                                        },
                                        openAddRequest = openAddRequest.value,
                                        onAddRequestHandled = {
                                            openAddRequest.value = false
                                        }
                                    )
                                }
                                composable("habits") {
                                    HabitScreen(viewModel = viewModel)
                                }
                                composable("mood") {
                                    MoodScreen(viewModel = viewModel)
                                }
                                composable("mine") {
                                    MineScreen(
                                        viewModel = viewModel,
                                        onNavigateToBadges = {
                                            navController.navigate("badges")
                                        },
                                        onNavigateToFamily = {
                                            navController.navigate("family")
                                        },
                                        onNavigateToRecurring = {
                                            navController.navigate("recurring")
                                        },
                                        onNavigateToCategories = {
                                            navController.navigate("categories")
                                        }
                                    )
                                }
                                // 兼容保留的旧入口路由
                                composable("settings") {
                                    MineScreen(
                                        viewModel = viewModel,
                                        onNavigateToBadges = {
                                            navController.navigate("badges")
                                        },
                                        onNavigateToFamily = {
                                            navController.navigate("family")
                                        },
                                        onNavigateToRecurring = {
                                            navController.navigate("recurring")
                                        },
                                        onNavigateToCategories = {
                                            navController.navigate("categories")
                                        }
                                    )
                                }
                                composable("badges") {
                                    BadgeScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("stats") {
                                    StatsScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable(
                                    route = "goal_detail/{goalId}",
                                    arguments = listOf(
                                        navArgument("goalId") {
                                            type = NavType.StringType
                                        }
                                    )
                                ) { entry ->
                                    GoalDetailScreen(
                                        viewModel = viewModel,
                                        goalId = entry.arguments
                                            ?.getString("goalId")
                                            .orEmpty(),
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("family") {
                                    FamilySyncScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("recurring") {
                                    RecurringScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                                composable("categories") {
                                    CategoriesScreen(
                                        viewModel = viewModel,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }

                        // 勋章解锁 / 目标达成的全屏撒花
                        ConfettiOverlay(
                            visible = confettiVisible,
                            onFinished = { viewModel.dismissConfetti() }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getStringExtra(EXTRA_ACTION) == ACTION_ADD) {
            openAddRequest.value = true
        }
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val ACTION_ADD = "add"
    }
}
