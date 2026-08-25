package com.yuanman.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.AmountDisplay
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailScreen(
    viewModel: RecordDetailViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) {
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.isCopiedSuccess) {
        if (uiState.isCopiedSuccess) {
            snackbarHostState.showSnackbar("已以此账单为模板复制新增今日记录 ✨")
            viewModel.resetCopiedFlag()
        }
    }

    val item = uiState.recordWithCategory

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("账单详情", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (item != null) {
                        IconButton(onClick = { onNavigateToEdit(item.record.id) }) {
                            Icon(Icons.Default.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text("账单不存在或已被删除", color = MaterialTheme.colorScheme.outline)
                }
            }
        } else {
            val record = item.record
            val category = item.category
            val isExpense = record.type == RecordType.EXPENSE.name
            val categoryColor = Color(category?.colorHex ?: 0xFF607D8BL)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 票据卡片容器
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 顶部背景光晕与分类图标
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            categoryColor.copy(alpha = 0.18f),
                                            Color.Transparent
                                        )
                                    )
                                )
                                .padding(top = 28.dp, bottom = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CategoryIconView(
                                    iconName = category?.iconName ?: "other",
                                    colorHex = category?.colorHex ?: 0xFF607D8BL,
                                    size = 68.dp,
                                    iconSize = 36.dp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = category?.name ?: "未分类",
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                AmountDisplay(
                                    amountInCents = record.amount,
                                    type = if (isExpense) RecordType.EXPENSE else RecordType.INCOME,
                                    showSign = true,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // 票据分割线
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )

                        // 属性清单
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            DetailRow(label = "收支类型", value = if (isExpense) "支出" else "收入")
                            DetailRow(label = "支付方式", value = record.paymentMethod.ifBlank { "默认" })
                            DetailRow(label = "记账时间", value = DateTimeUtils.formatDateTime(record.recordTime))
                            DetailRow(label = "备注说明", value = record.remark.ifBlank { "无备注" })
                            DetailRow(label = "创建时间", value = DateTimeUtils.formatDateTime(record.createdAt))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 底部操作按钮栏 (复制再记、编辑、删除)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.copyRecord() },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制一笔", fontSize = 13.sp)
                    }

                    Button(
                        onClick = { onNavigateToEdit(record.id) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("编辑", fontSize = 13.sp)
                    }

                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("删除", fontSize = 13.sp)
                    }
                }

                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }

    ConfirmDeleteDialog(
        visible = showDeleteDialog,
        title = "删除账单",
        message = "确定要删除这条账单记录吗？删除后数据不可恢复。",
        onConfirm = { viewModel.deleteRecord() },
        onDismiss = { showDeleteDialog = false }
    )
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
