package com.yuanman.app.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.data.model.ThemeMode
import com.yuanman.app.ui.components.ConfirmDeleteDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showFirstConfirmDialog by remember { mutableStateOf(false) }
    var showSecondConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isClearedSuccess) {
        if (uiState.isClearedSuccess) {
            snackbarHostState.showSnackbar("全部数据已成功清空并恢复默认设置")
            viewModel.resetClearedFlag()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 主题与外观
            Text(
                text = "外观模式",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ThemeMode.values().forEach { mode ->
                        val isSelected = uiState.themeMode == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setThemeMode(mode) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.SYSTEM -> Icons.Default.BrightnessAuto
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            }

                            RadioButton(
                                selected = isSelected,
                                onClick = { viewModel.setThemeMode(mode) }
                            )
                        }
                    }
                }
            }

            // 2. 记账偏好
            Text(
                text = "记账偏好",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 默认账单类型
                    Text(
                        text = "默认账单类型",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        FilterChip(
                            selected = uiState.defaultRecordType == RecordType.EXPENSE,
                            onClick = { viewModel.setDefaultRecordType(RecordType.EXPENSE) },
                            label = { Text("默认支出") },
                            modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = uiState.defaultRecordType == RecordType.INCOME,
                            onClick = { viewModel.setDefaultRecordType(RecordType.INCOME) },
                            label = { Text("默认收入") },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // 默认支付方式
                    Text(
                        text = "默认支付方式",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(PaymentMethod.ALL) { method ->
                            val isSelected = uiState.defaultPaymentMethod == method
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setDefaultPaymentMethod(method) },
                                label = { Text(method) }
                            )
                        }
                    }
                }
            }

            // 3. 数据管理
            Text(
                text = "数据管理",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本地存储数据", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "共 ${uiState.totalRecordCount} 笔账单",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                    }

                    Divider(modifier = Modifier.padding(vertical = 12.dp))

                    // 清空全部数据危险项
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showFirstConfirmDialog = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "清空全部数据",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    text = "清除所有账单记录并恢复默认分类",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // 4. 关于应用与隐私说明
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "沅满记账 v1.0.0",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "纯本地离线运行 · 守护您的个人财务隐私",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(88.dp))
        }
    }

    // 第一次确认清空
    ConfirmDeleteDialog(
        visible = showFirstConfirmDialog,
        title = "清空全部数据",
        message = "确定要清空全部数据吗？此操作将删除所有本地账单记录并重置分类，不可恢复！",
        confirmButtonText = "继续清空",
        onConfirm = {
            showFirstConfirmDialog = false
            showSecondConfirmDialog = true
        },
        onDismiss = { showFirstConfirmDialog = false }
    )

    // 第二次强确认清空
    ConfirmDeleteDialog(
        visible = showSecondConfirmDialog,
        title = "二次确认：清空数据",
        message = "【极为关键】您即将永久销毁所有记账数据，请确认是否继续？",
        confirmButtonText = "确认销毁并重置",
        confirmButtonColor = MaterialTheme.colorScheme.error,
        onConfirm = {
            viewModel.clearAllData()
            showSecondConfirmDialog = false
        },
        onDismiss = { showSecondConfirmDialog = false }
    )
}
