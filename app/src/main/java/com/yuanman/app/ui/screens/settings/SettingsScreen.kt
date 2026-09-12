package com.yuanman.app.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.app.data.local.DatabaseBackupManager
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.sync.PeerDevice
import com.yuanman.app.ui.components.BudgetSliderDialog
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.UpdateInfo
import com.yuanman.app.utils.UpdateState
import com.yuanman.app.utils.clickableDebounce

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategoryManage: (() -> Unit)? = null,
    onOpenQuickRecordSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val hasUnseenUpdate by viewModel.hasUnseenUpdate.collectAsStateWithLifecycle()
    val learningRules by viewModel.quickEntryLearningRules.collectAsStateWithLifecycle()
    val toast = com.yuanman.app.ui.components.LocalToastHostState.current
    val context = LocalContext.current

    val csvPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importRecordsFromCsv(context, uri) { success, message ->
                if (success) {
                    toast.success(message)
                } else {
                    toast.error(message)
                }
            }
        }
    }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showWifiSyncModal by remember { mutableStateOf(false) }
    // 数据管理底部操作层（导出 / 导入 / 备份与恢复）
    var showDataManageSheet by remember { mutableStateOf(false) }
    var showThemeBottomSheet by remember { mutableStateOf(false) }
    var showFirstConfirmDialog by remember { mutableStateOf(false) }
    var showSecondConfirmDialog by remember { mutableStateOf(false) }
    var prevUpdateState by remember { mutableStateOf<UpdateState?>(null) }
    var manualCheckRequested by remember { mutableStateOf(false) }
    var showRestartAfterRestoreDialog by remember { mutableStateOf(false) }
    var restoreSuccessMessage by remember { mutableStateOf("") }
    var isBackingUp by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var showStorageAccessDialog by remember { mutableStateOf(false) }

    val backupRestorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreFromBackupFile(context, uri) { success, message, needsRestart ->
                if (success) {
                    if (needsRestart) {
                        restoreSuccessMessage = message
                        showRestartAfterRestoreDialog = true
                    } else {
                        // 账户数据 JSON 逐键写回 DataStore，无需重启即自动刷新
                        toast.success(message)
                    }
                } else {
                    toast.error(message)
                }
            }
        }
    }

    // 持有"所有文件访问"权限时：直接扫描 文档/Yuanman 自动恢复；目录中没有备份则回退文件选择器
    val performDocumentsRestore: () -> Unit = {
        isRestoringBackup = true
        viewModel.restoreFromDocumentsNow(context) { success, message ->
            isRestoringBackup = false
            if (success) {
                restoreSuccessMessage = message
                showRestartAfterRestoreDialog = true
            } else {
                toast.error(message)
                backupRestorePickerLauncher.launch(arrayOf("*/*"))
            }
        }
    }

    // 系统"所有文件访问"设置页返回：授权成功则自动扫描恢复，否则回退手动选择
    val allFilesAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (DatabaseBackupManager.hasAllFilesAccess(context)) {
            performDocumentsRestore()
        } else {
            toast.info("未授予文件访问权限，请手动选择备份文件")
            backupRestorePickerLauncher.launch(arrayOf("*/*"))
        }
    }

    LaunchedEffect(uiState.isClearedSuccess) {
        if (uiState.isClearedSuccess) {
            toast.success("全部数据已成功清空并恢复默认设置")
            viewModel.resetClearedFlag()
        }
    }

    LaunchedEffect(updateState) {
        val prev = prevUpdateState
        val wasManualCheck = manualCheckRequested
        prevUpdateState = updateState
        when (val state = updateState) {
            is UpdateState.ReadyToInstall -> {
                if (prev is UpdateState.Downloading) {
                    toast.success("更新包已下载")
                }
            }
            is UpdateState.Error -> {
                if (wasManualCheck) {
                    toast.error(state.message)
                }
            }
            else -> {}
        }
        // 只有手动检查才提示，静默检查不打扰用户。
        if (prev is UpdateState.Checking && wasManualCheck) {
            manualCheckRequested = false
            when (val state = updateState) {
                is UpdateState.UpToDate -> {
                    toast.success("当前已是最新版本")
                }
                is UpdateState.Available -> {
                    toast.success("发现新版本 v${state.info.versionName}")
                }
                else -> {}
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                modifier = Modifier.offset(y = (-4).dp),
                title = { Text("设置", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 🌟 卡片 1: 记账偏好设置（最高频常用）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // 月度预算配置 (核心开销管理目标)
                    SettingsRowItem(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        title = "月度预算",
                        subtitle = if (uiState.monthlyBudget > 0L) "¥${MoneyUtils.centsToYuanString(uiState.monthlyBudget)}" else "未设置",
                        subtitleHighlight = uiState.monthlyBudget > 0L,
                        onClick = { showBudgetDialog = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 分类管理入口 (分类与子标签定制)
                    SettingsRowItem(
                        icon = Icons.Outlined.Category,
                        title = "分类管理",
                        subtitle = "管理支出与收入分类及专属子标签",
                        onClick = { onNavigateToCategoryManage?.invoke() }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 记账习惯二级页入口（闪电记账条总开关与分类学习管理迁至 QuickRecordSettingsScreen）
                    SettingsRowItem(
                        icon = Icons.Outlined.Bolt,
                        title = "记账习惯",
                        subtitle = if (uiState.quickEntryEnabled) {
                            if (learningRules.isEmpty()) "已开启 · 智能匹配分类"
                            else "已开启 · 已积累 ${learningRules.size} 条习惯规则"
                        } else {
                            "已关闭 · 点击开启与管理规则"
                        },
                        subtitleHighlight = uiState.quickEntryEnabled,
                        onClick = { onOpenQuickRecordSettings?.invoke() }
                    )
                }
            }

            // 🌟 卡片 2: 个性化外观（常用视觉偏好）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // 主题外观（浅色 / 深色 / 跟随系统）
                    SettingsRowItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "主题外观",
                        subtitle = uiState.themeMode.title,
                        subtitleHighlight = true,
                        onClick = { showThemeBottomSheet = true }
                    )
                }
            }

            // 🌟 卡片 3: 数据与资产管理（导出与多端互联）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // 数据管理（导出、导入、备份与恢复，点击弹出底部操作层）
                    SettingsRowItem(
                        icon = Icons.Outlined.Storage,
                        title = "数据管理",
                        subtitle = "导出、导入、备份与恢复",
                        onClick = { showDataManageSheet = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 设备同步 (同一 WiFi 局域网跨设备安全同步)
                    SettingsRowItem(
                        icon = Icons.Outlined.Wifi,
                        title = "设备同步",
                        subtitle = "同一 WiFi 发现设备后授权同步",
                        onClick = { showWifiSyncModal = true }
                    )
                }
            }

            // 🌟 卡片 4: 系统与更新（低频维护）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    val currentVer = viewModel.updateManager.currentVersionName
                    val (updateSubtitle, subtitleHighlight, downloadProgress) = when (val state = updateState) {
                        is UpdateState.Checking -> Triple("正在检查…", true, null)
                        is UpdateState.Available -> Triple("发现 v${state.info.versionName} · 点此查看", true, null)
                        is UpdateState.Downloading -> Triple("下载中 ${(state.progress * 100).toInt()}%", true, state.progress)
                        is UpdateState.ReadyToInstall -> Triple("已下载 · 点此安装", true, null)
                        is UpdateState.UpToDate -> Triple("v$currentVer · 已是最新", false, null)
                        is UpdateState.Error -> Triple("检查失败 · 点此重试", false, null)
                        else -> Triple("v$currentVer · 点此检查", false, null)
                    }

                    SettingsRowItem(
                        icon = Icons.Outlined.SystemUpdate,
                        title = "版本更新",
                        subtitle = updateSubtitle,
                        subtitleHighlight = subtitleHighlight,
                        showBadge = hasUnseenUpdate,
                        downloadProgress = downloadProgress,
                        isLoading = updateState is UpdateState.Checking,
                        onClick = {
                            when (val state = updateState) {
                                is UpdateState.ReadyToInstall -> {
                                    viewModel.markUpdateSeen(state.info.versionName)
                                    viewModel.requestUpdatePrompt()
                                }
                                is UpdateState.Available -> {
                                    viewModel.markUpdateSeen(state.info.versionName)
                                    viewModel.requestUpdatePrompt()
                                }
                                is UpdateState.Downloading -> {
                                    toast.info("正在下载新版本安装包，请稍候...")
                                }
                                is UpdateState.Checking -> Unit
                                else -> {
                                    manualCheckRequested = true
                                    viewModel.checkForUpdates(isManual = true)
                                }
                            }
                        }
                    )
                }
            }

            // 🌟 卡片 5: 数据清理危险区（置底防误触）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    // 清空全部数据
                    SettingsRowItem(
                        icon = Icons.Outlined.DeleteForever,
                        title = "清空全部数据",
                        subtitle = "清除所有账单记录并重置分类",
                        isDestructive = true,
                        onClick = { showFirstConfirmDialog = true }
                    )
                }
            }

            // 🌟 关于应用
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "沅满记账 · Yuanman",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "版本 v${viewModel.updateManager.currentVersionName} · 纯本地离线隐私保护",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // 卸载重装后系统可能清除备份文件索引：引导授予"所有文件访问"以便自动扫描 文档/Yuanman
    if (showStorageAccessDialog) {
        AlertDialog(
            onDismissRequest = { showStorageAccessDialog = false },
            title = { Text("恢复备份数据") },
            text = {
                Text(
                    "卸载重装后，系统可能无法再索引 文档/Yuanman 中的备份文件。\n\n" +
                        "授予「所有文件访问」权限后，应用将自动查找最近的备份并整体还原；" +
                        "也可以不授权，直接手动选择备份文件。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStorageAccessDialog = false
                    val uri = Uri.parse("package:${context.packageName}")
                    val settingsIntent =
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri)
                    try {
                        allFilesAccessLauncher.launch(settingsIntent)
                    } catch (e: Exception) {
                        try {
                            allFilesAccessLauncher.launch(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                            )
                        } catch (e2: Exception) {
                            toast.error("无法打开权限设置，请手动选择备份文件")
                            backupRestorePickerLauncher.launch(arrayOf("*/*"))
                        }
                    }
                }) { Text("去授权并恢复") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showStorageAccessDialog = false
                    backupRestorePickerLauncher.launch(arrayOf("*/*"))
                }) { Text("手动选择文件") }
            }
        )
    }

    // 从备份文件恢复成功后，提示重启使新数据完整生效
    if (showRestartAfterRestoreDialog) {
        AlertDialog(
            onDismissRequest = { showRestartAfterRestoreDialog = false },
            title = { Text("数据已恢复") },
            text = { Text("$restoreSuccessMessage。\n\n重启应用后新数据将完整生效，是否立即重启？") },
            confirmButton = {
                TextButton(onClick = {
                    showRestartAfterRestoreDialog = false
                    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                    if (intent != null) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        context.startActivity(intent)
                    }
                    Runtime.getRuntime().exit(0)
                }) { Text("立即重启") }
            },
            dismissButton = {
                TextButton(onClick = { showRestartAfterRestoreDialog = false }) { Text("稍后再说") }
            }
        )
    }

    // 🌟 月度预算设置弹窗（拖动滑杆设置金额）
    if (showBudgetDialog) {
        BudgetSliderDialog(
            title = "设置月度预算",
            subtitle = "设定合理的月度预算目标，可在首页看板实时把控消费节奏。",
            initialBudgetCents = uiState.monthlyBudget,
            onSave = {
                viewModel.setMonthlyBudget(it)
                showBudgetDialog = false
                toast.success("月度预算已保存")
            },
            onClear = {
                viewModel.setMonthlyBudget(0L)
                showBudgetDialog = false
                toast.success("已清除预算设置")
            },
            onDismiss = { showBudgetDialog = false }
        )
    }


    // 🌟 主题外观底部选择弹层
    if (showThemeBottomSheet) {
        YuanmanModalBottomSheet(
            onDismissRequest = { showThemeBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "主题外观",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "选择应用界面深浅色显示风格",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                ThemeMode.values().forEach { mode ->
                    val isSelected = uiState.themeMode == mode
                    val (modeIcon, modeDesc) = when (mode) {
                        ThemeMode.SYSTEM -> Pair(Icons.Outlined.BrightnessAuto, "根据系统深浅色设置自动适配")
                        ThemeMode.LIGHT -> Pair(Icons.Outlined.LightMode, "始终使用清晰通透的浅色外观")
                        ThemeMode.DARK -> Pair(Icons.Outlined.DarkMode, "始终使用沉浸舒适的深色外观")
                    }

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                viewModel.setThemeMode(mode)
                                showThemeBottomSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = modeIcon,
                                            contentDescription = null,
                                            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column {
                                    Text(
                                        text = mode.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = modeDesc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }

                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "已选择",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 🌟 设备同步弹层
    // 数据管理底部弹层：导出、导入、备份与恢复
    if (showDataManageSheet) {
        YuanmanModalBottomSheet(
            onDismissRequest = { showDataManageSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "数据管理",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "导出、导入、备份与恢复",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                SettingsRowItem(
                    icon = Icons.Outlined.FileDownload,
                    title = "导出账单表格",
                    subtitle = "支持 Excel 查看与微信/邮件分享 (共 ${uiState.totalRecordCount} 笔)",
                    onClick = {
                        viewModel.exportRecordsCsv(context)
                        showDataManageSheet = false
                    }
                )

                SettingsRowItem(
                    icon = Icons.Outlined.FileUpload,
                    title = "导入账单表格",
                    subtitle = "支持导入 CSV 账单表格并自动归类入库",
                    onClick = {
                        csvPickerLauncher.launch(
                            arrayOf(
                                "text/comma-separated-values",
                                "text/csv",
                                "text/plain",
                                "application/csv",
                                "*/*"
                            )
                        )
                        showDataManageSheet = false
                    }
                )

                SettingsRowItem(
                    icon = Icons.Outlined.Save,
                    title = "立即备份到文档",
                    subtitle = "分类、账单、账户与计划及个人习惯保存至 文档/Yuanman，重装可自动还原",
                    isLoading = isBackingUp,
                    onClick = {
                        isBackingUp = true
                        showDataManageSheet = false
                        viewModel.backupDataToDocumentsNow(context) { success, message ->
                            isBackingUp = false
                            if (success) {
                                toast.success(message)
                            } else {
                                toast.error(message)
                            }
                        }
                    }
                )

                SettingsRowItem(
                    icon = Icons.Outlined.SettingsBackupRestore,
                    title = "从备份文件恢复",
                    subtitle = "自动还原 文档/Yuanman 中分类、账单、账户与计划快照，也可手动选择备份文件",
                    isLoading = isRestoringBackup,
                    onClick = {
                        showDataManageSheet = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                            !DatabaseBackupManager.hasAllFilesAccess(context)
                        ) {
                            // 卸载重装后 MediaStore 索引可能已被系统清除，引导授予文件访问权限
                            showStorageAccessDialog = true
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            performDocumentsRestore()
                        } else {
                            backupRestorePickerLauncher.launch(arrayOf("*/*"))
                        }
                    }
                )

                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }

    if (showWifiSyncModal) {
        FamilySyncBottomSheet(
            syncManager = viewModel.syncManager,
            toast = toast,
            onDismiss = { showWifiSyncModal = false }
        )
    }

    // 第一次防误触确认
    ConfirmDeleteDialog(
        visible = showFirstConfirmDialog,
        title = "清空全部数据",
        message = "确定要清空全部账单数据吗？此操作无法撤销，建议先导出备份！",
        confirmButtonText = "继续清空",
        onConfirm = {
            showFirstConfirmDialog = false
            showSecondConfirmDialog = true
        },
        onDismiss = { showFirstConfirmDialog = false }
    )

    // 第二次强制防误触确认
    ConfirmDeleteDialog(
        visible = showSecondConfirmDialog,
        title = "最终确认清空",
        message = "您真的确定要删除全部账单数据吗？删除后数据将永远丢失！",
        confirmButtonText = "确认彻底清空",
        onConfirm = {
            viewModel.clearAllData()
            showSecondConfirmDialog = false
        },
        onDismiss = { showSecondConfirmDialog = false }
    )

}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    subtitleHighlight: Boolean = false,
    showBadge: Boolean = false,
    downloadProgress: Float? = null,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickableDebounce(debounceTimeMs = 400L, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box {
                Surface(
                    shape = CircleShape,
                    color = if (isDestructive) errorColor.copy(alpha = 0.12f) else primaryColor.copy(alpha = 0.12f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isDestructive) errorColor else primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (showBadge) {
                    UpdateBadge()
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = FontWeight.Medium,
                            color = if (isDestructive) errorColor else MaterialTheme.colorScheme.onSurface
                        )
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (subtitleHighlight) primaryColor else MaterialTheme.colorScheme.outline
                )

                if (downloadProgress != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = primaryColor,
                        trackColor = primaryColor.copy(alpha = 0.2f)
                    )
                }
            }
        }

        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = primaryColor
            )
            trailingContent != null -> trailingContent()
            else -> Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }

}

@Composable
private fun BoxScope.UpdateBadge() {
    val badgeTransition = rememberInfiniteTransition(label = "update-badge")
    val badgeScale by badgeTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "update-badge-scale"
    )

    Box(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .offset(x = 2.dp, y = (-2).dp)
            .size(10.dp)
            .scale(badgeScale)
            .background(Color(0xFFE53935), CircleShape)
            .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
    )
}

/**
 * 🌟 设备同步弹层 (基于 NSD 自动发现 + 人工确认授权 + AES-GCM 加密同步)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilySyncBottomSheet(
    syncManager: com.yuanman.app.sync.FamilySyncManager,
    toast: com.yuanman.app.ui.components.ToastHostState,
    onDismiss: () -> Unit
) {
    val devices by syncManager.devices.collectAsStateWithLifecycle()
    val syncing by syncManager.syncing.collectAsStateWithLifecycle()
    val syncStatus by syncManager.status.collectAsStateWithLifecycle()
    val pendingRequests by syncManager.pendingRequests.collectAsStateWithLifecycle()
    val pendingOutboundDevices by syncManager.pendingOutboundDevices.collectAsStateWithLifecycle()
    val primaryColor = MaterialTheme.colorScheme.primary

    var knownDeviceNames by remember { mutableStateOf(setOf<String>()) }

    DisposableEffect(syncManager) {
        syncManager.start()
        onDispose { syncManager.stop() }
    }

    // 🌟 设备发现提示：发现设备并不代表已经授权同步
    LaunchedEffect(devices) {
        val newlyFound = devices.filter { it.name !in knownDeviceNames }
        if (newlyFound.isNotEmpty()) {
            knownDeviceNames = devices.map { it.name }.toSet()
            newlyFound.forEach { device ->
                toast.info("发现设备 ${device.name}，点击即可发起同步")
            }
        }
    }

    // 🌟 同步完成提示：每次数据互通完成后 toast
    LaunchedEffect(Unit) {
        syncManager.events.collect { event ->
            toast.success("同步完成：更新 ${event.recordCount} 笔账单、${event.categoryCount} 个分类")
        }
    }

    val pendingRequest = pendingRequests.firstOrNull()
    if (pendingRequest != null) {
        AlertDialog(
            onDismissRequest = {
                syncManager.respondToSyncRequest(pendingRequest.id, accepted = false)
            },
            title = { Text("设备同步请求") },
            text = {
                Text(
                    "设备「${pendingRequest.deviceName}」正在请求同步账单数据。\n\n来源：${pendingRequest.hostAddress}\n\n请确认是否允许本次同步。"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        syncManager.respondToSyncRequest(pendingRequest.id, accepted = true)
                    }
                ) { Text("同意并同步") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        syncManager.respondToSyncRequest(pendingRequest.id, accepted = false)
                    }
                ) { Text("拒绝") }
            }
        )
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "设备同步",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = syncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "点击在线设备发起同步，对方确认后才会传输数据。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 🌟 在线设备列表与同步
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "在线设备 (${devices.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (devices.isEmpty()) {
                        Text(
                            text = "暂未发现同一 WiFi 下的其他设备，请确保双方连接在相同局域网。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        devices.forEach { device: PeerDevice ->
                            val isRequesting = device.name in pendingOutboundDevices
                            val canRequest = !syncing && !device.connected && !isRequesting
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(enabled = canRequest) {
                                        syncManager.requestSync(device)
                                    }
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (device.connected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = if (device.connected) primaryColor else MaterialTheme.colorScheme.outline
                                    )
                                    Column {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = "${device.host.hostAddress}:${device.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }

                                Text(
                                    text = when {
                                        device.connected -> "已连接"
                                        isRequesting -> "等待确认"
                                        else -> "点击连接"
                                    },
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isRequesting) MaterialTheme.colorScheme.tertiary else primaryColor
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { syncManager.syncNow() },
                        enabled = !syncing && pendingOutboundDevices.isEmpty() && devices.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("正在同步...")
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("立即同步")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

}
