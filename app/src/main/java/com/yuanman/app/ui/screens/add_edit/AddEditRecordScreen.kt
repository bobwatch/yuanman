package com.yuanman.app.ui.screens.add_edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.model.PaymentMethod
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.CategoryIconView
import com.yuanman.app.ui.components.ConfirmDeleteDialog
import com.yuanman.app.ui.components.CustomKeypad
import com.yuanman.app.ui.components.KeypadEngine
import com.yuanman.app.utils.DateTimeUtils
import java.math.BigDecimal
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecordScreen(
    viewModel: AddEditRecordViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSavedSuccess) {
        if (uiState.isSavedSuccess) {
            onNavigateBack()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearErrorMessage()
        }
    }

    LaunchedEffect(uiState.savedFeedbackMessage) {
        uiState.savedFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (uiState.isEditMode) "编辑账单" else "记一笔",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "取消")
                    }
                },
                actions = {
                    if (uiState.isEditMode) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 支出 / 收入 切换 Tab
            TabRow(
                selectedTabIndex = if (uiState.type == RecordType.EXPENSE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = uiState.type == RecordType.EXPENSE,
                    onClick = { viewModel.setRecordType(RecordType.EXPENSE) },
                    text = {
                        Text(
                            text = "支出",
                            fontSize = 15.sp,
                            fontWeight = if (uiState.type == RecordType.EXPENSE) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
                Tab(
                    selected = uiState.type == RecordType.INCOME,
                    onClick = { viewModel.setRecordType(RecordType.INCOME) },
                    text = {
                        Text(
                            text = "收入",
                            fontSize = 15.sp,
                            fontWeight = if (uiState.type == RecordType.INCOME) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }

            // 可滚动的内容区（金额展示 + 分类选择 + 快捷标签 + 扩展选项）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 金额展示大卡片
                val amountColor = if (uiState.type == RecordType.EXPENSE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "¥",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (uiState.expression.isEmpty()) "0.00" else uiState.expression,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (uiState.expression.isEmpty()) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else amountColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 如果包含算式运算符，实时展示计算结果提示
                            if (uiState.expression.contains("+") || uiState.expression.contains("-")) {
                                val computed = KeypadEngine.evaluateExpression(uiState.expression)
                                if (computed != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    ) {
                                        Text(
                                            text = "= ¥${KeypadEngine.formatDecimal(computed)}",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 分类选择网格
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "分类",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.outline
                    )

                    if (onNavigateToCategoryManage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToCategoryManage() }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "管理分类",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "管理分类",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.availableCategories) { category ->
                            val isSelected = uiState.selectedCategory?.id == category.id
                            val categoryColor = Color(category.colorHex)

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) categoryColor.copy(alpha = 0.15f) else Color.Transparent
                                    )
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) categoryColor else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { viewModel.selectCategory(category) }
                                    .padding(vertical = 6.dp, horizontal = 2.dp)
                            ) {
                                CategoryIconView(
                                    iconName = category.iconName,
                                    colorHex = category.colorHex,
                                    size = 38.dp,
                                    iconSize = 20.dp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) categoryColor else MaterialTheme.colorScheme.onSurface
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 智能推荐快捷备注标签
                if (uiState.quickRemarks.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Label,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "快捷备注",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.quickRemarks) { tag ->
                            val isSelected = uiState.remark.contains(tag)
                            SuggestionChip(
                                onClick = { viewModel.selectQuickRemark(tag) },
                                label = {
                                    Text(
                                        text = tag,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            )
                        }
                    }
                }

                // 更多选项折叠栏（时间、支付方式、手动备注）
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showMoreOptions = !showMoreOptions }
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${DateTimeUtils.formatMonthDayWithWeek(uiState.recordTime)} · ${uiState.paymentMethod}${if (uiState.remark.isNotBlank()) " · ${uiState.remark}" else ""}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = if (showMoreOptions) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = showMoreOptions) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 日期时间选择
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = uiState.recordTime }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, dayOfMonth ->
                                            cal.set(Calendar.YEAR, year)
                                            cal.set(Calendar.MONTH, month)
                                            cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                                            TimePickerDialog(
                                                context,
                                                { _, hourOfDay, minute ->
                                                    cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    cal.set(Calendar.MINUTE, minute)
                                                    viewModel.setRecordTime(cal.timeInMillis)
                                                },
                                                cal.get(Calendar.HOUR_OF_DAY),
                                                cal.get(Calendar.MINUTE),
                                                true
                                            ).show()
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.outline)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("记账时间", style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(
                                    text = DateTimeUtils.formatDateTime(uiState.recordTime),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }

                        // 支付方式
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(PaymentMethod.ALL) { method ->
                                FilterChip(
                                    selected = uiState.paymentMethod == method,
                                    onClick = { viewModel.setPaymentMethod(method) },
                                    label = { Text(method, fontSize = 12.sp) }
                                )
                            }
                        }

                        // 备注输入框
                        OutlinedTextField(
                            value = uiState.remark,
                            onValueChange = { viewModel.setRemark(it) },
                            label = { Text("备注说明") },
                            placeholder = { Text("如：朋友聚餐、买咖啡等") },
                            singleLine = true,
                            trailingIcon = {
                                if (uiState.remark.isNotEmpty()) {
                                    IconButton(onClick = { viewModel.setRemark("") }) {
                                        Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // 底部沉浸式计算器键盘
            CustomKeypad(
                expression = uiState.expression,
                onExpressionChange = { viewModel.setExpression(it) },
                onComplete = { viewModel.saveRecord(continueNext = false) },
                onSaveAndContinue = { viewModel.saveRecord(continueNext = true) },
                isEditMode = uiState.isEditMode,
                hapticEnabled = uiState.hapticEnabled
            )
        }
    }

    ConfirmDeleteDialog(
        visible = showDeleteConfirm,
        title = "删除账单",
        message = "确定要删除这条账单记录吗？删除后不可恢复。",
        onConfirm = { viewModel.deleteRecord() },
        onDismiss = { showDeleteConfirm = false }
    )
}
