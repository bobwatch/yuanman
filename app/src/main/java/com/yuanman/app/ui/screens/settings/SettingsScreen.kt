package com.yuanman.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.local.entity.QuickEntryLearningEntity
import com.yuanman.app.sync.PeerDevice
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.YuanmanModalBottomSheet
import com.yuanman.app.utils.MoneyUtils
import com.yuanman.app.utils.UpdateInfo
import com.yuanman.app.utils.UpdateState
import com.yuanman.app.utils.clickableDebounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategoryManage: (() -> Unit)? = null,
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
    var showThemeBottomSheet by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showFirstConfirmDialog by remember { mutableStateOf(false) }
    var showSecondConfirmDialog by remember { mutableStateOf(false) }
    var showResetLearningDialog by remember { mutableStateOf(false) }
    var showQuickEntrySheet by remember { mutableStateOf(false) }
    var prevUpdateState by remember { mutableStateOf<UpdateState?>(null) }
    var manualCheckRequested by remember { mutableStateOf(false) }

    // 进入设置页后静默检查一次，避免用户必须先点击才能知道有无新版本。
    LaunchedEffect(Unit) {
        viewModel.checkForUpdates(isManual = false)
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
                    showUpdateDialog = true
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

                    SettingsRowItem(
                        icon = Icons.Outlined.Bolt,
                        title = "快捷记账",
                        subtitle = if (uiState.quickEntryEnabled) {
                            if (learningRules.isEmpty()) "已开启 · 智能匹配分类"
                            else "已开启 · 已积累 ${learningRules.size} 条习惯规则"
                        } else {
                            "已关闭 · 点击开启与管理规则"
                        },
                        subtitleHighlight = uiState.quickEntryEnabled,
                        onClick = { showQuickEntrySheet = true }
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
                    // 导出账单表格 (高频实用)
                    SettingsRowItem(
                        icon = Icons.Outlined.FileDownload,
                        title = "导出账单表格",
                        subtitle = "支持 Excel 查看与微信/邮件分享 (共 ${uiState.totalRecordCount} 笔)",
                        onClick = { viewModel.exportRecordsCsv(context) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 导入账单表格
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
                        }
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
                                    showUpdateDialog = true
                                }
                                is UpdateState.Available -> {
                                    viewModel.markUpdateSeen(state.info.versionName)
                                    showUpdateDialog = true
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

    // 🌟 月度预算设置弹窗
    if (showBudgetDialog) {
        val budgetFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        var budgetInput by remember {
            mutableStateOf(
                if (uiState.monthlyBudget > 0L) MoneyUtils.centsToYuanString(uiState.monthlyBudget) else ""
            )
        }

        LaunchedEffect(Unit) {
            delay(120)
            budgetFocusRequester.requestFocus()
            keyboardController?.show()
        }

        Dialog(onDismissRequest = { showBudgetDialog = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "设置月度预算",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "设定合理的月度预算目标，可在首页看板实时把控消费节奏。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("预算金额 (元)") },
                        placeholder = { Text("如: 5000") },
                        prefix = { Text("¥ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(budgetFocusRequester)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                viewModel.setMonthlyBudget(0L)
                                showBudgetDialog = false
                                toast.success("已清除预算设置")
                            }
                        ) {
                            Text("清除预算", color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val cents = MoneyUtils.parseYuanToCents(budgetInput)
                                viewModel.setMonthlyBudget(cents)
                                showBudgetDialog = false
                                toast.success("月度预算已保存")
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // 🌟 新版本更新详情弹窗
    if (showUpdateDialog) {
        val info = when (val state = updateState) {
            is UpdateState.Available -> state.info
            is UpdateState.ReadyToInstall -> state.info
            else -> null
        }
        val readyApk = (updateState as? UpdateState.ReadyToInstall)?.apkFile
        if (info != null) {
            AlertDialog(
                onDismissRequest = { showUpdateDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (readyApk != null) {
                                "更新已就绪 v${info.versionName}"
                            } else {
                                "发现新版本 v${info.versionName}"
                            },
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (info.releaseTitle.isNotBlank()) {
                            Text(
                                text = info.releaseTitle,
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (info.releaseNotes.isNotBlank()) {
                            Text(
                                text = info.releaseNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "本次更新包含体验优化与问题修复。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        if (info.sizeBytes > 0L) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "安装包大小: ${"%.1f".format(info.sizeBytes / 1024.0 / 1024.0)} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (readyApk != null) {
                                viewModel.installApk(readyApk)
                                toast.info("正在打开安装器…")
                            } else {
                                viewModel.startDownload(info)
                                toast.info("开始下载更新…")
                            }
                            showUpdateDialog = false
                        }
                    ) {
                        Text(if (readyApk != null) "立即安装" else "下载更新")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) {
                        Text("稍后")
                    }
                }
            )
        }
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

    ConfirmDeleteDialog(
        visible = showResetLearningDialog,
        title = "重置分类学习",
        message = "将清除快捷记账的个人分类习惯，内置分类词典不会受影响。确定继续吗？",
        confirmButtonText = "确认重置",
        onConfirm = {
            viewModel.clearQuickEntryLearning()
            showResetLearningDialog = false
            toast.success("分类学习已重置")
        },
        onDismiss = { showResetLearningDialog = false }
    )

    if (showQuickEntrySheet) {
        QuickEntryBottomSheet(
            viewModel = viewModel,
            uiState = uiState,
            onReset = { showResetLearningDialog = true },
            onDismiss = { showQuickEntrySheet = false }
        )
    }
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun QuickEntryBottomSheet(
    viewModel: SettingsViewModel,
    uiState: SettingsUiState,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    val rules by viewModel.quickEntryLearningRules.collectAsStateWithLifecycle()
    val categories = viewModel.allCategories.collectAsStateWithLifecycle().value
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var selectedType by remember { mutableStateOf<RecordType?>(null) }
    var editingRule by remember { mutableStateOf<QuickEntryLearningEntity?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editorPhrase by remember { mutableStateOf("") }
    var editorType by remember { mutableStateOf(RecordType.EXPENSE) }
    var editorCategoryId by remember { mutableStateOf<Long?>(null) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var deleteRule by remember { mutableStateOf<QuickEntryLearningEntity?>(null) }
    val learningListState = rememberLazyListState()
    val pageSize = 60

    fun openEditor(rule: QuickEntryLearningEntity?) {
        editingRule = rule
        showAddDialog = rule == null
        editorPhrase = rule?.phrase.orEmpty()
        editorType = rule?.let { runCatching { RecordType.valueOf(it.type) }.getOrDefault(RecordType.EXPENSE) } ?: RecordType.EXPENSE
        editorCategoryId = categories.firstOrNull { it.syncId == rule?.categorySyncId }?.id
            ?: categories.firstOrNull { it.type == editorType.name }?.id
    }

    val visibleRules = rules.filter { rule ->
        val categoryName = categories.firstOrNull { it.syncId == rule.categorySyncId }?.name.orEmpty()
        (selectedType == null || rule.type == selectedType?.name) &&
            (searchQuery.isBlank() || rule.phrase.contains(searchQuery.trim(), ignoreCase = true) || categoryName.contains(searchQuery.trim(), ignoreCase = true))
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            delay(100)
            searchFocusRequester.requestFocus()
        }
    }

    // 词云按页渲染，首屏只创建少量 chip；滚动接近底部时再无感追加下一页。
    var loadedRuleCount by remember(searchQuery, selectedType, rules.size) { mutableIntStateOf(pageSize) }
    var isLoadingMoreRules by remember(searchQuery, selectedType, rules.size) { mutableStateOf(false) }
    val sortedRules = remember(visibleRules) {
        visibleRules.sortedWith(
            compareByDescending<QuickEntryLearningEntity> { it.sampleCount }
                .thenBy { it.phrase.length }
        )
    }
    val loadedRules = sortedRules.take(loadedRuleCount)
    LaunchedEffect(learningListState, loadedRules.size, sortedRules.size) {
        snapshotFlow {
            val layoutInfo = learningListState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null &&
                lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset
        }.collect { reachedEnd ->
            if (reachedEnd && loadedRules.size < sortedRules.size && !isLoadingMoreRules) {
                // 给用户一个明确的反馈，即使本地数据加载很快也短暂展示加载状态。
                isLoadingMoreRules = true
                delay(180)
                loadedRuleCount = (loadedRuleCount + pageSize).coerceAtMost(sortedRules.size)
                isLoadingMoreRules = false
            }
        }
    }

    YuanmanModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 顶部标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("快捷记账", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Text(
                        "自然语言智能识别与分类习惯管理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            // 2. 快捷记账总开关卡片
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(0.8.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = null,
                                    tint = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = "开启快捷记账",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (uiState.quickEntryEnabled) "首页顶部常驻闪电记账条" else "已隐藏首页快捷记账条",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.quickEntryEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(
                        checked = uiState.quickEntryEnabled,
                        onCheckedChange = viewModel::setQuickEntryEnabled
                    )
                }
            }

            AnimatedVisibility(
                visible = uiState.quickEntryEnabled,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 3. 分类学习管理区域
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("分类学习", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            val presetCount = rules.count { it.sampleCount == 0 }
                            Text(
                                "系统词库 ${presetCount} 条 · 个人习惯随记账自动积累",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            TextButton(onClick = { openEditor(null) }) { Text("新增") }
                            TextButton(onClick = onReset, enabled = rules.isNotEmpty()) { Text("重置") }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (searchExpanded) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f).focusRequester(searchFocusRequester),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                                placeholder = { Text("搜索关键词或分类") },
                                trailingIcon = {
                                    IconButton(onClick = {
                                        searchQuery = ""
                                        searchExpanded = false
                                    }) { Icon(Icons.Default.Close, contentDescription = "关闭搜索") }
                                }
                            )
                        } else {
                            FilterChip(selected = selectedType == null, onClick = { selectedType = null }, label = { Text("全部") })
                            FilterChip(selected = selectedType == RecordType.EXPENSE, onClick = { selectedType = RecordType.EXPENSE }, label = { Text("支出") })
                            FilterChip(selected = selectedType == RecordType.INCOME, onClick = { selectedType = RecordType.INCOME }, label = { Text("收入") })
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(onClick = { searchExpanded = true }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Search, contentDescription = "展开搜索")
                            }
                        }
                    }
                    if (rules.isEmpty()) {
                        Text(
                            "还没有学习记录。保存几笔快捷记账后，系统会逐步记住你的分类习惯。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 20.dp)
                        )
                    } else if (visibleRules.isEmpty()) {
                        Text("没有匹配的学习记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    } else {
                        LazyColumn(
                            state = learningListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(
                                items = loadedRules.chunked(pageSize),
                                key = { page ->
                                    page.firstOrNull()?.let { "${it.type}_${it.categorySyncId}_${it.phrase}" } ?: "learning_page"
                                }
                            ) { page ->
                                FlowRow(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    page.forEach { rule ->
                                        val category = categories.firstOrNull { it.syncId == rule.categorySyncId }
                                        val typeColor = if (rule.type == RecordType.EXPENSE.name) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        val categoryColor = category?.colorHex?.let { Color(it) } ?: typeColor
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .combinedClickable(
                                                    onClick = { openEditor(rule) },
                                                    onLongClick = { deleteRule = rule }
                                                ),
                                            shape = RoundedCornerShape(50),
                                            color = categoryColor.copy(alpha = 0.10f),
                                            border = BorderStroke(0.7.dp, categoryColor.copy(alpha = 0.35f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(categoryColor)
                                                )
                                                Text(
                                                    text = rule.phrase,
                                                    fontSize = (11 + rule.sampleCount.coerceIn(0, 3)).sp,
                                                    fontWeight = if (rule.sampleCount > 1) FontWeight.SemiBold else FontWeight.Normal,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (loadedRules.size < sortedRules.size) {
                                item(key = "learning_loading_footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isLoadingMoreRules) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "正在加载更多…",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        } else {
                                            Text(
                                                text = "继续下滑加载更多",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog || editingRule != null) {
        val currentRule = editingRule
        val editorCategories = categories.filter { it.type == editorType.name }
        val selectedCategory = editorCategories.firstOrNull { it.id == editorCategoryId }
        AlertDialog(
            onDismissRequest = { editingRule = null; showAddDialog = false },
            title = { Text(if (currentRule == null) "新增学习内容" else "编辑学习内容") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editorPhrase,
                        onValueChange = { editorPhrase = it },
                        label = { Text("描述关键词") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = editorType == RecordType.EXPENSE, onClick = { editorType = RecordType.EXPENSE; editorCategoryId = null }, label = { Text("支出") })
                        FilterChip(selected = editorType == RecordType.INCOME, onClick = { editorType = RecordType.INCOME; editorCategoryId = null }, label = { Text("收入") })
                    }
                    Box {
                        OutlinedButton(onClick = { categoryMenuExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedCategory?.name ?: "选择分类")
                        }
                        DropdownMenu(
                            expanded = categoryMenuExpanded,
                            onDismissRequest = { categoryMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp)
                        ) {
                            editorCategories.forEach { category ->
                                DropdownMenuItem(
                                    text = { Text(category.name) },
                                    onClick = { editorCategoryId = category.id; categoryMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val category = editorCategories.firstOrNull { it.id == editorCategoryId }
                        if (category != null) {
                            if (currentRule == null) viewModel.addQuickEntryLearning(editorType, editorPhrase, category.syncId)
                            else viewModel.updateQuickEntryLearning(currentRule, editorPhrase, editorType, category.syncId)
                        }
                        editingRule = null
                        showAddDialog = false
                    },
                    enabled = editorPhrase.isNotBlank() && selectedCategory != null
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingRule = null; showAddDialog = false }) { Text("取消") } }
        )
    }

    deleteRule?.let { rule ->
        AlertDialog(
            onDismissRequest = { deleteRule = null },
            title = { Text("删除学习内容") },
            text = { Text("确定删除“${rule.phrase}”这条分类学习记录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteQuickEntryLearning(rule)
                        deleteRule = null
                    }
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteRule = null }) { Text("取消") } }
        )
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
 * 🌟 设备同步弹层 (基于 NSD 自动发现 + 6 位配对码 + AES-GCM 加密同步)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilySyncBottomSheet(
    syncManager: com.yuanman.app.sync.FamilySyncManager,
    toast: com.yuanman.app.ui.components.ToastHostState,
    onDismiss: () -> Unit
) {
    val myCode by syncManager.pairingCode.collectAsStateWithLifecycle()
    val devices by syncManager.devices.collectAsStateWithLifecycle()
    val syncing by syncManager.syncing.collectAsStateWithLifecycle()
    val syncStatus by syncManager.status.collectAsStateWithLifecycle()
    val pendingRequests by syncManager.pendingRequests.collectAsStateWithLifecycle()
    val pendingOutboundDevices by syncManager.pendingOutboundDevices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    var peerCodeInput by remember { mutableStateOf("") }
    var confirmRegenerate by remember { mutableStateOf(false) }
    var showPairingFallback by remember { mutableStateOf(false) }
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
                    text = "点击在线设备发起同步，对方确认后才会传输数据。配对码仅作为备用方式。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 🌟 配对码备用入口：默认收起，优先使用在线设备授权。
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showPairingFallback = !showPairingFallback }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "备用：使用配对码",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "无法点击设备时再展开使用",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Icon(
                        imageVector = if (showPairingFallback) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (showPairingFallback) "收起配对码" else "展开配对码",
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (showPairingFallback) {
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
                        // 本机配对码只在备用入口展开时展示。
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "本机配对码",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = myCode,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = primaryColor,
                                    letterSpacing = 4.sp
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("pairingCode", myCode))
                                        toast.success("配对码已复制")
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("复制")
                                }
                                TextButton(onClick = { confirmRegenerate = true }) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("更换")
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                        Text(
                            text = "连接新设备",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = peerCodeInput,
                                onValueChange = {
                                    if (it.length <= 6) peerCodeInput = it.filter { ch -> ch.isDigit() }
                                },
                                label = { Text("对方 6 位配对码") },
                                placeholder = { Text("如: 123456") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = {
                                    if (peerCodeInput.length == 6) {
                                        val ok = syncManager.setPairingCode(peerCodeInput)
                                        if (ok) {
                                            peerCodeInput = ""
                                            toast.success("已设置对方配对码，正在尝试同步...")
                                            syncManager.syncNowWithPairingCode()
                                        }
                                    }
                                },
                                enabled = peerCodeInput.length == 6,
                                modifier = Modifier.height(52.dp)
                            ) {
                                Text("配对")
                            }
                        }
                    }
                }
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

    if (confirmRegenerate) {
        AlertDialog(
            onDismissRequest = { confirmRegenerate = false },
            title = { Text("换一个配对码") },
            text = { Text("生成新配对码后，另一台手机需重新输入该配对码才能同步。确定更换吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        syncManager.regeneratePairingCode()
                        confirmRegenerate = false
                        toast.success("已生成新配对码")
                    }
                ) {
                    Text("确定更换")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmRegenerate = false }) {
                    Text("取消")
                }
            }
        )
    }
}
