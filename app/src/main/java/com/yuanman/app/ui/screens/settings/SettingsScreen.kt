package com.yuanman.app.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.sync.PeerDevice
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.utils.MoneyUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategoryManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val toast = com.yuanman.app.ui.components.LocalToastHostState.current
    val context = LocalContext.current

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showWifiSyncModal by remember { mutableStateOf(false) }
    var showThemeDropdown by remember { mutableStateOf(false) }
    var showFirstConfirmDialog by remember { mutableStateOf(false) }
    var showSecondConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isClearedSuccess) {
        if (uiState.isClearedSuccess) {
            toast.success("全部数据已成功清空并恢复默认设置")
            viewModel.resetClearedFlag()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Bold) }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 🌟 卡片 1: 预算与分类（去标题化，极简纯净）
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
                    // 月度预算
                    SettingsRowItem(
                        icon = Icons.Outlined.Savings,
                        title = "月度预算目标",
                        subtitle = if (uiState.monthlyBudget > 0L) "当前设定: ¥${MoneyUtils.centsToYuanString(uiState.monthlyBudget)}" else "未设定（点击立即设定）",
                        subtitleHighlight = uiState.monthlyBudget > 0L,
                        onClick = { showBudgetDialog = true }
                    )

                    if (onNavigateToCategoryManage != null) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 2.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                        )

                        SettingsRowItem(
                            icon = Icons.Outlined.Category,
                            title = "收支分类管理",
                            subtitle = "自定义分类图标与配色",
                            onClick = { onNavigateToCategoryManage() }
                        )
                    }
                }
            }

            // 🌟 卡片 2: 数据与跨设备同步（去标题化）
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
                    // 设备同步 (基于 NSD 自动发现 + 配对码)
                    SettingsRowItem(
                        icon = Icons.Outlined.Wifi,
                        title = "设备同步",
                        subtitle = "同一 WiFi 自动发现与同步",
                        onClick = { showWifiSyncModal = true }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 2.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
                    )

                    // 导出表格
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

            // 🌟 卡片 3: 外观与显示（下拉菜单选择）
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    SettingsRowItem(
                        icon = Icons.Outlined.DarkMode,
                        title = "主题外观",
                        subtitle = uiState.themeMode.title,
                        subtitleHighlight = true,
                        onClick = { showThemeDropdown = true }
                    )

                    DropdownMenu(
                        expanded = showThemeDropdown,
                        onDismissRequest = { showThemeDropdown = false }
                    ) {
                        ThemeMode.values().forEach { mode ->
                            val isSelected = uiState.themeMode == mode
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = mode.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                leadingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDropdown = false
                                }
                            )
                        }
                    }
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
                    text = "版本 v0.0.1 · 纯本地离线隐私保护",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }

    // 🌟 月度预算弹窗
    if (showBudgetDialog) {
        var budgetText by remember {
            mutableStateOf(
                if (uiState.monthlyBudget > 0L) MoneyUtils.centsToYuanString(uiState.monthlyBudget) else ""
            )
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
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "设置月度预算",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = "设定合理的月度预算目标，实时把控消费节奏，避免超支。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { budgetText = it },
                        label = { Text("预算金额 (元)") },
                        placeholder = { Text("如: 5000") },
                        prefix = { Text("¥ ") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
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
                            }
                        ) {
                            Text("清除预算", color = MaterialTheme.colorScheme.error)
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val cents = MoneyUtils.parseYuanToCents(budgetText)
                                viewModel.setMonthlyBudget(cents)
                                showBudgetDialog = false
                            }
                        ) {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }

    // 🌟 局域网跨设备同步 BottomSheet (参考 moneyhistory 架构)
    if (showWifiSyncModal) {
        FamilySyncBottomSheet(
            syncManager = viewModel.syncManager,
            onDismiss = { showWifiSyncModal = false }
        )
    }

    // 第一次清空确认
    ConfirmDeleteDialog(
        visible = showFirstConfirmDialog,
        title = "危险操作：清空全部数据",
        message = "确定要清空本地所有账单记录并将分类恢复出厂默认吗？此操作不可撤销！",
        confirmButtonText = "继续下一步",
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
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
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

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                        color = if (isDestructive) errorColor else MaterialTheme.colorScheme.onSurface
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (subtitleHighlight) primaryColor else MaterialTheme.colorScheme.outline
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * 🌟 局域网跨设备同步弹层 (基于 NSD 自动发现 + 6位配对码 + AES-GCM 加密同步)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FamilySyncBottomSheet(
    syncManager: com.yuanman.app.sync.FamilySyncManager,
    onDismiss: () -> Unit
) {
    val devices by syncManager.devices.collectAsStateWithLifecycle()
    val status by syncManager.status.collectAsStateWithLifecycle()
    val syncing by syncManager.syncing.collectAsStateWithLifecycle()
    val myCode by syncManager.pairingCode.collectAsStateWithLifecycle()
    val lastEvent by syncManager.lastEvent.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val toast = com.yuanman.app.ui.components.LocalToastHostState.current
    var inputCode by remember { mutableStateOf("") }
    var joinMessage by remember { mutableStateOf<String?>(null) }
    var confirmRegenerate by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

    DisposableEffect(Unit) {
        syncManager.start()
        onDispose {
            syncManager.stop()
        }
    }

    LaunchedEffect(lastEvent) {
        lastEvent?.let { event ->
            toast.success("已成功同步设备 ${event.peerName} (${event.recordCount} 条账单)")
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
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
                    text = "同一 WiFi 下自动发现并加密同步数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            // 🌟 本机配对码展示卡片
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "本机配对码",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = myCode,
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        letterSpacing = 6.sp,
                        maxLines = 1
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        TextButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("pairingCode", myCode))
                                toast.success("配对码已复制")
                            }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("复制")
                        }

                        TextButton(onClick = { confirmRegenerate = true }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("换一个")
                        }
                    }
                }
            }

            // 🌟 加入配对输入框
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "加入配对",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { input ->
                                inputCode = input.filter { it.isDigit() }.take(6)
                                joinMessage = null
                            },
                            placeholder = { Text("输入对方 6 位配对码", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (syncManager.setPairingCode(inputCode)) {
                                    joinMessage = "已设置配对码 $inputCode"
                                    toast.success("配对码已设置，正在同步")
                                } else {
                                    joinMessage = "请输入 6 位数字配对码"
                                }
                            },
                            enabled = inputCode.length == 6
                        ) {
                            Text("连接")
                        }
                    }

                    if (joinMessage != null) {
                        Text(
                            text = joinMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = primaryColor
                        )
                    }
                }
            }

            // 🌟 局域网已发现设备与同步触发
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
                    Text(
                        text = "在线设备 (${devices.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    if (devices.isEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp),
                                color = primaryColor
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "正在搜索同一 WiFi 设备...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    } else {
                        devices.forEach { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.PhoneAndroid,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
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
                                    text = if (device.connected) "已连接" else "在线",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = primaryColor
                                )
                            }
                        }
                    }

                    Button(
                        onClick = { syncManager.syncNow() },
                        enabled = !syncing && devices.isNotEmpty(),
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
