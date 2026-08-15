package com.moneyhistory.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.moneyhistory.app.UpdateState
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
import com.moneyhistory.app.ui.SuccessOverlay
import com.moneyhistory.app.ui.ToastHost
import com.moneyhistory.app.ui.ToastHostState
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

private val tabRoutes = bottomTabs.map { it.route }

/** 前进方向：Tab 之间按索引左右滑；进入子页统一向左滑（新页从右进入）。 */
private fun navEnter(from: String?, to: String?): EnterTransition {
    val fi = tabRoutes.indexOf(from)
    val ti = tabRoutes.indexOf(to)
    return if (fi >= 0 && ti >= 0 && fi != ti) {
        if (ti > fi) slideInHorizontally { it } + fadeIn() else slideInHorizontally { -it } + fadeIn()
    } else {
        slideInHorizontally { it } + fadeIn()
    }
}

/** 后退方向：当前页向左退出（配合返回时从右弹回）。 */
private fun navExit(from: String?, to: String?): ExitTransition {
    val fi = tabRoutes.indexOf(from)
    val ti = tabRoutes.indexOf(to)
    return if (fi >= 0 && ti >= 0 && fi != ti) {
        if (ti > fi) slideOutHorizontally { -it } + fadeOut() else slideOutHorizontally { it } + fadeOut()
    } else {
        slideOutHorizontally { -it } + fadeOut()
    }
}

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
            val successNonce by viewModel.successNonce.collectAsStateWithLifecycle()
            val updateState by viewModel.updateState.collectAsStateWithLifecycle()
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
                    val toastHostState = remember { ToastHostState() }

                    // 全局 Toast（同步 / 周期账单 / 勋章解锁 / 升级等提示）
                    LaunchedEffect(Unit) {
                        viewModel.messages.collect { msg ->
                            toastHostState.show(msg.text, msg.variant)
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
                        // 当前路由决定底部导航栏是否显示：Tab 页显示，子页（统计/详情等）隐藏
                        val backStackEntry by navController
                            .currentBackStackEntryAsState()
                        val currentRoute = backStackEntry?.destination?.route
                        val showBottomBar =
                            currentRoute == null || currentRoute in tabRoutes
                        Scaffold(
                            // 各页面自绘渐变页头（含状态栏），外层不再叠加系统 insets
                            contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
                            bottomBar = {
                                if (showBottomBar) {
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 0.dp
                                    ) {
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
                                                    contentDescription = tabLabel,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            },
                                            label = {
                                                Text(
                                                    tabLabel,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (currentRoute == tab.route) {
                                                        FontWeight.Bold
                                                    } else {
                                                        FontWeight.Medium
                                                    }
                                                )
                                            },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme
                                                    .colorScheme.primary,
                                                selectedTextColor = MaterialTheme
                                                    .colorScheme.primary,
                                                unselectedIconColor = MaterialTheme
                                                    .colorScheme.onSurfaceVariant,
                                                unselectedTextColor = MaterialTheme
                                                    .colorScheme.onSurfaceVariant,
                                                indicatorColor = MaterialTheme
                                                    .colorScheme.primaryContainer
                                            ),
                                            modifier = Modifier
                                                .padding(horizontal = 6.dp)
                                                .clip(
                                                    RoundedCornerShape(
                                                        topStart = 16.dp,
                                                        topEnd = 16.dp
                                                    )
                                                )
                                        )
                                    }
                                }
                                }
                            }
                        ) { padding ->
                            NavHost(
                                navController = navController,
                                startDestination = "home",
                                modifier = Modifier
                                    .padding(padding)
                                    .background(MaterialTheme.colorScheme.background),
                                // 按方向滑动切换：Tab 之间按索引左右滑，进入子页面向左滑、返回向右滑
                                enterTransition = {
                                    navEnter(initialState.destination.route, targetState.destination.route)
                                },
                                exitTransition = {
                                    navExit(initialState.destination.route, targetState.destination.route)
                                },
                                popEnterTransition = { slideInHorizontally { -it } },
                                popExitTransition = { slideOutHorizontally { it } }
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
                                        },
                                        toastHostState = toastHostState
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

                        // 记账 / 打卡 / 存入成功的全局对勾动效：任何页面都可见
                        SuccessOverlay(trigger = successNonce)

                        // 全局 Toast：顶部向下一点，避开状态栏
                        ToastHost(
                            state = toastHostState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        )

                        // 在线升级：新版本弹窗 + 下载中进度
                        UpdateDialog(
                            updateState = updateState,
                            onDownload = { viewModel.downloadUpdate() },
                            onDismiss = { viewModel.dismissUpdate() }
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

/** 升级弹窗：新版本提示 + 下载中进度。 */
@Composable
private fun UpdateDialog(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit
) {
    when (updateState) {
        UpdateState.Idle -> Unit
        UpdateState.Downloading -> {
            AlertDialog(
                onDismissRequest = {},
                icon = {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                },
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {},
                confirmButton = {}
            )
        }
        is UpdateState.Available -> {
            val info = updateState.info
            AlertDialog(
                onDismissRequest = onDismiss,
                icon = {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                title = { Text(stringResource(R.string.update_title)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            text = stringResource(R.string.update_version, info.versionName),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (info.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = info.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDownload) {
                        Text(stringResource(R.string.update_download))
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.update_later))
                    }
                }
            )
        }
    }
}
