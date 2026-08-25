package com.yuanman.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yuanman.app.data.local.entity.RecordWithCategory
import com.yuanman.app.data.model.RecordType
import com.yuanman.app.ui.components.*
import com.yuanman.app.utils.DateTimeUtils
import com.yuanman.app.utils.MoneyUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToAddRecord: (RecordType) -> Unit,
    onNavigateToDetail: (Long) -> Unit,
    onNavigateToStatistics: () -> Unit,
    onNavigateToList: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val defaultType by viewModel.defaultRecordType.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showMonthPicker by remember { mutableStateOf(false) }

    // 筛选标签：全部 / 支出 / 收入 (Bitget Wallet 风格)
    var filterType by remember { mutableStateOf<RecordType?>(null) }

    // 长按快捷菜单状态
    var activeMenuRecord by remember { mutableStateOf<RecordWithCategory?>(null) }
    var recordToDelete by remember { mutableStateOf<RecordWithCategory?>(null) }

    // 过滤后的分组数据
    val filteredGroupedRecords = remember(uiState.groupedRecords, filterType) {
        if (filterType == null) {
            uiState.groupedRecords
        } else {
            uiState.groupedRecords.mapValues { (_, records) ->
                records.filter { it.record.type == filterType?.name }
            }.filterValues { it.isNotEmpty() }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 🌟 1. 顶部 Bitget Wallet 风格总资产看板
            BitgetAssetDashboard(
                year = uiState.selectedYear,
                month = uiState.selectedMonth,
                totalExpense = uiState.summary.totalExpense,
                totalIncome = uiState.summary.totalIncome,
                balance = uiState.summary.balance,
                monthlyBudget = uiState.monthlyBudget,
                budgetUsedPercent = uiState.budgetUsedPercent,
                remainingBudgetCents = uiState.remainingBudgetCents,
                dailyAvailableCents = uiState.dailyAvailableCents,
                onPrevMonth = { viewModel.previousMonth() },
                onNextMonth = { viewModel.nextMonth() },
                onMonthClick = { showMonthPicker = true },
                onAddRecord = { onNavigateToAddRecord(defaultType) },
                onViewList = onNavigateToList,
                onViewStats = onNavigateToStatistics,
                onViewBudget = onNavigateToSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 🌟 2. Bitget Wallet 分段切换 Tabs (全部 / 支出 / 收入)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BitgetFilterPill(
                    title = "全部",
                    isSelected = filterType == null,
                    onClick = { filterType = null }
                )
                BitgetFilterPill(
                    title = "仅支出",
                    isSelected = filterType == RecordType.EXPENSE,
                    onClick = { filterType = RecordType.EXPENSE }
                )
                BitgetFilterPill(
                    title = "仅收入",
                    isSelected = filterType == RecordType.INCOME,
                    onClick = { filterType = RecordType.INCOME }
                )
            }

            // 🌟 3. 账单列表 (Bitget 交易明细流)
            if (filteredGroupedRecords.isEmpty()) {
                EmptyStateView(
                    title = "${uiState.selectedYear}年${uiState.selectedMonth}月暂无账单",
                    description = "点击上方「记一笔」按钮，记录生活中的每一笔收支",
                    actionButtonText = "立即记一笔",
                    onActionClick = { onNavigateToAddRecord(defaultType) },
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 2.dp, bottom = 88.dp)
                ) {
                    filteredGroupedRecords.forEach { (dayTimestamp, records) ->
                        val daySum = uiState.daySummaries[dayTimestamp] ?: Pair(0L, 0L)

                        item(key = "header_$dayTimestamp") {
                            DateGroupHeader(
                                timestamp = dayTimestamp,
                                totalExpense = daySum.first,
                                totalIncome = daySum.second
                            )
                        }

                        items(
                            items = records,
                            key = { "record_${it.record.id}" }
                        ) { item ->
                            BitgetTransactionItem(
                                item = item,
                                onClick = { onNavigateToDetail(item.record.id) },
                                onLongClick = { activeMenuRecord = item }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }

    // 月份选择器弹窗
    MonthPickerModal(
        visible = showMonthPicker,
        initialYear = uiState.selectedYear,
        initialMonth = uiState.selectedMonth,
        onMonthSelected = { year, month ->
            viewModel.selectMonth(year, month)
            showMonthPicker = false
        },
        onDismiss = { showMonthPicker = false }
    )

    // 长按快捷操作底部弹层
    if (activeMenuRecord != null) {
        val target = activeMenuRecord!!
        ModalBottomSheet(
            onDismissRequest = { activeMenuRecord = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "账单操作 · ${target.category?.name ?: "未分类"} ¥${MoneyUtils.centsToYuanString(target.record.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                ListItem(
                    headlineContent = { Text("复制再记一笔") },
                    supportingContent = { Text("以此分类与金额为模板快速新增一条今日账单") },
                    leadingContent = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            viewModel.copyRecord(target.record)
                            activeMenuRecord = null
                        }
                )

                ListItem(
                    headlineContent = { Text("查看详情") },
                    leadingContent = {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val id = target.record.id
                            activeMenuRecord = null
                            onNavigateToDetail(id)
                        }
                )

                ListItem(
                    headlineContent = { Text("删除该账单", color = MaterialTheme.colorScheme.error) },
                    leadingContent = {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            recordToDelete = target
                            activeMenuRecord = null
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 删除确认弹窗
    ConfirmDeleteDialog(
        visible = recordToDelete != null,
        title = "删除账单",
        message = "确定要删除分类为「${recordToDelete?.category?.name}」金额为「${MoneyUtils.formatCurrency(recordToDelete?.record?.amount ?: 0L)}」的账单吗？",
        onConfirm = {
            recordToDelete?.let { viewModel.deleteRecord(it) }
            recordToDelete = null
        },
        onDismiss = { recordToDelete = null }
    )
}

/**
 * 🌟 Bitget Wallet 风格总资产看板 (Header Dashboard)
 */
@Composable
private fun BitgetAssetDashboard(
    year: Int,
    month: Int,
    totalExpense: Long,
    totalIncome: Long,
    balance: Long,
    monthlyBudget: Long,
    budgetUsedPercent: Float,
    remainingBudgetCents: Long,
    dailyAvailableCents: Long,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onMonthClick: () -> Unit,
    onAddRecord: () -> Unit,
    onViewList: () -> Unit,
    onViewStats: () -> Unit,
    onViewBudget: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // 顶栏：账本标识 + 月份选择胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(primaryColor)
                    )
                    Text(
                        text = "沅满账本",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 月份胶囊
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onPrevMonth,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "上月",
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        Text(
                            text = "${year}年${month}月",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { onMonthClick() }
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        )

                        IconButton(
                            onClick = onNextMonth,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "下月",
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 核心金额展示 (大字号)
            Text(
                text = "本月总支出",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "¥",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = MoneyUtils.centsToYuanString(totalExpense, withGrouping = true),
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 收入与结余胶囊标签行
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "收入 ¥${MoneyUtils.centsToYuanString(totalIncome)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primaryColor
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (balance >= 0) primaryColor.copy(alpha = 0.12f) else MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = if (balance >= 0) "结余 +¥${MoneyUtils.centsToYuanString(balance)}" else "结余 -¥${MoneyUtils.centsToYuanString(-balance)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (balance >= 0) primaryColor else MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // 预算进度
            if (monthlyBudget > 0L) {
                Spacer(modifier = Modifier.height(12.dp))
                val budgetColor = when {
                    budgetUsedPercent <= 0.7f -> primaryColor
                    budgetUsedPercent <= 0.95f -> Color(0xFFFF9800)
                    else -> MaterialTheme.colorScheme.error
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "预算 ¥${MoneyUtils.centsToYuanString(monthlyBudget)}",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = if (remainingBudgetCents >= 0) "剩余 ¥${MoneyUtils.centsToYuanString(remainingBudgetCents)}" else "超支 ¥${MoneyUtils.centsToYuanString(-remainingBudgetCents)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = budgetColor
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    LinearProgressIndicator(
                        progress = { budgetUsedPercent.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = budgetColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🌟 4个核心快捷按钮 (Bitget Wallet Signature 4-Button Grid)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BitgetActionButton(
                    icon = Icons.Default.Add,
                    label = "记一笔",
                    isHighlighted = true,
                    onClick = onAddRecord
                )
                BitgetActionButton(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    label = "查明细",
                    isHighlighted = false,
                    onClick = onViewList
                )
                BitgetActionButton(
                    icon = Icons.Default.PieChart,
                    label = "看统计",
                    isHighlighted = false,
                    onClick = onViewStats
                )
                BitgetActionButton(
                    icon = Icons.Default.Tune,
                    label = "设预算",
                    isHighlighted = false,
                    onClick = onViewBudget
                )
            }
        }
    }
}

@Composable
private fun BitgetActionButton(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 4.dp, horizontal = 6.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isHighlighted) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(46.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isHighlighted) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun BitgetFilterPill(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

/**
 * 🌟 Bitget Wallet 风格单条交易记录卡片
 */
@Composable
fun BitgetTransactionItem(
    item: RecordWithCategory,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val record = item.record
    val category = item.category
    val isExpense = record.type == RecordType.EXPENSE.name
    val primaryColor = MaterialTheme.colorScheme.primary

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CategoryIconView(
                iconName = category?.iconName ?: "other",
                colorHex = category?.colorHex ?: 0xFF607D8BL,
                size = 42.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = category?.name ?: "未分类",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                val subtitle = when {
                    record.remark.isNotBlank() && record.paymentMethod.isNotBlank() -> "${record.remark} · ${record.paymentMethod}"
                    record.remark.isNotBlank() -> record.remark
                    record.paymentMethod.isNotBlank() -> record.paymentMethod
                    else -> "常规收支"
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (isExpense) "-¥${MoneyUtils.centsToYuanString(record.amount)}" else "+¥${MoneyUtils.centsToYuanString(record.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    ),
                    color = if (isExpense) MaterialTheme.colorScheme.onSurface else primaryColor
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = DateTimeUtils.formatTime(record.recordTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}
