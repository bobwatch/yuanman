package com.yuanman.app.ui.screens.add_edit

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import java.math.RoundingMode
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditRecordScreen(
    viewModel: AddEditRecordViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategoryManage: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // 聚焦与软键盘状态（避免逐帧重组，保证极致丝滑）
    var isRemarkFocused by remember { mutableStateOf(false) }
    val isImeVisible = WindowInsets.isImeVisible
    val isKeyboardOpen = isRemarkFocused || isImeVisible

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
            viewModel.clearSavedFeedbackMessage()
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
                .imePadding()
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

            // 可滚动的内容区
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // 金额展示卡片
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
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor,
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = uiState.expression.ifEmpty { "0.00" },
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor,
                                    letterSpacing = (-0.5).sp
                                )
                            }

                            if (uiState.selectedCategory != null) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color(uiState.selectedCategory!!.colorHex).copy(alpha = 0.15f)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        CategoryIconView(
                                            iconName = uiState.selectedCategory!!.iconName,
                                            colorHex = uiState.selectedCategory!!.colorHex,
                                            size = 20.dp,
                                            iconSize = 12.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = uiState.selectedCategory!!.name,
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = Color(uiState.selectedCategory!!.colorHex),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 算式计算结果预览
                        val resultPreview = KeypadEngine.calculate(uiState.expression)
                        if (resultPreview != null && KeypadEngine.hasOperator(uiState.expression)) {
                            Text(
                                text = "= ¥${resultPreview.setScale(2, RoundingMode.HALF_UP)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 分类选择区标头
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "选择分类",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (onNavigateToCategoryManage != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToCategoryManage() }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
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

                Spacer(modifier = Modifier.height(8.dp))

                // 分类网格
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 150.dp)
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
                                    size = 36.dp,
                                    iconSize = 18.dp
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

                // 极简记账时间与支付方式行 (日历 Icon + 日期选择胶囊)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 日期时间选择胶囊
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
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
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CalendarToday,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = DateTimeUtils.formatDateTime(uiState.recordTime),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }

                    // 支付方式滑动行
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(PaymentMethod.ALL) { method ->
                            FilterChip(
                                selected = uiState.paymentMethod == method,
                                onClick = { viewModel.setPaymentMethod(method) },
                                label = { Text(method, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 快捷推荐备注
                if (uiState.quickRemarks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(uiState.quickRemarks) { tag ->
                            val isSelected = uiState.remark.contains(tag)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.selectQuickRemark(tag) },
                                label = {
                                    Text(
                                        text = tag,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                // 🌟 备注输入框 (原生硬件级顺滑响应，带完成按钮与清空按钮)
                OutlinedTextField(
                    value = uiState.remark,
                    onValueChange = { viewModel.setRemark(it) },
                    label = { Text("备注说明") },
                    placeholder = { Text("如：朋友聚餐、买咖啡等") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        isRemarkFocused = false
                    }),
                    trailingIcon = {
                        if (uiState.remark.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setRemark("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "清空", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isRemarkFocused = it.isFocused },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // 🌟 底部沉浸式计算器键盘 (键盘弹起时隐藏，平滑稳定无逐帧重组)
            if (!isKeyboardOpen) {
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
    }

    ConfirmDeleteDialog(
        visible = showDeleteConfirm,
        title = "删除账单",
        message = "确定要删除这条账单记录吗？删除后不可恢复。",
        onConfirm = { viewModel.deleteRecord() },
        onDismiss = { showDeleteConfirm = false }
    )
}
