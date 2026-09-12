package com.yuanman.app

import android.graphics.Color
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.navigation.compose.rememberNavController
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.ui.components.AppUpdateDialog
import com.yuanman.app.ui.components.LocalToastHostState
import com.yuanman.app.ui.components.PrimeIconPainters
import com.yuanman.app.ui.components.ToastHostState
import com.yuanman.app.ui.components.TopToastHost
import com.yuanman.app.ui.navigation.YuanmanNavGraph
import com.yuanman.app.ui.theme.YuanmanTheme
import com.yuanman.app.utils.UpdateState
import com.yuanman.app.widget.WidgetNavigation

import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.yuanman.app.data.local.DatabaseBackupManager
import com.yuanman.app.ui.components.FirstLaunchRestoreDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingWidgetRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        super.onCreate(savedInstanceState)
        pendingWidgetRoute = intent.getStringExtra(WidgetNavigation.EXTRA_ROUTE)

        val app = application as YuanmanApplication

        setContent {
            val scope = rememberCoroutineScope()
            val themeMode by app.preferencesRepository.themeMode.collectAsState(initial = ThemeMode.SYSTEM)
            val toastHostState = remember { ToastHostState() }
            val updateState by app.updateManager.updateState.collectAsState()
            val showUpdatePrompt by app.updateManager.showUpdatePrompt.collectAsState()

            // 首次安装后启动：检测历史账本状态
            var showFirstLaunchDialog by remember { mutableStateOf(false) }
            var firstLaunchNeedsPermission by remember { mutableStateOf(false) }
            var historicalBackupInfo by remember { mutableStateOf<DatabaseBackupManager.HistoricalBackupInfo?>(null) }
            var isRestoringFirstLaunch by remember { mutableStateOf(false) }

            val firstLaunchPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) {
                if (DatabaseBackupManager.hasAllFilesAccess(this@MainActivity)) {
                    scope.launch(Dispatchers.IO) {
                        val info = DatabaseBackupManager.detectHistoricalBackup(this@MainActivity)
                        withContext(Dispatchers.Main) {
                            if (info.hasBackup) {
                                historicalBackupInfo = info
                                firstLaunchNeedsPermission = false
                                showFirstLaunchDialog = true
                            } else {
                                showFirstLaunchDialog = false
                                DatabaseBackupManager.setFirstLaunchRestoreHandled(this@MainActivity, true)
                                app.preferencesRepository.setFirstLaunchRestoreHandled(true)
                                toastHostState.info("未检测到历史备份，已为您开启全新账本")
                            }
                        }
                    }
                } else {
                    showFirstLaunchDialog = false
                    DatabaseBackupManager.setFirstLaunchRestoreHandled(this@MainActivity, true)
                    scope.launch {
                        app.preferencesRepository.setFirstLaunchRestoreHandled(true)
                    }
                    toastHostState.info("已跳过检测，开启全新账本")
                }
            }

            // 🌟 App 启动时后台静默检查新版本
            LaunchedEffect(Unit) {
                app.updateManager.checkForUpdates(isManual = false)
            }

            // 🌟 首次安装启动自动检测历史账本
            LaunchedEffect(Unit) {
                val alreadyHandled = DatabaseBackupManager.hasFirstLaunchRestoreHandled(this@MainActivity)
                if (!alreadyHandled) {
                    val hasPermission = DatabaseBackupManager.hasAllFilesAccess(this@MainActivity)
                    if (hasPermission) {
                        val info = withContext(Dispatchers.IO) {
                            DatabaseBackupManager.detectHistoricalBackup(this@MainActivity)
                        }
                        if (info.hasBackup) {
                            historicalBackupInfo = info
                            firstLaunchNeedsPermission = false
                            showFirstLaunchDialog = true
                        } else {
                            DatabaseBackupManager.setFirstLaunchRestoreHandled(this@MainActivity, true)
                            app.preferencesRepository.setFirstLaunchRestoreHandled(true)
                        }
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        firstLaunchNeedsPermission = true
                        showFirstLaunchDialog = true
                    }
                }
            }

            YuanmanTheme(themeMode = themeMode) {
                CompositionLocalProvider(LocalToastHostState provides toastHostState) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val navController = rememberNavController()

                            // 常驻预置分类图标 painter，避免各页面每次进入重建矢量树导致卡顿
                            PrimeIconPainters()

                            LaunchedEffect(pendingWidgetRoute) {
                                pendingWidgetRoute?.let { route ->
                                    navController.navigate(route) {
                                        launchSingleTop = true
                                    }
                                    pendingWidgetRoute = null
                                }
                            }

                            // 页面导航图
                            YuanmanNavGraph(
                                navController = navController,
                                app = app
                            )

                            // 🌟 全局顶部 Toast 悬浮层 (浮于所有 Sheet 与页面之上，自顶部自然落下)
                            TopToastHost(state = toastHostState)

                            // 🌟 全局版本更新弹窗（支持启动后台检查自动弹出与取消后推迟1天）
                            AppUpdateDialog(
                                visible = showUpdatePrompt,
                                updateState = updateState,
                                onDownload = { info ->
                                    app.updateManager.startDownload(info)
                                    toastHostState.info("开始下载更新…")
                                },
                                onInstall = { apkFile ->
                                    app.updateManager.installApk(apkFile)
                                    toastHostState.info("正在打开安装器…")
                                },
                                onDismiss = {
                                    app.updateManager.dismissUpdatePrompt(postpone = true)
                                }
                            )

                            // 🌟 首次安装启动历史账本检测与恢复引导弹窗
                            FirstLaunchRestoreDialog(
                                visible = showFirstLaunchDialog,
                                needsPermission = firstLaunchNeedsPermission,
                                historicalBackupInfo = historicalBackupInfo,
                                isRestoring = isRestoringFirstLaunch,
                                onRequestPermission = {
                                    val uri = Uri.parse("package:$packageName")
                                    val settingsIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                                    try {
                                        firstLaunchPermissionLauncher.launch(settingsIntent)
                                    } catch (e: Exception) {
                                        try {
                                            firstLaunchPermissionLauncher.launch(
                                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                                            )
                                        } catch (e2: Exception) {
                                            toastHostState.error("无法打开权限设置页")
                                        }
                                    }
                                },
                                onConfirmRestore = {
                                    isRestoringFirstLaunch = true
                                    scope.launch {
                                        val result = DatabaseBackupManager.restoreFromDocuments(this@MainActivity)
                                        isRestoringFirstLaunch = false
                                        showFirstLaunchDialog = false
                                        result.onSuccess {
                                            toastHostState.success("历史账本已成功恢复！")
                                        }.onFailure { e ->
                                            toastHostState.error("恢复失败：${e.message}")
                                        }
                                    }
                                },
                                onDismissFreshStart = {
                                    showFirstLaunchDialog = false
                                    DatabaseBackupManager.setFirstLaunchRestoreHandled(this@MainActivity, true)
                                    scope.launch {
                                        app.preferencesRepository.setFirstLaunchRestoreHandled(true)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingWidgetRoute = intent.getStringExtra(WidgetNavigation.EXTRA_ROUTE)
    }
}
