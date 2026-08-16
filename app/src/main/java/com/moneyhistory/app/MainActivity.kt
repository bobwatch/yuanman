package com.moneyhistory.app

import android.content.Intent
import android.os.Bundle
import androidx.core.view.WindowCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.moneyhistory.app.ui.theme.incomeAmountColor

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector
)

// M3 规范：未选中用描边图标，选中用实心图标（同形切换，切页时 Crossfade 过渡）
private val bottomTabs = listOf(
    BottomTab(
        "home", R.string.tab_record,
        Icons.Outlined.Receipt, Icons.Filled.Receipt
    ),
    BottomTab(
        "habits", R.string.tab_habits,
        Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle
    ),
    BottomTab(
        "mood", R.string.tab_mood,
        Icons.Outlined.Face, Icons.Filled.Face
    ),
    BottomTab(
        "mine", R.string.tab_mine,
        Icons.Outlined.Person, Icons.Filled.Person
    )
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
        // 全面屏：内容延伸到状态栏/导航栏后面（各页自绘页头渐变 + insets padding 隔离内容）。
        // 在首帧组合之前设置，避免 Compose 挂载后再切换导致布局跳一下
        WindowCompat.setDecorFitsSystemWindows(window, false)
        handleIntent(intent)
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.settings.themeMode.collectAsStateWithLifecycle()
            val onboardingSeen by viewModel.settings.onboardingSeen
                .collectAsStateWithLifecycle()
            val confettiVisible by viewModel.confettiVisible.collectAsStateWithLifecycle()
            val successNonce by viewModel.successNonce.collectAsStateWithLifecycle()
            val successTone by viewModel.successTone.collectAsStateWithLifecycle()
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
                                        val selected = currentRoute == tab.route
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = {
                                                // 重复点击当前 Tab：通知页面滚回顶部（微信/支付宝式）
                                                if (selected) {
                                                    viewModel.onTabReclick(tab.route)
                                                } else {
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
                                                }
                                            },
                                            icon = {
                                                // 描边 ⇄ 实心淡切：选中状态变化不「干跳」
                                                Crossfade(
                                                    targetState = selected,
                                                    animationSpec = tween(220),
                                                    label = "tabIcon"
                                                ) { isSelected ->
                                                    Icon(
                                                        if (isSelected) {
                                                            tab.selectedIcon
                                                        } else {
                                                            tab.icon
                                                        },
                                                        contentDescription = tabLabel,
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }
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
                                        onBack = { navController.popBackStack() },
                                        toastHostState = toastHostState
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

                        // 记账 / 打卡 / 存入成功的全局对勾动效：任何页面都可见；
                        // 收入记绿勾（进账的喜悦感），其余动作记品牌蓝勾
                        SuccessOverlay(
                            trigger = successNonce,
                            tint = when (successTone) {
                                SuccessTone.INCOME -> incomeAmountColor()
                                SuccessTone.DEFAULT -> MaterialTheme.colorScheme.primary
                            }
                        )

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
                            onDismiss = { viewModel.dismissUpdate() },
                            onCancel = { viewModel.cancelUpdate() }
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

/** 升级弹窗：新版本提示 + 下载中进度（可取消）。 */
@Composable
private fun UpdateDialog(
    updateState: UpdateState,
    onDownload: () -> Unit,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    when (updateState) {
        UpdateState.Idle -> Unit
        is UpdateState.Downloading -> {
            val state = updateState
            // total 未知时（服务器未给大小）只显示转圈；有大小才显示进度条与百分比
            val percent = if (state.totalBytes > 0) {
                (state.downloadedBytes * 100 / state.totalBytes)
                    .toInt()
                    .coerceIn(0, 100)
            } else {
                null
            }
            AlertDialog(
                // 返回键 / 点外部 = 取消下载（不再是无出口的无限转圈）
                onDismissRequest = onCancel,
                icon = {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                },
                title = { Text(stringResource(R.string.update_downloading)) },
                text = {
                    if (percent != null) {
                        Column {
                            LinearProgressIndicator(
                                progress = { percent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = stringResource(
                                    R.string.update_download_progress,
                                    percent
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {},
                dismissButton = {
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.update_cancel))
                    }
                }
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
